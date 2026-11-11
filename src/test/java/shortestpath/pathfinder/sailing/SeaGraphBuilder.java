package shortestpath.pathfinder.sailing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.sailing.BoatSpeed;
import shortestpath.sailing.SeaArea;

/**
 * Builds a kind's {@link SeaGraph} from the sea, for {@code SailingDataGenerator}: traces the kind's water outline on
 * the tiles' edges, smooths it, links the corners and launches that see each other, and sails each run near the coast
 * on the tiles, slowing where it must. It takes about a minute for every kind, so the plugin ships the graphs, and this
 * lives in the plugin's package to fill a graph's fields directly.
 * <p>
 * Lattice point (i, j) of a traced outline is the corner (i - 0.5, j - 0.5): the lower left corner of tile (i, j).
 */
public final class SeaGraphBuilder
{
	private static final double TOLERANCE = SeaGraph.TOLERANCE, NEAR = SeaGraph.NEAR, MARGIN = SeaGraph.MARGIN;
	// The nudges tried, largest first: a corner in a narrow passage gets less margin
	private static final double[] NUDGES = {MARGIN, 1.5, 0.5};
	private static final int CORRIDOR = SeaGraph.CORRIDOR;
	private static final BooleanSupplier NEVER = () -> false;

	private SeaGraphBuilder()
	{
	}

	/** Free tiles on a bounded grid. */
	static final class Grid
	{
		final int minX, minY, width, height;
		final long[] bits;

		Grid(int minX, int minY, int width, int height)
		{
			this.minX = minX;
			this.minY = minY;
			this.width = width;
			this.height = height;
			bits = new long[(int) (((long) width * height + 63) / 64)];
		}

		void set(int x, int y)
		{
			long i = (long) (x - minX) * height + (y - minY);
			bits[(int) (i >>> 6)] |= 1L << (i & 63);
		}

		boolean free(int x, int y)
		{
			if (x < minX || y < minY || x >= minX + width || y >= minY + height) return false;
			long i = (long) (x - minX) * height + (y - minY);
			return (bits[(int) (i >>> 6)] & (1L << (i & 63))) != 0;
		}
	}

	/** {@code kind}'s graph over {@code waters}, with a launch for each of {@code moorings} it can cast off from. */
	public static SeaGraph build(SailingWaters waters, SailingVessel.Kind kind, List<SailingMooring> moorings)
	{
		SailingVessel vessel = SailingVessel.of(kind, BoatSpeed.FASTEST).fullyFitted();
		IntPredicate landing = waters.landing(vessel);
		CollisionMap map = waters.map();
		int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		List<Integer> tiles = new ArrayList<>();
		waters.forEachTile(tile ->
		{
			if (WorldPointUtil.unpackWorldPlane(tile) != 0 || !landing.test(tile)) return;
			tiles.add(tile);
			int x = WorldPointUtil.unpackWorldX(tile), y = WorldPointUtil.unpackWorldY(tile);
			box[0] = Math.min(box[0], x);
			box[1] = Math.min(box[1], y);
			box[2] = Math.max(box[2], x);
			box[3] = Math.max(box[3], y);
		});
		int m = moorings.size();
		if (tiles.isEmpty())
		{
			return assemble(kind, new ArrayList<>(), new ArrayList<>(), new int[0][], new byte[0][], new int[0][], fill(m, -1),
				new float[m * m]);
		}
		Grid water = new Grid(box[0] - 1, box[1] - 1, box[2] - box[0] + 3, box[3] - box[1] + 3);
		for (int tile : tiles) water.set(WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile));

