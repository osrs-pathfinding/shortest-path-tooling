package shortestpath.dump.candidates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import shortestpath.WorldPointUtil;
import shortestpath.candidates.Connector;
import shortestpath.transport.Transport;

/**
 * Finds a connector's far end: another connector with the complementary option (up with down, enter
 * with exit; a two-way {@code Climb} matches either). Rules, first match wins:
 *
 * <ul>
 * <li>{@code vertical}: footprint within {@link #VERTICAL_REACH} tiles, one plane up or down.</li>
 * <li>{@code id+offset}: an object id within {@link #ID_GAP} of this one, placed at most
 * {@link #ID_MAX_PLACEMENTS} times, exactly one such placement that also fits the offset hint.</li>
 * <li>{@code id}: as above, exactly one such placement.</li>
 * <li>{@code offset}: exactly one complementary connector within {@link #OFFSET_SLACK} tiles of the
 * point {@link #OFFSET} tiles north (going down) or south (going up), where the game often puts a
 * dungeon.</li>
 * </ul>
 *
 * Only the {@link #KEPT} rules are precise enough to keep; {@link #precision} measures every rule
 * against the plugin's own rows so the choice can be revisited on each run.
 */
final class ConnectorPairing {
    static final int VERTICAL_REACH = 2;
    static final int ID_GAP = 4;
    static final int ID_MAX_PLACEMENTS = 5;
    static final int OFFSET = 6400;
    static final int OFFSET_SLACK = 8;
    /**
     * The rules kept in the snapshot, measured on cache 2757 against the plugin's rows: vertical 1033
     * right / 47 wrong and id+offset 54 / 11, but id alone 79 / 65 and offset alone 36 / 28.
     */
    static final Set<String> KEPT = Set.of("vertical", "id+offset");
    static final Set<String> ASCEND = Set.of("climb-up", "walk-up", "exit", "leave", "climb-out");
    static final Set<String> DESCEND = Set.of("climb-down", "walk-down", "enter", "go-down");
    static final Set<String> BOTH = Set.of("climb");

    private final Map<Integer, List<CacheConnectors.Placement>> byId = new HashMap<>();
    private final Map<Long, List<CacheConnectors.Placement>> byCell = new HashMap<>();

    ConnectorPairing(List<CacheConnectors.Placement> placements) {
        for (CacheConnectors.Placement p : placements) {
            if (p.direction == null) continue;
            byId.computeIfAbsent(p.connector.objectId, k -> new ArrayList<>()).add(p);
            Connector c = p.connector;
            for (int x = c.x >> 3; x <= (c.x + c.sizeX - 1) >> 3; x++) {
                for (int y = c.y >> 3; y <= (c.y + c.sizeY - 1) >> 3; y++) {
                    byCell.computeIfAbsent(cell(x, y, c.plane), k -> new ArrayList<>()).add(p);
                }
            }
        }
    }

