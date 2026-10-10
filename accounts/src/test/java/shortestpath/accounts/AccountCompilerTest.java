package shortestpath.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class AccountCompilerTest {
    private static ClientState compile(Account.Builder account) {
        return AccountCompiler.compile(account.build());
    }

    @Test
    public void finishedQuestsSetTheirProgressVariables() {
        ClientState state = compile(Account.builder().quest(Quest.DRAGON_SLAYER_I, QuestState.FINISHED)
            .quest(Quest.THE_GRAND_TREE, QuestState.IN_PROGRESS));
        assertEquals(1, state.varbits().get(VarbitID.DRAGONSLAYER_CRANDOR_FOUND_SECRET_DOOR).intValue());
        assertEquals(10, state.varplayers().get(VarPlayerID.DRAGONQUEST).intValue());
        assertEquals(0, state.varplayers().get(VarPlayerID.GRANDTREE).intValue());
        assertEquals(QuestState.IN_PROGRESS, state.questState(Quest.THE_GRAND_TREE));
    }

    @Test
    public void aDiaryTierImpliesTheTiersBelowIt() {
        ClientState state = compile(Account.builder().diary(Diary.ARDOUGNE, Diary.Tier.MEDIUM));
        assertEquals(1, state.varbits().get(VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE).intValue());
        assertEquals(1, state.varbits().get(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE).intValue());
        assertEquals(0, state.varbits().get(VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE).intValue());
        assertEquals(0, state.varbits().get(VarbitID.VARROCK_DIARY_EASY_COMPLETE).intValue());
    }

    @Test
    public void unlocksSetTheirVariablesAndQuetzalPlatformsShareAMask() {
        ClientState state = compile(Account.builder().unlock(Unlock.MUSEUM_KUDOS_153,
            Unlock.QUETZAL_CAM_TORUM, Unlock.QUETZAL_KASTORI));
        assertEquals(153, state.varbits().get(VarbitID.VM_KUDOS).intValue());
        assertEquals(0, state.varbits().get(VarbitID.BOOKOFSCROLLS_NARDAH).intValue());
        assertEquals(32 | 16384, state.varplayers().get(VarPlayerID.QUETZALS_UNLOCKED).intValue());
    }

    @Test
    public void spellbookHouseAndMinigameCooldown() {
        Poh yanille = new Poh(Poh.Location.YANILLE, false, false, false, Poh.JewelleryBox.NONE,
            false, false, false, false, List.of());
        ClientState ready = compile(Account.builder().spellbook(Spellbook.LUNAR).poh(yanille).nowMinutes(1000));
        assertEquals(2, ready.varbits().get(VarbitID.SPELLBOOK).intValue());
        assertEquals(6, ready.varbits().get(VarbitID.POH_HOUSE_LOCATION).intValue());
        assertEquals(979, ready.varplayers().get(VarPlayerID.SLUG2_REGIONUID).intValue());
        assertEquals(995, compile(Account.builder().nowMinutes(1000).minigameTeleportUsedAt(995))
            .varplayers().get(VarPlayerID.SLUG2_REGIONUID).intValue());
        assertNull(compile(Account.builder()).varbits().get(VarbitID.POH_HOUSE_LOCATION));
    }

    @Test
    public void rawVariablesOverrideTheFacts() {
        assertEquals(3, compile(Account.builder().varbit(10662, 3)).varbits().get(10662).intValue());
        ClientState halfDone = compile(Account.builder().quest(Quest.LEGENDS_QUEST, QuestState.FINISHED)
            .varplayer(VarPlayerID.LEGENDSQUEST, 50));
        assertEquals(50, halfDone.varplayers().get(VarPlayerID.LEGENDSQUEST).intValue());
    }

    @Test
    public void runePouchRunesCountAsCarriedAndTheTotalLevelIsTheSum() {
        ClientState state = compile(Account.builder().inventory(ItemID.LAWRUNE, 5).runePouch(ItemID.LAWRUNE, 10)
            .level(Skill.ATTACK, 50).level(Skill.MAGIC, 60));
        assertEquals(15, state.inventory().get(ItemID.LAWRUNE).intValue());
        assertEquals(110, state.totalLevel());
        assertNull(state.equipment());
        assertNull(state.bank());
    }
}
