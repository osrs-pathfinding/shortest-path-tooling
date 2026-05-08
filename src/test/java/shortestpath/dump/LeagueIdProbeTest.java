package shortestpath.dump;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import net.runelite.cache.ItemManager;
import net.runelite.cache.fs.Store;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.util.XteaKeyManager;
import org.junit.Assume;
import org.junit.Test;

/**
 * One-off helper to surface league-related item IDs from the cache. Run via
 * the {@code leagueIdProbe} Gradle task, then copy the printed IDs into
 * {@code seasonal_transports.tsv} (no placeholder file).
 */
public class LeagueIdProbeTest {
    @Test
    public void probeLeagueIds() throws Exception {
        Assume.assumeTrue(
            "Enable with -Dleague.id.probe=true and supply -Dleague.id.cacheDir / -Dleague.id.xteaPath",
            Boolean.getBoolean("league.id.probe"));

        String cacheDir = CacheUtils.requiredProperty("league.id.cacheDir");
        String xteaPath = CacheUtils.requiredProperty("league.id.xteaPath");

        XteaKeyManager xteaKeyManager = new XteaKeyManager();
        try (FileInputStream fin = new FileInputStream(xteaPath)) {
            xteaKeyManager.loadKeys(fin);
        }

        try (Store store = new Store(new File(cacheDir))) {
            store.load();

            ItemManager itemManager = new ItemManager(store);
            itemManager.load();

            System.out.println("=== Banker's Briefcase candidates ===");
            List<ItemDefinition> briefcases = CacheUtils.findItemsByNameSubstring(
                itemManager, "banker's briefcase", "bankers briefcase", "banker briefcase");
            for (ItemDefinition item : briefcases) {
                System.out.println("  id=" + item.getId() + "\tname=\"" + item.getName() + "\"");
            }

            System.out.println();
            System.out.println("=== Relic candidates (Bank Heist / Heist) ===");
            List<ItemDefinition> relics = CacheUtils.findItemsByNameSubstring(
                itemManager, "bank heist", "heist");
            for (ItemDefinition item : relics) {
                System.out.println("  id=" + item.getId() + "\tname=\"" + item.getName() + "\"");
            }
        }
    }
}
