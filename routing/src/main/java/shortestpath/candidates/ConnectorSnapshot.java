package shortestpath.candidates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The cache facts behind the transport candidates: every connector placement that no transport row
 * covered when the snapshot was made, with its pairing evidence. Written by the tooling cache dumper
 * ({@code ./gradlew transportCandidates}); judged live by {@link TransportCandidates} against whichever
 * plugin data is loaded, so a row added later still removes its candidate.
 */
public final class ConnectorSnapshot {
    /** The bundled snapshot. */
    public static final String RESOURCE = "/candidates/connectors.json";

    /** The OSRS cache it was read from: openrs2 id and game build. */
    public String cache;
    /** The plugin commit whose rows decided coverage at generation time. */
    public String plugin;
    /** Counts of the extraction funnel, in order. */
    public Map<String, Integer> extraction = new LinkedHashMap<>();
    /** The agility shortcut map icon and how well it matches the plugin's shortcut rows. */
    public IconMarker shortcutMarker;
    /** The dungeon entrance map icon and how well it matches the plugin's rows going down or in. */
    public IconMarker dungeonMarker;
    public List<Connector> connectors = new ArrayList<>();
    /** The world map's links between its maps (a dungeon's entrance icon and the dungeon), both directions. */
    public List<MapLink> mapLinks = new ArrayList<>();
    /** Every placement of the agility shortcut map icon. */
    public List<ShortcutIcon> shortcutIcons = new ArrayList<>();
    /** Every placement of the dungeon entrance map icon. */
    public List<EntranceIcon> dungeonIcons = new ArrayList<>();

    /** A tile. */
    public static final class Tile {
        public int x, y, plane;

        public Tile() { }

        public Tile(int x, int y, int plane) { this.x = x; this.y = y; this.plane = plane; }
    }

    /** One world map link: clicking the icon at {@code from} opens the map at {@code to}. Icon positions, not exact tiles. */
    public static final class MapLink {
        public Tile from, to;
    }

    /**
     * A world map icon the game draws with invisible map-function objects placed beside what it marks
     * (an agility shortcut, a dungeon entrance), not on the object itself. The dumper finds which icon
     * it is from the data: the one most often placed beside the plugin's rows of that kind.
     */
    public static final class IconMarker {
        /** e.g. {@code mapAreaId=71}, or null when none was found. */
        public String field;
        /** Icon placements, and how many have one of those rows' origins within 5 tiles. */
        public int icons;
        public int iconsNearRows;
    }

    /** A shortcut icon placement, with RuneLite's description of the shortcut when one is that close. */
    public static final class ShortcutIcon {
        public int x, y, plane;
        /** From RuneLite's {@code AgilityShortcut} enum (BSD-2), or null when no entry is within 10 tiles. */
        public String runelite;
        public String description;
        public int level = -1;
        /** Placements of the shortcut's obstacle objects within 10 tiles (RuneLite's ids, else nearby connectors). */
        public List<Connector> obstacles = new ArrayList<>();
    }

    /** A dungeon entrance icon placement, with the connectors within 5 tiles of it. */
    public static final class EntranceIcon {
        public int x, y, plane;
        public List<Connector> connectors = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static ConnectorSnapshot read(Reader reader) {
        return GSON.fromJson(reader, ConnectorSnapshot.class);
    }

    public static ConnectorSnapshot bundled() {
        try (InputStream stream = Objects.requireNonNull(ConnectorSnapshot.class.getResourceAsStream(RESOURCE), RESOURCE);
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return read(reader);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** The header pretty-printed and one connector per line, so a regenerated snapshot diffs by placement. */
    public String toJson() {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("cache", cache);
        header.put("plugin", plugin);
        header.put("extraction", extraction);
        header.put("shortcutMarker", shortcutMarker);
        header.put("dungeonMarker", dungeonMarker);
        String head = PRETTY.toJson(header);
        StringBuilder out = new StringBuilder(head.substring(0, head.length() - 2));
        list(out, "connectors", connectors);
        list(out, "mapLinks", mapLinks);
        list(out, "shortcutIcons", shortcutIcons);
        list(out, "dungeonIcons", dungeonIcons);
        return out.append("\n}\n").toString();
    }

    private static void list(StringBuilder out, String name, List<?> values) {
        out.append(",\n  \"").append(name).append("\": [");
        for (int i = 0; i < values.size(); i++) {
            out.append(i == 0 ? "\n    " : ",\n    ").append(GSON.toJson(values.get(i)));
        }
        out.append(values.isEmpty() ? "]" : "\n  ]");
    }
}
