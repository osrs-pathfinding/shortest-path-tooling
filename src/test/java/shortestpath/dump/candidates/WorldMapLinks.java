package shortestpath.dump.candidates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.runelite.cache.WorldMapManager;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Position;
import shortestpath.candidates.ConnectorSnapshot;

/**
 * The world map's links between its maps ({@link WorldMapManager#getIntermapLinks}): clicking a
 * dungeon's entrance icon opens the dungeon's map. Both directions are separate links. Their ends
 * are icon positions, not exact tiles.
 */
final class WorldMapLinks {
    private WorldMapLinks() { }

    static List<ConnectorSnapshot.MapLink> read(Store store) throws IOException {
        // The library's loader asserts on a field newer caches set; the links it reads are unaffected.
        WorldMapManager.class.getClassLoader().setClassAssertionStatus(WorldMapManager.class.getName(), false);
        WorldMapManager worldMap = new WorldMapManager(store);
        worldMap.load();
        List<ConnectorSnapshot.MapLink> links = new ArrayList<>();
        for (Map.Entry<Position, Position> link : worldMap.getIntermapLinks().entrySet()) {
            ConnectorSnapshot.MapLink mapLink = new ConnectorSnapshot.MapLink();
            mapLink.from = new ConnectorSnapshot.Tile(link.getKey().getX(), link.getKey().getY(), link.getKey().getZ());
            mapLink.to = new ConnectorSnapshot.Tile(link.getValue().getX(), link.getValue().getY(), link.getValue().getZ());
            links.add(mapLink);
        }
        links.sort(Comparator.comparingInt((ConnectorSnapshot.MapLink l) -> l.from.plane).thenComparingInt(l -> l.from.x)
            .thenComparingInt(l -> l.from.y).thenComparingInt(l -> l.to.x).thenComparingInt(l -> l.to.y));
        return links;
    }
}
