package shortestpath.dump.candidates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.cache.AreaManager;
import net.runelite.cache.WorldMapManager;
import net.runelite.cache.definitions.AreaDefinition;
import net.runelite.cache.definitions.WorldMapElementDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Position;
import shortestpath.candidates.ConnectorSnapshot;

/**
 * The world map's place names ({@link WorldMapManager#getElements} whose map area has a name, such as
 * {@code Ancient<br>Forge}), which title the unreached areas they sit in.
 */
final class WorldMapLabels {
    private WorldMapLabels() { }

    static List<ConnectorSnapshot.Label> read(Store store) throws IOException {
        AreaManager areas = new AreaManager(store);
        areas.load();
        WorldMapManager.class.getClassLoader().setClassAssertionStatus(WorldMapManager.class.getName(), false);
        WorldMapManager worldMap = new WorldMapManager(store);
        worldMap.load();
        List<ConnectorSnapshot.Label> labels = new ArrayList<>();
        for (WorldMapElementDefinition element : worldMap.getElements()) {
            AreaDefinition area = areas.getArea(element.getAreaDefinitionId());
            if (area == null || area.getName() == null || area.getName().isBlank()) continue;
            Position at = element.getWorldPosition();
            ConnectorSnapshot.Label label = new ConnectorSnapshot.Label();
            label.name = area.getName().replaceAll("(?i)<br>", " ").replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
            label.x = at.getX(); label.y = at.getY(); label.plane = at.getZ();
            labels.add(label);
        }
        labels.sort(Comparator.comparingInt((ConnectorSnapshot.Label l) -> l.plane).thenComparingInt(l -> l.x)
            .thenComparingInt(l -> l.y).thenComparing(l -> l.name));
        return labels;
    }
}
