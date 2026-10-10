package shortestpath.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import shortestpath.accounts.canonical.CanonicalAccounts;

public class AccountBuildsTest {
    @Test
    public void semanticAccountStateOverridesCompatibilityVariables() {
        AccountBuild build = CanonicalAccounts.build("mid");
        build.diaries.put("Ardougne", "Easy");
        build.runtime.spellbook = "Ancient";
        build.runtime.minigameTeleport.state = "usedAt";
        build.runtime.minigameTeleport.minutes = 123L;
        build.completedQuests.remove("The Grand Tree");

        Account account = AccountBuilds.toAccount(build);
        assertEquals(1, account.varbits().get(VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE).intValue());
        assertEquals(0, account.varbits().get(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE).intValue());
        assertEquals(1, account.varbits().get(VarbitID.SPELLBOOK).intValue());
        assertEquals(0, account.varplayers().get(VarPlayerID.GRANDTREE).intValue());
        assertEquals(123, account.varplayers().get(VarPlayerID.SLUG2_REGIONUID).intValue());
        assertEquals(QuestState.NOT_STARTED, account.questState(Quest.THE_GRAND_TREE));
    }

    @Test
    public void levelsAreRealLevelsAndTheTotalIgnoresDerivedEntries() {
        AccountBuild build = CanonicalAccounts.build("mid");
        Account account = AccountBuilds.toAccount(build);
        int total = build.levels.entrySet().stream()
            .filter(entry -> !entry.getKey().equals("Quest") && !entry.getKey().equals("Total"))
            .mapToInt(entry -> entry.getValue()).sum();
        assertEquals(total, account.totalLevel());
        assertEquals(build.levels.get("Attack").intValue(), account.level(Skill.ATTACK));
        assertTrue(account.reportsRealLevels());
    }

    @Test
    public void itemsMayBeNamedByTheirPluginVariation() {
        AccountBuild build = CanonicalAccounts.build("early");
        build.inventory.clear();
        build.inventory.put("COINS", 100);
        build.runePouch.clear();
        build.runePouch.put("995", 5);
        assertEquals(105, AccountBuilds.toAccount(build).inventory().values().stream().mapToInt(Integer::intValue).sum());

        build.inventory.put("NOT_AN_ITEM", 1);
        assertThrows(IllegalArgumentException.class, () -> AccountBuilds.toAccount(build));
    }

    @Test
    public void unknownSkillsAreRejected() {
        AccountBuild build = CanonicalAccounts.build("early");
        build.levels.put("Overall", 50);
        assertThrows(IllegalArgumentException.class, () -> AccountBuilds.toAccount(build));
    }
}
