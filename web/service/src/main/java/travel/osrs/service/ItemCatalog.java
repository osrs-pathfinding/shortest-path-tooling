package travel.osrs.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import shortestpath.pathfinder.ServicePathfinderConfig;

final class ItemCatalog
{
	private static final Map<Integer, ApiModels.ItemOption> ITEMS = load();

	private ItemCatalog() { }

	static String name(int id)
	{
		ApiModels.ItemOption item = ITEMS.get(id);
		return item == null ? "Item " + id : item.name;
	}

	static List<ApiModels.ItemOption> find(String query, String ids)
	{
		if (ids != null && !ids.isBlank()) return Arrays.stream(ids.split(","))
			.map(String::trim).filter(value -> value.matches("\\d+"))
			.map(Integer::valueOf).distinct().map(ITEMS::get).filter(java.util.Objects::nonNull)
			.collect(Collectors.toList());
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (needle.length() < 2) return List.of();
		return ITEMS.values().stream().filter(item -> item.name.toLowerCase(Locale.ROOT).contains(needle))
			.limit(30).collect(Collectors.toList());
	}

	private static Map<Integer, ApiModels.ItemOption> load()
	{
		Map<Integer, String> names = loadNames();
		List<ApiModels.ItemOption> items = ServicePathfinderConfig.routeItemIds().stream().map(id -> {
			String name = names.get(id);
			if (name == null) throw new IllegalStateException("missing MOID name for route item " + id);
			return new ApiModels.ItemOption(Integer.toString(id), name);
		}).sorted(Comparator.comparing((ApiModels.ItemOption item) -> item.name).thenComparing(item -> item.key))
			.collect(Collectors.toList());
		Map<Integer, ApiModels.ItemOption> result = new LinkedHashMap<>();
		items.forEach(item -> result.put(Integer.valueOf(item.key), item));
		return result;
	}

	private static Map<Integer, String> loadNames()
	{
		Map<Integer, String> result = new LinkedHashMap<>();
		InputStream input = Objects.requireNonNull(ItemCatalog.class.getResourceAsStream("/route-item-names.tsv"));
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)))
		{
			reader.lines().filter(line -> !line.isBlank()).forEach(line -> {
				String[] fields = line.split("\\t", 2);
				result.put(Integer.valueOf(fields[0]), fields[1]);
			});
		}
		catch (IOException error) { throw new ExceptionInInitializerError(error); }
		return result;
	}
}
