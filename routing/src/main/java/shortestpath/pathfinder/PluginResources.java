package shortestpath.pathfinder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.transport.Transport;
import shortestpath.transport.ResourceLoader;
import shortestpath.poh.PohService;

/**
 * The plugin's world data, loaded once per JVM and shared by every headless
 * {@link PathfinderConfig}. In this package to access its package-private destination filter.
 *
 * <p>Each kind of data has its own holder, so a caller that only needs transports never loads the
 * collision map.
 * Returned data is shared and must not be mutated.
 */
public final class PluginResources {
    private PluginResources() { }

    private static final class Transports {
        static final Map<Integer, Set<Transport>> ALL = load();

        private static Map<Integer, Set<Transport>> load() {
            Map<Integer, Set<Transport>> transports = ResourceLoader.transports();
            PohService.remapPohDestinations(transports);
            return transports;
        }
    }

    private static final class Destinations {
        static final Map<String, Set<Integer>> ALL = ResourceLoader.destinations();
        static final Map<String, Set<Integer>> FILTERED = PathfinderConfig.filterDestinations(ALL);
        static final Map<Integer, List<DestinationRequirements>> BANKS = ResourceLoader.bankRequirements();
    }

    private static final class CollisionData {
        static final SplitFlagMap DATA = SplitFlagMap.fromResources();
    }

    public static SplitFlagMap map() { return CollisionData.DATA; }
    public static Map<Integer, Set<Transport>> transports() { return Transports.ALL; }
    public static Map<String, Set<Integer>> destinations() { return Destinations.ALL; }
    public static Map<String, Set<Integer>> filteredDestinations() { return Destinations.FILTERED; }
    public static Map<Integer, List<DestinationRequirements>> bankRequirements() { return Destinations.BANKS; }
}
