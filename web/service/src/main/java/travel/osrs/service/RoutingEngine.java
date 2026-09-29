package travel.osrs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.ExactPathfinder;
import shortestpath.pathfinder.ExactRoutingStaticProvider;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.ServicePathfinderConfig;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.pathfinder.exact.ExactRoutingSession;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.transport.Transport;

final class RoutingEngine
{
	private static final Comparator<Transport> TRANSPORT_ORDER = Comparator
		.comparing((Transport value) -> value.getType() == null ? "TRANSPORT" : value.getType().name())
		.thenComparing(value -> value.getDisplayInfo() == null ? "" : value.getDisplayInfo())
		.thenComparing(value -> value.getObjectInfo() == null ? "" : value.getObjectInfo());

	private final ObjectMapper mapper;
	private final RoutingStatic routingStatic;
	private final AccountCompiler compiler = new AccountCompiler();
	private final Cache<String, CompiledAccount> accounts = Caffeine.newBuilder().maximumSize(32).build();
	private final Cache<String, ApiModels.RoutePlan> routes = Caffeine.newBuilder().maximumSize(256).build();

	RoutingEngine(ObjectMapper mapper)
	{
		this.mapper = mapper;
		this.routingStatic = new ExactRoutingStaticProvider(
			() -> new CollisionMap(SplitFlagMap.fromResources())).get();
	}

	ApiModels.RoutePlan route(ApiModels.RouteRequest request)
	{
		String accountKey = json(request.account) + json(request.policy);
		String routeKey = accountKey + '|' + packed(request.start.coordinate) + '|' + packed(request.destination.coordinate);
		ApiModels.RoutePlan cached = routes.getIfPresent(routeKey);
		if (cached != null) return cached;

		CompiledAccount account = accounts.get(accountKey,
			ignored -> new CompiledAccount(compiler.compile(request.account, request.policy)));
		int start = packed(request.start.coordinate);
		int target = packed(request.destination.coordinate);
		ExactPathfinder pathfinder = new ExactPathfinder(account.config, routingStatic, account.session,
			start, Set.of(target), null, account.config.getExactHeuristicWeight());
		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();
		if (result == null) throw new IllegalStateException("ExactPathfinder returned no result");

		ApiModels.RoutePlan plan = plan(request, result, account.config);
		routes.put(routeKey, plan);
		return plan;
	}

	private static ApiModels.RoutePlan plan(ApiModels.RouteRequest request, PathfinderResult result,
		PathfinderConfig config)
	{
		ApiModels.RoutePlan plan = new ApiModels.RoutePlan();
		plan.reachable = result.isReached();
		plan.costTicks = result.isReached() ? result.getPathCost() : null;
		plan.start = request.start;
		plan.destination = request.destination;
		plan.metadata = new ApiModels.Metadata();
		plan.metadata.worldDataVersion = "shortest-path-resources";
		plan.metadata.routingEngineVersion = "exact-v1";
		plan.segments = segments(result.getPathSteps(), config);
		return plan;
	}

	private static List<Object> segments(List<PathStep> path, PathfinderConfig config)
	{
		List<Object> segments = new ArrayList<>();
		List<ApiModels.WorldPoint> walking = new ArrayList<>();
		for (int i = 1; i < path.size(); i++)
		{
			PathStep from = path.get(i - 1);
			PathStep to = path.get(i);
			if (!from.isBankVisited() && to.isBankVisited())
			{
				flushWalk(segments, walking);
				ApiModels.BankSegment bank = new ApiModels.BankSegment();
				bank.location = point(to.getPackedPosition());
				bank.costTicks = 0;
				segments.add(bank);
			}
			Transport transport = transportFor(from, to, config);
			if (transport != null)
			{
				flushWalk(segments, walking);
				ApiModels.TravelSegment travel = new ApiModels.TravelSegment();
				String type = transport.getType() == null ? "TRANSPORT" : transport.getType().name();
				travel.kind = transport.getType() != null && transport.getType().isTeleport() ? "teleport" : "transport";
				travel.transportId = type + ':' + from.getPackedPosition() + ':' + to.getPackedPosition();
				travel.name = label(transport, type);
				travel.from = point(from.getPackedPosition());
				travel.to = point(to.getPackedPosition());
				travel.costTicks = Math.max(0, transport.getDuration());
				segments.add(travel);
			}
			else
			{
				if (walking.isEmpty()) walking.add(point(from.getPackedPosition()));
				walking.add(point(to.getPackedPosition()));
			}
		}
		flushWalk(segments, walking);
		if (path.size() == 1)
		{
			ApiModels.WalkSegment walk = new ApiModels.WalkSegment();
			walk.costTicks = 0;
			walk.path = List.of(point(path.get(0).getPackedPosition()));
			segments.add(walk);
		}
		return segments;
	}

	private static void flushWalk(List<Object> segments, List<ApiModels.WorldPoint> walking)
	{
		if (walking.isEmpty()) return;
		ApiModels.WalkSegment walk = new ApiModels.WalkSegment();
		walk.path = List.copyOf(walking);
		walk.costTicks = Math.max(0, walking.size() - 1);
		segments.add(walk);
		walking.clear();
	}

	private static Transport transportFor(PathStep from, PathStep to, PathfinderConfig config)
	{
		List<Transport> candidates = new ArrayList<>();
		for (Transport transport : config.getTransportsPacked(to.isBankVisited())
			.getOrDefault(from.getPackedPosition(), TransportAvailability.EMPTY_TRANSPORTS))
			if (transport.getDestination() == to.getPackedPosition()) candidates.add(transport);
		if (!candidates.isEmpty()) return candidates.stream().min(TRANSPORT_ORDER).orElse(null);

		boolean walking = WorldPointUtil.unpackWorldPlane(from.getPackedPosition())
			== WorldPointUtil.unpackWorldPlane(to.getPackedPosition())
			&& WorldPointUtil.distanceBetween2D(from.getPackedPosition(), to.getPackedPosition()) <= 1;
		if (!walking)
			for (Transport transport : config.getUsableTeleports(to.isBankVisited()))
				if (transport.getDestination() == to.getPackedPosition()) candidates.add(transport);
		return candidates.stream().min(TRANSPORT_ORDER).orElse(null);
	}

	private static String label(Transport transport, String fallback)
	{
		if (transport.getDisplayInfo() != null && !transport.getDisplayInfo().isEmpty()) return transport.getDisplayInfo();
		if (transport.getObjectInfo() != null && !transport.getObjectInfo().isEmpty()) return transport.getObjectInfo();
		return fallback;
	}

	private static int packed(ApiModels.WorldPoint point)
	{
		return WorldPointUtil.packWorldPoint(point.x, point.y, point.plane);
	}

	private static ApiModels.WorldPoint point(int packed)
	{
		return new ApiModels.WorldPoint(WorldPointUtil.unpackWorldX(packed),
			WorldPointUtil.unpackWorldY(packed), WorldPointUtil.unpackWorldPlane(packed));
	}

	private String json(Object value)
	{
		try { return mapper.writeValueAsString(value); }
		catch (JsonProcessingException error) { throw new IllegalArgumentException("invalid route request", error); }
	}

	private static final class CompiledAccount
	{
		private final ServicePathfinderConfig config;
		private final ExactRoutingSession session = new ExactRoutingSession();
		private CompiledAccount(ServicePathfinderConfig config) { this.config = config; }
	}
}
