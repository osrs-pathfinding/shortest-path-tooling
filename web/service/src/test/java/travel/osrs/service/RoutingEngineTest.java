package travel.osrs.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.InputStream;
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
	}

	private static ApiModels.Location location(int x, int y, int plane)
	{
		ApiModels.Location location = new ApiModels.Location();
		location.coordinate = new ApiModels.WorldPoint(x, y, plane);
		return location;
	}
}
