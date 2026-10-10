package shortestpath.routeapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

/** Reads the canonical account builds from {@code corpus/profiles}. */
final class Fixtures {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Fixtures() { }

    static RouteApi.AccountBuild profile(String name) {
        File file = new File(System.getProperty("corpus.dir", "../corpus"), "profiles/" + name + ".json");
        try {
            return MAPPER.readValue(file, RouteApi.AccountBuild.class);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }

    static RouteApi.RoutePolicy policy() {
        RouteApi.RoutePolicy policy = new RouteApi.RoutePolicy();
        policy.avoidWilderness = true;
        policy.banking = "allow";
        policy.resources = "fastest";
        return policy;
    }
}
