package shortestpath.route;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.AStarPathfinder;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.astar.TeleportCapability;
import shortestpath.scenarios.Observation;
import shortestpath.scenarios.Scenario;
import shortestpath.scenarios.ScenarioRunner;
import shortestpath.scenarios.Suites;
import shortestpath.transport.Transport;

/**
 * Batch walk dump: runs every scenario of the canonical suite that belongs to a tier in one JVM
 * and writes one JSON line per scenario, after a header line that records both commits. Two dumps
 * of two plugin checkouts (select the checkout with {@code -PshortestPathDir}) are compared by
 * {@code scripts/walk_diff.py}.
 *
 * <p>Usage: {@code PathDump TIER OUTPUT}, with TIER one of smoke, standard or full. A row is
 * {@code scenario} ({@code <route id>/<account>}), {@code reached}, {@code cost} (null when
 * unreached), {@code termination}, {@code steps} ([packed position, bank-state ordinal, 1 when the
 * step is a transport, else 0] per path step), {@code transports} (one record per transport step),
 * {@code clicks}, {@code kept} (indices into the path) and {@code follows}.
 *
 * <p>A transport record carries the transport's origin (-1 for a global teleport), destination,
 * type, duration, display and object info, and whether the previous tile can serve it:
 * {@code usableFromPrevious} for a transport with an origin (the account's transports from the
 * previous tile include one with the same destination, type and duration) and
 * {@code castableFromPrevious} for a global teleport (the previous tile lies where it can be cast).
 * The other flag is null.
 *
 * <p>{@code follows} is null when the scenario was not reached, else whether every click's stretch
 * from the previous click or kept step equals the path of an independent, unbounded breadth-first
 * search over the game's walking order.
 */
public final class PathDump {
    private static final Set<String> TIERS = Set.of("smoke", "standard", "full");
    private static final String USAGE = "usage: PathDump TIER OUTPUT (TIER is smoke, standard or full)";

    /** Offsets in the game's neighbour order: W, E, S, N, SW, SE, NW, NE. */
    private static final int[] DX = {-1, 1, 0, 0, -1, 1, -1, 1};
    private static final int[] DY = {0, 0, -1, 1, -1, -1, 1, 1};
    /** The walking-mask bit of each of those directions. */
    private static final int[] MASK_BITS = {6, 2, 4, 0, 5, 3, 7, 1};

