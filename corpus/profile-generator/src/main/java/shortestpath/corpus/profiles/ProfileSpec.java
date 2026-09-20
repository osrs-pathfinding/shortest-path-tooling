package shortestpath.corpus.profiles;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Semantic source model. It deliberately has no raw varbit/varplayer fields. */
final class ProfileSpec {
    final String name;
    final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
    final Set<Quest> completedQuests = EnumSet.noneOf(Quest.class);
    final Set<CorpusQuest> completedCorpusQuests = EnumSet.noneOf(CorpusQuest.class);
    final Map<Diary, DiaryTier> diaries = new EnumMap<>(Diary.class);
    final Map<Integer, Integer> inventory = new LinkedHashMap<>();
    final Map<Integer, Integer> equipment = new LinkedHashMap<>();
    final Map<Integer, Integer> runePouch = new LinkedHashMap<>();
    final Map<Integer, Integer> bank = new LinkedHashMap<>();
    final PohSpec poh = new PohSpec();
    final Set<PlantedSpiritTree> plantedSpiritTrees = EnumSet.noneOf(PlantedSpiritTree.class);
    final Set<QuestMilestone> questMilestones = EnumSet.noneOf(QuestMilestone.class);
    final Set<QuetzalPlatform> quetzalPlatforms = EnumSet.noneOf(QuetzalPlatform.class);
    final Set<HotAirBalloonDestination> hotAirBalloonDestinations =
        EnumSet.noneOf(HotAirBalloonDestination.class);
    final Set<CatacombsEntrance> catacombsEntrances = EnumSet.noneOf(CatacombsEntrance.class);
    final Set<PermanentUnlock> permanentUnlocks = EnumSet.noneOf(PermanentUnlock.class);
    boolean fairyRingsUnlocked;
    RuntimeSpec runtime = RuntimeSpec.ready();

    ProfileSpec(String name) { this.name = name; }

    ProfileSpec levels(Map<Skill, Integer> values) { levels.putAll(values); return this; }
    ProfileSpec completed(Quest... quests) { for (Quest quest : quests) completedQuests.add(quest); return this; }
    ProfileSpec completed(CorpusQuest... quests) { for (CorpusQuest quest : quests) completedCorpusQuests.add(quest); return this; }
    ProfileSpec diary(Diary diary, DiaryTier tier) { diaries.put(diary, tier); return this; }
    ProfileSpec inventory(Map<Integer, Integer> values) { inventory.putAll(values); return this; }
    ProfileSpec equipment(Map<Integer, Integer> values) { equipment.putAll(values); return this; }
    ProfileSpec runePouch(Map<Integer, Integer> values) { runePouch.putAll(values); return this; }
    ProfileSpec bank(Map<Integer, Integer> values) { bank.putAll(values); return this; }
    ProfileSpec planted(PlantedSpiritTree... trees) {
        for (PlantedSpiritTree tree : trees) plantedSpiritTrees.add(tree);
        return this;
    }
    ProfileSpec fairyRings(boolean value) { fairyRingsUnlocked = value; return this; }
    ProfileSpec runtime(RuntimeSpec value) { runtime = value; return this; }
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
    PohLocation location = PohLocation.RIMMINGTON;
    JewelleryBox jewelleryBox = JewelleryBox.NONE;
    PortalMode portalMode = PortalMode.SELECTED;
    final Set<String> portalDestinations = new LinkedHashSet<>();
    boolean fairyRing;
    boolean spiritTree;
    boolean obelisk;
    boolean mountedGlory;
    boolean mountedXerics;
    boolean mountedDigsite;
    boolean mountedMythical;
}

enum PohLocation { RIMMINGTON, TAVERLEY, POLLNIVNEACH, RELLEKKA, BRIMHAVEN, YANILLE, PRIFDDINAS, HOSIDIUS, ALDARIN }
enum JewelleryBox { NONE, FANCY, ORNATE }
enum PortalMode { ALL, SELECTED }
enum Spellbook { STANDARD, ANCIENT, LUNAR, ARCEUUS }

final class RuntimeSpec {
    Spellbook spellbook = Spellbook.STANDARD;
    Cooldown cooldown = Cooldown.READY;
    boolean arriveInsidePoh = true;
    static RuntimeSpec ready() { return new RuntimeSpec(); }
}

final class Cooldown {
    final Integer usedAt;
    static final Cooldown READY = new Cooldown(null);
    private Cooldown(Integer usedAt) { this.usedAt = usedAt; }
    static Cooldown usedAt(int minutes) { return new Cooldown(minutes); }
}
