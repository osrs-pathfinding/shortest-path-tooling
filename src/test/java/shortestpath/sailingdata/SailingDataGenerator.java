package shortestpath.sailingdata;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.pathfinder.sailing.SailingDataFingerprint;
import shortestpath.pathfinder.sailing.SailingMooring;
import shortestpath.pathfinder.sailing.SailingMoorings;
import shortestpath.pathfinder.sailing.SailingVessel;
import shortestpath.pathfinder.sailing.SailingWaters;
import shortestpath.pathfinder.sailing.SeaGraph;
import shortestpath.pathfinder.sailing.SeaGraphBuilder;

/**
 * Writes the sailing data, from the collision map and the moorings: the collision map's sailing layer
 * ({@link SailingLayerBuilder}: the sea, and each boat kind's bodies of water) and each kind's sea graph into that layer
 * ({@link SeaGraphBuilder}). Run it with the {@code sailingData} task whenever the collision map, the moorings or the
 * sailing rules change, before the routing cuts (whose fingerprint covers the whole collision map); the plugin's
 * {@code SailingDataTest} says when the committed data is stale.
 */
public final class SailingDataGenerator
{
	private SailingDataGenerator()
	{
	}

	public static void main(String[] args) throws Exception
	{
		Path resources = Path.of(args.length > 0 ? args[0] : "src/main/resources");
		Path zipPath = resources.resolve("collision-map.zip");
		byte[] zip = Files.readAllBytes(zipPath);
		List<SailingMooring> moorings = SailingMoorings.all();
		long started = System.nanoTime();
		String fingerprint = SailingDataFingerprint.of(zip, moorings);
		int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
		SortedMap<String, byte[]> layer = new TreeMap<>(SailingLayerBuilder.build(
			SplitFlagMap.fromZip(new ByteArrayInputStream(zip)), moorings, fingerprint, threads));
		// The graphs are built on the sea the layer just made
		SailingWaters waters = SailingWaters.of(new CollisionMap(SplitFlagMap.fromZip(
			new ByteArrayInputStream(SailingLayerBuilder.withLayer(zip, layer)))));
		for (SailingVessel.Kind kind : SailingVessel.Kind.values())
		{
			SeaGraph graph = SeaGraphBuilder.build(waters, kind, moorings);
			layer.put(SeaGraph.ENTRY_PREFIX + kind.name(), graph.write());
			System.err.printf("built %s's sea graph, %d nodes, at %.1f s%n", kind, graph.size(), (System.nanoTime() - started) / 1e9);
		}
		byte[] written = SailingLayerBuilder.withLayer(zip, layer);
		Files.write(zipPath, written);
		System.err.printf("wrote %s (%d KB) at %.1f s%n", zipPath, written.length / 1024, (System.nanoTime() - started) / 1e9);
	}

}
