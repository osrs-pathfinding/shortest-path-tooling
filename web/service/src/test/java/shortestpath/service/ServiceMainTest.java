package shortestpath.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

class ServiceMainTest {
    private static final RoutingEngine ENGINE = new RoutingEngine(Requests.MAPPER);

    @Test
    void servesPresetsCatalogAndRoutes() {
        JavalinTest.test(ServiceMain.app(Requests.MAPPER, ENGINE), (server, client) -> {
            JsonNode presets = Requests.MAPPER.readTree(client.get("/v1/presets").body().string());
            assertEquals("mid", presets.get(1).get("id").asText());
            JsonNode catalog = Requests.MAPPER.readTree(client.get("/v1/catalog").body().string());
            assertTrue(catalog.get("settings").size() > 50);

            ObjectNode request = Requests.MAPPER.valueToTree(Requests.route("mid", 3222, 3218, 3210, 3424));
            assertEquals(200, client.post("/v1/route", request.toString()).code());

            ObjectNode unknownQuest = request.deepCopy();
            ((ArrayNode) unknownQuest.get("account").get("completedQuests")).add("NOT_A_QUEST");
            var response = client.post("/v1/route", unknownQuest.toString());
            assertEquals(400, response.code());
            assertTrue(response.body().string().contains("NOT_A_QUEST at account.completedQuests["));

            ObjectNode unknownSetting = request.deepCopy();
            ((ObjectNode) unknownSetting.get("settings")).put("drawMap", true);
            response = client.post("/v1/route", unknownSetting.toString());
            assertEquals(400, response.code());
            assertTrue(response.body().string().contains("unknown setting: drawMap"));
        });
    }
}
