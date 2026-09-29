package travel.osrs.service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;

final class ItemCatalog
{
	private static final Map<Integer, ApiModels.ItemOption> ITEMS = load();

	private ItemCatalog() { }

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
		Map<Integer, ApiModels.ItemOption> result = new LinkedHashMap<>();
		Arrays.stream(ItemID.class.getFields()).filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == int.class)
			.sorted(Comparator.comparingInt(field -> field.getName().length()))
			.forEach(field -> add(result, field));
		return result;
	}

	private static void add(Map<Integer, ApiModels.ItemOption> result, Field field)
	{
		try
		{
			int id = field.getInt(null);
			String name = Arrays.stream(field.getName().split("_"))
				.map(word -> word.isEmpty() ? word : word.substring(0, 1) + word.substring(1).toLowerCase(Locale.ROOT))
				.collect(Collectors.joining(" "));
			result.putIfAbsent(id, new ApiModels.ItemOption(Integer.toString(id), name));
		}
		catch (IllegalAccessException error) { throw new ExceptionInInitializerError(error); }
	}
}
