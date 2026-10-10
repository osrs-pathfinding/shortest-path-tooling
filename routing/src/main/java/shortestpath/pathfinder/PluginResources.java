package shortestpath.pathfinder;

import java.util.Map;
import java.util.Set;
import shortestpath.Destination;
import shortestpath.DestinationRequirements;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;

/**
 * The plugin's world data, loaded once per JVM and shared by every headless
 * {@link PathfinderConfig}. In this package because the loaders it needs are package-private.
 *
 * <p>Each kind of data has its own holder, so a caller that only needs transports never loads the
 * collision map.
 */
public final class PluginResources {
    private PluginResources() { }

    private static final class Transports {
        static final Map<Integer, Set<Transport>> ALL = load();

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
}
