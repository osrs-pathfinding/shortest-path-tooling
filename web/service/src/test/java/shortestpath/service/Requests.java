package shortestpath.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import shortestpath.accounts.AccountBuild;
import shortestpath.routeapi.RouteApi;

/** Route requests for the canonical profiles, as the web planner sends them. */
final class Requests {
    static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private Requests() { }

    /** A request with the default policy: avoid the wilderness, allow banking, fastest resources. */
    static RouteApi.RouteRequest route(String profile, int fromX, int fromY, int toX, int toY) {
        RouteApi.RouteRequest request = new RouteApi.RouteRequest();
        try {
            request.account = MAPPER.readValue(new File(System.getProperty("corpus.dir"), "profiles/" + profile + ".json"),
                AccountBuild.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
