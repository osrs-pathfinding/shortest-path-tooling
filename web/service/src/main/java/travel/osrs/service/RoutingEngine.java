package travel.osrs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Set;
import shortestpath.accounts.Account;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.ExactPathfinder;
import shortestpath.pathfinder.ExactRoutingStaticProvider;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PluginResources;
import shortestpath.pathfinder.exact.ExactRoutingSession;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.profiles.AccountPathfinderConfig;
import shortestpath.profiles.Setup;
import shortestpath.routeapi.AccountBuilds;
import shortestpath.routeapi.RouteApi;
import shortestpath.routeapi.RoutePlans;
import shortestpath.routeapi.RoutePolicies;

/** Plans routes on the exact pathfinder, caching compiled accounts and finished plans. */
final class RoutingEngine
{
	private final ObjectMapper mapper;
	private final RoutingStatic routingStatic;
	private final Cache<String, CompiledAccount> accounts = Caffeine.newBuilder().maximumSize(32).build();
	private final Cache<String, RouteApi.RoutePlan> routes = Caffeine.newBuilder().maximumSize(256).build();

	RoutingEngine(ObjectMapper mapper)
	{
		this.mapper = mapper;
		this.routingStatic = new ExactRoutingStaticProvider(() -> new CollisionMap(PluginResources.map())).get();
	}

	RouteApi.RoutePlan route(RouteApi.RouteRequest request)
	{
		String accountKey = json(request.account) + json(request.policy);
		int start = RoutePlans.packed(request.start.coordinate);
		int target = RoutePlans.packed(request.destination.coordinate);
		String routeKey = accountKey + '|' + start + '|' + target;
		RouteApi.RoutePlan cached = routes.getIfPresent(routeKey);
		if (cached != null) return cached;

		CompiledAccount account = accounts.get(accountKey, ignored -> compile(request));
		ExactPathfinder pathfinder = new ExactPathfinder(account.config, routingStatic, account.session,
			start, Set.of(target), null, account.config.getExactHeuristicWeight());
		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();
		if (result == null) throw new IllegalStateException("ExactPathfinder returned no result");

		RouteApi.RoutePlan plan = RoutePlans.plan(request.start, request.destination, result, account.config,
			ItemCatalog::name);
		routes.put(routeKey, plan);
		return plan;
	}

	/** Compiles and refreshes the account on the calling thread, which the plugin treats as the client thread. */
	private static CompiledAccount compile(RouteApi.RouteRequest request)
	{
		Account account = AccountBuilds.toAccount(request.account);
		Setup setup = new Setup(account.toBuilder(), RoutePolicies.toSettings(request.policy, account.poh()));
		return new CompiledAccount(setup.compile().getConfig());
	}

	private String json(Object value)
	{
		try { return mapper.writeValueAsString(value); }
		catch (JsonProcessingException error) { throw new IllegalArgumentException("invalid route request", error); }
	}

	private static final class CompiledAccount
	{
		private final AccountPathfinderConfig config;
		private final ExactRoutingSession session = new ExactRoutingSession();
		private CompiledAccount(AccountPathfinderConfig config) { this.config = config; }
	}
}
