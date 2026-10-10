package shortestpath.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Set;
import shortestpath.accounts.Account;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.ExactPathfinder;
import shortestpath.pathfinder.ExactRoutingStaticProvider;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PluginResources;
import shortestpath.pathfinder.exact.ExactRoutingSession;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.profiles.AccountPathfinderConfig;
import shortestpath.profiles.CompiledAccount;
import shortestpath.profiles.PluginSettings;
import shortestpath.routeapi.PlannerSettings;
import shortestpath.routeapi.RouteApi;
import shortestpath.routeapi.RoutePlans;

/**
 * Plans routes on the backend the settings choose, caching each account's config and exact search
 * session, and finished plans.
 */
final class RoutingEngine {
    private final ObjectMapper mapper;
    private final RoutingStatic routingStatic;
    private final Cache<String, AccountSession> accounts = Caffeine.newBuilder().maximumSize(32).build();
    private final Cache<String, RouteApi.RoutePlan> routes = Caffeine.newBuilder().maximumSize(256).build();

    RoutingEngine(ObjectMapper mapper) {
        this.mapper = mapper;
        this.routingStatic = new ExactRoutingStaticProvider(() -> new CollisionMap(PluginResources.map())).get();
    }

    RouteApi.RoutePlan route(RouteApi.RouteRequest request) {
        String accountKey = json(request.account) + json(request.settings);
        int start = RoutePlans.packed(request.start.coordinate);
        int target = RoutePlans.packed(request.destination.coordinate);
        String routeKey = accountKey + '|' + start + '|' + target;
        RouteApi.RoutePlan cached = routes.getIfPresent(routeKey);
        if (cached != null) {
            return cached;
        }

        AccountSession account = accounts.get(accountKey, ignored -> new AccountSession(request));
        PathfinderResult result;
        if (account.backend == PathfinderBackend.LEGACY) {
            Pathfinder pathfinder = new Pathfinder(account.config, start, Set.of(target));
            pathfinder.run();
            result = pathfinder.getResult();
        } else {
            ExactPathfinder pathfinder = new ExactPathfinder(account.config, routingStatic, account.session,
                start, Set.of(target), null, account.config.getExactHeuristicWeight());
            pathfinder.run();
            result = pathfinder.getResult();
        }
        if (result == null) {
            throw new IllegalStateException("the pathfinder returned no result");
        }

        RouteApi.RoutePlan plan = RoutePlans.plan(request.start, request.destination, result, account.config,
            account.backend, ItemCatalog::name);
        routes.put(routeKey, plan);
        return plan;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("invalid route request", error);
        }
    }

    /** A request's account and settings compiled once, and the exact search state its routes share. */
    private static final class AccountSession {
        private final AccountPathfinderConfig config;
        private final PathfinderBackend backend;
        private final ExactRoutingSession session = new ExactRoutingSession();

        private AccountSession(RouteApi.RouteRequest request) {
            Account account = request.account.toAccount();
            PluginSettings settings = PlannerSettings.fromJson(request.settings);
            if (account.poh() != null) {
                settings.applyPoh(account.poh(), true);
            }
            config = CompiledAccount.of(account, settings).getConfig();
            backend = settings.pathfinderBackend();
        }
    }
}
