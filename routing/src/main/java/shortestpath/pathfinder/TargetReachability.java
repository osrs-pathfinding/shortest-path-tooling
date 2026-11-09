package shortestpath.pathfinder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import shortestpath.WorldPointUtil;
import shortestpath.candidates.Walking;
import shortestpath.transport.Transport;

/**
 * Whether a player could ever end a route at a target, with the plugin's own snapping
 * ({@link ReachableGoalRule}, which the A* backend uses for blocked targets): a target on a counter, a
 * rock face or in a wall snaps to the reachable tiles beside it, as it does in game. Reachable means
 * in a walking area that a chain of transports reaches from Lumbridge, ignoring requirements (see
 * {@link Walking}). In this package to use the package-private rule.
 */
public final class TargetReachability {
    /** The plugin's default Unreachable Target Distance, which bounds how far a blocked target snaps. */
    public static final int DEFAULT_UNREACHABLE_DISTANCE = 2;

    public enum Outcome {
        /** The target is a reachable walkable tile. */
        ON_TARGET,
        /** The target snaps to reachable tiles (or transport landings) beside it. */
        SNAPPED,
        /** Nothing reachable within the snapping distance: no route ever ends here. */
        NEVER
    }

    public static final class Result {
        public final Outcome outcome;
        /** The tiles a route ends on: the target itself, its snapped goals, or none. Sorted, packed. */
        public final Set<Integer> goals;

        Result(Outcome outcome, Set<Integer> goals) {
            this.outcome = outcome;
            this.goals = goals;
        }
    }

    private final Walking walking;
    private final ReachableGoalRule rule;

    /**
     * Over {@code walking} (built from the same collision map) and the plugin's {@code transports}: only
     * landings of transports a player can get to count, those with no origin (teleports) or one on or
     * beside a reachable tile.
     */
    public TargetReachability(CollisionMap map, Walking walking,
                              Map<Integer, Set<Transport>> transports, int unreachableDistance) {
        this.walking = walking;
        Set<Integer> landings = new HashSet<>();
        for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet()) {
            int origin = entry.getKey();
            if (origin != Transport.UNDEFINED_ORIGIN && !reachableBeside(origin)) continue;
            for (Transport t : entry.getValue()) {
                if (t.getDestination() != Transport.UNDEFINED_DESTINATION) landings.add(t.getDestination());
            }
        }
        rule = new ReachableGoalRule(map, landings::contains, this::reachable, WorldPointUtil.UNDEFINED, unreachableDistance);
    }

    public Result resolve(int target) {
        if (rule.endsOnItself(target)) return new Result(Outcome.ON_TARGET, Collections.singleton(target));
        Set<Integer> goals = new TreeSet<>(rule.fallbackTiles(target));
        return goals.isEmpty() ? new Result(Outcome.NEVER, Collections.emptySet()) : new Result(Outcome.SNAPPED, goals);
    }

    private boolean reachable(int tile) {
        int index = walking.indexOf(tile);
        return index >= 0 && walking.isStructurallyReachable(walking.naturalComponent(index));
    }

    private boolean reachableBeside(int tile) {
        int x = WorldPointUtil.unpackWorldX(tile), y = WorldPointUtil.unpackWorldY(tile), plane = WorldPointUtil.unpackWorldPlane(tile);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (reachable(WorldPointUtil.packWorldPoint(x + dx, y + dy, plane))) return true;
            }
        }
        return false;
    }
}
