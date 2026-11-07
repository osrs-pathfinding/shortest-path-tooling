package shortestpath.dump.candidates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import shortestpath.candidates.Connector;
import shortestpath.candidates.ConnectorSnapshot;

/**
 * The dungeon entrance map icons: the map icon, other than the agility shortcut's, most often placed
 * within {@link #VOTE_REACH} tiles of the origin of a {@code transports.tsv} row that goes down or in
 * ({@link ConnectorPairing#DESCEND}; {@link MapIcons}). Each icon keeps the connectors within
 * {@link #CONNECTOR_REACH} tiles, the objects it most likely marks.
 */
final class DungeonIcons {
    static final int VOTE_REACH = 3;
    static final int CONNECTOR_REACH = 5;
    /** How close a row's origin must be for the marker report to count an icon as known. */
    static final int ROW_REACH = 5;

    final ConnectorSnapshot.IconMarker marker = new ConnectorSnapshot.IconMarker();
    final List<ConnectorSnapshot.EntranceIcon> icons = new ArrayList<>();

    DungeonIcons(CacheConnectors cache, int shortcutIcon) throws IOException {
        List<int[]> origins = MapIcons.origins("/transports/transports.tsv", ConnectorPairing.DESCEND::contains);
        int markerArea = MapIcons.vote(cache.icons, origins, VOTE_REACH, Set.of(shortcutIcon));
        for (int[] placed : cache.icons) {
            if (placed[0] != markerArea) continue;
            ConnectorSnapshot.EntranceIcon icon = new ConnectorSnapshot.EntranceIcon();
            icon.x = placed[1]; icon.y = placed[2]; icon.plane = placed[3];
            for (CacheConnectors.Placement p : cache.placements) {
                int d = p.connector.distance(icon.x, icon.y, icon.plane);
                if (d >= 0 && d <= CONNECTOR_REACH) icon.connectors.add(p.connector);
            }
            icons.add(icon);
        }
        icons.sort(Comparator.comparingInt((ConnectorSnapshot.EntranceIcon i) -> i.plane).thenComparingInt(i -> i.x).thenComparingInt(i -> i.y));
        marker.field = markerArea < 0 ? null : "mapAreaId=" + markerArea;
        marker.icons = icons.size();
        marker.iconsNearRows = (int) icons.stream().filter(i -> MapIcons.nearest(origins, i.x, i.y, i.plane) <= ROW_REACH).count();
    }
}
