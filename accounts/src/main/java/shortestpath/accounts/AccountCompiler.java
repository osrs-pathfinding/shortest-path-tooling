package shortestpath.accounts;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Compiles an {@link Account} into the {@link ClientState} the plugin reads. This is the only place
 * an account's facts become varbits and varplayers.
 *
 * <p>Every variable a fact controls is set, to 0 when the fact is absent (a quest not finished, a
 * diary tier not reached, an unlock not unlocked), so an account always describes them completely.
 * The account's raw variables are applied last and override them; tests use that for in-between
 * states no fact describes, such as a quest half done.
 */
public final class AccountCompiler {
    private AccountCompiler() { }

    /** A quest's progress variable and its value once the quest is finished. */
    private static final class QuestVariable {
        final Quest quest;
        final boolean varplayer;
        final int id;
        final int finished;

        QuestVariable(Quest quest, boolean varplayer, int id, int finished) {
            this.quest = quest;
            this.varplayer = varplayer;
            this.id = id;
            this.finished = finished;
        }
    }

    private static final List<QuestVariable> QUEST_VARIABLES = List.of(
        varbit(Quest.THE_FORSAKEN_TOWER, VarbitID.LOVAQUEST, 11),
        varbit(Quest.THE_FORSAKEN_TOWER, VarbitID.LOVAQUEST_REWARD, 1),
        varbit(Quest.MAKING_FRIENDS_WITH_MY_ARM, VarbitID.MY2ARM_STATUS, 207),
        varbit(Quest.ZOGRE_FLESH_EATERS, VarbitID.THZFE_BLOCKING_BARRICADE, 1),
        varbit(Quest.ZOGRE_FLESH_EATERS, VarbitID.ZOGRE, 14),
        varbit(Quest.THE_DEPTHS_OF_DESPAIR, VarbitID.HOSIDIUSQUEST, 7),
        varbit(Quest.THE_DEPTHS_OF_DESPAIR, VarbitID.HOSIDIUSQUEST_REWARD, 1),
        varbit(Quest.DRAGON_SLAYER_I, VarbitID.DRAGONSLAYER_CRANDOR_FOUND_SECRET_DOOR, 1),
        varbit(Quest.DARKNESS_OF_HALLOWVALE, VarbitID.MYQ3_MAIN_QUEST, 320),
        varbit(Quest.MOUNTAIN_DAUGHTER, VarbitID.MDAUGHTER_QUEST_VAR, 70),
        varbit(Quest.BETWEEN_A_ROCK, VarbitID.DWARFROCK_QUEST, 10),
        varbit(Quest.THE_GOLEM, VarbitID.GOLEM_A, 10),
        varbit(Quest.ICTHLARINS_LITTLE_HELPER, VarbitID.ICS_LITTLE_VAR, 26),
        varbit(Quest.TEARS_OF_GUTHIX, VarbitID.TOG_JUNA_BOWL, 2),
        varbit(Quest.THE_LOST_TRIBE, VarbitID.LOST_TRIBE_QUEST, 12),
        varbit(Quest.SWAN_SONG, VarbitID.SWANSONG, 200),
        varbit(Quest.THE_FREMENNIK_ISLES, VarbitID.FRIS_QUEST, 340),
        varbit(Quest.CLIENT_OF_KOUREND, VarbitID.VEOS_PROGRESS, 1),
        varbit(Quest.THE_QUEEN_OF_THIEVES, VarbitID.PISCQUEST_REWARD, 1),
        varbit(Quest.BENEATH_CURSED_SANDS, VarbitID.BCS, 108),
        varplayer(Quest.LEGENDS_QUEST, VarPlayerID.LEGENDSQUEST, 75),
        varplayer(Quest.SHILO_VILLAGE, VarPlayerID.ZOMBIEQUEEN, 15),
        varplayer(Quest.WATERFALL_QUEST, VarPlayerID.WATERFALL_QUEST, 10),
        varplayer(Quest.FISHING_CONTEST, VarPlayerID.FISHINGCOMPO, 5),
        varplayer(Quest.TREE_GNOME_VILLAGE, VarPlayerID.TREEQUEST, 9),
        varplayer(Quest.THE_GRAND_TREE, VarPlayerID.GRANDTREE, 160),
        varplayer(Quest.PLAGUE_CITY, VarPlayerID.ELENAQUEST, 30),
        varplayer(Quest.DRAGON_SLAYER_I, VarPlayerID.DRAGONQUEST, 10),
        varplayer(Quest.WATCHTOWER, VarPlayerID.ITWATCHTOWER, 14),
        varplayer(Quest.REGICIDE, VarPlayerID.REGICIDE_QUEST, 15),
        varplayer(Quest.THRONE_OF_MISCELLANIA, VarPlayerID.MISC_QUEST, 100),
        varplayer(Quest.MOURNINGS_END_PART_I, VarPlayerID.MOURNING_QUEST, 9));

