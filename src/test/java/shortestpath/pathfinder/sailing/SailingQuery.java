package shortestpath.pathfinder.sailing;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * One sailing search's inputs: the boat's moves and hull, which way it faces where it sets out, what to look for, and
 * what guides or limits it. Made with {@link #of} and the {@code with} methods, each of which returns a new query. Where
 * the boat sets out from is given to the solver ({@link SailingSolver#castOff}).
 */
@Getter
@Accessors(fluent = true)
public final class SailingQuery
{
	private final SailingMoves moves;
	private final BoatHull hull;
	/** One of the 16 headings (0 south, 8 north) the boat faces where it sets out, or -1 when unknown. */
	private final int initialHeading;
	/** Tiles the boat arrives at once its hull passes over one. */
	private final int[] targets;
	/** Moorings it reaches once in docking range. */
	private final List<SailingMooring> exits;
	private final SeaGate gate;
	/**
	 * A consistent lower bound on the ticks from each boat tile to what the search looks for, or
	 * {@link SailingSolver#NO_ROUTE} for a tile it can't be reached from. By default 0: the search goes by cost alone.
	 */
	private final IntUnaryOperator estimate;
	/** The tiles a move may end on. */
	private final IntPredicate landing;

	private SailingQuery(SailingMoves moves, BoatHull hull, int initialHeading, int[] targets,
		List<SailingMooring> exits, SeaGate gate, IntUnaryOperator estimate, IntPredicate landing)
	{
		this.moves = moves;
		this.hull = hull;
		this.initialHeading = initialHeading;
		this.targets = targets;
		this.exits = exits;
		this.gate = gate;
		this.estimate = estimate;
		this.landing = landing;
	}

	/** A search for a boat with {@code moves} and {@code hull}, heading unknown, that looks for nothing yet. */
	public static SailingQuery of(SailingMoves moves, BoatHull hull)
	{
		return new SailingQuery(moves, hull, -1, new int[0], List.of(), SeaGate.NONE, tile -> 0, tile -> true);
	}

	/** A search for {@code vessel}, with a hull of its own, ending its moves only where {@code waters} let it. */
	public static SailingQuery of(SailingVessel vessel, SailingWaters waters)
	{
		return of(vessel.moves(), vessel.hull()).landingOn(waters.landing(vessel));
	}

	/** This query looking for {@code targets}. */
	public SailingQuery withTargets(int[] targets)
	{
		return new SailingQuery(moves, hull, initialHeading, targets.clone(), exits, gate, estimate, landing);
	}

	/** This query looking for the moorings {@code exits}. */
	public SailingQuery withExits(List<SailingMooring> exits)
	{
		return new SailingQuery(moves, hull, initialHeading, targets, List.copyOf(exits), gate, estimate, landing);
	}

	/** This query heading for its ends by {@code estimate}. */
	public SailingQuery withEstimate(IntUnaryOperator estimate)
	{
		return new SailingQuery(moves, hull, initialHeading, targets, exits, gate, estimate, landing);
	}

	/** This query with every move ending on a tile {@code landing} accepts. */
	public SailingQuery landingOn(IntPredicate landing)
	{
		return new SailingQuery(moves, hull, initialHeading, targets, exits, gate, estimate, landing);
	}
}
