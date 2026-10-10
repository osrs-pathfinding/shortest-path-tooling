package shortestpath.accounts.canonical;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MainTest {
    @Test public void staleFixtureFailsWithoutWritingCandidate() throws Exception {
        Path directory = Files.createTempDirectory("profile-verification");
        Path fixture = directory.resolve("account-profiles-v1.json");
        String current = ProfileJsonRenderer.render(CanonicalAccounts.NAMES.stream().map(CanonicalAccounts::build)
            .collect(java.util.stream.Collectors.toList()));
        Files.writeString(fixture, current.replace("\"formatVersion\": 1", "\"formatVersion\": 2"), StandardCharsets.UTF_8);
        try {
            Main.verifyAccountProfiles(fixture);
        } catch (IllegalStateException e) {
            assertFalse(Files.exists(directory.resolve("account-profiles-v1.generated.json")));
            assertEquals(2, new com.google.gson.JsonParser().parse(Files.readString(fixture)).getAsJsonObject()
                .get("formatVersion").getAsInt());
            return;
        }
        throw new AssertionError("stale fixture was accepted");
    }
}
