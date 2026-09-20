package shortestpath.corpus.profiles;

import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.Quest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compiles routing-facing variables while keeping IDs out of ProfileSpec.
 *
 * The two compact baseline tables retain the old v1 fixture's intentionally
 * unmodelled routing state. Named progression rules below use RuneLite
 * VarbitID/VarPlayerID constants; a future investigation can replace one
 * baseline entry with a named semantic rule without changing ProfileSpec.
 */
final class RoutingVariables {
    static final int BENCHMARK_NOW_MINUTES = 100_000_000;
    private static final int[] IDS = ints("260,299,346,418,451,487,496,532,538,621,668,2098,2187,2573,2867,2868,2869,2870,2871,2872,3264,3311,3578,3598,3599,3611,3637,3741,3759,3910,4070,4441,4458,4459,4460,4461,4462,4463,4464,4465,4466,4467,4468,4469,4471,4472,4473,4474,4475,4476,4477,4478,4479,4480,4481,4482,4483,4484,4485,4486,4487,4488,4489,4490,4491,4492,4493,4494,4495,4496,4497,4498,4541,4542,4548,4552,4558,4560,4561,4564,4566,4585,4744,4819,5005,5023,5087,5088,5421,5619,5629,5672,5673,5674,5675,5676,5677,5678,5679,5680,5681,5682,5683,5684,5810,6027,6028,6038,6056,6069,6076,6312,6359,6528,7255,7796,7801,7857,7925,7926,7927,7928,7937,7938,8253,8397,8398,9805,10449,10450,10528,11175,11176,11177,11178,11410,12310,12341,13599,13839,13841,17226,18351,18355,18356,18370,18371");
    private static final int[] EARLY = ints("0,0,0,0,0,0,0,12,0,0,0,0,1,0,0,0,0,0,0,0,0,0,1,1,1,0,0,1,0,0,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0");
    private static final int[] PROGRESSED = ints("70,10,10,26,2,14,1,12,1,1,1,200,1,320,2,2,1,1,1,1,11,340,1,1,1,1,153,1,2,1,0,2,1,1,1,0,1,1,1,0,1,1,1,0,1,1,1,0,1,1,1,0,1,1,1,0,1,1,1,0,1,1,1,0,1,1,1,0,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,7,1,1,1,0,1,1,1,207,88,11,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,9,1,1,50,1,108,1,1,1,1,1,1");
    private static final int[] PLAYER_IDS = ints("11,65,111,116,139,150,165,176,212,328,359,517,888,892,4182,4560");
    private static final int[] EARLY_PLAYERS = ints("0,0,9,0,0,160,30,10,14,15,0,0,99999979,0,0,0");
    private static final int[] PROGRESSED_PLAYERS = ints("5,10,9,15,75,160,30,10,14,15,100,9,99999979,0,18912,0");

    private RoutingVariables() { }

    static CompiledVariables compile(ProfileSpec profile) {
        Map<Integer, Integer> bits = new LinkedHashMap<>();
        int[] values = profile.name.equals("early") ? EARLY : PROGRESSED;
        for (int i = 0; i < IDS.length; i++) bits.put(IDS[i], values[i]);
        compileDiary(bits, profile);
        bits.put(VarbitID.SPELLBOOK, profile.runtime.spellbook.ordinal());
        bits.put(VarbitID.POH_HOUSE_LOCATION, pohLocation(profile.poh.location));
        Map<Integer, Integer> players = playersFor(profile);
        if (!profile.name.equals("early")) {
            compileQuestBits(bits, profile);
            compileQuestPlayers(players, profile);
            compileUnlocks(bits, profile);
            compileBalloonBits(bits, profile);
            compileCatacombsBits(bits, profile);
            compileQuetzal(players, profile);
        }
        players.put(VarPlayerID.SLUG2_REGIONUID,
            profile.runtime.cooldown.usedAt == null ? BENCHMARK_NOW_MINUTES - 21 : profile.runtime.cooldown.usedAt);
        return new CompiledVariables(bits, players);
    }

