package shortestpath.dashboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.requirement.Capability;
import shortestpath.requirement.model.SkillLevels;

/** The unlock benchmark's report arithmetic and its out-of-order flags. */
public class UnlockBenchmarkReportTest {

    private static UnlockBenchmarkTest.Suggested suggested(Capability capability) {
        UnlockBenchmarkTest.Suggested suggested = new UnlockBenchmarkTest.Suggested();
        suggested.capability = capability;
        suggested.label = UnlockBenchmarkTest.label(capability);
        return suggested;
    }

    private static Capability agility(int level, int missing) {
        int[] levels = new int[SkillLevels.LENGTH];
        levels[Skill.AGILITY.ordinal()] = level;
        return Capability.skills(SkillLevels.of(levels), missing);
    }

    @Test
    public void percentilesAreNearestRank() {
        List<Double> values = List.of(5.0, 1.0, 4.0, 2.0, 3.0);
        assertEquals(3.0, UnlockBenchmarkTest.median(values), 0);
        assertEquals(5.0, UnlockBenchmarkTest.percentile(values, 95), 0);
        assertEquals(1.0, UnlockBenchmarkTest.percentile(values, 1), 0);
        assertEquals(0.0, UnlockBenchmarkTest.percentile(List.of(), 50), 0);
    }

    @Test
    public void flagsAQuestAboveAFewLevelsAndAMajorUnlockFirst() {
        Capability coins = Capability.items(Map.of(ItemID.COINS, 20), false);
        Capability quest = Capability.quests(Set.of(Quest.TWILIGHTS_PROMISE));

        assertEquals(List.of(), UnlockBenchmarkTest.suspicious(List.of(suggested(coins), suggested(quest))));
        assertEquals(List.of("Complete Twilight's Promise above Reach 10 Agility"),
            UnlockBenchmarkTest.suspicious(List.of(suggested(quest), suggested(agility(10, 4)))));
        assertTrue("a few levels after the quest are fine when there are many",
            UnlockBenchmarkTest.suspicious(List.of(suggested(quest), suggested(agility(60, 20)))).isEmpty());
        assertEquals(List.of("major unlock first: Reach 90 Agility"),
            UnlockBenchmarkTest.suspicious(List.of(suggested(agility(90, 60)))));
    }
}
