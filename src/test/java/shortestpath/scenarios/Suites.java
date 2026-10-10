package shortestpath.scenarios;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import shortestpath.profiles.Profiles;

/**
 * Every scenario suite, by name. Suites with account or settings overrides are Java;
 * {@code clue-locations-full} ({@link ClueLocationScenarios}, with item overrides for a few steps)
 * and {@code canonical} ({@link CanonicalCorpus}) are route data ({@link Route}) run with a list of
 * profiles. A suite's optional exact lengths are in
 * {@code /scenarios/expected-lengths/<suite>.json}.
 */
public final class Suites {
    /** Adds a suite's scenarios; data suites read their route files. */
    private interface Definition {
        void define(Suite suite) throws IOException;
    }

    private static final Map<String, Definition> SUITES = new LinkedHashMap<>();

    static {
        SUITES.put("routes", RouteScenarios::define);
        SUITES.put("unit-tests", UnitTestScenarios::define);
        SUITES.put("routing-issues", RoutingIssueScenarios::define);
        SUITES.put("collision-map-issues", CollisionMapIssueScenarios::define);
        SUITES.put("f2p-routes", F2pRouteScenarios::define);
        SUITES.put("seasonal-briefcase-routes", SeasonalBriefcaseScenarios::define);
        SUITES.put("quetzal-whistle-routes", QuetzalWhistleScenarios::define);
        SUITES.put("clue-locations-full", ClueLocationScenarios::define);
        SUITES.put("canonical", suite -> addRoutes(suite,
            CanonicalCorpus.routes(CanonicalCorpus.dir()), List.copyOf(Profiles.canonicalNames())));
    }

    private Suites() { }

    /**
     * Prints the scenario index as JSON, {@code {suite: [{name, category, profile, tiers,
     * description}, ...]}}: {@code ./gradlew -q scenarioIndex}. Scripts read suites from it.
     */
    public static void main(String[] args) throws IOException {
        JsonObject index = new JsonObject();
        for (String suite : names()) {
            JsonArray scenarios = new JsonArray();
            for (Scenario scenario : load(suite)) {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", scenario.getName());
                entry.addProperty("category", scenario.getCategory());
                entry.addProperty("profile", scenario.getProfile().name());
                JsonArray tiers = new JsonArray();
                scenario.getTiers().stream().sorted().forEach(tiers::add);
                entry.add("tiers", tiers);
                entry.addProperty("description", scenario.getDescription());
                scenarios.add(entry);
            }
            index.add(suite, scenarios);
        }
        System.out.println(new GsonBuilder().disableHtmlEscaping().create().toJson(index));
    }

    public static Set<String> names() {
        return Collections.unmodifiableSet(SUITES.keySet());
    }

    /** The scenarios of {@code suite} with their expected lengths. */
    public static List<Scenario> load(String suite) throws IOException {
        Definition definition = SUITES.get(suite);
        if (definition == null) {
            throw new IllegalArgumentException("unknown scenario suite '" + suite + "'; expected one of " + names());
        }
        Suite scenarios = new Suite();
        definition.define(scenarios);
        Map<String, Integer> lengths = ExpectedLengths.load(suite);
        for (Scenario.Builder scenario : scenarios.scenarios()) {
            Integer length = lengths.get(scenario.name());
            if (length != null) {
                scenario.expectedLength(length);
            }
        }
        return build(suite, scenarios.scenarios());
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
        String needle = query.toLowerCase(Locale.ROOT);
        List<Scenario> result = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            if (scenario.getName().toLowerCase(Locale.ROOT).contains(needle)
                    || scenario.getCategory().toLowerCase(Locale.ROOT).contains(needle)) {
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
}
