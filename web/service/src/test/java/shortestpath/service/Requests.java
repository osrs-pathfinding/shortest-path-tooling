package shortestpath.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import shortestpath.routeapi.RouteApi;

/** Route requests for the canonical profiles, which the build copies from {@code corpus/profiles}. */
final class Requests {
    static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private Requests() { }

    /** A request with the default policy: avoid the wilderness, allow banking, fastest resources. */
    static RouteApi.RouteRequest route(String profile, int fromX, int fromY, int toX, int toY) {
        RouteApi.RouteRequest request = new RouteApi.RouteRequest();
        request.account = account(profile);
        request.policy = new RouteApi.RoutePolicy();
        request.policy.avoidWilderness = true;
        request.policy.banking = "allow";
        request.policy.resources = "fastest";
        request.start = location(fromX, fromY);
        request.destination = location(toX, toY);
        return request;
    }

    private static RouteApi.AccountBuild account(String profile) {
        try (InputStream json = Requests.class.getResourceAsStream("/profiles/" + profile + ".json")) {
            return MAPPER.readValue(json, RouteApi.AccountBuild.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static RouteApi.Location location(int x, int y) {
        RouteApi.Location location = new RouteApi.Location();
        location.coordinate = new RouteApi.WorldPoint(x, y, 0);
        return location;
    }
}
