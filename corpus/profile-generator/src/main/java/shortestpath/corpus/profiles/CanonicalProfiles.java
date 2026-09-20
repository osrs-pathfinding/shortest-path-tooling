package shortestpath.corpus.profiles;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** The four canonical accounts. This is the profile source of truth. */
final class CanonicalProfiles {
    private CanonicalProfiles() { }

    static List<ProfileSpec> all() {
        List<ProfileSpec> profiles = new ArrayList<>();
        profiles.add(early()); profiles.add(mid()); profiles.add(end()); profiles.add(maxed());
        return profiles;
    }

    static ProfileSpec early() {
        return base("early", earlyLevels(), earlyQuests())
            .inventory(CanonicalItems.earlyInventory()).bank(CanonicalItems.earlyBank())
            .runePouch(CanonicalItems.runePouch()).fairyRings(true);
    }

    static ProfileSpec mid() {
        return midUnlocks(base("mid", skills(80), progressedQuests())
            .levels(with(skills(80), Skill.CONSTRUCTION, 78, Skill.HERBLORE, 78, Skill.HITPOINTS, 85,
                Skill.MAGIC, 85, Skill.PRAYER, 70, Skill.RUNECRAFT, 75, Skill.STRENGTH, 85,
                Skill.FARMING, 83, Skill.THIEVING, 82, Skill.SAILING, 75))
            .inventory(CanonicalItems.midInventory()).bank(CanonicalItems.midBank())
            .runePouch(CanonicalItems.runePouch()).planted(PlantedSpiritTree.FARMING_GUILD)
            .fairyRings(true));
    }

    static ProfileSpec end() {
        ProfileSpec profile = base("end", skills(90), progressedQuests())
            .levels(with(skills(90), Skill.CONSTRUCTION, 85, Skill.COOKING, 95, Skill.FARMING, 91,
                Skill.HITPOINTS, 95, Skill.MAGIC, 94, Skill.MINING, 85, Skill.PRAYER, 85,
                Skill.RUNECRAFT, 85, Skill.SAILING, 85, Skill.SLAYER, 95, Skill.SMITHING, 91,
                Skill.STRENGTH, 95, Skill.THIEVING, 91))
            .inventory(CanonicalItems.endInventory()).bank(CanonicalItems.endBank())
            .runePouch(CanonicalItems.runePouch()).planted(PlantedSpiritTree.FARMING_GUILD,
                PlantedSpiritTree.PORT_SARIM).fairyRings(true);
        profile.diary(Diary.LUMBRIDGE_DRAYNOR, DiaryTier.ELITE);
        return midUnlocks(profile);
    }

    static ProfileSpec maxed() {
        Map<Skill, Integer> levels = skills(99);
        ProfileSpec profile = midUnlocks(base("maxed", levels, progressedQuests())
            .levels(levels).inventory(CanonicalItems.maxedInventory()).bank(CanonicalItems.maxedBank())
            .runePouch(CanonicalItems.runePouch()).planted(PlantedSpiritTree.FARMING_GUILD,
                PlantedSpiritTree.PORT_SARIM, PlantedSpiritTree.ETCETERIA, PlantedSpiritTree.BRIMHAVEN,
                PlantedSpiritTree.HOSIDIUS).fairyRings(true));
        for (Diary diary : Diary.values()) profile.diary(diary, DiaryTier.ELITE);
        return profile;
    }

    private static ProfileSpec base(String name, Map<Skill, Integer> levels, EnumSet<Quest> quests) {
        ProfileSpec profile = new ProfileSpec(name).levels(levels);
        profile.completedQuests.addAll(quests);
        if (!name.equals("early")) {
            profile.completedCorpusQuests.add(CorpusQuest.ARCHITECTURAL_ALLIANCE);
            profile.completedCorpusQuests.add(CorpusQuest.TALE_OF_THE_RIGHTEOUS);
        }
        for (Diary diary : Diary.values()) profile.diary(diary, name.equals("early") ? DiaryTier.MEDIUM : DiaryTier.HARD);
        profile.poh.portalMode = name.equals("early") || name.equals("mid") ? PortalMode.SELECTED : PortalMode.ALL;
        if (!name.equals("early")) profile.poh.portalDestinations.addAll(List.of("Ardougne Portal", "Barrows Portal",
            "Camelot Portal", "Falador Portal", "Kourend Portal", "Varrock Portal"));
        profile.poh.fairyRing = name.equals("end") || name.equals("maxed");
        profile.poh.spiritTree = !name.equals("early") && !name.equals("mid");
        profile.poh.obelisk = !name.equals("early") && !name.equals("mid");
        profile.poh.mountedGlory = !name.equals("early");
        profile.poh.mountedXerics = !name.equals("early");
        profile.poh.mountedDigsite = !name.equals("early");
        profile.poh.mountedMythical = !name.equals("early");
        profile.poh.jewelleryBox = name.equals("early") ? JewelleryBox.NONE
            : name.equals("mid") ? JewelleryBox.FANCY : JewelleryBox.ORNATE;
        return profile;
    }

    private static Map<Skill, Integer> earlyLevels() {
        return with(skills(70), Skill.CONSTRUCTION, 60, Skill.CRAFTING, 65, Skill.FARMING, 65,
            Skill.FISHING, 65, Skill.FLETCHING, 65, Skill.HERBLORE, 65, Skill.HITPOINTS, 75,
            Skill.HUNTER, 65, Skill.MINING, 65, Skill.PRAYER, 60, Skill.RUNECRAFT, 60,
            Skill.SAILING, 60, Skill.SLAYER, 65, Skill.SMITHING, 65, Skill.STRENGTH, 75,
            Skill.THIEVING, 65, Skill.WOODCUTTING, 65);
    }

    private static ProfileSpec midUnlocks(ProfileSpec profile) {
        profile.questMilestones.add(QuestMilestone.LAND_OF_THE_GOBLINS_YU_BIUSK_ACCESS);
        profile.questMilestones.add(QuestMilestone.SINS_OF_THE_FATHER_SLEPE_BOAT_ACCESS);
        profile.quetzalPlatforms.addAll(EnumSet.allOf(QuetzalPlatform.class));
        profile.hotAirBalloonDestinations.addAll(EnumSet.allOf(HotAirBalloonDestination.class));
        profile.catacombsEntrances.addAll(EnumSet.allOf(CatacombsEntrance.class));
        profile.permanentUnlocks.addAll(EnumSet.allOf(PermanentUnlock.class));
        return profile;
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

    private static Map<Skill, Integer> skills(int value) {
        Map<Skill, Integer> result = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) if (skill != Skill.OVERALL) result.put(skill, value);
        return result;
    }

    private static Map<Skill, Integer> with(Map<Skill, Integer> base, Object... changes) {
        for (int i = 0; i < changes.length; i += 2) base.put((Skill) changes[i], (Integer) changes[i + 1]);
        return base;
    }
}
