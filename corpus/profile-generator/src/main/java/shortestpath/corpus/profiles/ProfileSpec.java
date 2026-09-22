package shortestpath.corpus.profiles;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Small mutable authoring object. Profile names are identity metadata only. */
final class ProfileSpec {
    private final String name;
    private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
    private final Set<Quest> completedQuests = EnumSet.noneOf(Quest.class);
    private final Set<CorpusQuest> completedCorpusQuests = EnumSet.noneOf(CorpusQuest.class);
    private final Map<Diary, DiaryTier> diaries = new EnumMap<>(Diary.class);
    private final Map<Integer, Integer> inventory = new LinkedHashMap<>();
    private final Map<Integer, Integer> equipment = new LinkedHashMap<>();
    private final Map<Integer, Integer> runePouch = new LinkedHashMap<>();
    private final Map<Integer, Integer> bank = new LinkedHashMap<>();
    private final Set<PlantedSpiritTree> plantedSpiritTrees = EnumSet.noneOf(PlantedSpiritTree.class);
    private final Set<QuestMilestone> questMilestones = EnumSet.noneOf(QuestMilestone.class);
    private final Set<QuetzalPlatform> quetzalPlatforms = EnumSet.noneOf(QuetzalPlatform.class);
    private final Set<HotAirBalloonDestination> hotAirBalloonDestinations =
        EnumSet.noneOf(HotAirBalloonDestination.class);
    private final Set<CatacombsEntrance> catacombsEntrances = EnumSet.noneOf(CatacombsEntrance.class);
    private final Set<PermanentUnlock> permanentUnlocks = EnumSet.noneOf(PermanentUnlock.class);
    private final Map<Integer, Integer> routingVarbits = new LinkedHashMap<>();
    private final Map<Integer, Integer> routingVarplayers = new LinkedHashMap<>();
    private PohSpec poh = PohSpec.standard();
    private RuntimeSpec runtime = RuntimeSpec.ready();
    private boolean fairyRingsUnlocked;
    private boolean includeProgressionRouting;
    private Integer questPoints;
    private Integer totalLevel;

    ProfileSpec(String name) { this.name = name; }

    String name() { return name; }
    ProfileSpec renamed(String value) {
        ProfileSpec copy = new ProfileSpec(value)
            .levels(levels).quests(completedQuests.toArray(new Quest[0]))
            .corpusQuests(completedCorpusQuests.toArray(new CorpusQuest[0]))
            .diaries(diaries).inventory(inventory).equipment(equipment).runePouch(runePouch).bank(bank)
            .poh(poh).planted(plantedSpiritTrees.toArray(new PlantedSpiritTree[0]))
            .questMilestones(questMilestones.toArray(new QuestMilestone[0]))
            .quetzalPlatforms(quetzalPlatforms.toArray(new QuetzalPlatform[0]))
            .balloons(hotAirBalloonDestinations.toArray(new HotAirBalloonDestination[0]))
            .catacombs(catacombsEntrances.toArray(new CatacombsEntrance[0]))
            .unlocks(permanentUnlocks.toArray(new PermanentUnlock[0]))
            .routing(routingVarbits, routingVarplayers).fairyRings(fairyRingsUnlocked)
            .progressionRouting(includeProgressionRouting).runtime(runtime);
        if (questPoints != null) copy.questPoints(questPoints);
        if (totalLevel != null) copy.totalLevel(totalLevel);
        return copy;
    }

