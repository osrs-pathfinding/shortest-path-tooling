package shortestpath.corpus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** The committed corpus/ data: what tools/validate.js checked in the old corpus repository. */
public class CorpusDataTest {
    private static final List<String> PROFILES = List.of("early", "mid", "end", "maxed");
    private static final Set<String> TIERS = Set.of("smoke", "standard", "full");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static Path corpus() {
        return Path.of(System.getProperty("corpus.dir", "../corpus"));
    }

    private static JsonNode read(String file) throws IOException {
        return JSON.readTree(corpus().resolve(file).toFile());
    }

    @Test
    public void manifestVersions() throws IOException {
        JsonNode manifest = read("manifest.json");
        assertEquals(2, manifest.get("formatVersion").asInt());
        assertEquals(1, manifest.get("accountProfilesVersion").asInt());
        assertEquals(1, manifest.get("routesVersion").asInt());
        assertEquals(1, manifest.get("accountSchemaVersion").asInt());
        assertEquals(manifest.get("accountProfilesVersion").asInt(),
            read("accounts/account-profiles-v1.json").get("formatVersion").asInt());
    }

    @Test
    public void presetsMatchSchemaAndFixture() throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
            builder -> builder.schemaMappers(mappers -> mappers.mapPrefix("https://osrs.travel/schemas/",
                corpus().resolve("schemas").toUri().toString())));
        for (String schema : List.of("account-build-v1", "route-policy-v1", "place-v1", "route-api-v1")) {
            factory.getSchema(SchemaLocation.of("https://osrs.travel/schemas/" + schema + ".schema.json"))
                .initializeValidators();
        }
        JsonSchema account = factory.getSchema(
            SchemaLocation.of("https://osrs.travel/schemas/account-build-v1.schema.json"));
        JsonNode fixture = read("accounts/account-profiles-v1.json");
        for (String name : PROFILES) {
            JsonNode preset = read("profiles/" + name + ".json");
            Set<ValidationMessage> errors = account.validate(preset);
            assertTrue(name + " preset: " + errors, errors.isEmpty());
            assertEquals(name, preset.get("id").asText());
            assertEquals(1, preset.get("schemaVersion").asInt());
            assertEquals(fixture.get("benchmarkNowMinutes"), preset.get("benchmarkNowMinutes"));

            ObjectNode semantic = preset.deepCopy();
            for (String field : List.of("schemaVersion", "id", "name", "benchmarkNowMinutes", "routingVariables")) {
                semantic.remove(field);
            }
            semantic.set("varbits", preset.get("routingVariables").get("varbits"));
            semantic.set("varplayers", preset.get("routingVariables").get("varplayers"));
            assertEquals(name + " preset differs from the benchmark fixture",
                fixture.get("profiles").get(name), semantic);
        }
        assertEquals(Set.copyOf(PROFILES), fieldNames(fixture.get("profiles")));
    }

    @Test
    public void routes() throws IOException {
        Set<String> ids = new HashSet<>();
        for (JsonNode route : read("corpus/routes-v1.json")) {
            String id = route.path("id").asText();
            assertTrue("route IDs must be unique non-empty strings: " + id, !id.isEmpty() && ids.add(id));
            for (String field : List.of("name", "startName", "targetName", "startSource", "targetSource")) {
                assertTrue(id + ": " + field + " must be a non-empty string",
                    route.path(field).isTextual() && !route.get(field).asText().isEmpty());
            }
            for (String field : List.of("start", "target")) {
                JsonNode point = route.path(field);
                assertTrue(id + ": " + field + " must be [x, y, plane]", point.isArray() && point.size() == 3
                    && point.get(0).isInt() && point.get(1).isInt() && point.get(2).isInt());
            }
            assertTrue(id + ": allowTransports", route.path("allowTransports").isBoolean());
            Set<String> tiers = new HashSet<>();
            route.path("tiers").forEach(tier -> tiers.add(tier.asText()));
            assertTrue(id + ": invalid tiers " + tiers, tiers.contains("full") && TIERS.containsAll(tiers));
            route.path("negativeProfiles").forEach(profile ->
                assertTrue(id + ": invalid negative profile " + profile, PROFILES.contains(profile.asText())));
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
