package shortestpath.route;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.ExactPathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.profiles.CompiledAccount;
import shortestpath.profiles.Profiles;
import shortestpath.scenarios.CanonicalCorpus;
import shortestpath.scenarios.Observation;
import shortestpath.scenarios.Route;
import shortestpath.scenarios.Scenario;
import shortestpath.scenarios.ScenarioRunner;
import shortestpath.scenarios.Suites;
import shortestpath.pathfinder.exact.ExactForwardSearch;
import shortestpath.transport.Transport;

/** Command-line frontend for querying one route: a canonical route, coordinates, or any suite scenario. */
public final class RouteCli {
    private RouteCli() { }

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] rawArgs) throws Exception {
        Arguments arguments = Arguments.parse(rawArgs);
        if (arguments.corpus != null) {
            // The canonical suites read the corpus the query names.
            System.setProperty("corpus.dir", arguments.corpus.toString());
        }
        if (arguments.suite != null) {
            runSuite(arguments);
            return;
        }
        List<Route> routes = CanonicalCorpus.routes(arguments.corpus);
        ScenarioRunner runner = new ScenarioRunner(ScenarioRunner.Backend.parse(arguments.algorithm));

        if (arguments.routeIds != null) {
            for (int i = 0; i < arguments.routeIds.size(); i++) {
                String routeId = arguments.routeIds.get(i);
                Route route = routes.stream()
                    .filter(candidate -> candidate.getId().equals(routeId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown route: " + routeId));
                String profile = arguments.profiles.get(i);
                checkProfile(profile);
                query(route.scenario(profile).build(), route, null, profile, arguments, runner);
            }
            return;
        }

        if (arguments.routeSelector != null) {
            Route route = findRoute(routes, arguments.routeSelector);
            checkProfile(arguments.profile);
            // The named-route form has always allowed transports.
            query(route.scenario(arguments.profile).allowTransports(true).build(), route, null,
                arguments.profile, arguments, runner);
            return;
        }

        checkProfile(arguments.profile);
        Scenario query = Scenario.scenario("query", "query")
            .fromTile(arguments.start).toTile(arguments.target).profile(Profiles.get(arguments.profile)).build();
        query(query, null, null, arguments.profile, arguments, runner);
    }

    /** {@code --suite S --list} lists its scenarios; {@code --suite S --scenario Q} runs one. */
    private static void runSuite(Arguments arguments) throws Exception {
        if (arguments.list) {
            List<Scenario> scenarios = Suites.load(arguments.suite);
            if (arguments.json) {
                JsonArray rows = new JsonArray();
                for (Scenario scenario : scenarios) {
                    JsonObject row = new JsonObject();
                    row.addProperty("name", scenario.getName());
                    row.addProperty("category", scenario.getCategory());
                    row.addProperty("profile", scenario.getProfile().name());
                    row.addProperty("description", scenario.getDescription());
                    rows.add(row);
                }
                System.out.println(new GsonBuilder().serializeNulls().create().toJson(rows));
            } else {
                for (Scenario scenario : scenarios) {
                    System.out.println(scenario.getName() + "\t" + scenario.getCategory());
                }
            }
            return;
        }
        Scenario scenario = Suites.find(arguments.suite, arguments.scenario);
        query(scenario, null, arguments.suite, scenario.getProfile().name(), arguments,
            new ScenarioRunner(ScenarioRunner.Backend.parse(arguments.algorithm)));
    }

    private static Route findRoute(List<Route> routes, String selector) {
        return routes.stream()
            .filter(route -> route.getId().equals(selector) || route.getName().equals(selector))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown corpus route: " + selector));
    }

    private static void checkProfile(String profileName) {
        try {
            Profiles.get(profileName);
        } catch (IllegalArgumentException unknown) {
            List<String> names = new ArrayList<>(Profiles.canonicalNames());
            names.addAll(Profiles.presetNames());
            throw new IllegalArgumentException("unknown profile \"" + profileName + "\"; expected "
                + String.join(", ", names));
        }
    }

    /**
     * Runs {@code scenario} and prints it. {@code route} is the corpus route of the route forms;
     * {@code suite} names the suite of the {@code --suite} form; both are {@code null} for coordinates.
     */
    private static void query(Scenario scenario, Route route, String suite, String profileName,
            Arguments arguments, ScenarioRunner runner) {
        Observation observation = runner.run(scenario);
        CompiledAccount account = observation.getAccount();
        ExactPathfinder exact = observation.getExact();
        PathfinderResult result = observation.getResult();
        int start = scenario.getRouteStart();
        int target = scenario.getTarget();
        Scenario suiteScenario = suite != null ? scenario : null;
        List<MatchedTransport> transports = observation.isReached()
            ? transports(result.getPathSteps(), account.getConfig()) : List.of();
        if (arguments.json) {
            System.out.println(new GsonBuilder().serializeNulls().create().toJson(
                jsonResult(start, target, route, suite, suiteScenario, profileName, arguments.algorithm,
                    observation, transports)));
        } else {
            printHuman(start, target, route, suite, suiteScenario, profileName, arguments.algorithm, observation,
                transports, arguments.counters, exact);
        }
    }

    private static void printHuman(int start, int target, Route route, String suite, Scenario scenario,
            String profile, String algorithm, Observation observation, List<MatchedTransport> transports,
            boolean counters, ExactPathfinder exact) {
        PathfinderResult result = observation.getResult();
        System.out.println("algorithm: " + algorithm);
        System.out.println("profile: " + profile);
        if (scenario != null) {
            System.out.println("suite: " + suite);
            System.out.println("scenario: " + scenario.getName());
            if (scenario.getDescription() != null) {
                System.out.println("name: " + scenario.getDescription());
            }
            System.out.println("expected: " + (scenario.isExpectedReachable() ? "reachable" : "unreachable"));
        }
        if (route != null) {
            System.out.println("route: " + route.getId());
            System.out.println("name: " + route.getName());
        }
        System.out.println("start: " + coordinate(start));
        System.out.println("target: " + coordinate(target));
        if (!observation.isReached()) {
            System.out.println("unreachable");
        } else {
            System.out.println("cost: " + observation.getCost());
            System.out.println("expanded nodes: " + result.getNodesChecked());
            System.out.println("route:");
            List<PathStep> path = result.getPathSteps();
            System.out.println("  start " + coordinate(path.isEmpty() ? start : path.get(0).getPackedPosition(),
                !path.isEmpty() && path.get(0).isBankVisited()));
            for (int i = 1; i < path.size(); i++) {
                PathStep from = path.get(i - 1);
                PathStep to = path.get(i);
                MatchedTransport transport = findAt(transports, i);
                if (to.isBankVisited() && !from.isBankVisited()) {
                    System.out.println("  bank-state -> " + coordinate(to.getPackedPosition(), true));
                }
                if (transport == null) {
                    System.out.println("  walk -> " + coordinate(to.getPackedPosition(), to.isBankVisited()));
                } else {
                    String label = transport.label();
                    String details = transport.type + (label.equals(transport.type) ? "" : " \"" + label + "\"");
                    System.out.println("  transport " + details + " "
                        + coordinate(transport.origin) + " -> "
                        + coordinate(transport.destination, to.isBankVisited()));
                }
            }
            System.out.println("  final destination: " + coordinate(target));
        }
        if (counters) {
            System.out.println("metrics:");
            System.out.println("  route_cost: " + (observation.isReached() ? observation.getCost() : "unreachable"));
            System.out.println("  route_steps: " + result.getPathSteps().size());
            System.out.println("  nodes_expanded: " + result.getNodesChecked());
            System.out.println("  transports_checked: " + result.getTransportsChecked());
            System.out.println("  search_ms: " + result.getElapsedNanos() / 1_000_000.0);
            if (exact != null) {
                ExactForwardSearch.Counters stats = exact.getExactStats();
                System.out.println("  forward_search_ms: " + exact.getForwardSearchNanos() / 1_000_000.0);
                System.out.println("  pq_pushes: " + stats.pqPushes());
                System.out.println("  walking_pq_pushes: " + stats.walkingPqPushes());
                System.out.println("  local_transport_pq_pushes: " + stats.localTransportPqPushes());
                System.out.println("  global_pq_pushes: " + stats.globalPqPushes());
                System.out.println("  banking_pq_pushes: " + stats.bankingPqPushes());
                System.out.println("  stale_pops: " + stats.staleEntries());
                System.out.println("  max_queue_size: " + stats.maxQueueSize());
                System.out.println("  transport_candidates: " + stats.transportCandidates());
                System.out.println("  transport_relaxations: " + stats.transportRelaxations());
                System.out.println("  successful_transport_relaxations: "
                    + stats.successfulTransportRelaxations());
                System.out.println("  initial_capability: " + stats.initialCapability());
                System.out.println("  normal_heuristic_enabled_at_start: "
                    + stats.normalHeuristicEnabledAtStart());
                System.out.println("  restricted_heuristic_states: " + stats.restrictedHeuristicStates());
                System.out.println("  restricted_heuristic_zeroes: " + stats.restrictedHeuristicZeroes());
                System.out.println("  normal_heuristic_states: " + stats.normalHeuristicStates());
            }
        }
    }

    private static JsonObject jsonResult(int start, int target, Route route, String suite,
            Scenario scenario, String profile, String algorithm, Observation observation,
            List<MatchedTransport> transports) {
        PathfinderResult result = observation.getResult();
        JsonObject output = new JsonObject();
        output.addProperty("ok", true);
        if (scenario != null) {
            output.addProperty("suite", suite);
            output.addProperty("scenario", scenario.getName());
            output.addProperty("scenarioDescription", scenario.getDescription());
            output.addProperty("allowTransports", scenario.isAllowTransports());
            output.addProperty("expectedReachable", scenario.isExpectedReachable());
        }
        if (route != null) {
            output.addProperty("routeId", route.getId());
            output.addProperty("routeName", route.getName());
            output.addProperty("allowTransports", route.isAllowTransports());
        }
        output.addProperty("profile", profile);
        output.addProperty("algorithm", algorithm);
        output.add("start", point(start, false));
        output.add("target", point(target, false));
        output.addProperty("reachable", observation.isReached());
        output.addProperty("reached", observation.isReached());
        Integer cost = observation.getCost();
        if (cost != null) {
            output.addProperty("cost", cost);
        } else {
            output.add("cost", JsonNull.INSTANCE);
        }
        output.addProperty("expandedNodes", result.getNodesChecked());
        output.addProperty("nodesChecked", result.getNodesChecked());
        output.addProperty("transportsChecked", result.getTransportsChecked());
        output.addProperty("terminationReason", result.getTerminationReason().name());

        JsonArray path = new JsonArray();
        List<PathStep> steps = observation.isReached() ? result.getPathSteps() : List.of();
        for (int i = 1; i < steps.size(); i++) {
            PathStep step = steps.get(i);
            MatchedTransport transport = findAt(transports, i);
            JsonObject value = new JsonObject();
            value.addProperty("kind", transport == null ? "walk" : "transport");
            value.addProperty("coordinate", coordinate(step.getPackedPosition()));
            value.addProperty("banked", step.isBankVisited());
            if (transport != null) {
                value.addProperty("label", transport.label());
                value.addProperty("type", transport.type);
                value.add("origin", point(transport.origin, false));
                value.add("destination", point(transport.destination, step.isBankVisited()));
            }
            path.add(value);
        }
        output.add("path", path);

        JsonArray transportValues = new JsonArray();
        for (MatchedTransport transport : transports) {
            JsonObject value = new JsonObject();
            value.addProperty("stepIndex", transport.stepIndex);
            value.addProperty("type", transport.type);
            value.addProperty("name", transport.label());
            value.addProperty("displayInfo", transport.transport.getDisplayInfo());
            value.addProperty("objectInfo", transport.transport.getObjectInfo());
            value.add("origin", point(transport.origin, false));
            value.add("destination", point(transport.destination, transport.banked));
            value.addProperty("banked", transport.banked);
            transportValues.add(value);
        }
        output.add("transports", transportValues);

        JsonArray bankEvents = new JsonArray();
        for (int index = 0; index < steps.size(); index++) {
            PathStep step = steps.get(index);
            if (step.isBankVisited() && (index == 0 || !steps.get(index - 1).isBankVisited())) {
                JsonObject event = new JsonObject();
                event.addProperty("stepIndex", index);
                event.add("location", point(step.getPackedPosition(), true));
                bankEvents.add(event);
            }
        }
        output.add("bankEvents", bankEvents);
        return output;
    }

    private static List<MatchedTransport> transports(List<PathStep> path, PathfinderConfig config) {
        List<MatchedTransport> result = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            Transport transport = transportFor(path.get(i - 1), path.get(i), config);
            if (transport != null) {
                result.add(new MatchedTransport(i, path.get(i - 1).getPackedPosition(),
                    path.get(i).getPackedPosition(), path.get(i).isBankVisited(), transport));
            }
        }
        return result;
    }

    private static Transport transportFor(PathStep from, PathStep to, PathfinderConfig config) {
        boolean banked = to.isBankVisited();
        Transport[] local = config.getTransportsPacked(banked)
            .getOrDefault(from.getPackedPosition(), TransportAvailability.EMPTY_TRANSPORTS);
        List<Transport> candidates = new ArrayList<>();
        for (Transport transport : local) {
            if (transport.getDestination() == to.getPackedPosition()) candidates.add(transport);
        }
        if (!candidates.isEmpty()) return candidates.stream().min(TRANSPORT_ORDER).orElse(null);

        boolean walking = WorldPointUtil.unpackWorldPlane(from.getPackedPosition())
            == WorldPointUtil.unpackWorldPlane(to.getPackedPosition())
            && WorldPointUtil.distanceBetween2D(from.getPackedPosition(), to.getPackedPosition()) <= 1;
        if (!walking) {
            for (Transport transport : config.getUsableTeleports(banked)) {
                if (transport.getDestination() == to.getPackedPosition()) candidates.add(transport);
            }
        }
        return candidates.stream().min(TRANSPORT_ORDER).orElse(null);
    }

    private static final Comparator<Transport> TRANSPORT_ORDER = Comparator
        .comparing((Transport transport) -> transport.getType() == null ? "TRANSPORT" : transport.getType().name())
        .thenComparing(transport -> transport.getDisplayInfo() == null ? "" : transport.getDisplayInfo())
        .thenComparing(transport -> transport.getObjectInfo() == null ? "" : transport.getObjectInfo());

    private static MatchedTransport findAt(List<MatchedTransport> transports, int stepIndex) {
        for (MatchedTransport transport : transports) {
            if (transport.stepIndex == stepIndex) return transport;
        }
        return null;
    }

    private static JsonObject point(int packed, boolean banked) {
        JsonObject point = new JsonObject();
        point.addProperty("x", WorldPointUtil.unpackWorldX(packed));
        point.addProperty("y", WorldPointUtil.unpackWorldY(packed));
        point.addProperty("plane", WorldPointUtil.unpackWorldPlane(packed));
        point.addProperty("banked", banked);
        return point;
    }

    private static String coordinate(int packed) {
        return coordinate(packed, false);
    }

    private static String coordinate(int packed, boolean banked) {
        String value = WorldPointUtil.unpackWorldX(packed) + "/"
            + WorldPointUtil.unpackWorldY(packed) + "/"
            + WorldPointUtil.unpackWorldPlane(packed);
        return banked ? value + " [banked]" : value;
    }

    private static final class MatchedTransport {
        final int stepIndex;
        final int origin;
        final int destination;
        final boolean banked;
        final Transport transport;
        final String type;

        MatchedTransport(int stepIndex, int origin, int destination, boolean banked,
                Transport transport) {
            this.stepIndex = stepIndex;
            this.origin = origin;
            this.destination = destination;
            this.banked = banked;
            this.transport = transport;
            this.type = transport.getType() == null ? "TRANSPORT" : transport.getType().name();
        }

        String label() {
            if (transport.getDisplayInfo() != null && !transport.getDisplayInfo().isEmpty()) {
                return transport.getDisplayInfo();
            }
            if (transport.getObjectInfo() != null && !transport.getObjectInfo().isEmpty()) {
                return transport.getObjectInfo();
            }
            return type;
        }
    }

    private static final class Arguments {
        final List<String> routeIds;
        final List<String> profiles;
        final String routeSelector;
        final String profile;
        final int start;
        final int target;
        final boolean counters;
        final boolean json;
        final Path corpus;
        final String algorithm;
        String suite;
        String scenario;
        boolean list;

        private Arguments(List<String> routeIds, List<String> profiles, String routeSelector,
                String profile, int start, int target,
                boolean counters, boolean json, Path corpus, String algorithm) {
            this.routeIds = routeIds;
            this.profiles = profiles;
            this.routeSelector = routeSelector;
            this.profile = profile;
            this.start = start;
            this.target = target;
            this.counters = counters;
            this.json = json;
            this.corpus = corpus;
            this.algorithm = algorithm;
        }

        static Arguments parse(String[] rawArgs) {
            List<String> positional = new ArrayList<>();
            String profile = null;
            String routeSelector = null;
            String startText = null;
            String targetText = null;
            boolean counters = false;
            boolean json = false;
            String algorithm = "legacy";
            Path corpus = null;
            String suite = null;
            String scenario = null;
            boolean list = false;
            for (int i = 0; i < rawArgs.length; i++) {
                String argument = rawArgs[i];
                if (i == 0 && argument.equals("route")) continue;
                if (argument.equals("--counters")) {
                    counters = true;
                } else if (argument.equals("--json")) {
                    json = true;
                } else if (argument.equals("--corpus")) {
                    if (++i >= rawArgs.length) throw usage();
                    corpus = Path.of(rawArgs[i]);
                } else if (argument.equals("--suite")) {
                    if (++i >= rawArgs.length) throw usage();
                    suite = rawArgs[i];
                } else if (argument.equals("--scenario")) {
                    if (++i >= rawArgs.length) throw usage();
                    scenario = rawArgs[i];
                } else if (argument.equals("--list")) {
                    list = true;
                } else if (argument.equals("--algorithm")) {
                    if (++i >= rawArgs.length) throw usage();
                    algorithm = rawArgs[i];
                } else if (argument.equals("--profile")) {
                    if (++i >= rawArgs.length) throw usage();
                    profile = rawArgs[i];
                } else if (argument.equals("--route")) {
                    if (++i >= rawArgs.length) throw usage();
                    routeSelector = rawArgs[i];
                } else if (argument.equals("--start")) {
                    if (++i >= rawArgs.length) throw usage();
                    startText = rawArgs[i];
                } else if (argument.equals("--end")) {
                    if (++i >= rawArgs.length) throw usage();
                    targetText = rawArgs[i];
                } else {
                    positional.add(argument);
                }
            }
            if (!algorithm.equals("legacy") && !algorithm.equals("exact")) {
                throw new IllegalArgumentException("algorithm must be legacy or exact");
            }
            if (suite != null || scenario != null || list) {
                boolean other = !positional.isEmpty() || profile != null || routeSelector != null
                    || startText != null || targetText != null;
                if (suite == null || other || (scenario == null) == !list || (scenario != null && list)) {
                    throw usage();
                }
                Arguments arguments = new Arguments(null, null, null, null, 0, 0, counters, json, corpus,
                    algorithm);
                arguments.suite = suite;
                arguments.scenario = scenario;
                arguments.list = list;
                return arguments;
            }
            if (corpus == null) {
                throw new IllegalArgumentException("--corpus DIR is required; " + usage().getMessage());
            }
            boolean named = profile != null || routeSelector != null || startText != null || targetText != null;
            if (named) {
                if (!positional.isEmpty() || profile == null) throw usage();
                if (routeSelector != null && (startText != null || targetText != null)) throw usage();
                if (routeSelector == null && (startText == null || targetText == null)) throw usage();
                if (routeSelector != null) {
                    return new Arguments(null, null, routeSelector, profile, 0, 0,
                        counters, json, corpus, algorithm);
                }
                return new Arguments(null, null, null, profile,
                    point("start", startText), point("target", targetText),
                    counters, json, corpus, algorithm);
            }
            if (positional.size() >= 2 && positional.size() % 2 == 0) {
                List<String> routeIds = new ArrayList<>();
                List<String> profiles = new ArrayList<>();
                for (int i = 0; i < positional.size(); i += 2) {
                    routeIds.add(positional.get(i));
                    profiles.add(positional.get(i + 1));
                }
                return new Arguments(routeIds, profiles, null, null, 0, 0,
                    counters, json, corpus, algorithm);
            }
            if (positional.size() != 7) throw usage();
            return new Arguments(null, null, null, positional.get(0),
                pack("start", positional, 1), pack("target", positional, 4),
                counters, json, corpus, algorithm);
        }

        private static int point(String label, String value) {
            String[] values = value.split(",", -1);
            if (values.length != 3) throw new IllegalArgumentException(label + " must be X,Y,PLANE");
            List<String> point = List.of(values);
            return pack(label, point, 0);
        }

        private static int pack(String label, List<String> values, int offset) {
            int x = integer(label + " x", values.get(offset));
            int y = integer(label + " y", values.get(offset + 1));
            int plane = integer(label + " plane", values.get(offset + 2));
            if (x < 0 || x > 32767 || y < 0 || y > 32767 || plane < 0 || plane > 3) {
                throw new IllegalArgumentException(label
                    + " must be within x/y 0..32767 and plane 0..3");
            }
            return WorldPointUtil.packWorldPoint(x, y, plane);
        }

        private static int integer(String label, String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(label + " must be an integer");
            }
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException("usage: route --corpus DIR (--route ROUTE --profile PROFILE "
                + "| --start X,Y,PLANE --end X,Y,PLANE --profile PROFILE "
                + "| PROFILE START_X START_Y START_PLANE TARGET_X TARGET_Y TARGET_PLANE "
                + "| ROUTE PROFILE [ROUTE PROFILE ...]"
                + "| --suite SUITE (--scenario NAME | --list)) [--algorithm legacy|exact] [--json] [--counters]"
                + "; PROFILE is any profile (early, mid, end, maxed, ALL, UNIT_TEST, SEASONAL, ...)");
        }
    }
}
