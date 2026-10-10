package shortestpath.accounts.canonical;

import static java.util.Map.entry;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import shortestpath.accounts.Account;
import shortestpath.accounts.Diary;
import shortestpath.accounts.PlantedSpiritTree;
import shortestpath.accounts.Poh;
import shortestpath.accounts.Unlock;

/**
 * The four canonical benchmark accounts, {@code early}, {@code mid}, {@code end} and {@code maxed}.
 * Every profile-specific fact is declared here.
 */
public final class CanonicalAccounts {
    public static final List<String> NAMES = List.of("early", "mid", "end", "maxed");

    /** The game time every canonical profile is evaluated at, in minutes. */
    public static final long NOW_MINUTES = 100_000_000;

    private static final List<String> PROGRESS_PORTALS = List.of("Ardougne Portal", "Barrows Portal",
        "Camelot Portal", "Falador Portal", "Kourend Portal", "Varrock Portal");

    private static final Map<String, Supplier<Account>> ACCOUNTS = Map.of(
        "early", CanonicalAccounts::early,
        "mid", CanonicalAccounts::mid,
        "end", CanonicalAccounts::end,
        "maxed", CanonicalAccounts::maxed);

    private CanonicalAccounts() { }

    public static Account account(String name) {
        Supplier<Account> account = ACCOUNTS.get(name);
        if (account == null) {
            throw new IllegalArgumentException("unknown canonical profile: " + name);
        }
        return account.get();
    }

    static Account early() {
        return items(base(earlyLevels(), earlyQuests()), CanonicalItems.earlyInventory(), CanonicalItems.earlyBank())
            .diaries(Diary.Tier.MEDIUM)
            .poh(new Poh(Poh.Location.RIMMINGTON, false, false, false, Poh.JewelleryBox.NONE,
                false, false, false, false, List.of()))
            .unlock(Unlock.FAIRY_RINGS, Unlock.CORSAIR_COVE_RESOURCE_AREA, Unlock.DWARVEN_MINE_CREVICE)
            .plantedSpiritTrees()
            .build();
    }

    static Account mid() {
        return items(base(midLevels(), progressedQuests()), CanonicalItems.midInventory(), CanonicalItems.midBank())
            .diaries(Diary.Tier.HARD)
            .poh(new Poh(Poh.Location.RIMMINGTON, false, false, false, Poh.JewelleryBox.FANCY,
                true, true, true, true, PROGRESS_PORTALS))
            .unlock(Unlock.values())
            .plantedSpiritTrees(PlantedSpiritTree.FARMING_GUILD)
            .build();
    }

    static Account end() {
        return items(base(endLevels(), EnumSet.allOf(Quest.class)), CanonicalItems.endInventory(), CanonicalItems.endBank())
            .diaries(Diary.Tier.HARD).diary(Diary.LUMBRIDGE_DRAYNOR, Diary.Tier.ELITE)
            .poh(new Poh(Poh.Location.RIMMINGTON, true, true, true, Poh.JewelleryBox.ORNATE,
                true, true, true, true, null))
            .unlock(Unlock.values())
            .plantedSpiritTrees(PlantedSpiritTree.FARMING_GUILD, PlantedSpiritTree.PORT_SARIM)
            .questPoints(327)
            .build();
    }

    static Account maxed() {
        return items(base(skills(99), EnumSet.allOf(Quest.class)), CanonicalItems.maxedInventory(), CanonicalItems.maxedBank())
            .diaries(Diary.Tier.ELITE)
            .poh(new Poh(Poh.Location.RIMMINGTON, true, true, true, Poh.JewelleryBox.ORNATE,
                true, true, true, true, null))
            .unlock(Unlock.values())
            .plantedSpiritTrees(PlantedSpiritTree.values())
            .questPoints(327)
            .totalLevel(2376)
            .build();
    }

    private static Account.Builder base(Map<Skill, Integer> levels, EnumSet<Quest> finished) {
        Account.Builder account = Account.builder()
            .nowMinutes(NOW_MINUTES)
            .inventoryContainer().equipmentContainer().bankContainer();
        levels.forEach(account::level);
        for (Quest quest : finished) {
            account.quest(quest, QuestState.FINISHED);
        }
        return account;
    }

    private static Account.Builder items(Account.Builder account, Map<Integer, Integer> inventory,
            Map<Integer, Integer> bank) {
        inventory.forEach(account::inventory);
        bank.forEach(account::bank);
        CanonicalItems.runePouch().forEach(account::runePouch);
        return account;
    }

    private static Map<Skill, Integer> earlyLevels() {
        return with(skills(70), Map.ofEntries(entry(Skill.CONSTRUCTION, 60), entry(Skill.CRAFTING, 65),
            entry(Skill.FARMING, 65), entry(Skill.FISHING, 65), entry(Skill.FLETCHING, 65),
            entry(Skill.HERBLORE, 65), entry(Skill.HITPOINTS, 75), entry(Skill.HUNTER, 65),
            entry(Skill.MINING, 65), entry(Skill.PRAYER, 60), entry(Skill.RUNECRAFT, 60),
            entry(Skill.SAILING, 60), entry(Skill.SLAYER, 65), entry(Skill.SMITHING, 65),
            entry(Skill.STRENGTH, 75), entry(Skill.THIEVING, 65), entry(Skill.WOODCUTTING, 65)));
    }

    private static Map<Skill, Integer> midLevels() {
        return with(skills(80), Map.ofEntries(entry(Skill.CONSTRUCTION, 78), entry(Skill.HERBLORE, 78),
            entry(Skill.HITPOINTS, 85), entry(Skill.MAGIC, 85), entry(Skill.PRAYER, 70),
            entry(Skill.RUNECRAFT, 75), entry(Skill.STRENGTH, 85), entry(Skill.FARMING, 83),
            entry(Skill.THIEVING, 82), entry(Skill.SAILING, 75)));
    }

