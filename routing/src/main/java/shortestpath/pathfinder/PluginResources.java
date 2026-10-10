package shortestpath.pathfinder;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import shortestpath.Destination;
import shortestpath.DestinationRequirements;
import shortestpath.ItemVariations;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.requirement.ItemRequirement;

/**
 * The plugin's world data, loaded once per JVM and shared by every headless
 * {@link PathfinderConfig}. In this package because the loaders it needs are package-private.
 *
 * <p>Each kind of data has its own holder, so a caller that only needs transports (the route item
 * list) never loads the collision map.
 */
public final class PluginResources {
    private PluginResources() { }

    private static final class Transports {
        static final Map<Integer, Set<Transport>> ALL = load();
        static final Set<Integer> ROUTE_ITEM_IDS = routeItemIds(ALL);

        private static Map<Integer, Set<Transport>> load() {
            Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();
            PathfinderConfig.remapPohDestinations(transports);
            return transports;
        }
    }

    private static final class Destinations {
        static final Map<String, Set<Integer>> ALL = Destination.loadAllFromResources();
        static final Map<String, Set<Integer>> FILTERED = PathfinderConfig.filterDestinations(ALL);
        static final Map<Integer, DestinationRequirements> BANKS = Destination.loadBankRequirementsFromResources();
    }

    private static final class CollisionData {
        static final SplitFlagMap DATA = SplitFlagMap.fromResources();
    }

    public static SplitFlagMap map() { return CollisionData.DATA; }
    public static Map<Integer, Set<Transport>> transports() { return Transports.ALL; }
    public static Map<String, Set<Integer>> destinations() { return Destinations.ALL; }
    public static Map<String, Set<Integer>> filteredDestinations() { return Destinations.FILTERED; }
    public static Map<Integer, DestinationRequirements> bankRequirements() { return Destinations.BANKS; }

    /** Every item a route can require: transport item requirements plus the items the plugin checks in code. */
    public static Set<Integer> routeItemIds() { return Transports.ROUTE_ITEM_IDS; }

    private static Set<Integer> routeItemIds(Map<Integer, Set<Transport>> all) {
        Set<Integer> result = new HashSet<>();
        for (Set<Transport> transports : all.values()) {
            for (Transport transport : transports) {
                if (transport.getItemRequirements() != null) {
                    for (ItemRequirement requirement : transport.getItemRequirements().getRequirements()) {
                        add(result, requirement.getItemIds());
                        add(result, requirement.getStaffIds());
                        add(result, requirement.getOffhandIds());
                    }
                }
            }
        }
        // PathfinderConfig checks these in code rather than through transport requirements:
        // fairy rings need a staff, pouch runes need a pouch, and currencies pay for fares.
        add(result, ItemVariations.DRAMEN_STAFF.getIds());
        result.addAll(PathfinderConfig.RUNE_POUCHES);
        result.addAll(PathfinderConfig.CURRENCIES);
        return Set.copyOf(result);
    }

    private static void add(Set<Integer> target, int[] ids) {
        if (ids != null) {
            for (int id : ids) {
                target.add(id);
            }
        }
    }
}