    /**
     * Gives every uncovered placement its far end, when a kept rule finds one, and returns each rule's
     * precision on the covered ones: rule to {right, wrong}, where right means the partner stands
     * within 3 tiles of where the plugin's rows from that connector land.
     */
    Map<String, int[]> pairAll(List<CacheConnectors.Placement> placements, Map<Integer, Set<Transport>> transports) {
        Map<String, int[]> precision = new TreeMap<>();
        for (CacheConnectors.Placement p : placements) {
            Connector.Partner partner = pair(p);
            if (!p.covered) {
                p.connector.partner = partner != null && KEPT.contains(partner.rule) ? partner : null;
                continue;
            }
            if (p.direction == null) continue;
            List<Integer> landings = landings(transports, p.connector);
            if (landings.isEmpty()) continue;
            int[] counts = precision.computeIfAbsent(partner == null ? "none" : partner.rule, k -> new int[2]);
            boolean right = partner != null && landings.stream().anyMatch(tile -> {
                int distance = Connector.Footprint.distance(partner.x, partner.y, partner.plane, partner.sizeX, partner.sizeY,
                    WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile), WorldPointUtil.unpackWorldPlane(tile));
                return distance >= 0 && distance <= 3;
            });
            counts[right ? 0 : 1]++;
        }
        return precision;
    }

    Connector.Partner pair(CacheConnectors.Placement p) {
        if (p.direction == null) return null;
        Connector c = p.connector;
        List<CacheConnectors.Placement> vertical = new ArrayList<>();
        if (!p.direction.equals("descend")) vertical.addAll(near(p, c.x, c.y, c.sizeX, c.sizeY, c.plane + 1, VERTICAL_REACH));
        if (!p.direction.equals("ascend") && c.plane > 0) vertical.addAll(near(p, c.x, c.y, c.sizeX, c.sizeY, c.plane - 1, VERTICAL_REACH));
        vertical.sort(Comparator.comparingInt(o -> boxDistance(c.x, c.y, c.sizeX, c.sizeY, o.connector)));
        if (!vertical.isEmpty()) return partner(vertical.get(0), "vertical");

        Set<CacheConnectors.Placement> offset = new LinkedHashSet<>();
        if (!p.direction.equals("ascend")) offset.addAll(near(p, c.x, c.y + OFFSET, c.sizeX, c.sizeY, c.plane, OFFSET_SLACK));
        if (!p.direction.equals("descend")) offset.addAll(near(p, c.x, c.y - OFFSET, c.sizeX, c.sizeY, c.plane, OFFSET_SLACK));

        List<CacheConnectors.Placement> byIdMatches = new ArrayList<>();
        for (int id = c.objectId - ID_GAP; id <= c.objectId + ID_GAP; id++) {
            List<CacheConnectors.Placement> same = byId.getOrDefault(id, List.of());
            if (same.size() > ID_MAX_PLACEMENTS) continue;
            for (CacheConnectors.Placement other : same) {
                if (other == p || !complements(p.direction, other.direction)) continue;
                if (other.connector.plane == c.plane && boxDistance(c.x, c.y, c.sizeX, c.sizeY, other.connector) <= VERTICAL_REACH) continue;
                byIdMatches.add(other);
            }
        }
        List<CacheConnectors.Placement> both = new ArrayList<>(byIdMatches);
        both.retainAll(offset);
        if (both.size() == 1) return partner(both.get(0), "id+offset");
        if (byIdMatches.size() == 1) return partner(byIdMatches.get(0), "id");
        if (offset.size() == 1) return partner(offset.iterator().next(), "offset");
        return null;
    }

    /** Complementary placements whose footprint is within {@code reach} of the box at (x, y). */
    private List<CacheConnectors.Placement> near(CacheConnectors.Placement self, int x, int y, int sizeX, int sizeY, int plane, int reach) {
        Set<CacheConnectors.Placement> found = new LinkedHashSet<>();
        for (int cx = (x - reach) >> 3; cx <= (x + sizeX - 1 + reach) >> 3; cx++) {
            for (int cy = (y - reach) >> 3; cy <= (y + sizeY - 1 + reach) >> 3; cy++) {
                for (CacheConnectors.Placement other : byCell.getOrDefault(cell(cx, cy, plane), List.of())) {
                    if (other == self || !complements(self.direction, other.direction)) continue;
                    if (boxDistance(x, y, sizeX, sizeY, other.connector) <= reach) found.add(other);
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static long cell(int cx, int cy, int plane) {
        return ((long) plane << 40) | ((long) cx << 20) | cy;
    }

    private static Connector.Partner partner(CacheConnectors.Placement other, String rule) {
        Connector o = other.connector;
        Connector.Partner partner = new Connector.Partner();
        partner.objectId = o.objectId;
        partner.name = o.name;
        partner.option = o.option();
        partner.x = o.x; partner.y = o.y; partner.plane = o.plane;
        partner.sizeX = o.sizeX; partner.sizeY = o.sizeY;
        partner.rule = rule;
        return partner;
    }

    static int boxDistance(int x, int y, int sizeX, int sizeY, Connector o) {
        int dx = Math.max(0, Math.max(x - (o.x + o.sizeX - 1), o.x - (x + sizeX - 1)));
        int dy = Math.max(0, Math.max(y - (o.y + o.sizeY - 1), o.y - (y + sizeY - 1)));
        return Math.max(dx, dy);
    }

    static boolean complements(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equals("both") || b.equals("both")) return true;
        return !a.equals(b);
    }

    /** {@code ascend}, {@code descend} or {@code both} from the first option that says, or null. */
    static String direction(List<String> options) {
        for (String option : options) {
            String o = option.toLowerCase();
            if (ASCEND.contains(o)) return "ascend";
            if (DESCEND.contains(o)) return "descend";
            if (BOTH.contains(o)) return "both";
        }
        return null;
    }

    /** Where the rows starting at this connector land. */
    private static List<Integer> landings(Map<Integer, Set<Transport>> transports, Connector c) {
        List<Integer> landings = new ArrayList<>();
        for (int x = c.x - 2; x < c.x + c.sizeX + 2; x++) {
            for (int y = c.y - 2; y < c.y + c.sizeY + 2; y++) {
                for (Transport t : transports.getOrDefault(WorldPointUtil.packWorldPoint(x, y, c.plane), Set.of())) {
                    if (t.getDestination() != Transport.UNDEFINED_DESTINATION) landings.add(t.getDestination());
                }
            }
        }
        return landings;
    }
}
