package shortestpath.accounts;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;

/**
 * An account as a player would describe it: levels, quests, diaries, unlocks, items, house,
 * spellbook. Profiles, scenarios and the web planner all write accounts this way, and
 * {@link AccountCompiler} turns one into the {@link ClientState} the plugin reads.
 *
 * <p>Raw varbits and varplayers are for what no field here describes: league area picks, or a
 * test's in-between state such as a quest half done. They override what the facts imply.
 */
public final class Account {
    private final Map<Skill, Integer> levels;
    private final int defaultLevel;
    private final Integer totalLevel;
    private final int questPoints;
    private final Map<Quest, QuestState> quests;
    private final QuestState defaultQuestState;
    private final Map<Diary, Diary.Tier> diaries;
    private final Set<Unlock> unlocks;
    private final Spellbook spellbook;
    private final Long minigameTeleportUsedAt;
    private final Map<Integer, Integer> inventory;
    private final Map<Integer, Integer> runePouch;
    private final Map<Integer, Integer> equipment;
    private final Map<Integer, Integer> bank;
    private final Poh poh;
    private final Set<PlantedSpiritTree> plantedSpiritTrees;
    private final Set<WorldType> worldTypes;
    private final Location location;
    private final Long nowMinutes;
    private final Map<Integer, Integer> varbits;
    private final Map<Integer, Integer> varplayers;

