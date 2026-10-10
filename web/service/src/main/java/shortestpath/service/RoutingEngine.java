package shortestpath.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Set;
import shortestpath.accounts.Account;
import shortestpath.accounts.AccountBuilds;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.ExactPathfinder;
import shortestpath.pathfinder.ExactRoutingStaticProvider;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PluginResources;
import shortestpath.pathfinder.exact.ExactRoutingSession;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.profiles.AccountPathfinderConfig;
import shortestpath.profiles.CompiledAccount;
import shortestpath.routeapi.RouteApi;
import shortestpath.routeapi.RoutePlans;
import shortestpath.routeapi.RoutePolicies;

/** Plans routes on the exact pathfinder, caching each account's config and search session, and finished plans. */
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
        String accountKey = json(request.account) + json(request.policy);
        int start = RoutePlans.packed(request.start.coordinate);
        int target = RoutePlans.packed(request.destination.coordinate);
        String routeKey = accountKey + '|' + start + '|' + target;
        RouteApi.RoutePlan cached = routes.getIfPresent(routeKey);
        if (cached != null) {
            return cached;
        }

        AccountSession account = accounts.get(accountKey, ignored -> new AccountSession(request));
        ExactPathfinder pathfinder = new ExactPathfinder(account.config, routingStatic, account.session,
            start, Set.of(target), null, account.config.getExactHeuristicWeight());
        pathfinder.run();
        PathfinderResult result = pathfinder.getResult();
        if (result == null) {
            throw new IllegalStateException("ExactPathfinder returned no result");
        }

        RouteApi.RoutePlan plan = RoutePlans.plan(request.start, request.destination, result, account.config,
            ItemCatalog::name);
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

    /** A request's account and policy compiled once, and the exact search state its routes share. */
    private static final class AccountSession {
        private final AccountPathfinderConfig config;
        private final ExactRoutingSession session = new ExactRoutingSession();

        private AccountSession(RouteApi.RouteRequest request) {
            Account account = AccountBuilds.toAccount(request.account);
            config = CompiledAccount.of(account, RoutePolicies.toSettings(request.policy, account.poh())).getConfig();
        }
    }
}
