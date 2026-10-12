package shortestpath.benchmark;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.route.RouteCli;
import shortestpath.scenarios.CanonicalCorpus;
import shortestpath.scenarios.Observation;
import shortestpath.scenarios.Route;
import shortestpath.scenarios.Scenario;
import shortestpath.scenarios.ScenarioRunner;
import shortestpath.scenarios.Suites;

public class BenchmarkMainTest {
    private static Path CORPUS;

    @BeforeClass
    public static void requireCorpus() {
        CORPUS = CanonicalCorpus.dir();
    }

    @Test
    public void rejectsUnknownRoute() throws Exception {
        Path manifest = manifest("unknown-route", "early", 1, 100000000L);
        expectFailure(manifest);
    }

    @Test
    public void rejectsUnknownProfile() throws Exception {
        Route route = firstRoute();
        Path manifest = manifest(route.getId(), "unknown", 1, 100000000L);
        expectFailure(manifest);
    }

    @Test
    public void rejectsDuplicateCase() throws Exception {
        Route route = firstRoute();
        boolean expectedReachable = expectedReachable(route, "early");
        Path manifest = writeManifest(List.of(caseJson(route, "early", 0, expectedReachable),
            caseJson(route, "early", 0, expectedReachable)), 1, false, 100000000L, false);
        expectFailure(manifest);
    }

