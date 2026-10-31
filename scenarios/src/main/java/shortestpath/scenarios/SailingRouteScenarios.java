package shortestpath.scenarios;

import static shortestpath.profiles.Profiles.UNIT_TEST;

/**
 * Routes at sea. Suite {@code sailing-routes}: the dashboard runs each with the normal search and, when the
 * plugin checkout has it, with the experimental sailing search at the scenario's speed, keeping the named boat's
 * hull clear. The {@code sailingBenchmark} task runs every route with every boat and speed; scenarios with the
 * same start and target are one route there.
 */
final class SailingRouteScenarios {
    private SailingRouteScenarios() { }

    static void define(Suite suite) {
        // Long routes between ports
        suite.scenario("Port Sarim to the Pandemonium (sloop)", "sailing")
            .from(3048, 3184, 0).to(3074, 2983, 0)
            .profile(UNIT_TEST)
            .sailing(1.5, "sloop");

        suite.scenario("Port Sarim to Catherby (sloop)", "sailing")
            .from(3053, 3187, 0).to(2797, 3410, 0)
            .profile(UNIT_TEST)
            .sailing(1.5, "sloop");

        suite.scenario("Musa Point to Corsair Cove (sloop)", "sailing")
            .from(2961, 3150, 0).to(2585, 2843, 0)
            .profile(UNIT_TEST)
            .sailing(1.5, "sloop");

        suite.scenario("Ardougne to Port Tyras (sloop)", "sailing")
            .from(2663, 3259, 0).to(2142, 3117, 0)
            .profile(UNIT_TEST)
            .sailing(1.5, "sloop");

        // Tight spots
        suite.scenario("Past the rocks off Mudskipper Point (raft)", "sailing-tight")
            .from(2948, 3074, 0).to(3074, 2983, 0)
            .profile(UNIT_TEST)
            .sailing(3.0, "raft");

        suite.scenario("Past the rocks off Mudskipper Point (sloop)", "sailing-tight")
            .from(2948, 3074, 0).to(3074, 2983, 0)
            .profile(UNIT_TEST)
            .sailing(3.0, "sloop");

        suite.scenario("Ruins of Unkah past the Elid Delta islands (skiff)", "sailing-tight")
            .from(3143, 2847, 0).to(3273, 2738, 0)
            .profile(UNIT_TEST)
            .sailing(1.5, "skiff");

        // A sloop is too big to get past the Elid Delta islands, so its sailing search should find the
        // target unreachable
        suite.scenario("Ruins of Unkah past the Elid Delta islands (sloop)", "sailing-tight")
            .from(3143, 2847, 0).to(3273, 2738, 0)
            .profile(UNIT_TEST)
            .sailing(1.5, "sloop");

        // Rocky seas
        suite.scenario("Through the Storm Tempor rocks past Tempoross Cove (raft)", "sailing-rocky")
            .from(2928, 2842, 0).to(3125, 2869, 0)
            .profile(UNIT_TEST)
            .sailing(1.5, "raft");

        suite.scenario("Through the Storm Tempor rocks past Tempoross Cove (sloop)", "sailing-rocky")
            .from(2928, 2842, 0).to(3125, 2869, 0)
            .profile(UNIT_TEST)
            .sailing(1.5, "sloop");

        suite.scenario("North of Charred Island to south of Wyrmscraig (raft)", "sailing-rocky")
            .from(2672, 2452, 0).to(2515, 2160, 0)
            .profile(UNIT_TEST)
            .sailing(1.5, "raft");

        suite.scenario("North of Charred Island to south of Wyrmscraig (sloop)", "sailing-rocky")
            .from(2672, 2452, 0).to(2515, 2160, 0)
            .profile(UNIT_TEST)
            .sailing(1.5, "sloop");
    }
}
