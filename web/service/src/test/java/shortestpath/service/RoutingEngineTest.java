package shortestpath.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import shortestpath.routeapi.RouteApi;

class RoutingEngineTest {
    private static final RoutingEngine ENGINE = new RoutingEngine(Requests.MAPPER);

    @Test
    void canonicalProfileRunsThroughExactPathfinder() {
        RouteApi.RouteRequest request = Requests.route("mid", 3222, 3218, 3210, 3424);
        new SchemaValidator().validate(Requests.MAPPER.valueToTree(request));

        RouteApi.RoutePlan plan = ENGINE.route(request);

        assertTrue(plan.reachable);
        assertTrue(plan.costTicks > 0);
        assertEquals("exact-v1", plan.metadata.routingEngineVersion);
        assertFalse(plan.segments.isEmpty());
        assertTrue(plan.segments.stream().filter(RouteApi.TravelSegment.class::isInstance)
                .map(RouteApi.TravelSegment.class::cast).anyMatch(segment -> !segment.requirements.isEmpty()),
            "semantic travel steps should expose their player-facing requirements");
    }

    @Test
    void settingsReachThePathfinder() {
        RouteApi.RouteRequest request = Requests.route("maxed", 3222, 3218, 2757, 3477);
        request.settings.put("includeBankPath", false);
        request.settings.put("useTeleportationItems", "INVENTORY");

        RouteApi.RoutePlan fastest = ENGINE.route(request);
        List<String> avoided = List.of("useTeleportationSpells", "useTeleportationSpellsHome",
            "useTeleportationMinigames", "useTeleportationPortals", "useFairyRings", "useSpiritTrees");
        avoided.forEach(key -> request.settings.put(key, false));
        request.settings.put("useTeleportationItems", "NONE");
        RouteApi.RoutePlan restricted = ENGINE.route(request);

        assertTrue(fastest.reachable && restricted.reachable);
        assertTrue(restricted.costTicks > fastest.costTicks, "avoiding teleports should make the route longer");
        for (RouteApi.RoutePlan plan : List.of(fastest, restricted)) {
            assertFalse(plan.segments.stream().anyMatch(RouteApi.BankSegment.class::isInstance),
                "without a bank path the route must not visit a bank");
        }
        assertFalse(restricted.segments.stream().filter(RouteApi.TravelSegment.class::isInstance)
            .map(segment -> ((RouteApi.TravelSegment) segment).transportId.split(":")[0])
            .anyMatch(List.of("TELEPORTATION_ITEM", "TELEPORTATION_SPELL", "FAIRY_RING", "SPIRIT_TREE")::contains));
    }

    @Test
    void theLegacyBackendCanBeChosen() {
        RouteApi.RouteRequest request = Requests.route("mid", 3222, 3218, 3210, 3424);
        request.settings.put("pathfinderBackend", "LEGACY");
        RouteApi.RoutePlan plan = ENGINE.route(request);
        assertTrue(plan.reachable);
        assertEquals("legacy-v1", plan.metadata.routingEngineVersion);
    }
}
