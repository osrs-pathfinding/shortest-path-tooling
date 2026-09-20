package shortestpath.corpus.profiles;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Main {
    private Main() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2 || !(args[0].equals("generate") || args[0].equals("verify"))) {
            throw new IllegalArgumentException("usage: generate|verify OUTPUT");
        }
        List<ProfileSpec> profiles = CanonicalProfiles.all();
        validate(profiles);
        String generated = ProfileJsonRenderer.render(profiles);
        Path output = Paths.get(args[1]);
        if (args[0].equals("generate")) {
            Files.createDirectories(output.getParent());
            Files.writeString(output, generated, StandardCharsets.UTF_8);
            return;
        }
        JsonElement expected = new JsonParser().parse(Files.readString(output, StandardCharsets.UTF_8));
        JsonElement actual = new JsonParser().parse(generated);
        if (!expected.equals(actual)) {
            Path candidate = output.getParent().resolve("account-profiles-v1.generated.json");
            Files.writeString(candidate, generated, StandardCharsets.UTF_8);
            throw new IllegalStateException("generated account fixture differs; candidate written to " + candidate);
        }
    }

    private static void validate(List<ProfileSpec> profiles) {
        if (profiles.size() != 4) throw new IllegalStateException("expected four canonical profiles");
        for (ProfileSpec profile : profiles) {
            Set<String> names = new HashSet<>();
            for (var quest : profile.completedQuests) {
                if (quest.getName() == null || quest.getName().isEmpty() || !names.add(quest.getName())) {
                    throw new IllegalStateException("duplicate/empty rendered quest name: " + quest);
                }
            }
        }
        if (!CanonicalProfiles.early().plantedSpiritTrees.isEmpty()
            || !CanonicalProfiles.mid().plantedSpiritTrees.contains(PlantedSpiritTree.FARMING_GUILD)
            || !CanonicalProfiles.end().plantedSpiritTrees.contains(PlantedSpiritTree.PORT_SARIM)
            || CanonicalProfiles.maxed().plantedSpiritTrees.size() != 5) {
            throw new IllegalStateException("canonical planted spirit-tree progression changed");
        }
    }
}
