package shortestpath.pathfinder.sailing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.sailing.SeaArea;

/**
 * The plugin's sailing data held against the exact tile model ({@link SailingSolver}): the sea graph's legs take about
 * what the boat takes on the tiles, boats leave their berths, and a move arrives where its hull passes over a target.
 * The plugin sails on the graph, which this generator builds; the tile search lives here to check it.
 */
public class SailingTileModelTest
{
	private static final CollisionMap MAP = new CollisionMap(SplitFlagMap.fromResources());

	@Test
	public void legsOnTheGraphTakeAboutWhatTheBoatTakesOnTheTiles()
	{
		// The graph is a model of the sea, not the sea: a leg's time on it is near the boat's on the tiles, and its bound
		// at the boat's top speed is never more than the leg on the graph
		List<SailingMooring> moorings = SailingMoorings.all();
		int compared = 0;
		for (SailingVessel.Kind kind : SailingVessel.Kind.values())
		{
			SeaGraph graph = SeaGraph.of(MAP, kind);
			SailingVessel vessel = SailingVessel.of(kind, 256).fullyFitted();
			for (int a = 0; a < moorings.size(); a += 9)
			{
				for (int b = 4; b < moorings.size(); b += 13)
				{
					SailingMooring from = moorings.get(a), to = moorings.get(b);
					if (from == to || !graph.joins(from, to)) continue;
					SailingLeg exact = sail(MAP, vessel, from, to);
					if (exact == null) continue;
					double onGraph = ticks(graph, vessel.moves(), from, to);
					String leg = kind + " " + from + " -> " + to + ": graph " + onGraph + ", tiles " + exact.cost();
					assertTrue(leg, Math.abs(onGraph - exact.cost()) <= Math.max(12, 0.1 * exact.cost()));
					assertTrue(leg, graph.legTiles(from, to) / vessel.moves().topSpeed() <= onGraph + 1e-6);
					compared++;
				}
			}
		}
		assertTrue(compared > 20);
	}

	@Test
	public void aSlowBoatLeavesItsBerth()
	{
		// At 1.5 every move is short: a skiff moored at the Isle of Bones used to make one move and still have its
		// stern over the dock. Cast off clear of it, it sails on.
		SailingMooring from = mooring("Isle of Bones");
		for (Object[] leg : new Object[][] {{SailingVessel.Kind.SKIFF, "Charred Island"}, {SailingVessel.Kind.SLOOP, "Port Roberts"}})
		{
			SailingVessel vessel = SailingVessel.of((SailingVessel.Kind) leg[0], 192).withAreas(SeaArea.ALL);
			assertNotNull(leg[0].toString(), sail(MAP, vessel, from, mooring((String) leg[1])));
		}
	}

	@Test
	public void testOnlyAMovePassingOverTheTargetArrives()
	{
		// With the target a tile east of where the moves end, a raft only passes over it sailing east
		SailingMoves moves = SailingMoves.forSpeed(1.5);
		int target = WorldPointUtil.packWorldPoint(2706, 3049, 0);
		SailingArrival arrival = new SailingArrival(raftHull(), moves, new int[] {target}, -1);

		assertEquals(target, arrival.targetPassed(WorldPointUtil.packWorldPoint(2702, 3049, 0), moves.indexOf(3, 0)));
		assertEquals("Sailing north passes beside the target", WorldPointUtil.UNDEFINED,
			arrival.targetPassed(WorldPointUtil.packWorldPoint(2705, 3046, 0), moves.indexOf(0, 3)));
	}

	@Test
	public void testAMoveArrivesWhenItsHullPassesOverTheTarget()
	{
		// At 2.5 tiles a tick a raft sails 5 tiles east in a move, longer than its hull: the tiles 2 and 3 east of
		// where it sets out are under it neither there nor where it ends, only as it passes
		SailingMoves moves = SailingMoves.forSpeed(2.5);
		int eastMove = moves.indexOf(5, 0);
		BoatHull raft = raftHull();
		int heading = moves.heading(eastMove);
		assertFalse(raft.covers(heading, 2, 0) || raft.covers(heading, -3, 0));
		int target = WorldPointUtil.packWorldPoint(2702, 3049, 0);
		SailingArrival arrival = new SailingArrival(raft, moves, new int[] {target}, -1);
		assertEquals(target, arrival.targetPassed(WorldPointUtil.packWorldPoint(2700, 3049, 0), eastMove));
		assertEquals("A tile off to the side is never under it", WorldPointUtil.UNDEFINED,
			arrival.targetPassed(WorldPointUtil.packWorldPoint(2700, 3052, 0), eastMove));
	}

	/** The boat's leg on the tiles from casting off at {@code from} to docking at {@code to}, or null. */
	static SailingLeg sail(CollisionMap map, SailingVessel vessel, SailingMooring from, SailingMooring to)
	{
		SailingWaters waters = SailingWaters.of(map);
		SailingSolver solver = new SailingSolver(map, SailingQuery.of(vessel, waters).withExits(List.of(to))
			.withEstimate(tile -> vessel.ticksToSail(Tiles.distance(tile, to.berthTile()) - SailingMooring.DOCKING_REACH)));
		solver.castOff(waters.castOff(from, vessel));
		SailingLeg[] leg = new SailingLeg[1];
		solver.advance(Integer.MAX_VALUE, () -> false, new SailingSolver.Listener()
		{
			@Override
			public boolean exit(int exit, int node)
			{
				leg[0] = solver.leg(node, to.berthTile());
				return false;
			}
		});
		solver.release();
		return leg[0];
	}

	// The ticks of the quickest way from casting off at from to docking at to, on the graph
	static double ticks(SeaGraph graph, SailingMoves moves, SailingMooring from, SailingMooring to)
	{
		double[] cost = new double[graph.size()];
		Arrays.fill(cost, Double.MAX_VALUE);
		int start = graph.launch[from.id()];
		cost[start] = 0;
		PriorityQueue<double[]> open = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
		open.add(new double[] {cost[start], start});
		double best = Double.MAX_VALUE;
		while (!open.isEmpty())
		{
			double[] top = open.poll();
			int i = (int) top[1];
			if (top[0] > cost[i] || top[0] >= best) continue;
			if (i == graph.launch[to.id()]) best = Math.min(best, cost[i]);
			for (int k = graph.runOffsets[i]; k < graph.runOffsets[i + 1]; k++)
			{
				int j = graph.runTargets[k];
				double next = cost[i] + moves.openWaterTicks(graph.x[j] - graph.x[i], graph.y[j] - graph.y[i]) + graph.runExtra[k];
				if (next >= cost[j]) continue;
				cost[j] = next;
				open.add(new double[] {next, j});
			}
		}
		return best;
	}

	private static SailingMooring mooring(String name)
	{
		return SailingMoorings.all().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
	}

	private static BoatHull raftHull()
	{
		return SailingVessel.of(SailingVessel.Kind.RAFT, 192).hull();
	}
}
