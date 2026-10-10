package shortestpath.accounts.canonical;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import shortestpath.accounts.AccountBuild;

/**
 * Writes a canonical profile as an {@link AccountBuild}: its semantic state, plus the routing
 * variables the corpus publishes for consumers that read variables rather than semantic state.
 */
final class ProfileCompiler {
    private ProfileCompiler() { }

    static AccountBuild build(ProfileSpec source) {
        AccountBuild build = new AccountBuild();
        build.schemaVersion = 1;
        build.id = source.name();
        build.name = title(source.name());
        build.benchmarkNowMinutes = RoutingVariables.BENCHMARK_NOW_MINUTES;

        Map<String, Integer> levels = new TreeMap<>();
        for (Map.Entry<Skill, Integer> entry : source.levels().entrySet()) {
            levels.put(entry.getKey().getName(), entry.getValue());
        }
        if (source.questPoints() != null) levels.put("Quest", source.questPoints());
        if (source.totalLevel() != null) levels.put("Total", source.totalLevel());
        build.levels.putAll(levels);

        List<String> quests = new ArrayList<>();
        for (Quest quest : source.completedQuests()) quests.add(quest.getName());
        for (CorpusQuest quest : source.completedCorpusQuests()) quests.add(quest.name);
        quests.sort(String::compareTo);
        build.completedQuests.addAll(quests);

        for (Diary diary : Diary.values()) {
            build.diaries.put(diary.jsonName, source.diaries().getOrDefault(diary, DiaryTier.NONE).jsonName);
        }
        items(build.inventory, source.inventory());
        items(build.equipment, source.equipment());
        items(build.runePouch, source.runePouch());
        items(build.bank, source.bank());
        build.fairyRingsUnlocked = source.fairyRingsUnlocked();
        for (PlantedSpiritTree tree : PlantedSpiritTree.values()) {
            if (source.plantedSpiritTrees().contains(tree)) build.plantedSpiritTrees.add(tree.name());
        }
        build.poh = poh(source.poh());
        build.runtime = runtime(source.runtime());
        build.routingVariables = RoutingVariables.compile(source, build);
        return build;
    }

    private static void items(Map<String, Integer> target, Map<Integer, Integer> source) {
        new TreeMap<>(source).forEach((id, quantity) -> target.put(id.toString(), quantity));
    }

    private static AccountBuild.Poh poh(PohSpec source) {
        AccountBuild.Poh poh = new AccountBuild.Poh();
        poh.location = title(source.location().name());
        poh.jewelleryBox = jewelleryBox(source.jewelleryBox());
        poh.portals = new AccountBuild.Portals();
        poh.portals.mode = source.portalMode() == PortalMode.ALL ? "all" : "selected";
        if (source.portalMode() != PortalMode.ALL) {
            poh.portals.destinations.addAll(source.portalDestinations());
            poh.portals.destinations.sort(String::compareTo);
        }
        poh.fairyRing = source.fairyRing();
        poh.spiritTree = source.spiritTree();
        poh.obelisk = source.obelisk();
        poh.mountedGlory = source.mountedGlory();
        poh.mountedXerics = source.mountedXerics();
        poh.mountedDigsite = source.mountedDigsite();
        poh.mountedMythical = source.mountedMythical();
        return poh;
    }

    private static AccountBuild.RuntimeState runtime(RuntimeSpec source) {
        AccountBuild.RuntimeState runtime = new AccountBuild.RuntimeState();
        runtime.arriveInsidePoh = source.arriveInsidePoh;
        runtime.spellbook = title(source.spellbook.name());
        runtime.minigameTeleport = new AccountBuild.MinigameTeleport();
        runtime.minigameTeleport.state = source.cooldown.usedAt == null ? "ready" : "usedAt";
        runtime.minigameTeleport.minutes = source.cooldown.usedAt == null ? null : source.cooldown.usedAt.longValue();
        return runtime;
    }

    private static String jewelleryBox(JewelleryBox box) {
        switch (box) {
            case NONE: return "NoJewelleryBox";
            case FANCY: return "FancyJewelleryBox";
            case ORNATE: return "OrnateJewelleryBox";
            default: throw new AssertionError(box);
        }
    }

    static String title(String value) {
        return value.substring(0, 1) + value.substring(1).toLowerCase();
    }
}