    private static Map<Integer, Integer> playersFor(ProfileSpec profile) {
        Map<Integer, Integer> players = new LinkedHashMap<>();
        int[] values = profile.name.equals("early") ? EARLY_PLAYERS : PROGRESSED_PLAYERS;
        for (int i = 0; i < PLAYER_IDS.length; i++) players.put(PLAYER_IDS[i], values[i]);
        return players;
    }

    private static void compileQuestBits(Map<Integer, Integer> bits, ProfileSpec profile) {
        assign(bits, VarbitID.LOVAQUEST, done(profile, Quest.THE_FORSAKEN_TOWER, 11), "LOVAQUEST", "The Forsaken Tower");
        assign(bits, VarbitID.MY2ARM_STATUS, done(profile, Quest.MAKING_FRIENDS_WITH_MY_ARM, 207), "MY2ARM_STATUS", "Making Friends with My Arm");
        assign(bits, VarbitID.THZFE_BLOCKING_BARRICADE, done(profile, Quest.ZOGRE_FLESH_EATERS, 1), "THZFE_BLOCKING_BARRICADE", "Zogre Flesh Eaters");
        assign(bits, VarbitID.HOSIDIUSQUEST, done(profile, Quest.THE_DEPTHS_OF_DESPAIR, 7), "HOSIDIUSQUEST", "The Depths of Despair");
        assign(bits, VarbitID.MYQ5, profile.questMilestones.contains(QuestMilestone.SINS_OF_THE_FATHER_SLEPE_BOAT_ACCESS) ? 88 : 0, "MYQ5", "Sins of the Father");
        assign(bits, VarbitID.LOTG, profile.questMilestones.contains(QuestMilestone.LAND_OF_THE_GOBLINS_YU_BIUSK_ACCESS) ? 50 : 0, "LOTG", "Land of the Goblins");
        assign(bits, VarbitID.DRAGONSLAYER_CRANDOR_FOUND_SECRET_DOOR, done(profile, Quest.DRAGON_SLAYER_I, 1), "DRAGONSLAYER_CRANDOR_FOUND_SECRET_DOOR", "Dragon Slayer I");
        assign(bits, VarbitID.MYQ3_MAIN_QUEST, done(profile, Quest.DARKNESS_OF_HALLOWVALE, 320), "MYQ3_MAIN_QUEST", "Darkness of Hallowvale");
        assign(bits, VarbitID.MDAUGHTER_QUEST_VAR, done(profile, Quest.MOUNTAIN_DAUGHTER, 70), "MDAUGHTER_QUEST_VAR", "Mountain Daughter");
        assign(bits, VarbitID.DWARFROCK_QUEST, done(profile, Quest.BETWEEN_A_ROCK, 10), "DWARFROCK_QUEST", "Between a Rock...");
        assign(bits, VarbitID.GOLEM_A, done(profile, Quest.THE_GOLEM, 10), "GOLEM_A", "The Golem");
        assign(bits, VarbitID.ICS_LITTLE_VAR, done(profile, Quest.ICTHLARINS_LITTLE_HELPER, 26), "ICS_LITTLE_VAR", "Icthlarin's Little Helper");
        assign(bits, VarbitID.TOG_JUNA_BOWL, done(profile, Quest.TEARS_OF_GUTHIX, 2), "TOG_JUNA_BOWL", "Tears of Guthix");
        assign(bits, VarbitID.ZOGRE, done(profile, Quest.ZOGRE_FLESH_EATERS, 14), "ZOGRE", "Zogre Flesh Eaters");
        assign(bits, VarbitID.LOST_TRIBE_QUEST, done(profile, Quest.THE_LOST_TRIBE, 12), "LOST_TRIBE_QUEST", "The Lost Tribe");
        assign(bits, VarbitID.SWANSONG, done(profile, Quest.SWAN_SONG, 200), "SWANSONG", "Swan Song");
        assign(bits, VarbitID.FRIS_QUEST, done(profile, Quest.THE_FREMENNIK_ISLES, 340), "FRIS_QUEST", "The Fremennik Isles");
        assign(bits, VarbitID.VEOS_PROGRESS, done(profile, Quest.CLIENT_OF_KOUREND, 1), "VEOS_PROGRESS", "Client of Kourend");
        assign(bits, VarbitID.HOSIDIUSQUEST_REWARD, done(profile, Quest.THE_DEPTHS_OF_DESPAIR, 1), "HOSIDIUSQUEST_REWARD", "The Depths of Despair");
        assign(bits, VarbitID.PISCQUEST_REWARD, done(profile, Quest.THE_QUEEN_OF_THIEVES, 1), "PISCQUEST_REWARD", "The Queen of Thieves");
        assign(bits, VarbitID.SHAYZIENQUEST_REWARD, done(profile, CorpusQuest.TALE_OF_THE_RIGHTEOUS, 1), "SHAYZIENQUEST_REWARD", "The Tale of the Righteous");
        assign(bits, VarbitID.LOVAQUEST_REWARD, done(profile, Quest.THE_FORSAKEN_TOWER, 1), "LOVAQUEST_REWARD", "The Forsaken Tower");
        assign(bits, VarbitID.ARCQUEST_REWARD, done(profile, CorpusQuest.ARCHITECTURAL_ALLIANCE, 1), "ARCQUEST_REWARD", "Architectural Alliance");
        assign(bits, VarbitID.BCS, done(profile, Quest.BENEATH_CURSED_SANDS, 108), "BCS", "Beneath Cursed Sands");
    }

