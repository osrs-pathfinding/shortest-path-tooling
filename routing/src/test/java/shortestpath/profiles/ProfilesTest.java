package shortestpath.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.accounts.Poh;
import shortestpath.accounts.canonical.CanonicalAccounts;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;

public class ProfilesTest {
    private static CompiledAccount canonical(String name) {
        return Profiles.get(name).setup(new ProfileContext(WorldPointUtil.UNDEFINED, true)).compile();
    }

    @Test
    public void canonicalProfilesUseConcreteFactsAndQuestStates() {
        CompiledAccount mid = canonical("mid");
        assertNotNull(mid.getConfig().bank);
        assertEquals(80, mid.getClient().getBoostedSkillLevel(Skill.ATTACK));
        assertEquals(1, mid.getClient().getVarbitValue(VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE));
        assertEquals(QuestState.FINISHED, mid.getConfig().getQuestState(Quest.THE_GRAND_TREE));
        assertEquals(QuestState.NOT_STARTED, mid.getConfig().getQuestState(Quest.RUM_DEAL));
        assertEquals(EnumSet.of(PohNexusPortal.ARDOUGNE, PohNexusPortal.BARROWS, PohNexusPortal.CAMELOT,
                PohNexusPortal.FALADOR, PohNexusPortal.KOUREND, PohNexusPortal.VARROCK),
            mid.getSettings().pohNexusPortals());
        assertEquals(CanonicalAccounts.NOW_MINUTES, mid.getConfig().evaluationTimeMinutes());
    }

    @Test
    public void canonicalProfilesPlantSpiritTreesIndependentlyOfThePoh() {
        assertEquals(Set.of(), canonical("early").getConfig().availableSpiritTrees);
        assertEquals(Set.of("Farming Guild"), canonical("mid").getConfig().availableSpiritTrees);
        assertEquals(Set.of("Farming Guild", "Port Sarim"), canonical("end").getConfig().availableSpiritTrees);
        assertEquals(Set.of("Farming Guild", "Port Sarim", "Etceteria", "Brimhaven", "Hosidius"),
            canonical("maxed").getConfig().availableSpiritTrees);
        assertFalse(canonical("mid").getSettings().usePohSpiritTree());
        assertTrue(canonical("end").getSettings().usePohSpiritTree());
    }

    @Test
    public void mountedItemsFollowThePoh() {
        Poh poh = new Poh(Poh.Location.RIMMINGTON, false, false, false, Poh.JewelleryBox.NONE,
            true, false, true, false, List.of());
        assertEquals(EnumSet.of(PohMountedItem.GLORY, PohMountedItem.DIGSITE_PENDANT), PluginSettings.mountedItems(poh));
    }

    @Test
    public void earlyFishingTrawlerTeleportUsesTheProfileClock() {
        Setup cooldown = Profiles.EARLY.setup(new ProfileContext(WorldPointUtil.UNDEFINED, true));
        cooldown.account.minigameTeleportUsedAt(CanonicalAccounts.NOW_MINUTES - 10);
        assertTrue(Arrays.stream(canonical("early").getConfig().getUsableTeleports(false))
            .anyMatch(transport -> "Fishing Trawler Minigame Teleport".equals(transport.getDisplayInfo())));
        assertTrue(Arrays.stream(cooldown.compile().getConfig().getUsableTeleports(false))
            .noneMatch(transport -> "Fishing Trawler Minigame Teleport".equals(transport.getDisplayInfo())));
    }

    @Test
    public void presetsStartFromTheHarnessAccount() {
        int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
        CompiledAccount unitTest = Profiles.get("unit_test").setup(new ProfileContext(start, true)).compile();
        assertEquals(99, unitTest.getClient().getBoostedSkillLevel(Skill.AGILITY));
        assertEquals(2277, unitTest.getClient().getTotalLevel());
        assertEquals(0, unitTest.getClient().getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE));
        assertEquals(QuestState.FINISHED, unitTest.getConfig().getQuestState(Quest.RUM_DEAL));
        assertEquals(30, unitTest.getSettings().calculationCutoff());
        assertNull(unitTest.getClient().getItemContainer(InventoryID.INV));
        assertNull(unitTest.getConfig().bank);
        assertEquals(3222, unitTest.getClient().getLocalPlayer().getWorldLocation().getX());

        CompiledAccount seasonal = Profiles.SEASONAL.setup(new ProfileContext(start, true)).compile();
        assertEquals(EnumSet.of(WorldType.SEASONAL), seasonal.getClient().getWorldType());
        assertEquals(1, seasonal.getClient().getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE));

        assertEquals(Set.of("ALL", "NONE", "SEASONAL", "UNIT_TEST"), Profiles.presetNames());
    }

    @Test
    public void overridesWinOverTheProfile() {
        Setup setup = Profiles.UNIT_TEST.setup(new ProfileContext(WorldPointUtil.UNDEFINED, true));
        setup.account.level(Skill.AGILITY, 50).varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 1)
            .quest(Quest.RUM_DEAL, QuestState.IN_PROGRESS).inventory(995, 10).inventory(995, 5);
        setup.settings.setUseFairyRings(true);
        CompiledAccount compiled = setup.compile();
        assertEquals(50, compiled.getClient().getBoostedSkillLevel(Skill.AGILITY));
        assertEquals(1, compiled.getClient().getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE));
        assertEquals(QuestState.IN_PROGRESS, compiled.getConfig().getQuestState(Quest.RUM_DEAL));
        assertEquals(15, compiled.getClient().getItemContainer(InventoryID.INV).getItems()[0].getQuantity());
        assertTrue(compiled.getSettings().useFairyRings());
    }
}