		// The coast, smoothed, and its corners
		double[][] berths = new double[m][];
		for (int k = 0; k < m; k++)
		{
			int berth = moorings.get(k).berthTile();
			berths[k] = new double[] {WorldPointUtil.unpackWorldX(berth), WorldPointUtil.unpackWorldY(berth)};
		}
		List<double[]> rings = smooth(trace(water), berths);
		List<double[]> nodes = new ArrayList<>();
		SeaGraph coast = assemble(kind, nodes, rings, new int[0][], new byte[0][], new int[0][], fill(m, -1), new float[0]);
		for (double[] ring : rings) corners(ring, coast, nodes);
		// The corners of the open water round each restricted area, for boats without its part
		for (SeaArea area : SeaArea.values())
		{
			if (!kind.fits(area)) continue;
			Grid outside = new Grid(water.minX, water.minY, water.width, water.height);
			boolean any = false;
			for (int tile : tiles)
			{
				if (waters.area(tile) == area) any = true;
				else outside.set(WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile));
			}
			if (!any) continue;
			// Only the corners the area makes: those touching one of its tiles. The rest of this outline is the coast's
			List<double[]> found = new ArrayList<>();
			for (double[] ring : smooth(trace(outside), berths)) corners(ring, coast, found);
			for (double[] node : found)
			{
				// Lattice point (i, j) is the shared corner of tiles (i - 1, j - 1) to (i, j)
				int i = (int) Math.round(node[2] + 0.5), j = (int) Math.round(node[3] + 0.5);
				boolean touches = false;
				for (int dx = -1; dx <= 0 && !touches; dx++)
				{
					for (int dy = -1; dy <= 0; dy++) touches |= waters.area(WorldPointUtil.packWorldPoint(i + dx, j + dy, 0)) == area;
				}
				if (touches) nodes.add(node);
			}
		}

		// Each mooring's launch: of the tiles it casts off from, the one with the most room round it, so the most of the sea
		// in sight, out of the restricted areas if it can be; the nearer its berth of those as roomy
		int[] clearance = clearance(water);
		int[] launch = fill(m, -1);
		for (int k = 0; k < m; k++)
		{
			SailingMooring mooring = moorings.get(k);
			if (!mooring.fits(vessel)) continue;
			int[] castOff = waters.castOff(mooring, vessel);
			if (castOff.length == 0) continue;
			int best = -1;
			double bestScore = Double.MAX_VALUE;
			for (int tile : castOff)
			{
				int x = WorldPointUtil.unpackWorldX(tile) - water.minX, y = WorldPointUtil.unpackWorldY(tile) - water.minY;
				double room = x >= 0 && y >= 0 && x < water.width && y < water.height ? clearance[y * water.width + x] : 0;
				double score = -room + Tiles.distance(tile, mooring.berthTile()) / 100 + (waters.area(tile) != null ? 1e6 : 0);
				if (score < bestScore)
				{
					bestScore = score;
					best = tile;
				}
			}
			launch[k] = nodes.size();
			nodes.add(launchNode(best));
		}
		int[] launched = fill(nodes.size(), -1);
		for (int k = 0; k < m; k++) if (launch[k] >= 0) launched[launch[k]] = k;
		SeaGraph shape = assemble(kind, nodes, rings, new int[0][], new byte[0][], new int[0][], launch, new float[0]);

		// Runs between nodes that see each other, tangent at each corner, each sailed on the tiles but those so far from
		// the coast that the moves can't but sail them straight
		int n = nodes.size();
		int[][] upper = sight(shape);
		byte[][] upperAreas = new byte[n][];
		int[][] upperExtra = new int[n][];
		SailingMoves moves = vessel.moves().withSlow();
		double clear = (kind.reach() + vessel.moves().longestMove() / 2 + 1) * 3;
		// The water by each node, reached through water: where a run to it ends and one from it sets out
		int[][] by = new int[n][];
		IntStream.range(0, n).parallel().forEach(i -> by[i] = by(landing, tile(shape.x[i], shape.y[i]), SeaGraph.near(kind)));
		IntStream.range(0, n).parallel().forEach(i ->
		{
			List<int[]> kept = new ArrayList<>();
			for (int j : upper[i])
			{
				byte areas = (byte) SeaGraph.areasAlong(waters, shape.x[i], shape.y[i], shape.x[j], shape.y[j]);
				if (launched[i] < 0 && launched[j] < 0 && clearBetween(clearance, water, shape, i, j, SeaGraph.near(kind), clear))
				{
					kept.add(new int[] {j, areas, 0});
					continue;
				}
				int[] sailed = sail(map, waters, vessel, moves, landing, shape, i, j, by, launched, moorings);
				if (sailed == null) continue;
				kept.add(new int[] {j, areas | sailed[1], sailed[0]});
			}
			upper[i] = kept.stream().mapToInt(run -> run[0]).toArray();
			upperAreas[i] = new byte[kept.size()];
			upperExtra[i] = new int[kept.size()];
			for (int k = 0; k < kept.size(); k++)
			{
				upperAreas[i][k] = (byte) kept.get(k)[1];
				upperExtra[i][k] = kept.get(k)[2];
			}
		});

		SeaGraph linked = assemble(kind, nodes, rings, upper, upperAreas, upperExtra, launch, new float[0]);
		return assemble(kind, nodes, rings, upper, upperAreas, upperExtra, launch, legTiles(linked));
	}

	/**
	 * The run from node i to node j sailed on the tiles, {extra ticks, areas}: the ticks it takes past its open-water
	 * bound, and the restricted areas the way it was sailed crosses, as a bit set. Sailed with {@code moves}
	 * within {@link SeaGraph#CORRIDOR} tiles of it: from a launch itself, else from near i (within
	 * {@link #near}, as a node by the coast may be closer to it than the hull lets the boat be); to a launch, docking in
	 * range of its mooring, else to near j. Null if it can't be sailed.
	 */
	private static int[] sail(CollisionMap map, SailingWaters waters, SailingVessel vessel, SailingMoves moves, IntPredicate landing,
		SeaGraph shape, int i, int j, int[][] by, int[] launched, List<SailingMooring> moorings)
	{
		int a = tile(shape.x[i], shape.y[i]), b = tile(shape.x[j], shape.y[j]);
		double near = SeaGraph.near(vessel.kind());
		int[] from = launched[i] >= 0 ? new int[] {a} : fitting(map, vessel.hull(), by[i]);
		SailingMooring docking = launched[j] >= 0 ? moorings.get(launched[j]) : null;
		int[] arrive = by[j];
		HeadingSearch.End end = docking != null ? (tile, heading) -> docking.inDockingRange(tile)
			: (tile, heading) -> Arrays.binarySearch(arrive, tile) >= 0;
		// Most runs the boat sails straight along at full speed; the rest are searched
		HeadingSearch.Sailed sailed = straight(map, vessel.moves(), vessel.hull(), landing, nearest(from, a), b, end, near);
		if (sailed == null)
		{
			sailed = HeadingSearch.along(map, moves, vessel.hull(), landing, SeaGate.NONE, from, -1,
				List.of(new int[] {a, b}), end, docking != null ? SailingMooring.DOCKING_REACH : near, CORRIDOR, false, NEVER);
		}
		if (sailed == null) return null;
		int start = sailed.tiles[0], stop = sailed.tiles[sailed.tiles.length - 1];
		double bound = vessel.moves().openWaterTicks(WorldPointUtil.unpackWorldX(stop) - WorldPointUtil.unpackWorldX(start),
			WorldPointUtil.unpackWorldY(stop) - WorldPointUtil.unpackWorldY(start));
		int areas = 0;
		for (int k = 1; k < sailed.tiles.length; k++)
		{
			int p = sailed.tiles[k - 1], q = sailed.tiles[k];
			areas |= SeaGraph.areasAlong(waters, WorldPointUtil.unpackWorldX(p), WorldPointUtil.unpackWorldY(p),
				WorldPointUtil.unpackWorldX(q), WorldPointUtil.unpackWorldY(q));
		}
		return new int[] {Math.max(0, sailed.ticks - (int) Math.round(bound)), areas};
	}

	/**
	 * The boat's tiles sailing straight from {@code start} towards {@code b} at full speed to an {@code end}: each move
	 * the one, of those heading within an eighth of a turn of the way to {@code b} that the turn rate allows, ending
	 * nearest the line, as a helmsman holds a course between two headings; null if a move is blocked or it strays past.
	 */
	static HeadingSearch.Sailed straight(CollisionMap map, SailingMoves moves, BoatHull hull, IntPredicate landing, int start,
		int b, HeadingSearch.End end, double near)
	{
		if (start == -1) return null;
		double ax = WorldPointUtil.unpackWorldX(start), ay = WorldPointUtil.unpackWorldY(start);
		double bx = WorldPointUtil.unpackWorldX(b), by = WorldPointUtil.unpackWorldY(b);
		double length = Math.hypot(bx - ax, by - ay);
		if (length == 0) return null;
		double ux = (bx - ax) / length, uy = (by - ay) / length;
		List<Integer> tiles = new ArrayList<>();
		tiles.add(start);
		int at = start, heading = -1, ticks = 0;
		while (!end.at(at, heading))
		{
			int x = WorldPointUtil.unpackWorldX(at), y = WorldPointUtil.unpackWorldY(at);
			double rx = bx - x, ry = by - y, left = Math.hypot(rx, ry);
			if (left == 0 || tiles.size() > 4 * length + 16) return null;
			int best = -1;
			double bestOff = Double.MAX_VALUE, bestAhead = 0;
			for (int move = 0; move < moves.size(); move++)
			{
				int dx = moves.dx(move), dy = moves.dy(move), h = moves.heading(move);
				if ((dx * rx + dy * ry) < Math.cos(Math.PI / 4) * moves.distance(move) * left) continue;
				if (heading >= 0 && turn(heading, h) > moves.ticks(move)) continue;
				double qx = x + dx - ax, qy = y + dy - ay;
				double ahead = qx * ux + qy * uy, off = Math.abs(qx * uy - qy * ux);
				if (ahead > length + near) continue;
				if (off < bestOff - 1e-9 || (off < bestOff + 1e-9 && ahead > bestAhead))
				{
					best = move;
					bestOff = off;
					bestAhead = ahead;
				}
			}
			if (best < 0) return null;
			int dx = moves.dx(best), dy = moves.dy(best), h = moves.heading(best);
			int to = WorldPointUtil.packWorldPoint(x + dx, y + dy, 0);
			if (!landing.test(to) || !hull.canMove(map, x, y, 0, h, dx, dy)) return null;
			tiles.add(to);
			at = to;
			heading = h;
			ticks += moves.ticks(best);
		}
		return new HeadingSearch.Sailed(tiles.stream().mapToInt(Integer::intValue).toArray(), heading, ticks);
	}

	private static int turn(int from, int to)
	{
		int turn = Math.abs(to - from);
		return Math.min(turn, 16 - turn);
	}

	// The tile of tiles nearest tile, or -1 for none
	private static int nearest(int[] tiles, int tile)
	{
		int best = -1;
		for (int candidate : tiles) if (best == -1 || Tiles.distance(candidate, tile) < Tiles.distance(best, tile)) best = candidate;
		return best;
	}

	/**
	 * The water within {@code near} tiles of {@code tile}, reached through water from it (or from the water next to it,
	 * if it isn't water), so never across a spit of land; sorted.
	 */
	static int[] by(IntPredicate landing, int tile, double near)
	{
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
		for (int seed : landing.test(tile) ? new int[] {tile} : Tiles.within(tile, 1))
		{
			if (landing.test(seed) && seen.add(seed)) queue.add(seed);
		}
		while (!queue.isEmpty())
		{
			int at = queue.poll();
			int x = WorldPointUtil.unpackWorldX(at), y = WorldPointUtil.unpackWorldY(at);
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					int next = WorldPointUtil.packWorldPoint(x + dx, y + dy, 0);
					if (Tiles.distance(next, tile) > near || !landing.test(next) || !seen.add(next)) continue;
					queue.add(next);
				}
			}
		}
		return seen.stream().mapToInt(Integer::intValue).sorted().toArray();
	}

	// Of tiles, those the hull fits on
	private static int[] fitting(CollisionMap map, BoatHull hull, int[] tiles)
	{
		return Arrays.stream(tiles).filter(t -> hull.fits(map, WorldPointUtil.unpackWorldX(t), WorldPointUtil.unpackWorldY(t), 0)).toArray();
	}

	// Whether the run from i to j is clear by clear (as clearance() gives it) all along but within near of either end,
	// where the boat is by a corner whatever
	private static boolean clearBetween(int[] clearance, Grid water, SeaGraph shape, int i, int j, double near, double clear)
	{
		double length = Math.hypot(shape.x[j] - shape.x[i], shape.y[j] - shape.y[i]);
		if (length <= 2 * near) return false;
		double t0 = near / length, t1 = 1 - t0;
		return least(clearance, water, shape.x[i] + t0 * (shape.x[j] - shape.x[i]), shape.y[i] + t0 * (shape.y[j] - shape.y[i]),
			shape.x[i] + t1 * (shape.x[j] - shape.x[i]), shape.y[i] + t1 * (shape.y[j] - shape.y[i])) >= clear;
	}

	// The least clearance (as clearance() gives it) on the tiles along the line between the points
	private static int least(int[] clearance, Grid water, double x0, double y0, double x1, double y1)
	{
		double length = Math.hypot(x1 - x0, y1 - y0);
		int steps = (int) Math.ceil(length * 2), least = Integer.MAX_VALUE;
		for (int s = 0; s <= steps; s++)
		{
			double t = steps == 0 ? 0 : (double) s / steps;
			int x = (int) Math.round(x0 + t * (x1 - x0)) - water.minX, y = (int) Math.round(y0 + t * (y1 - y0)) - water.minY;
			if (x < 0 || y < 0 || x >= water.width || y >= water.height) return 0;
			least = Math.min(least, clearance[y * water.width + x]);
		}
		return least;
	}

	private static int tile(double x, double y)
	{
		return WorldPointUtil.packWorldPoint((int) Math.round(x), (int) Math.round(y), 0);
	}

	// For each node, the nodes after it that it sees, tangent at each corner
	private static int[][] sight(SeaGraph shape)
	{
		int n = shape.size();
		int[][] upper = new int[n][];
		IntStream.range(0, n).parallel().forEach(i ->
		{
			int[] list = new int[16];
			int count = 0;
			for (int j = i + 1; j < n; j++)
			{
				if (!shape.tangent(i, cornerX(shape, j), cornerY(shape, j)) || !shape.tangent(j, cornerX(shape, i), cornerY(shape, i)))
					continue;
				if (!shape.sees(shape.x[i], shape.y[i], shape.x[j], shape.y[j])) continue;
				if (count == list.length) list = Arrays.copyOf(list, count * 2);
				list[count++] = j;
			}
			upper[i] = Arrays.copyOf(list, count);
		});
		return upper;
	}

	// A node at a tile that isn't a corner: a launch
	private static double[] launchNode(int tile)
	{
		return new double[] {WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile), Double.NaN, Double.NaN,
			Double.NaN, Double.NaN, Double.NaN, Double.NaN};
	}

	/**
	 * Each tile's distance to the nearest tile that isn't water, by a chamfer distance (3 per step along, 4 across), so
	 * in thirds of a tile; by y * width + x over the grid.
	 */
	static int[] clearance(Grid water)
	{
		int w = water.width, h = water.height;
		int[] d = new int[w * h];
		for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) d[y * w + x] = water.free(water.minX + x, water.minY + y) ? Integer.MAX_VALUE / 2 : 0;
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
		{
			int i = y * w + x;
			if (x > 0) d[i] = Math.min(d[i], d[i - 1] + 3);
			if (y > 0) d[i] = Math.min(d[i], d[i - w] + 3);
			if (x > 0 && y > 0) d[i] = Math.min(d[i], d[i - w - 1] + 4);
			if (x < w - 1 && y > 0) d[i] = Math.min(d[i], d[i - w + 1] + 4);
		}
		for (int y = h - 1; y >= 0; y--) for (int x = w - 1; x >= 0; x--)
		{
			int i = y * w + x;
			if (x < w - 1) d[i] = Math.min(d[i], d[i + 1] + 3);
			if (y < h - 1) d[i] = Math.min(d[i], d[i + w] + 3);
			if (x < w - 1 && y < h - 1) d[i] = Math.min(d[i], d[i + w + 1] + 4);
			if (x > 0 && y < h - 1) d[i] = Math.min(d[i], d[i + w - 1] + 4);
		}
		return d;
	}

	// Where tangency is asked towards a node: its coast vertex for a corner, so that along a coast edge the line is the
	// edge itself, which nodes offset into the water would clip; its position for a launch
	private static double cornerX(SeaGraph graph, int i)
	{
		return Double.isNaN(graph.vx[i]) ? graph.x[i] : graph.vx[i];
	}

	private static double cornerY(SeaGraph graph, int i)
	{
		return Double.isNaN(graph.vy[i]) ? graph.y[i] : graph.vy[i];
	}

	private static int[] fill(int size, int value)
	{
		int[] array = new int[size];
		Arrays.fill(array, value);
		return array;
	}

	// The convex vertices of ring as nodes {x, y, vx, vy, prevX, prevY, nextX, nextY}: set into the water along the
	// bisector of the edges' left normals, as far as the coast allows
	private static void corners(double[] ring, SeaGraph coast, List<double[]> nodes)
	{
		int m = ring.length / 2;
		for (int k = 0; k < m; k++)
		{
			double ax = ring[2 * ((k + m - 1) % m)], ay = ring[2 * ((k + m - 1) % m) + 1];
			double bx = ring[2 * k], by = ring[2 * k + 1];
			double cx = ring[2 * ((k + 1) % m)], cy = ring[2 * ((k + 1) % m) + 1];
			// Water on the left: a right turn is a corner sticking out into the water
			if (SeaGraph.cross(ax, ay, bx, by, cx, cy) >= 0) continue;
			double l1 = Math.hypot(bx - ax, by - ay), l2 = Math.hypot(cx - bx, cy - by);
			double nx = -(by - ay) / l1 - (cy - by) / l2, ny = (bx - ax) / l1 + (cx - bx) / l2;
			double nl = Math.hypot(nx, ny);
			for (double nudge : NUDGES)
			{
				double x = bx + nudge * nx / nl, y = by + nudge * ny / nl;
				// Set across another stretch of coast, as off a thin spit: try closer in, or drop it
				if (!coast.sees(bx, by, x, y)) continue;
				nodes.add(new double[] {x, y, bx, by, ax, ay, cx, cy});
				break;
			}
		}
	}

	// For each pair of moorings, the straight-line tiles of the shortest way from the first's launch to the second's,
	// every part fitted: a bound on any boat's time, at its top speed
	private static float[] legTiles(SeaGraph graph)
	{
		int moorings = graph.moorings, n = graph.size();
		float[] tiles = new float[moorings * moorings];
		Arrays.fill(tiles, Float.POSITIVE_INFINITY);
		IntStream.range(0, moorings).parallel().forEach(from ->
		{
			int start = graph.launch[from];
			if (start < 0) return;
			double[] distance = new double[n];
			Arrays.fill(distance, Double.MAX_VALUE);
			distance[start] = 0;
			PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
			open.add(new double[] {distance[start], start});
			while (!open.isEmpty())
			{
				double[] top = open.poll();
				int i = (int) top[1];
				if (top[0] > distance[i]) continue;
				for (int k = graph.dockOffsets[i]; k < graph.dockOffsets[i + 1]; k++)
				{
					int to = graph.dockMooring[k];
					double total = distance[i];
					int index = from * moorings + to;
					if (to != from && total < tiles[index]) tiles[index] = (float) total;
				}
				for (int k = graph.runOffsets[i]; k < graph.runOffsets[i + 1]; k++)
				{
					int j = graph.runTargets[k];
					double next = distance[i] + Math.hypot(graph.x[j] - graph.x[i], graph.y[j] - graph.y[i]);
					if (next >= distance[j]) continue;
					distance[j] = next;
					open.add(new double[] {next, j});
				}
			}
		});
		// Stored as floats, rounded down so they stay bounds
		for (int k = 0; k < tiles.length; k++)
		{
			if (Float.isFinite(tiles[k])) tiles[k] = Math.max(0, Math.nextDown(tiles[k]));
		}
		return tiles;
	}

	private static SeaGraph assemble(SailingVessel.Kind kind, List<double[]> nodes, List<double[]> rings, int[][] upper,
		byte[][] upperAreas, int[][] upperExtra, int[] launch, float[] legTiles)
	{
		int n = nodes.size();
		// As the layer stores them, so what sees what is the same read back
		double[][] columns = new double[8][n];
		for (int i = 0; i < n; i++) for (int c = 0; c < 8; c++) columns[c][i] = (float) nodes.get(i)[c];
		int[] degree = new int[n];
		for (int i = 0; i < upper.length; i++)
		{
			degree[i] += upper[i].length;
			for (int j : upper[i]) degree[j]++;
		}
		int[] runOffsets = new int[n + 1];
		for (int i = 0; i < n; i++) runOffsets[i + 1] = runOffsets[i] + degree[i];
		int[] runTargets = new int[runOffsets[n]], runExtra = new int[runOffsets[n]];
		byte[] runAreas = new byte[runOffsets[n]];
		int[] fill = Arrays.copyOf(runOffsets, n);
		for (int i = 0; i < upper.length; i++)
		{
			for (int k = 0; k < upper[i].length; k++)
			{
				int j = upper[i][k];
				runTargets[fill[i]] = j;
				runExtra[fill[i]] = upperExtra[i][k];
				runAreas[fill[i]++] = upperAreas[i][k];
				runTargets[fill[j]] = i;
				runExtra[fill[j]] = upperExtra[i][k];
				runAreas[fill[j]++] = upperAreas[i][k];
			}
		}
		int points = 0;
		for (double[] ring : rings) points += ring.length / 2;
		double[] flat = new double[2 * points];
		int[] ringStarts = new int[rings.size() + 1];
		int at = 0;
		for (int r = 0; r < rings.size(); r++)
		{
			ringStarts[r] = at;
			double[] ring = rings.get(r);
			for (int k = 0; k < ring.length; k++) flat[2 * at + k] = (float) ring[k];
			at += rings.get(r).length / 2;
		}
		ringStarts[rings.size()] = at;
		return new SeaGraph(kind, columns[0], columns[1], columns[2], columns[3], columns[4], columns[5], columns[6],
			columns[7], runOffsets, runTargets, runAreas, runExtra, launch,
			legTiles.length == 0 ? new float[launch.length * launch.length] : legTiles, flat, ringStarts);
	}

	/** The water's boundary loops, each a ring of lattice points (x, y) with water on the left, collinear points merged. */
	static List<int[]> trace(Grid grid)
	{
		// Directed unit edges from each lattice point; up to two leave a point where water meets diagonally
		Map<Long, int[]> out = new HashMap<>();
		for (int x = grid.minX; x < grid.minX + grid.width; x++)
		{
			for (int y = grid.minY; y < grid.minY + grid.height; y++)
			{
				if (!grid.free(x, y)) continue;
				if (!grid.free(x, y - 1)) addEdge(out, x, y, x + 1, y);
				if (!grid.free(x + 1, y)) addEdge(out, x + 1, y, x + 1, y + 1);
				if (!grid.free(x, y + 1)) addEdge(out, x + 1, y + 1, x, y + 1);
				if (!grid.free(x - 1, y)) addEdge(out, x, y + 1, x, y);
			}
		}
		List<int[]> loops = new ArrayList<>();
		while (!out.isEmpty())
		{
			long start = out.keySet().iterator().next();
			int sx = (int) (start >> 32), sy = (int) start;
			List<Integer> ring = new ArrayList<>();
			int x = sx, y = sy, px = Integer.MIN_VALUE, py = 0;
			while (true)
			{
				long at = (long) x << 32 | (y & 0xFFFFFFFFL);
				int[] choices = out.get(at);
				if (choices == null) break;
				int pick = 0;
				if (choices.length == 4 && px != Integer.MIN_VALUE)
				{
					// A pinch where water meets diagonally: turn left, keeping each body of water to itself
					int inX = x - px, inY = y - py;
					int left0 = inX * (choices[1] - y) - inY * (choices[0] - x);
					pick = left0 > 0 ? 0 : 2;
				}
				int nx = choices[pick], ny = choices[pick + 1];
				if (choices.length == 4)
				{
					out.put(at, pick == 0 ? new int[] {choices[2], choices[3]} : new int[] {choices[0], choices[1]});
				}
				else out.remove(at);
				ring.add(x);
				ring.add(y);
				px = x;
				py = y;
				x = nx;
				y = ny;
				if (x == sx && y == sy) break;
			}
			int n = ring.size() / 2;
			List<Integer> merged = new ArrayList<>();
			for (int i = 0; i < n; i++)
			{
				int ax = ring.get(2 * ((i + n - 1) % n)), ay = ring.get(2 * ((i + n - 1) % n) + 1);
				int bx = ring.get(2 * i), by = ring.get(2 * i + 1);
				int cx = ring.get(2 * ((i + 1) % n)), cy = ring.get(2 * ((i + 1) % n) + 1);
				if ((long) (bx - ax) * (cy - by) - (long) (by - ay) * (cx - bx) != 0)
				{
					merged.add(bx);
					merged.add(by);
				}
			}
			if (merged.size() >= 6) loops.add(merged.stream().mapToInt(Integer::intValue).toArray());
		}
		return loops;
	}

	private static void addEdge(Map<Long, int[]> out, int x0, int y0, int x1, int y1)
	{
		long key = (long) x0 << 32 | (y0 & 0xFFFFFFFFL);
		int[] old = out.get(key);
		out.put(key, old == null ? new int[] {x1, y1} : new int[] {old[0], old[1], x1, y1});
	}

	private static double px(int[] loop, int i)
	{
		return loop[2 * i] - 0.5;
	}

	private static double py(int[] loop, int i)
	{
		return loop[2 * i + 1] - 0.5;
	}

	/**
	 * The loops smoothed (Douglas-Peucker), as rings of points: by up to {@link SeaGraph#TOLERANCE} tiles, none within
	 * {@link SeaGraph#NEAR} of a berth; only ever cutting into land; never crossing another stretch of coast; rocks within the
	 * tolerance dropped.
	 */
	static List<double[]> smooth(List<int[]> loops, double[][] berths)
	{
		Map<Long, List<double[]>> berthCells = new HashMap<>();
		int cell = (int) Math.ceil(NEAR);
		for (double[] b : berths) berthCells.computeIfAbsent(key((int) Math.floor(b[0] / cell), (int) Math.floor(b[1] / cell)), k -> new ArrayList<>()).add(b);
		List<double[]> tolerance = new ArrayList<>();
		for (int[] loop : loops)
		{
			int n = loop.length / 2;
			double[] tol = new double[n];
			for (int i = 0; i < n; i++)
			{
				double x = px(loop, i), y = py(loop, i);
				tol[i] = TOLERANCE;
				int cx = (int) Math.floor(x / cell), cy = (int) Math.floor(y / cell);
				for (int ix = cx - 1; ix <= cx + 1 && tol[i] > 0; ix++)
				{
					for (int iy = cy - 1; iy <= cy + 1; iy++)
					{
						for (double[] b : berthCells.getOrDefault(key(ix, iy), List.of()))
						{
							if (Math.hypot(b[0] - x, b[1] - y) <= NEAR) tol[i] = 0;
						}
					}
				}
			}
			tolerance.add(tol);
		}
		List<boolean[]> keeps = new ArrayList<>();
		for (int l = 0; l < loops.size(); l++)
		{
			int[] loop = loops.get(l);
			int n = loop.length / 2;
			boolean[] keep = new boolean[n];
			if (isRock(loop, tolerance.get(l)))
			{
				// Left empty: the rock is dropped
				keeps.add(keep);
				continue;
			}
			int far = 0;
			for (int i = 1; i < n; i++)
			{
				if (Math.hypot(px(loop, i) - px(loop, 0), py(loop, i) - py(loop, 0)) > Math.hypot(px(loop, far) - px(loop, 0), py(loop, far) - py(loop, 0))) far = i;
			}
			keep[0] = keep[far] = true;
			split(loop, tolerance.get(l), keep, 0, far);
			split(loop, tolerance.get(l), keep, far, n);
			int count = 0;
			for (boolean k : keep) if (k) count++;
			if (count < 3)
			{
				int best = -1;
				double bestD = -1;
				for (int i = 0; i < n; i++)
				{
					if (keep[i]) continue;
					double d = distance(loop, i, 0, far);
					if (d > bestD)
					{
						bestD = d;
						best = i;
					}
				}
				if (best >= 0) keep[best] = true;
			}
			keeps.add(keep);
		}
		restoreCrossings(loops, keeps);
		List<double[]> rings = new ArrayList<>();
		for (int l = 0; l < loops.size(); l++)
		{
			int[] loop = loops.get(l);
			boolean[] keep = keeps.get(l);
			int[] kept = IntStream.range(0, keep.length).filter(i -> keep[i]).toArray();
			if (kept.length == 0) continue;
			double[] ring = new double[2 * kept.length];
			for (int k = 0; k < kept.length; k++)
			{
				ring[2 * k] = px(loop, kept[k]);
				ring[2 * k + 1] = py(loop, kept[k]);
			}
			rings.add(ring);
		}
		return rings;
	}

	private static long key(int cx, int cy)
	{
		return (long) cx << 32 | (cy & 0xFFFFFFFFL);
	}

	/**
	 * Whether a loop is a rock to drop: land inside the water (the ring runs clockwise), small enough to fit within
	 * twice the tolerance of every point on it, so sailing through it is out by no more than smoothing allows.
	 */
	private static boolean isRock(int[] loop, double[] tol)
	{
		int n = loop.length / 2;
		long area = 0;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
		double least = Double.MAX_VALUE;
		for (int i = 0; i < n; i++)
		{
			int j = (i + 1) % n;
			area += (long) loop[2 * i] * loop[2 * j + 1] - (long) loop[2 * j] * loop[2 * i + 1];
			minX = Math.min(minX, loop[2 * i]);
			maxX = Math.max(maxX, loop[2 * i]);
			minY = Math.min(minY, loop[2 * i + 1]);
			maxY = Math.max(maxY, loop[2 * i + 1]);
			least = Math.min(least, tol[i]);
		}
		return area < 0 && Math.hypot(maxX - minX, maxY - minY) <= 2 * least;
	}

	private static double distance(int[] loop, int i, int a, int b)
	{
		double ax = px(loop, a), ay = py(loop, a), bx = px(loop, b), by = py(loop, b), x = px(loop, i), y = py(loop, i);
		double length = Math.hypot(bx - ax, by - ay);
		if (length == 0) return Math.hypot(x - ax, y - ay);
		double t = Math.max(0, Math.min(1, ((x - ax) * (bx - ax) + (y - ay) * (by - ay)) / (length * length)));
		return Math.hypot(x - ax - t * (bx - ax), y - ay - t * (by - ay));
	}

	// Keeps the points between a and b (exclusive; b may be n for point 0) that a straight edge can't stand in for
	private static void split(int[] loop, double[] tol, boolean[] keep, int a, int b)
	{
		int n = loop.length / 2;
		if (b - a < 2) return;
		int worst = -1;
		double worstExcess = 0;
		for (int k = a + 1; k < b; k++)
		{
			// Only ever shave land: a point on the land side (right) of the straight edge must stay, so the water
			// only grows and no passage narrows
			double side = SeaGraph.cross(px(loop, a), py(loop, a), px(loop, b % n), py(loop, b % n), px(loop, k), py(loop, k));
			double excess = side < 0 ? 1 + distance(loop, k, a, b % n) : distance(loop, k, a, b % n) - tol[k];
			if (excess > 1e-9 && (worst < 0 || excess > worstExcess))
			{
				worst = k;
				worstExcess = excess;
			}
		}
		if (worst < 0) return;
		keep[worst] = true;
		split(loop, tol, keep, a, worst);
		split(loop, tol, keep, worst, b);
	}

	// Puts points back wherever a simplified edge meets another boundary, simplified or not, until none does
	private static void restoreCrossings(List<int[]> loops, List<boolean[]> keeps)
	{
		for (int round = 0; round < 100; round++)
		{
			List<double[]> segments = new ArrayList<>();
			List<int[]> spans = new ArrayList<>();
			for (int l = 0; l < loops.size(); l++)
			{
				int[] loop = loops.get(l);
				boolean[] keep = keeps.get(l);
				int n = loop.length / 2;
				int first = -1, prev = -1;
				for (int i = 0; i < n; i++)
				{
					if (!keep[i]) continue;
					if (first < 0) first = i;
					else
					{
						segments.add(new double[] {px(loop, prev), py(loop, prev), px(loop, i), py(loop, i)});
						spans.add(new int[] {l, prev, i});
					}
					prev = i;
				}
				if (first < 0) continue;
				segments.add(new double[] {px(loop, prev), py(loop, prev), px(loop, first), py(loop, first)});
				spans.add(new int[] {l, prev, first + n});
			}
			List<double[]> original = new ArrayList<>();
			List<int[]> originalAt = new ArrayList<>();
			for (int l = 0; l < loops.size(); l++)
			{
				int[] loop = loops.get(l);
				int n = loop.length / 2;
				boolean dropped = true;
				for (boolean k : keeps.get(l)) dropped &= !k;
				if (dropped) continue;
				for (int i = 0; i < n; i++)
				{
					original.add(new double[] {px(loop, i), py(loop, i), px(loop, (i + 1) % n), py(loop, (i + 1) % n)});
					originalAt.add(new int[] {l, i});
				}
			}
			Buckets simplified = new Buckets(segments), coast = new Buckets(original);
			boolean[] bad = new boolean[segments.size()];
			IntStream.range(0, segments.size()).parallel().forEach(s ->
			{
				int[] span = spans.get(s);
				if (span[2] - span[1] == 1) return;
				double[] seg = segments.get(s);
				int n = loops.get(span[0]).length / 2;
				for (long cellKey : Buckets.cells(seg))
				{
					for (int o : simplified.at(cellKey))
					{
						double[] other = segments.get(o);
						if (o != s && SeaGraph.meets(seg[0], seg[1], seg[2], seg[3], other[0], other[1], other[2], other[3]))
						{
							bad[s] = true;
							return;
						}
					}
					for (int o : coast.at(cellKey))
					{
						int[] at = originalAt.get(o);
						if (at[0] == span[0])
						{
							// Inside the stretch this edge replaces
							int i = at[1];
							if ((i >= span[1] && i < span[2]) || (i + n >= span[1] && i + n < span[2])) continue;
						}
						double[] other = original.get(o);
						if (SeaGraph.meets(seg[0], seg[1], seg[2], seg[3], other[0], other[1], other[2], other[3]))
						{
							bad[s] = true;
							return;
						}
					}
				}
			});
			int fixed = 0;
			for (int s = 0; s < segments.size(); s++)
			{
				if (!bad[s]) continue;
				int[] span = spans.get(s);
				int[] loop = loops.get(span[0]);
				int n = loop.length / 2;
				int best = -1;
				double bestD = -1;
				for (int k = span[1] + 1; k < span[2]; k++)
				{
					double d = distance(loop, k % n, span[1], span[2] % n);
					if (d > bestD)
					{
						bestD = d;
						best = k % n;
					}
				}
				if (best >= 0)
				{
					keeps.get(span[0])[best] = true;
					fixed++;
				}
			}
			if (fixed == 0) return;
		}
	}

	/** Segments bucketed by 16-tile cell, for crossing tests while smoothing. */
	private static final class Buckets
	{
		private static final int CELL = 16;
		private final Map<Long, int[]> cells = new HashMap<>();

		Buckets(List<double[]> segments)
		{
			Map<Long, List<Integer>> lists = new HashMap<>();
			for (int i = 0; i < segments.size(); i++)
			{
				for (long cellKey : cells(segments.get(i))) lists.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(i);
			}
			lists.forEach((cellKey, list) -> cells.put(cellKey, list.stream().mapToInt(Integer::intValue).toArray()));
		}

		int[] at(long cellKey)
		{
			return cells.getOrDefault(cellKey, new int[0]);
		}

		// The cells a segment's bounding box covers
		static long[] cells(double[] s)
		{
			int x0 = (int) Math.floor(Math.min(s[0], s[2]) / CELL), x1 = (int) Math.floor(Math.max(s[0], s[2]) / CELL);
			int y0 = (int) Math.floor(Math.min(s[1], s[3]) / CELL), y1 = (int) Math.floor(Math.max(s[1], s[3]) / CELL);
			long[] keys = new long[(x1 - x0 + 1) * (y1 - y0 + 1)];
			int k = 0;
			for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) keys[k++] = key(x, y);
			return keys;
		}
	}
}
