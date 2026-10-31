package shortestpath.sailingdata;

import shortestpath.pathfinder.sailing.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import static net.runelite.api.Constants.REGION_SIZE;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.IntDeque;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.sailing.BoatSpeed;
import shortestpath.sailing.SeaArea;

/**
 * Makes the collision map's sailing layer ({@link SailingWaters}) from the dumper's {@value SailingWaters#DUMPER_LAYER}
 * layer (each tile's sea water and area, from the floor textures) and the collision flags.
 * <p>
 * The sea is every tile of sea water reachable, by open tile edges between water tiles, from the water a few tiles
 * around a berth. The collision flags alone can't tell water from land: open sea is as walkable as a beach.
 * <p>
 * A kind's bodies of water come from every move a boat of its kind could make: holding any heading for the fewest ticks
 * that lands on a whole tile, clear for the kind's hull at its tile's centre, at every base speed up to the fastest
 * (every whole number of 128ths of a tile per tick). Each such move joins its two ends into one body, within one area; a
 * move from one area to another links their bodies. Only bodies of at least {@value #MIN_BODY_TILES} tiles that a boat
 * can get to from some berth, through such bodies, are kept: the rest, coastal pockets and pools no mooring reaches, are
 * taken to be unreachable.
 */
final class SailingLayerBuilder
{
	/** The fewest tiles a body of water keeps: smaller ones, coastal pockets, are taken to be unreachable. */
	static final int MIN_BODY_TILES = 100;

	private static final int REGION_SHIFT = Integer.numberOfTrailingZeros(REGION_SIZE);
	private static final int REGION_MASK = REGION_SIZE - 1;
	private static final int TILES = REGION_SIZE * REGION_SIZE;
	// How far around a berth the water starts, in tiles: the berth and the jetty can stand between them
	private static final int BERTH_REACH = 3;
	// Entries' times, so the same layers make the same zip
	private static final long ENTRY_TIME = 315532800000L;

	// The sea's tiles, all on plane 0, and each one's index among them by region
	private final int[] tiles;
	private final Map<Integer, int[]> indexByRegion = new HashMap<>();
	// Each sea tile's area code (SailingWaters.areaCode)
	private final int[] areaCodes;

	private SailingLayerBuilder(int[] tiles, Map<Integer, Integer> water)
	{
		this.tiles = tiles;
		areaCodes = new int[tiles.length];
		for (int i = 0; i < tiles.length; i++)
		{
			indexByRegion.computeIfAbsent(regionKey(tiles[i]), k ->
			{
				int[] page = new int[TILES];
				Arrays.fill(page, -1);
				return page;
			})[SailingWaters.indexInRegion(tiles[i])] = i;
			areaCodes[i] = water.get(tiles[i]);
		}
	}

