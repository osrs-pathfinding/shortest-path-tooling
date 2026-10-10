package travel.osrs.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import shortestpath.pathfinder.ServicePathfinderConfig;

/**
 * Writes the item name table served by {@link ItemCatalog}: every item the current transport data can
 * require, named from the OSRS Wiki's Chisel item database. The build runs this through the
 * {@code generateRouteItemNames} Gradle task, so each deploy picks up new transport requirements.
 */
public final class RouteItemNames
{
	@JsonIgnoreProperties(ignoreUnknown = true)
	static final class ChiselItem
	{
		public int id;
		public String name;
	}

	private RouteItemNames() { }

	public static void main(String[] args) throws IOException, InterruptedException
	{
		if (args.length != 2) throw new IllegalArgumentException("usage: RouteItemNames <itemsmin.js path or URL> <output.tsv>");
		Map<Integer, String> names = chiselNames(read(args[0]));
		List<Integer> ids = ServicePathfinderConfig.routeItemIds().stream().sorted().collect(Collectors.toList());
		List<Integer> missing = ids.stream().filter(id -> !names.containsKey(id)).collect(Collectors.toList());
		if (!missing.isEmpty()) throw new IllegalStateException("Chisel has no name for route items " + missing);

		Path output = Path.of(args[1]);
		Files.createDirectories(output.getParent());
		Files.write(output, ids.stream().map(id -> id + "\t" + names.get(id)).collect(Collectors.toList()), StandardCharsets.UTF_8);
		System.out.println("Wrote " + ids.size() + " route item names to " + output);
	}

	static Map<Integer, String> chiselNames(String source) throws IOException
	{
		// itemsmin.js assigns a JSON array to a global: items=[{"id":0,"name":"Dwarf remains",...},...]
		int start = source.indexOf('[');
		if (start < 0) throw new IOException("Chisel item data does not contain an item array");
		ChiselItem[] items = new ObjectMapper().readValue(source.substring(start), ChiselItem[].class);
		Map<Integer, String> result = new HashMap<>();
		for (ChiselItem item : items)
			if (item.name != null && !item.name.isBlank()) result.putIfAbsent(item.id, item.name);
		return result;
	}

	private static String read(String source) throws IOException, InterruptedException
	{
		if (!source.startsWith("http://") && !source.startsWith("https://"))
			return Files.readString(Path.of(source), StandardCharsets.UTF_8);
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(30)).build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(source)).timeout(Duration.ofMinutes(2))
			.header("User-Agent", "shortest-path-web route item generator").build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() != 200) throw new IOException("GET " + source + " returned " + response.statusCode());
		return response.body();
	}
}
