package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.astar.RoutingStaticBuilder;
import shortestpath.transport.ResourceLoader;

public class TargetReachabilityTest {
    private static TargetReachability reachability;

    @BeforeClass
    public static void build() {
        CollisionMap map = new CollisionMap(PluginResources.map());
        reachability = new TargetReachability(map, RoutingStaticBuilder.walkingGraph(map, ResourceLoader.transports()),
            PluginResources.transports(), TargetReachability.DEFAULT_UNREACHABLE_DISTANCE);
    }

    private static TargetReachability.Result resolve(int x, int y, int plane) {
        return reachability.resolve(WorldPointUtil.packWorldPoint(x, y, plane));
    }

    @Test
    public void aReachableWalkableTileIsItsOwnGoal() {
        TargetReachability.Result lumbridge = resolve(3222, 3218, 0);
        assertEquals(TargetReachability.Outcome.ON_TARGET, lumbridge.outcome);
        assertEquals(1, lumbridge.goals.size());
    }

    @Test
    public void aBlockedBankSnapsToTheReachableTilesBesideIt() {
        TargetReachability.Result bank = resolve(3768, 3899, 0);
        assertEquals(TargetReachability.Outcome.SNAPPED, bank.outcome);
        assertFalse(bank.goals.isEmpty());
        assertFalse(bank.goals.contains(WorldPointUtil.packWorldPoint(3768, 3899, 0)));
    }

    @Test
    public void aBankWithNothingReachableNearIsNeverReached() {
        TargetReachability.Result bank = resolve(2280, 2544, 0);
        assertEquals(TargetReachability.Outcome.NEVER, bank.outcome);
        assertTrue(bank.goals.isEmpty());
    }
}
