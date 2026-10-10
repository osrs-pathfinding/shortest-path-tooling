package shortestpath.routeapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import shortestpath.accounts.Account;
import shortestpath.accounts.canonical.CanonicalAccounts;

public class AccountBuildsTest {
    /** The generated corpus/profiles documents describe the same accounts as the Java profiles. */
    @Test
    public void canonicalBuildsCompileToTheCanonicalAccounts() {
        for (String name : List.of("early", "mid", "end", "maxed")) {
            Account build = AccountBuilds.toAccount(Fixtures.profile(name));
            Account canonical = CanonicalAccounts.account(name);
            assertEquals(name, canonical.varbits(), build.varbits());
            assertEquals(name, canonical.varplayers(), build.varplayers());
            assertEquals(name, canonical.levels(), build.levels());
            assertEquals(name, canonical.totalLevel(), build.totalLevel());
            assertEquals(name, canonical.questStates(), build.questStates());
            assertEquals(name, canonical.defaultQuestState(), build.defaultQuestState());
            assertEquals(name, canonical.inventory(), build.inventory());
            assertEquals(name, canonical.equipment(), build.equipment());
            assertEquals(name, canonical.bank(), build.bank());
            assertEquals(name, canonical.nowMinutes(), build.nowMinutes());
            assertEquals(name, canonical.plantedSpiritTrees(), build.plantedSpiritTrees());
            assertEquals(name, canonical.poh().portals, build.poh().portals);
        }
    }

    @Test
    public void semanticAccountStateOverridesCompatibilityVariables() {
        RouteApi.AccountBuild build = Fixtures.profile("mid");
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
        RouteApi.AccountBuild build = Fixtures.profile("mid");
        Account account = AccountBuilds.toAccount(build);
        int total = build.levels.entrySet().stream()
            .filter(entry -> !entry.getKey().equals("Quest") && !entry.getKey().equals("Total"))
            .mapToInt(entry -> entry.getValue()).sum();
        assertEquals(total, account.totalLevel());
        assertEquals(build.levels.get("Attack").intValue(), account.level(Skill.ATTACK));
        assertEquals(true, account.reportsRealLevels());
    }

    @Test
    public void itemsMayBeNamedByTheirPluginVariation() {
        RouteApi.AccountBuild build = Fixtures.profile("early");
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
        RouteApi.AccountBuild build = Fixtures.profile("early");
        build.levels.put("Overall", 50);
        assertThrows(IllegalArgumentException.class, () -> AccountBuilds.toAccount(build));
    }
}