    ProfileSpec levels(Map<Skill, Integer> values) { levels.putAll(values); return this; }
    ProfileSpec quests(Quest... values) { for (Quest value : values) completedQuests.add(value); return this; }
    ProfileSpec corpusQuests(CorpusQuest... values) {
        for (CorpusQuest value : values) completedCorpusQuests.add(value);
        return this;
    }
    ProfileSpec diaries(Map<Diary, DiaryTier> values) { diaries.putAll(values); return this; }
    ProfileSpec diaries(DiaryTier tier) { for (Diary diary : Diary.values()) diaries.put(diary, tier); return this; }
    ProfileSpec diary(Diary diary, DiaryTier tier) { diaries.put(diary, tier); return this; }
    ProfileSpec inventory(Map<Integer, Integer> values) { inventory.putAll(values); return this; }
    ProfileSpec equipment(Map<Integer, Integer> values) { equipment.putAll(values); return this; }
    ProfileSpec runePouch(Map<Integer, Integer> values) { runePouch.putAll(values); return this; }
    ProfileSpec bank(Map<Integer, Integer> values) { bank.putAll(values); return this; }
    ProfileSpec poh(PohSpec value) { poh = value; return this; }
    ProfileSpec planted(PlantedSpiritTree... values) {
        for (PlantedSpiritTree value : values) plantedSpiritTrees.add(value);
        return this;
    }
    ProfileSpec questMilestones(QuestMilestone... values) {
        for (QuestMilestone value : values) questMilestones.add(value);
        return this;
    }
    ProfileSpec quetzalPlatforms(QuetzalPlatform... values) {
        for (QuetzalPlatform value : values) quetzalPlatforms.add(value);
        return this;
    }
    ProfileSpec balloons(HotAirBalloonDestination... values) {
        for (HotAirBalloonDestination value : values) hotAirBalloonDestinations.add(value);
        return this;
    }
    ProfileSpec catacombs(CatacombsEntrance... values) {
        for (CatacombsEntrance value : values) catacombsEntrances.add(value);
        return this;
    }
    ProfileSpec unlocks(PermanentUnlock... values) {
        for (PermanentUnlock value : values) permanentUnlocks.add(value);
        return this;
    }
    ProfileSpec routing(Map<Integer, Integer> varbits, Map<Integer, Integer> varplayers) {
        routingVarbits.putAll(varbits);
        routingVarplayers.putAll(varplayers);
        return this;
    }
    ProfileSpec fairyRings(boolean value) { fairyRingsUnlocked = value; return this; }
    ProfileSpec progressionRouting(boolean value) { includeProgressionRouting = value; return this; }
    ProfileSpec questPoints(int value) { questPoints = value; return this; }
    ProfileSpec totalLevel(int value) { totalLevel = value; return this; }
    ProfileSpec runtime(RuntimeSpec value) { runtime = value; return this; }

    Map<Skill, Integer> levels() { return Collections.unmodifiableMap(levels); }
    Set<Quest> completedQuests() { return Collections.unmodifiableSet(completedQuests); }
    Set<CorpusQuest> completedCorpusQuests() { return Collections.unmodifiableSet(completedCorpusQuests); }
    Map<Diary, DiaryTier> diaries() { return Collections.unmodifiableMap(diaries); }
    Map<Integer, Integer> inventory() { return Collections.unmodifiableMap(inventory); }
    Map<Integer, Integer> equipment() { return Collections.unmodifiableMap(equipment); }
    Map<Integer, Integer> runePouch() { return Collections.unmodifiableMap(runePouch); }
    Map<Integer, Integer> bank() { return Collections.unmodifiableMap(bank); }
    PohSpec poh() { return poh; }
    Set<PlantedSpiritTree> plantedSpiritTrees() { return Collections.unmodifiableSet(plantedSpiritTrees); }
    Set<QuestMilestone> questMilestones() { return Collections.unmodifiableSet(questMilestones); }
    Set<QuetzalPlatform> quetzalPlatforms() { return Collections.unmodifiableSet(quetzalPlatforms); }
    Set<HotAirBalloonDestination> hotAirBalloonDestinations() { return Collections.unmodifiableSet(hotAirBalloonDestinations); }
    Set<CatacombsEntrance> catacombsEntrances() { return Collections.unmodifiableSet(catacombsEntrances); }
    Set<PermanentUnlock> permanentUnlocks() { return Collections.unmodifiableSet(permanentUnlocks); }
    Map<Integer, Integer> routingVarbits() { return Collections.unmodifiableMap(routingVarbits); }
    Map<Integer, Integer> routingVarplayers() { return Collections.unmodifiableMap(routingVarplayers); }
    boolean fairyRingsUnlocked() { return fairyRingsUnlocked; }
    boolean includeProgressionRouting() { return includeProgressionRouting; }
    Integer questPoints() { return questPoints; }
    Integer totalLevel() { return totalLevel; }
    RuntimeSpec runtime() { return runtime; }
}

