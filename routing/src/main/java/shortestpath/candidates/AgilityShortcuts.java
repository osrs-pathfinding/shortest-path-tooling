package shortestpath.candidates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Candidate set 2, agility shortcuts: the game's agility shortcut map icon
 * ({@link ConnectorSnapshot.ShortcutIcon}) with no transport origin within {@link #COVER} tiles and
 * no covered obstacle, beside reached ground. One item per shortcut.
 *
 * <p>The game draws the icon with invisible map-function objects beside each shortcut, sometimes
 * repeated on other planes or several to a shortcut, so icons on any plane within {@link #MERGE}
 * tiles of one already kept, or sharing an obstacle with it, are the same shortcut. Icons beside
 * unreached ground only are counted, not listed: nobody can walk to them, so their area is the gap.
 *
 * <p>Shortcuts RuneLite knows come first: they come with a description, a level and obstacle ids.
 */
final class AgilityShortcuts {
    /** A transport origin this close to a shortcut icon covers it; also how far its walkable side may be. */
    static final int COVER = 5;
    /** Shortcut icons this close (on any plane) are one shortcut. */
    static final int MERGE = 3;

    private AgilityShortcuts() { }

    static List<Report.Item> list(ConnectorSnapshot snapshot, Walking walking, Coverage coverage, Scope scope,
                                  Map<String, Integer> funnel) {
        List<ConnectorSnapshot.ShortcutIcon> icons = new ArrayList<>(snapshot.shortcutIcons);
        icons.sort(Comparator.comparingInt((ConnectorSnapshot.ShortcutIcon i) -> i.plane).thenComparingInt(i -> i.x).thenComparingInt(i -> i.y));
        List<ConnectorSnapshot.ShortcutIcon> kept = new ArrayList<>();
        int repeated = 0, covered = 0, outOfScope = 0, offGround = 0, unreached = 0;
        List<Report.Item> items = new ArrayList<>();
        for (ConnectorSnapshot.ShortcutIcon icon : icons) {
            if (kept.stream().anyMatch(k -> sameShortcut(k, icon))) { repeated++; continue; }
            kept.add(icon);
            if (coverage.originNear(icon.x, icon.y, icon.plane, COVER) || icon.obstacles.stream().anyMatch(coverage::covers)) {
                covered++;
                continue;
            }
            if (scope.excludes(icon.x, icon.y, icon.plane)) { outOfScope++; continue; }
            int stand = walking.stand(icon.x, icon.y, icon.plane, 1, 1, COVER);
            if (stand == Walking.NONE) { offGround++; continue; }
            if (!walking.isStructurallyReachable(walking.area(stand))) { unreached++; continue; }
            items.add(item(icon, stand, walking));
        }
        funnel.put("shortcut icons", icons.size());
        funnel.put("shortcut icons repeating another", repeated);
        funnel.put("shortcuts with a transport within " + COVER + " tiles", covered);
        funnel.put("shortcuts out of scope", outOfScope);
        funnel.put("shortcuts with no walkable tile within " + COVER, offGround);
        funnel.put("shortcuts in unreached areas", unreached);
        items.sort(Comparator.comparing((Report.Item i) -> i.shortcut.runelite == null).thenComparing(i -> i.title));
        funnel.put("listed agility shortcuts", items.size());
        return items;
    }

    private static Report.Item item(ConnectorSnapshot.ShortcutIcon icon, int stand, Walking walking) {
        Report.Item item = new Report.Item();
        item.kind = "shortcut";
        item.shortcut = icon;
        item.focus = new Report.Point(icon.x, icon.y, icon.plane);
        for (Connector obstacle : icon.obstacles) {
            int at = walking.stand(obstacle.x, obstacle.y, obstacle.plane, obstacle.sizeX, obstacle.sizeY, 1);
            Integer partner = UnreachedAreas.partnerStand(walking, obstacle);
            item.connectors.add(new Report.Candidate("shortcut", obstacle, Report.Point.of(at == Walking.NONE ? stand : at),
                partner == null ? null : Report.Point.of(partner)));
        }
        item.paired = item.connectors.stream().anyMatch(c -> c.partnerStand != null);
        item.title = (icon.description != null ? icon.description : "Agility shortcut") + " near " + icon.x + " " + icon.y + " " + icon.plane;
        item.id = Report.id("shortcut", icon.x + " " + icon.y + " " + icon.plane);
        return item;
    }

    private static boolean sameShortcut(ConnectorSnapshot.ShortcutIcon a, ConnectorSnapshot.ShortcutIcon b) {
        if (Math.abs(a.x - b.x) <= MERGE && Math.abs(a.y - b.y) <= MERGE) return true;
        for (Connector x : a.obstacles) {
            for (Connector y : b.obstacles) if (x.objectId == y.objectId && x.x == y.x && x.y == y.y && x.plane == y.plane) return true;
        }
        return false;
    }
}
