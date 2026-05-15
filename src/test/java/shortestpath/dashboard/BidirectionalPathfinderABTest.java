package shortestpath.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BidirectionalPathfinder;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderResult;

/**
 * A/B comparison between production {@link Pathfinder} and experimental
 * {@link BidirectionalPathfinder}. Runs both pathfinders on the same
 * scenario set and prints comparative statistics.
 */
public class BidirectionalPathfinderABTest
{
	private static final String DEFAULT_DATASET = "/dashboard/routes.csv";
	private static final String DATASET_PROPERTY = "dashboard.dataset";
	private static final int MAX_SCENARIOS = Integer.getInteger("bidir.maxScenarios", 50);

	private final DashboardScenarioLoader loader = new DashboardScenarioLoader();
	private Client client;
	private ItemContainer universalBankContainer;
	private Runnable clientBaseline;

	@Before
	public void setUp()
	{
		Item[] bankItems = new Item[25000];
		for (int i = 0; i < 25000; i++)
		{
			bankItems[i] = new Item(i, 1000);
		}

		client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		when(client.getTotalLevel()).thenReturn(2277);
		when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));

		universalBankContainer = mock(ItemContainer.class);
		when(universalBankContainer.getItems()).thenReturn(bankItems);

		clientBaseline = () -> {};
	}

	@Test
	public void runABComparison() throws IOException
	{
		String dataset = System.getProperty(DATASET_PROPERTY, DEFAULT_DATASET);
		List<DashboardScenario> allScenarios = loadScenarios(dataset);
		List<DashboardScenario> scenarios = allScenarios.subList(0, Math.min(MAX_SCENARIOS, allScenarios.size()));

		List<ABResult> results = new ArrayList<>();

		for (DashboardScenario scenario : scenarios)
		{
			DashboardScenarioRunner.ApplyResult applied = DashboardScenarioRunner.apply(
				scenario, client, clientBaseline, universalBankContainer);

			int start = scenario.getStartPoint() != WorldPointUtil.UNDEFINED
				? scenario.getStartPoint()
				: WorldPointUtil.packWorldPoint(3185, 3436, 0);
			int end = scenario.getEndPoint();

			// Warmup: one throwaway run with Pathfinder to JIT the code paths.
			new Pathfinder(applied.pathfinderConfig, start, Set.of(end)).run();

			// Production pathfinder.
			Pathfinder pf = new Pathfinder(applied.pathfinderConfig, start, Set.of(end));
			long pfStart = System.nanoTime();
			pf.run();
			long pfElapsed = System.nanoTime() - pfStart;
			PathfinderResult pfResult = pf.getResult();

			// Bidirectional pathfinder.
			BidirectionalPathfinder bpf = new BidirectionalPathfinder(applied.pathfinderConfig, start, Set.of(end));
			long bpfStart = System.nanoTime();
			bpf.run();
			long bpfElapsed = System.nanoTime() - bpfStart;
			PathfinderResult bpfResult = bpf.getResult();
			if (pfResult == null || bpfResult == null) {
				System.out.printf("[%2d/%-2d] %s %s  NO_RESULT%n", results.size()+1, scenarios.size(), "?", scenario.getName());
				continue;
			}

			boolean bothReached = pfResult.isReached() && bpfResult.isReached();
			boolean neitherReached = !pfResult.isReached() && !bpfResult.isReached();

			results.add(new ABResult(
				scenario.getName(),
				pfResult.isReached(),
				bpfResult.isReached(),
				pfElapsed,
				bpfElapsed,
				pfResult.getNodesChecked() + pfResult.getTransportsChecked(),
				bpfResult.getNodesChecked() + bpfResult.getTransportsChecked(),
				pfResult.getPathSteps().size(),
				bpfResult.getPathSteps().size(),
				bothReached,
				neitherReached));

			char marker = bothReached ? '\u2714' : (neitherReached ? '\u2716' : '!');
			double pfMs = pfElapsed / 1_000_000.0;
			double bpfMs = bpfElapsed / 1_000_000.0;
			double deltaPct = pfMs > 0 ? ((bpfMs - pfMs) / pfMs * 100.0) : 0;
			System.out.printf("[%2d/%-2d] %s %s  pf=%.1fms  bidir=%.1fms  (%+.1f%%)  pfNodes=%d  bidirNodes=%d  pfLen=%d  bidirLen=%d%n",
				results.size(), scenarios.size(), marker, scenario.getName(),
				pfMs, bpfMs, deltaPct,
				pfResult.getNodesChecked() + pfResult.getTransportsChecked(),
				bpfResult.getNodesChecked() + bpfResult.getTransportsChecked(),
				pfResult.getPathSteps().size(),
				bpfResult.getPathSteps().size());
		}

		printSummary(results);
	}

	private List<DashboardScenario> loadScenarios(String dataset) throws IOException
	{
		if (dataset.startsWith("/"))
		{
			return loader.loadFromResource(dataset);
		}
		return loader.loadFromCsv(java.nio.file.Paths.get(dataset));
	}

	private void printSummary(List<ABResult> results)
	{
		if (results.isEmpty()) { return; }

		int bothReached = (int) results.stream().filter(r -> r.bothReached).count();
		int neitherReached = (int) results.stream().filter(r -> r.neitherReached).count();
		int disagree = results.size() - bothReached - neitherReached;

		double totalPfMs = results.stream().mapToLong(r -> r.pfElapsedNanos).sum() / 1_000_000.0;
		double totalBpfMs = results.stream().mapToLong(r -> r.bpfElapsedNanos).sum() / 1_000_000.0;

		List<Double> pctDeltas = new ArrayList<>();
		for (ABResult r : results)
		{
			if (r.pfElapsedNanos > 0)
			{
				pctDeltas.add(((double) r.bpfElapsedNanos - r.pfElapsedNanos) / r.pfElapsedNanos * 100.0);
			}
		}
		double meanPct = pctDeltas.stream().mapToDouble(Double::doubleValue).average().orElse(0);
		double medianPct = pctDeltas.stream().sorted().skip(pctDeltas.size() / 2).findFirst().orElse(0.0);

		long totalPfNodes = results.stream().mapToLong(r -> r.pfNodesChecked).sum();
		long totalBpfNodes = results.stream().mapToLong(r -> r.bpfNodesChecked).sum();

		System.out.println();
		System.out.println("=== A/B Summary ===");
		System.out.printf("Scenarios: %d  (bothReached=%d  neitherReached=%d  disagree=%d)%n",
			results.size(), bothReached, neitherReached, disagree);
		System.out.printf("Total time:  Pathfinder=%.1fms  Bidir=%.1fms  (%.1f%%)%n",
			totalPfMs, totalBpfMs, (totalBpfMs - totalPfMs) / totalPfMs * 100.0);
		System.out.printf("Total nodes: Pathfinder=%d  Bidir=%d  (%.1f%%)%n",
			totalPfNodes, totalBpfNodes, totalPfNodes > 0 ? (totalBpfNodes - totalPfNodes) * 100.0 / totalPfNodes : 0);
		System.out.printf("Per-run time mean delta: %+.1f%%  median: %+.1f%%%n", meanPct, medianPct);
	}

	private static class ABResult
	{
		final String name;
		final boolean pfReached, bpfReached;
		final long pfElapsedNanos, bpfElapsedNanos;
		final int pfNodesChecked, bpfNodesChecked;
		final int pfPathLength, bpfPathLength;
		final boolean bothReached, neitherReached;

		ABResult(String name, boolean pfReached, boolean bpfReached,
			long pfElapsedNanos, long bpfElapsedNanos,
			int pfNodesChecked, int bpfNodesChecked,
			int pfPathLength, int bpfPathLength,
			boolean bothReached, boolean neitherReached)
		{
			this.name = name;
			this.pfReached = pfReached;
			this.bpfReached = bpfReached;
			this.pfElapsedNanos = pfElapsedNanos;
			this.bpfElapsedNanos = bpfElapsedNanos;
			this.pfNodesChecked = pfNodesChecked;
			this.bpfNodesChecked = bpfNodesChecked;
			this.pfPathLength = pfPathLength;
			this.bpfPathLength = bpfPathLength;
			this.bothReached = bothReached;
			this.neitherReached = neitherReached;
		}
	}
}
