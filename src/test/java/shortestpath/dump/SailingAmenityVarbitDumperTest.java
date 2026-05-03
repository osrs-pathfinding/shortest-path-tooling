package shortestpath.dump;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.cache.ItemManager;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.XteaKeyManager;
import org.junit.Assume;
import org.junit.Test;

/**
 * Finds the varbits that gate the Sailing-island construction-built bank
 * chests.
 *
 * The wiki's Bank chest (amenity) infobox names two object IDs:
 *   58662 — "Bank chest space" (unbuilt)
 *   58663 — "Bank chest" (built)
 *
 * Both definitions carry no varbit and are never placed in the landscape
 * directly. Each physical chest is a multi-loc parent object whose
 * {@code configChangeDest} transforms between 58662 and 58663 based on its
 * varbit. This dumper:
 *   1. finds every parent object whose transforms reference 58662/58663,
 *   2. prints that parent's varbit,
 *   3. looks up the parent's one and only world placement.
 *
 * Run with:
 *   ./gradlew sailingAmenityVarbitDump \
 *     -PsailingAmenityCacheDir=$PWD/cache \
 *     -PsailingAmenityXteaPath=$PWD/keys.json
 */
public class SailingAmenityVarbitDumperTest {
    private static final int[] SEED_IDS = {58662, 58663};
    // Sailors' Marker scenery object IDs (Port Roberts, Red Rock, Deepfin Point).
    // Interacting with a marker unlocks the Sailors' amulet teleport to that location.
    private static final int[] SAILORS_MARKER_IDS = {59985, 59986, 59988};

