package shortestpath.scenarios;

import java.util.List;
import java.util.OptionalInt;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.ExactPathfinder;
import shortestpath.pathfinder.PathfinderProfile;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.profiles.CompiledAccount;

/** One run of a scenario: the pathfinder's result and how it compares with the scenario's expectation. */
public final class Observation {
    private final Scenario scenario;
    private final ScenarioRunner.Backend backend;
    private final CompiledAccount account;
    private final PathfinderResult result;
    private final ExactPathfinder exact;
    private final PathfinderProfile profile;
    private final long totalNanos;

    Observation(Scenario scenario, ScenarioRunner.Backend backend, CompiledAccount account, PathfinderResult result,
            ExactPathfinder exact, PathfinderProfile profile, long totalNanos) {
        this.scenario = scenario;
        this.backend = backend;
        this.account = account;
        this.result = result;
        this.exact = exact;
        this.profile = profile;
        this.totalNanos = totalNanos;
    }

    public Scenario getScenario() { return scenario; }
    public ScenarioRunner.Backend getBackend() { return backend; }
    public CompiledAccount getAccount() { return account; }
    public PathfinderResult getResult() { return result; }
    /** The exact pathfinder, for its phase timings and counters; {@code null} on legacy. */
    public ExactPathfinder getExact() { return exact; }
    /** The legacy profiler's measurements; {@code null} unless profiling was on. */
    public PathfinderProfile getProfile() { return profile; }
    /** Nanoseconds spent constructing and running the pathfinder. */
    public long getTotalNanos() { return totalNanos; }

    /**
     * Whether the path ends on the target or on a tile next to it. Many targets (NPCs, objects,
     * clue spots) cannot be stood on, so a path that stops beside one has reached it.
     */
    public boolean isReached() {
        if (result.isReached()) {
            return true;
        }
        List<PathStep> path = result.getPathSteps();
        return !path.isEmpty() && WorldPointUtil.distanceBetween(
            path.get(path.size() - 1).getPackedPosition(), scenario.getEndPoint()) <= 1;
    }

    /** The path cost when reached and the backend reports one, otherwise {@code null}. */
    public Integer getCost() {
        if (!isReached() || result.getPathCost() == PathfinderResult.NO_PATH_COST) {
            return null;
        }
        return result.getPathCost();
    }

    public int getPathLength() {
        return result.getPathSteps().size();
    }

    public boolean isReachabilityAsExpected() {
        return isReached() == scenario.isExpectedReachable();
    }

    /**
     * The scenario's expectation checked against this run. Reachability comes first; for a
     * reached route the exact expected length, else the minimum length, is checked.
     */
    public Assertion check() {
        int length = getPathLength();
        if (!isReachabilityAsExpected()) {
            return new Assertion(false, scenario.isExpectedReachable()
                ? "Expected reachable but no path found"
                : "Expected unreachable but path found (" + length + " steps)");
        }
        if (!scenario.isExpectedReachable()) {
            return new Assertion(true, "Expected unreachable");
        }
        OptionalInt expected = scenario.getExpectedLength();
        if (expected.isPresent()) {
            return length == expected.getAsInt() ? new Assertion(true, null)
                : new Assertion(false, "Expected path length " + expected.getAsInt() + " but got " + length);
        }
        OptionalInt minimum = scenario.getMinimumLength();
        if (minimum.isPresent()) {
            return length >= minimum.getAsInt() ? new Assertion(true, null)
                : new Assertion(false, "Expected minimum path length " + minimum.getAsInt() + " but got " + length);
        }
        return new Assertion(null, null);
    }

    /** {@code passed} is {@code null} when the scenario asserts nothing beyond reaching its target. */
    public static final class Assertion {
        public final Boolean passed;
        public final String message;

        Assertion(Boolean passed, String message) {
            this.passed = passed;
            this.message = message;
        }
    }
}
