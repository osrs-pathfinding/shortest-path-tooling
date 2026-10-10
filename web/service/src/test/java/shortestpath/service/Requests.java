package shortestpath.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import shortestpath.accounts.canonical.CanonicalAccounts;
import shortestpath.routeapi.RouteApi;

/** Route requests for the canonical profiles, as the web planner sends them. */
final class Requests {
    static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private Requests() { }

    /** A request with the default policy: avoid the wilderness, allow banking, fastest resources. */
    static RouteApi.RouteRequest route(String profile, int fromX, int fromY, int toX, int toY) {
        RouteApi.RouteRequest request = new RouteApi.RouteRequest();
        request.account = CanonicalAccounts.build(profile);
        request.policy = new RouteApi.RoutePolicy();
        request.policy.avoidWilderness = true;
        request.policy.banking = "allow";
        request.policy.resources = "fastest";
        request.start = location(fromX, fromY);
        request.destination = location(toX, toY);
        return request;
    }

    private static RouteApi.Location location(int x, int y) {
        RouteApi.Location location = new RouteApi.Location();
        location.coordinate = new RouteApi.WorldPoint(x, y, 0);
        return location;
    }
}
