package shortestpath.corpus.profiles;

import org.junit.Test;
import java.util.LinkedHashMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProfileCompilerTest {
    @Test public void readyCooldownUsesSyntheticClock() {
        assertEquals(99_999_979, RoutingVariables.compile(CanonicalProfiles.mid()).varplayers.get(888).intValue());
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

    @Test public void spiritTreeProgressionIsCanonical() {
        assertEquals(0, CanonicalProfiles.early().plantedSpiritTrees.size());
        assertEquals(1, CanonicalProfiles.mid().plantedSpiritTrees.size());
        assertEquals(2, CanonicalProfiles.end().plantedSpiritTrees.size());
        assertEquals(5, CanonicalProfiles.maxed().plantedSpiritTrees.size());
    }
}