    private static void compileQuestPlayers(Map<Integer, Integer> players, ProfileSpec profile) {
        // These values are RuneLite's completed quest state values used by GPS.
        players.put(VarPlayerID.LEGENDSQUEST, player(profile, Quest.LEGENDS_QUEST, 75));
        players.put(VarPlayerID.ZOMBIEQUEEN, player(profile, Quest.SHILO_VILLAGE, 15));
        players.put(VarPlayerID.WATERFALL_QUEST, player(profile, Quest.WATERFALL_QUEST, 10));
        players.put(VarPlayerID.FISHINGCOMPO, player(profile, Quest.FISHING_CONTEST, 5));
        players.put(VarPlayerID.TREEQUEST, player(profile, Quest.TREE_GNOME_VILLAGE, 9));
        players.put(VarPlayerID.GRANDTREE, player(profile, Quest.THE_GRAND_TREE, 160));
        players.put(VarPlayerID.ELENAQUEST, player(profile, Quest.PLAGUE_CITY, 30));
        players.put(VarPlayerID.DRAGONQUEST, player(profile, Quest.DRAGON_SLAYER_I, 10));
        players.put(VarPlayerID.ITWATCHTOWER, player(profile, Quest.WATCHTOWER, 14));
        players.put(VarPlayerID.REGICIDE_QUEST, player(profile, Quest.REGICIDE, 15));
        players.put(VarPlayerID.MISC_QUEST, player(profile, Quest.THRONE_OF_MISCELLANIA, 100));
        players.put(VarPlayerID.MOURNING_QUEST, player(profile, Quest.MOURNINGS_END_PART_I, 9));
    }