    private static Map<Skill, Integer> endLevels() {
        return with(skills(90), Map.ofEntries(entry(Skill.CONSTRUCTION, 85), entry(Skill.COOKING, 95),
            entry(Skill.FARMING, 91), entry(Skill.HITPOINTS, 95), entry(Skill.MAGIC, 94), entry(Skill.MINING, 85),
            entry(Skill.PRAYER, 85), entry(Skill.RUNECRAFT, 85), entry(Skill.SAILING, 85), entry(Skill.SLAYER, 95),
            entry(Skill.SMITHING, 91), entry(Skill.STRENGTH, 95), entry(Skill.THIEVING, 91)));
    }

    private static EnumSet<Quest> earlyQuests() {
        return EnumSet.of(Quest.ANOTHER_SLICE_OF_HAM, Quest.BIOHAZARD, Quest.BONE_VOYAGE,
            Quest.CHILDREN_OF_THE_SUN, Quest.CLIENT_OF_KOUREND, Quest.CREATURE_OF_FENKENSTRAIN,
            Quest.DEATH_TO_THE_DORGESHUUN, Quest.DRAGON_SLAYER_I, Quest.ENTER_THE_ABYSS,
            Quest.GARDEN_OF_TRANQUILLITY, Quest.HAUNTED_MINE, Quest.HOLY_GRAIL,
            Quest.IN_SEARCH_OF_THE_MYREQUE, Quest.LOST_CITY, Quest.MONKEY_MADNESS_I,
            Quest.NATURE_SPIRIT, Quest.OBSERVATORY_QUEST, Quest.PLAGUE_CITY, Quest.PRIEST_IN_PERIL,
            Quest.REGICIDE, Quest.SEA_SLUG, Quest.SHADES_OF_MORTTON, Quest.TAI_BWO_WANNAI_TRIO,
            Quest.THE_CORSAIR_CURSE, Quest.THE_FREMENNIK_TRIALS, Quest.THE_GIANT_DWARF,
            Quest.THE_GRAND_TREE, Quest.THE_LOST_TRIBE, Quest.TREE_GNOME_VILLAGE,
            Quest.TWILIGHTS_PROMISE, Quest.WATCHTOWER);
    }

    private static EnumSet<Quest> progressedQuests() {
        return EnumSet.of(Quest.ANOTHER_SLICE_OF_HAM,
            Quest.BENEATH_CURSED_SANDS, Quest.BETWEEN_A_ROCK, Quest.BIOHAZARD, Quest.BONE_VOYAGE,
            Quest.CABIN_FEVER, Quest.CHILDREN_OF_THE_SUN, Quest.CLIENT_OF_KOUREND,
            Quest.CREATURE_OF_FENKENSTRAIN, Quest.DARKNESS_OF_HALLOWVALE,
            Quest.DEATH_TO_THE_DORGESHUUN, Quest.DRAGON_SLAYER_I, Quest.ENLIGHTENED_JOURNEY,
            Quest.ENTER_THE_ABYSS, Quest.FISHING_CONTEST, Quest.GARDEN_OF_TRANQUILLITY,
            Quest.HAUNTED_MINE, Quest.HOLY_GRAIL, Quest.ICTHLARINS_LITTLE_HELPER,
            Quest.IN_SEARCH_OF_THE_MYREQUE, Quest.LAND_OF_THE_GOBLINS, Quest.LEGENDS_QUEST,
            Quest.LOST_CITY, Quest.MAKING_FRIENDS_WITH_MY_ARM, Quest.MONKEY_MADNESS_I,
            Quest.MOUNTAIN_DAUGHTER, Quest.MOURNINGS_END_PART_I, Quest.NATURE_SPIRIT,
            Quest.OBSERVATORY_QUEST, Quest.PLAGUE_CITY, Quest.PRIEST_IN_PERIL, Quest.REGICIDE,
            Quest.SEA_SLUG, Quest.SHADES_OF_MORTTON, Quest.SHILO_VILLAGE, Quest.SINS_OF_THE_FATHER,
            Quest.SONG_OF_THE_ELVES, Quest.SWAN_SONG, Quest.TAI_BWO_WANNAI_TRIO,
            Quest.TEARS_OF_GUTHIX, Quest.THE_CORSAIR_CURSE, Quest.THE_DEPTHS_OF_DESPAIR,
            Quest.THE_FORSAKEN_TOWER, Quest.THE_FREMENNIK_ISLES, Quest.THE_FREMENNIK_TRIALS,
            Quest.THE_GIANT_DWARF, Quest.THE_GOLEM, Quest.THE_GRAND_TREE, Quest.THE_LOST_TRIBE,
            Quest.THE_PATH_OF_GLOUPHRIE, Quest.THE_QUEEN_OF_THIEVES,
            Quest.THRONE_OF_MISCELLANIA, Quest.TREE_GNOME_VILLAGE, Quest.TROUBLED_TORTUGANS,
            Quest.TWILIGHTS_PROMISE, Quest.WATCHTOWER, Quest.WATERFALL_QUEST,
            Quest.ZOGRE_FLESH_EATERS);
    }

    private static EnumSet<Quest> questCapeQuests() {
        return EnumSet.allOf(Quest.class);
    }

    private static Map<Skill, Integer> skills(int value) {
        Map<Skill, Integer> result = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) if (skill != Skill.OVERALL) result.put(skill, value);
        return result;
    }

    private static Map<Skill, Integer> with(Map<Skill, Integer> base, Map<Skill, Integer> changes) {
        base.putAll(changes);
        return base;
    }
}
