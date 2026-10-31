package shortestpath.dashboard;


import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathfinderProfile;
import shortestpath.profiles.CompiledAccount;
import shortestpath.scenarios.ExpectedLengths;
import shortestpath.scenarios.Observation;
import shortestpath.scenarios.ScenarioRunner;
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
 * <p>A scenario may expect its route to be <em>unreachable</em> (an
 * intentional-failure scenario, {@code .expectUnreachable()} or a route's
 * {@code negativeProfiles}); the report records whether each run met its
 * expectation ({@code Observation.check()}).</p>
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
 *   <tr><td>{@code dashboard.suite}</td><td>{@code routes} (see {@link Suites});
 *       {@code all} runs every suite in {@link Suites#names()} inside this one JVM,
 *       publishing each suite's bundle as it finishes. Ignored when
 *       {@code dashboard.dataset} is set — a dataset file still wins.</td></tr>
 *   <tr><td>{@code dashboard.dataset}</td><td>unset; a route file
 *       ({@link shortestpath.scenarios.Route}) to run instead of a suite</td></tr>
 *   <tr><td>{@code dashboard.bundleName}</td><td>{@code routes}</td></tr>
 *   <tr><td>{@code dashboard.title}</td><td>{@code Dashboard}</td></tr>
 *   <tr><td>{@code dashboard.tier}</td><td>unset; run only scenarios tagged with this tier
 *       (the canonical suite's {@code smoke}, {@code standard}, {@code full}, {@code sailing})</td></tr>
 *   <tr><td>{@code dashboard.backend}</td><td>{@code legacy}; {@code astar} runs the A*
 *       pathfinder (unprofiled)</td></tr>
 *   <tr><td>{@code dashboard.filter}</td><td>unset; run only the scenarios whose name or
 *       category contains it (ignoring case)</td></tr>
 *   <tr><td>{@code dashboard.subtitle}</td><td>suite or file name</td></tr>
 *   <tr><td>{@code dashboard.profile}</td><td>auto — on for datasets up to
 *       {@value #PROFILE_AUTO_MAX_SCENARIOS} scenarios, off above; an explicit
 *       {@code true}/{@code false} always wins</td></tr>
 *   <tr><td>{@code dashboard.heatmap}</td><td>{@code true} (profiling only)</td></tr>
 *   <tr><td>{@code dashboard.threads}</td><td>{@code availableProcessors() - 3}</td></tr>
 *   <tr><td>{@code reachability.maxTargets}</td><td>{@code 10000}</td></tr>
 * </table>
 */
public class DashboardTest {

    static {
        ((Logger) LoggerFactory.getLogger("shortestpath.transport.Transport")).setLevel(Level.OFF);
    }

    private static final String SUITE_PROPERTY = "dashboard.suite";
    private static final String DATASET_PROPERTY = "dashboard.dataset";
    private static final String FILTER_PROPERTY = "dashboard.filter";
    private static final String TIER_PROPERTY = "dashboard.tier";
    private static final String BUNDLE_NAME_PROPERTY = DashboardBundlePublisher.BUNDLE_NAME_PROPERTY;
    private static final String DEFAULT_SUITE = "routes";
    /** {@code dashboard.suite} sentinel running every suite in {@link Suites#names()} in one JVM. */
    private static final String ALL_SUITES = "all";
    private static final int MAX_SCENARIOS = Integer.getInteger("reachability.maxTargets", 10000);
    /**
     * Profiling is auto-disabled above this scenario count unless
     * {@code dashboard.profile} is set explicitly — instrumented searches run
     * ~30–50x slower on heavy datasets, so large sweeps stay cheap while small
     * debugging datasets keep the profiler by default.
     */
    private static final int PROFILE_AUTO_MAX_SCENARIOS = 200;

    private final ProfilerReportWriter profilerReportWriter = new ProfilerReportWriter();
    private final ScenarioRunner runner = new ScenarioRunner(
        ScenarioRunner.Backend.parse(System.getProperty("dashboard.backend", "legacy")));
    private final PathfinderDashboardReportWriter reportWriter = new PathfinderDashboardReportWriter();
    private final DashboardBundlePublisher bundlePublisher = new DashboardBundlePublisher();

    /** Null unless the plugin checkout has the experimental sailing search (see SailingRouteRunner). */
    private final SailingRouteRunner sailingRunner = SailingRouteRunner.find();

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
        if (suite != null && ALL_SUITES.equalsIgnoreCase(suite)) {
            runAllSuites();
            return;
        }
        String dataset = suite != null ? suite : file;
        runDashboard(dataset, suite,
            System.getProperty(BUNDLE_NAME_PROPERTY, "routes"),
            System.getProperty("dashboard.title", "Dashboard"),
            System.getProperty("dashboard.subtitle", datasetLabel(dataset)),
            true);
    }

    /**
     * Multi-suite mode ({@code -PdashboardSuite=all}): run every registered suite inside
     * this JVM, in sorted order, publishing each suite's bundle the moment it finishes so
     * a crash mid-sweep still leaves the earlier reports on disk. The one
     * {@link ScenarioRunner} is shared across suites, so its memoized {@code RoutingStatic}
     * and prepared account sessions are built once instead of once per suite; results and
     * captured lengths stay per-suite.
     */
    private void runAllSuites() throws IOException, InterruptedException {
        List<String> suites = new ArrayList<>(new TreeSet<>(Suites.names()));
        // Build the A* static routing data up front rather than inside the first timed
        // run of the first suite (a no-op for the legacy backend).
        runner.prepare();
        int i = 0;
        for (String suite : suites) {
            i++;
            // Deliberately not an [i/N] heartbeat line — the verify progress hook passes
            // non-heartbeat lines through, and this boundary marker keeps the sweep's
            // per-suite tqdm resets legible.
            System.out.printf("--- suite %d/%d: %s%n", i, suites.size(), suite);
            // Bundle name and title replicate the dashboards.gradle bundleFor/titleFor
            // closures per suite; the global dashboard.bundleName/dashboard.title props
            // would carry the meaningless 'all' derivation in this mode.
            runDashboard(suite, suite, bundleNameFor(suite), titleFor(suite), datasetLabel(suite),
                false);
        }
    }

    /**
     * Runs one dataset (a suite, or a route file when {@code suite} is null) and publishes
     * its bundle. {@code standalone} is single-dataset mode: the global
     * {@code dashboard.seasonal}/{@code dashboard.f2p} props tag the bundle and
     * {@code dashboard.sourceResourcesDir} captures expected lengths. In multi-suite mode
     * both stay off — a global flag would tag every suite (the per-suite bundle-name
     * prefix supplies the flags instead) and the capture task never runs with
     * {@code dashboard.suite=all}.
     */
    private void runDashboard(String dataset, String suite, String bundleName,
            String reportTitle, String reportSubtitle, boolean standalone)
            throws IOException, InterruptedException {
        String profileProp = System.getProperty("dashboard.profile");
        Path siteRoot = bundlePublisher.getOutputRoot();

        List<Scenario> allScenarios = suite != null ? Suites.load(suite) : Suites.loadFile(Paths.get(dataset));
        String tier = System.getProperty(TIER_PROPERTY, "");
        if (!tier.isEmpty()) {
            allScenarios = Suites.withTier(allScenarios, tier);
        }
        String filter = System.getProperty(FILTER_PROPERTY, "");
        if (!filter.isEmpty()) {
            allScenarios = Suites.filter(allScenarios, filter);
        }
        List<Scenario> scenarios = allScenarios.subList(0, Math.min(MAX_SCENARIOS, allScenarios.size()));

        int n = scenarios.size();
        // An explicit dashboard.profile always wins; unset means auto — profile
        // only datasets small enough that the instrumentation stays cheap.
        // The profiler instruments the legacy search only.
        boolean profile = runner.getBackend() == ScenarioRunner.Backend.LEGACY && (profileProp != null
            ? Boolean.parseBoolean(profileProp)
            : n <= PROFILE_AUTO_MAX_SCENARIOS);
        // The per-tile heatmap rides on the profiler's visit counting; it only
        // exists when profiling is on, and it is the dominant profiling
        // allocation, so it gets its own off switch.
        boolean heatmap = profile
            && Boolean.parseBoolean(System.getProperty("dashboard.heatmap", "true"));

        int workers = resolveWorkerCount(n);
        // Deliberately not an [i/N] heartbeat line — maintenance.py's progress
        // hook consumes anything matching that shape.
        System.out.printf("Running %d scenario(s) on %d worker(s) [%s, %s]%n", n, workers,
            runner.getBackend().id(), profile ? (heatmap ? "profiled, heatmap" : "profiled") : "unprofiled");

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
        for (int i = 0; i < results.length; i++) {
            PathfinderDashboardModels.RunRecord r = results[i];
            if (r == null) {
                // A null slot means a worker died mid-scenario — the
                // pool.submit wrapper's catch (Throwable) only reached
                // stderr, losing every scenario still queued behind it.
                // Fill the slot with a counted, attributed failure so
                // the report can never read as complete coverage.
                Scenario scenario = scenarios.get(i);
                r = new PathfinderDashboardModels.RunRecord();
                r.name = scenario.getName();
                r.category = scenario.getCategory() != null && !scenario.getCategory().isEmpty()
                    ? scenario.getCategory()
                    : "dashboard";
                r.reached = false;
                r.expectedReachable = scenario.isExpectedReachable();
                r.assertionPassed = false;
                r.assertionMessage = "no result recorded — a worker thread terminated mid-scenario";
            }
            runs.add(r);
        }

        PathfinderDashboardModels.Report report = reportWriter.createReport(
            reportTitle,
            reportSubtitle,
            System.currentTimeMillis() - started,
            runs,
            reportWriter.createTransportLayerPointsAlwaysAvailable());
        // The scenario count the sweep scheduled — lets report.json
        // consumers (scan_report) verify coverage rather than trusting
        // totalRuns, which counts only the records that exist.
        report.summary.expectedRuns = n;
        report.bankNamesFromData = BankDestinationLabels.uniqueSortedBankNames();
        // Tag seasonal bundles so the frontend knows to draw the league
        // region overlay. We treat any bundle whose name starts with
        // "seasonal" as seasonal; this lets `-PdashboardBundle=seasonal-…`
        // and the default `seasonal-…` slugs both opt in without an
        // additional system property.
        report.seasonal = (standalone
                && Boolean.parseBoolean(System.getProperty("dashboard.seasonal", "false")))
            || (bundleName != null && bundleName.toLowerCase(java.util.Locale.ROOT).startsWith("seasonal"));
        // Same convention for F2P bundles: `f2p-…` slug or -PdashboardF2p=true.
        report.f2p = (standalone
                && Boolean.parseBoolean(System.getProperty("dashboard.f2p", "false")))
            || (bundleName != null && bundleName.toLowerCase(java.util.Locale.ROOT).startsWith("f2p"));

        bundlePublisher.publishBundle(bundleName, report);

        String sourceResourcesDir = System.getProperty("dashboard.sourceResourcesDir");
        if (sourceResourcesDir != null && suite != null && standalone) {
            Path lengthsFile = ExpectedLengths.file(Paths.get(sourceResourcesDir), suite);
            // The whole suite, not just the scenarios a tier or filter selected: the rest keep
            // their lengths.
            ExpectedLengths.update(lengthsFile, Suites.load(suite), capturedLengths);
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
                String category = scenario.getCategory() != null && !scenario.getCategory().isEmpty()
                    ? scenario.getCategory()
                    : "dashboard";

                Observation observation = runner.run(scenario, applied, !profile ? ScenarioRunner.Profiling.OFF
                    : heatmap ? ScenarioRunner.Profiling.HEATMAP : ScenarioRunner.Profiling.ON);
                PathfinderResult result = observation.getResult();
                PathfinderProfile profileData = observation.getProfile();

                if (result == null) {
                    // A null result must still surface in report.json
                    // (the sweep's only pass/fail surface) as a counted
                    // failure — dropping the slot would read as full
                    // coverage.
                    PathfinderDashboardModels.RunRecord failure = new PathfinderDashboardModels.RunRecord();
                    failure.name = scenario.getName();
                    failure.category = category;
                    failure.reached = false;
                    failure.expectedReachable = scenario.isExpectedReachable();
                    failure.assertionPassed = false;
                    failure.assertionMessage = "scenario produced no result";
                    results[i] = failure;
                    synchronized (HEARTBEAT_LOCK) {
                        System.out.printf("[%2d/%-2d] ✖ %s  NO_RESULT%n",
                            completed.incrementAndGet(), n, scenario.getName());
                    }
                    continue;
                }

                int pathLength = observation.getPathLength();
                boolean reached = observation.isReached();
                if (reached) {
                    capturedLengths.put(scenario.getName(), pathLength);
                }
                Observation.Assertion assertion = observation.check();
                Boolean assertionPassed = assertion.passed;
                String assertionMessage = assertion.message;
                boolean expectedReachable = scenario.isExpectedReachable();

                List<String> details = new ArrayList<>(List.of(
                    "Dataset: " + datasetLabel(dataset),
                    "Backend: " + runner.getBackend().id(),
                    "Scenario: " + scenario.getName()));
                if (scenario.getDescription() != null) {
                    details.add("Route: " + scenario.getDescription());
                }
                details.add("Preset: " + scenario.getProfile().name());
                details.add("Expected reachable: " + expectedReachable);
                // Benchmark harness: the cost and the A* phases, sailing included, for comparing runs
                if (reached) {
                    details.add("Cost: " + observation.getCost());
                }
                if (observation.getResult().getMessage() != null) {
                    details.add("Message: " + observation.getResult().getMessage());
                }
                if (observation.getAStar() != null) {
                    observation.getAStar().debugStats().forEach((label, value) -> details.add(label + ": " + value));
                    if (observation.getAStar().getAStarStats() != null) {
                        details.add("Popped: " + observation.getAStar().getAStarStats().statesPopped());
                    }
                    details.add("Total: " + observation.getTotalNanos());
                }

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
                    applied.getClient().getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE));

                if (profileData != null) {
                    profilerReportWriter.populateProfilerData(run, profileData);
                }
                // Sailing scenarios also run the sailing search, drawn next to the normal path
                String sailingSummary = "";
                if (scenario.getSailingSpeed().isPresent()) {
                    if (sailingRunner != null) {
                        run.sailing = sailingRunner.run(applied.getConfig(), scenario, result.getPathSteps());
                        sailingSummary = String.format("  | sailing %s %s: %s %d ticks, %d legs, %.0fms",
                            run.sailing.speed, run.sailing.boat.isEmpty() ? "(centre only)" : run.sailing.boat,
                            run.sailing.reached ? "\u2714" : "\u2716", run.sailing.ticks, run.sailing.legs,
                            run.sailing.elapsedNanos / 1_000_000.0);
                    } else {
                        sailingSummary = "  | sailing skipped: the plugin checkout has no sailing search (-PshortestPathDir)";
                    }
                }
                // The scenario index is a valid unique heatmap name — the
                // frontend resolves heatmaps via the recorded heatmapFile
                // path, not by run position.
                bundlePublisher.externalizeRunHeatmap(bundleName, i, run);
                results[i] = run;

                synchronized (HEARTBEAT_LOCK) {
                    System.out.printf("[%2d/%-2d] %s %s  %.0fms  %d steps%s%n",
                        completed.incrementAndGet(), n,
                        reached ? "✔" : "✖",
                        scenario.getName(),
                        result.getElapsedNanos() / 1_000_000.0,
                        pathLength,
                        sailingSummary);
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

    /**
     * The bundle name a single-suite Gradle run derives for {@code suite} — a port of
     * dashboards.gradle's {@code bundleFor} closure: the stem, lowercased with underscores
     * turned to dashes, then the backend slug, then {@code -profiled} when
     * {@code -PdashboardProfile=true} was passed explicitly.
     */
    private static String bundleNameFor(String suite) {
        String slug = sourceStem(suite).replace('_', '-').toLowerCase(Locale.ROOT);
        return slug + backendSlug() + (explicitlyProfiled() ? "-profiled" : "");
    }

    /**
     * The report title a single-suite Gradle run derives for {@code suite} — a port of
     * dashboards.gradle's {@code titleFor} closure: the stem tokens capitalized, then the
     * backend word, then {@code Profiled} when profiled was forced on.
     */
    private static String titleFor(String suite) {
        StringBuilder title = new StringBuilder();
        for (String token : sourceStem(suite).split("[_-]")) {
            if (token.isEmpty()) {
                continue;
            }
            if (title.length() > 0) {
                title.append(' ');
            }
            title.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        if (!backendSlug().isEmpty()) {
            String backend = System.getProperty("dashboard.backend", "legacy").toLowerCase(Locale.ROOT);
            title.append(' ').append(Character.toUpperCase(backend.charAt(0))).append(backend.substring(1));
        }
        if (explicitlyProfiled()) {
            title.append(" Profiled");
        }
        return title.toString();
    }

    /**
     * dashboards.gradle's {@code sourceStem}: the last path segment without its extension,
     * plus a {@code _<tier>} suffix when a tier is selected.
     */
    private static String sourceStem(String source) {
        String filename = source;
        int slash = filename.lastIndexOf('/');
        if (slash >= 0) {
            filename = filename.substring(slash + 1);
        }
        String stem = filename.contains(".")
            ? filename.substring(0, filename.lastIndexOf('.'))
            : filename;
        String tier = System.getProperty(TIER_PROPERTY, "");
        return tier.isEmpty() ? stem : stem + "_" + tier;
    }

    /** dashboards.gradle's {@code backendSlug}: empty for the legacy backend, else {@code -<backend>}. */
    private static String backendSlug() {
        String backend = System.getProperty("dashboard.backend", "legacy");
        return "legacy".equalsIgnoreCase(backend) ? "" : "-" + backend.toLowerCase(Locale.ROOT);
    }

    /**
     * dashboards.gradle's {@code profiled} flag — true only when
     * {@code -PdashboardProfile=true} was passed explicitly, so the per-suite auto
     * profiling decision never leaks into a bundle slug.
     */
    private static boolean explicitlyProfiled() {
        return "true".equals(System.getProperty("dashboard.profile"));
    }

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
