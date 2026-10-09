package shortestpath.accounts.canonical;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
        if (args.length != 3 || !(args[0].equals("generate") || args[0].equals("verify"))) {
            throw new IllegalArgumentException("usage: generate|verify FIXTURE PRESET_DIRECTORY");
        }
        List<ProfileSpec> profiles = CanonicalProfiles.all();
        validate(profiles);
        String generated = ProfileJsonRenderer.render(profiles);
        Path output = Paths.get(args[1]);
        Path presetDirectory = Paths.get(args[2]);
        if (args[0].equals("generate")) {
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            Files.writeString(output, generated, StandardCharsets.UTF_8);
            Files.createDirectories(presetDirectory);
            for (ProfileSpec profile : profiles) {
                Files.writeString(presetDirectory.resolve(profile.name() + ".json"),
                    ProfileJsonRenderer.preset(profile), StandardCharsets.UTF_8);
            }
            return;
        }
        verifyAccountProfiles(output, generated);
        for (ProfileSpec profile : profiles) {
            verify(presetDirectory.resolve(profile.name() + ".json"), ProfileJsonRenderer.preset(profile));
        }
    }

    static void verifyAccountProfiles(Path output) throws IOException {
        verifyAccountProfiles(output, ProfileJsonRenderer.render(CanonicalProfiles.all()));
    }

    private static void verifyAccountProfiles(Path output, String generated) throws IOException {
        verify(output, generated);
    }

    private static void verify(Path output, String generated) throws IOException {
        String committedText = Files.readString(output, StandardCharsets.UTF_8);
        JsonElement committed = new JsonParser().parse(committedText);
        JsonElement actual = new JsonParser().parse(generated);
        if (!committed.equals(actual)) {
            throw new IllegalStateException("generated account file differs at " + output + ": "
                + difference(committed, actual, "$"));
        }
        // Consumers diff and hash these files, so formatting is part of the contract too.
        if (!committedText.equals(generated)) {
            throw new IllegalStateException("generated account file is formatted differently at " + output
                + "; run generateAccountProfiles");
        }
    }

    private static String difference(JsonElement committed, JsonElement generated, String path) {
        if (committed == null || generated == null || committed.getClass() != generated.getClass()) {
            return path + " committed=" + committed + ", generated=" + generated;
        }
        if (committed.isJsonObject()) {
            JsonObject left = committed.getAsJsonObject();
            JsonObject right = generated.getAsJsonObject();
            Set<String> names = new HashSet<>(left.keySet());
            names.addAll(right.keySet());
            for (String name : names) {
                if (!left.has(name) || !right.has(name)) {
                    return path + "." + name + " committed=" + left.get(name) + ", generated=" + right.get(name);
                }
                String difference = difference(left.get(name), right.get(name), path + "." + name);
                if (difference != null) return difference;
            }
            return null;
        }
        if (committed.isJsonArray()) {
            JsonArray left = committed.getAsJsonArray();
            JsonArray right = generated.getAsJsonArray();
            if (left.size() != right.size()) return path + " committed size=" + left.size() + ", generated size=" + right.size();
            for (int i = 0; i < left.size(); i++) {
                String difference = difference(left.get(i), right.get(i), path + "[" + i + "]");
                if (difference != null) return difference;
            }
            return null;
        }
        return committed.equals(generated) ? null : path + " committed=" + committed + ", generated=" + generated;
    }

    private static void validate(List<ProfileSpec> profiles) {
        if (profiles.size() != 4) throw new IllegalStateException("expected four canonical profiles");
        for (ProfileSpec profile : profiles) {
            Set<String> names = new HashSet<>();
            for (var quest : profile.completedQuests()) {
                if (quest.getName() == null || quest.getName().isEmpty() || !names.add(quest.getName())) {
                    throw new IllegalStateException("duplicate/empty rendered quest name: " + quest);
                }
            }
        }
        if (!CanonicalProfiles.early().plantedSpiritTrees().isEmpty()
            || !CanonicalProfiles.mid().plantedSpiritTrees().contains(PlantedSpiritTree.FARMING_GUILD)
            || !CanonicalProfiles.end().plantedSpiritTrees().contains(PlantedSpiritTree.PORT_SARIM)
            || CanonicalProfiles.maxed().plantedSpiritTrees().size() != 5) {
            throw new IllegalStateException("canonical planted spirit-tree progression changed");
        }
    }
}