    @Test
    public void dumpSailingAmenityVarbits() throws Exception {
        Assume.assumeTrue(
            "Enable with -Dsailing.amenity.dump=true and supply -Dsailing.amenity.cacheDir / -Dsailing.amenity.xteaPath",
            Boolean.getBoolean("sailing.amenity.dump"));

        String cacheDir = requiredProperty("sailing.amenity.cacheDir");
        String xteaPath = requiredProperty("sailing.amenity.xteaPath");

        XteaKeyManager xteaKeyManager = new XteaKeyManager();
        try (FileInputStream fin = new FileInputStream(xteaPath)) {
            xteaKeyManager.loadKeys(fin);
        }

        try (Store store = new Store(new File(cacheDir))) {
            store.load();

            ObjectManager objectManager = new ObjectManager(store);
            objectManager.load();

            Set<Integer> seedSet = new HashSet<>();
            for (int id : SEED_IDS) seedSet.add(id);

            // 1. Parent multi-loc objects that transform into the seed IDs.
            List<ObjectDefinition> parents = new ArrayList<>();
            for (ObjectDefinition def : objectManager.getObjects()) {
                int[] dest = def.getConfigChangeDest();
                if (dest == null) continue;
                for (int d : dest) {
                    if (seedSet.contains(d)) { parents.add(def); break; }
                }
            }
            parents.sort((a, b) -> Integer.compare(a.getId(), b.getId()));

            // 2. World placements of those parents.
            RegionLoader regionLoader = new RegionLoader(store, xteaKeyManager);
            regionLoader.loadRegions();
            regionLoader.calculateBounds();

            Set<Integer> parentIds = new HashSet<>();
            for (ObjectDefinition p : parents) parentIds.add(p.getId());

            Map<Integer, int[]> placement = new HashMap<>();
            for (Region region : regionLoader.getRegions()) {
                for (Location loc : region.getLocations()) {
                    if (!parentIds.contains(loc.getId())) continue;
                    Position pos = loc.getPosition();
                    placement.put(loc.getId(), new int[]{pos.getX(), pos.getY(), pos.getZ()});
                }
            }

            // 3. Report.
            System.out.println("id\tvarbitId\tvarpId\ttransforms\tx\ty\tplane");
            for (ObjectDefinition p : parents) {
                int[] pos = placement.get(p.getId());
                String coord = pos == null ? "?\t?\t?" : pos[0] + "\t" + pos[1] + "\t" + pos[2];
                System.out.println(p.getId()
                    + "\t" + p.getVarbitID()
                    + "\t" + p.getVarpID()
                    + "\t" + Arrays.toString(p.getConfigChangeDest())
                    + "\t" + coord);
            }

            // 4. Rowboat dump. Same pattern as bank chests but seeded on the rowboat
            //    object IDs: 58658 = "Rowboat space" (unbuilt), 58659 = "Rowboat" (built).
            //    Uses tile settings (bit 0 = BLOCKED/water) to identify which face of each
            //    rowboat footprint is accessible, giving the exact player departure tile.
            System.out.println();
            System.out.println("=== Rowboats ===");

            // Build a set of world tiles marked as blocked (water / impassable) at plane 0.
            // TILE_SETTING_BLOCKED = 1 (bit 0). We only care about plane 0 for these transports.
            Set<Long> blockedTiles = new HashSet<>();
            for (Region region : regionLoader.getRegions()) {
                int baseX = region.getBaseX();
                int baseY = region.getBaseY();
                for (int lx = 0; lx < 64; lx++) {
                    for (int ly = 0; ly < 64; ly++) {
                        if ((region.getTileSetting(0, lx, ly) & 1) != 0) {
                            long key = ((long)(baseX + lx) << 16) | ((long)(baseY + ly) << 2);
                            blockedTiles.add(key);
                        }
                    }
                }
            }

            int[] rowboatSeedIds = {58658, 58659};
            Set<Integer> rowboatSeedSet = new HashSet<>();
            for (int id : rowboatSeedIds) rowboatSeedSet.add(id);

            List<ObjectDefinition> rowboatParents = new ArrayList<>();
            for (ObjectDefinition def : objectManager.getObjects()) {
                int[] dest = def.getConfigChangeDest();
                if (dest == null) continue;
                for (int d : dest) {
                    if (rowboatSeedSet.contains(d)) { rowboatParents.add(def); break; }
                }
            }
            rowboatParents.sort((a, b) -> Integer.compare(a.getId(), b.getId()));

            Set<Integer> rowboatParentIds = new HashSet<>();
            for (ObjectDefinition p : rowboatParents) rowboatParentIds.add(p.getId());

            // Collect all Location objects (preserving orientation) per parent ID.
            Map<Integer, List<Location>> rowboatLocations = new HashMap<>();
            for (Region region : regionLoader.getRegions()) {
                for (Location loc : region.getLocations()) {
                    if (!rowboatParentIds.contains(loc.getId())) continue;
                    if (!rowboatLocations.containsKey(loc.getId())) {
                        rowboatLocations.put(loc.getId(), new ArrayList<>());
                    }
                    rowboatLocations.get(loc.getId()).add(loc);
                }
            }

            // The departure tile is the walkable (non-blocked) adjacent tile closest to the
            // object's geometric center: centerX = objX + sizeX/2, centerY = objY + sizeY/2.
            // This matches how OSRS pathfinds to interactable objects and is verified against
            // the known Angler's Retreat departure tiles (2471,2724 island; 2543,2844 mainland).
            System.out.println("id\tvarbitId\ttransforms\tobjX\tobjY\tplane\tori\tsizeX\tsizeY\tdeparture");
            for (ObjectDefinition p : rowboatParents) {
                List<Location> locs = rowboatLocations.containsKey(p.getId())
                    ? rowboatLocations.get(p.getId()) : new ArrayList<>();
                for (Location loc : locs) {
                    Position pos = loc.getPosition();
                    int ori = loc.getOrientation();
                    // Orientation 1 and 3 rotate 90°, swapping X and Y extents.
                    int sizeX = (ori == 1 || ori == 3) ? p.getSizeY() : p.getSizeX();
                    int sizeY = (ori == 1 || ori == 3) ? p.getSizeX() : p.getSizeY();
                    int ox = pos.getX(), oy = pos.getY(), oz = pos.getZ();

                    // Geometric center of the footprint (integer division, matching wiki map endpoints).
                    int centerX = ox + sizeX / 2;
                    int centerY = oy + sizeY / 2;

                    // Enumerate all four faces of the footprint, keep only non-blocked tiles.
                    int bestX = -1, bestY = -1;
                    double bestDist = Double.MAX_VALUE;
                    int[][] candidates = new int[sizeX * 2 + sizeY * 2][2];
                    int nc = 0;
                    for (int dy = 0; dy < sizeY; dy++) {
                        candidates[nc++] = new int[]{ox - 1, oy + dy};           // west
                        candidates[nc++] = new int[]{ox + sizeX, oy + dy};       // east
                    }
                    for (int dx = 0; dx < sizeX; dx++) {
                        candidates[nc++] = new int[]{ox + dx, oy - 1};           // south
                        candidates[nc++] = new int[]{ox + dx, oy + sizeY};       // north
                    }
                    for (int i = 0; i < nc; i++) {
                        int cx = candidates[i][0], cy = candidates[i][1];
                        long key = ((long)cx << 16) | ((long)cy << 2) | oz;
                        if (blockedTiles.contains(key)) continue;
                        double dx = cx - centerX, dy = cy - centerY;
                        double dist = dx * dx + dy * dy;
                        if (dist < bestDist) { bestDist = dist; bestX = cx; bestY = cy; }
                    }

                    String departure = bestX == -1 ? "?" : bestX + "," + bestY + "," + oz;
                    System.out.println(p.getId()
                        + "\t" + p.getVarbitID()
                        + "\t" + Arrays.toString(p.getConfigChangeDest())
                        + "\t" + ox + "\t" + oy + "\t" + oz
                        + "\t" + ori + "\t" + sizeX + "\t" + sizeY
                        + "\t" + departure);
                }
            }

            // 5. Sailors' Marker dump. Print the marker def's own varbit/varp,
            //    any multi-loc parent that transforms into it, and every
            //    world placement of the marker object id.
            System.out.println();
            System.out.println("=== Sailors' Markers ===");
            System.out.println("id\tname\tvarbitId\tvarpId\ttransforms");
            Set<Integer> markerSet = new HashSet<>();
            for (int id : SAILORS_MARKER_IDS) markerSet.add(id);
            List<ObjectDefinition> markerParents = new ArrayList<>();
            for (ObjectDefinition def : objectManager.getObjects()) {
                if (markerSet.contains(def.getId())) {
                    System.out.println(def.getId()
                        + "\t" + def.getName()
                        + "\t" + def.getVarbitID()
                        + "\t" + def.getVarpID()
                        + "\t" + Arrays.toString(def.getConfigChangeDest()));
                }
                int[] dest = def.getConfigChangeDest();
                if (dest == null) continue;
                for (int d : dest) {
                    if (markerSet.contains(d)) { markerParents.add(def); break; }
                }
            }
            System.out.println();
            System.out.println("=== Sailors' Marker parents (multi-loc) ===");
            System.out.println("id\tname\tvarbitId\tvarpId\ttransforms");
            for (ObjectDefinition p : markerParents) {
                System.out.println(p.getId()
                    + "\t" + p.getName()
                    + "\t" + p.getVarbitID()
                    + "\t" + p.getVarpID()
                    + "\t" + Arrays.toString(p.getConfigChangeDest()));
            }
            System.out.println();
            System.out.println("=== Sailors' Marker placements ===");
            System.out.println("id\tx\ty\tplane");
            Set<Integer> markerPlusParentIds = new HashSet<>(markerSet);
            for (ObjectDefinition p : markerParents) markerPlusParentIds.add(p.getId());
            for (Region region : regionLoader.getRegions()) {
                for (Location loc : region.getLocations()) {
                    if (!markerPlusParentIds.contains(loc.getId())) continue;
                    Position pos = loc.getPosition();
                    System.out.println(loc.getId() + "\t" + pos.getX() + "\t" + pos.getY() + "\t" + pos.getZ());
                }
            }

            // 5. Sailors' amulet item dump. Walk every item named "Sailors' amulet..." and print
            //    inventoryActions (interfaceOptions), subops, params, and countObj/countCo chains.
            //    Teleport-gating varbits on OSRS items are typically stored in the item's params map
            //    (int-keyed) or implied by the subops that a cs2 script toggles.
            System.out.println();
            System.out.println("=== Sailors' amulet items ===");
            ItemManager itemManager = new ItemManager(store);
            itemManager.load();
            List<ItemDefinition> matches = new ArrayList<>();
            for (ItemDefinition item : itemManager.getItems()) {
                String name = item.getName();
                if (name == null) continue;
                if (name.toLowerCase().contains("sailors' amulet") || name.toLowerCase().contains("sailor's amulet")) {
                    matches.add(item);
                }
            }
            matches.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
            for (ItemDefinition item : matches) {
                System.out.println();
                System.out.println("-- id=" + item.getId() + " name=\"" + item.getName() + "\" --");
                System.out.println("  examine:          " + item.getExamine());
                System.out.println("  notedID:          " + item.notedID);
                System.out.println("  placeholderId:    " + item.placeholderId);
                System.out.println("  boughtId:         " + item.boughtId);
                System.out.println("  countCo:          " + Arrays.toString(item.getCountCo()));
                System.out.println("  countObj:         " + Arrays.toString(item.getCountObj()));
                System.out.println("  interfaceOptions: " + Arrays.toString(item.getInterfaceOptions()));
                String[][] subops = item.getSubops();
                if (subops != null) {
                    for (int i = 0; i < subops.length; i++) {
                        System.out.println("  subops[" + i + "]:        " + Arrays.toString(subops[i]));
                    }
                }
                Map<Integer, Object> params = item.params;
                if (params != null && !params.isEmpty()) {
                    // Sort params by key for readability.
                    List<Integer> keys = new ArrayList<>(params.keySet());
                    keys.sort(Integer::compareTo);
                    for (Integer k : keys) {
                        System.out.println("  param[" + k + "] = " + params.get(k));
                    }
                }
            }

            // Note: the cs2 script referenced by param[2257] on item 32399 handles the Teleport
            // submenu gating, but the bundled runelite-cache 1.12.24 ScriptLoader can't
            // reliably decode current-rev scripts, so we don't extract the unlock varbit here.
            // The subop indices (0=Pandemonium, 1=Port Roberts, 4=Deepfin Point) and the marker
            // coordinates from section 4 are enough for a contributor to identify the varbit
            // in-game via RuneLite's varbit inspector.
        }
    }

    private static String requiredProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing required system property -D" + key + "=...");
        }
        return value;
    }
}
