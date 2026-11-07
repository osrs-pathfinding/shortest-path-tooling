package shortestpath.dump.candidates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds which world map icon marks a kind of place. The game draws its map icons with invisible
 * map-function objects placed beside what they mark, so the icon is found from the data: the map icon
 * placed most often within a few tiles of the plugin's rows of that kind.
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

    /** Chebyshev distance to the nearest origin on the plane, or {@link Integer#MAX_VALUE}. */
    static int nearest(List<int[]> origins, int x, int y, int plane) {
        int best = Integer.MAX_VALUE;
        for (int[] o : origins) if (o[2] == plane) best = Math.min(best, Math.max(Math.abs(o[0] - x), Math.abs(o[1] - y)));
        return best;
    }
}
