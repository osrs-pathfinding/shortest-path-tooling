package shortestpath.accounts;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.ItemVariations;

/**
 * Compiles an {@link AccountBuild} into an {@link Account}: the only place an account's semantic
 * state becomes the varbits and varplayers the plugin reads.
 *
 * <p>The semantic state wins over the build's compatibility {@code routingVariables}: the variables
 * it implies ({@link #semanticVarbits}, {@link #semanticVarplayers}) overwrite them, so editing a
 * quest or diary in the web planner changes the route even when the variables say otherwise. The
 * canonical profile generator writes the same implied values into the variables it publishes.
 */
public final class AccountBuilds {
    private AccountBuilds() { }

    public static Account toAccount(AccountBuild build) {
        Account.Builder account = Account.builder().reportsRealLevels(true)
            .nowMinutes(build.benchmarkNowMinutes)
            .defaultQuestState(QuestState.NOT_STARTED)
            .inventoryContainer().equipmentContainer().bankContainer()
            .plantedSpiritTrees(spiritTrees(build.plantedSpiritTrees))
            .poh(poh(build.poh));

        int total = 0;
        for (Map.Entry<String, Integer> entry : build.levels.entrySet()) {
            if (entry.getKey().equals("Quest") || entry.getKey().equals("Total")) {
                continue;
            }
            account.level(skill(entry.getKey()), entry.getValue());
            total += entry.getValue();
        }
        account.totalLevel(total);

        for (Quest quest : Quest.values()) {
            if (build.completedQuests.contains(quest.getName())) {
                account.quest(quest, QuestState.FINISHED);
            }
        }

        build.routingVariables.varbits.forEach(account::varbit);
        build.routingVariables.varplayers.forEach(account::varplayer);
        account.varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, build.fairyRingsUnlocked ? 100 : 0);
        semanticVarbits(build).forEach(account::varbit);
        semanticVarplayers(build).forEach(account::varplayer);
        if (!build.routingVariables.varplayers.containsKey(VarPlayerID.QP)) {
            account.varplayer(VarPlayerID.QP, build.levels.getOrDefault("Quest", 0));
        }

