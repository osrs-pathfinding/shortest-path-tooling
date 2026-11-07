package shortestpath.candidates;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import shortestpath.WorldPointUtil;

/**
 * The judged candidates: every listed item, and the funnel counting what each step set aside. Plain
 * public fields, serialised as JSON by the workbench.
 */
public final class Report {
    /** The criteria, in words, for the page that shows the list. */
    public String rule;
    /** The cache the snapshot was read from, and the plugin commit it was made against. */
    public String cache;
    public String snapshotPlugin;
    public Map<String, Integer> extraction;
    /** Counts at each step of the judgement, in order. */
    public Map<String, Integer> funnel = new LinkedHashMap<>();
    public ConnectorSnapshot.ShortcutMarker shortcutMarker;
    public List<Item> items = new ArrayList<>();

    /** A tile, as the planner's other tools serialise one. */
    public static final class Point {
        public final int x, y, plane;

        Point(int x, int y, int plane) { this.x = x; this.y = y; this.plane = plane; }

        static Point of(int packed) {
            return new Point(WorldPointUtil.unpackWorldX(packed), WorldPointUtil.unpackWorldY(packed), WorldPointUtil.unpackWorldPlane(packed));
        }
    }

    /** One connector of a listed item, with the role the judgement gave it. */
    public static final class Candidate {
        /** {@code way-out}, {@code way-in}, {@code open-end} or {@code shortcut} (an obstacle). */
        public final String role;
        public final Connector connector;
        /** Where it is used from: a walkable tile next to (or on) the footprint, nearest the centre. */
        public final Point stand;
        /** The partner's stand tile, when it has a partner with a walkable side. */
        public final Point partnerStand;

        Candidate(String role, Connector connector, Point stand, Point partnerStand) {
            this.role = role; this.connector = connector; this.stand = stand; this.partnerStand = partnerStand;
        }
    }

    /** A review item: an unreached area and its connectors ({@link UnreachedAreas}), or one shortcut ({@link AgilityShortcuts}). */
    public static final class Item {
        /** Stable across cache builds: a hash of the placements the item is about. */
        public String id;
        /** {@code area} or {@code shortcut}. */
        public String kind;
        public String title;
        /** Unreached area only: walkable tiles, bounds {minX, minY, maxX, maxY, plane}, and what it contains. */
        public Integer areaTiles;
        public int[] bounds;
        public Point focus;
        public Map<String, Integer> contains;
        /** True when a connector has a known far end. */
        public boolean paired;
        public List<Candidate> connectors = new ArrayList<>();
        /** Shortcut only: the icon, with RuneLite's description and level when it knows the shortcut. */
        public ConnectorSnapshot.ShortcutIcon shortcut;
    }

    /** {@code prefix-} and the first 12 bytes of the text's SHA-256. */
    static String id(String prefix, String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder(prefix).append('-');
            for (int i = 0; i < 12; i++) id.append(String.format("%02x", digest[i]));
            return id.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
