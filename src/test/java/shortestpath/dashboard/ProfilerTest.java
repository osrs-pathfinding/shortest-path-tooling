package shortestpath.dashboard;

import java.util.EnumSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueRegion;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderProfile;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.ProfilingPathfinder;
import shortestpath.profiles.ProfileContext;
import shortestpath.profiles.Profiles;
import shortestpath.profiles.Setup;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that {@link ProfilingPathfinder} produces the same results as the
 * standard {@link Pathfinder} and that profiling overhead is negligible.
 * Lives in the dashboard package because {@link ProfilingPathfinder} is the
 * engine behind {@link DashboardTest}.
 */
public class ProfilerTest {
    private PathfinderConfig pathfinderConfig;

    @Before
    public void setUp() {
        pathfinderConfig = Profiles.ALL.setup(new ProfileContext(WorldPointUtil.UNDEFINED, true))
            .compile().getConfig();
    }

    @Test
    public void profilingDoesNotAffectResults() {
        // Routes chosen to cover distinct code paths:
        //   walk          — pure tile expansion, no transports
        //   teleport      — abstract-node expansion dominates
        //   multi-plane   — z != 0, exercises plane-packed positions
        //   long cross-map — route 22 (Port Sarim → Land's End), the route that exposed the
        //                    missing enqueue profiling; may hit the cutoff, but both
        //                    implementations must produce the same best-effort result
        int[][] routes = {
            // sx,   sy,    sz,  tx,   ty,    tz   description
            { 3222, 3218,   0,  3105, 3251,   0 }, // Lumbridge → Draynor Village (walk)
            { 3222, 3218,   0,  3213, 3428,   0 }, // Lumbridge → Varrock (teleport spells available)
            { 2964, 3378,   0,  2961, 3339,   2 }, // Falador → White Knight 2F (multi-plane)
            { 3038, 3192,   0,  1496, 3403,   0 }, // Port Sarim → Land's End (long cross-map)
        };

        // Warm up both implementations on the short walk so JIT does not skew the overhead ratio.
        int warmStart  = WorldPointUtil.packWorldPoint(routes[0][0], routes[0][1], routes[0][2]);
        int warmTarget = WorldPointUtil.packWorldPoint(routes[0][3], routes[0][4], routes[0][5]);
        for (int i = 0; i < 10; i++) {
            new Pathfinder(pathfinderConfig, warmStart, Set.of(warmTarget)).run();
            new ProfilingPathfinder(pathfinderConfig, warmStart, Set.of(warmTarget)).run();
        }

        // Correctness check: profiled and unprofiled must produce identical results for every route.
        for (int[] r : routes) {
            int s = WorldPointUtil.packWorldPoint(r[0], r[1], r[2]);
            int t = WorldPointUtil.packWorldPoint(r[3], r[4], r[5]);
            String label = "(" + r[0] + "," + r[1] + "," + r[2] + ")→(" + r[3] + "," + r[4] + "," + r[5] + ")";

            Pathfinder unprofiled = new Pathfinder(pathfinderConfig, s, Set.of(t));
            unprofiled.run();
            ProfilingPathfinder profiled = new ProfilingPathfinder(pathfinderConfig, s, Set.of(t));
            profiled.run();

            PathfinderResult ur = unprofiled.getResult();
            PathfinderResult pr = profiled.getResult();
            assertNotNull("Unprofiled result null for " + label, ur);
            assertNotNull("Profiled result null for " + label, pr);
            assertTrue("Reached state differs for " + label, ur.isReached() == pr.isReached());
            assertTrue("Path length differs for " + label + ": " +
                ur.getPathSteps().size() + " vs " + pr.getPathSteps().size(),
                ur.getPathSteps().size() == pr.getPathSteps().size());
        }

        // Profile completeness: verify all instrumented phases recorded data on the short walk.
        ProfilingPathfinder timed = new ProfilingPathfinder(pathfinderConfig, warmStart, Set.of(warmTarget));
        long timedStart = System.nanoTime();
        timed.run();
        long timedNanos = System.nanoTime() - timedStart;

        PathfinderProfile profile = timed.getProfile();
        assertNotNull("Profile data should be present", profile);
        assertTrue("Should have checked some nodes", profile.getTileNeighborsAdded() > 0);
        assertTrue("addNeighbors time should be positive", profile.getAddNeighborsNanos() > 0);
        assertTrue("enqueue time should be positive", profile.getEnqueueNanos() > 0);
        assertTrue("Peak boundary size should be positive", profile.getPeakBoundarySize() > 0);

        // Profiling overhead must not cause catastrophic slowdown on the short walk.
        // Use an absolute cap (2× the calculation cutoff) rather than a ratio,
        // because ratio-based checks are unreliable when the unprofiled run is very fast.
        long maxTimedNanos = 500 * 2_000_000L;
        assertTrue("Profiling overhead too high: profiled=" + timedNanos / 1_000_000 + "ms" +
            ", limit=" + maxTimedNanos / 1_000_000 + "ms",
            timedNanos < maxTimedNanos);
    }

