package shortestpath.dump;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import org.junit.Assume;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.sailing.SailingMooring;
import shortestpath.pathfinder.sailing.SailingMoorings;

/** Every buoy in the map, by the nearest berth, with the box the dock buoys round each make. DOCK_CACHE, DOCK_KEYS. */
public class SailingDockBuoysTest
{
	@Test
	public void dump() throws Exception
	{
		String cacheDir = System.getenv("DOCK_CACHE"), keys = System.getenv("DOCK_KEYS");
		Assume.assumeTrue(cacheDir != null && keys != null);
		try (Store store = CacheUtils.openStore(cacheDir))
		{
			ObjectManager objects = new ObjectManager(store);
			objects.load();
			RegionLoader regions = CacheUtils.loadRegions(store, CacheUtils.loadXteaKeys(keys));
			Map<String, List<int[]>> byMooring = new TreeMap<>();
			Map<Integer, String> kinds = new TreeMap<>();
			for (Region region : regions.getRegions())
			{
				for (Location loc : region.getLocations())
				{
					ObjectDefinition def = objects.getObject(loc.getId());
					if (def == null || def.getName() == null || !def.getName().toLowerCase().contains("buoy")) continue;
					String ops = "";
					if (def.getOps() != null && def.getOps().ops != null)
						for (net.runelite.cache.EntityOpsDefinition.Op op : def.getOps().ops) if (op != null && op.text != null) ops += op.text + "/";
					kinds.put(loc.getId(), def.getName() + " " + ops + " size " + def.getSizeX() + "x" + def.getSizeY());
					int x = loc.getPosition().getX(), y = loc.getPosition().getY();
					SailingMooring nearest = null;
					double best = Double.MAX_VALUE;
					for (SailingMooring m : SailingMoorings.all())
					{
						double d = Math.hypot(WorldPointUtil.unpackWorldX(m.berthTile()) - x, WorldPointUtil.unpackWorldY(m.berthTile()) - y);
						if (d < best) { best = d; nearest = m; }
					}
					String key = best <= 60 ? nearest.name() : "(none within 60)";
					byMooring.computeIfAbsent(key, k -> new ArrayList<>()).add(new int[] {loc.getId(), x, y, loc.getPosition().getZ(), (int) best});
				}
			}
			kinds.forEach((id, d) -> System.out.println("BUOY kind " + id + " " + d));
			for (SailingMooring m : SailingMoorings.all())
			{
				List<int[]> list = byMooring.getOrDefault(m.name(), List.of());
				int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, dockBuoys = 0;
				StringBuilder other = new StringBuilder();
				for (int[] b : list)
				{
					if (kinds.get(b[0]).startsWith("Dock buoy"))
					{
						dockBuoys++;
						minX = Math.min(minX, b[1]); maxX = Math.max(maxX, b[1]); minY = Math.min(minY, b[2]); maxY = Math.max(maxY, b[2]);
					}
					else other.append(b[0]).append('@').append(b[1]).append(',').append(b[2]).append(' ');
				}
				System.out.printf("BUOY %-24s berth %d,%d: %d dock buoys%s; other %s%n", m.name(), WorldPointUtil.unpackWorldX(m.berthTile()),
					WorldPointUtil.unpackWorldY(m.berthTile()), dockBuoys,
					dockBuoys == 0 ? "" : String.format(" box %d..%d x %d..%d (%dx%d)", minX, maxX, minY, maxY, maxX - minX + 1, maxY - minY + 1), other);
			}
			System.out.println("BUOY unassigned " + byMooring.getOrDefault("(none within 60)", List.of()).size());
		}
	}
}
