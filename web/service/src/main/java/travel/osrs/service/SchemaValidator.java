package travel.osrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.Set;

public final class SchemaValidator
{
	private final JsonSchema request;

	public SchemaValidator()
	{
		JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
			builder -> builder.schemaMappers(mappers -> mappers.mapPrefix(
				"https://osrs.travel/schemas/", "classpath:/schemas/")));
		request = factory.getSchema(resource("/schemas/route-api-v1.schema.json"));
	}

	public void validate(JsonNode request)
	{
		if (request == null || !request.isObject())
		{
			throw new IllegalArgumentException("request body must be a JSON object");
		}
		validate(this.request, request, "request");
	}

	private static void validate(JsonSchema schema, JsonNode value, String field)
	{
		if (value == null)
		{
			throw new IllegalArgumentException("missing " + field);
		}
		Set<ValidationMessage> errors = schema.validate(value);
		if (!errors.isEmpty())
		{
			throw new IllegalArgumentException(field + " is invalid: " + errors.iterator().next().getMessage());
		}
	}

	private static InputStream resource(String name)
	{
		InputStream stream = SchemaValidator.class.getResourceAsStream(name);
		if (stream == null)
		{
			throw new IllegalStateException("missing service resource " + name);
		}
		return stream;
	}
}
