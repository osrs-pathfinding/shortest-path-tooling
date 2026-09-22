package shortestpath.corpus.profiles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class ProfileJsonRenderer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private ProfileJsonRenderer() { }

    static String render(List<ProfileSpec> sources) {
        JsonObject root = new JsonObject();
        root.addProperty("benchmarkNowMinutes", RoutingVariables.BENCHMARK_NOW_MINUTES);
        root.addProperty("formatVersion", 1);
        JsonObject profiles = new JsonObject();
        for (ProfileSpec source : sources) profiles.add(source.name(), profile(source));
        root.add("profiles", profiles);
        return GSON.toJson(root) + "\n";
    }

    static JsonObject profile(ProfileSpec source) {
        CompiledProfile compiled = ProfileCompiler.compile(source);
        JsonObject result = new JsonObject();
        result.add("bank", integerMap(source.bank()));
        result.add("completedQuests", strings(compiled.quests));
        result.add("diaries", diaries(source));
        result.add("equipment", integerMap(source.equipment()));
        result.addProperty("fairyRingsUnlocked", source.fairyRingsUnlocked());
        result.add("inventory", integerMap(source.inventory()));
        result.add("levels", stringIntegerMap(compiled.levels));
        result.add("plantedSpiritTrees", spiritTrees(source));
        result.add("poh", poh(source));
        result.add("runePouch", integerMap(source.runePouch()));
        result.add("runtime", runtime(source));
        result.add("varbits", integerMap(compiled.variables.varbits));
        result.add("varplayers", integerMap(compiled.variables.varplayers));
        return result;
    }

    private static JsonObject poh(ProfileSpec source) {
        PohSpec poh = source.poh();
        JsonObject result = new JsonObject();
        result.addProperty("fairyRing", poh.fairyRing());
        result.addProperty("jewelleryBox", jewelleryName(poh.jewelleryBox()));
        result.addProperty("location", title(poh.location().name()));
        result.addProperty("mountedDigsite", poh.mountedDigsite());
        result.addProperty("mountedGlory", poh.mountedGlory());
        result.addProperty("mountedMythical", poh.mountedMythical());
        result.addProperty("mountedXerics", poh.mountedXerics());
        result.addProperty("obelisk", poh.obelisk());
        JsonObject portals = new JsonObject();
        List<String> destinations = poh.portalMode() == PortalMode.ALL ? new ArrayList<>()
            : new ArrayList<>(poh.portalDestinations());
        Collections.sort(destinations);
        portals.add("destinations", strings(destinations));
        portals.addProperty("mode", poh.portalMode() == PortalMode.ALL ? "all" : "selected");
        result.add("portals", portals);
        result.addProperty("spiritTree", poh.spiritTree());
        return result;
    }

    private static JsonObject runtime(ProfileSpec source) {
        RuntimeSpec runtime = source.runtime();
        JsonObject result = new JsonObject();
        result.addProperty("arriveInsidePoh", runtime.arriveInsidePoh);
        JsonObject cooldown = new JsonObject();
        cooldown.addProperty("state", runtime.cooldown.usedAt == null ? "ready" : "usedAt");
        if (runtime.cooldown.usedAt != null) cooldown.addProperty("minutes", runtime.cooldown.usedAt);
        result.add("minigameTeleport", cooldown);
        result.addProperty("spellbook", title(runtime.spellbook.name()));
        return result;
    }

    private static JsonObject diaries(ProfileSpec source) {
        JsonObject result = new JsonObject();
        for (Diary diary : Diary.values()) result.addProperty(diary.jsonName,
            source.diaries().getOrDefault(diary, DiaryTier.NONE).jsonName);
        return result;
    }

    private static JsonArray spiritTrees(ProfileSpec source) {
        JsonArray result = new JsonArray();
        for (PlantedSpiritTree tree : PlantedSpiritTree.values()) if (source.plantedSpiritTrees().contains(tree)) {
            result.add(tree.name());
        }
        return result;
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }

    private static JsonObject stringIntegerMap(Map<String, Integer> values) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, Integer> entry : values.entrySet()) result.addProperty(entry.getKey(), entry.getValue());
        return result;
    }

    private static JsonObject integerMap(Map<Integer, Integer> values) {
        JsonObject result = new JsonObject();
        Map<Integer, Integer> sorted = new TreeMap<>(values);
        for (Map.Entry<Integer, Integer> entry : sorted.entrySet()) result.addProperty(entry.getKey().toString(), entry.getValue());
        return result;
    }

    private static String title(String value) {
        return value.substring(0, 1) + value.substring(1).toLowerCase();
    }

    private static String jewelleryName(JewelleryBox box) {
        switch (box) {
            case NONE: return "NoJewelleryBox";
            case FANCY: return "FancyJewelleryBox";
            case ORNATE: return "OrnateJewelleryBox";
            default: throw new AssertionError(box);
        }
    }
}