    @Test
    public void rejectsCorpusArgument() throws Exception {
        // The routes are this checkout's corpus/, so a corpus from elsewhere can't run with these accounts.
        try {
            BenchmarkMain.main(new String[] {"--manifest", "m.json", "--corpus", "elsewhere", "--output", "o.json"});
            Assert.fail("expected --corpus to be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("--corpus"));
        }
    }

    @Test
    public void rejectsUnsupportedProtocolVersion() throws Exception {
        Route route = firstRoute();
        Path manifest = manifest(route.getId(), "early", 1, 100000000L);
        JsonObject json = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        json.addProperty("format_version", 99);
        Files.writeString(manifest, new Gson().toJson(json), StandardCharsets.UTF_8);
        expectFailure(manifest);
    }

    @Test
    public void executesOnlyExplicitCasesAndReportsJvmMetadata() throws Exception {
        Route route = firstRoute();
        boolean expectedReachable = expectedReachable(route, "early");
        Path manifest = writeManifest(List.of(caseJson(route, "early", 0, expectedReachable),
            caseJson(route, "early", 1, expectedReachable)), 2, false, 123456789L, true);
        Path output = Files.createTempFile("benchmark-main-result", ".json");
        Path progress = Files.createTempFile("benchmark-main-progress", ".jsonl");
        System.setProperty("benchmark.runId", "java-test-run");
        System.setProperty("benchmark.progressFile", progress.toString());
        try {
            BenchmarkMain.run(manifest, output);
        } finally {
            System.clearProperty("benchmark.runId");
            System.clearProperty("benchmark.progressFile");
        }
        JsonObject result = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
        Assert.assertEquals(1, result.get("protocol_version").getAsInt());
        Assert.assertEquals(2, result.getAsJsonArray("observations").size());
        Assert.assertTrue(result.getAsJsonObject("execution_metadata")
            .getAsJsonObject("runtime").get("java_version").getAsString().length() > 0);
        Assert.assertEquals(123456789L, result.getAsJsonObject("execution_metadata")
            .get("synthetic_benchmark_time").getAsLong());
        Assert.assertEquals(0, result.getAsJsonArray("observations").get(0)
            .getAsJsonObject().get("repetition").getAsInt());
        Assert.assertEquals(1, result.getAsJsonArray("observations").get(1)
            .getAsJsonObject().get("repetition").getAsInt());
        List<String> events = Files.readAllLines(progress);
        Assert.assertEquals(5, events.size());
        Assert.assertEquals("running", JsonParser.parseString(events.get(0)).getAsJsonObject()
            .get("phase").getAsString());
        Assert.assertEquals("case-complete", JsonParser.parseString(events.get(2)).getAsJsonObject()
            .get("type").getAsString());
    }

    /** The manifest's synthetic clock is the time cooldowns are checked at, whatever the profile's own clock. */
    @Test
    public void syntheticClockGovernsCooldowns() throws Exception {
        long clock = 123456789L;
        BenchmarkMain.Case current = new BenchmarkMain.Case(firstRoute(), "early", 0, true);
        Assert.assertEquals(clock, BenchmarkMain.compileAccount(current, clock, account -> { })
            .getConfig().evaluationTimeMinutes());
        // A minigame teleport used 10 minutes before the clock is on its 20-minute cooldown; a day later it isn't.
        Assert.assertFalse(hasFishingTrawlerTeleport(BenchmarkMain.compileAccount(current, clock,
            account -> account.minigameTeleportUsedAt(clock - 10))));
        Assert.assertTrue(hasFishingTrawlerTeleport(BenchmarkMain.compileAccount(current, clock + 24 * 60,
            account -> account.minigameTeleportUsedAt(clock - 10))));
    }

    private static boolean hasFishingTrawlerTeleport(shortestpath.profiles.CompiledAccount account) {
        return java.util.Arrays.stream(account.getConfig().getUsableTeleports(BankVisitState.CARRIED))
            .anyMatch(transport -> "Fishing Trawler Minigame Teleport".equals(transport.getDisplayInfo()));
    }

    @Test
    public void acceptsAStarProjectWithAStarAlgorithm() throws Exception {
        Path manifest = manifest(firstRoute().getId(), "early", 1, 100000000L);
        JsonObject json = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        json.addProperty("project", "shortest-path-astar");
        json.getAsJsonObject("policy").getAsJsonObject("adapter_args")
            .addProperty("algorithm", "astar");
        Files.writeString(manifest, new Gson().toJson(json), StandardCharsets.UTF_8);
        BenchmarkMain.Plan plan = BenchmarkMain.loadPlan(manifest);
        Assert.assertEquals("shortest-path-astar", plan.project);
        Assert.assertEquals("astar", plan.algorithm);
    }

    @Test
    public void aStarSessionDefaultsToColdAndIsValidated() throws Exception {
        Assert.assertEquals(ScenarioRunner.AStarSession.COLD, BenchmarkMain.loadPlan(aStarManifest(null, false)).aStarSession);
        Assert.assertEquals(ScenarioRunner.AStarSession.ACCOUNT, BenchmarkMain.loadPlan(aStarManifest("account", false)).aStarSession);
        Assert.assertEquals(ScenarioRunner.AStarSession.TARGET, BenchmarkMain.loadPlan(aStarManifest("target", false)).aStarSession);
        expectFailure(aStarManifest("warm", false));
        Path legacy = manifest(firstRoute().getId(), "early", 1, 100000000L);
        JsonObject json = JsonParser.parseString(Files.readString(legacy)).getAsJsonObject();
        json.getAsJsonObject("policy").getAsJsonObject("adapter_args").addProperty("astar_session", "target");
        Files.writeString(legacy, new Gson().toJson(json), StandardCharsets.UTF_8);
        expectFailure(legacy);
    }

    @Test
    public void aStarSessionModesPrepareWhatTheyDescribeWithAndWithoutWarmup() throws Exception {
        for (boolean warmup : new boolean[] {false, true}) {
            String label = warmup ? "with warmup" : "without warmup";
            JsonObject cold = onlyObservation(aStarManifest("cold", warmup));
            JsonObject account = onlyObservation(aStarManifest("account", warmup));
            JsonObject target = onlyObservation(aStarManifest("target", warmup));

            assertStages(label + ", cold", cold, false, false);
            assertStages(label + ", account", account, true, false);
            assertStages(label + ", target", target, true, true);
            Assert.assertEquals(label, 0, target.get("reverse_search_ns").getAsLong());
            Assert.assertEquals(label, cold.get("path_cost"), account.get("path_cost"));
            Assert.assertEquals(label, cold.get("path_cost"), target.get("path_cost"));
        }
    }

    /** Sessions stay per account and per target across a run with several of each. */
    @Test
    public void aStarSessionModesHoldAcrossTargetsAndAccounts() throws Exception {
        List<Route> routes = Route.load(CORPUS.resolve("corpus/routes-v1.json")).subList(0, 2);
        List<JsonObject> cases = new java.util.ArrayList<>();
        for (Route route : routes) {
            for (String profile : List.of("early", "maxed")) {
                cases.add(caseJson(route, profile, 0, expectedReachable(route, profile)));
            }
        }
        Map<String, JsonElement> coldCosts = new HashMap<>();
        for (String session : List.of("cold", "account", "target")) {
            Path manifest = writeManifest(cases, 1, false, 100000000L, true);
            JsonObject json = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
            json.addProperty("project", "shortest-path-astar");
            JsonObject adapterArgs = json.getAsJsonObject("policy").getAsJsonObject("adapter_args");
            adapterArgs.addProperty("algorithm", "astar");
            adapterArgs.addProperty("astar_session", session);
            Files.writeString(manifest, new Gson().toJson(json), StandardCharsets.UTF_8);
            for (JsonObject observation : observations(manifest)) {
                String key = observation.get("route_id").getAsString() + "/"
                    + observation.get("account_profile").getAsString();
                assertStages(session + " " + key, observation, !session.equals("cold"), session.equals("target"));
                if (session.equals("cold")) {
                    coldCosts.put(key, observation.get("path_cost"));
                } else {
                    Assert.assertEquals(session + " " + key, coldCosts.get(key), observation.get("path_cost"));
                }
            }
        }
        Assert.assertEquals(4, coldCosts.size());
    }

    private static void assertStages(String label, JsonObject observation, boolean graphReused, boolean targetReused) {
        Assert.assertEquals(label + ": graph_reused", graphReused, observation.get("graph_reused").getAsBoolean());
        Assert.assertEquals(label + ": target_reused", targetReused, observation.get("target_reused").getAsBoolean());
    }

    /**
     * A route with {@code allowTransports: false} (no canonical route has one) plans the same in every
     * frontend: the route CLI by name and by id, the canonical suite and this adapter.
     */
    @Test
    public void walkingOnlyRouteRunsTheSameInEveryFrontend() throws Exception {
        // transport-heavy-0001/maxed takes a fairy ring when transports are allowed
        Route withTransports = Route.load(CORPUS.resolve("corpus/routes-v1.json")).stream()
            .filter(route -> route.getId().equals("transport-heavy-0001")).findFirst().orElseThrow();
        JsonObject transported = routeCli("transport-heavy-0001", "maxed", "--json");
        Assert.assertTrue(transported.getAsJsonArray("transports").size() > 0);

        Path corpus = Files.createTempDirectory("walking-only-corpus");
        Files.createDirectories(corpus.resolve("corpus"));
        Files.writeString(corpus.resolve("corpus/routes-v1.json"), "[{\"id\": \"walking-only-0001\", "
            + "\"name\": \"Walking only\", \"start\": " + point(withTransports.getStart()) + ", \"target\": "
            + point(withTransports.getTarget()) + ", \"allowTransports\": false}]", StandardCharsets.UTF_8);
        String previous = System.getProperty("corpus.dir");
        System.setProperty("corpus.dir", corpus.toString());
        try {
            Scenario scenario = Suites.find("canonical", "walking-only-0001/maxed");
            Assert.assertFalse(scenario.isAllowTransports());
            Observation suite = new ScenarioRunner(ScenarioRunner.Backend.LEGACY).run(scenario);
            Assert.assertTrue(suite.isReached());

            JsonObject named = routeCli("--route", "walking-only-0001", "--profile", "maxed", "--json");
            JsonObject byId = routeCli("walking-only-0001", "maxed", "--json");
            Assert.assertFalse(named.get("allowTransports").getAsBoolean());
            assertOnlyWalkingTransports(named);
            Assert.assertEquals(named.toString(), byId.toString());
            Assert.assertEquals(suite.getCost().intValue(), named.get("cost").getAsInt());
            Assert.assertTrue(named.get("cost").getAsInt() > transported.get("cost").getAsInt());

            Path manifest = writeManifest(List.of(caseJson("walking-only-0001", "maxed", 0, true)), 1, false,
                100000000L, false);
            Assert.assertEquals(suite.getCost().intValue(), onlyObservation(manifest).get("path_cost").getAsInt());
        } finally {
            if (previous == null) {
                System.clearProperty("corpus.dir");
            } else {
                System.setProperty("corpus.dir", previous);
            }
        }
    }

    /** Doors, stairs and the like ({@code TRANSPORT}) are part of walking; nothing else is. */
    private static void assertOnlyWalkingTransports(JsonObject result) {
        for (var transport : result.getAsJsonArray("transports")) {
            Assert.assertEquals(transport.toString(), "TRANSPORT",
                transport.getAsJsonObject().get("type").getAsString());
        }
    }

    private static JsonObject routeCli(String... args) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8.name()));
            RouteCli.main(args);
        } finally {
            System.setOut(previous);
        }
        return JsonParser.parseString(new String(output.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String point(int packed) {
        return "[" + WorldPointUtil.unpackWorldX(packed) + ", " + WorldPointUtil.unpackWorldY(packed) + ", "
            + WorldPointUtil.unpackWorldPlane(packed) + "]";
    }

    private static Path aStarManifest(String session, boolean warmup) throws Exception {
        Route route = firstRoute();
        Path manifest = writeManifest(List.of(caseJson(route, "early", 0, expectedReachable(route, "early"))),
            1, warmup, 100000000L, true);
        JsonObject json = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        json.addProperty("project", "shortest-path-astar");
        JsonObject adapterArgs = json.getAsJsonObject("policy").getAsJsonObject("adapter_args");
        adapterArgs.addProperty("algorithm", "astar");
        if (session != null) {
            adapterArgs.addProperty("astar_session", session);
        }
        Files.writeString(manifest, new Gson().toJson(json), StandardCharsets.UTF_8);
        return manifest;
    }

    private static JsonObject onlyObservation(Path manifest) throws Exception {
        List<JsonObject> observations = observations(manifest);
        Assert.assertEquals(1, observations.size());
        return observations.get(0);
    }

    private static List<JsonObject> observations(Path manifest) throws Exception {
        Path output = Files.createTempFile("benchmark-main-result", ".json");
        System.setProperty("benchmark.runId", "java-test-run");
        try {
            BenchmarkMain.run(manifest, output);
        } finally {
            System.clearProperty("benchmark.runId");
        }
        List<JsonObject> observations = new java.util.ArrayList<>();
        JsonParser.parseString(Files.readString(output)).getAsJsonObject().getAsJsonArray("observations")
            .forEach(observation -> observations.add(observation.getAsJsonObject()));
        return observations;
    }

    private static Route firstRoute() throws Exception {
        return Route.load(CORPUS.resolve("corpus/routes-v1.json")).get(0);
    }

    private static boolean expectedReachable(Route route, String profile) {
        return !route.getNegativeProfiles().contains(profile);
    }

    private static Path manifest(String routeId, String profile, int repetitions, long clock)
            throws Exception {
        Route route = firstRoute();
        boolean expectedReachable = profile.equals("early") && route.getId().equals(routeId)
            ? expectedReachable(route, profile) : false;
        return writeManifest(List.of(caseJson(routeId, profile, 0, expectedReachable)), repetitions,
            false, clock, false);
    }

    private static JsonObject caseJson(Route route, String profile, int repetition,
            boolean expectedReachable) {
        return caseJson(route.getId(), profile, repetition, expectedReachable);
    }

    private static JsonObject caseJson(String routeId, String profile, int repetition,
            boolean expectedReachable) {
        JsonObject result = new JsonObject();
        result.addProperty("route_id", routeId);
        result.addProperty("profile", profile);
        result.addProperty("repetition", repetition);
        result.addProperty("expected_reachable", expectedReachable);
        return result;
    }

    private static Path writeManifest(List<JsonObject> cases, int repetitions, boolean warmup,
            long clock, boolean diagnostic) throws Exception {
        JsonObject policy = new JsonObject();
        policy.addProperty("repetitions", repetitions);
        policy.addProperty("warmup", warmup);
        policy.addProperty("synthetic_benchmark_time", clock);
        JsonObject adapterArgs = new JsonObject();
        adapterArgs.addProperty("diagnostic", diagnostic);
        policy.add("adapter_args", adapterArgs);

        JsonObject result = new JsonObject();
        result.addProperty("format_version", 1);
        result.addProperty("project", "shortest-path");
        result.add("policy", policy);
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        cases.forEach(array::add);
        result.add("cases", array);
        Path path = Files.createTempFile("benchmark-manifest", ".json");
        Files.writeString(path, new Gson().toJson(result), StandardCharsets.UTF_8);
        return path;
    }

    private static void expectFailure(Path manifest) throws Exception {
        try {
            BenchmarkMain.loadPlan(manifest);
            Assert.fail("expected manifest rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
