package shortestpath.dump;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.BitSet;
import net.runelite.cache.CollisionMapDumper;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CollisionMapDumperTest {
    @Rule
    public TemporaryFolder output = new TemporaryFolder();

    @Test
    public void tentOpeningCorrectionOnlyAppliesToVerifiedPlacement() throws Exception {
        Class<?> exclusions = Class.forName("net.runelite.cache.CollisionMapDumper$Exclusion");
        Method matches = exclusions.getDeclaredMethod("matches", int.class, int.class, int.class, int.class);
        matches.setAccessible(true);
        assertEquals(Boolean.TRUE, matches.invoke(null, 3673, 2887, 3539, 0));
        assertNull(matches.invoke(null, 3673, 2888, 3539, 0));
        assertNull(matches.invoke(null, 3673, 2887, 3540, 0));
        assertNull(matches.invoke(null, 3673, 2887, 3539, 1));
        assertNull(matches.invoke(null, 3672, 2887, 3539, 0));
    }

    @Test
    public void burthorpeTentEntranceConnectsBoxesWithoutOpeningTentWalls() throws Exception {
        Assume.assumeTrue("Enable the real-cache regression with -Dtile.probe=true",
            Boolean.getBoolean("tile.probe"));
        String cacheDir = System.getProperty("tile.probe.cacheDir", "cache");
        String xteaPath = System.getProperty("tile.probe.xteaPath", "keys.json");
        try (Store store = CacheUtils.openStore(cacheDir)) {
            RegionLoader loader = new RegionLoader(store, CacheUtils.loadXteaKeys(xteaPath));
            CollisionMapDumper dumper = new CollisionMapDumper(store, loader).load();
            Region region = loader.findRegionForRegionCoordinates(45, 55);
            Method dump = CollisionMapDumper.class.getDeclaredMethod("makeCollisionMap",
                Region.class, String.class, int.class, int.class);
            dump.setAccessible(true);
            dump.invoke(dumper, region, output.getRoot().getAbsolutePath(), 1, 1);
            byte[] bytes = Files.readAllBytes(output.getRoot().toPath().resolve("45_55"));
            BitSet flags = BitSet.valueOf(bytes);

            // In-game verified opening -> eastern interior -> only valid box approach.
            assertTrue("step north through the opening", flag(flags, 2887, 3539, 0));
            assertTrue("step west to the box interaction tile", flag(flags, 2886, 3540, 1));
            assertTrue("the opening must connect south to the outside", flag(flags, 2887, 3538, 0));

            // The opening correction must not remove the surrounding tent walls,
            // boxes, or beds (including the wall separating the PR's old endpoint).
            assertFalse("west tent wall", flag(flags, 2884, 3540, 1));
            assertFalse("northern bed", flag(flags, 2886, 3540, 0));
            assertFalse("boxes are not a walkable tile", flag(flags, 2885, 3540, 1));
            assertFalse("east tent wall", flag(flags, 2887, 3540, 1));
        }
    }

    private static boolean flag(BitSet flags, int x, int y, int direction) {
        // Four flags per tile, matching the standalone dumper's wire format.
        return flags.get((((y & 63) * 64) + (x & 63)) * 4 + direction);
    }
}
