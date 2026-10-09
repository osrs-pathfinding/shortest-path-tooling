package shortestpath.scenarios;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import shortestpath.profiles.Profiles;

/**
 * Loads a data-only scenario list: routes that all start from a named profile unchanged. Any
 * account or settings override belongs in a Java suite instead, so the format has no columns for
 * them and rejects unknown columns.
 *
 * <pre>
 * name,category,start_x,start_y,start_plane,x,y,plane,profile[,minimum_length][,expect_reachable][,source_file][,source_line]
 * </pre>
 *
 * Rows split on bare commas (no quoting); blank and {@code #} lines are skipped. An empty start
 * means the dashboard's default start.
 */
public final class ScenarioData {
    static final List<String> REQUIRED = List.of("name", "category", "start_x", "start_y", "start_plane",
        "x", "y", "plane", "profile");
    static final Set<String> OPTIONAL = Set.of("minimum_length", "expect_reachable", "source_file", "source_line");

    private ScenarioData() { }

    public static List<Scenario.Builder> loadResource(String resource) throws IOException {
        try (InputStream in = ScenarioData.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing scenario data resource " + resource);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), resource);
        }
    }

    public static List<Scenario.Builder> loadFile(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8), path.toString());
    }

    static List<Scenario.Builder> parse(String contents, String source) {
        String[] lines = contents.split("\r?\n");
        String[] header = lines[0].split(",", -1);
        List<String> columns = new ArrayList<>();
        for (String cell : header) {
            String column = cell.trim();
            if (!REQUIRED.contains(column) && !OPTIONAL.contains(column)) {
                throw new IllegalArgumentException(source + ":1: unknown column '" + column + "'");
            }
            columns.add(column);
        }
        for (String column : REQUIRED) {
            if (!columns.contains(column)) {
                throw new IllegalArgumentException(source + ":1: missing column '" + column + "'");
            }
        }
        List<Scenario.Builder> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split(",", -1);
            if (fields.length != columns.size()) {
                throw new IllegalArgumentException(source + ":" + (i + 1) + ": " + fields.length
                    + " fields for " + columns.size() + " columns (a comma in a name?)");
            }
            String name = field(columns, fields, "name");
            if (!names.add(name)) {
                throw new IllegalArgumentException(source + ":" + (i + 1) + ": duplicate name '" + name + "'");
            }
            Scenario.Builder scenario = Scenario.scenario(name, field(columns, fields, "category"))
                .to(integer(columns, fields, "x"), integer(columns, fields, "y"), integer(columns, fields, "plane"))
                .profile(Profiles.get(field(columns, fields, "profile")));
            if (!field(columns, fields, "start_x").isEmpty()) {
                scenario.from(integer(columns, fields, "start_x"), integer(columns, fields, "start_y"),
                    integer(columns, fields, "start_plane"));
            }
            String minimum = field(columns, fields, "minimum_length");
            if (!minimum.isEmpty()) {
                scenario.minimumLength(Integer.parseInt(minimum));
            }
            String reachable = field(columns, fields, "expect_reachable");
            if (!reachable.isEmpty()) {
                if (!reachable.equals("true") && !reachable.equals("false")) {
                    throw new IllegalArgumentException(source + ":" + (i + 1)
                        + ": expect_reachable must be true or false");
                }
                scenario.expectReachable(Boolean.parseBoolean(reachable));
            }
            result.add(scenario);
        }
        return result;
    }

    private static String field(List<String> columns, String[] fields, String column) {
        int index = columns.indexOf(column);
        return index < 0 ? "" : fields[index].trim();
    }

    private static int integer(List<String> columns, String[] fields, String column) {
        return Integer.parseInt(field(columns, fields, column));
    }
}
