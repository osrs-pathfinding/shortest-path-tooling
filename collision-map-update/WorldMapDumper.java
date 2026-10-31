package net.runelite.cache;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.cache.definitions.AbstractWorldMapDataDefinition;
import net.runelite.cache.definitions.MapSquareDefinition;
import net.runelite.cache.definitions.WorldMapCompositeDefinition;
import net.runelite.cache.definitions.ZoneDefinition;
import net.runelite.cache.definitions.loaders.WorldMapCompositeLoader;
import net.runelite.cache.definitions.loaders.WorldMapLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

/**
 * World map area dumper
 *
 * Underground world maps draw some areas away from their real tiles, and some
 * planes side by side. For each map this writes the drawn rectangles that are
 * not their own tiles on plane 0, with the real tile's offset and the lowest
 * drawn plane, so the plugin can turn a world map click into the real tile.
 * Run "java -cp cache.jar net.runelite.cache.WorldMapDumper ../cache ../world-map-areas.tsv"
 */
public class WorldMapDumper
{
	public static void main(String[] args) throws IOException
	{
		try (Store store = new Store(new File(args[0])); PrintWriter out = new PrintWriter(args[1], "UTF-8"))
		{
			store.load();
			// The world map index is unnamed: archive 0 holds the map details, 1 the composite maps
			Index index = store.getIndex(IndexType.WORLDMAP);
			Map<Integer, String> names = new HashMap<>();
			for (FSFile file : files(store, index.getArchive(0)))
			{
				names.put(file.getFileId(), new WorldMapLoader().load(file.getContents(), file.getFileId()).name);
			}
			WorldMapCompositeLoader loader = new WorldMapCompositeLoader().configureForRevision(index.getRevision());
			out.println("#map\tx\ty\twidth\theight\tdx\tdy\tplane");
			for (FSFile file : files(store, index.getArchive(1)))
			{
				WorldMapCompositeDefinition map = loader.load(file.getContents());
				String name = names.get(file.getFileId());
				// Drawn 8x8 zone (y << 16 | x) -> {dx, dy, lowest plane}: the real tile is drawn + (dx, dy)
				TreeMap<Integer, int[]> zones = new TreeMap<>();
				for (MapSquareDefinition square : map.getMapSquareDefinitions())
				{
					for (int i = 0; i < 64; i++)
					{
						add(name, zones, square, i % 8, i / 8, i % 8, i / 8);
					}
				}
				for (ZoneDefinition zone : map.getZoneDefinitions())
				{
					add(name, zones, zone, zone.getDisplayZoneX(), zone.getDisplayZoneZ(), zone.getSourceZoneX(), zone.getSourceZoneZ());
				}
				zones.values().removeIf(v -> v[0] == 0 && v[1] == 0 && v[2] == 0);
				while (!zones.isEmpty())
				{
					int key = zones.firstKey();
					int[] v = zones.get(key);
					int x = key & 0xffff;
					int y = key >> 16;
					int w = 1;
					int h = 1;
					while (same(zones, x + w, y, 1, v))
					{
						w++;
					}
					while (same(zones, x, y + h, w, v))
					{
						h++;
					}
					for (int i = 0; i < w * h; i++)
					{
						zones.remove((y + i / w) << 16 | (x + i % w));
					}
					out.println(name + "\t" + x * 8 + "\t" + y * 8 + "\t" + w * 8 + "\t" + h * 8 + "\t" + v[0] + "\t" + v[1] + "\t" + v[2]);
				}
			}
		}
	}

	private static Collection<FSFile> files(Store store, Archive archive) throws IOException
	{
		return archive.getFiles(store.getStorage().loadArchive(archive)).getFiles();
	}

	private static void add(String name, TreeMap<Integer, int[]> zones, AbstractWorldMapDataDefinition d,
		int displayZoneX, int displayZoneY, int sourceZoneX, int sourceZoneY)
	{
		int x = d.getDisplaySquareX() * 8 + displayZoneX;
		int y = d.getDisplaySquareZ() * 8 + displayZoneY;
		int dx = (d.getSourceSquareX() * 8 + sourceZoneX - x) * 8;
		int dy = (d.getSourceSquareZ() * 8 + sourceZoneY - y) * 8;
		int[] old = zones.putIfAbsent(y << 16 | x, new int[]{dx, dy, d.getMinLevel()});
		if (old != null && (old[0] != dx || old[1] != dy))
		{
			System.out.println("Warning: " + name + " draws two areas at " + x * 8 + ", " + y * 8);
		}
		else if (old != null)
		{
			old[2] = Math.min(old[2], d.getMinLevel());
		}
	}

	private static boolean same(TreeMap<Integer, int[]> zones, int x, int y, int w, int[] v)
	{
		for (int i = 0; i < w; i++)
		{
			if (!Arrays.equals(zones.get(y << 16 | (x + i)), v))
			{
				return false;
			}
		}
		return true;
	}
}