        items("inventory", build.inventory).forEach(account::inventory);
        items("runePouch", build.runePouch).forEach(account::inventory);
        items("equipment", build.equipment).forEach(account::equipment);
        items("bank", build.bank).forEach(account::bank);
        return account.build();
    }

    /** The varbits a build's diaries, quests, spellbook and POH location imply. */
    public static Map<Integer, Integer> semanticVarbits(AccountBuild build) {
        Map<Integer, Integer> bits = new HashMap<>();
        bits.put(VarbitID.SPELLBOOK, spellbook(build.runtime.spellbook));
        bits.put(VarbitID.POH_HOUSE_LOCATION, pohLocation(build.poh.location));
        diary(bits, build, "Ardougne", VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE, VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE);
        diary(bits, build, "Desert", VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE, VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE);
        diary(bits, build, "Falador", VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE, VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE);
        diary(bits, build, "Fremennik", VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE, VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE);
        diary(bits, build, "Kandarin", VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE);
        diary(bits, build, "Karamja", VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE, VarbitID.ATJUN_HARD_DONE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE);
        diary(bits, build, "KourendKebos", VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE, VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE);
        diary(bits, build, "LumbridgeDraynor", VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE, VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);
        diary(bits, build, "Morytania", VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE, VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE);
        diary(bits, build, "Varrock", VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE);
        diary(bits, build, "WesternProvinces", VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE, VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE);
        diary(bits, build, "Wilderness", VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE);
        questProgress(bits, build, VarbitID.LOVAQUEST, "The Forsaken Tower", 11);
        questProgress(bits, build, VarbitID.MY2ARM_STATUS, "Making Friends with My Arm", 207);
        questProgress(bits, build, VarbitID.THZFE_BLOCKING_BARRICADE, "Zogre Flesh Eaters", 1);
        questProgress(bits, build, VarbitID.HOSIDIUSQUEST, "The Depths of Despair", 7);
        questProgress(bits, build, VarbitID.DRAGONSLAYER_CRANDOR_FOUND_SECRET_DOOR, "Dragon Slayer I", 1);
        questProgress(bits, build, VarbitID.MYQ3_MAIN_QUEST, "Darkness of Hallowvale", 320);
        questProgress(bits, build, VarbitID.MDAUGHTER_QUEST_VAR, "Mountain Daughter", 70);
        questProgress(bits, build, VarbitID.DWARFROCK_QUEST, "Between a Rock...", 10);
        questProgress(bits, build, VarbitID.GOLEM_A, "The Golem", 10);
        questProgress(bits, build, VarbitID.ICS_LITTLE_VAR, "Icthlarin's Little Helper", 26);
        questProgress(bits, build, VarbitID.TOG_JUNA_BOWL, "Tears of Guthix", 2);
        questProgress(bits, build, VarbitID.ZOGRE, "Zogre Flesh Eaters", 14);
        questProgress(bits, build, VarbitID.LOST_TRIBE_QUEST, "The Lost Tribe", 12);
        questProgress(bits, build, VarbitID.SWANSONG, "Swan Song", 200);
        questProgress(bits, build, VarbitID.FRIS_QUEST, "The Fremennik Isles", 340);
        questProgress(bits, build, VarbitID.VEOS_PROGRESS, "Client of Kourend", 1);
        questProgress(bits, build, VarbitID.HOSIDIUSQUEST_REWARD, "The Depths of Despair", 1);
        questProgress(bits, build, VarbitID.PISCQUEST_REWARD, "The Queen of Thieves", 1);
        questProgress(bits, build, VarbitID.SHAYZIENQUEST_REWARD, "The Tale of the Righteous", 1);
        questProgress(bits, build, VarbitID.LOVAQUEST_REWARD, "The Forsaken Tower", 1);
        questProgress(bits, build, VarbitID.ARCQUEST_REWARD, "Architectural Alliance", 1);
        questProgress(bits, build, VarbitID.BCS, "Beneath Cursed Sands", 108);
        return bits;
    }

    /** The varplayers a build's quests and minigame teleport cooldown imply. */
    public static Map<Integer, Integer> semanticVarplayers(AccountBuild build) {
        Map<Integer, Integer> players = new HashMap<>();
        players.put(VarPlayerID.SLUG2_REGIONUID, "ready".equals(build.runtime.minigameTeleport.state)
            ? Math.toIntExact(build.benchmarkNowMinutes - 21)
            : Math.toIntExact(build.runtime.minigameTeleport.minutes));
        questProgress(players, build, VarPlayerID.LEGENDSQUEST, "Legends' Quest", 75);
        questProgress(players, build, VarPlayerID.ZOMBIEQUEEN, "Shilo Village", 15);
        questProgress(players, build, VarPlayerID.WATERFALL_QUEST, "Waterfall Quest", 10);
        questProgress(players, build, VarPlayerID.FISHINGCOMPO, "Fishing Contest", 5);
        questProgress(players, build, VarPlayerID.TREEQUEST, "Tree Gnome Village", 9);
        questProgress(players, build, VarPlayerID.GRANDTREE, "The Grand Tree", 160);
        questProgress(players, build, VarPlayerID.ELENAQUEST, "Plague City", 30);
        questProgress(players, build, VarPlayerID.DRAGONQUEST, "Dragon Slayer I", 10);
        questProgress(players, build, VarPlayerID.ITWATCHTOWER, "Watchtower", 14);
        questProgress(players, build, VarPlayerID.REGICIDE_QUEST, "Regicide", 15);
        questProgress(players, build, VarPlayerID.MISC_QUEST, "Throne of Miscellania", 100);
        questProgress(players, build, VarPlayerID.MOURNING_QUEST, "Mourning's End Part I", 9);
        return players;
    }

    private static void questProgress(Map<Integer, Integer> target, AccountBuild build, int id,
            String quest, int complete) {
        target.put(id, build.completedQuests.contains(quest) ? complete : 0);
    }

    private static void diary(Map<Integer, Integer> target, AccountBuild build, String region,
            int easy, int medium, int hard, int elite) {
        String tier = build.diaries.getOrDefault(region, "NoDiary");
        int level = List.of("NoDiary", "Easy", "Medium", "Hard", "Elite").indexOf(tier);
        if (level < 0) {
            throw new IllegalArgumentException("unknown diary tier: " + tier);
        }
        target.put(easy, level >= 1 ? 1 : 0);
        target.put(medium, level >= 2 ? 1 : 0);
        target.put(hard, level >= 3 ? 1 : 0);
        target.put(elite, level >= 4 ? 1 : 0);
    }

    private static int spellbook(String value) {
        switch (value) {
            case "Standard": return 0;
            case "Ancient": return 1;
            case "Lunar": return 2;
            case "Arceuus": return 3;
            default: throw new IllegalArgumentException("unknown spellbook: " + value);
        }
    }

    private static int pohLocation(String value) {
        switch (value) {
            case "Rimmington": return 1;
            case "Taverley": return 2;
            case "Pollnivneach": return 3;
            case "Rellekka": return 4;
            case "Brimhaven": return 5;
            case "Yanille": return 6;
            case "Prifddinas": return 7;
            case "Hosidius": return 8;
            case "Aldarin": return 9;
            default: throw new IllegalArgumentException("unknown POH location: " + value);
        }
    }

    private static Account.Poh poh(AccountBuild.Poh poh) {
        return new Account.Poh(poh.fairyRing, poh.spiritTree, poh.obelisk, jewelleryBox(poh.jewelleryBox),
            poh.mountedGlory, poh.mountedXerics, poh.mountedDigsite, poh.mountedMythical,
            "all".equals(poh.portals.mode) ? null : poh.portals.destinations);
    }

    private static Account.JewelleryBox jewelleryBox(String value) {
        switch (value) {
            case "NoJewelleryBox": return Account.JewelleryBox.NONE;
            case "FancyJewelleryBox": return Account.JewelleryBox.FANCY;
            case "OrnateJewelleryBox": return Account.JewelleryBox.ORNATE;
            default: throw new IllegalArgumentException("unknown POH jewellery box: " + value);
        }
    }

    private static Skill skill(String name) {
        for (Skill skill : Skill.values()) {
            if (skill.getName().equals(name)) {
                return skill;
            }
        }
        throw new IllegalArgumentException("unknown skill: " + name);
    }

    /** Items by id; a key may also name an {@link ItemVariations} constant, which means its first id. */
    private static Map<Integer, Integer> items(String field, Map<String, Integer> source) {
        Map<Integer, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            int id;
            try {
                id = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException notAnId) {
                ItemVariations variation = ItemVariations.fromName(entry.getKey());
                if (variation == null) {
                    throw new IllegalArgumentException("unknown item in " + field + ": " + entry.getKey());
                }
                id = variation.getIds()[0];
            }
            result.merge(id, entry.getValue(), Math::addExact);
        }
        return result;
    }

    private static Set<String> spiritTrees(List<String> names) {
        Set<String> result = new LinkedHashSet<>();
        for (String name : names) {
            switch (name) {
                case "FARMING_GUILD": result.add("Farming Guild"); break;
                case "PORT_SARIM": result.add("Port Sarim"); break;
                case "ETCETERIA": result.add("Etceteria"); break;
                case "BRIMHAVEN": result.add("Brimhaven"); break;
                case "HOSIDIUS": result.add("Hosidius"); break;
                default: throw new IllegalArgumentException("unknown planted spirit tree: " + name);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
