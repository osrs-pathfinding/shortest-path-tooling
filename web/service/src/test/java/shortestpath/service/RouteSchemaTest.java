package shortestpath.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class RouteSchemaTest {
    private static final SchemaValidator VALIDATOR = new SchemaValidator();

    @Test
    void schemaAcceptsPlannerRequestsAndRejectsMalformedOnes() {
        ObjectNode request = Requests.MAPPER.valueToTree(Requests.route("mid", 3222, 3218, 3210, 3424));
        ((ObjectNode) request.get("settings")).put("useFairyRings", false).put("costBoats", 10)
            .put("useTeleportationItems", "ALL");
        VALIDATOR.validate(request);

        assertRejected(request, "settings", Requests.MAPPER.createObjectNode().set("useFairyRings",
            Requests.MAPPER.createArrayNode()));
        assertRejected(request, "start", Requests.MAPPER.createObjectNode());
        ObjectNode account = (ObjectNode) request.get("account").deepCopy();
        ((ObjectNode) account.get("levels")).put("ATTACK", 100);
        assertRejected(request, "account", account);
        ObjectNode diary = (ObjectNode) request.get("account").deepCopy();
        ((ObjectNode) diary.get("diaries")).put("ARDOUGNE", "Hard");
        assertRejected(request, "account", diary);
    }

    private static void assertRejected(ObjectNode request, String field, JsonNode value) {
        ObjectNode copy = request.deepCopy();
        copy.set(field, value);
        assertThrows(IllegalArgumentException.class, () -> VALIDATOR.validate(copy));
    }
}
