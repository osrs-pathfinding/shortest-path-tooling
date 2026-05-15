package shortestpath.pathfinder;

import java.util.List;
import java.util.Set;

/**
 * Test-only profiling wrapper for {@link BidirectionalPathfinder}.
 * Runs the bidirectional algorithm with instrumentation for A/B comparison.
 */
public class ProfilingBidirectionalPathfinder implements Runnable
{
	private final BidirectionalPathfinder inner;
	private PathfinderResult result;
	private long elapsedNanos;

	public ProfilingBidirectionalPathfinder(PathfinderConfig config, int start, Set<Integer> targets)
	{
		this.inner = new BidirectionalPathfinder(config, start, targets);
	}

	@Override
	public void run()
	{
		long startNanos = System.nanoTime();
		inner.run();
		elapsedNanos = System.nanoTime() - startNanos;
		result = inner.getResult();
	}

	public PathfinderResult getResult()
	{
		return result;
	}

	public long getElapsedNanos()
	{
		return elapsedNanos;
	}

	public boolean isDone()
	{
		return inner.isDone();
	}

	public void cancel()
	{
		inner.cancel();
	}

	public int getNodesChecked()
	{
		return result != null ? result.getNodesChecked() : 0;
	}

	public int getTransportsChecked()
	{
		return result != null ? result.getTransportsChecked() : 0;
	}

	public int getStart()
	{
		return inner.getStart();
	}

	public Set<Integer> getTargets()
	{
		return inner.getTargets();
	}

	public List<PathStep> getPathSteps()
	{
		return result != null ? result.getPathSteps() : List.of();
	}
}
