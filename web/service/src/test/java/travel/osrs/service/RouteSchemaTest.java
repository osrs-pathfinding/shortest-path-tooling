package travel.osrs.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import shortestpath.routeapi.RouteApi;

class RouteSchemaTest
{
	private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();
	private static RouteApi.AccountBuild account;

	@BeforeAll
	static void loadAccount() throws Exception
	{
		try (InputStream profile = RouteSchemaTest.class.getResourceAsStream("/profiles/mid.json"))
		{
			account = MAPPER.readValue(profile, RouteApi.AccountBuild.class);
		}
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
		RouteApi.RouteRequest request = new RouteApi.RouteRequest();
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

	private static RouteApi.Location location(int x, int y)
	{
		RouteApi.Location location = new RouteApi.Location();
		location.coordinate = new RouteApi.WorldPoint(x, y, 0);
		return location;
	}

	private static RouteApi.RoutePolicy policy()
	{
		RouteApi.RoutePolicy policy = new RouteApi.RoutePolicy();
		policy.avoidWilderness = true;
		policy.banking = "allow";
		policy.resources = "fastest";
		return policy;
	}
}
