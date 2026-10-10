package shortestpath.scenarios;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.accounts.canonical.CanonicalAccounts;

/** The canonical routes in {@code corpus/corpus/routes-v1.json}. */
public class CanonicalCorpusTest {
    private static final Set<String> TIERS = Set.of("smoke", "standard", "full");

    @Test
    public void routesAreWellFormed() throws IOException {
        Set<String> ids = new HashSet<>();
        for (Route route : CanonicalCorpus.routes(CanonicalCorpus.dir())) {
            String id = route.getId();
            assertTrue("route IDs must be unique: " + id, ids.add(id));
            assertTrue(id + ": has a start", route.getStart() != WorldPointUtil.UNDEFINED);
            assertTrue(id + ": invalid tiers " + route.getTiers(),
                route.getTiers().contains("full") && TIERS.containsAll(route.getTiers()));
            assertTrue(id + ": invalid negative profiles " + route.getNegativeProfiles(),
                CanonicalAccounts.NAMES.containsAll(route.getNegativeProfiles()));
        }
    }
}
