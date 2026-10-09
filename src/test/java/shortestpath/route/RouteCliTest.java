package shortestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.scenarios.CanonicalScenarios;
import shortestpath.scenarios.Route;

public class RouteCliTest {
    @Test
    public void reportsWalkingTransportBankRestrictedAndUnreachableRoutes() throws Exception {
        JsonObject walking = query("gps-natural-0001", "early");
        assertTrue(walking.get("reachable").getAsBoolean());
        assertTrue(walking.getAsJsonArray("path").size() > 0);
        assertEquals(0, walking.getAsJsonArray("transports").size());

        JsonObject oneTransport = query("transport-heavy-0001", "maxed");
        assertEquals(1, oneTransport.getAsJsonArray("transports").size());
        assertEquals("FAIRY_RING", oneTransport.getAsJsonArray("transports")
            .get(0).getAsJsonObject().get("type").getAsString());

        JsonObject manyTransports = query("transport-heavy-0008", "maxed");
        assertTrue(manyTransports.getAsJsonArray("transports").size() > 1);
        assertIncreasingStepIndexes(manyTransports);
        assertTrue(manyTransports.getAsJsonArray("transports").toString().contains("TELEPORT"));

        JsonObject bankRoute = query("transport-heavy-0022", "maxed");
        assertTrue(bankRoute.getAsJsonArray("bankEvents").size() > 0);

        JsonObject restricted = query("quest-natural-0011", "early");
        assertFalse(restricted.get("reachable").getAsBoolean());

        JsonObject unreachable = query("gps-natural-0012", "early");
        assertFalse(unreachable.get("reachable").getAsBoolean());
        assertEquals(0, unreachable.getAsJsonArray("path").size());
        assertEquals(0, unreachable.getAsJsonArray("transports").size());
        assertEquals(0, unreachable.getAsJsonArray("bankEvents").size());
    }

    @Test
    public void suiteScenarioMatchesTheCanonicalRouteQuery() throws Exception {
        JsonObject positional = query("transport-heavy-0001", "maxed");
        JsonObject scenario = queryArgs("--suite", "canonical", "--scenario", "transport-heavy-0001/maxed", "--json");
        assertEquals("canonical", scenario.get("suite").getAsString());
        assertEquals("transport-heavy-0001/maxed", scenario.get("scenario").getAsString());
        assertEquals("maxed", scenario.get("profile").getAsString());
        assertEquals(positional.get("cost"), scenario.get("cost"));
        assertEquals(positional.get("path"), scenario.get("path"));
    }

    @Test
    public void runsAJavaSuiteScenarioByUniqueSubstring() throws Exception {
        JsonObject usable = queryArgs("--suite", "routing-issues", "--scenario", "#140) usable", "--json");
        assertEquals("Mage arena tele (#140) usable after guardian talk", usable.get("scenario").getAsString());
        assertEquals("UNIT_TEST", usable.get("profile").getAsString());
        assertTrue(usable.getAsJsonArray("transports").toString().contains("Mage Training Arena"));
        try {
            queryArgs("--suite", "routing-issues", "--scenario", "#140", "--json");
            fail("an ambiguous scenario query must list its matches");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("locked before guardian talk"));
        }
    }

    @Test
    public void acceptsPresetProfiles() throws Exception {
        JsonObject result = queryArgs("UNIT_TEST", "3222", "3218", "0", "3105", "3251", "0", "--json");
        assertEquals("UNIT_TEST", result.get("profile").getAsString());
        assertTrue(result.get("reachable").getAsBoolean());
    }

    @Test
    public void repeatedQueryIsStable() throws Exception {
        JsonObject first = query("transport-heavy-0022", "maxed");
        JsonObject second = query("transport-heavy-0022", "maxed");
        assertEquals(first.toString(), second.toString());
    }

    @Test
    public void namedRouteMatchesNamedCoordinatesAndSupportsRouteNames() throws Exception {
        Path corpus = CanonicalScenarios.defaultCorpusDir();
        Route route = Route.load(corpus.resolve("corpus/routes-v1.json")).stream()
            .filter(candidate -> candidate.getId().equals("transport-heavy-0001"))
            .findFirst().orElseThrow();
        JsonObject named = queryArgs("--corpus", corpus.toString(), "--route", route.getId(),
            "--profile", "maxed", "--json");
        JsonObject explicit = queryArgs("--corpus", corpus.toString(), "--start",
            point(route.getStart()), "--end", point(route.getTarget()), "--profile", "maxed", "--json");
        assertEquals(route.getId(), named.get("routeId").getAsString());
        assertEquals(route.getName(), queryArgs("--corpus", corpus.toString(), "--route",
            route.getName(), "--profile", "maxed", "--json").get("routeName").getAsString());
        for (String field : new String[] {"start", "target", "reachable", "cost", "path",
                "transports", "bankEvents"}) {
            assertEquals(field, explicit.get(field).toString(), named.get(field).toString());
        }
    }

    @Test
    public void rejectsUnknownOrAmbiguousNamedRoutes() throws Exception {
        Path corpus = CanonicalScenarios.defaultCorpusDir();
        try {
            RouteCli.run(new String[] {"--corpus", corpus.toString(), "--route",
                "regression-9999", "--profile", "maxed"});
            fail("unknown route should fail");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("Unknown corpus route: regression-9999"));
        }
        assertRejected("--route", "transport-heavy-0001", "--start", "2411,4434,0",
            "--end", "2995,3114,0", "--profile", "maxed");
        assertRejected("--start", "2411,4434,0", "--profile", "maxed");
        assertRejected("--end", "2995,3114,0", "--profile", "maxed");
        assertRejected("--route", "transport-heavy-0001", "--profile", "maxed",
            "--algorithm", "approximate");
    }

    private static JsonObject query(String route, String profile) throws Exception {
        return queryArgs(route, profile, "--json");
    }

    private static JsonObject queryArgs(String... args) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8.name()));
            RouteCli.run(withCorpus(args));
        } finally {
            System.setOut(previous);
        }
        return JsonParser.parseString(new String(output.toByteArray(), StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    private static void assertRejected(String... args) throws Exception {
        try {
            RouteCli.run(withCorpus(args));
            fail("invalid route source should fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static String point(int packed) {
        return WorldPointUtil.unpackWorldX(packed) + "," + WorldPointUtil.unpackWorldY(packed) + ","
            + WorldPointUtil.unpackWorldPlane(packed);
    }

    private static void assertIncreasingStepIndexes(JsonObject result) {
        int previous = -1;
        for (var value : result.getAsJsonArray("transports")) {
            int current = value.getAsJsonObject().get("stepIndex").getAsInt();
            assertTrue(current > previous);
            previous = current;
        }
    }

    private static String[] withCorpus(String... args) {
        String[] result = new String[args.length + 2];
        result[0] = "--corpus";
        result[1] = CanonicalScenarios.defaultCorpusDir().toString();
        System.arraycopy(args, 0, result, 2, args.length);
        return result;
    }
}
