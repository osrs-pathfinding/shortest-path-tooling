package shortestpath.route;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import shortestpath.pathfinder.AlternativeRoute;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.scenarios.Observation;
import shortestpath.scenarios.Scenario;
import shortestpath.scenarios.ScenarioRunner;
import shortestpath.scenarios.Suites;

/** Runs a suite's tier and writes each scenario's first route and alternatives, one JSON object per line. */
public final class AlternativesReport {
    private AlternativesReport() { }

    public static void main(String[] args) throws Exception {
        List<Scenario> scenarios = Suites.withTier(Suites.load(args[0]), args[1]);
        int count = Integer.parseInt(args[2]);
        ScenarioRunner runner = new ScenarioRunner(ScenarioRunner.Backend.parse("astar"));
        Gson gson = new Gson();
        Path out = Path.of(args[3]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(out))) {
            for (Scenario scenario : scenarios) {
                Observation observation = runner.run(scenario);
                PathfinderResult result = observation.getResult();
                JsonObject row = new JsonObject();
                row.addProperty("scenario", scenario.getName());
                row.addProperty("description", scenario.getDescription());
                row.addProperty("profile", scenario.getProfile().name());
                row.addProperty("reached", observation.isReached());
                if (observation.isReached()) {
                    List<AlternativeRoute> alternatives = observation.getAStar().findAlternatives(count);
                    row.addProperty("cost", result.getPathCost());
                    row.addProperty("forwardMs", observation.getAStar().getForwardSearchNanos() / 1_000_000.0);
                    row.addProperty("nodesChecked", result.getNodesChecked());
                    row.add("journeys", RouteCli.jsonJourneys(result.getPathSteps()));
                    row.add("alternatives", RouteCli.jsonAlternatives(alternatives, result.getPathCost()));
                }
                writer.println(gson.toJson(row));
            }
        }
    }
}
