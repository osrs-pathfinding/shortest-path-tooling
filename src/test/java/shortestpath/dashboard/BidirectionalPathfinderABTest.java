package shortestpath.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * scenario set, prints summary, and emits per-scenario JSONL to
 * {@code build/reports/bidir-ab/&lt;dataset&gt;.jsonl} for downstream analysis.
 */
public class BidirectionalPathfinderABTest
{
	private static final String DEFAULT_DATASET = "/dashboard/routes.csv";
	private static final String DATASET_PROPERTY = "dashboard.dataset";
	private static final String OUTPUT_DIR_PROPERTY = "bidir.outputDir";
	private static final int MAX_SCENARIOS = Integer.getInteger("bidir.maxScenarios", Integer.MAX_VALUE);

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
		Path outputPath = resolveOutputPath(dataset);
		Files.createDirectories(outputPath.getParent());

		try (BufferedWriter out = Files.newBufferedWriter(outputPath))
		{
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

				if (pfResult == null || bpfResult == null)
				{
					System.out.printf("[%2d/%-2d] %s NO_RESULT%n", results.size() + 1, scenarios.size(), scenario.getName());
					continue;
				}

				boolean bothReached = pfResult.isReached() && bpfResult.isReached();
				boolean neitherReached = !pfResult.isReached() && !bpfResult.isReached();

				double pfMs = pfElapsed / 1_000_000.0;
				double bpfMs = bpfElapsed / 1_000_000.0;
				double deltaPct = pfElapsed > 0 ? (bpfElapsed - pfElapsed) * 100.0 / pfElapsed : 0.0;

				String tag;
				if (pfResult.isReached() && bpfResult.isReached()) tag = "OK";
				else if (!pfResult.isReached() && !bpfResult.isReached()) tag = "UR";
				else tag = "DG";

				System.out.printf("[%3d/%-3d] %s %-60s  pf=%7.2fms  bidir=%7.2fms  %+6.1f%%  pfNodes=%d  bpfNodes=%d%n",
					results.size() + 1, scenarios.size(), tag,
					truncate(scenario.getName(), 60),
					pfMs, bpfMs, deltaPct,
					pfResult.getNodesChecked() + pfResult.getTransportsChecked(),
					bpfResult.getNodesChecked() + bpfResult.getTransportsChecked());

				ABResult r = new ABResult(
					scenario.getName(),
					pfResult.isReached(), bpfResult.isReached(),
					pfElapsed, bpfElapsed,
					pfResult.getNodesChecked() + pfResult.getTransportsChecked(),
					bpfResult.getNodesChecked() + bpfResult.getTransportsChecked(),
					pfResult.getPathSteps().size(),
					bpfResult.getPathSteps().size(),
					bothReached, neitherReached);
				results.add(r);
				out.write(r.toJson());
				out.newLine();
			}
		}

		System.out.printf("%nWrote per-scenario JSONL to %s%n", outputPath.toAbsolutePath());
		printSummary(results);
	}

	private static Path resolveOutputPath(String dataset)
	{
		String outDirProp = System.getProperty(OUTPUT_DIR_PROPERTY, "build/reports/bidir-ab");
		String basename = dataset;
		int slash = basename.lastIndexOf('/');
		if (slash >= 0)
		{
			basename = basename.substring(slash + 1);
		}
		if (basename.endsWith(".csv"))
		{
			basename = basename.substring(0, basename.length() - 4);
		}
		if (basename.isEmpty())
		{
			basename = "dataset";
		}
		return Paths.get(outDirProp, basename + ".jsonl");
	}

	private static String truncate(String s, int n)
	{
		return s.length() <= n ? s : s.substring(0, n - 1) + "\u2026";
	}

	private List<DashboardScenario> loadScenarios(String dataset) throws IOException
	{
		if (dataset.startsWith("/"))
		{
			return loader.loadFromResource(dataset);
		}
		return loader.loadFromCsv(Paths.get(dataset));
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

		// Split reachable / unreachable for per-route UX framing.
		List<Long> reachablePf = new ArrayList<>();
		List<Long> reachableBpf = new ArrayList<>();
		List<Long> unreachablePf = new ArrayList<>();
		List<Long> unreachableBpf = new ArrayList<>();
		for (ABResult r : results)
		{
			if (r.bothReached)
			{
				reachablePf.add(r.pfElapsedNanos);
				reachableBpf.add(r.bpfElapsedNanos);
			}
			else if (r.neitherReached)
			{
				unreachablePf.add(r.pfElapsedNanos);
				unreachableBpf.add(r.bpfElapsedNanos);
			}
		}

		System.out.println();
		System.out.println("=== A/B Summary (Pathfinder vs BidirectionalPathfinder) ===");
		System.out.printf("Scenarios: %d  (bothReached=%d  neitherReached=%d  disagree=%d)%n",
			results.size(), bothReached, neitherReached, disagree);
		System.out.printf("Total time:  Pathfinder=%.1fms  Bidir=%.1fms  (%+.1f%%)%n",
			totalPfMs, totalBpfMs, totalPfMs > 0 ? (totalBpfMs - totalPfMs) / totalPfMs * 100.0 : 0);
		System.out.printf("Total nodes: Pathfinder=%d  Bidir=%d  (%+.1f%%)%n",
			totalPfNodes, totalBpfNodes, totalPfNodes > 0 ? (totalBpfNodes - totalPfNodes) * 100.0 / totalPfNodes : 0);
		System.out.printf("Per-run delta:  mean=%+.1f%%  median=%+.1f%%%n", meanPct, medianPct);
		if (!reachablePf.isEmpty())
		{
			System.out.printf("Reachable   (%d): pf median=%.2fms max=%.2fms  | bidir median=%.2fms max=%.2fms%n",
				reachablePf.size(),
				median(reachablePf) / 1e6, max(reachablePf) / 1e6,
				median(reachableBpf) / 1e6, max(reachableBpf) / 1e6);
		}
		if (!unreachablePf.isEmpty())
		{
			System.out.printf("Unreachable (%d): pf median=%.2fms max=%.2fms  | bidir median=%.2fms max=%.2fms%n",
				unreachablePf.size(),
				median(unreachablePf) / 1e6, max(unreachablePf) / 1e6,
				median(unreachableBpf) / 1e6, max(unreachableBpf) / 1e6);
		}
	}

	private static double median(List<Long> values)
	{
		List<Long> sorted = new ArrayList<>(values);
		sorted.sort(Long::compare);
		int n = sorted.size();
		if (n == 0) return 0;
		if ((n & 1) == 1) return sorted.get(n / 2);
		return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
	}

	private static double max(List<Long> values)
	{
		long m = 0;
		for (long v : values) { if (v > m) m = v; }
		return m;
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

		String toJson()
		{
			StringBuilder sb = new StringBuilder(256);
			sb.append('{');
			sb.append("\"name\":").append(jsonString(name)).append(',');
			sb.append("\"pfReached\":").append(pfReached).append(',');
			sb.append("\"bpfReached\":").append(bpfReached).append(',');
			sb.append("\"pfElapsedNanos\":").append(pfElapsedNanos).append(',');
			sb.append("\"bpfElapsedNanos\":").append(bpfElapsedNanos).append(',');
			sb.append("\"pfNodesChecked\":").append(pfNodesChecked).append(',');
			sb.append("\"bpfNodesChecked\":").append(bpfNodesChecked).append(',');
			sb.append("\"pfPathLength\":").append(pfPathLength).append(',');
			sb.append("\"bpfPathLength\":").append(bpfPathLength).append(',');
			sb.append("\"bothReached\":").append(bothReached).append(',');
			sb.append("\"neitherReached\":").append(neitherReached);
			sb.append('}');
			return sb.toString();
		}

		private static String jsonString(String s)
		{
			StringBuilder sb = new StringBuilder(s.length() + 2);
			sb.append('"');
			for (int i = 0; i < s.length(); i++)
			{
				char c = s.charAt(i);
				switch (c)
				{
					case '"': sb.append("\\\""); break;
					case '\\': sb.append("\\\\"); break;
					case '\n': sb.append("\\n"); break;
					case '\r': sb.append("\\r"); break;
					case '\t': sb.append("\\t"); break;
					default:
						if (c < 0x20) { sb.append(String.format("\\u%04x", (int) c)); }
						else { sb.append(c); }
				}
			}
			sb.append('"');
			return sb.toString();
		}
	}
}
