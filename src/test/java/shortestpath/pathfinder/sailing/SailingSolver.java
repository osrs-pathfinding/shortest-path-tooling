package shortestpath.pathfinder.sailing;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.PrimitiveIntList;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.IntMinHeap;
import shortestpath.pathfinder.NodeGraph;

/**
 * The exact tile model's sailing search, which the tests hold the sea graph and the drawing against: from where a boat
 * sets out, over its moves ({@link SailingMoves}), each costing its ticks, the whole hull clear along the way
 * ({@link BoatHull}). Turning is free, so a state is a boat tile, and a tile is settled at its least cost.
 * <p>
 * It can be advanced a cost band at a time, reporting each end it settles, a target it arrives at
 * ({@link SailingArrival}) or a mooring in docking range, to a {@link Listener}, so a caller can interleave it with
 * another search and stop it whenever it likes. It holds the search's node graph until {@link #release}.
 */
public final class SailingSolver
{
	/** {@link #frontier} once nothing is left to expand. */
	public static final int EXHAUSTED = Integer.MAX_VALUE;
	/** What a query's estimate answers for a boat tile from which nothing it looks for can be reached. */
	public static final int NO_ROUTE = Integer.MAX_VALUE;

	/** What a search reports as it reaches its ends; each method returns whether to keep going. */
	public interface Listener
	{
		/** The boat arrived at {@code target} on {@code node}. */
		default boolean target(int target, int node)
		{
			return true;
		}

		/** The boat first came within docking range of exit {@code exit} (its index in the query's exits) on {@code node}. */
		default boolean exit(int exit, int node)
		{
			return true;
		}
	}

	private final CollisionMap map;
	private final SailingMoves moves;
	private final BoatHull hull;
	private final SeaGate gate;
	private final IntUnaryOperator estimate;
	private final IntPredicate landing;
	// How the boat arrives at the targets, or null when the query has none
	private final SailingArrival arrival;
	private final NodeGraph graph = new NodeGraph(1 << 14);
	private final IntMinHeap pending = new IntMinHeap(graph, 256);
	private final PrimitiveIntList neighbors = new PrimitiveIntList(16);
	// The cheapest cost each tile has been queued at, and been settled at
	private final TileInts queued, settled;
	// Which moves arriving at a target have been queued into each tile at a cost that doesn't beat its cheapest: a tile's
	// cheapest move may not pass over the target, so each that does is still queued, once
	private final TileInts queuedArrivals;
	// Exits by every boat tile within docking range of them
	private final PrimitiveIntHashMap<int[]> exitsByTile;
	private final boolean[] exitReported;
	private boolean released;

	public SailingSolver(CollisionMap map, SailingQuery query)
	{
		this.map = map;
		moves = query.moves();
		hull = query.hull();
		gate = query.gate();
		estimate = query.estimate();
		landing = query.landing();
		arrival = query.targets().length == 0 ? null
			: new SailingArrival(hull, moves, query.targets(), query.initialHeading());
		queued = new TileInts(map.regionExtent(), Integer.MAX_VALUE, 0);
		settled = new TileInts(map.regionExtent(), Integer.MAX_VALUE, 0);
		queuedArrivals = new TileInts(map.regionExtent(), 0, 0);
		List<SailingMooring> exits = query.exits();
		exitReported = new boolean[exits.size()];
		exitsByTile = new PrimitiveIntHashMap<>(Math.max(16, exits.size() * 128));
		for (int i = 0; i < exits.size(); i++)
		{
			for (int tile : exits.get(i).dockingTiles())
			{
				int[] here = exitsByTile.get(tile);
				int[] grown = here == null ? new int[1] : Arrays.copyOf(here, here.length + 1);
				grown[grown.length - 1] = i;
				exitsByTile.put(tile, grown);
			}
		}
	}

	/** Sets out from every tile of {@code tiles}, at cost 0: where a boat boarded at a mooring casts off, say. */
	public void castOff(int[] tiles)
	{
		for (int tile : tiles)
		{
			int estimated = estimate.applyAsInt(tile);
			if (estimated == NO_ROUTE) continue;
			pending.add(graph.createWeightedTile(tile, NodeGraph.NO_NODE, 0, estimated, BankVisitState.CARRIED, -1));
			queued.set(tile, 0);
		}
	}

	/**
	 * The lowest cost plus estimate of any state not yet settled, or {@link #EXHAUSTED}: every end not reported yet
	 * costs at least this.
	 */
	public int frontier()
	{
		if (released) return EXHAUSTED;
		int head = pending.peek();
		return head == NodeGraph.NO_NODE ? EXHAUSTED : graph.compareCost(head);
	}

