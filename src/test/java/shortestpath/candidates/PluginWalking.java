package shortestpath.candidates;

import shortestpath.pathfinder.astar.RoutingStaticBuilder;

/**
 * Adapts the walking graph that the plugin's routing-component builder produces to the {@link Walking}
 * interface the candidates read. The builder lives in the plugin's test sources, which the root test
 * sources compile, so this adapter lives here rather than in the shared routing library.
 */
public final class PluginWalking {
    private PluginWalking() {
    }

    public static Walking of(RoutingStaticBuilder.WalkingGraph graph) {
        return new Walking() {
            @Override public int tileCount() { return graph.tileCount(); }
            @Override public int tile(int index) { return graph.tile(index); }
            @Override public int indexOf(int tile) { return graph.indexOf(tile); }
            @Override public int naturalComponentCount() { return graph.naturalComponentCount(); }
            @Override public int naturalComponent(int index) { return graph.naturalComponent(index); }
            @Override public boolean isStructurallyReachable(int component) { return graph.isStructurallyReachable(component); }
        };
    }
}
