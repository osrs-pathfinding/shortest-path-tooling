package shortestpath.routeapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The route API contract ({@code corpus/schemas/route-api-v1}) as Jackson-bindable classes: a
 * request names an account ({@link AccountJson}), the settings that differ from the planner's
 * defaults, and two locations; a plan is a list of walk, travel and bank segments.
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

    public static final class RouteRequest {
        public AccountJson account;
        /** The plugin settings that differ from the planner's defaults; see {@link PlannerSettings}. */
        // Sorted so that equal settings share route and account cache keys.
        public SortedMap<String, Object> settings = new TreeMap<>();
        public Location start;
        public Location destination;
    }

    /** A named starting account, as {@code GET /v1/presets} lists them. */
    public static final class Preset {
        public final String id;
        public final String name;
        public final AccountJson account;

        public Preset(String id, String name, AccountJson account) {
            this.id = id;
            this.name = name;
            this.account = account;
        }
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
