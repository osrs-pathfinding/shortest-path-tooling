package travel.osrs.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.InputStream;
import java.util.List;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.jupiter.api.Test;

class RoutingEngineTest
{
	@Test
	void canonicalProfileRunsThroughExactPathfinder() throws Exception
	{
		ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
		ApiModels.RouteRequest request = new ApiModels.RouteRequest();
		try (InputStream profile = getClass().getResourceAsStream("/profiles/mid.json"))
		{
			request.account = mapper.readValue(profile, ApiModels.AccountBuild.class);
		}
		request.policy = new ApiModels.RoutePolicy();
		request.policy.avoidWilderness = true;
		request.policy.banking = "allow";
		request.policy.resources = "fastest";
		request.start = location(3222, 3218, 0);
		request.destination = location(3210, 3424, 0);
		new SchemaValidator().validate(mapper.valueToTree(request));

		ApiModels.RoutePlan plan = new RoutingEngine(mapper).route(request);

		assertTrue(plan.reachable);
		assertTrue(plan.costTicks > 0);
		assertEquals("exact-v1", plan.metadata.routingEngineVersion);
		assertTrue(!plan.segments.isEmpty());
		assertTrue(plan.segments.stream().filter(ApiModels.TravelSegment.class::isInstance)
			.map(ApiModels.TravelSegment.class::cast).anyMatch(segment -> !segment.requirements.isEmpty()),
			"semantic travel steps should expose their player-facing requirements");
	}

	@Test
	void routePolicyOptionsReachTheExactPathfinder() throws Exception
	{
		ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
		ApiModels.RouteRequest request = new ApiModels.RouteRequest();
		try (InputStream profile = getClass().getResourceAsStream("/profiles/maxed.json"))
		{
			request.account = mapper.readValue(profile, ApiModels.AccountBuild.class);
		}
		request.policy = new ApiModels.RoutePolicy();
		request.policy.avoidWilderness = true;
		request.policy.banking = "never";
		request.policy.resources = "fastest";
		request.start = location(3222, 3218, 0);
		request.destination = location(2757, 3477, 0);
		RoutingEngine engine = new RoutingEngine(mapper);

		ApiModels.RoutePlan fastest = engine.route(request);
		request.policy.avoidedTransportTypes = List.of("TELEPORTATION_ITEM", "TELEPORTATION_SPELL",
			"TELEPORTATION_SPELL_HOME", "TELEPORTATION_MINIGAME", "TELEPORTATION_PORTAL", "FAIRY_RING", "SPIRIT_TREE");
		ApiModels.RoutePlan restricted = engine.route(request);

		assertTrue(fastest.reachable && restricted.reachable);
		assertTrue(restricted.costTicks > fastest.costTicks, "avoiding teleports should make the route longer");
		for (ApiModels.RoutePlan plan : List.of(fastest, restricted))
			assertFalse(plan.segments.stream().anyMatch(ApiModels.BankSegment.class::isInstance),
				"banking \"never\" must not visit a bank");
		assertFalse(restricted.segments.stream().filter(ApiModels.TravelSegment.class::isInstance)
			.map(segment -> ((ApiModels.TravelSegment) segment).transportId.split(":")[0])
			.anyMatch(request.policy.avoidedTransportTypes::contains));
	}

	@Test
	void semanticAccountStateOverridesCompatibilityVariables() throws Exception
	{
		ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
		ApiModels.AccountBuild account;
		try (InputStream profile = getClass().getResourceAsStream("/profiles/mid.json"))
		{
			account = mapper.readValue(profile, ApiModels.AccountBuild.class);
		}
		account.diaries.put("Ardougne", "Easy");
		account.runtime.spellbook = "Ancient";
		account.runtime.minigameTeleport.state = "usedAt";
		account.runtime.minigameTeleport.minutes = 123L;
		account.completedQuests.remove("The Grand Tree");

		assertEquals(1, AccountCompiler.semanticVarbits(account).get(VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE));
		assertEquals(0, AccountCompiler.semanticVarbits(account).get(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE));
		assertEquals(1, AccountCompiler.semanticVarbits(account).get(VarbitID.SPELLBOOK));
		assertEquals(0, AccountCompiler.semanticVarplayers(account).get(VarPlayerID.GRANDTREE));
		assertEquals(123, AccountCompiler.semanticVarplayers(account).get(VarPlayerID.SLUG2_REGIONUID));
	}

	@Test
	void itemCatalogResolvesIdsAndSearchesNames()
	{
		assertEquals("995", ItemCatalog.find(null, "995").get(0).key);
		assertTrue(ItemCatalog.find("coins", null).stream().anyMatch(item -> "995".equals(item.key)));
		assertTrue(ItemCatalog.find("teleport to house", null).stream()
			.anyMatch(item -> "8013".equals(item.key) && "Teleport to house".equals(item.name)));
		assertFalse(ItemCatalog.find("teleporttohouse", null).stream().anyMatch(item -> "8013".equals(item.key)));
		assertTrue(ItemCatalog.find("bronze dagger", null).isEmpty());
	}

	@Test
	void itemCatalogIncludesItemsCheckedOutsideTransportData()
	{
		assertEquals("Dramen staff", ItemCatalog.find(null, "772").get(0).name);
		assertTrue(ItemCatalog.find("lunar staff", null).stream().anyMatch(item -> "9084".equals(item.key)));
		assertTrue(ItemCatalog.find("rune pouch", null).stream().anyMatch(item -> "12791".equals(item.key)));
	}

	private static ApiModels.Location location(int x, int y, int plane)
	{
		ApiModels.Location location = new ApiModels.Location();
		location.coordinate = new ApiModels.WorldPoint(x, y, plane);
		return location;
	}
}
