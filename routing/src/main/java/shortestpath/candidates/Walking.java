package shortestpath.candidates;

import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.astar.RoutingStaticBuilder;

/**
 * The plugin's walking graph as the candidates read it: walkable tiles, their natural walking areas
 * (components), and whether a chain of transports reaches each area from Lumbridge, ignoring
 * requirements ({@link RoutingStaticBuilder#walkingGraph}). An interface so tests can use a small grid.
 */
public interface Walking {
    /** No tile. Packed tiles on planes 2 and 3 are negative, so this is the plugin's own sentinel, not "below 0". */
    int NONE = WorldPointUtil.UNDEFINED;

    int tileCount();

    int tile(int index);

    /** The tile's index, or -1 when it is not walkable. */
    int indexOf(int tile);

    int naturalComponentCount();

    /** In {@code 1..naturalComponentCount()}. */
    int naturalComponent(int index);

    boolean isStructurallyReachable(int naturalComponent);

    static Walking of(RoutingStaticBuilder.WalkingGraph graph) {
        return new Walking() {
            @Override public int tileCount() { return graph.tileCount(); }
            @Override public int tile(int index) { return graph.tile(index); }
            @Override public int indexOf(int tile) { return graph.indexOf(tile); }
            @Override public int naturalComponentCount() { return graph.naturalComponentCount(); }
            @Override public int naturalComponent(int index) { return graph.naturalComponent(index); }
            @Override public boolean isStructurallyReachable(int component) { return graph.isStructurallyReachable(component); }
        };
    }

    /**
     * Where a footprint is used from: the walkable tile on it or within {@code reach} of it nearest its
     * centre, or {@link #NONE}.
     */
    default int stand(int x, int y, int plane, int sizeX, int sizeY, int reach) {
        int best = NONE, bestDistance = Integer.MAX_VALUE;
        // Doubled coordinates keep the centre of even-sized footprints exact.
        int cx = 2 * x + sizeX - 1, cy = 2 * y + sizeY - 1;
        for (int tx = x - reach; tx < x + sizeX + reach; tx++) {
            for (int ty = y - reach; ty < y + sizeY + reach; ty++) {
                int tile = WorldPointUtil.packWorldPoint(tx, ty, plane);
                if (indexOf(tile) < 0) continue;
                int distance = Math.abs(2 * tx - cx) + Math.abs(2 * ty - cy);
                if (distance < bestDistance) { best = tile; bestDistance = distance; }
            }
        }
        return best;
    }

    /** The walking area of a walkable tile. */
    default int area(int tile) {
        return naturalComponent(indexOf(tile));
    }

    /** Walkable tiles in each area, indexed by area. */
    default int[] areaSizes() {
        int[] sizes = new int[naturalComponentCount() + 1];
        for (int i = 0; i < tileCount(); i++) sizes[naturalComponent(i)]++;
        return sizes;
    }
}