    private PathDump() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2 || !TIERS.contains(args[0])) {
            throw new IllegalArgumentException(USAGE);
        }
        String tier = args[0];
        Path output = Paths.get(args[1]).toAbsolutePath();
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        ScenarioRunner runner = new ScenarioRunner(ScenarioRunner.Backend.ASTAR, ScenarioRunner.AStarSession.ACCOUNT);
        runner.prepare();
        Gson gson = new GsonBuilder().serializeNulls().create();
        long started = System.nanoTime();
        int count = 0;
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
            writer.println(gson.toJson(header(tier)));
            for (Scenario scenario : Suites.load("canonical")) {
                if (!scenario.getTiers().contains(tier)) {
                    continue;
                }
                writer.println(gson.toJson(row(scenario, runner.run(scenario))));
                count++;
            }
        }
        if (count == 0) {
            throw new IllegalStateException("tier " + tier + " selected no scenario of the canonical suite");
        }
        System.out.println("path dump: " + count + " scenarios (" + tier + ") in "
            + (System.nanoTime() - started) / 1_000_000 + " ms -> " + output);
    }

    private static Map<String, Object> header(String tier) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("suite", "canonical");
        header.put("tier", tier);
        header.put("pluginCommit", System.getProperty("pathDump.shortestPathCommit", "unknown"));
        header.put("pluginDirty", Boolean.getBoolean("pathDump.shortestPathDirty"));
        header.put("toolingCommit", System.getProperty("pathDump.toolingCommit", "unknown"));
        header.put("toolingDirty", Boolean.getBoolean("pathDump.toolingDirty"));
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("header", header);
        return line;
    }

    private static Map<String, Object> row(Scenario scenario, Observation observation) {
        PathfinderResult result = observation.getResult();
        AStarPathfinder aStar = observation.getAStar();
        PathfinderConfig config = observation.getAccount().getConfig();
        List<PathStep> path = result.getPathSteps();
        List<Integer> clicks = aStar == null ? Collections.<Integer>emptyList() : aStar.getClickPoints();
        List<Integer> kept = aStar == null ? Collections.<Integer>emptyList() : aStar.getKeptStepIndices();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scenario", scenario.getName());
        row.put("reached", observation.isReached());
        row.put("cost", observation.isReached() ? observation.getCost() : null);
        row.put("termination", result.getTerminationReason().name());
        List<long[]> steps = new ArrayList<>();
        List<Map<String, Object>> transports = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            PathStep step = path.get(i);
            steps.add(new long[] {step.getPackedPosition(), step.getBankVisitState().ordinal(),
                step.getTransport() == null ? 0 : 1});
            if (step.getTransport() != null) {
                transports.add(transportRecord(config, i, step.getTransport(), i == 0 ? null : path.get(i - 1)));
            }
        }
        row.put("steps", steps);
        row.put("transports", transports);
        row.put("clicks", clicks);
        row.put("kept", kept);
        row.put("follows", aStar == null || !observation.isReached()
            ? null : follows(config.getMap(), path, clicks, kept));
        return row;
    }

    private static Map<String, Object> transportRecord(PathfinderConfig config, int index, Transport transport,
            PathStep previous) {
        boolean local = transport.getOrigin() != Transport.UNDEFINED_ORIGIN;
        Boolean usable = null;
        Boolean castable = null;
        if (local) {
            usable = previous != null && usableFrom(config, previous, transport);
        } else {
            castable = previous != null && TeleportCapability.at(previous.getPackedPosition()).canCast(transport);
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("index", index);
        record.put("origin", transport.getOrigin());
        record.put("destination", transport.getDestination());
        record.put("type", transport.getType() == null ? null : transport.getType().name());
        record.put("duration", transport.getDuration());
        record.put("displayInfo", transport.getDisplayInfo());
        record.put("objectInfo", transport.getObjectInfo());
        record.put("usableFromPrevious", usable);
        record.put("castableFromPrevious", castable);
        return record;
    }

    /** Whether the account's transports from the previous step's tile include one like {@code taken}. */
    private static boolean usableFrom(PathfinderConfig config, PathStep previous, Transport taken) {
        PrimitiveIntHashMap<Transport[]> byOrigin = config.getTransportsPacked(previous.getBankVisitState());
        Transport[] candidates = byOrigin == null ? null : byOrigin.get(previous.getPackedPosition());
        if (candidates == null) {
            return false;
        }
        for (Transport candidate : candidates) {
            if (candidate.getDestination() == taken.getDestination()
                && candidate.getType() == taken.getType()
                && candidate.getDuration() == taken.getDuration()) {
                return true;
            }
        }
        return false;
    }

    private static boolean walk(PathStep a, PathStep b) {
        if (b.getTransport() != null || a.getBankVisitState() != b.getBankVisitState()) {
            return false;
        }
        int p = a.getPackedPosition();
        int q = b.getPackedPosition();
        return WorldPointUtil.unpackWorldPlane(p) == WorldPointUtil.unpackWorldPlane(q)
            && Math.max(Math.abs(WorldPointUtil.unpackWorldX(p) - WorldPointUtil.unpackWorldX(q)),
                Math.abs(WorldPointUtil.unpackWorldY(p) - WorldPointUtil.unpackWorldY(q))) == 1;
    }

    /** Whether every click's stretch from the previous boundary is the game's own path (an unbounded reference search). */
    private static boolean follows(CollisionMap map, List<PathStep> path, List<Integer> clicks, List<Integer> kept) {
        Set<Integer> clickSet = new HashSet<>(clicks);
        Set<Integer> keptSet = new HashSet<>(kept);
        int legStart = 0;
        for (int i = 1; i <= path.size(); i++) {
            if (i < path.size() && walk(path.get(i - 1), path.get(i))) {
                continue;
            }
            int legEnd = i - 1;
            int previous = legStart;
            for (int b = legStart + 1; b <= legEnd; b++) {
                if (!clickSet.contains(b) && !keptSet.contains(b)) {
                    continue;
                }
                if (clickSet.contains(b)) {
                    List<Integer> game = gamePath(map, path.get(previous).getPackedPosition(),
                        path.get(b).getPackedPosition());
                    List<Integer> actual = new ArrayList<>();
                    for (int k = previous + 1; k <= b; k++) {
                        actual.add(path.get(k).getPackedPosition());
                    }
                    if (!game.equals(actual)) {
                        return false;
                    }
                }
                previous = b;
            }
            legStart = i;
        }
        return true;
    }

    /** The tiles after {@code from} up to {@code to} on the game's breadth-first path; empty when unreachable. */
    private static List<Integer> gamePath(CollisionMap map, int from, int to) {
        Map<Integer, Integer> parent = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        parent.put(from, from);
        queue.add(from);
        while (!queue.isEmpty() && !parent.containsKey(to)) {
            int at = queue.poll();
            int mask = Byte.toUnsignedInt(map.ordinaryWalkingMask(at));
            for (int i = 0; i < DX.length; i++) {
                if ((mask & 1 << MASK_BITS[i]) == 0) {
                    continue;
                }
                int next = WorldPointUtil.packWorldPoint(WorldPointUtil.unpackWorldX(at) + DX[i],
                    WorldPointUtil.unpackWorldY(at) + DY[i], WorldPointUtil.unpackWorldPlane(at));
                if (parent.putIfAbsent(next, at) == null) {
                    queue.add(next);
                }
            }
        }
        List<Integer> out = new ArrayList<>();
        if (!parent.containsKey(to)) {
            return out;
        }
        for (int at = to; at != from; at = parent.get(at)) {
            out.add(at);
        }
        Collections.reverse(out);
        return out;
    }
}
