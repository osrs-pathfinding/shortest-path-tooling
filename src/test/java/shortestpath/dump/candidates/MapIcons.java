package shortestpath.dump.candidates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.cache.AreaManager;
import net.runelite.cache.definitions.AreaDefinition;
import net.runelite.cache.fs.Store;

/**
 * Finds which world map icon marks a kind of place. The game draws its map icons with invisible
 * map-function objects placed beside what they mark, so the icon is found from the data: the map icon
 * placed most often within a few tiles of the plugin's rows of that kind. A kind of place can have
 * more than one icon (dungeon entrances use two sprites), so the winner is widened to every icon of
 * its map area category.
 */
final class MapIcons {
    private MapIcons() { }

    /** {x, y, plane} origins of a plugin TSV's rows whose menu option (lower case) passes {@code option}. */
    static List<int[]> origins(String resource, Predicate<String> option) throws IOException {
        List<int[]> origins = new ArrayList<>();
        for (String[] cells : CacheConnectors.rows(resource)) {
            if (cells[0].isBlank()) continue;
            String menu = cells.length < 3 || cells[2].isBlank() ? "" : cells[2].trim().split("\\s+")[0].toLowerCase();
            if (option.test(menu)) origins.add(Arrays.stream(cells[0].trim().split("\\s+")).mapToInt(Integer::parseInt).toArray());
        }
        return origins;
    }

    /**
     * The map icon ({@code mapAreaId}) placed most often within {@code reach} tiles of {@code origins},
     * leaving out {@code exclude}, or -1. Ties go to the lower id, so the choice never depends on map
     * iteration order.
     */
    static int vote(List<int[]> icons, List<int[]> origins, int reach, Set<Integer> exclude) {
        Map<Integer, Integer> votes = new HashMap<>();
        for (int[] icon : icons) {
            if (!exclude.contains(icon[0]) && nearest(origins, icon[1], icon[2], icon[3]) <= reach) votes.merge(icon[0], 1, Integer::sum);
        }
        return votes.entrySet().stream()
            .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
            .map(Map.Entry::getKey).orElse(-1);
    }

    /** Each map area's ({@code mapAreaId}'s) category. */
    static Map<Integer, Integer> categories(Store store) throws IOException {
        AreaManager areas = new AreaManager(store);
        areas.load();
        Map<Integer, Integer> categories = new HashMap<>();
        for (AreaDefinition area : areas.getAreas()) categories.put(area.getId(), area.getCategory());
        return categories;
    }

    /** {@code area} and every other map area in its category, or none when {@code area} is -1. */
    static Set<Integer> sameKind(int area, Map<Integer, Integer> categories) {
        Set<Integer> kind = new TreeSet<>();
        if (area < 0) return kind;
        kind.add(area);
        Integer category = categories.get(area);
        if (category != null && category >= 0) categories.forEach((id, c) -> { if (category.equals(c)) kind.add(id); });
        return kind;
    }

    /** A marker's snapshot field, e.g. {@code mapAreaId=12,13}, or null when no icon was found. */
    static String field(Set<Integer> areas) {
        return areas.isEmpty() ? null : "mapAreaId=" + areas.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Chebyshev distance to the nearest origin on the plane, or {@link Integer#MAX_VALUE}. */
    static int nearest(List<int[]> origins, int x, int y, int plane) {
        int best = Integer.MAX_VALUE;
        for (int[] o : origins) if (o[2] == plane) best = Math.min(best, Math.max(Math.abs(o[0] - x), Math.abs(o[1] - y)));
        return best;
    }
}
