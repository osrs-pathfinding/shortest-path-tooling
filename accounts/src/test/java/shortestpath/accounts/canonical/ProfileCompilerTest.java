package shortestpath.accounts.canonical;

import net.runelite.api.Quest;
import org.junit.Test;
import java.util.LinkedHashMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProfileCompilerTest {
    @Test public void readyCooldownUsesSyntheticClock() {
        assertEquals(99_999_979, CanonicalAccounts.build("mid").routingVariables.varplayers.get(888).intValue());
    }

    @Test public void conflictFailsWithSource() {
        LinkedHashMap<Integer, Integer> values = new LinkedHashMap<>();
        RoutingVariables.assign(values, 1234, 1, "TEST", "quest A");
        try {
            RoutingVariables.assign(values, 1234, 2, "TEST", "unlock B");
        } catch (VariableConflictException e) {
            assertTrue(e.getMessage().contains("TEST (1234)"));
            assertTrue(e.getMessage().contains("unlock B"));
            return;
        }
        throw new AssertionError("conflicting semantic variables were accepted");
    }

    @Test public void sameAssignmentIsAccepted() {
        LinkedHashMap<Integer, Integer> values = new LinkedHashMap<>();
        RoutingVariables.assign(values, 1234, 1, "TEST", "quest A");
        RoutingVariables.assign(values, 1234, 1, "TEST", "quest A again");
        assertEquals(Integer.valueOf(1), values.get(1234));
    }

    @Test public void changingOnlyProfileNameChangesNoCompiledAccountState() {
        assertEquals(ProfileJsonRenderer.profile(ProfileCompiler.build(CanonicalProfiles.early())),
            ProfileJsonRenderer.profile(ProfileCompiler.build(CanonicalProfiles.early().renamed("renamed"))));
    }

    @Test public void spiritTreeProgressionIsCanonical() {
        assertEquals(0, CanonicalProfiles.early().plantedSpiritTrees().size());
        assertEquals(1, CanonicalProfiles.mid().plantedSpiritTrees().size());
        assertEquals(2, CanonicalProfiles.end().plantedSpiritTrees().size());
        assertEquals(5, CanonicalProfiles.maxed().plantedSpiritTrees().size());
    }

    @Test public void endAndMaxedAreQuestCapeProfiles() {
        assertEquals(Quest.values().length, CanonicalProfiles.end().completedQuests().size());
        assertEquals(Quest.values().length, CanonicalProfiles.maxed().completedQuests().size());
    }
}