    private static void compileUnlocks(Map<Integer, Integer> bits, ProfileSpec profile) {
        int[][] values = {{VarbitID.RAIDS_GUIDE_TRAVEL_UNLOCK, 1}, {VarbitID.CORSAIR_COVE_RESOURCE_ENTRY, 1},
            {VarbitID.LOST_TRIBE_HOLE_2_DUG, 1}, {VarbitID.BARBASSAULT_ARENANEWB, 11}, {VarbitID.VM_KUDOS, 153},
            {VarbitID.BRUT_FIRE, 2}, {VarbitID.FENK_BUILT_BRIDGE_NORTH, 2}, {VarbitID.FENK_BUILT_BRIDGE_SOUTH, 2},
            {VarbitID.KARAM_DUNGEON_BACKDOOR, 1}, {VarbitID.OBSERVATORY_SHORTCUT_ROPE, 1},
            {VarbitID.HOSDUN_WEST_DOOR_STATUS, 1}, {VarbitID.HOSDUN_EAST_DOOR_STATUS, 1},
            {VarbitID.DARKM_SHORTCUT_INNER, 1}, {VarbitID.DARKM_SHORTCUT_OUTER, 1},
            {VarbitID.MET_AUBURN_MOUNTAIN_GUIDE, 1}, {VarbitID.BOOKOFSCROLLS_NARDAH, 1},
            {VarbitID.BOOKOFSCROLLS_DIGSITE, 1}, {VarbitID.BOOKOFSCROLLS_FELDIP, 1},
            {VarbitID.BOOKOFSCROLLS_LUNARISLE, 1}, {VarbitID.BOOKOFSCROLLS_MORTTON, 1},
            {VarbitID.BOOKOFSCROLLS_PESTCONTROL, 1}, {VarbitID.BOOKOFSCROLLS_PISCATORIS, 1},
            {VarbitID.BOOKOFSCROLLS_TAIBWO, 1}, {VarbitID.BOOKOFSCROLLS_ELF, 1},
            {VarbitID.BOOKOFSCROLLS_MOSLES, 1}, {VarbitID.BOOKOFSCROLLS_LUMBERYARD, 1},
            {VarbitID.BOOKOFSCROLLS_ZULANDRA, 1}, {VarbitID.BOOKOFSCROLLS_CERBERUS, 1},
            {VarbitID.BOOKOFSCROLLS_REVENANTS, 1}, {VarbitID.BOOKOFSCROLLS_WATSON_LOWBITS, 1},
            {VarbitID.PENDANT_OF_ATES_DARKFROST_FOUND, 1}, {VarbitID.PENDANT_OF_ATES_TWILIGHT_FOUND, 1},
            {VarbitID.PENDANT_OF_ATES_RALOS_FOUND, 1}, {VarbitID.PENDANT_OF_ATES_ALDARIN_FOUND, 1},
            {VarbitID.PHARAOHS_SCEPTRE_NECROPOLIS, 1}, {VarbitID.COLOSSEUM_HIGHEST_WAVE, 9},
            {VarbitID.AMENITY_ROWBOAT_VATRACHOS, 1}, {VarbitID.AMENITY_ROWBOAT_ANGLERS, 1},
            {VarbitID.AMENITY_ROWBOAT_SOUL_TEAR, 1}, {VarbitID.AMENITY_ROWBOAT_YNYSDAIL, 1},
            {VarbitID.AMENITY_ROWBOAT_BUCCANEERS, 1}, {VarbitID.FALADOR_SPAWN, 1}, {VarbitID.CAMELOT_SPAWN, 1},
            {VarbitID.EDGEVILLE_SPAWN, 1}, {VarbitID.WILDERNESS_SPAWN, 1}, {VarbitID.KOUREND_SPAWN, 1},
            {VarbitID.CIVITAS_SPAWN, 1}};
        PermanentUnlock[] unlocks = PermanentUnlock.values();
        for (int i = 0; i < values.length; i++) {
            bits.put(values[i][0], profile.permanentUnlocks.contains(unlocks[i]) ? values[i][1] : 0);
        }
    }

    private static void compileBalloonBits(Map<Integer, Integer> bits, ProfileSpec profile) {
        int[] ids = {VarbitID.ZEP_MULTI_BASKET, VarbitID.ZEP_MULTI_PICCARD, VarbitID.ZEP_MULTI_CAST,
            VarbitID.ZEP_MULTI_GNO, VarbitID.ZEP_MULTI_CRAFT, VarbitID.ZEP_MULTI_VARR};
        int[] values = {2, 2, 1, 1, 1, 1};
        HotAirBalloonDestination[] destinations = HotAirBalloonDestination.values();
        for (int i = 0; i < ids.length; i++) {
            bits.put(ids[i], profile.hotAirBalloonDestinations.contains(destinations[i]) ? values[i] : 0);
        }
    }

    private static void compileCatacombsBits(Map<Integer, Integer> bits, ProfileSpec profile) {
        bits.put(VarbitID.CATA_HOLE1, profile.catacombsEntrances.contains(CatacombsEntrance.FORTHOS_DUNGEON) ? 1 : 0);
        bits.put(VarbitID.CATA_HOLE2, profile.catacombsEntrances.contains(CatacombsEntrance.SURFACE_ENTRANCES) ? 1 : 0);
        bits.put(VarbitID.CATA_HOLE_GIANTS_DEN, profile.catacombsEntrances.contains(CatacombsEntrance.GIANTS_DEN) ? 1 : 0);
    }

