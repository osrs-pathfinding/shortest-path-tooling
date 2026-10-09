package shortestpath.scenarios;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The exact path length each scenario of a suite is expected to have, by scenario name. Kept
 * as data next to the suites ({@code /scenarios/expected-lengths/<suite>.json}) because
 * {@code captureExpectedLengths} rewrites it from a dashboard run.
 */
public final class ExpectedLengths {
    static final String RESOURCE_DIR = "/scenarios/expected-lengths/";

    private ExpectedLengths() { }

    public static Map<String, Integer> load(String suite) throws IOException {
        try (InputStream in = ExpectedLengths.class.getResourceAsStream(RESOURCE_DIR + suite + ".json")) {
            if (in == null) {
                throw new IOException("suite " + suite + " has no " + RESOURCE_DIR + suite + ".json");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static Map<String, Integer> parse(String json) {
        Map<String, Integer> result = new LinkedHashMap<>();
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        for (String name : object.keySet()) {
            result.put(name, object.get(name).getAsInt());
        }
        return result;
    }

    /** The file for {@code suite} under a test-resources source directory. */
    public static Path file(Path resourcesDir, String suite) {
        return resourcesDir.resolve(RESOURCE_DIR.substring(1) + suite + ".json");
    }

    /**
     * Rewrites {@code file} with {@code captured} lengths replacing the old ones, in scenario
     * order. Scenarios without a captured length keep their old one.
     */
    public static void update(Path file, List<Scenario> scenarios, Map<String, Integer> captured)
            throws IOException {
        Map<String, Integer> old = Files.exists(file)
            ? parse(Files.readString(file, StandardCharsets.UTF_8)) : Map.of();
        JsonObject json = new JsonObject();
        for (Scenario scenario : scenarios) {
            Integer length = captured.getOrDefault(scenario.getName(), old.get(scenario.getName()));
            if (length != null) {
                json.addProperty(scenario.getName(), length);
            }
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json)
            + "\n", StandardCharsets.UTF_8);
    }
}
