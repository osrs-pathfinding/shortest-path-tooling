package shortestpath.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.Set;

/** Validates route requests against {@code route-api-v1}'s {@code routeRequest}, copied from {@code corpus/schemas}. */
final class SchemaValidator {
    private final JsonSchema request;

    SchemaValidator() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
            builder -> builder.schemaMappers(mappers -> mappers.mapPrefix(
                "https://osrs.travel/schemas/", "classpath:/schemas/")));
        request = factory.getSchema(
            SchemaLocation.of("https://osrs.travel/schemas/route-api-v1.schema.json#/$defs/routeRequest"));
    }

    void validate(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("request body must be a JSON object");
        }
        Set<ValidationMessage> errors = request.validate(body);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("request is invalid: " + errors.iterator().next().getMessage());
        }
    }
}
