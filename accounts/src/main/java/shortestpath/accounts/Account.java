package shortestpath.accounts;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;

/**
 * The game state a route is planned for: what the RuneLite {@code Client} reports plus the few
 * facts the plugin reads from its own state (bank, planted spirit trees, POH, clock).
 *
 * <p>Profiles build one with {@link #builder()}; scenarios override single facts with
 * {@link #toBuilder()}. Everything not set reads as the RuneLite default: varbits and varplayers
 * 0, quests {@link #defaultQuestState()}, skills {@link #defaultLevel()}.
 */
public final class Account {
    private final Map<Skill, Integer> levels;
    private final int defaultLevel;
    private final Integer totalLevel;
    private final Map<Integer, Integer> varbits;
    private final Map<Integer, Integer> varplayers;
    private final Map<Quest, QuestState> questStates;
    private final QuestState defaultQuestState;
    private final Map<Integer, Integer> inventory;
    private final Map<Integer, Integer> equipment;
    private final Map<Integer, Integer> bank;
    private final Set<WorldType> worldTypes;
    private final Location location;
    private final Long nowMinutes;
    private final Set<String> plantedSpiritTrees;
    private final Poh poh;

    private Account(Builder builder) {
        levels = Collections.unmodifiableMap(new EnumMap<>(builder.levels));
        defaultLevel = builder.defaultLevel;
        totalLevel = builder.totalLevel;
        varbits = Collections.unmodifiableMap(new LinkedHashMap<>(builder.varbits));
        varplayers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.varplayers));
        questStates = Collections.unmodifiableMap(new EnumMap<>(builder.questStates));
        defaultQuestState = builder.defaultQuestState;
        inventory = copy(builder.inventory);
        equipment = copy(builder.equipment);
        bank = builder.bank == UNIVERSAL_BANK ? UNIVERSAL_BANK : copy(builder.bank);
        worldTypes = Collections.unmodifiableSet(builder.worldTypes.isEmpty()
            ? EnumSet.noneOf(WorldType.class) : EnumSet.copyOf(builder.worldTypes));
        location = builder.location;
        nowMinutes = builder.nowMinutes;
        plantedSpiritTrees = builder.plantedSpiritTrees == null ? null
            : Collections.unmodifiableSet(new LinkedHashSet<>(builder.plantedSpiritTrees));
        poh = builder.poh;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Boosted skill levels; skills not listed have {@link #defaultLevel()}. */
    public Map<Skill, Integer> levels() { return levels; }
    public int defaultLevel() { return defaultLevel; }
    public int level(Skill skill) { return levels.getOrDefault(skill, defaultLevel); }
    /** The reported total level: explicit, or the sum of the listed levels. */
    public int totalLevel() {
        if (totalLevel != null) {
            return totalLevel;
        }
        int total = 0;
        for (int level : levels.values()) {
            total = Math.addExact(total, level);
        }
        return total;
    }
    public Map<Integer, Integer> varbits() { return varbits; }
    public Map<Integer, Integer> varplayers() { return varplayers; }
    public Map<Quest, QuestState> questStates() { return questStates; }
    public QuestState defaultQuestState() { return defaultQuestState; }
    public QuestState questState(Quest quest) { return questStates.getOrDefault(quest, defaultQuestState); }
    /** Carried items by id; {@code null} means the client reports no inventory container. */
    public Map<Integer, Integer> inventory() { return inventory; }
    /** Worn items by id; {@code null} means the client reports no equipment container. */
    public Map<Integer, Integer> equipment() { return equipment; }
    /** Banked items by id; {@code null} means the bank has not been seen. */
    public Map<Integer, Integer> bank() { return bank; }
    public boolean hasUniversalBank() { return bank == UNIVERSAL_BANK; }
    public Set<WorldType> worldTypes() { return worldTypes; }
    /** Where the player stands; {@code null} means the client reports no local player. */
    public Location location() { return location; }
    /** Game time for timed requirements; {@code null} means the wall clock. */
    public Long nowMinutes() { return nowMinutes; }
    /** Planted spirit trees by plugin name; {@code null} leaves the plugin to detect them. */
    public Set<String> plantedSpiritTrees() { return plantedSpiritTrees; }
    /** POH facilities; {@code null} leaves the plugin settings unchanged. */
    public Poh poh() { return poh; }

    private static Map<Integer, Integer> copy(Map<Integer, Integer> items) {
        return items == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    /** Every item id below 25000, 1000 of each: a bank that never limits banked teleports. */
    private static final Map<Integer, Integer> UNIVERSAL_BANK;

    static {
        Map<Integer, Integer> all = new LinkedHashMap<>();
        for (int id = 0; id < 25000; id++) {
            all.put(id, 1000);
        }
        UNIVERSAL_BANK = Collections.unmodifiableMap(all);
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

    public enum JewelleryBox { NONE, FANCY, ORNATE }

    /** POH facilities, which the plugin takes from its settings rather than the client. */
    public static final class Poh {
        public final boolean fairyRing;
        public final boolean spiritTree;
        public final boolean obelisk;
        public final JewelleryBox jewelleryBox;
        public final boolean mountedGlory;
        public final boolean mountedXerics;
        public final boolean mountedDigsite;
        public final boolean mountedMythical;
        /** Nexus portal destinations by plugin display name; {@code null} means every portal. */
        public final List<String> portals;

        public Poh(boolean fairyRing, boolean spiritTree, boolean obelisk, JewelleryBox jewelleryBox,
                boolean mountedGlory, boolean mountedXerics, boolean mountedDigsite, boolean mountedMythical,
                List<String> portals) {
            this.fairyRing = fairyRing;
            this.spiritTree = spiritTree;
            this.obelisk = obelisk;
            this.jewelleryBox = jewelleryBox;
            this.mountedGlory = mountedGlory;
            this.mountedXerics = mountedXerics;
            this.mountedDigsite = mountedDigsite;
            this.mountedMythical = mountedMythical;
            this.portals = portals == null ? null : List.copyOf(portals);
        }
    }

    public static final class Builder {
        private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
        private int defaultLevel = 1;
        private Integer totalLevel;
        private final Map<Integer, Integer> varbits = new LinkedHashMap<>();
        private final Map<Integer, Integer> varplayers = new LinkedHashMap<>();
        private final Map<Quest, QuestState> questStates = new EnumMap<>(Quest.class);
        private QuestState defaultQuestState = QuestState.NOT_STARTED;
        private Map<Integer, Integer> inventory;
        private Map<Integer, Integer> equipment;
        private Map<Integer, Integer> bank;
        private final Set<WorldType> worldTypes = EnumSet.noneOf(WorldType.class);
        private Location location;
        private Long nowMinutes;
        private Set<String> plantedSpiritTrees;
        private Poh poh;

        private Builder() { }

        private Builder(Account account) {
            levels.putAll(account.levels);
            defaultLevel = account.defaultLevel;
            totalLevel = account.totalLevel;
            varbits.putAll(account.varbits);
            varplayers.putAll(account.varplayers);
            questStates.putAll(account.questStates);
            defaultQuestState = account.defaultQuestState;
            inventory = account.inventory == null ? null : new LinkedHashMap<>(account.inventory);
            equipment = account.equipment == null ? null : new LinkedHashMap<>(account.equipment);
            bank = account.bank == null || account.bank == UNIVERSAL_BANK ? account.bank
                : new LinkedHashMap<>(account.bank);
            worldTypes.addAll(account.worldTypes);
            location = account.location;
            nowMinutes = account.nowMinutes;
            plantedSpiritTrees = account.plantedSpiritTrees == null ? null
                : new LinkedHashSet<>(account.plantedSpiritTrees);
            poh = account.poh;
        }

        public Builder level(Skill skill, int level) { levels.put(skill, level); return this; }
        /** The level of every skill not set with {@link #level}. */
        public Builder defaultLevel(int level) { defaultLevel = level; return this; }
        public Builder totalLevel(int level) { totalLevel = level; return this; }
        public Builder varbit(int id, int value) { varbits.put(id, value); return this; }
        public Builder varplayer(int id, int value) { varplayers.put(id, value); return this; }
        public Builder quest(Quest quest, QuestState state) { questStates.put(quest, state); return this; }
        /** The state of every quest not set with {@link #quest}. */
        public Builder defaultQuestState(QuestState state) { defaultQuestState = state; return this; }

        /** Adds carried items; quantities of the same id add up. */
        public Builder inventory(int id, int quantity) {
            inventory = add(inventory, id, quantity);
            return this;
        }
        /** Adds worn items; quantities of the same id add up. */
        public Builder equipment(int id, int quantity) {
            equipment = add(equipment, id, quantity);
            return this;
        }
        /** Adds banked items; quantities of the same id add up. */
        public Builder bank(int id, int quantity) {
            if (bank == UNIVERSAL_BANK) {
                throw new IllegalStateException("the universal bank already holds every item");
            }
            bank = add(bank, id, quantity);
            return this;
        }
        /** Reports an inventory container even when nothing is added to it. */
        public Builder inventoryContainer() { inventory = inventory == null ? new LinkedHashMap<>() : inventory; return this; }
        /** Reports an equipment container even when nothing is added to it. */
        public Builder equipmentContainer() { equipment = equipment == null ? new LinkedHashMap<>() : equipment; return this; }
        /** Marks the bank as seen even when nothing is added to it. */
        public Builder bankContainer() { bank = bank == null ? new LinkedHashMap<>() : bank; return this; }
        public Builder universalBank() { bank = UNIVERSAL_BANK; return this; }

        public Builder world(WorldType type) { worldTypes.add(type); return this; }
        public Builder location(int x, int y, int plane) { location = new Location(x, y, plane); return this; }
        public Builder nowMinutes(long minutes) { nowMinutes = minutes; return this; }
        public Builder plantedSpiritTrees(Set<String> names) {
            plantedSpiritTrees = new LinkedHashSet<>(names);
            return this;
        }
        public Builder poh(Poh value) { poh = value; return this; }

        public Account build() {
            return new Account(this);
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
