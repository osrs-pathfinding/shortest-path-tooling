package shortestpath.dashboard;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ExactPathfinder;
import shortestpath.pathfinder.ExactRoutingStaticProvider;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathfinderProfile;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.ProfilingPathfinder;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.TestPathfinderConfig;
import shortestpath.profiles.CompiledAccount;
import shortestpath.profiles.Profiles;
import shortestpath.scenarios.ExpectedLengths;
import shortestpath.scenarios.Scenario;
import shortestpath.scenarios.Suites;

/**
 * Generic dashboard test harness.
 *
 * <p>This class runs its scenario suite whenever a task executes it — under the
 * dedicated {@code dashboard}/{@code captureExpectedLengths} Gradle tasks and under
 * plain {@code ./gradlew test}; no system property gates anything and
 * {@code dashboard.profile} controls profiling only. The Gradle tasks exist to run
 * this class with the right heap and dataset sysprops, and they set
 * {@code ignoreFailures = true}, so the Gradle exit code carries no pass/fail
 * signal — the published bundle's {@code report.json} is the only pass/fail
 * surface.</p>
 *
 * <p>Routes CSV rows may set {@code expect_reachable=false} to assert the route
 * is <em>unreachable</em> (an intentional-failure scenario); absent or any other
 * value means expected reachable.</p>
 *
 * <h3>Gradle invocation</h3>
 * <pre>
 * ./gradlew dashboard
 *   -PdashboardSuite=unit-tests
 *   -PdashboardBundle=unit-tests
 * </pre>
 *
 * <h3>System properties</h3>
 * <table>
 *   <tr><th>Property</th><th>Default</th></tr>
 *   <tr><td>{@code dashboard.suite}</td><td>{@code routes} (see {@link Suites})</td></tr>
 *   <tr><td>{@code dashboard.dataset}</td><td>unset; a data-only scenario CSV file
 *       ({@link shortestpath.scenarios.ScenarioData}) to run instead of a suite</td></tr>
 *   <tr><td>{@code dashboard.bundleName}</td><td>{@code routes}</td></tr>
 *   <tr><td>{@code dashboard.title}</td><td>{@code Dashboard}</td></tr>
 *   <tr><td>{@code dashboard.subtitle}</td><td>suite or file name</td></tr>
 *   <tr><td>{@code dashboard.profile}</td><td>auto — on for datasets up to
 *       {@value #PROFILE_AUTO_MAX_SCENARIOS} scenarios, off above; an explicit
 *       {@code true}/{@code false} always wins</td></tr>
 *   <tr><td>{@code dashboard.heatmap}</td><td>{@code true} (profiling only)</td></tr>
 *   <tr><td>{@code dashboard.threads}</td><td>{@code availableProcessors() - 3}</td></tr>
 *   <tr><td>{@code dashboard.backend}</td><td>{@code LEGACY} — {@code EXACT} runs
 *       the exact engine instead; a per-row {@code pathfinderBackend} config
 *       override still wins, and exact searches record unprofiled</td></tr>
 *   <tr><td>{@code reachability.maxTargets}</td><td>{@code 10000}</td></tr>
 * </table>
 */
public class DashboardTest {

    static {
        ((Logger) LoggerFactory.getLogger("shortestpath.transport.Transport")).setLevel(Level.OFF);
    }

    private static final String SUITE_PROPERTY = "dashboard.suite";
    private static final String DATASET_PROPERTY = "dashboard.dataset";
    private static final String BUNDLE_NAME_PROPERTY = DashboardBundlePublisher.BUNDLE_NAME_PROPERTY;
    private static final String DEFAULT_SUITE = "routes";
    private static final int MAX_SCENARIOS = Integer.getInteger("reachability.maxTargets", 10000);
    /**
     * Profiling is auto-disabled above this scenario count unless
     * {@code dashboard.profile} is set explicitly — instrumented searches run
     * ~30–50x slower on heavy datasets, so large sweeps stay cheap while small
     * debugging datasets keep the profiler by default.
     */
    private static final int PROFILE_AUTO_MAX_SCENARIOS = 200;

    private final ProfilerReportWriter profilerReportWriter = new ProfilerReportWriter();
    private final PathfinderDashboardReportWriter reportWriter = new PathfinderDashboardReportWriter();
    private final DashboardBundlePublisher bundlePublisher = new DashboardBundlePublisher();
    /**
     * The exact backend's shared routing graph, built on first use by whichever
     * worker hits an EXACT scenario. {@link ExactRoutingStaticProvider#get} is
     * synchronized, so concurrent workers share one build.
     */
    private volatile ExactRoutingStaticProvider exactRoutingStatic;

