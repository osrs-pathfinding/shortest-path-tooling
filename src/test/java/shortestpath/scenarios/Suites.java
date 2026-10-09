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
 * Every scenario suite, by name. Suites with account or settings overrides are Java;
 * {@code clue-locations-full} and {@code canonical} (the canonical corpus, tagged with its tiers;
 * {@link CanonicalScenarios}) are route data ({@link Route}). A suite's optional exact lengths are in
 * {@code /scenarios/expected-lengths/<suite>.json}.
 */
public final class Suites {
    private static final Map<String, Consumer<Suite>> SUITES = new LinkedHashMap<>();

    static {
        SUITES.put("routes", RouteScenarios::define);
        SUITES.put("unit-tests", UnitTestScenarios::define);
        SUITES.put("routing-issues", RoutingIssueScenarios::define);
        SUITES.put("collision-map-issues", CollisionMapIssueScenarios::define);
        SUITES.put("f2p-routes", F2pRouteScenarios::define);
        SUITES.put("seasonal-briefcase-routes", SeasonalBriefcaseScenarios::define);
        SUITES.put("quetzal-whistle-routes", QuetzalWhistleScenarios::define);
        SUITES.put("clue-locations-full", suite -> addRoutes(suite,
            unchecked(() -> Route.loadResource("/scenarios/clue-locations.json")), List.of("ALL")));
        SUITES.put("canonical", suite -> unchecked(() -> {
            CanonicalScenarios.define(suite, CanonicalScenarios.defaultCorpusDir());
            return null;
        }));
    }

    private Suites() { }

    /** Prints every suite name, one per line: {@code ./gradlew -q scenarioSuites}. */
    public static void main(String[] args) {
        names().forEach(System.out::println);
    }

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

    /**
     * The scenario of {@code suite} called {@code query}, or else the only one whose name contains
     * it (ignoring case).
     */
    public static Scenario find(String suite, String query) throws IOException {
        List<Scenario> scenarios = load(suite);
        for (Scenario scenario : scenarios) {
            if (scenario.getName().equals(query)) {
                return scenario;
            }
        }
        List<Scenario> matches = filter(scenarios, query);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("no scenario in " + suite + " matches '" + query + "'");
        }
        StringBuilder message = new StringBuilder(matches.size() + " scenarios in " + suite
            + " match '" + query + "':");
        matches.stream().limit(20).forEach(match -> message.append("\n  ").append(match.getName()));
        throw new IllegalArgumentException(message.toString());
    }

    /** The scenarios whose name or category contains {@code query}, ignoring case. */
    public static List<Scenario> filter(List<Scenario> scenarios, String query) {
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        List<Scenario> result = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            if (scenario.getName().toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || scenario.getCategory().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                result.add(scenario);
            }
        }
        return result;
    }

    /**
     * A route file outside the committed suites (the {@link Route} format), run with
     * {@code ALL} unless a route lists its profiles; it has no expected lengths.
     */
    public static List<Scenario> loadFile(Path path) throws IOException {
        Suite suite = new Suite();
        addRoutes(suite, Route.load(path), List.of("ALL"));
        return build(path.toString(), suite.scenarios());
    }

    /** Adds each route with each of its profiles (the route's own, else {@code profiles}). */
    static void addRoutes(Suite suite, List<Route> routes, List<String> profiles) {
        for (Route route : routes) {
            for (String profile : route.getProfiles() != null ? route.getProfiles() : profiles) {
                suite.add(route.scenario(profile));
            }
        }
    }

    /** The scenarios tagged with {@code tier}. */
    public static List<Scenario> withTier(List<Scenario> scenarios, String tier) {
        List<Scenario> result = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            if (scenario.getTiers().contains(tier)) {
                result.add(scenario);
            }
        }
        return result;
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

    private interface IoSupplier<T> {
        T get() throws IOException;
    }

    private static <T> T unchecked(IoSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
