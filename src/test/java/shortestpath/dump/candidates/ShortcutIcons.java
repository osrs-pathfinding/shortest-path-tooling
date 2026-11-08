package shortestpath.dump.candidates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.AgilityShortcut;
import shortestpath.candidates.Connector;
import shortestpath.candidates.ConnectorSnapshot;

/**
 * The agility shortcut map icons: the map icon most often placed within {@link #VOTE_REACH} tiles of
 * an {@code agility_shortcuts.tsv} origin ({@link MapIcons}).
 *
 * <p>Each icon is matched to the nearest entry of RuneLite's {@link AgilityShortcut} enum (BSD-2,
 * used at build time only) within {@link #RUNELITE_REACH} tiles, for a description, a level and
 * obstacle ids. Its obstacles are the placements of those ids within {@link #RUNELITE_REACH} tiles,
 * or else the connectors within {@link #OBSTACLE_REACH} tiles.
 */
final class ShortcutIcons {
    static final int VOTE_REACH = 3;
    static final int RUNELITE_REACH = 10;
    static final int OBSTACLE_REACH = 5;
    /** How close a shortcut row's origin must be for the marker report to count an icon as known. */
    static final int ROW_REACH = 5;

    final ConnectorSnapshot.IconMarker marker = new ConnectorSnapshot.IconMarker();
    /** The shortcut icon's {@code mapAreaId}s, or none. */
    final Set<Integer> markerAreas;
    final List<ConnectorSnapshot.ShortcutIcon> icons = new ArrayList<>();
    /** Icons matched to a RuneLite entry, and the entries matched. */
    int matched;
    final Set<AgilityShortcut> known = new HashSet<>();

    /** The object ids RuneLite lists as shortcut obstacles; {@link CacheConnectors} collects their placements. */
    static Set<Integer> obstacleIds() {
        Set<Integer> ids = new HashSet<>();
        for (AgilityShortcut shortcut : AgilityShortcut.values()) for (int id : shortcut.getObstacleIds()) ids.add(id);
        return ids;
    }

    ShortcutIcons(CacheConnectors cache, Map<Integer, Integer> categories) throws IOException {
        List<int[]> origins = MapIcons.origins("/transports/agility_shortcuts.tsv", option -> true);
        markerAreas = MapIcons.sameKind(MapIcons.vote(cache.icons, origins, VOTE_REACH, Set.of()), categories);
        for (int[] placed : cache.icons) {
            if (markerAreas.contains(placed[0])) icons.add(icon(placed, cache));
        }
        icons.sort(Comparator.comparingInt((ConnectorSnapshot.ShortcutIcon i) -> i.plane).thenComparingInt(i -> i.x).thenComparingInt(i -> i.y));
        marker.field = MapIcons.field(markerAreas);
        marker.icons = icons.size();
        marker.iconsNearRows = (int) icons.stream().filter(i -> MapIcons.nearest(origins, i.x, i.y, i.plane) <= ROW_REACH).count();
    }

    private ConnectorSnapshot.ShortcutIcon icon(int[] placed, CacheConnectors cache) {
        ConnectorSnapshot.ShortcutIcon icon = new ConnectorSnapshot.ShortcutIcon();
        icon.x = placed[1]; icon.y = placed[2]; icon.plane = placed[3];
        AgilityShortcut shortcut = runelite(icon);
        if (shortcut != null) {
            matched++;
            known.add(shortcut);
            icon.runelite = shortcut.name();
            icon.description = shortcut.getDescription();
            icon.level = shortcut.getLevel();
            for (int id : shortcut.getObstacleIds()) {
                for (Connector o : cache.obstaclesById.getOrDefault(id, List.of())) if (near(o, icon, RUNELITE_REACH)) icon.obstacles.add(o);
            }
        }
        if (icon.obstacles.isEmpty()) {
            for (CacheConnectors.Placement p : cache.placements) if (near(p.connector, icon, OBSTACLE_REACH)) icon.obstacles.add(p.connector);
        }
        return icon;
    }

    /** The RuneLite shortcut whose location is nearest the icon, within {@link #RUNELITE_REACH} tiles, or null. */
    private static AgilityShortcut runelite(ConnectorSnapshot.ShortcutIcon icon) {
        AgilityShortcut best = null;
        int bestDistance = RUNELITE_REACH + 1;
        for (AgilityShortcut shortcut : AgilityShortcut.values()) {
            for (WorldPoint p : new WorldPoint[] {shortcut.getWorldLocation(), shortcut.getWorldMapLocation()}) {
                if (p == null || p.getPlane() != icon.plane) continue;
                int d = Math.max(Math.abs(p.getX() - icon.x), Math.abs(p.getY() - icon.y));
                if (d < bestDistance) { best = shortcut; bestDistance = d; }
            }
        }
        return best;
    }

    private static boolean near(Connector c, ConnectorSnapshot.ShortcutIcon icon, int reach) {
        int d = c.distance(icon.x, icon.y, icon.plane);
        return d >= 0 && d <= reach;
    }
}