    private ExactRoutingStaticProvider exactRoutingStatic(TestPathfinderConfig config) {
        ExactRoutingStaticProvider provider = exactRoutingStatic;
        if (provider == null) {
            synchronized (this) {
                provider = exactRoutingStatic;
                if (provider == null) {
                    provider = new ExactRoutingStaticProvider(config::getMap);
                    exactRoutingStatic = provider;
                }
            }
        }
        return provider;
    }

    /**
     * Serializes the heartbeat increment together with its printf. The
     * counter alone would still tick monotonically, but two workers could
     * both increment and then print out of order — the [i/N] lines must be
     * emitted in strictly increasing order for the tqdm progress hook.
     */
    private static final Object HEARTBEAT_LOCK = new Object();

    /**
     * Resolve the scenario worker count: the {@code dashboard.threads} system
     * property when set to a positive value, otherwise
     * {@code max(1, availableProcessors() - 3)}, clamped to the scenario count.
     */
    private static int resolveWorkerCount(int scenarioCount) {
        Integer configured = Integer.getInteger("dashboard.threads");
        int workers = configured != null && configured > 0
            ? configured
            : Math.max(1, Runtime.getRuntime().availableProcessors() - 3);
        return Math.max(1, Math.min(workers, scenarioCount));
    }

    @Test
    public void run() throws IOException, InterruptedException {
        String file = System.getProperty(DATASET_PROPERTY, "");
        String suite = file.isEmpty() ? System.getProperty(SUITE_PROPERTY, DEFAULT_SUITE) : null;
        String dataset = suite != null ? suite : file;
        String profileProp = System.getProperty("dashboard.profile");
        String bundleName = System.getProperty(BUNDLE_NAME_PROPERTY, "routes");
        String reportTitle = System.getProperty("dashboard.title", "Dashboard");
        String reportSubtitle = System.getProperty("dashboard.subtitle", datasetLabel(dataset));
        Path siteRoot = bundlePublisher.getOutputRoot();

        List<Scenario> allScenarios = suite != null ? Suites.load(suite) : Suites.loadFile(Paths.get(file));
        List<Scenario> scenarios = allScenarios.subList(0, Math.min(MAX_SCENARIOS, allScenarios.size()));

        int n = scenarios.size();
        // An explicit dashboard.profile always wins; unset means auto — profile
        // only datasets small enough that the instrumentation stays cheap.
        boolean profile = profileProp != null
            ? Boolean.parseBoolean(profileProp)
            : n <= PROFILE_AUTO_MAX_SCENARIOS;
        // The per-tile heatmap rides on the profiler's visit counting; it only
        // exists when profiling is on, and it is the dominant profiling
        // allocation, so it gets its own off switch.
        boolean heatmap = profile
            && Boolean.parseBoolean(System.getProperty("dashboard.heatmap", "true"));

        int workers = resolveWorkerCount(n);
        // Deliberately not an [i/N] heartbeat line — maintenance.py's progress
        // hook consumes anything matching that shape.
        System.out.printf("Running %d scenario(s) on %d worker(s) [%s]%n", n, workers,
            profile ? (heatmap ? "profiled, heatmap" : "profiled") : "unprofiled");

        // Indexed by scenario position so report.json's run list stays in
        // dataset order regardless of completion order. A null slot means the
        // scenario produced no result (same as the old `continue`).
        PathfinderDashboardModels.RunRecord[] results = new PathfinderDashboardModels.RunRecord[n];
        Map<String, Integer> capturedLengths = new ConcurrentHashMap<>();
        // Shared work queue: workers pull the next scenario index, so a slow
        // scenario can't strand a worker the way pre-partitioned ranges would.
        AtomicInteger nextIndex = new AtomicInteger(0);
        // Heartbeat numerator: counts finished scenarios, not scenario indexes,
        // so the [i/N] lines stay monotonic under completion-order execution.
        AtomicInteger completed = new AtomicInteger(0);
        long started = System.currentTimeMillis();

        AtomicInteger workerIds = new AtomicInteger(0);
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "dashboard-worker-" + workerIds.incrementAndGet());
            t.setDaemon(false);
            return t;
        };
        ExecutorService pool = Executors.newFixedThreadPool(workers, threadFactory);
        for (int w = 0; w < workers; w++) {
            pool.submit(() -> {
                try {
                    runScenarioWorker(
                        scenarios, results, capturedLengths, nextIndex, completed,
                        dataset, bundleName, profile, heatmap);
                } catch (Throwable t) {
                    System.err.println("Dashboard worker terminated abnormally: " + t);
                    t.printStackTrace();
                }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            throw e;
        }

        List<PathfinderDashboardModels.RunRecord> runs = new ArrayList<>(n);
        for (PathfinderDashboardModels.RunRecord r : results) {
            if (r != null) {
                runs.add(r);
            }
        }

        PathfinderDashboardModels.Report report = reportWriter.createReport(
            reportTitle,
            reportSubtitle,
            System.currentTimeMillis() - started,
            runs,
            reportWriter.createTransportLayerPointsAlwaysAvailable());
        report.bankNamesFromData = BankDestinationLabels.uniqueSortedBankNames();
        // Tag seasonal bundles so the frontend knows to draw the league
        // region overlay. We treat any bundle whose name starts with
        // "seasonal" as seasonal; this lets `-PdashboardBundle=seasonal-…`
        // and the default `seasonal-…` slugs both opt in without an
        // additional system property.
        report.seasonal = Boolean.parseBoolean(System.getProperty("dashboard.seasonal", "false"))
            || (bundleName != null && bundleName.toLowerCase(java.util.Locale.ROOT).startsWith("seasonal"));
        // Same convention for F2P bundles: `f2p-…` slug or -PdashboardF2p=true.
        report.f2p = Boolean.parseBoolean(System.getProperty("dashboard.f2p", "false"))
            || (bundleName != null && bundleName.toLowerCase(java.util.Locale.ROOT).startsWith("f2p"));

        bundlePublisher.publishBundle(bundleName, report);

        String sourceResourcesDir = System.getProperty("dashboard.sourceResourcesDir");
        if (sourceResourcesDir != null && suite != null) {
            Path lengthsFile = ExpectedLengths.file(Paths.get(sourceResourcesDir), suite);
            ExpectedLengths.update(lengthsFile, allScenarios, capturedLengths);
            System.out.println("Captured expected lengths for " + capturedLengths.size()
                + " route(s) in " + lengthsFile);
        }

        System.out.println("Dashboard run summary:");
        System.out.println(" - tested: " + scenarios.size() + "/" + allScenarios.size());
        System.out.println(" - " + (suite != null ? "suite: " : "dataset: ") + dataset);

        long unreachable = runs.stream().filter(r -> !r.reached).count();
        if (unreachable > 0) {
            System.out.printf("%d unreachable target(s). See %s%n", unreachable, siteRoot.resolve("index.html"));
        }
    }

    /**
     * Worker loop: pull scenario indexes off the shared queue and run each
     * scenario. {@link Scenario#compile} builds a fresh client and
     * config per scenario on this thread; everything shared (the scenario list,
     * the results array, the writers and publisher) is either immutable or
     * written under an index unique to this scenario.
     */
    private void runScenarioWorker(
            List<Scenario> scenarios,
            PathfinderDashboardModels.RunRecord[] results,
            Map<String, Integer> capturedLengths,
            AtomicInteger nextIndex,
            AtomicInteger completed,
            String dataset,
            String bundleName,
            boolean profile,
            boolean heatmap) {
        int n = scenarios.size();
        for (int i = nextIndex.getAndIncrement(); i < n; i = nextIndex.getAndIncrement()) {
            Scenario scenario = scenarios.get(i);
            try {
                CompiledAccount applied = scenario.compile();

                // Default to the Grand Exchange bank when no explicit start is set (e.g. clue-step CSV rows)
                int start = scenario.getStartPoint() != WorldPointUtil.UNDEFINED
                    ? scenario.getStartPoint()
                    : WorldPointUtil.packWorldPoint(3185, 3436, 0);
                int end = scenario.getEndPoint();
                String category = scenario.getCategory() != null && !scenario.getCategory().isEmpty()
                    ? scenario.getCategory()
                    : "dashboard";

                PathfinderResult result;
                PathfinderProfile profileData = null;
                if (applied.getSettings().pathfinderBackend() == PathfinderBackend.EXACT) {
                    // ProfilingPathfinder instruments the legacy engine only;
                    // exact searches always record unprofiled.
                    ExactPathfinder exact = new ExactPathfinder(applied.getConfig(),
                        exactRoutingStatic(applied.getConfig()), start, Set.of(end), null);
                    exact.run();
                    result = exact.getResult();
                } else if (profile) {
                    ProfilingPathfinder profiler = new ProfilingPathfinder(
                        applied.getConfig(), start, Set.of(end), heatmap);
                    profiler.run();
                    result = profiler.getResult();
                    profileData = profiler.getProfile();
                } else {
                    Pathfinder pathfinder = new Pathfinder(applied.getConfig(), start, Set.of(end));
                    pathfinder.run();
                    result = pathfinder.getResult();
                }

                if (result == null) {
                    synchronized (HEARTBEAT_LOCK) {
                        System.out.printf("[%2d/%-2d] ✖ %s  NO_RESULT%n",
                            completed.incrementAndGet(), n, scenario.getName());
                    }
                    continue;
                }

                List<PathStep> path = result.getPathSteps();
                int pathLength = path.size();
                boolean reached = result.isReached();
                if (reached) {
                    capturedLengths.put(scenario.getName(), pathLength);
                }

                // Evaluate assertions — the reachability expectation takes
                // precedence over length assertions: an expect_reachable=true row
                // that fails to reach fails here, and an expect_reachable=false row
                // passes only when no path is found.
                Boolean assertionPassed = null;
                String assertionMessage = null;
                boolean expectedReachable = scenario.isExpectedReachable();
                OptionalInt expectedLength = scenario.getExpectedLength();
                OptionalInt minimumLength = scenario.getMinimumLength();
                if (reached != expectedReachable) {
                    assertionPassed = false;
                    assertionMessage = expectedReachable
                        ? "Expected reachable but no path found"
                        : "Expected unreachable but path found (" + pathLength + " steps)";
                } else if (!expectedReachable) {
                    assertionPassed = true;
                    assertionMessage = "Expected unreachable";
                } else if (expectedLength.isPresent()) {
                    int expected = expectedLength.getAsInt();
                    if (pathLength == expected) {
                        assertionPassed = true;
                    } else {
                        assertionPassed = false;
                        assertionMessage = "Expected path length " + expected + " but got " + pathLength;
                    }
                } else if (minimumLength.isPresent()) {
                    int minimum = minimumLength.getAsInt();
                    if (pathLength >= minimum) {
                        assertionPassed = true;
                    } else {
                        assertionPassed = false;
                        assertionMessage = "Expected minimum path length " + minimum + " but got " + pathLength;
                    }
                }

                List<String> details = List.of(
                    "Dataset: " + datasetLabel(dataset),
                    "Scenario: " + scenario.getName(),
                    "Preset: " + scenario.getProfile().name(),
                    "Expected reachable: " + expectedReachable);

                PathfinderDashboardModels.RunRecord run = reportWriter.createRunRecord(
                    scenario.getName(),
                    category,
                    details,
                    result,
                    applied.getConfig(),
                    reached,
                    assertionPassed,
                    assertionMessage);
                run.expectedReachable = expectedReachable;

                DashboardRunMetadata.apply(run, scenario.getProfile().name(), applied.getSettings(),
                    Profiles.lumbridgeDiaryElite(scenario.getProfile()));

                if (profileData != null) {
                    profilerReportWriter.populateProfilerData(run, profileData);
                }
                // The scenario index is a valid unique heatmap name — the
                // frontend resolves heatmaps via the recorded heatmapFile
                // path, not by run position.
                bundlePublisher.externalizeRunHeatmap(bundleName, i, run);
                results[i] = run;

                synchronized (HEARTBEAT_LOCK) {
                    System.out.printf("[%2d/%-2d] %s %s  %.0fms  %d steps%n",
                        completed.incrementAndGet(), n,
                        reached ? "✔" : "✖",
                        scenario.getName(),
                        result.getElapsedNanos() / 1_000_000.0,
                        pathLength);
                }
            } catch (Throwable t) {
                // A crashing scenario must still surface in report.json (the
                // sweep's only pass/fail surface — scan_report flags
                // assertionPassed: false) instead of aborting the whole run.
                synchronized (HEARTBEAT_LOCK) {
                    System.out.printf("[%d/%d] ✖ %s FAILED (%s)%n",
                        completed.incrementAndGet(), n, scenario.getName(), t);
                }
                PathfinderDashboardModels.RunRecord failure = new PathfinderDashboardModels.RunRecord();
                failure.name = scenario.getName();
                failure.category = scenario.getCategory() != null && !scenario.getCategory().isEmpty()
                    ? scenario.getCategory()
                    : "dashboard";
                failure.reached = false;
                failure.expectedReachable = scenario.isExpectedReachable();
                failure.assertionPassed = false;
                failure.assertionMessage = "Scenario threw " + t;
                results[i] = failure;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String datasetLabel(String dataset) {
        if (!dataset.contains("/") && !dataset.contains("\\")) {
            return dataset;
        }
        Path path = Paths.get(dataset);
        return path.getFileName() != null ? path.getFileName().toString() : dataset;
    }

    private static String formatBankEventsSummary(PathfinderDashboardModels.RunRecord run) {
        if (run.bankEvents == null || run.bankEvents.isEmpty()) {
            return "none";
        }
        return run.bankEvents.stream()
            .map(ev -> {
                String name = ev.bankName != null ? ev.bankName : "unknown";
                return name + " (step " + ev.stepIndex + ")";
            })
            .collect(Collectors.joining(", "));
    }
}
