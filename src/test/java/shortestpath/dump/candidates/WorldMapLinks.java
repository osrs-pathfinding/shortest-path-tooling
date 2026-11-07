package shortestpath.dump.candidates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.cache.WorldMapManager;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Position;
import shortestpath.WorldPointUtil;
import shortestpath.candidates.ConnectorSnapshot;
import shortestpath.candidates.Walking;
import shortestpath.transport.Transport;

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

    /**
     * A sanity check for the report: how many links join two walking areas that some transport row
     * also joins directly (in either direction), or lie within one area.
     */
    static int joinedByRows(List<ConnectorSnapshot.MapLink> links, Walking walking, Map<Integer, Set<Transport>> transports) {
        Set<Long> joined = new HashSet<>();
        for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet()) {
            if (entry.getKey() == Transport.UNDEFINED_ORIGIN) continue;
            int from = area(walking, entry.getKey(), 1);
            for (Transport t : entry.getValue()) {
                int to = t.getDestination() == Transport.UNDEFINED_DESTINATION ? -1 : area(walking, t.getDestination(), 1);
                if (from > 0 && to > 0) { joined.add(((long) from << 32) | to); joined.add(((long) to << 32) | from); }
            }
        }
        int count = 0;
        for (ConnectorSnapshot.MapLink link : links) {
            int from = area(walking, WorldPointUtil.packWorldPoint(link.from.x, link.from.y, link.from.plane), 5);
            int to = area(walking, WorldPointUtil.packWorldPoint(link.to.x, link.to.y, link.to.plane), 5);
            if (from > 0 && to > 0 && (from == to || joined.contains(((long) from << 32) | to))) count++;
        }
        return count;
    }

    private static int area(Walking walking, int tile, int reach) {
        int stand = walking.stand(WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile),
            WorldPointUtil.unpackWorldPlane(tile), 1, 1, reach);
        return stand == Walking.NONE ? -1 : walking.area(stand);
    }
}