	/**
	 * Settles states while {@link #frontier} is at most {@code limit}, and at least one if any is left, until the
	 * listener says stop or {@code cancelled}. That is asked at every state, so it must be cheap.
	 */
	public void advance(int limit, BooleanSupplier cancelled, Listener listener)
	{
		boolean first = true;
		while (true)
		{
			int frontier = frontier();
			if (frontier == EXHAUSTED || (!first && frontier > limit) || cancelled.getAsBoolean()) return;
			int node = pending.poll();
			int tile = graph.packedPosition(node), cost = graph.cost(node);
			int target = arrivedTarget(node, tile);
			// A dearer duplicate has nothing to add, unless it is the move that arrives
			if (target == WorldPointUtil.UNDEFINED && (cost > queued.get(tile) || cost >= settled.get(tile))) continue;
			if (cost < settled.get(tile)) settled.set(tile, cost);
			first = false;
			if (target != WorldPointUtil.UNDEFINED && !listener.target(target, node)) return;
			if (!reportExits(node, tile, listener)) return;
			expand(node, tile);
		}
	}

	// The target the boat arrives at on node, or UNDEFINED
	private int arrivedTarget(int node, int tile)
	{
		if (arrival == null) return WorldPointUtil.UNDEFINED;
		int previous = graph.previous(node);
		return previous == NodeGraph.NO_NODE ? arrival.targetCovered(tile)
			: arrival.targetPassed(graph.packedPosition(previous), graph.moveIndex(node));
	}

	private boolean reportExits(int node, int tile, Listener listener)
	{
		int[] exits = exitsByTile.get(tile);
		if (exits == null) return true;
		for (int exit : exits)
		{
			if (exitReported[exit]) continue;
			exitReported[exit] = true;
			if (!listener.exit(exit, node)) return false;
		}
		return true;
	}

	private void expand(int node, int tile)
	{
		int x = WorldPointUtil.unpackWorldX(tile), y = WorldPointUtil.unpackWorldY(tile), z = WorldPointUtil.unpackWorldPlane(tile);
		int costSoFar = graph.cost(node);
		neighbors.clear();
		for (int move = 0; move < moves.size(); move++)
		{
			int dx = moves.dx(move), dy = moves.dy(move);
			int to = WorldPointUtil.packWorldPoint(x + dx, y + dy, z);
			boolean arrives = arrival != null && arrival.arrivesBy(tile, move);
			int cost = costSoFar + moves.ticks(move);
			// The cost check comes first, as it costs less than the hull's
			if (cost >= queued.get(to) && !(arrives && markArrivalQueued(to, move))) continue;
			if (!hull.canMove(map, x, y, z, moves.heading(move), dx, dy)) continue;
			if (!landing.test(to) || !gated(tile, to, x, y, z)) continue;
			// Nothing is left to sail once the boat has arrived, wherever the move ends
			int estimated = arrives ? 0 : estimate.applyAsInt(to);
			if (estimated == NO_ROUTE) continue;
			pending.add(graph.createWeightedTile(to, node, moves.ticks(move), estimated, BankVisitState.CARRIED, move));
			if (cost < queued.get(to)) queued.set(to, cost);
		}
	}

	// Whether the move may be taken. It crosses its whole span at once, so its midpoint is checked too, so a leg can't
	// clip a gated area
	private boolean gated(int from, int to, int x, int y, int z)
	{
		int middle = WorldPointUtil.packWorldPoint((x + WorldPointUtil.unpackWorldX(to)) / 2,
			(y + WorldPointUtil.unpackWorldY(to)) / 2, z);
		return gate.allowed(from, to) && gate.allowed(from, middle) && gate.allowed(middle, to);
	}

	// Notes that move is queued into tile despite its cost; false if it already was
	private boolean markArrivalQueued(int tile, int move)
	{
		int arrivals = queuedArrivals.get(tile);
		if ((arrivals & (1 << move)) != 0) return false;
		queuedArrivals.set(tile, arrivals | (1 << move));
		return true;
	}

	/** The leg from where it set out to {@code node}, ending for {@code reached}. */
	public SailingLeg leg(int node, int reached)
	{
		int count = 0;
		for (int at = node; at != NodeGraph.NO_NODE; at = graph.previous(at)) count++;
		int[] points = new int[count], costs = new int[count];
		int i = count;
		for (int at = node; at != NodeGraph.NO_NODE; at = graph.previous(at))
		{
			i--;
			points[i] = graph.packedPosition(at);
			costs[i] = graph.cost(at);
		}
		return new SailingLeg(points, costs, reached);
	}

	/** Lets go of the node graph; the solver reports nothing after. */
	public void release()
	{
		if (released) return;
		released = true;
		pending.clear();
		graph.release();
		hull.release();
	}
}
