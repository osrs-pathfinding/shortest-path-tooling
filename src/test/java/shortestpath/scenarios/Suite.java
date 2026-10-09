package shortestpath.scenarios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects a suite's scenarios: each {@link #scenario} call adds one, configured by chaining. */
public final class Suite {
    private final List<Scenario.Builder> scenarios = new ArrayList<>();

    /** Adds a scenario and returns its builder; the suite builds it after {@code define} returns. */
    public Scenario.Builder scenario(String name, String category) {
        Scenario.Builder builder = Scenario.scenario(name, category);
        scenarios.add(builder);
        return builder;
    }

    void add(Scenario.Builder scenario) {
        scenarios.add(scenario);
    }

    List<Scenario.Builder> scenarios() {
        return Collections.unmodifiableList(scenarios);
    }
}