    /** Each region's easy, medium, hard and elite completion varbits. */
    private static final Map<Diary, int[]> DIARY_VARBITS = Map.ofEntries(
        Map.entry(Diary.ARDOUGNE, new int[] {VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE, VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE}),
        Map.entry(Diary.DESERT, new int[] {VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE, VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE}),
        Map.entry(Diary.FALADOR, new int[] {VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE, VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE}),
        Map.entry(Diary.FREMENNIK, new int[] {VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE, VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE}),
        Map.entry(Diary.KANDARIN, new int[] {VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE}),
        Map.entry(Diary.KARAMJA, new int[] {VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE, VarbitID.ATJUN_HARD_DONE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE}),
        Map.entry(Diary.KOUREND_KEBOS, new int[] {VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE, VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE}),
        Map.entry(Diary.LUMBRIDGE_DRAYNOR, new int[] {VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE, VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE}),
        Map.entry(Diary.MORYTANIA, new int[] {VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE, VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE}),
        Map.entry(Diary.VARROCK, new int[] {VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE}),
        Map.entry(Diary.WESTERN_PROVINCES, new int[] {VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE, VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE}),
        Map.entry(Diary.WILDERNESS, new int[] {VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE}));

    /** How long ago a "ready" minigame teleport was last used: just past its 20-minute cooldown. */
    private static final int MINIGAME_TELEPORT_READY_MINUTES_AGO = 21;

    public static ClientState compile(Account account) {
        Map<Integer, Integer> varbits = new LinkedHashMap<>();
        Map<Integer, Integer> varplayers = new LinkedHashMap<>();

        for (QuestVariable variable : QUEST_VARIABLES) {
            int value = account.questState(variable.quest) == QuestState.FINISHED ? variable.finished : 0;
            (variable.varplayer ? varplayers : varbits).put(variable.id, value);
        }
        for (Diary diary : Diary.values()) {
            int[] tiers = DIARY_VARBITS.get(diary);
            int reached = account.diary(diary).ordinal();
            for (int tier = 0; tier < tiers.length; tier++) {
                varbits.put(tiers[tier], reached > tier ? 1 : 0);
            }
        }
        int quetzalPlatforms = 0;
        for (Unlock unlock : Unlock.values()) {
            boolean unlocked = account.unlocks().contains(unlock);
            if (unlock.kind == Unlock.Kind.VARPLAYER_BIT) {
                quetzalPlatforms |= unlocked ? unlock.value : 0;
            } else {
                varbits.put(unlock.id, unlocked ? unlock.value : 0);
            }
        }
        varplayers.put(VarPlayerID.QUETZALS_UNLOCKED, quetzalPlatforms);
        varbits.put(VarbitID.SPELLBOOK, account.spellbook().ordinal());
        if (account.poh() != null) {
            varbits.put(VarbitID.POH_HOUSE_LOCATION, account.poh().location.ordinal() + 1);
        }
        varplayers.put(VarPlayerID.SLUG2_REGIONUID, minigameTeleportUsedAt(account));
        varplayers.put(VarPlayerID.QP, account.questPoints());

        varbits.putAll(account.varbits());
        varplayers.putAll(account.varplayers());

        Map<Integer, Integer> inventory = account.inventory();
        if (!account.runePouch().isEmpty()) {
            inventory = inventory == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inventory);
            for (Map.Entry<Integer, Integer> rune : account.runePouch().entrySet()) {
                inventory.merge(rune.getKey(), rune.getValue(), Math::addExact);
            }
        }
        Set<String> trees = null;
        if (account.plantedSpiritTrees() != null) {
            trees = new LinkedHashSet<>();
            for (PlantedSpiritTree tree : account.plantedSpiritTrees()) {
                trees.add(tree.pluginName);
            }
        }
        int totalLevel = account.totalLevel() != null ? account.totalLevel()
            : account.levels().values().stream().mapToInt(Integer::intValue).sum();

        return new ClientState(account.levels(), account.defaultLevel(), totalLevel,
            varbits, varplayers, account.quests(), account.defaultQuestState(),
            inventory, account.equipment(), account.bank(), account.worldTypes(), account.location(),
            account.nowMinutes(), trees);
    }

    private static int minigameTeleportUsedAt(Account account) {
        if (account.minigameTeleportUsedAt() != null) {
            return Math.toIntExact(account.minigameTeleportUsedAt());
        }
        // Ready: used just over the cooldown ago, or at time 0 when the clock is the wall clock.
        return account.nowMinutes() == null ? 0
            : Math.toIntExact(account.nowMinutes() - MINIGAME_TELEPORT_READY_MINUTES_AGO);
    }

    private static QuestVariable varbit(Quest quest, int id, int finished) {
        return new QuestVariable(quest, false, id, finished);
    }

    private static QuestVariable varplayer(Quest quest, int id, int finished) {
        return new QuestVariable(quest, true, id, finished);
    }
}
