package shortestpath.accounts.canonical;

import net.runelite.api.Quest;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Translates semantic profile state into the routing variables consumed by v1. */
final class RoutingVariables {
    static final int BENCHMARK_NOW_MINUTES = 100_000_000;

    /**
     * The raw routing variables every profile sets, as (early value, progressed value). Early is the
     * {@code early} baseline; progressed is {@code mid}, {@code end} and {@code maxed}. Semantic state
     * (diaries, quests, unlocks, POH) is compiled on top and must agree with these.
     */
    private static final List<Keyed> VARBITS = List.of(
        keyed(VarbitID.MDAUGHTER_QUEST_VAR, 0, 70),
        keyed(VarbitID.DWARFROCK_QUEST, 0, 10),
        keyed(VarbitID.GOLEM_A, 0, 10),
        keyed(VarbitID.ICS_LITTLE_VAR, 0, 26),
        keyed(VarbitID.TOG_JUNA_BOWL, 0, 2),
        keyed(VarbitID.ZOGRE, 0, 14),
        keyed(VarbitID.THZFE_BLOCKING_BARRICADE, 0, 1),
        keyed(VarbitID.LOST_TRIBE_QUEST, 12, 12),
        keyed(VarbitID.LOST_TRIBE_HOLE_2_DUG, 0, 1),
        keyed(VarbitID.EDGEVILLE_SPAWN, 0, 1),
        keyed(VarbitID.FALADOR_SPAWN, 0, 1),
        keyed(VarbitID.SWANSONG, 0, 200),
        keyed(VarbitID.POH_HOUSE_LOCATION, 1, 1),
        keyed(VarbitID.MYQ3_MAIN_QUEST, 0, 320),
        keyed(VarbitID.ZEP_MULTI_BASKET, 0, 2),
        keyed(VarbitID.ZEP_MULTI_PICCARD, 0, 2),
        keyed(VarbitID.ZEP_MULTI_CAST, 0, 1),
        keyed(VarbitID.ZEP_MULTI_GNO, 0, 1),
        keyed(VarbitID.ZEP_MULTI_CRAFT, 0, 1),
        keyed(VarbitID.ZEP_MULTI_VARR, 0, 1),
        keyed(VarbitID.BARBASSAULT_ARENANEWB, 0, 11),
        keyed(VarbitID.FRIS_QUEST, 0, 340),
        keyed(VarbitID.ATJUN_MED_REWARD, 1, 1),
        keyed(VarbitID.VM_KUDOS, 0, 153),
        keyed(VarbitID.DRAGONSLAYER_CRANDOR_FOUND_SECRET_DOOR, 1, 1),
        keyed(VarbitID.BRUT_FIRE, 0, 2),
        keyed(VarbitID.CAMELOT_SPAWN, 0, 1),
        keyed(VarbitID.SPELLBOOK, 0, 0),
        keyed(VarbitID.FENK_BUILT_BRIDGE_NORTH, 0, 2),
        keyed(VarbitID.WILDERNESS_SWORD_LAST_TELEPORT, 0, 0),
        keyed(VarbitID.MORYTANIA_LEGS_LAST_TELEPORT, 0, 0),
        keyed(VarbitID.YANILLE_TELEPORT_LOCATION, 0, 0),
        keyed(VarbitID.LUMBRIDGE_CABBAGE_TELEPORT, 0, 0),
        keyed(VarbitID.DESERT_NARDAH_TELEPORT, 0, 0),
        keyed(VarbitID.SEERS_CAMELOT_TELEPORT, 0, 0),
        keyed(VarbitID.SEERS_SHERLOCK_TELEPORT, 0, 0),
        keyed(VarbitID.WESTERN_PISC_TELEPORT, 0, 0),
        keyed(VarbitID.VARROCK_GE_TELEPORT, 0, 0),
        keyed(VarbitID.POH_TELE_TOGGLE, 0, 0),
        keyed(VarbitID.CHINCHOMPA_TELEPORTS, 0, 0),
        keyed(VarbitID.FREMENNIK_BASIC_TELEPORT, 0, 0),
        keyed(VarbitID.FENK_BUILT_BRIDGE_SOUTH, 0, 2),
        keyed(VarbitID.CATA_HOLE1, 0, 1),
        keyed(VarbitID.CATA_HOLE2, 0, 1),
        keyed(VarbitID.RAIDS_GUIDE_TRAVEL_UNLOCK, 0, 1),
        keyed(VarbitID.VEOS_PROGRESS, 1, 1),
        keyed(VarbitID.KARAM_DUNGEON_BACKDOOR, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_NARDAH, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_DIGSITE, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_FELDIP, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_LUNARISLE, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_MORTTON, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_PESTCONTROL, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_PISCATORIS, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_TAIBWO, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_ELF, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_MOSLES, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_LUMBERYARD, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_ZULANDRA, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_CERBERUS, 0, 1),
        keyed(VarbitID.OBSERVATORY_SHORTCUT_ROPE, 0, 1),
        keyed(VarbitID.HOSIDIUSQUEST, 0, 7),
        keyed(VarbitID.HOSIDIUSQUEST_REWARD, 0, 1),
        keyed(VarbitID.PISCQUEST_REWARD, 0, 1),
        keyed(VarbitID.BOOKOFSCROLLS_REVENANTS, 0, 1),
        keyed(VarbitID.ARDOUGNE_CLOAK_LOWBITS, 0, 0),
        keyed(VarbitID.CORSAIR_COVE_RESOURCE_ENTRY, 1, 1),
        keyed(VarbitID.LUMBRIDGE_MED_COUNT, 1, 1),
        keyed(VarbitID.SHAYZIENQUEST_REWARD, 0, 1),
        keyed(VarbitID.MY2ARM_STATUS, 0, 207),
        keyed(VarbitID.MYQ5, 0, 88),
        keyed(VarbitID.LOVAQUEST, 0, 11),
        keyed(VarbitID.LOVAQUEST_REWARD, 0, 1),
        keyed(VarbitID.ARCQUEST_REWARD, 0, 1),
        keyed(VarbitID.ZEAH_BLESSING_WOODLAND_TELEPORT, 0, 0),
        keyed(VarbitID.ZEAH_BLESSING_BRIMSTONE_TELEPORT, 0, 0),
        keyed(VarbitID.BOOKOFSCROLLS_WATSON_LOWBITS, 0, 1),
        keyed(VarbitID.HOSDUN_WEST_DOOR_STATUS, 0, 1),
        keyed(VarbitID.HOSDUN_EAST_DOOR_STATUS, 0, 1),
        keyed(VarbitID.CIVITAS_SPAWN, 0, 1),
        keyed(VarbitID.DARKM_SHORTCUT_INNER, 0, 1),
        keyed(VarbitID.DARKM_SHORTCUT_OUTER, 0, 1),
        keyed(VarbitID.WILDERNESS_SPAWN, 0, 1),
        keyed(VarbitID.PENDANT_OF_ATES_DARKFROST_FOUND, 0, 1),
        keyed(VarbitID.PENDANT_OF_ATES_TWILIGHT_FOUND, 0, 1),
        keyed(VarbitID.PENDANT_OF_ATES_RALOS_FOUND, 0, 1),
        keyed(VarbitID.PENDANT_OF_ATES_ALDARIN_FOUND, 0, 1),
        keyed(VarbitID.COLOSSEUM_HIGHEST_WAVE, 0, 9),
        keyed(VarbitID.KOUREND_SPAWN, 0, 1),
        keyed(VarbitID.CATA_HOLE_GIANTS_DEN, 0, 1),
        keyed(VarbitID.LOTG, 0, 50),
        keyed(VarbitID.PHARAOHS_SCEPTRE_NECROPOLIS, 0, 1),
        keyed(VarbitID.BCS, 0, 108),
        keyed(VarbitID.MET_AUBURN_MOUNTAIN_GUIDE, 0, 1),
        keyed(VarbitID.AMENITY_ROWBOAT_VATRACHOS, 0, 1),
        keyed(VarbitID.AMENITY_ROWBOAT_ANGLERS, 0, 1),
        keyed(VarbitID.AMENITY_ROWBOAT_SOUL_TEAR, 0, 1),
        keyed(VarbitID.AMENITY_ROWBOAT_YNYSDAIL, 0, 1),
        keyed(VarbitID.AMENITY_ROWBOAT_BUCCANEERS, 0, 1)
    );
    private static final List<Keyed> VARPLAYERS = List.of(
        keyed(VarPlayerID.FISHINGCOMPO, 0, 5),
        keyed(VarPlayerID.WATERFALL_QUEST, 0, 10),
        keyed(VarPlayerID.TREEQUEST, 9, 9),
        keyed(VarPlayerID.ZOMBIEQUEEN, 0, 15),
        keyed(VarPlayerID.LEGENDSQUEST, 0, 75),
        keyed(VarPlayerID.GRANDTREE, 160, 160),
        keyed(VarPlayerID.ELENAQUEST, 30, 30),
        keyed(VarPlayerID.DRAGONQUEST, 10, 10),
        keyed(VarPlayerID.ITWATCHTOWER, 14, 14),
        keyed(VarPlayerID.REGICIDE_QUEST, 15, 15),
        keyed(VarPlayerID.MISC_QUEST, 0, 100),
        keyed(VarPlayerID.MOURNING_QUEST, 0, 9),
        keyed(VarPlayerID.SLUG2_REGIONUID, 99_999_979, 99_999_979),
        keyed(VarPlayerID.AIDE_TELE_TIMER, 0, 0),
        keyed(VarPlayerID.QUETZALS_UNLOCKED, 0, 18912),
        keyed(VarPlayerID.HOME_TELEPORT_ANIM_TOGGLES, 0, 0)
    );

