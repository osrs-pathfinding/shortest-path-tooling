package travel.osrs.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public final class ApiModels
{
	private ApiModels()
	{
	}

	public static final class WorldPoint
	{
		public int x;
		public int y;
		public int plane;

		public WorldPoint()
		{
		}

		public WorldPoint(int x, int y, int plane)
		{
			this.x = x;
			this.y = y;
			this.plane = plane;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static final class Location
	{
		public String placeId;
		public String name;
		public WorldPoint coordinate;
	}

	public static final class AccountBuild
	{
		public int schemaVersion;
		public String id;
		public String name;
		public long benchmarkNowMinutes;
		public Map<String, Integer> levels = new LinkedHashMap<>();
		public List<String> completedQuests = new ArrayList<>();
		public Map<String, String> diaries = new LinkedHashMap<>();
		public Map<String, Integer> inventory = new LinkedHashMap<>();
		public Map<String, Integer> equipment = new LinkedHashMap<>();
		public Map<String, Integer> runePouch = new LinkedHashMap<>();
		public Map<String, Integer> bank = new LinkedHashMap<>();
		public boolean fairyRingsUnlocked;
		public List<String> plantedSpiritTrees = new ArrayList<>();
		public Poh poh;
		public RuntimeState runtime;
		public RoutingVariables routingVariables;
	}

	public static final class Poh
	{
		public String location;
		public String jewelleryBox;
		public Portals portals;
		public boolean fairyRing;
		public boolean spiritTree;
		public boolean obelisk;
		public boolean mountedGlory;
		public boolean mountedXerics;
		public boolean mountedDigsite;
		public boolean mountedMythical;
	}

	public static final class Portals
	{
		public String mode;
		public List<String> destinations = new ArrayList<>();
	}

	public static final class RuntimeState
	{
		public boolean arriveInsidePoh;
		public String spellbook;
		public MinigameTeleport minigameTeleport;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static final class MinigameTeleport
	{
		public String state;
		public Long minutes;
	}

	public static final class RoutingVariables
	{
		public Map<Integer, Integer> varbits = new LinkedHashMap<>();
		public Map<Integer, Integer> varplayers = new LinkedHashMap<>();
	}

	public static final class RoutePolicy
	{
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

	public static final class RouteRequest
	{
		public AccountBuild account;
		public Location start;
		public Location destination;
		public RoutePolicy policy;
	}

	public static final class RoutePlan
	{
		public String apiVersion = "v1";
		public boolean reachable;
		public Integer costTicks;
		public Location start;
		public Location destination;
		public List<Object> segments = new ArrayList<>();
		public Metadata metadata;
	}

	public static final class WalkSegment
	{
		public String kind = "walk";
		public int costTicks;
		public List<WorldPoint> path;
	}

	public static final class TravelSegment
	{
		public String kind;
		public String transportId;
		public String name;
		public WorldPoint from;
		public WorldPoint to;
		public int costTicks;
		public List<Capability> requirements = new ArrayList<>();
	}

	public static final class BankSegment
	{
		public String kind = "bank";
		public WorldPoint location;
		public int costTicks;
	}

	public static final class Capability
	{
		public String kind;
		public String name;
	}

	public static final class Metadata
	{
		public String worldDataVersion;
		public int accountSchemaVersion = 1;
		public String routingEngineVersion;
	}

	public static final class ErrorResponse
	{
		public final String error;
		public final String requestId;

		public ErrorResponse(String error, String requestId)
		{
			this.error = error;
			this.requestId = requestId;
		}
	}

	public static final class ItemOption
	{
		public final String key;
		public final String name;

		public ItemOption(String key, String name)
		{
			this.key = key;
			this.name = name;
		}
	}
}
