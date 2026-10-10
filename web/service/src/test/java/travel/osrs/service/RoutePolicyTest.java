package travel.osrs.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import shortestpath.TeleportationItem;

class RoutePolicyTest
{
	private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();
	private static ApiModels.AccountBuild account;

	@BeforeAll
	static void loadAccount() throws Exception
	{
		try (InputStream profile = RoutePolicyTest.class.getResourceAsStream("/profiles/mid.json"))
		{
			account = MAPPER.readValue(profile, ApiModels.AccountBuild.class);
		}
	}

	@Test
	void minimalPoliciesKeepTheirPreviousBehaviour()
	{
		ServiceRoutingConfig config = config(policy());

		assertEquals(TeleportationItem.INVENTORY_AND_BANK, config.useTeleportationItems());
		assertTrue(config.includeBankPath());
		assertEquals(Integer.MAX_VALUE, config.currencyThreshold());
		assertEquals(0, config.costBoats());
		assertEquals(0, config.costBankVisit());
		assertEquals(0, config.costConsumableTeleportationItems());
		assertFalse(config.unlockCanoeAxe());
		assertFalse(config.respawnPrifddinas());
	}

	@Test
	void teleportItemModeFollowsBankingResourcesAndItemSource()
	{
		ApiModels.RoutePolicy policy = policy();
		policy.banking = "never";
		assertEquals(TeleportationItem.INVENTORY, config(policy).useTeleportationItems());
		assertFalse(config(policy).includeBankPath());

		policy.resources = "permanent-only";
		assertEquals(TeleportationItem.INVENTORY_NON_CONSUMABLE, config(policy).useTeleportationItems());

		policy.banking = "avoid";
		assertEquals(TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE, config(policy).useTeleportationItems());
		assertEquals(ServiceRoutingConfig.AVOID_THRESHOLD, config(policy).costBankVisit());

		policy.teleportItems = "any";
		assertEquals(TeleportationItem.ALL_NON_CONSUMABLE, config(policy).useTeleportationItems());
		policy.resources = "preserve-consumables";
		assertEquals(TeleportationItem.ALL, config(policy).useTeleportationItems());
		assertEquals(ServiceRoutingConfig.AVOID_THRESHOLD, config(policy).costConsumableTeleportationItems());

		policy.avoidedTransportTypes = List.of("TELEPORTATION_ITEM");
		assertEquals(TeleportationItem.NONE, config(policy).useTeleportationItems());
	}

	@Test
	void thresholdsUnlocksAndCurrencyReachThePluginConfig()
	{
		ApiModels.RoutePolicy policy = policy();
		policy.transportThresholds.put("FAIRY_RING", 12);
		policy.transportThresholds.put("TELEPORTATION_BOX", 3);
		policy.currencyThreshold = 5000;
		policy.declaredUnlocks = List.of("CANOE_AXE", "PRIFDDINAS_RESPAWN");
		policy.avoidedTransportTypes = List.of("QUETZAL", "CANOE");

		ServiceRoutingConfig config = config(policy);
		assertEquals(12, config.costFairyRings());
		assertEquals(3, config.costTeleportationBoxes());
		assertEquals(0, config.costSpiritTrees());
		assertEquals(5000, config.currencyThreshold());
		assertTrue(config.unlockCanoeAxe());
		assertTrue(config.respawnPrifddinas());
		assertFalse(config.unlockXericsHonour());
		assertFalse(config.useQuetzals());
		assertFalse(config.useCanoes());
		assertTrue(config.useBoats());
	}

	@Test
	void schemaAcceptsCompletePoliciesAndRejectsUnknownOptions()
	{
		SchemaValidator validator = new SchemaValidator();
		ObjectNode request = request();
		ObjectNode policy = (ObjectNode) request.get("policy");
		policy.put("banking", "avoid").put("resources", "permanent-only").put("teleportItems", "any")
			.put("currencyThreshold", 0);
		policy.putArray("avoidedTransportTypes").add("FAIRY_RING");
		policy.putObject("transportThresholds").put("TELEPORTATION_BOX", 10000);
		policy.putArray("declaredUnlocks").add("DRAGONTOOTH_PASSAGE");
		validator.validate(request);

		assertThrows(IllegalArgumentException.class, () -> validator.validate(with(request, "avoidedTransportTypes",
			MAPPER.createArrayNode().add("QUETZAL_WHISTLE"))));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(with(request, "transportThresholds",
			MAPPER.createObjectNode().put("BOAT", 10001))));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(with(request, "transportThresholds",
			MAPPER.createObjectNode().put("SEASONAL_TRANSPORTS", 1))));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(with(request, "declaredUnlocks",
			MAPPER.createArrayNode().add("EVERYTHING"))));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(with(request, "currencyThreshold",
			MAPPER.getNodeFactory().numberNode(-1))));
	}

	private static ObjectNode with(ObjectNode request, String field, com.fasterxml.jackson.databind.JsonNode value)
	{
		ObjectNode copy = request.deepCopy();
		((ObjectNode) copy.get("policy")).set(field, value);
		return copy;
	}

	private static ObjectNode request()
	{
		ApiModels.RouteRequest request = new ApiModels.RouteRequest();
		request.account = account;
		request.policy = policy();
		request.start = location(3222, 3218);
		request.destination = location(3210, 3424);
		ObjectNode node = MAPPER.valueToTree(request);
		// Optional policy fields are omitted by clients that predate them.
		ObjectNode policy = (ObjectNode) node.get("policy");
		policy.remove(List.of("teleportItems", "currencyThreshold", "transportThresholds", "declaredUnlocks"));
		return node;
	}

	private static ApiModels.Location location(int x, int y)
	{
		ApiModels.Location location = new ApiModels.Location();
		location.coordinate = new ApiModels.WorldPoint(x, y, 0);
		return location;
	}

	private static ApiModels.RoutePolicy policy()
	{
		ApiModels.RoutePolicy policy = new ApiModels.RoutePolicy();
		policy.avoidWilderness = true;
		policy.banking = "allow";
		policy.resources = "fastest";
		return policy;
	}

	private static ServiceRoutingConfig config(ApiModels.RoutePolicy policy)
	{
		return new ServiceRoutingConfig(account, policy);
	}
}
