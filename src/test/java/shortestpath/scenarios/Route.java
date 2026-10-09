package shortestpath.scenarios;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import shortestpath.WorldPointUtil;

/**
 * One route in a data suite file: a JSON array of route objects in the canonical corpus route
 * format ({@code corpus/corpus/routes-v1.json}).
 *
 * <pre>
 * {"id": "gps-natural-0012", "name": "Cauldron of Thunder → Ice Queen's Lair",
 *  "start": [2895, 9833, 0], "target": [2861, 9947, 0], "allowTransports": true,
 *  "tiers": ["smoke", "full"], "negativeProfiles": ["early"]}
 * </pre>
 *
 * Required: {@code id}, {@code name}, {@code target}. Optional: {@code start} (absent: the default
 * start), {@code allowTransports} (default true), {@code tiers}, {@code negativeProfiles} (profiles
 * that must not reach the target), {@code profiles} (overrides the suite's profiles for this route),
 * {@code category} (default: the id without its trailing number), and the provenance strings
 * {@code startName}, {@code targetName}, {@code startSource}, {@code targetSource}. Other fields are
 * rejected.
 */
public final class Route {
    private static final Set<String> FIELDS = Set.of("id", "name", "category", "start", "target", "startName",
        "targetName", "startSource", "targetSource", "allowTransports", "tiers", "negativeProfiles", "profiles");

    private final String id;
    private final String name;
    private final String category;
    private final int start;
    private final int target;
    private final boolean allowTransports;
    private final Set<String> tiers;
    private final Set<String> negativeProfiles;
    private final List<String> profiles;

    private Route(JsonObject json, String source) {
        for (String field : json.keySet()) {
            if (!FIELDS.contains(field)) {
                throw new IllegalArgumentException(source + ": route field '" + field + "' is unknown");
            }
        }
        id = string(json, "id", source);
        name = string(json, "name", source);
        category = json.has("category") ? string(json, "category", source) : id.replaceFirst("-\\d+$", "");
        start = json.has("start") ? point(json, "start", source) : WorldPointUtil.UNDEFINED;
        target = point(json, "target", source);
        allowTransports = !json.has("allowTransports") || json.get("allowTransports").getAsBoolean();
        tiers = Collections.unmodifiableSet(new LinkedHashSet<>(strings(json, "tiers")));
        negativeProfiles = Collections.unmodifiableSet(new LinkedHashSet<>(strings(json, "negativeProfiles")));
        profiles = json.has("profiles") ? Collections.unmodifiableList(strings(json, "profiles")) : null;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    /** The packed start tile, or {@code WorldPointUtil.UNDEFINED}. */
    public int getStart() { return start; }
    public int getTarget() { return target; }
    public boolean isAllowTransports() { return allowTransports; }
    public Set<String> getTiers() { return tiers; }
    public Set<String> getNegativeProfiles() { return negativeProfiles; }
    /** The profiles this route runs with, or {@code null} for the suite's. */
    public List<String> getProfiles() { return profiles; }

    /** This route with {@code profile}: scenario {@code <id>/<profile>}. */
    public Scenario.Builder scenario(String profile) {
        return Scenario.scenario(id + "/" + profile, category)
            .description(name)
            .fromTile(start).toTile(target)
            .profile(shortestpath.profiles.Profiles.get(profile))
            .allowTransports(allowTransports)
            .tiers(tiers)
            .expectReachable(!negativeProfiles.contains(profile));
    }

    public static List<Route> load(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8), path.toString());
    }

    public static List<Route> loadResource(String resource) throws IOException {
        try (InputStream in = Route.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing route resource " + resource);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), resource);
        }
    }

    static List<Route> parse(String json, String source) {
        List<Route> routes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            Route route = new Route(element.getAsJsonObject(), source);
            if (!ids.add(route.id)) {
                throw new IllegalArgumentException(source + ": duplicate route id " + route.id);
            }
            routes.add(route);
        }
        return Collections.unmodifiableList(routes);
    }

    private static String string(JsonObject json, String field, String source) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive() || json.get(field).getAsString().isEmpty()) {
            throw new IllegalArgumentException(source + ": route " + json.get("id") + " needs a non-empty " + field);
        }
        return json.get(field).getAsString();
    }

    private static int point(JsonObject json, String field, String source) {
        JsonArray array = json.has(field) && json.get(field).isJsonArray() ? json.getAsJsonArray(field) : null;
        if (array == null || array.size() != 3) {
            throw new IllegalArgumentException(source + ": route " + json.get("id") + " " + field
                + " must be [x, y, plane]");
        }
        return WorldPointUtil.packWorldPoint(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    private static List<String> strings(JsonObject json, String field) {
        List<String> result = new ArrayList<>();
        if (json.has(field)) {
            for (JsonElement value : json.getAsJsonArray(field)) {
                result.add(value.getAsString());
            }
        }
        return result;
    }
}