    private Account(Builder builder) {
        levels = Collections.unmodifiableMap(new EnumMap<>(builder.levels));
        defaultLevel = builder.defaultLevel;
        totalLevel = builder.totalLevel;
        questPoints = builder.questPoints;
        quests = Collections.unmodifiableMap(new EnumMap<>(builder.quests));
        defaultQuestState = builder.defaultQuestState;
        diaries = Collections.unmodifiableMap(new EnumMap<>(builder.diaries));
        unlocks = Collections.unmodifiableSet(builder.unlocks.isEmpty()
            ? EnumSet.noneOf(Unlock.class) : EnumSet.copyOf(builder.unlocks));
        spellbook = builder.spellbook;
        minigameTeleportUsedAt = builder.minigameTeleportUsedAt;
        inventory = copy(builder.inventory);
        runePouch = copy(builder.runePouch);
        equipment = copy(builder.equipment);
        bank = copy(builder.bank);
        poh = builder.poh;
        plantedSpiritTrees = builder.plantedSpiritTrees == null ? null
            : Collections.unmodifiableSet(builder.plantedSpiritTrees.isEmpty()
                ? EnumSet.noneOf(PlantedSpiritTree.class) : EnumSet.copyOf(builder.plantedSpiritTrees));
        worldTypes = Collections.unmodifiableSet(builder.worldTypes.isEmpty()
            ? EnumSet.noneOf(WorldType.class) : EnumSet.copyOf(builder.worldTypes));
        location = builder.location;
        nowMinutes = builder.nowMinutes;
        varbits = Collections.unmodifiableMap(new LinkedHashMap<>(builder.varbits));
        varplayers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.varplayers));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Skill levels; skills not listed have {@link #defaultLevel()}. */
    public Map<Skill, Integer> levels() { return levels; }
    public int defaultLevel() { return defaultLevel; }
    public int level(Skill skill) { return levels.getOrDefault(skill, defaultLevel); }
    /** The reported total level when set; otherwise the sum of {@link #levels()}. */
    public Integer totalLevel() { return totalLevel; }
    public int questPoints() { return questPoints; }
    /** Quest states; quests not listed have {@link #defaultQuestState()}. */
    public Map<Quest, QuestState> quests() { return quests; }
    public QuestState defaultQuestState() { return defaultQuestState; }
    public QuestState questState(Quest quest) { return quests.getOrDefault(quest, defaultQuestState); }
    /** Diary tiers; regions not listed have {@link Diary.Tier#NONE}. */
    public Map<Diary, Diary.Tier> diaries() { return diaries; }
    public Diary.Tier diary(Diary diary) { return diaries.getOrDefault(diary, Diary.Tier.NONE); }
    public Set<Unlock> unlocks() { return unlocks; }
    public Spellbook spellbook() { return spellbook; }
    /** When a minigame teleport was last used, in game minutes; {@code null} means it is ready. */
    public Long minigameTeleportUsedAt() { return minigameTeleportUsedAt; }
    /** Carried items by id; {@code null} means the client reports no inventory container. */
    public Map<Integer, Integer> inventory() { return inventory; }
    /** Runes in a carried rune pouch, by id; they count as carried. */
    public Map<Integer, Integer> runePouch() { return runePouch; }
    /** Worn items by id; {@code null} means the client reports no equipment container. */
    public Map<Integer, Integer> equipment() { return equipment; }
    /** Banked items by id; {@code null} means the bank has not been seen. */
    public Map<Integer, Integer> bank() { return bank; }
    /** The player's house; {@code null} means none. */
    public Poh poh() { return poh; }
    /** {@code null} leaves the plugin to detect them. */
    public Set<PlantedSpiritTree> plantedSpiritTrees() { return plantedSpiritTrees; }
    public Set<WorldType> worldTypes() { return worldTypes; }
    /** Where the player stands; {@code null} means the client reports no local player. */
    public Location location() { return location; }
    /** Game time for timed requirements; {@code null} means the wall clock. */
    public Long nowMinutes() { return nowMinutes; }
    /** Raw varbits for facts with no field; see the class comment. */
    public Map<Integer, Integer> varbits() { return varbits; }
    /** Raw varplayers for facts with no field; see the class comment. */
    public Map<Integer, Integer> varplayers() { return varplayers; }

    private static Map<Integer, Integer> copy(Map<Integer, Integer> items) {
        return items == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    public static final class Location {
        public final int x;
        public final int y;
        public final int plane;

        public Location(int x, int y, int plane) {
            this.x = x;
            this.y = y;
            this.plane = plane;
        }
    }

    public static final class Builder {
        private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
        private int defaultLevel = 1;
        private Integer totalLevel;
        private int questPoints;
        private final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
        private QuestState defaultQuestState = QuestState.NOT_STARTED;
        private final Map<Diary, Diary.Tier> diaries = new EnumMap<>(Diary.class);
        private final Set<Unlock> unlocks = EnumSet.noneOf(Unlock.class);
        private Spellbook spellbook = Spellbook.STANDARD;
        private Long minigameTeleportUsedAt;
        private Map<Integer, Integer> inventory;
        private Map<Integer, Integer> runePouch = new LinkedHashMap<>();
        private Map<Integer, Integer> equipment;
        private Map<Integer, Integer> bank;
        private Poh poh;
        private Set<PlantedSpiritTree> plantedSpiritTrees;
        private final Set<WorldType> worldTypes = EnumSet.noneOf(WorldType.class);
        private Location location;
        private Long nowMinutes;
        private final Map<Integer, Integer> varbits = new LinkedHashMap<>();
        private final Map<Integer, Integer> varplayers = new LinkedHashMap<>();

        private Builder() { }

        private Builder(Account account) {
            levels.putAll(account.levels);
            defaultLevel = account.defaultLevel;
            totalLevel = account.totalLevel;
            questPoints = account.questPoints;
            quests.putAll(account.quests);
            defaultQuestState = account.defaultQuestState;
            diaries.putAll(account.diaries);
            unlocks.addAll(account.unlocks);
            spellbook = account.spellbook;
            minigameTeleportUsedAt = account.minigameTeleportUsedAt;
            inventory = mutable(account.inventory);
            runePouch = mutable(account.runePouch);
            equipment = mutable(account.equipment);
            bank = mutable(account.bank);
            poh = account.poh;
            plantedSpiritTrees = account.plantedSpiritTrees == null ? null
                : EnumSet.copyOf(account.plantedSpiritTrees.isEmpty()
                    ? EnumSet.noneOf(PlantedSpiritTree.class) : account.plantedSpiritTrees);
            worldTypes.addAll(account.worldTypes);
            location = account.location;
            nowMinutes = account.nowMinutes;
            varbits.putAll(account.varbits);
            varplayers.putAll(account.varplayers);
        }

        public Builder level(Skill skill, int level) { levels.put(skill, level); return this; }
        /** The level of every skill not set with {@link #level}. */
        public Builder defaultLevel(int level) { defaultLevel = level; return this; }
        public Builder totalLevel(int level) { totalLevel = level; return this; }
        public Builder questPoints(int value) { questPoints = value; return this; }
        public Builder quest(Quest quest, QuestState state) { quests.put(quest, state); return this; }
        /** The state of every quest not set with {@link #quest}. */
        public Builder defaultQuestState(QuestState state) { defaultQuestState = state; return this; }
        public Builder diary(Diary diary, Diary.Tier tier) { diaries.put(diary, tier); return this; }
        /** Sets every region's diary to {@code tier}. */
        public Builder diaries(Diary.Tier tier) {
            for (Diary diary : Diary.values()) {
                diaries.put(diary, tier);
            }
            return this;
        }
        public Builder unlock(Unlock... values) { unlocks.addAll(Set.of(values)); return this; }
        public Builder lock(Unlock... values) { unlocks.removeAll(Set.of(values)); return this; }
        public Builder spellbook(Spellbook value) { spellbook = value; return this; }
        public Builder minigameTeleportUsedAt(long minutes) { minigameTeleportUsedAt = minutes; return this; }

        /** Adds carried items; quantities of the same id add up. */
        public Builder inventory(int id, int quantity) { inventory = add(inventory, id, quantity); return this; }
        /** Adds runes to the rune pouch; quantities of the same id add up. */
        public Builder runePouch(int id, int quantity) { runePouch = add(runePouch, id, quantity); return this; }
        /** Adds worn items; quantities of the same id add up. */
        public Builder equipment(int id, int quantity) { equipment = add(equipment, id, quantity); return this; }
        /** Adds banked items; quantities of the same id add up. */
        public Builder bank(int id, int quantity) { bank = add(bank, id, quantity); return this; }
        /** Reports an inventory container even when nothing is added to it. */
        public Builder inventoryContainer() { inventory = inventory == null ? new LinkedHashMap<>() : inventory; return this; }
        /** Reports an equipment container even when nothing is added to it. */
        public Builder equipmentContainer() { equipment = equipment == null ? new LinkedHashMap<>() : equipment; return this; }
        /** Marks the bank as seen even when nothing is added to it. */
        public Builder bankContainer() { bank = bank == null ? new LinkedHashMap<>() : bank; return this; }

        public Builder poh(Poh value) { poh = value; return this; }
        public Builder plantedSpiritTrees(PlantedSpiritTree... trees) {
            plantedSpiritTrees = EnumSet.noneOf(PlantedSpiritTree.class);
            plantedSpiritTrees.addAll(Set.of(trees));
            return this;
        }
        public Builder world(WorldType type) { worldTypes.add(type); return this; }
        public Builder location(int x, int y, int plane) { location = new Location(x, y, plane); return this; }
        public Builder nowMinutes(long minutes) { nowMinutes = minutes; return this; }
        /** A raw varbit, for a fact with no field. */
        public Builder varbit(int id, int value) { varbits.put(id, value); return this; }
        /** A raw varplayer, for a fact with no field. */
        public Builder varplayer(int id, int value) { varplayers.put(id, value); return this; }

        public Account build() {
            return new Account(this);
        }

        private static Map<Integer, Integer> mutable(Map<Integer, Integer> items) {
            return items == null ? null : new LinkedHashMap<>(items);
        }

        private static Map<Integer, Integer> add(Map<Integer, Integer> items, int id, int quantity) {
            Map<Integer, Integer> result = items == null ? new LinkedHashMap<>() : items;
            try {
                result.put(id, Math.addExact(result.getOrDefault(id, 0), quantity));
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("item quantity overflow for item " + id, e);
            }
            return result;
        }
    }
}