    private static void compileQuetzal(Map<Integer, Integer> players, ProfileSpec profile) {
        int mask = 0;
        if (profile.quetzalPlatforms.contains(QuetzalPlatform.CAM_TORUM)) mask |= 32;
        if (profile.quetzalPlatforms.contains(QuetzalPlatform.COLOSSAL_WYRM_REMAINS)) mask |= 64;
        if (profile.quetzalPlatforms.contains(QuetzalPlatform.OUTER_FORTIS)) mask |= 128;
        if (profile.quetzalPlatforms.contains(QuetzalPlatform.FORTIS_COLOSSEUM)) mask |= 256;
        if (profile.quetzalPlatforms.contains(QuetzalPlatform.SALVAGER_OVERLOOK)) mask |= 2048;
        if (profile.quetzalPlatforms.contains(QuetzalPlatform.KASTORI)) mask |= 16384;
        players.put(VarPlayerID.QUETZALS_UNLOCKED, mask);
    }

    private static int pohLocation(PohLocation location) {
        switch (location) {
            case RIMMINGTON: return 1;
            case TAVERLEY: return 2;
            case POLLNIVNEACH: return 3;
            case RELLEKKA: return 4;
            case BRIMHAVEN: return 5;
            case YANILLE: return 6;
            case PRIFDDINAS: return 7;
            case HOSIDIUS: return 8;
            case ALDARIN: return 9;
            default: throw new AssertionError(location);
        }
    }

    private static int done(ProfileSpec profile, Quest quest, int value) {
        return profile.completedQuests.contains(quest) ? value : 0;
    }

    private static int player(ProfileSpec profile, Quest quest, int value) {
        return done(profile, quest, value);
    }

    private static int done(ProfileSpec profile, CorpusQuest quest, int value) {
        return profile.completedCorpusQuests.contains(quest) ? value : 0;
    }

    static void compileDiary(Map<Integer, Integer> bits, ProfileSpec profile) {
        for (Diary diary : Diary.values()) {
            DiaryTier tier = profile.diaries.getOrDefault(diary, DiaryTier.NONE);
            int[] ids = diaryIds(diary);
            for (int i = 0; i < ids.length; i++) bits.put(ids[i], tier.ordinal() > i ? 1 : 0);
        }
    }

    static void assign(Map<Integer, Integer> target, int id, int value, String symbol, String source) {
        if (target.containsKey(id) && target.get(id) != value) {
            throw new VariableConflictException("Conflicting VARBIT " + symbol + " (" + id + "): existing="
                + target.get(id) + ", " + source + " -> " + value);
        }
        target.put(id, value);
    }

    private static int[] diaryIds(Diary diary) {
        switch (diary) {
            case ARDOUGNE: return new int[] {VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE, VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE};
            case DESERT: return new int[] {VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE, VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE};
            case FALADOR: return new int[] {VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE, VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE};
            case FREMENNIK: return new int[] {VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE, VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE};
            case KANDARIN: return new int[] {VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE};
            case KARAMJA: return new int[] {VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE, VarbitID.ATJUN_HARD_DONE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE};
            case KOUREND_KEBOS: return new int[] {VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE, VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE};
            case LUMBRIDGE_DRAYNOR: return new int[] {VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE, VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE};
            case MORYTANIA: return new int[] {VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE, VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE};
            case VARROCK: return new int[] {VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE};
            case WESTERN_PROVINCES: return new int[] {VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE, VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE};
            case WILDERNESS: return new int[] {VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE};
            default: throw new AssertionError(diary);
        }
    }

    private static int[] ints(String values) {
        String[] parts = values.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Integer.parseInt(parts[i]);
        return result;
    }
}

final class CompiledVariables {
    final Map<Integer, Integer> varbits;
    final Map<Integer, Integer> varplayers;
    CompiledVariables(Map<Integer, Integer> varbits, Map<Integer, Integer> varplayers) {
        this.varbits = varbits;
        this.varplayers = varplayers;
    }
}

final class VariableConflictException extends IllegalArgumentException {
    VariableConflictException(String message) { super(message); }
}
