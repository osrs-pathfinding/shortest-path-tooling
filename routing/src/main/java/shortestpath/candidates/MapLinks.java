package shortestpath.candidates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

/**
 * Candidate set 3, unmodelled world map links: the world map links a dungeon's entrance icon to the
 * dungeon's map (and back), but no transport starts within {@link #ROW_REACH} tiles of the icon and
 * lands in the walking area at the link's other end. One item per link, with its reverse when the
 * map has one.
 *
 * <p>The test is local to the icon, not to the two areas: a dungeon with a modelled entrance and a
 * second, unmodelled one joins the same two areas, and still lists the second entrance.
 *
 * <p>Set aside and counted instead: links with an end with no walkable tile within
 * {@link UnreachedAreas#LINK_REACH} tiles of its icon; links into an unreached area (listed by
 * {@link UnreachedAreas} as that area's way in or out); links within one walking area; links out of
 * scope; and links a transport already models in every direction the map has.
 */
final class MapLinks {
    /** A transport starting this close to a link's icon, landing in the other end's area, models it. */
    static final int ROW_REACH = 15;

    private MapLinks() { }

    /** One direction of a link, with its walkable ends. */
    private static final class Direction {
        final ConnectorSnapshot.MapLink link;
        final int from, to;
        boolean modelled;

        Direction(ConnectorSnapshot.MapLink link, int from, int to) {
            this.link = link; this.from = from; this.to = to;
        }
    }

    static List<Report.Item> list(ConnectorSnapshot snapshot, Walking walking, Map<Integer, Set<Transport>> transports,
                                  Scope scope, Map<String, Integer> funnel) {
        List<Direction> directions = new ArrayList<>();
        int offGround = 0, unreached = 0, sameArea = 0, outOfScope = 0, modelled = 0;
        for (ConnectorSnapshot.MapLink link : snapshot.mapLinks) {
            int from = walking.stand(link.from.x, link.from.y, link.from.plane, 1, 1, UnreachedAreas.LINK_REACH);
            int to = walking.stand(link.to.x, link.to.y, link.to.plane, 1, 1, UnreachedAreas.LINK_REACH);
            if (from == Walking.NONE || to == Walking.NONE) { offGround++; continue; }
            int fromArea = walking.area(from), toArea = walking.area(to);
            if (!walking.isStructurallyReachable(fromArea) || !walking.isStructurallyReachable(toArea)) { unreached++; continue; }
            if (fromArea == toArea) { sameArea++; continue; }
            if (scope.excludes(from) || scope.excludes(to)) { outOfScope++; continue; }
            Direction direction = new Direction(link, from, to);
            direction.modelled = modelled(link.from, toArea, walking, transports);
            if (direction.modelled) modelled++;
            directions.add(direction);
        }
        funnel.put("map links considered", snapshot.mapLinks.size());
        funnel.put("map links with an end off walkable ground", offGround);
        funnel.put("map links into unreached areas", unreached);
        funnel.put("map links within one walking area", sameArea);
        funnel.put("map links out of scope", outOfScope);
        funnel.put("map links a transport models", modelled);

        // A link and its reverse are one item; it is listed when either direction is unmodelled.
        List<Report.Item> items = new ArrayList<>();
        List<Direction> used = new ArrayList<>();
        for (Direction direction : directions) {
            if (used.contains(direction)) continue;
            used.add(direction);
            Direction reverse = directions.stream().filter(d -> !used.contains(d) && isReverse(direction, d)).findFirst().orElse(null);
            if (reverse != null) used.add(reverse);
            if (direction.modelled && (reverse == null || reverse.modelled)) continue;
            items.add(item(direction, reverse));
        }
        items.sort(Comparator.comparing((Report.Item i) -> i.title));
        funnel.put("listed map links", items.size());
        return items;
    }

    /** A transport starts within {@link #ROW_REACH} tiles of the icon and lands in {@code area}. */
    private static boolean modelled(ConnectorSnapshot.Tile icon, int area, Walking walking, Map<Integer, Set<Transport>> transports) {
        for (int x = icon.x - ROW_REACH; x <= icon.x + ROW_REACH; x++) {
            for (int y = icon.y - ROW_REACH; y <= icon.y + ROW_REACH; y++) {
                for (Transport t : transports.getOrDefault(WorldPointUtil.packWorldPoint(x, y, icon.plane), Set.of())) {
                    int destination = t.getDestination();
                    if (destination == Transport.UNDEFINED_DESTINATION) continue;
                    int land = walking.stand(WorldPointUtil.unpackWorldX(destination), WorldPointUtil.unpackWorldY(destination),
                        WorldPointUtil.unpackWorldPlane(destination), 1, 1, 1);
                    if (land != Walking.NONE && walking.area(land) == area) return true;
                }
            }
        }
        return false;
    }

    /** {@code b} goes back the way {@code a} came: each icon within {@link #ROW_REACH} tiles of the other's. */
    private static boolean isReverse(Direction a, Direction b) {
        return near(a.link.from, b.link.to) && near(a.link.to, b.link.from);
    }

    private static boolean near(ConnectorSnapshot.Tile a, ConnectorSnapshot.Tile b) {
        return a.plane == b.plane && Math.abs(a.x - b.x) <= ROW_REACH && Math.abs(a.y - b.y) <= ROW_REACH;
    }

    private static Report.Item item(Direction direction, Direction reverse) {
        Report.Item item = new Report.Item();
        item.kind = "link";
        item.paired = true;
        StringBuilder key = new StringBuilder();
        for (Direction d : reverse == null ? List.of(direction) : List.of(direction, reverse)) {
            item.connectors.add(new Report.Candidate(d.modelled ? "modelled" : "unmodelled", UnreachedAreas.linkConnector(d.link),
                Report.Point.of(d.from), Report.Point.of(d.to)));
            key.append(tile(d.link.from)).append(" > ").append(tile(d.link.to)).append('\n');
        }
        item.focus = Report.Point.of(direction.from);
        item.title = "World map link " + tile(direction.link.from) + " ↔ " + tile(direction.link.to);
        item.id = Report.id("link", key.toString());
        return item;
    }

    private static String tile(ConnectorSnapshot.Tile t) {
        return t.x + " " + t.y + " " + t.plane;
    }
}
