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
 * What the RuneLite client reports for an {@link Account}, plus the few facts the plugin keeps in
 * its own state (bank, planted spirit trees, clock). Only {@link AccountCompiler} makes one;
 * {@link HeadlessClient} answers the pathfinder's client calls from it.
 *
 * <p>Anything not set reads as the client's default: varbits and varplayers 0, quests
 * {@link #defaultQuestState()}, skills {@link #defaultLevel()}.
 */
public final class ClientState {
    private final Map<Skill, Integer> levels;
    private final int defaultLevel;
    private final int totalLevel;
    private final boolean reportsRealLevels;
    private final Map<Integer, Integer> varbits;
    private final Map<Integer, Integer> varplayers;
    private final Map<Quest, QuestState> questStates;
    private final QuestState defaultQuestState;
    private final Map<Integer, Integer> inventory;
    private final Map<Integer, Integer> equipment;
    private final Map<Integer, Integer> bank;
    private final Set<WorldType> worldTypes;
    private final Account.Location location;
    private final Long nowMinutes;
    private final Set<String> plantedSpiritTrees;

    ClientState(Map<Skill, Integer> levels, int defaultLevel, int totalLevel, boolean reportsRealLevels,
            Map<Integer, Integer> varbits, Map<Integer, Integer> varplayers,
            Map<Quest, QuestState> questStates, QuestState defaultQuestState,
            Map<Integer, Integer> inventory, Map<Integer, Integer> equipment, Map<Integer, Integer> bank,
            Set<WorldType> worldTypes, Account.Location location, Long nowMinutes, Set<String> plantedSpiritTrees) {
        this.levels = Collections.unmodifiableMap(copy(Skill.class, levels));
        this.defaultLevel = defaultLevel;
        this.totalLevel = totalLevel;
        this.reportsRealLevels = reportsRealLevels;
        this.varbits = Collections.unmodifiableMap(new LinkedHashMap<>(varbits));
        this.varplayers = Collections.unmodifiableMap(new LinkedHashMap<>(varplayers));
        this.questStates = Collections.unmodifiableMap(copy(Quest.class, questStates));
        this.defaultQuestState = defaultQuestState;
        this.inventory = copy(inventory);
        this.equipment = copy(equipment);
        this.bank = copy(bank);
        this.worldTypes = Collections.unmodifiableSet(worldTypes.isEmpty()
            ? EnumSet.noneOf(WorldType.class) : EnumSet.copyOf(worldTypes));
        this.location = location;
        this.nowMinutes = nowMinutes;
        this.plantedSpiritTrees = plantedSpiritTrees == null ? null : Collections.unmodifiableSet(plantedSpiritTrees);
    }

    private static <K extends Enum<K>, V> EnumMap<K, V> copy(Class<K> type, Map<K, V> values) {
        EnumMap<K, V> result = new EnumMap<>(type);
        result.putAll(values);
        return result;
    }

    private static Map<Integer, Integer> copy(Map<Integer, Integer> items) {
        return items == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    /** Skill levels; skills not listed have {@link #defaultLevel()}. */
    public Map<Skill, Integer> levels() { return levels; }
    public int defaultLevel() { return defaultLevel; }
    public int level(Skill skill) { return levels.getOrDefault(skill, defaultLevel); }
    public int totalLevel() { return totalLevel; }
    /**
     * Whether the client reports {@link #level} as the real skill level too. Otherwise it reports
     * 0, so the plugin computes combat level 3: the tooling profiles keep that for benchmark parity.
     */
    public boolean reportsRealLevels() { return reportsRealLevels; }
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
    public Set<WorldType> worldTypes() { return worldTypes; }
    /** Where the player stands; {@code null} means the client reports no local player. */
    public Account.Location location() { return location; }
    /** Game time for timed requirements; {@code null} means the wall clock. */
    public Long nowMinutes() { return nowMinutes; }
    /** Planted spirit trees by plugin name; {@code null} leaves the plugin to detect them. */
    public Set<String> plantedSpiritTrees() { return plantedSpiritTrees; }
}