    /**
     * Verifies that profiled and unprofiled results match when the league-region filter
     * is active (Kandarin unlocked only — so Misthalin/Asgarnia are blocked).
     * A route that crosses from Falador (Asgarnia) into Varrock (Misthalin) should be
     * unreachable, and both implementations must agree on that.
     */
    @Test
    public void profilingDoesNotAffectResultsLeague() {
        PathfinderConfig leaguePfConfig = Profiles.ALL.setup(new ProfileContext(WorldPointUtil.UNDEFINED, true))
            .compile().getConfig();
        // Kandarin only: Misthalin (Varrock area) and Asgarnia (Falador area) are blocked.
        leaguePfConfig.getLeagueModeState().setForTest(true, EnumSet.of(LeagueRegion.KANDARIN));

        int[][] routes = {
            // Both endpoints inside Kandarin — should be reachable.
            { 2728, 3486, 0,  2690, 3463, 0 }, // Catherby → Fishing Guild (Kandarin)
            // Source in Kandarin, target in blocked Misthalin — should be unreachable.
            { 2728, 3486, 0,  3213, 3428, 0 }, // Catherby → Varrock (crosses into Misthalin)
        };

        for (int[] r : routes) {
            int s = WorldPointUtil.packWorldPoint(r[0], r[1], r[2]);
            int t = WorldPointUtil.packWorldPoint(r[3], r[4], r[5]);
            String label = "League (" + r[0] + "," + r[1] + ")→(" + r[3] + "," + r[4] + ")";

            Pathfinder unprofiled = new Pathfinder(leaguePfConfig, s, Set.of(t));
            unprofiled.run();
            ProfilingPathfinder profiled = new ProfilingPathfinder(leaguePfConfig, s, Set.of(t));
            profiled.run();

            PathfinderResult ur = unprofiled.getResult();
            PathfinderResult pr = profiled.getResult();
            assertNotNull("Unprofiled result null for " + label, ur);
            assertNotNull("Profiled result null for " + label, pr);
            assertTrue("Reached state differs for " + label, ur.isReached() == pr.isReached());
            assertTrue("Path length differs for " + label + ": " +
                ur.getPathSteps().size() + " vs " + pr.getPathSteps().size(),
                ur.getPathSteps().size() == pr.getPathSteps().size());
        }
    }

    /**
     * Verifies that profiled and unprofiled results match when bank-path mode is enabled.
     * A route that benefits from picking up a teleportation item at a bank should produce
     * identical results from both implementations.
     */
    @Test
    public void profilingDoesNotAffectResultsBankPath() {
        Setup bank = Profiles.ALL.setup(new ProfileContext(WorldPointUtil.UNDEFINED, true));
        bank.settings.setIncludeBankPath(true);
        PathfinderConfig bankPfConfig = bank.compile().getConfig();

        int[][] routes = {
            // Lumbridge → Ardougne: long walk without bank, but bank-path mode unlocks
            // teleports picked up en route (e.g. Ardougne teleport from GE bank).
            { 3222, 3218, 0,  2661, 3305, 0 }, // Lumbridge → Ardougne
            // Short walk to verify bank-path doesn't break simple cases.
            { 3222, 3218, 0,  3105, 3251, 0 }, // Lumbridge → Draynor
        };

        for (int[] r : routes) {
            int s = WorldPointUtil.packWorldPoint(r[0], r[1], r[2]);
            int t = WorldPointUtil.packWorldPoint(r[3], r[4], r[5]);
            String label = "BankPath (" + r[0] + "," + r[1] + ")→(" + r[3] + "," + r[4] + ")";

            Pathfinder unprofiled = new Pathfinder(bankPfConfig, s, Set.of(t));
            unprofiled.run();
            ProfilingPathfinder profiled = new ProfilingPathfinder(bankPfConfig, s, Set.of(t));
            profiled.run();

            PathfinderResult ur = unprofiled.getResult();
            PathfinderResult pr = profiled.getResult();
            assertNotNull("Unprofiled result null for " + label, ur);
            assertNotNull("Profiled result null for " + label, pr);
            assertTrue("Reached state differs for " + label, ur.isReached() == pr.isReached());
            assertTrue("Path length differs for " + label + ": " +
                ur.getPathSteps().size() + " vs " + pr.getPathSteps().size(),
                ur.getPathSteps().size() == pr.getPathSteps().size());
        }
    }
}
