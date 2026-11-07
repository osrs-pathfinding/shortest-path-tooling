package shortestpath.candidates;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

/**
 * Whether the plugin already has a transport for a connector: a transport origin within
 * {@link #ANY_ROW} tiles of its footprint, or a row anchored on one of its own object ids within
 * {@link #SAME_ID} tiles. Built from the loaded transports, so a row added to the plugin covers its
 * connector on the next judgement.
 */
public final class Coverage {
    /** A transport origin this close to a connector's footprint covers it. */
    static final int ANY_ROW = 2;
    /** A transport anchored on the connector's own object id this close covers it. */
    static final int SAME_ID = 3;

    private final Map<Integer, Set<Integer>> idsByOrigin = new HashMap<>();

    public Coverage(Map<Integer, Set<Transport>> transports) {
        for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet()) {
            int origin = entry.getKey();
            if (origin == Transport.UNDEFINED_ORIGIN) continue;
            Set<Integer> ids = idsByOrigin.computeIfAbsent(origin, k -> new HashSet<>());
            for (Transport transport : entry.getValue()) {
                int id = anchorId(transport.getObjectInfo());
                if (id >= 0) ids.add(id);
            }
        }
    }

    /** The trailing object id of a {@code menuOption menuTarget objectID} cell, or -1. */
    public static int anchorId(String objectInfo) {
        if (objectInfo == null) return -1;
        String text = objectInfo.trim();
        int space = text.lastIndexOf(' ');
        try {
            return Integer.parseInt(text.substring(space + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Any transport starts within {@code reach} tiles of the tile, on its plane. */
    public boolean originNear(int x, int y, int plane, int reach) {
        for (int tx = x - reach; tx <= x + reach; tx++) {
            for (int ty = y - reach; ty <= y + reach; ty++) {
                if (idsByOrigin.containsKey(WorldPointUtil.packWorldPoint(tx, ty, plane))) return true;
            }
        }
        return false;
    }

    public boolean covers(Connector c) {
        int reach = Math.max(ANY_ROW, SAME_ID);
        for (int x = c.x - reach; x < c.x + c.sizeX + reach; x++) {
            for (int y = c.y - reach; y < c.y + c.sizeY + reach; y++) {
                Set<Integer> ids = idsByOrigin.get(WorldPointUtil.packWorldPoint(x, y, c.plane));
                if (ids == null) continue;
                if (c.distance(x, y, c.plane) <= ANY_ROW) return true;
                for (int id : c.ids) if (ids.contains(id)) return true;
            }
        }
        return false;
    }
}
