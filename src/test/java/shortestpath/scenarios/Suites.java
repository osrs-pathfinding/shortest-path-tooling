package shortestpath.scenarios;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Every committed scenario suite, by name. Suites with account or settings overrides are Java;
 * {@code clue_locations_full} is data ({@link ScenarioData}). Each suite's expected lengths are
 * in {@code /scenarios/expected-lengths/<suite>.json}.
 */
public final class Suites {
    private static final Map<String, Consumer<Suite>> SUITES = new LinkedHashMap<>();

    static {
        SUITES.put("routes", RouteScenarios::define);
        SUITES.put("unit-tests", UnitTestScenarios::define);
        SUITES.put("routing-issues", RoutingIssueScenarios::define);
        SUITES.put("collision-map-issues", CollisionMapIssueScenarios::define);
        SUITES.put("f2p_routes", F2pRouteScenarios::define);
        SUITES.put("seasonal_briefcase_routes", SeasonalBriefcaseScenarios::define);
        SUITES.put("quetzal_whistle_routes", QuetzalWhistleScenarios::define);
        SUITES.put("clue_locations_full", suite -> data(suite, "/scenarios/clue_locations_full.csv"));
    }

    private Suites() { }

    public static Set<String> names() {
        return Collections.unmodifiableSet(SUITES.keySet());
    }

    /** The scenarios of {@code suite} with their expected lengths. */
    public static List<Scenario> load(String suite) throws IOException {
        Consumer<Suite> definition = SUITES.get(suite);
        if (definition == null) {
            throw new IllegalArgumentException("unknown scenario suite '" + suite + "'; expected one of " + names());
        }
        Suite scenarios = new Suite();
        definition.accept(scenarios);
        return withLengths(build(suite, scenarios.scenarios()), ExpectedLengths.load(suite));
    }

    /** A data-only scenario file outside the committed suites; it has no expected lengths. */
    public static List<Scenario> loadFile(Path path) throws IOException {
        return build(path.toString(), ScenarioData.loadFile(path));
    }

    static List<Scenario> build(String suite, List<Scenario.Builder> builders) {
        List<Scenario> result = new ArrayList<>(builders.size());
        Set<String> names = new HashSet<>();
        for (Scenario.Builder builder : builders) {
            Scenario scenario = builder.build();
            if (!names.add(scenario.getName())) {
                throw new IllegalArgumentException("suite " + suite + " has two scenarios named '"
                    + scenario.getName() + "'");
            }
            result.add(scenario);
        }
        return result;
    }

    static List<Scenario> withLengths(List<Scenario> scenarios, Map<String, Integer> lengths) {
        List<Scenario> result = new ArrayList<>(scenarios.size());
        for (Scenario scenario : scenarios) {
            Integer length = lengths.get(scenario.getName());
            result.add(length == null ? scenario : scenario.withExpectedLength(OptionalInt.of(length)));
        }
        return result;
    }

    private static void data(Suite suite, String resource) {
        try {
            for (Scenario.Builder scenario : ScenarioData.loadResource(resource)) {
                suite.add(scenario);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
