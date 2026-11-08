package shortestpath.candidates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Candidate set 4, dungeon entrances: the world map's dungeon entrance icon
 * ({@link ConnectorSnapshot.EntranceIcon}) with no transport origin within {@link #COVER} tiles and no
 * covered connector beside it, on reached ground. One item per entrance, with the connectors beside
 * the icon as the objects it most likely marks.
 *
 * <p>Icons on any plane within {@link #MERGE} tiles of one already kept are the same entrance. Set
 * aside and counted instead, because another set already judges them: icons within
 * {@link #LINK_NEAR} tiles of a world map link's icon (the map already joins that dungeon; a link into
 * an unreached area is judged by {@link UnreachedAreas}), icons
 * whose connectors are an unreached area's way in ({@link UnreachedAreas}), and icons beside
 * unreached ground only.
 */
final class DungeonEntrances {
    /** A transport origin this close to an entrance icon covers it; also how far its walkable side may be. */
    static final int COVER = 5;
    /** Entrance icons this close (on any plane) are one entrance. */
    static final int MERGE = 3;
    /** A world map link icon this close to an entrance icon is the same entrance, which the map already joins. */
    static final int LINK_NEAR = 10;

    private DungeonEntrances() { }

    static List<Report.Item> list(ConnectorSnapshot snapshot, Walking walking, Coverage coverage, Scope scope,
                                  List<Report.Item> areas, Map<String, Integer> funnel) {
        Set<String> wayIn = new HashSet<>();
        for (Report.Item area : areas) for (Report.Candidate c : area.connectors) wayIn.add(key(c.connector));
        List<ConnectorSnapshot.EntranceIcon> icons = new ArrayList<>(snapshot.dungeonIcons);
        icons.sort(Comparator.comparingInt((ConnectorSnapshot.EntranceIcon i) -> i.plane).thenComparingInt(i -> i.x).thenComparingInt(i -> i.y));
        List<ConnectorSnapshot.EntranceIcon> kept = new ArrayList<>();
        int repeated = 0, covered = 0, linked = 0, ofArea = 0, outOfScope = 0, offGround = 0, unreached = 0;
        List<Report.Item> items = new ArrayList<>();
        for (ConnectorSnapshot.EntranceIcon icon : icons) {
            if (kept.stream().anyMatch(k -> Math.abs(k.x - icon.x) <= MERGE && Math.abs(k.y - icon.y) <= MERGE)) { repeated++; continue; }
            kept.add(icon);
            if (coverage.originNear(icon.x, icon.y, icon.plane, COVER) || icon.connectors.stream().anyMatch(coverage::covers)) {
                covered++;
                continue;
            }
            if (nearLink(snapshot.mapLinks, icon)) { linked++; continue; }
            if (icon.connectors.stream().anyMatch(c -> wayIn.contains(key(c)))) { ofArea++; continue; }
            if (scope.excludes(icon.x, icon.y, icon.plane)) { outOfScope++; continue; }
            int stand = walking.stand(icon.x, icon.y, icon.plane, 1, 1, COVER);
            if (stand == Walking.NONE) { offGround++; continue; }
            if (!walking.isStructurallyReachable(walking.area(stand))) { unreached++; continue; }
            items.add(item(icon, stand, walking));
        }
        funnel.put("dungeon entrance icons", icons.size());
        funnel.put("dungeon icons repeating another", repeated);
        funnel.put("dungeon entrances with a transport within " + COVER + " tiles", covered);
        funnel.put("dungeon entrances beside a world map link", linked);
        funnel.put("dungeon entrances that are an unreached area's way in", ofArea);
        funnel.put("dungeon entrances out of scope", outOfScope);
        funnel.put("dungeon entrances with no walkable tile within " + COVER, offGround);
        funnel.put("dungeon entrances in unreached areas", unreached);
        items.sort(Comparator.comparing((Report.Item i) -> i.title));
        funnel.put("listed dungeon entrances", items.size());
        return items;
    }

    private static Report.Item item(ConnectorSnapshot.EntranceIcon icon, int stand, Walking walking) {
        Report.Item item = new Report.Item();
        item.kind = "dungeon";
        item.focus = new Report.Point(icon.x, icon.y, icon.plane);
        for (Connector c : icon.connectors) {
            int at = walking.stand(c.x, c.y, c.plane, c.sizeX, c.sizeY, 1);
            Integer partner = UnreachedAreas.partnerStand(walking, c);
            item.connectors.add(new Report.Candidate("entrance", c, Report.Point.of(at == Walking.NONE ? stand : at),
                partner == null ? null : Report.Point.of(partner)));
        }
        item.paired = item.connectors.stream().anyMatch(c -> c.partnerStand != null);
        item.title = "Dungeon entrance near " + icon.x + " " + icon.y + " " + icon.plane;
        item.id = Report.id("dungeon", icon.x + " " + icon.y + " " + icon.plane);
        return item;
    }

    private static boolean nearLink(List<ConnectorSnapshot.MapLink> links, ConnectorSnapshot.EntranceIcon icon) {
        for (ConnectorSnapshot.MapLink link : links) {
            ConnectorSnapshot.Tile t = link.from;
            if (t.plane == icon.plane && Math.abs(t.x - icon.x) <= LINK_NEAR && Math.abs(t.y - icon.y) <= LINK_NEAR) return true;
        }
        return false;
    }

    private static String key(Connector c) {
        return c.objectId + " " + c.x + " " + c.y + " " + c.plane;
    }
}
