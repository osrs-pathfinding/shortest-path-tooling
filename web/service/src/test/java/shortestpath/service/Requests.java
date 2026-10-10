package shortestpath.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import shortestpath.accounts.canonical.CanonicalAccounts;
import shortestpath.routeapi.AccountJson;
import shortestpath.routeapi.RouteApi;

/** Route requests for the canonical accounts, as the web planner sends them. */
final class Requests {
    static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private Requests() { }

    /** A request with the planner's default settings. */
    static RouteApi.RouteRequest route(String profile, int fromX, int fromY, int toX, int toY) {
        RouteApi.RouteRequest request = new RouteApi.RouteRequest();
        request.account = AccountJson.of(profile, CanonicalAccounts.account(profile));
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
