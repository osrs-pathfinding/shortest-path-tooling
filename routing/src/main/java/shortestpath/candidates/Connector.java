package shortestpath.candidates;

import java.util.List;

/**
 * One cache placement of an object a player uses to move somewhere (a ladder, staircase, trapdoor,
 * cave entrance or agility shortcut), as the cache dumper found it. Plain fields, read and written
 * as JSON by {@link ConnectorSnapshot}.
 */
public final class Connector {
    /** The placed object's id, and every id it can turn into (multi-loc children), for matching rows. */
    public int objectId;
    public int[] ids;
    public String name;
    /** The connector options the object offers, in menu order; the first is the one shown. */
    public List<String> options;
    /** South-west tile of the footprint, on the plane the collision map uses (bridges lowered). */
    public int x, y, plane;
    public int sizeX, sizeY;
    /** The other end, when a pairing rule found exactly one; otherwise null. */
    public Partner partner;

    /** The far end of a connector and the rule that paired them. */
    public static final class Partner {
        public int objectId;
        public String name;
        public String option;
        public int x, y, plane;
        public int sizeX, sizeY;
        /** {@code vertical} or {@code id+offset} (tooling's {@code ConnectorPairing}), or {@code map-link} for a world map link. */
        public String rule;
    }

    public String option() {
        return options == null || options.isEmpty() ? "" : options.get(0);
    }

    /** Chebyshev distance from the tile to the footprint on the same plane, or -1 on another plane. */
    public int distance(int tx, int ty, int tplane) {
        return Footprint.distance(x, y, plane, sizeX, sizeY, tx, ty, tplane);
    }

    /** Footprint geometry shared by connectors and their partners. */
    public static final class Footprint {
        private Footprint() { }

        public static int distance(int x, int y, int plane, int sizeX, int sizeY, int tx, int ty, int tplane) {
            if (plane != tplane) return -1;
            int dx = tx < x ? x - tx : Math.max(0, tx - (x + sizeX - 1));
            int dy = ty < y ? y - ty : Math.max(0, ty - (y + sizeY - 1));
            return Math.max(dx, dy);
        }
    }
}
