package shortestpath.routeapi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.transport.Transport;
import shortestpath.transport.requirement.ItemRequirement;

/**
 * Turns a pathfinder result into a {@code route-api-v1} plan: consecutive walking steps become a
 * walk segment, each transport or teleport a travel segment with its requirements, and the first
 * step with the bank visited a bank segment.
 */
public final class RoutePlans {
    private static final String ROUTING_ENGINE_VERSION = "exact-v1";

    private static final Comparator<Transport> TRANSPORT_ORDER = Comparator
        .comparing((Transport value) -> value.getType() == null ? "TRANSPORT" : value.getType().name())
        .thenComparing(value -> value.getDisplayInfo() == null ? "" : value.getDisplayInfo())
        .thenComparing(value -> value.getObjectInfo() == null ? "" : value.getObjectInfo());

    private RoutePlans() { }

    /**
     * @param config the config the result was planned with, which identifies each step's transport
     * @param itemName names an item id in requirements
     */
    public static RouteApi.RoutePlan plan(RouteApi.Location start, RouteApi.Location destination,
            PathfinderResult result, PathfinderConfig config, IntFunction<String> itemName) {
        RouteApi.RoutePlan plan = new RouteApi.RoutePlan();
        plan.reachable = result.isReached();
        plan.costTicks = result.isReached() ? result.getPathCost() : null;
        plan.start = start;
        plan.destination = destination;
        plan.metadata = new RouteApi.Metadata();
        plan.metadata.worldDataVersion = "shortest-path-resources";
        plan.metadata.routingEngineVersion = ROUTING_ENGINE_VERSION;
        plan.segments = segments(result.getPathSteps(), config, itemName);
        return plan;
    }

    public static int packed(RouteApi.WorldPoint point) {
        return WorldPointUtil.packWorldPoint(point.x, point.y, point.plane);
    }

    private static RouteApi.WorldPoint point(int packed) {
        return new RouteApi.WorldPoint(WorldPointUtil.unpackWorldX(packed),
            WorldPointUtil.unpackWorldY(packed), WorldPointUtil.unpackWorldPlane(packed));
    }

    private static List<Object> segments(List<PathStep> path, PathfinderConfig config, IntFunction<String> itemName) {
        List<Object> segments = new ArrayList<>();
        List<RouteApi.WorldPoint> walking = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            PathStep from = path.get(i - 1);
            PathStep to = path.get(i);
            if (!from.isBankVisited() && to.isBankVisited()) {
                flushWalk(segments, walking);
                RouteApi.BankSegment bank = new RouteApi.BankSegment();
                bank.location = point(to.getPackedPosition());
                bank.costTicks = 0;
                segments.add(bank);
            }
            Transport transport = transportFor(from, to, config);
            if (transport != null) {
                flushWalk(segments, walking);
                RouteApi.TravelSegment travel = new RouteApi.TravelSegment();
                String type = transport.getType() == null ? "TRANSPORT" : transport.getType().name();
                travel.kind = transport.getType() != null && transport.getType().isTeleport() ? "teleport" : "transport";
                travel.transportId = type + ':' + from.getPackedPosition() + ':' + to.getPackedPosition();
                travel.name = label(transport, type);
                travel.from = point(from.getPackedPosition());
                travel.to = point(to.getPackedPosition());
                travel.costTicks = Math.max(0, transport.getDuration());
                travel.requirements = requirements(transport, itemName);
                segments.add(travel);
            } else {
                if (walking.isEmpty()) {
                    walking.add(point(from.getPackedPosition()));
                }
                walking.add(point(to.getPackedPosition()));
            }
        }
        flushWalk(segments, walking);
        if (path.size() == 1) {
            RouteApi.WalkSegment walk = new RouteApi.WalkSegment();
            walk.costTicks = 0;
            walk.path = List.of(point(path.get(0).getPackedPosition()));
            segments.add(walk);
        }
        return segments;
    }

    private static List<RouteApi.Capability> requirements(Transport transport, IntFunction<String> itemName) {
        List<RouteApi.Capability> result = new ArrayList<>();
        transport.getQuests().stream().sorted(Comparator.comparing(Quest::getName))
            .forEach(quest -> result.add(capability("quest", quest.getName())));
        int[] levels = transport.getSkillLevels();
        Skill[] skills = Skill.values();
        for (int i = 0; i < skills.length && i < levels.length; i++) {
            if (levels[i] > 0) {
                result.add(capability("skill", levels[i] + " " + skills[i].getName()));
            }
        }
        // The plugin stores total level, combat level and quest points after the skills.
        if (levels.length > skills.length && levels[skills.length] > 0) {
            result.add(capability("skill", levels[skills.length] + " total level"));
        }
        if (levels.length > skills.length + 1 && levels[skills.length + 1] > 0) {
            result.add(capability("skill", levels[skills.length + 1] + " combat level"));
        }
        if (levels.length > skills.length + 2 && levels[skills.length + 2] > 0) {
            result.add(capability("skill", levels[skills.length + 2] + " quest points"));
        }
        if (transport.getItemRequirements() != null) {
            for (ItemRequirement item : transport.getItemRequirements().getRequirements()) {
                if (item.getItemIds() == null || item.getItemIds().length == 0) {
                    continue;
                }
                String name = itemName.apply(item.getItemIds()[0]);
                result.add(capability("item", item.getQuantity() > 1 ? name + " x" + item.getQuantity() : name));
            }
        }
        return result;
    }

    private static RouteApi.Capability capability(String kind, String name) {
        RouteApi.Capability capability = new RouteApi.Capability();
        capability.kind = kind;
        capability.name = name;
        return capability;
    }

    private static void flushWalk(List<Object> segments, List<RouteApi.WorldPoint> walking) {
        if (walking.isEmpty()) {
            return;
        }
        RouteApi.WalkSegment walk = new RouteApi.WalkSegment();
        walk.path = List.copyOf(walking);
        walk.costTicks = Math.max(0, walking.size() - 1);
        segments.add(walk);
        walking.clear();
    }

    /** The transport a step took: one leaving {@code from} for {@code to}, else a teleport to {@code to}. */
    private static Transport transportFor(PathStep from, PathStep to, PathfinderConfig config) {
        List<Transport> candidates = new ArrayList<>();
        for (Transport transport : config.getTransportsPacked(to.isBankVisited())
                .getOrDefault(from.getPackedPosition(), TransportAvailability.EMPTY_TRANSPORTS)) {
            if (transport.getDestination() == to.getPackedPosition()) {
                candidates.add(transport);
            }
        }
        if (!candidates.isEmpty()) {
            return candidates.stream().min(TRANSPORT_ORDER).orElse(null);
        }

        boolean walking = WorldPointUtil.unpackWorldPlane(from.getPackedPosition())
            == WorldPointUtil.unpackWorldPlane(to.getPackedPosition())
            && WorldPointUtil.distanceBetween2D(from.getPackedPosition(), to.getPackedPosition()) <= 1;
        if (!walking) {
            for (Transport transport : config.getUsableTeleports(to.isBankVisited())) {
                if (transport.getDestination() == to.getPackedPosition()) {
                    candidates.add(transport);
                }
            }
        }
        return candidates.stream().min(TRANSPORT_ORDER).orElse(null);
    }

    private static String label(Transport transport, String fallback) {
        if (transport.getDisplayInfo() != null && !transport.getDisplayInfo().isEmpty()) {
            return transport.getDisplayInfo();
        }
        if (transport.getObjectInfo() != null && !transport.getObjectInfo().isEmpty()) {
            return transport.getObjectInfo();
        }
        return fallback;
    }
}
