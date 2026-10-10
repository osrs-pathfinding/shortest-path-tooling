package shortestpath.service;

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

/** The items a route can require, named from the table {@link RouteItemNames} writes at build time. */
final class ItemCatalog {
    private static final Map<Integer, Item> ITEMS = load();

    private ItemCatalog() { }

    /** An item as {@code GET /v1/items} returns it; the key is the item id. */
    public static final class Item {
        public final String key;
        public final String name;

        Item(String key, String name) {
            this.key = key;
            this.name = name;
        }
    }

    static String name(int id) {
        Item item = ITEMS.get(id);
        return item == null ? "Item " + id : item.name;
    }

    /** The items with the given comma-separated ids, else up to 30 whose names contain the query. */
    static List<Item> find(String query, String ids) {
        if (ids != null && !ids.isBlank()) {
            return Arrays.stream(ids.split(","))
                .map(String::trim).filter(value -> value.matches("\\d+"))
                .map(Integer::valueOf).distinct().map(ITEMS::get).filter(Objects::nonNull)
                .collect(Collectors.toList());
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.length() < 2) {
            return List.of();
        }
        return ITEMS.values().stream().filter(item -> item.name.toLowerCase(Locale.ROOT).contains(needle))
            .limit(30).collect(Collectors.toList());
    }

    private static Map<Integer, Item> load() {
        Map<Integer, String> names = loadNames();
        List<Item> items = RouteItemNames.routeItemIds().stream().map(id -> {
            String name = names.get(id);
            if (name == null) {
                throw new IllegalStateException("missing MOID name for route item " + id);
            }
            return new Item(Integer.toString(id), name);
        }).sorted(Comparator.comparing((Item item) -> item.name).thenComparing(item -> item.key))
            .collect(Collectors.toList());
        Map<Integer, Item> result = new LinkedHashMap<>();
        items.forEach(item -> result.put(Integer.valueOf(item.key), item));
        return result;
    }

    private static Map<Integer, String> loadNames() {
        Map<Integer, String> result = new LinkedHashMap<>();
        InputStream input = Objects.requireNonNull(ItemCatalog.class.getResourceAsStream("/route-item-names.tsv"));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            reader.lines().filter(line -> !line.isBlank()).forEach(line -> {
                String[] fields = line.split("\\t", 2);
                result.put(Integer.valueOf(fields[0]), fields[1]);
            });
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
        return result;
    }
}