    static Map<Integer, Integer> earlyVarbits() { return values(VARBITS, true); }
    static Map<Integer, Integer> earlyVarplayers() { return values(VARPLAYERS, true); }
    static Map<Integer, Integer> progressedVarbits() { return values(VARBITS, false); }
    static Map<Integer, Integer> progressedVarplayers() { return values(VARPLAYERS, false); }

    private static final class Keyed {
        final int id;
        final int early;
        final int progressed;

        Keyed(int id, int early, int progressed) {
            this.id = id;
            this.early = early;
            this.progressed = progressed;
        }
    }

    private static Keyed keyed(int id, int early, int progressed) {
        return new Keyed(id, early, progressed);
    }

    private static Map<Integer, Integer> values(List<Keyed> baseline, boolean early) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (Keyed variable : baseline) {
            if (result.put(variable.id, early ? variable.early : variable.progressed) != null) {
                throw new IllegalStateException("routing variable " + variable.id + " is keyed twice");
            }
        }
        return result;
    }

    private RoutingVariables() { }

    static CompiledVariables compile(ProfileSpec profile) {
        Map<Integer, Integer> bits = new LinkedHashMap<>();
        apply(bits, profile.routingVarbits(), "profile varbit baseline");
        compileDiary(bits, profile);
        assign(bits, VarbitID.SPELLBOOK, profile.runtime().spellbook.ordinal(), "SPELLBOOK", "runtime");
        assign(bits, VarbitID.POH_HOUSE_LOCATION, pohLocation(profile.poh().location()), "POH_HOUSE_LOCATION", "POH");
        Map<Integer, Integer> players = new LinkedHashMap<>();
        apply(players, profile.routingVarplayers(), "profile varplayer baseline");
        if (profile.includeProgressionRouting()) {
            compileQuestBits(bits, profile);
            compileQuestPlayers(players, profile);
            compileUnlocks(bits, profile);
            compileBalloonBits(bits, profile);
            compileCatacombsBits(bits, profile);
            compileQuetzal(players, profile);
        }
        assign(players, VarPlayerID.SLUG2_REGIONUID,
            profile.runtime().cooldown.usedAt == null ? BENCHMARK_NOW_MINUTES - 21 : profile.runtime().cooldown.usedAt,
            "SLUG2_REGIONUID", "runtime cooldown");
        return new CompiledVariables(bits, players);
    }

    private static void apply(Map<Integer, Integer> target, Map<Integer, Integer> values, String source) {
        for (Map.Entry<Integer, Integer> value : values.entrySet()) {
            assign(target, value.getKey(), value.getValue(), String.valueOf(value.getKey()), source);
        }
    }

    private static void compileQuestBits(Map<Integer, Integer> bits, ProfileSpec profile) {
        assign(bits, VarbitID.LOVAQUEST, done(profile, Quest.THE_FORSAKEN_TOWER, 11), "LOVAQUEST", "The Forsaken Tower");
        assign(bits, VarbitID.MY2ARM_STATUS, done(profile, Quest.MAKING_FRIENDS_WITH_MY_ARM, 207), "MY2ARM_STATUS", "Making Friends with My Arm");
        assign(bits, VarbitID.THZFE_BLOCKING_BARRICADE, done(profile, Quest.ZOGRE_FLESH_EATERS, 1), "THZFE_BLOCKING_BARRICADE", "Zogre Flesh Eaters");
        assign(bits, VarbitID.HOSIDIUSQUEST, done(profile, Quest.THE_DEPTHS_OF_DESPAIR, 7), "HOSIDIUSQUEST", "The Depths of Despair");
        assign(bits, VarbitID.MYQ5, profile.questMilestones().contains(QuestMilestone.SINS_OF_THE_FATHER_SLEPE_BOAT_ACCESS) ? 88 : 0, "MYQ5", "Sins of the Father");
        assign(bits, VarbitID.LOTG, profile.questMilestones().contains(QuestMilestone.LAND_OF_THE_GOBLINS_YU_BIUSK_ACCESS) ? 50 : 0, "LOTG", "Land of the Goblins");
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
        assign(players, VarPlayerID.LEGENDSQUEST, player(profile, Quest.LEGENDS_QUEST, 75), "LEGENDSQUEST", "Legends' Quest");
        assign(players, VarPlayerID.ZOMBIEQUEEN, player(profile, Quest.SHILO_VILLAGE, 15), "ZOMBIEQUEEN", "Shilo Village");
        assign(players, VarPlayerID.WATERFALL_QUEST, player(profile, Quest.WATERFALL_QUEST, 10), "WATERFALL_QUEST", "Waterfall Quest");
        assign(players, VarPlayerID.FISHINGCOMPO, player(profile, Quest.FISHING_CONTEST, 5), "FISHINGCOMPO", "Fishing Contest");
        assign(players, VarPlayerID.TREEQUEST, player(profile, Quest.TREE_GNOME_VILLAGE, 9), "TREEQUEST", "Tree Gnome Village");
        assign(players, VarPlayerID.GRANDTREE, player(profile, Quest.THE_GRAND_TREE, 160), "GRANDTREE", "The Grand Tree");
        assign(players, VarPlayerID.ELENAQUEST, player(profile, Quest.PLAGUE_CITY, 30), "ELENAQUEST", "Plague City");
        assign(players, VarPlayerID.DRAGONQUEST, player(profile, Quest.DRAGON_SLAYER_I, 10), "DRAGONQUEST", "Dragon Slayer I");
        assign(players, VarPlayerID.ITWATCHTOWER, player(profile, Quest.WATCHTOWER, 14), "ITWATCHTOWER", "Watchtower");
        assign(players, VarPlayerID.REGICIDE_QUEST, player(profile, Quest.REGICIDE, 15), "REGICIDE_QUEST", "Regicide");
        assign(players, VarPlayerID.MISC_QUEST, player(profile, Quest.THRONE_OF_MISCELLANIA, 100), "MISC_QUEST", "Throne of Miscellania");
        assign(players, VarPlayerID.MOURNING_QUEST, player(profile, Quest.MOURNINGS_END_PART_I, 9), "MOURNING_QUEST", "Mourning's End Part I");
    }

    private static void compileUnlocks(Map<Integer, Integer> bits, ProfileSpec profile) {
        unlock(bits, profile, PermanentUnlock.RAIDS_MOUNTAIN_GUIDE_TRAVEL, VarbitID.RAIDS_GUIDE_TRAVEL_UNLOCK, 1);
        unlock(bits, profile, PermanentUnlock.CORSAIR_COVE_RESOURCE_AREA, VarbitID.CORSAIR_COVE_RESOURCE_ENTRY, 1);
        unlock(bits, profile, PermanentUnlock.LOST_TRIBE_CELLAR_HOLE, VarbitID.LOST_TRIBE_HOLE_2_DUG, 1);
        unlock(bits, profile, PermanentUnlock.BARBARIAN_ASSAULT_TUTORIAL, VarbitID.BARBASSAULT_ARENANEWB, 11);
        unlock(bits, profile, PermanentUnlock.MUSEUM_KUDOS_153, VarbitID.VM_KUDOS, 153);
        unlock(bits, profile, PermanentUnlock.BARBARIAN_FIREMAKING_TRAINING, VarbitID.BRUT_FIRE, 2);
        unlock(bits, profile, PermanentUnlock.FENKENSTRAIN_BRIDGE_NORTH, VarbitID.FENK_BUILT_BRIDGE_NORTH, 2);
        unlock(bits, profile, PermanentUnlock.FENKENSTRAIN_BRIDGE_SOUTH, VarbitID.FENK_BUILT_BRIDGE_SOUTH, 2);
        unlock(bits, profile, PermanentUnlock.KARAMJA_DUNGEON_BACKDOOR, VarbitID.KARAM_DUNGEON_BACKDOOR, 1);
        unlock(bits, profile, PermanentUnlock.OBSERVATORY_SHORTCUT_ROPE, VarbitID.OBSERVATORY_SHORTCUT_ROPE, 1);
        unlock(bits, profile, PermanentUnlock.HOSIDIUS_DUNGEON_WEST_DOOR, VarbitID.HOSDUN_WEST_DOOR_STATUS, 1);
        unlock(bits, profile, PermanentUnlock.HOSIDIUS_DUNGEON_EAST_DOOR, VarbitID.HOSDUN_EAST_DOOR_STATUS, 1);
        unlock(bits, profile, PermanentUnlock.DARKMEYER_INNER_SHORTCUT, VarbitID.DARKM_SHORTCUT_INNER, 1);
        unlock(bits, profile, PermanentUnlock.DARKMEYER_OUTER_SHORTCUT, VarbitID.DARKM_SHORTCUT_OUTER, 1);
        unlock(bits, profile, PermanentUnlock.MET_AUBURN_MOUNTAIN_GUIDE, VarbitID.MET_AUBURN_MOUNTAIN_GUIDE, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_NARDAH, VarbitID.BOOKOFSCROLLS_NARDAH, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_DIGSITE, VarbitID.BOOKOFSCROLLS_DIGSITE, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_FELDIP, VarbitID.BOOKOFSCROLLS_FELDIP, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_LUNAR_ISLE, VarbitID.BOOKOFSCROLLS_LUNARISLE, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_MORTTON, VarbitID.BOOKOFSCROLLS_MORTTON, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_PEST_CONTROL, VarbitID.BOOKOFSCROLLS_PESTCONTROL, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_PISCATORIS, VarbitID.BOOKOFSCROLLS_PISCATORIS, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_TAI_BWO, VarbitID.BOOKOFSCROLLS_TAIBWO, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_ELF, VarbitID.BOOKOFSCROLLS_ELF, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_MOS_LE_HARMLESS, VarbitID.BOOKOFSCROLLS_MOSLES, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_LUMBERYARD, VarbitID.BOOKOFSCROLLS_LUMBERYARD, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_ZUL_ANDRA, VarbitID.BOOKOFSCROLLS_ZULANDRA, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_CERBERUS, VarbitID.BOOKOFSCROLLS_CERBERUS, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_REVENANTS, VarbitID.BOOKOFSCROLLS_REVENANTS, 1);
        unlock(bits, profile, PermanentUnlock.BOOK_OF_SCROLLS_WATSON, VarbitID.BOOKOFSCROLLS_WATSON_LOWBITS, 1);
        unlock(bits, profile, PermanentUnlock.PENDANT_OF_ATES_DARKFROST, VarbitID.PENDANT_OF_ATES_DARKFROST_FOUND, 1);
        unlock(bits, profile, PermanentUnlock.PENDANT_OF_ATES_TWILIGHT, VarbitID.PENDANT_OF_ATES_TWILIGHT_FOUND, 1);
        unlock(bits, profile, PermanentUnlock.PENDANT_OF_ATES_RALOS, VarbitID.PENDANT_OF_ATES_RALOS_FOUND, 1);
        unlock(bits, profile, PermanentUnlock.PENDANT_OF_ATES_ALDARIN, VarbitID.PENDANT_OF_ATES_ALDARIN_FOUND, 1);
        unlock(bits, profile, PermanentUnlock.PHARAOHS_SCEPTRE_NECROPOLIS, VarbitID.PHARAOHS_SCEPTRE_NECROPOLIS, 1);
        unlock(bits, profile, PermanentUnlock.COLOSSEUM_WAVE_NINE, VarbitID.COLOSSEUM_HIGHEST_WAVE, 9);
        unlock(bits, profile, PermanentUnlock.ROWBOAT_VATRACHOS, VarbitID.AMENITY_ROWBOAT_VATRACHOS, 1);
        unlock(bits, profile, PermanentUnlock.ROWBOAT_ANGLERS, VarbitID.AMENITY_ROWBOAT_ANGLERS, 1);
        unlock(bits, profile, PermanentUnlock.ROWBOAT_SOUL_TEAR, VarbitID.AMENITY_ROWBOAT_SOUL_TEAR, 1);
        unlock(bits, profile, PermanentUnlock.ROWBOAT_YNYSDAIL, VarbitID.AMENITY_ROWBOAT_YNYSDAIL, 1);
        unlock(bits, profile, PermanentUnlock.ROWBOAT_BUCCANEERS, VarbitID.AMENITY_ROWBOAT_BUCCANEERS, 1);
        unlock(bits, profile, PermanentUnlock.RESPAWN_FALADOR, VarbitID.FALADOR_SPAWN, 1);
        unlock(bits, profile, PermanentUnlock.RESPAWN_CAMELOT, VarbitID.CAMELOT_SPAWN, 1);
        unlock(bits, profile, PermanentUnlock.RESPAWN_EDGEVILLE, VarbitID.EDGEVILLE_SPAWN, 1);
        unlock(bits, profile, PermanentUnlock.RESPAWN_FEROX_ENCLAVE, VarbitID.WILDERNESS_SPAWN, 1);
        unlock(bits, profile, PermanentUnlock.RESPAWN_KOUREND_CASTLE, VarbitID.KOUREND_SPAWN, 1);
        unlock(bits, profile, PermanentUnlock.RESPAWN_CIVITAS_ILLA_FORTIS, VarbitID.CIVITAS_SPAWN, 1);
    }

    private static void unlock(Map<Integer, Integer> bits, ProfileSpec profile, PermanentUnlock unlock,
                               int id, int value) {
        assign(bits, id, profile.permanentUnlocks().contains(unlock) ? value : 0,
            String.valueOf(id), "unlock " + unlock);
    }

    private static void compileBalloonBits(Map<Integer, Integer> bits, ProfileSpec profile) {
        balloon(bits, profile, HotAirBalloonDestination.ENTRANA, VarbitID.ZEP_MULTI_BASKET, 2);
        balloon(bits, profile, HotAirBalloonDestination.TAVERLEY, VarbitID.ZEP_MULTI_PICCARD, 2);
        balloon(bits, profile, HotAirBalloonDestination.CASTLE_WARS, VarbitID.ZEP_MULTI_CAST, 1);
        balloon(bits, profile, HotAirBalloonDestination.GRAND_TREE, VarbitID.ZEP_MULTI_GNO, 1);
        balloon(bits, profile, HotAirBalloonDestination.CRAFTING_GUILD, VarbitID.ZEP_MULTI_CRAFT, 1);
        balloon(bits, profile, HotAirBalloonDestination.VARROCK, VarbitID.ZEP_MULTI_VARR, 1);
    }

    private static void balloon(Map<Integer, Integer> bits, ProfileSpec profile,
                                HotAirBalloonDestination destination, int id, int value) {
        assign(bits, id, profile.hotAirBalloonDestinations().contains(destination) ? value : 0,
            String.valueOf(id), "balloon " + destination);
    }

    private static void compileCatacombsBits(Map<Integer, Integer> bits, ProfileSpec profile) {
        assign(bits, VarbitID.CATA_HOLE1, profile.catacombsEntrances().contains(CatacombsEntrance.FORTHOS_DUNGEON) ? 1 : 0,
            "CATA_HOLE1", "Catacombs Forthos entrance");
        assign(bits, VarbitID.CATA_HOLE2, profile.catacombsEntrances().contains(CatacombsEntrance.SURFACE_ENTRANCES) ? 1 : 0,
            "CATA_HOLE2", "Catacombs surface entrance");
        assign(bits, VarbitID.CATA_HOLE_GIANTS_DEN, profile.catacombsEntrances().contains(CatacombsEntrance.GIANTS_DEN) ? 1 : 0,
            "CATA_HOLE_GIANTS_DEN", "Catacombs Giants' Den entrance");
    }

    private static void compileQuetzal(Map<Integer, Integer> players, ProfileSpec profile) {
        int mask = 0;
        if (profile.quetzalPlatforms().contains(QuetzalPlatform.CAM_TORUM)) mask |= 32;
        if (profile.quetzalPlatforms().contains(QuetzalPlatform.COLOSSAL_WYRM_REMAINS)) mask |= 64;
        if (profile.quetzalPlatforms().contains(QuetzalPlatform.OUTER_FORTIS)) mask |= 128;
        if (profile.quetzalPlatforms().contains(QuetzalPlatform.FORTIS_COLOSSEUM)) mask |= 256;
        if (profile.quetzalPlatforms().contains(QuetzalPlatform.SALVAGER_OVERLOOK)) mask |= 2048;
        if (profile.quetzalPlatforms().contains(QuetzalPlatform.KASTORI)) mask |= 16384;
        assign(players, VarPlayerID.QUETZALS_UNLOCKED, mask, "QUETZALS_UNLOCKED", "Quetzal platforms");
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
        return profile.completedQuests().contains(quest) ? value : 0;
    }

    private static int player(ProfileSpec profile, Quest quest, int value) { return done(profile, quest, value); }

    private static int done(ProfileSpec profile, CorpusQuest quest, int value) {
        return profile.completedCorpusQuests().contains(quest) ? value : 0;
    }

    static void compileDiary(Map<Integer, Integer> bits, ProfileSpec profile) {
        for (Diary diary : Diary.values()) {
            DiaryTier tier = profile.diaries().getOrDefault(diary, DiaryTier.NONE);
            compileDiary(bits, diary, tier);
        }
    }

    private static void compileDiary(Map<Integer, Integer> bits, Diary diary, DiaryTier tier) {
        switch (diary) {
            case ARDOUGNE: diary(bits, tier, "ARDOUGNE", VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE, VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE); break;
            case DESERT: diary(bits, tier, "DESERT", VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE, VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE); break;
            case FALADOR: diary(bits, tier, "FALADOR", VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE, VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE); break;
            case FREMENNIK: diary(bits, tier, "FREMENNIK", VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE, VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE); break;
            case KANDARIN: diary(bits, tier, "KANDARIN", VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE); break;
            case KARAMJA: diary(bits, tier, "KARAMJA", VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE, VarbitID.ATJUN_HARD_DONE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE); break;
            case KOUREND_KEBOS: diary(bits, tier, "KOUREND_KEBOS", VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE, VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE); break;
            case LUMBRIDGE_DRAYNOR: diary(bits, tier, "LUMBRIDGE_DRAYNOR", VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE, VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE); break;
            case MORYTANIA: diary(bits, tier, "MORYTANIA", VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE, VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE); break;
            case VARROCK: diary(bits, tier, "VARROCK", VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE); break;
            case WESTERN_PROVINCES: diary(bits, tier, "WESTERN_PROVINCES", VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE, VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE); break;
            case WILDERNESS: diary(bits, tier, "WILDERNESS", VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE); break;
            default: throw new AssertionError(diary);
        }
    }

    private static void diary(Map<Integer, Integer> bits, DiaryTier tier, String source,
                              int easy, int medium, int hard, int elite) {
        assign(bits, easy, tier.ordinal() > 0 ? 1 : 0, source + " easy", "diary " + source);
        assign(bits, medium, tier.ordinal() > 1 ? 1 : 0, source + " medium", "diary " + source);
        assign(bits, hard, tier.ordinal() > 2 ? 1 : 0, source + " hard", "diary " + source);
        assign(bits, elite, tier.ordinal() > 3 ? 1 : 0, source + " elite", "diary " + source);
    }

    static void assign(Map<Integer, Integer> target, int id, int value, String symbol, String source) {
        Integer existing = target.get(id);
        if (existing != null && existing != value) {
            throw new VariableConflictException("Conflicting routing variable " + symbol + " (" + id + "): existing="
                + existing + ", " + source + " -> " + value);
        }
        target.put(id, value);
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
