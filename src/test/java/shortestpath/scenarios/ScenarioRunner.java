package shortestpath.scenarios;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.AStarPathfinder;
import shortestpath.pathfinder.AStarRoutingStaticProvider;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.ProfilingPathfinder;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.pathfinder.astar.AStarRoutingSession;
import shortestpath.pathfinder.astar.PreparedRoutingAccount;
import shortestpath.pathfinder.astar.RoutingStatic;
import shortestpath.pathfinder.astar.SiteGraph;
import shortestpath.profiles.CompiledAccount;

/**
 * Runs scenarios on one pathfinder backend. The only place the route CLI, the dashboard and the
 * benchmark adapter call a pathfinder, so they agree on what a run measures and what "reached"
 * means ({@link Observation#isReached()}).
 *
 * <p>Thread-safe: the A* backend's static routing data is built once and shared; sessions are
 * keyed by account fingerprint.
 */
public final class ScenarioRunner {
    public enum Backend {
        LEGACY, ASTAR;

        public static Backend parse(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("algorithm must be legacy or astar");
            }
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * How much of the A* backend's preparation is done before the timed run. Static routing data is
     * always built first ({@link #prepare()}) and reported separately.
     */
    public enum AStarSession {
        /** Nothing per account or per target before the run: every stage is prepared inside it. */
        COLD,
        /** The account's graph is prepared before the run; the target is prepared in it, as for a new destination. */
        ACCOUNT,
        /** The account's graph and this target are both prepared before the run. */
        TARGET;

        public static AStarSession parse(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("astar_session must be cold, account or target");
            }
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Legacy profiling: off, on, or on with the per-tile visit heatmap. */
    public enum Profiling { OFF, ON, HEATMAP }

    private final Backend backend;
    private final AStarSession aStarSession;
    private final Map<Long, AStarRoutingSession> sessions = new ConcurrentHashMap<>();
    private volatile RoutingStatic routingStatic;
    private long routingStaticBuildNanos;

    public ScenarioRunner(Backend backend) {
        this(backend, AStarSession.COLD);
    }

    public ScenarioRunner(Backend backend, AStarSession aStarSession) {
        this.backend = backend;
        this.aStarSession = aStarSession;
    }

    public Backend getBackend() { return backend; }
    public AStarSession getAStarSession() { return aStarSession; }

    /** Builds the A* backend's static routing data now rather than in the first run. */
    public synchronized void prepare() {
        if (backend == Backend.ASTAR && routingStatic == null) {
            long started = System.nanoTime();
            routingStatic = new AStarRoutingStaticProvider(() -> new CollisionMap(SplitFlagMap.fromResources())).get();
            routingStaticBuildNanos = System.nanoTime() - started;
        }
    }

    /** How long building the A* static routing data took; 0 for legacy. */
    public long getRoutingStaticBuildNanos() {
        return routingStaticBuildNanos;
    }

    /** The A* backend's static routing data once {@link #prepare()} has built it; {@code null} for legacy. */
    public RoutingStatic getRoutingStatic() {
        return routingStatic;
    }

    /** Compiles {@code scenario} and runs it, on the calling thread. */
    public Observation run(Scenario scenario) {
        return run(scenario, scenario.compile(), Profiling.OFF);
    }

    /**
     * Runs {@code scenario} with an account already compiled for it (on this thread, which the
     * compiled config treats as the client thread). Only the search is timed.
     */
    public Observation run(Scenario scenario, CompiledAccount account, Profiling profiling) {
        PathfinderConfig config = account.getConfig();
        int start = scenario.getRouteStart();
        Set<Integer> targets = Set.of(scenario.getTarget());
        if (backend == Backend.LEGACY) {
            if (profiling == Profiling.OFF) {
                long started = System.nanoTime();
                Pathfinder pathfinder = new Pathfinder(config, start, targets);
                pathfinder.run();
                long total = System.nanoTime() - started;
                return new Observation(scenario, backend, account, pathfinder.getResult(), null, null, total);
            }
            long started = System.nanoTime();
            ProfilingPathfinder pathfinder = new ProfilingPathfinder(config, start, targets,
                profiling == Profiling.HEATMAP);
            pathfinder.run();
            long total = System.nanoTime() - started;
            return new Observation(scenario, backend, account, pathfinder.getResult(), null,
                pathfinder.getProfile(), total);
        }
        prepare();
        AStarRoutingSession session = session(config, scenario.getTarget());
        long started = System.nanoTime();
        AStarPathfinder pathfinder = new AStarPathfinder(config, routingStatic, session, start, targets, null,
            config.getAStarHeuristicWeight());
        pathfinder.run();
        long total = System.nanoTime() - started;
        return new Observation(scenario, backend, account, pathfinder.getResult(), pathfinder, null, total);
    }

    /**
     * The session the query runs with, in the state its mode describes, prepared before timing. Sessions
     * are per account fingerprint; each keeps its graph and a bounded cache of prepared targets.
     */
    private AStarRoutingSession session(PathfinderConfig config, int target) {
        if (aStarSession == AStarSession.COLD) {
            return null;
        }
        PreparedRoutingAccount account = config.prepareAStarRoutingAccount(true);
        AStarRoutingSession session = sessions.computeIfAbsent(account.fingerprint(), ignored -> new AStarRoutingSession());
        SiteGraph graph = session.graph(routingStatic, account).value();
        if (aStarSession == AStarSession.ACCOUNT) {
            session.clearTargets();
        } else {
            session.target(graph, config.getMap(), target);
        }
        return session;
    }
}
