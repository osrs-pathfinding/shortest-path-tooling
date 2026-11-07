package shortestpath.pathfinder.sailing;

import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;

/**
 * When a boat has reached a target at sea. A boat arrives on the move whose hull, as it sails, passes over the target,
 * wherever the move ends: at 2.5 tiles a tick every move sails 5 tiles, and a small inlet can lie between where they
 * end. A boat that hasn't moved yet arrives if its hull covers the target where it sets out.
 */
final class SailingArrival
{
	private final BoatHull hull;
	private final int[] targets;
	private final int initialHeading;
	// Per tile a move can set out from to pass over a target, which moves do, as one bitmask per target
	private final PrimitiveIntHashMap<int[]> moves;

	/** @param initialHeading the heading the boat faces where it sets out; -1 if unknown, which lets it face any */
	SailingArrival(BoatHull hull, SailingMoves moves, int[] targets, int initialHeading)
	{
		this.hull = hull;
		this.targets = targets;
		this.initialHeading = initialHeading;
		this.moves = movesOver(hull, moves, targets);
	}

	/** Each tile a move can set out from with {@code hull} passing over a target: which moves, per target. */
	static PrimitiveIntHashMap<int[]> movesOver(BoatHull hull, SailingMoves moves, int[] targets)
	{
		PrimitiveIntHashMap<int[]> starts = new PrimitiveIntHashMap<>(256);
		for (int t = 0; t < targets.length; t++)
		{
			int tx = WorldPointUtil.unpackWorldX(targets[t]), ty = WorldPointUtil.unpackWorldY(targets[t]);
			int tz = WorldPointUtil.unpackWorldPlane(targets[t]);
			for (int move = 0; move < moves.size(); move++)
			{
				int[] swept = hull.sweptOffsets(moves.heading(move), moves.dx(move), moves.dy(move));
				for (int i = 0; i < swept.length; i += 2)
				{
					int start = WorldPointUtil.packWorldPoint(tx - swept[i], ty - swept[i + 1], tz);
					int[] masks = starts.get(start);
					if (masks == null)
					{
						masks = new int[targets.length];
						starts.put(start, masks);
					}
					masks[t] |= 1 << move;
				}
			}
		}
		return starts;
	}

	/** Whether sailing {@code move} from {@code from} passes over a target. */
	boolean arrivesBy(int from, int move)
	{
		return targetPassed(from, move) != WorldPointUtil.UNDEFINED;
	}

	/** The target passed over sailing {@code move} from {@code from}, or {@link WorldPointUtil#UNDEFINED}. */
	int targetPassed(int from, int move)
	{
		int[] masks = moves.get(from);
		if (masks == null) return WorldPointUtil.UNDEFINED;
		for (int t = 0; t < masks.length; t++)
		{
			if ((masks[t] & (1 << move)) != 0) return targets[t];
		}
		return WorldPointUtil.UNDEFINED;
	}

	/** The target a boat setting out from {@code tile} already has under its hull, or {@link WorldPointUtil#UNDEFINED}. */
	int targetCovered(int tile)
	{
		for (int target : targets)
		{
			if (covers(hull, target, tile, initialHeading)) return target;
		}
		return WorldPointUtil.UNDEFINED;
	}

	/** Whether {@code hull} on {@code tile} facing {@code heading} (-1 any) covers {@code target}. */
	static boolean covers(BoatHull hull, int target, int tile, int heading)
	{
		return hull.coversTile(target, tile, heading);
	}
}
