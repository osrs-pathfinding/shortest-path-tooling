package shortestpath.routeapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import shortestpath.accounts.AccountBuild;

/**
 * The route API contract ({@code corpus/schemas/route-api-v1} and {@code route-policy-v1}) as
 * Jackson-bindable classes: a request names an {@link AccountBuild}, two locations and a route
 * policy; a plan is a list of walk, travel and bank segments.
 */
public final class RouteApi {
    private RouteApi() { }

    public static final class WorldPoint {
        public int x;
        public int y;
        public int plane;

        public WorldPoint() { }

        public WorldPoint(int x, int y, int plane) {
            this.x = x;
            this.y = y;
            this.plane = plane;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Location {
        public String placeId;
        public String name;
        public WorldPoint coordinate;
    }

    public static final class RoutePolicy {
        public boolean avoidWilderness;
        public String banking;
        public String resources;
        public List<String> avoidedTransportTypes = new ArrayList<>();
        public String teleportItems = "owned";
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public Integer currencyThreshold;
        // Sorted so that equivalent policies share route and account cache keys.
        public SortedMap<String, Integer> transportThresholds = new TreeMap<>();
        public List<String> declaredUnlocks = new ArrayList<>();
    }

    public static final class RouteRequest {
        public AccountBuild account;
        public Location start;
        public Location destination;
        public RoutePolicy policy;
    }

    public static final class RoutePlan {
        public String apiVersion = "v1";
        public boolean reachable;
        public Integer costTicks;
        public Location start;
        public Location destination;
        public List<Object> segments = new ArrayList<>();
        public Metadata metadata;
    }

    public static final class WalkSegment {
        public String kind = "walk";
        public int costTicks;
        public List<WorldPoint> path;
    }

    public static final class TravelSegment {
        public String kind;
        public String transportId;
        public String name;
        public WorldPoint from;
        public WorldPoint to;
        public int costTicks;
        public List<Capability> requirements = new ArrayList<>();
    }

    public static final class BankSegment {
        public String kind = "bank";
        public WorldPoint location;
        public int costTicks;
    }

    public static final class Capability {
        public String kind;
        public String name;
    }

    public static final class Metadata {
        public String worldDataVersion;
        public int accountSchemaVersion = 1;
        public String routingEngineVersion;
    }
}
