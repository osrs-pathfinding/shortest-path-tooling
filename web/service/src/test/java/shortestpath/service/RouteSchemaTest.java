package shortestpath.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteSchemaTest {
    private static final SchemaValidator VALIDATOR = new SchemaValidator();

    @Test
    void schemaAcceptsCompletePoliciesAndRejectsUnknownOptions() {
        ObjectNode request = Requests.MAPPER.valueToTree(Requests.route("mid", 3222, 3218, 3210, 3424));
        ObjectNode policy = (ObjectNode) request.get("policy");
        // Optional policy fields are omitted by clients that predate them.
        policy.remove(List.of("teleportItems", "currencyThreshold", "transportThresholds", "declaredUnlocks"));
        VALIDATOR.validate(request);

        policy.put("banking", "avoid").put("resources", "permanent-only").put("teleportItems", "any")
            .put("currencyThreshold", 0);
        policy.putArray("avoidedTransportTypes").add("FAIRY_RING");
        policy.putObject("transportThresholds").put("TELEPORTATION_BOX", 10000);
        policy.putArray("declaredUnlocks").add("DRAGONTOOTH_PASSAGE");
        VALIDATOR.validate(request);

        assertRejected(request, "avoidedTransportTypes", Requests.MAPPER.createArrayNode().add("QUETZAL_WHISTLE"));
        assertRejected(request, "transportThresholds", Requests.MAPPER.createObjectNode().put("BOAT", 10001));
        assertRejected(request, "transportThresholds", Requests.MAPPER.createObjectNode().put("SEASONAL_TRANSPORTS", 1));
        assertRejected(request, "declaredUnlocks", Requests.MAPPER.createArrayNode().add("EVERYTHING"));
        assertRejected(request, "currencyThreshold", Requests.MAPPER.getNodeFactory().numberNode(-1));
    }

    private static void assertRejected(ObjectNode request, String policyField, JsonNode value) {
        ObjectNode copy = request.deepCopy();
        ((ObjectNode) copy.get("policy")).set(policyField, value);
        assertThrows(IllegalArgumentException.class, () -> VALIDATOR.validate(copy));
    }
}