	/**
	 * The sailing layer for the collision map {@code flags}, by entry name within it ({@value SailingWaters#TILES} and
	 * {@value SailingWaters#INDEX}), with {@code inputs} as its fingerprint.
	 */
	static SortedMap<String, byte[]> build(SplitFlagMap flags, List<SailingMooring> moorings, String inputs,
		int threads) throws Exception
	{
		CollisionMap map = new CollisionMap(flags);
		Map<Integer, Integer> water = water(flags.layers(SailingWaters.DUMPER_LAYER));
		int[] sea = flood(map, water, moorings);
		System.err.printf("sea: %d tiles%n", sea.length);
		SailingLayerBuilder builder = new SailingLayerBuilder(sea, water);
		int[] numbers = builder.areaCodes.clone();
		StringBuilder index = new StringBuilder(SailingWaters.INPUTS + "\t" + inputs + "\n");
		int[][] moves = moves();
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try
		{
			for (SailingVessel.Kind kind : SailingVessel.Kind.values())
			{
				long started = System.nanoTime();
				builder.addBodies(kind, moves, moorings, pool, threads, flags, numbers, index);
				System.err.printf("%s bodies at %.1f s%n", kind, (System.nanoTime() - started) / 1e9);
			}
		}
		finally
		{
			pool.shutdownNow();
		}
		SortedMap<Integer, int[]> byRegion = new TreeMap<>();
		for (int i = 0; i < sea.length; i++)
			byRegion.computeIfAbsent(regionKey(sea[i]), k -> new int[TILES])[SailingWaters.indexInRegion(sea[i])] = numbers[i];
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes))
		{
			TileLayer.write(out, byRegion);
		}
		SortedMap<String, byte[]> layer = new TreeMap<>();
		layer.put(SailingWaters.TILES, bytes.toByteArray());
		layer.put(SailingWaters.INDEX, index.toString().getBytes(StandardCharsets.UTF_8));
		return layer;
	}

	/**
	 * {@code zip} with its sailing layer replaced by {@code layer}: the entries in name order, with fixed times, so the
	 * same contents make the same bytes.
	 */
	static byte[] withLayer(byte[] zip, SortedMap<String, byte[]> layer) throws IOException
	{
		SortedMap<String, byte[]> entries = new TreeMap<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip)))
		{
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null)
			{
				if (entry.isDirectory() || entry.getName().startsWith(SailingWaters.LAYER + "/")) continue;
				entries.put(entry.getName(), in.readAllBytes());
			}
		}
		for (Map.Entry<String, byte[]> entry : layer.entrySet())
			entries.put(SailingWaters.LAYER + "/" + entry.getKey(), entry.getValue());
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream out = new ZipOutputStream(bytes))
		{
			for (Map.Entry<String, byte[]> entry : entries.entrySet())
			{
				ZipEntry written = new ZipEntry(entry.getKey());
				written.setTime(ENTRY_TIME);
				out.putNextEntry(written);
				out.write(entry.getValue());
				out.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

	// The dumper's layer as each sea water tile's area code (SailingWaters.areaCode), by packed plane-0 tile
	private static Map<Integer, Integer> water(Map<String, byte[]> layer)
	{
		byte[] legend = layer.get(SailingWaters.LEGEND);
		if (legend == null) throw new IllegalStateException("the collision map has no sea layer");
		int[] codes = new int[256];
		for (String line : new String(legend, StandardCharsets.UTF_8).split("\n"))
		{
			if (line.isBlank()) continue;
			String[] fields = line.split("\t");
			String name = fields[1].trim();
			codes[Integer.parseInt(fields[0].trim())] = SailingWaters.areaCode(name.equals(SailingWaters.OPEN) ? null
				: SeaArea.valueOf(name));
		}
		Map<Integer, Integer> water = new HashMap<>();
		for (Map.Entry<String, byte[]> region : layer.entrySet())
		{
			if (region.getKey().equals(SailingWaters.LEGEND)) continue;
			String[] name = region.getKey().split("_");
			int baseX = Integer.parseInt(name[0]) << REGION_SHIFT, baseY = Integer.parseInt(name[1]) << REGION_SHIFT;
			byte[] bytes = region.getValue();
			for (int i = 0; i < bytes.length; i++)
			{
				int code = codes[bytes[i] & 0xFF];
				if (code != 0) water.put(WorldPointUtil.packWorldPoint(baseX + (i & REGION_MASK), baseY + (i >> REGION_SHIFT), 0), code);
			}
		}
		return water;
	}

	// The sea: every water tile reachable by open edges between water tiles from the water round a berth, sorted
	private static int[] flood(CollisionMap map, Map<Integer, Integer> water, List<SailingMooring> moorings)
	{
		Set<Integer> sea = new HashSet<>();
		IntDeque queue = new IntDeque(4096);
		for (SailingMooring mooring : moorings)
		{
			int berth = mooring.berthTile();
			int x = WorldPointUtil.unpackWorldX(berth), y = WorldPointUtil.unpackWorldY(berth);
			int plane = WorldPointUtil.unpackWorldPlane(berth);
			for (int dy = -BERTH_REACH; dy <= BERTH_REACH; dy++)
			{
				for (int dx = -BERTH_REACH; dx <= BERTH_REACH; dx++)
				{
					int tile = WorldPointUtil.packWorldPoint(x + dx, y + dy, plane);
					if (map.isBlocked(x + dx, y + dy, plane) || !water.containsKey(tile) || !sea.add(tile)) continue;
					queue.addLast(tile);
					while (!queue.isEmpty())
					{
						int next = queue.pollFirst();
						int tx = WorldPointUtil.unpackWorldX(next), ty = WorldPointUtil.unpackWorldY(next);
						int tz = WorldPointUtil.unpackWorldPlane(next);
						if (map.w(tx, ty, tz)) add(water, sea, queue, tx - 1, ty, tz);
						if (map.e(tx, ty, tz)) add(water, sea, queue, tx + 1, ty, tz);
						if (map.s(tx, ty, tz)) add(water, sea, queue, tx, ty - 1, tz);
						if (map.n(tx, ty, tz)) add(water, sea, queue, tx, ty + 1, tz);
					}
				}
			}
		}
		int[] tiles = sea.stream().mapToInt(Integer::intValue).toArray();
		Arrays.sort(tiles);
		for (int tile : tiles)
		{
			if (WorldPointUtil.unpackWorldPlane(tile) != 0) throw new IllegalStateException("sea off plane 0 at " + tile);
		}
		return tiles;
	}

	private static void add(Map<Integer, Integer> water, Set<Integer> sea, IntDeque queue, int x, int y, int z)
	{
		int tile = WorldPointUtil.packWorldPoint(x, y, z);
		if (water.containsKey(tile) && sea.add(tile)) queue.addLast(tile);
	}

	// Every move a boat makes at some base speed: {heading, dx, dy}, shortest first
	private static int[][] moves()
	{
		Set<List<Integer>> moves = new LinkedHashSet<>();
		for (int units = 1; units <= SailingMoves.MAX_SPEED * BoatSpeed.UNITS; units++)
		{
			SailingMoves table = SailingMoves.forSpeed(units / (double) BoatSpeed.UNITS);
			for (int move = 0; move < table.size(); move++)
				moves.add(List.of(table.heading(move), table.dx(move), table.dy(move)));
		}
		List<int[]> sorted = new ArrayList<>();
		for (List<Integer> move : moves) sorted.add(new int[] {move.get(0), move.get(1), move.get(2)});
		sorted.sort((a, b) -> Integer.compare(a[1] * a[1] + a[2] * a[2], b[1] * b[1] + b[2] * b[2]));
		System.err.printf("%d moves%n", sorted.size());
		return sorted.toArray(new int[0][]);
	}

	/**
	 * Adds a kind's bodies of water: each kept body's number into {@code numbers} (at its kind's place,
	 * {@link SailingWaters#bodyShift}), and a line per body to {@code index}. Every move the kind's hull clears joins both
	 * its ends, within an area; a move from one area to another links their bodies.
	 */
	private void addBodies(SailingVessel.Kind kind, int[][] moves, List<SailingMooring> moorings, ExecutorService pool,
		int threads, SplitFlagMap flags, int[] numbers, StringBuilder index) throws Exception
	{
		SailingVessel vessel = SailingVessel.of(kind, BoatSpeed.ESTIMATED);
		int n = tiles.length;
		// Each thread takes its share of the moves: it joins the ends of those within an area, and keeps those from one
		// area to another as {from, to} tile pairs
		List<Future<Object[]>> parts = new ArrayList<>();
		for (int t = 0; t < threads; t++)
		{
			int share = t;
			parts.add(pool.submit(() ->
			{
				CollisionMap own = new CollisionMap(flags);
				// At its tile's centre, as the model has it
				BoatHull hull = vessel.hull();
				int[] parent = identity(n);
				BitSet touched = new BitSet(n);
				List<int[]> across = new ArrayList<>();
				for (int m = share; m < moves.length; m += threads)
				{
					int[] move = moves[m];
					for (int i = 0; i < n; i++)
					{
						int j = indexOf(tiles[i], move[1], move[2]);
						if (j < 0) continue;
						boolean within = areaCodes[i] == areaCodes[j];
						// Already joined: nothing to learn
						if (within && touched.get(i) && touched.get(j) && find(parent, i) == find(parent, j)) continue;
						if (!hull.canMove(own, x(i), y(i), 0, move[0], move[1], move[2])) continue;
						touched.set(i);
						touched.set(j);
						if (within) union(parent, i, j);
						else across.add(new int[] {i, j});
					}
				}
				return new Object[] {parent, touched, across};
			}));
		}
		int[] parent = identity(n);
		BitSet touched = new BitSet(n);
		List<int[]> across = new ArrayList<>();
		for (Future<Object[]> part : parts)
		{
			Object[] result = part.get();
			int[] own = (int[]) result[0];
			for (int i = 0; i < n; i++)
			{
				int root = find(own, i);
				if (root != i) union(parent, i, root);
			}
			touched.or((BitSet) result[1]);
			@SuppressWarnings("unchecked") List<int[]> pairs = (List<int[]>) result[2];
			across.addAll(pairs);
		}
		// Each body by its root: its size, its area, and the bodies moves join it to
		Map<Integer, Integer> size = new HashMap<>(), area = new HashMap<>();
		for (int i = touched.nextSetBit(0); i >= 0; i = touched.nextSetBit(i + 1))
		{
			size.merge(find(parent, i), 1, Integer::sum);
			area.putIfAbsent(find(parent, i), areaCodes[i]);
		}
		Map<Integer, Set<Integer>> links = new HashMap<>();
		for (int[] pair : across)
		{
			int a = find(parent, pair[0]), b = find(parent, pair[1]);
			links.computeIfAbsent(a, k -> new HashSet<>()).add(b);
			links.computeIfAbsent(b, k -> new HashSet<>()).add(a);
		}
		// Keep the big enough bodies a boat gets to from a berth: those within docking range of one, and those linked to
		// them through kept bodies
		Deque<Integer> queue = new ArrayDeque<>();
		Set<Integer> kept = new HashSet<>();
		for (SailingMooring mooring : moorings)
		{
			for (int tile : mooring.dockingTiles())
			{
				int i = indexOf(tile, 0, 0);
				if (i < 0 || !touched.get(i)) continue;
				int root = find(parent, i);
				if (size.get(root) >= MIN_BODY_TILES && kept.add(root)) queue.add(root);
			}
		}
		while (!queue.isEmpty())
		{
			for (int next : links.getOrDefault(queue.poll(), Set.of()))
			{
				if (size.get(next) >= MIN_BODY_TILES && kept.add(next)) queue.add(next);
			}
		}
		if (kept.size() > SailingWaters.MAX_BODIES)
			throw new IllegalStateException(kind + " has " + kept.size() + " bodies of water, more than a tile can number");
		// Number the kept bodies from 1 in tile order
		Map<Integer, Integer> number = new HashMap<>();
		List<Integer> roots = new ArrayList<>();
		int shift = SailingWaters.bodyShift(kind);
		int tilesKept = 0;
		for (int i = touched.nextSetBit(0); i >= 0; i = touched.nextSetBit(i + 1))
		{
			int root = find(parent, i);
			if (!kept.contains(root)) continue;
			Integer body = number.get(root);
			if (body == null)
			{
				body = number.size() + 1;
				number.put(root, body);
				roots.add(root);
			}
			numbers[i] |= body << shift;
			tilesKept++;
		}
		for (int root : roots)
		{
			int code = area.get(root);
			Set<Integer> to = new TreeSet<>();
			for (int next : links.getOrDefault(root, Set.of())) if (number.containsKey(next)) to.add(number.get(next));
			StringBuilder line = new StringBuilder();
			for (int next : to) line.append(line.length() == 0 ? "" : ",").append(next);
			index.append(kind.name()).append('\t').append(number.get(root)).append('\t')
				.append(code == 1 ? SailingWaters.OPEN : SeaArea.values()[code - 2].name()).append('\t').append(line).append('\n');
		}
		System.err.printf("%s: %d bodies, %d kept over %d tiles%n", kind, size.size(), kept.size(), tilesKept);
	}

	private int x(int i)
	{
		return WorldPointUtil.unpackWorldX(tiles[i]);
	}

	private int y(int i)
	{
		return WorldPointUtil.unpackWorldY(tiles[i]);
	}

	// The index of the sea tile (dx, dy) from tile, or -1 if that isn't sea
	private int indexOf(int tile, int dx, int dy)
	{
		int x = WorldPointUtil.unpackWorldX(tile) + dx, y = WorldPointUtil.unpackWorldY(tile) + dy;
		int[] page = indexByRegion.get(((x >> REGION_SHIFT) << 16) | (y >> REGION_SHIFT));
		return page == null ? -1 : page[((y & REGION_MASK) << REGION_SHIFT) | (x & REGION_MASK)];
	}

	private static int regionKey(int tile)
	{
		return ((WorldPointUtil.unpackWorldX(tile) >> REGION_SHIFT) << 16) | (WorldPointUtil.unpackWorldY(tile) >> REGION_SHIFT);
	}

	private static int[] identity(int n)
	{
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) parent[i] = i;
		return parent;
	}

	private static int find(int[] parent, int i)
	{
		while (parent[i] != i)
		{
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	private static void union(int[] parent, int a, int b)
	{
		int ra = find(parent, a), rb = find(parent, b);
		if (ra == rb) return;
		if (ra < rb) parent[rb] = ra;
		else parent[ra] = rb;
	}
}
