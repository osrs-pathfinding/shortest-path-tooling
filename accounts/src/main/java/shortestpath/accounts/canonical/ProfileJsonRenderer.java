package shortestpath.accounts.canonical;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import shortestpath.accounts.AccountBuild;

/**
 * Renders canonical account builds as the corpus files: {@code account-profiles-v1.json} (every
 * profile, variables at the top level) and {@code profiles/<name>.json} (one account-build-v1
 * document each). Field order and formatting are part of the contract.
 */
final class ProfileJsonRenderer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private ProfileJsonRenderer() { }

    static String render(List<AccountBuild> builds) {
        JsonObject root = new JsonObject();
        root.addProperty("benchmarkNowMinutes", RoutingVariables.BENCHMARK_NOW_MINUTES);
        root.addProperty("formatVersion", 1);
        JsonObject profiles = new JsonObject();
        for (AccountBuild build : builds) profiles.add(build.id, profile(build));
        root.add("profiles", profiles);
        return GSON.toJson(root) + "\n";
    }

    static String preset(AccountBuild build) {
        JsonObject result = profile(build);
        JsonObject routingVariables = new JsonObject();
        routingVariables.add("varbits", result.remove("varbits"));
        routingVariables.add("varplayers", result.remove("varplayers"));
        result.addProperty("schemaVersion", build.schemaVersion);
        result.addProperty("id", build.id);
        result.addProperty("name", build.name);
        result.addProperty("benchmarkNowMinutes", build.benchmarkNowMinutes);
        result.add("routingVariables", routingVariables);
        return GSON.toJson(result) + "\n";
    }

    static JsonObject profile(AccountBuild build) {
        JsonObject result = new JsonObject();
        result.add("bank", itemMap(build.bank));
        result.add("completedQuests", strings(build.completedQuests));
        result.add("diaries", stringMap(build.diaries));
        result.add("equipment", itemMap(build.equipment));
        result.addProperty("fairyRingsUnlocked", build.fairyRingsUnlocked);
        result.add("inventory", itemMap(build.inventory));
        result.add("levels", integerMap(build.levels));
        result.add("plantedSpiritTrees", strings(build.plantedSpiritTrees));
        result.add("poh", poh(build.poh));
        result.add("runePouch", itemMap(build.runePouch));
        result.add("runtime", runtime(build.runtime));
        result.add("varbits", variables(build.routingVariables.varbits));
        result.add("varplayers", variables(build.routingVariables.varplayers));
        return result;
    }

    private static JsonObject poh(AccountBuild.Poh poh) {
        JsonObject result = new JsonObject();
        result.addProperty("fairyRing", poh.fairyRing);
        result.addProperty("jewelleryBox", poh.jewelleryBox);
        result.addProperty("location", poh.location);
        result.addProperty("mountedDigsite", poh.mountedDigsite);
        result.addProperty("mountedGlory", poh.mountedGlory);
        result.addProperty("mountedMythical", poh.mountedMythical);
        result.addProperty("mountedXerics", poh.mountedXerics);
        result.addProperty("obelisk", poh.obelisk);
        JsonObject portals = new JsonObject();
        portals.add("destinations", strings(poh.portals.destinations));
        portals.addProperty("mode", poh.portals.mode);
        result.add("portals", portals);
        result.addProperty("spiritTree", poh.spiritTree);
        return result;
    }

    private static JsonObject runtime(AccountBuild.RuntimeState runtime) {
        JsonObject result = new JsonObject();
        result.addProperty("arriveInsidePoh", runtime.arriveInsidePoh);
        JsonObject cooldown = new JsonObject();
        cooldown.addProperty("state", runtime.minigameTeleport.state);
        if (runtime.minigameTeleport.minutes != null) cooldown.addProperty("minutes", runtime.minigameTeleport.minutes);
        result.add("minigameTeleport", cooldown);
        result.addProperty("spellbook", runtime.spellbook);
        return result;
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }

    private static JsonObject stringMap(Map<String, String> values) {
        JsonObject result = new JsonObject();
        values.forEach(result::addProperty);
        return result;
    }

    private static JsonObject integerMap(Map<String, Integer> values) {
        JsonObject result = new JsonObject();
        values.forEach(result::addProperty);
        return result;
    }

    /** Items by id, in id order. */
    private static JsonObject itemMap(Map<String, Integer> values) {
        Map<Integer, Integer> sorted = new TreeMap<>();
        values.forEach((id, quantity) -> sorted.put(Integer.valueOf(id), quantity));
        return variables(sorted);
    }

    /** Variables by id, in id order. */
    private static JsonObject variables(Map<Integer, Integer> values) {
        JsonObject result = new JsonObject();
        new TreeMap<>(values).forEach((id, value) -> result.addProperty(id.toString(), value));
        return result;
    }
}