enum Diary { ARDOUGNE("Ardougne"), DESERT("Desert"), FALADOR("Falador"), FREMENNIK("Fremennik"),
    KANDARIN("Kandarin"), KARAMJA("Karamja"), KOUREND_KEBOS("KourendKebos"),
    LUMBRIDGE_DRAYNOR("LumbridgeDraynor"), MORYTANIA("Morytania"), VARROCK("Varrock"),
    WESTERN_PROVINCES("WesternProvinces"), WILDERNESS("Wilderness");
    final String jsonName;
    Diary(String jsonName) { this.jsonName = jsonName; }
}

enum CorpusQuest {
    ARCHITECTURAL_ALLIANCE("Architectural Alliance"),
    TALE_OF_THE_RIGHTEOUS("The Tale of the Righteous");
    final String name;
    CorpusQuest(String name) { this.name = name; }
}

enum DiaryTier { NONE("NoDiary"), EASY("Easy"), MEDIUM("Medium"), HARD("Hard"), ELITE("Elite");
    final String jsonName;
    DiaryTier(String jsonName) { this.jsonName = jsonName; }
}

enum PlantedSpiritTree { FARMING_GUILD, PORT_SARIM, ETCETERIA, BRIMHAVEN, HOSIDIUS }
enum QuestMilestone { LAND_OF_THE_GOBLINS_YU_BIUSK_ACCESS, SINS_OF_THE_FATHER_SLEPE_BOAT_ACCESS }
enum QuetzalPlatform { CAM_TORUM, COLOSSAL_WYRM_REMAINS, OUTER_FORTIS, FORTIS_COLOSSEUM, SALVAGER_OVERLOOK, KASTORI }
enum HotAirBalloonDestination { ENTRANA, TAVERLEY, CASTLE_WARS, GRAND_TREE, CRAFTING_GUILD, VARROCK }
enum CatacombsEntrance { FORTHOS_DUNGEON, SURFACE_ENTRANCES, GIANTS_DEN }
enum PermanentUnlock {
    RAIDS_MOUNTAIN_GUIDE_TRAVEL, CORSAIR_COVE_RESOURCE_AREA, LOST_TRIBE_CELLAR_HOLE,
    BARBARIAN_ASSAULT_TUTORIAL, MUSEUM_KUDOS_153, BARBARIAN_FIREMAKING_TRAINING,
    FENKENSTRAIN_BRIDGE_NORTH, FENKENSTRAIN_BRIDGE_SOUTH, KARAMJA_DUNGEON_BACKDOOR,
    OBSERVATORY_SHORTCUT_ROPE, HOSIDIUS_DUNGEON_WEST_DOOR, HOSIDIUS_DUNGEON_EAST_DOOR,
    DARKMEYER_INNER_SHORTCUT, DARKMEYER_OUTER_SHORTCUT, MET_AUBURN_MOUNTAIN_GUIDE,
    BOOK_OF_SCROLLS_NARDAH, BOOK_OF_SCROLLS_DIGSITE, BOOK_OF_SCROLLS_FELDIP,
    BOOK_OF_SCROLLS_LUNAR_ISLE, BOOK_OF_SCROLLS_MORTTON, BOOK_OF_SCROLLS_PEST_CONTROL,
    BOOK_OF_SCROLLS_PISCATORIS, BOOK_OF_SCROLLS_TAI_BWO, BOOK_OF_SCROLLS_ELF,
    BOOK_OF_SCROLLS_MOS_LE_HARMLESS, BOOK_OF_SCROLLS_LUMBERYARD, BOOK_OF_SCROLLS_ZUL_ANDRA,
    BOOK_OF_SCROLLS_CERBERUS, BOOK_OF_SCROLLS_REVENANTS, BOOK_OF_SCROLLS_WATSON,
    PENDANT_OF_ATES_DARKFROST, PENDANT_OF_ATES_TWILIGHT, PENDANT_OF_ATES_RALOS,
    PENDANT_OF_ATES_ALDARIN, PHARAOHS_SCEPTRE_NECROPOLIS, COLOSSEUM_WAVE_NINE,
    ROWBOAT_VATRACHOS, ROWBOAT_ANGLERS, ROWBOAT_SOUL_TEAR, ROWBOAT_YNYSDAIL,
    ROWBOAT_BUCCANEERS, RESPAWN_FALADOR, RESPAWN_CAMELOT, RESPAWN_EDGEVILLE,
    RESPAWN_FEROX_ENCLAVE, RESPAWN_KOUREND_CASTLE, RESPAWN_CIVITAS_ILLA_FORTIS
}

