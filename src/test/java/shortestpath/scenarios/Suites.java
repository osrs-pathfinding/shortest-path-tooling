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
import java.util.function.Supplier;

/**
 * Every committed scenario suite, by name. Suites with account or settings overrides are Java;
 * {@code clue_locations_full} is data ({@link ScenarioData}). Each suite's expected lengths are
 * in {@code /scenarios/expected-lengths/<suite>.json}.
 */
public final class Suites {
    private static final Map<String, Supplier<List<Scenario.Builder>>> SUITES = new LinkedHashMap<>();

    static {
        SUITES.put("routes", RouteScenarios::all);
        SUITES.put("unit-tests", UnitTestScenarios::all);
        SUITES.put("routing-issues", RoutingIssueScenarios::all);
        SUITES.put("collision-map-issues", CollisionMapIssueScenarios::all);
        SUITES.put("f2p_routes", F2pRouteScenarios::all);
        SUITES.put("seasonal_briefcase_routes", SeasonalBriefcaseScenarios::all);
        SUITES.put("quetzal_whistle_routes", QuetzalWhistleScenarios::all);
        SUITES.put("clue_locations_full", () -> data("/scenarios/clue_locations_full.csv"));
    }

    private Suites() { }

    public static Set<String> names() {
        return Collections.unmodifiableSet(SUITES.keySet());
    }

    /** The scenarios of {@code suite} with their expected lengths. */
    public static List<Scenario> load(String suite) throws IOException {
        Supplier<List<Scenario.Builder>> source = SUITES.get(suite);
        if (source == null) {
            throw new IllegalArgumentException("unknown scenario suite '" + suite + "'; expected one of " + names());
        }
        return withLengths(build(suite, source.get()), ExpectedLengths.load(suite));
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

    private static List<Scenario.Builder> data(String resource) {
        try {
            return ScenarioData.loadResource(resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
