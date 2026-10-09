package shortestpath.scenarios;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.ExactPathfinder;
import shortestpath.pathfinder.ExactRoutingStaticProvider;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.ProfilingPathfinder;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.pathfinder.exact.ExactRoutingSession;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.pathfinder.exact.SiteGraph;
import shortestpath.profiles.CompiledAccount;

/**
 * Runs scenarios on one pathfinder backend. The only place the route CLI, the dashboard and the
 * benchmark adapter call a pathfinder, so they agree on what a run measures and what "reached"
 * means ({@link Observation#isReached()}).
 *
 * <p>Thread-safe: the exact backend's static routing data is built once and shared; sessions are
 * keyed by account fingerprint.
 */
public final class ScenarioRunner {
    public enum Backend {
        LEGACY, EXACT;

        public static Backend parse(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("algorithm must be legacy or exact");
            }
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How much of the exact backend's preparation a query reuses from earlier queries. */
    public enum ExactSession {
        /** Every stage is prepared inside the timed run. */
        COLD,
        /** The account's graph is reused; targets are prepared in the run, as for a new destination. */
        ACCOUNT,
        /** The account's graph and this target are prepared before the timed run. */
        TARGET;

        public static ExactSession parse(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("exact_session must be cold, account or target");
            }
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Legacy profiling: off, on, or on with the per-tile visit heatmap. */
    public enum Profiling { OFF, ON, HEATMAP }

    private final Backend backend;
    private final ExactSession exactSession;
    private final Map<Long, ExactRoutingSession> sessions = new ConcurrentHashMap<>();
    private volatile RoutingStatic routingStatic;
    private long routingStaticBuildNanos;

    public ScenarioRunner(Backend backend) {
        this(backend, ExactSession.COLD);
    }

    public ScenarioRunner(Backend backend, ExactSession exactSession) {
        this.backend = backend;
        this.exactSession = exactSession;
    }

    public Backend getBackend() { return backend; }
    public ExactSession getExactSession() { return exactSession; }

    /** Builds the exact backend's static routing data now rather than in the first run. */
    public synchronized void prepare() {
        if (backend == Backend.EXACT && routingStatic == null) {
            long started = System.nanoTime();
            routingStatic = new ExactRoutingStaticProvider(() -> new CollisionMap(SplitFlagMap.fromResources())).get();
            routingStaticBuildNanos = System.nanoTime() - started;
        }
    }

    /** How long building the exact static routing data took; 0 for legacy. */
    public long getRoutingStaticBuildNanos() {
        return routingStaticBuildNanos;
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
        Set<Integer> targets = Set.of(scenario.getEndPoint());
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
        ExactRoutingSession session = session(config, scenario.getEndPoint());
        long started = System.nanoTime();
        ExactPathfinder pathfinder = new ExactPathfinder(config, routingStatic, session, start, targets, null,
            config.getExactHeuristicWeight());
        pathfinder.run();
        long total = System.nanoTime() - started;
        return new Observation(scenario, backend, account, pathfinder.getResult(), pathfinder, null, total);
    }

    /** The session the query runs with, in the state its mode describes, prepared before timing. */
    private ExactRoutingSession session(PathfinderConfig config, int target) {
        if (exactSession == ExactSession.COLD) {
            return null;
        }
        long fingerprint = config.prepareExactRoutingAccount(true).fingerprint();
        ExactRoutingSession session = sessions.computeIfAbsent(fingerprint, ignored -> new ExactRoutingSession());
        if (exactSession == ExactSession.ACCOUNT) {
            session.clearTargets();
        } else {
            SiteGraph graph = session.graph(routingStatic, config.prepareExactRoutingAccount(true)).value();
            session.target(graph, config.getMap(), target);
        }
        return session;
    }
}