final class PohSpec {
    private final PohLocation location;
    private final JewelleryBox jewelleryBox;
    private final PortalMode portalMode;
    private final Set<String> portalDestinations;
    private final boolean fairyRing;
    private final boolean spiritTree;
    private final boolean obelisk;
    private final boolean mountedGlory;
    private final boolean mountedXerics;
    private final boolean mountedDigsite;
    private final boolean mountedMythical;

    PohSpec(PohLocation location, JewelleryBox jewelleryBox, PortalMode portalMode, Set<String> portalDestinations,
            boolean fairyRing, boolean spiritTree, boolean obelisk, boolean mountedGlory,
            boolean mountedXerics, boolean mountedDigsite, boolean mountedMythical) {
        this.location = location;
        this.jewelleryBox = jewelleryBox;
        this.portalMode = portalMode;
        this.portalDestinations = Set.copyOf(portalDestinations);
        this.fairyRing = fairyRing;
        this.spiritTree = spiritTree;
        this.obelisk = obelisk;
        this.mountedGlory = mountedGlory;
        this.mountedXerics = mountedXerics;
        this.mountedDigsite = mountedDigsite;
        this.mountedMythical = mountedMythical;
    }

    static PohSpec standard() {
        return new PohSpec(PohLocation.RIMMINGTON, JewelleryBox.NONE, PortalMode.SELECTED,
            Set.of(), false, false, false, false, false, false, false);
    }
    PohLocation location() { return location; }
    JewelleryBox jewelleryBox() { return jewelleryBox; }
    PortalMode portalMode() { return portalMode; }
    Set<String> portalDestinations() { return portalDestinations; }
    boolean fairyRing() { return fairyRing; }
    boolean spiritTree() { return spiritTree; }
    boolean obelisk() { return obelisk; }
    boolean mountedGlory() { return mountedGlory; }
    boolean mountedXerics() { return mountedXerics; }
    boolean mountedDigsite() { return mountedDigsite; }
    boolean mountedMythical() { return mountedMythical; }
}

enum PohLocation { RIMMINGTON, TAVERLEY, POLLNIVNEACH, RELLEKKA, BRIMHAVEN, YANILLE, PRIFDDINAS, HOSIDIUS, ALDARIN }
enum JewelleryBox { NONE, FANCY, ORNATE }
enum PortalMode { ALL, SELECTED }
enum Spellbook { STANDARD, ANCIENT, LUNAR, ARCEUUS }

final class RuntimeSpec {
    final Spellbook spellbook;
    final Cooldown cooldown;
    final boolean arriveInsidePoh;
    RuntimeSpec(Spellbook spellbook, Cooldown cooldown, boolean arriveInsidePoh) {
        this.spellbook = spellbook;
        this.cooldown = cooldown;
        this.arriveInsidePoh = arriveInsidePoh;
    }
    static RuntimeSpec ready() { return new RuntimeSpec(Spellbook.STANDARD, Cooldown.READY, true); }
}

final class Cooldown {
    final Integer usedAt;
    static final Cooldown READY = new Cooldown(null);
    private Cooldown(Integer usedAt) { this.usedAt = usedAt; }
    static Cooldown usedAt(int minutes) { return new Cooldown(minutes); }
}
