package shortestpath.pathfinder.sailing;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * One sailed stretch of a route: the boat's tile where it sets out and at the end of every move, and the ticks to each
 * from the start of the leg. {@link #reached} is what the leg ended for: the berth of the mooring it docks at, or the
 * target it passed over, which can be a boat length from the boat's own tile.
 */
@Accessors(fluent = true)
public final class SailingLeg
{
	private final int[] points;
	private final int[] costs;
	@Getter private final int reached;

	SailingLeg(int[] points, int[] costs, int reached)
	{
		if (points.length == 0 || costs.length != points.length)
			throw new IllegalArgumentException("a sailing leg needs one cost per point");
		this.points = points;
		this.costs = costs;
		this.reached = reached;
	}

	/** Points on the leg. */
	public int size()
	{
		return points.length;
	}

	/** The boat's tile at point {@code index}. */
	public int point(int index)
	{
		return points[index];
	}

	/** Ticks the leg takes. */
	public int cost()
	{
		return costs[costs.length - 1];
	}
}
