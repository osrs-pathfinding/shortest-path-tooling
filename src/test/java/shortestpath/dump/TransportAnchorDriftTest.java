package shortestpath.dump;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.XteaKeyManager;
import org.junit.Assume;
import org.junit.Test;
import shortestpath.poh.PohService;
import shortestpath.transport.parser.TransportRecord;

/**
 * Compares every committed transport row's {@code menuOption menuTarget objectID}
 * anchor against a freshly downloaded OSRS cache and reports anchors whose
 * object no longer exists or no longer exposes the named menu action.
 *
 * <p>Transports anchor to game objects by object id + menu option; a weekly
 * game update can move, rename, or remove an object while the TSV row still
 * looks syntactically fine. Only a live-cache comparison catches that drift.
 *
 * <p>The scan is <b>advisory by construction</b>: findings populate a grouped
 * triage report and the stdout summary but never fail the test — post-update
 * XTEA key lag and legitimate object churn make nonzero diffs expected. Only
 * genuine infrastructure problems (missing properties, unreadable cache or
 * TSV root) surface as test errors.
 *
 * <p>Anchors whose trailing token is not a parseable object id land in the
 * report's {@code unparseable} bucket rather than crashing the scan; the
 * trailing-integer convention is documented but unenforced.
 *
 * <p>Rows with an origin are also checked for <b>placement</b>: the object (or
 * a multi-loc parent that turns into it) should stand on the origin's plane
 * within {@link #NEAR} tiles of it. When it doesn't, the report names any
 * object within {@link #AT_ORIGIN} tiles of the origin with the anchor's menu
 * option and name, which is usually the right id (a swapped pair, a typo, an
 * object replaced in a game update). Rows are set aside rather than reported
 * when the origin is the plugin's player-owned house landing
 * ({@code PohService.POH_LANDING_X/Y}, built at run time), when no interactive
 * object stands near the origin at all (other instances), when an object of the anchor's name stands at the
 * origin under another id but none offers the option (an opened door or
 * trapdoor the server swaps in), or when the anchor's name is not the
 * object's and nothing matching is near (NPCs share the column, and NPC and
 * object ids overlap).
 *
 * Run via:
 *   ./gradlew transportAnchorDrift \
 *     -PtransportDriftCacheDir=$PWD/cache \
 *     -PtransportDriftXteaPath=$PWD/keys.json
 *
 * Output: build/transport-drift.txt and console.
 */
public class TransportAnchorDriftTest {

    private static final class Anchor {
        String file;
        int line;
        String cell;        // raw anchor cell text
        String menuOption;
        int objectId = -1;
        String menuTarget;
        int[] origin;       // x, y, plane, or null without an origin
        String parseError;  // non-null when the cell failed to parse
    }

    /** Tiles from the origin within which the anchor's object counts as placed there. */
    static final int NEAR = 10;
    /** Tiles from the origin within which another object is suggested as the anchor's. */
    static final int AT_ORIGIN = 2;

    @Test
    public void scanTransportAnchors() throws Exception {
        Assume.assumeTrue(
            "Enable with -Dtransport.drift.scan=true",
            Boolean.getBoolean("transport.drift.scan"));

        String cacheDir = CacheUtils.requiredProperty("transport.drift.cacheDir");
        String xteaPath = CacheUtils.requiredProperty("transport.drift.xteaPath");
        String tsvDir = CacheUtils.requiredProperty("transport.drift.tsvDir");
        String outPath = System.getProperty("transport.drift.outPath", "build/transport-drift.txt");

        Path transportsDir = Paths.get(tsvDir, "transports");
        if (!Files.isDirectory(transportsDir)) {
            throw new IllegalStateException("Transport TSV directory not readable: " + transportsDir);
        }

        List<Anchor> anchors = readAnchors(transportsDir);

        XteaKeyManager xteaKeyManager = CacheUtils.loadXteaKeys(xteaPath);

        Path outFile = Paths.get(outPath);
        if (outFile.getParent() != null) {
            Files.createDirectories(outFile.getParent());
        }

        try (Store store = CacheUtils.openStore(cacheDir);
             PrintWriter out = new PrintWriter(outFile.toFile())) {

            ObjectManager objectManager = new ObjectManager(store);
            objectManager.load();

            RegionLoader regionLoader = CacheUtils.loadRegions(store, xteaKeyManager);
            regionLoader.calculateBounds();

            List<String> missingObject = new ArrayList<>();
            List<String> missingAction = new ArrayList<>();
            List<String> unparseable = new ArrayList<>();
            List<Anchor> actionFindings = new ArrayList<>();
            Map<Anchor, List<Integer>> actionFindingIds = new HashMap<>();
            Set<Integer> placementIds = new HashSet<>();
            int scanned = 0;

            for (Anchor a : anchors) {
                if (a.parseError != null) {
                    unparseable.add(a.file + ":" + a.line + "\t" + a.cell + "\t" + a.parseError);
                    continue;
                }
                scanned++;

                ObjectDefinition def = objectManager.getObject(a.objectId);
                if (def == null) {
                    missingObject.add(a.file + ":" + a.line + "\t" + a.cell
                        + "\tobject " + a.objectId + " absent from cache");
                    continue;
                }

                if (CacheUtils.hasAction(def, a.menuOption)) {
                    continue;
                }

                // Multi-loc parent resolution: the TSV objectID may be the child
                // a placed parent object transforms into via varbit/varp.
                List<ObjectDefinition> parents =
                    CacheUtils.collectMultiLocParents(objectManager, a.objectId);
                boolean parentHasAction = false;
                for (ObjectDefinition p : parents) {
                    if (CacheUtils.hasAction(p, a.menuOption)) {
                        parentHasAction = true;
                        break;
                    }
                }
                if (parentHasAction) {
                    continue;
                }

                List<Integer> ids = new ArrayList<>();
                ids.add(def.getId());
                for (ObjectDefinition p : parents) {
                    ids.add(p.getId());
                }
                actionFindings.add(a);
                actionFindingIds.put(a, ids);
                placementIds.addAll(ids);
            }

            // One region sweep annotates findings with first world placements.
            Map<Integer, int[]> placement =
                CacheUtils.collectFirstPlacementByObjectId(regionLoader, placementIds);

            for (Anchor a : actionFindings) {
                StringBuilder detail = new StringBuilder();
                detail.append("object ").append(a.objectId)
                    .append(" lacks menu option \"").append(a.menuOption).append("\"");
                String where = null;
                for (int id : actionFindingIds.get(a)) {
                    int[] pos = placement.get(id);
                    if (pos != null) {
                        where = id + " at " + pos[0] + "," + pos[1] + "," + pos[2];
                        break;
                    }
                }
                detail.append(where == null ? " — no world placement" : " — " + where);
                missingAction.add(a.file + ":" + a.line + "\t" + a.cell + "\t" + detail);
            }

            Placements placements = new Placements(regionLoader, objectManager);
            List<String> notAtOrigin = new ArrayList<>();
            int setAside = 0;
            for (Anchor a : anchors) {
                if (a.parseError != null || a.origin == null || objectManager.getObject(a.objectId) == null) {
                    continue;
                }
                String finding = placements.check(a, objectManager);
                if (finding == null) {
                    continue;
                }
                if (finding.isEmpty()) {
                    setAside++;
                } else {
                    notAtOrigin.add(a.file + ":" + a.line + "\t" + a.cell + "\t" + finding);
                }
            }

            String summary = "anchors scanned=" + scanned
                + " missing-object=" + missingObject.size()
                + " missing-action=" + missingAction.size()
                + " not-at-origin=" + notAtOrigin.size()
                + " (set aside " + setAside + ")"
                + " unparseable=" + unparseable.size();

            out.println("=== missing-object (" + missingObject.size() + ") ===");
            for (String s : missingObject) {
                out.println(s);
            }
            out.println();
            out.println("=== missing-action (" + missingAction.size() + ") ===");
            for (String s : missingAction) {
                out.println(s);
            }
            out.println();
            out.println("=== not-at-origin (" + notAtOrigin.size() + ") ===");
            for (String s : notAtOrigin) {
                out.println(s);
            }
            out.println();
            out.println("=== unparseable (" + unparseable.size() + ") ===");
            for (String s : unparseable) {
                out.println(s);
            }
            out.println();
            out.println(summary);

            System.out.println();
            System.out.println(summary);
            System.out.println("report -> " + outPath);
        }
    }

    /**
     * Reads every {@code *.tsv} under {@code transportsDir} and extracts the
     * {@code menuOption menuTarget objectID} anchor from each data row.
     * Files without the anchor column contribute nothing; rows with an empty
     * anchor cell carry no object anchor and are skipped.
     */
    private static List<Anchor> readAnchors(Path transportsDir) throws IOException {
        List<Anchor> anchors = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(transportsDir, "*.tsv")) {
            for (Path p : stream) {
                files.add(p);
            }
        }
        files.sort(null);

        for (Path file : files) {
            List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty()) {
                continue;
            }
            String header = lines.get(0);
            if (header.startsWith("# ")) {
                header = header.substring(2);
            } else if (header.startsWith("#")) {
                header = header.substring(1);
            }
            String[] cols = header.split("\t", -1);
            int objectInfoCol = -1;
            int originCol = -1;
            for (int i = 0; i < cols.length; i++) {
                if (cols[i].trim().equals(TransportRecord.Fields.OBJECT_INFO)) {
                    objectInfoCol = i;
                } else if (cols[i].trim().equals(TransportRecord.Fields.ORIGIN)) {
                    originCol = i;
                }
            }
            if (objectInfoCol < 0) {
                continue; // file has no anchor column
            }

            String name = file.getFileName().toString();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isEmpty() || line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length <= objectInfoCol) {
                    Anchor a = new Anchor();
                    a.file = name;
                    a.line = i + 1;
                    a.cell = "";
                    a.parseError = "row has fewer columns than the anchor column index";
                    anchors.add(a);
                    continue;
                }
                String cell = fields[objectInfoCol].trim();
                if (cell.isEmpty()) {
                    continue; // no object anchor on this row
                }
                Anchor a = new Anchor();
                a.file = name;
                a.line = i + 1;
                a.cell = cell;
                parseAnchor(a);
                if (originCol >= 0 && originCol < fields.length) {
                    a.origin = parseOrigin(fields[originCol]);
                }
                anchors.add(a);
            }
        }
        return anchors;
    }

    /**
     * Splits an anchor cell into menu option + object id. The convention is
     * {@code <menuOption> <menuTarget...> <objectID>}: the first whitespace-
     * separated token is the menu option and the trailing token is the object
     * id. Cells that don't fit land in the report's unparseable bucket.
     */
    private static void parseAnchor(Anchor a) {
        String[] tokens = a.cell.split("\\s+");
        if (tokens.length < 2) {
            a.parseError = "anchor cell has no trailing object id token";
            return;
        }
        try {
            a.objectId = Integer.parseInt(tokens[tokens.length - 1]);
        } catch (NumberFormatException e) {
            a.parseError = "trailing token \"" + tokens[tokens.length - 1]
                + "\" is not an object id";
            return;
        }
        a.menuOption = tokens[0];
        a.menuTarget = String.join(" ", java.util.Arrays.copyOfRange(tokens, 1, tokens.length - 1));
    }

    /** An {@code x y plane} origin, or null for an empty or malformed one. */
    private static int[] parseOrigin(String cell) {
        String[] t = cell.trim().split("\\s+");
        if (t.length != 3) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Every interactive object the cache places (one with an action, or a
     * multi-loc that turns into others), by id and by each id it can turn
     * into, and by 8x8-tile bucket. Planes are the ones the game shows: under
     * a bridge (tile setting 0x2 on plane 1) objects move down one.
     */
    static final class Placements {
        /** id, x, y, plane, sizeX, sizeY. */
        private final Map<Integer, List<int[]>> byId = new HashMap<>();
        private final Map<Long, List<int[]>> byBucket = new HashMap<>();

        Placements(RegionLoader regions, ObjectManager objects) {
            for (net.runelite.cache.region.Region region : regions.getRegions()) {
                for (net.runelite.cache.region.Location loc : region.getLocations()) {
                    ObjectDefinition def = objects.getObject(loc.getId());
                    if (def == null || (!hasAnyAction(def) && def.getConfigChangeDest() == null)) {
                        continue;
                    }
                    net.runelite.cache.region.Position p = loc.getPosition();
                    int lx = p.getX() - region.getBaseX();
                    int ly = p.getY() - region.getBaseY();
                    boolean bridge = (region.getTileSetting(1, lx, ly) & 2) != 0;
                    int plane = bridge && p.getZ() > 0 ? p.getZ() - 1 : p.getZ();
                    int[] placed = {loc.getId(), p.getX(), p.getY(), plane,
                        CacheUtils.effectiveSizeX(def, loc.getOrientation()),
                        CacheUtils.effectiveSizeY(def, loc.getOrientation())};
                    byId.computeIfAbsent(loc.getId(), k -> new ArrayList<>()).add(placed);
                    if (def.getConfigChangeDest() != null) {
                        for (int form : def.getConfigChangeDest()) {
                            if (form >= 0 && form != loc.getId()) {
                                byId.computeIfAbsent(form, k -> new ArrayList<>()).add(placed);
                            }
                        }
                    }
                    byBucket.computeIfAbsent(bucket(p.getX() >> 3, p.getY() >> 3, plane), k -> new ArrayList<>()).add(placed);
                }
            }
        }

        /**
         * Null when the anchor's object stands at its origin; empty when the
         * row is set aside; otherwise what is wrong and the likely fix.
         */
        String check(Anchor a, ObjectManager objects) {
            int x = a.origin[0], y = a.origin[1], z = a.origin[2];
            if (x == PohService.POH_LANDING_X && y == PohService.POH_LANDING_Y) {
                return "";
            }
            int[] nearest = null;
            int nearestDistance = Integer.MAX_VALUE;
            for (int[] placed : byId.getOrDefault(a.objectId, List.of())) {
                int d = distance(x, y, placed) + (placed[3] == z ? 0 : 100_000);
                if (d < nearestDistance) {
                    nearest = placed;
                    nearestDistance = d;
                }
            }
            if (nearestDistance <= NEAR) {
                return null;
            }
            List<int[]> around = around(x, y, z, NEAR);
            if (around.isEmpty()) {
                return "";
            }
            String target = a.menuTarget.toLowerCase();
            List<Integer> fix = new ArrayList<>();
            boolean sameName = false;
            for (int[] placed : around) {
                if (distance(x, y, placed) > AT_ORIGIN) {
                    continue;
                }
                for (ObjectDefinition form : forms(placed[0], objects)) {
                    if (form.getName() != null && form.getName().equalsIgnoreCase(target)) {
                        sameName = true;
                        if (CacheUtils.hasAction(form, a.menuOption) && !fix.contains(placed[0])) {
                            fix.add(placed[0]);
                        }
                    }
                }
            }
            boolean named = false;
            for (ObjectDefinition form : forms(a.objectId, objects)) {
                named |= form.getName() != null && form.getName().equalsIgnoreCase(target);
            }
            if (fix.isEmpty() && (sameName && nearest == null || !named)) {
                return "";
            }
            String where = nearest == null ? "never placed"
                : "nearest at " + nearest[1] + "," + nearest[2] + "," + nearest[3]
                    + (nearest[3] == z ? ", " + distance(x, y, nearest) + " tiles away" : " (another plane)");
            return "origin " + x + "," + y + "," + z + ": " + where
                + (fix.isEmpty() ? "" : "; at the origin: " + fix);
        }

        /** The object and every form it can turn into. */
        private static List<ObjectDefinition> forms(int id, ObjectManager objects) {
            List<ObjectDefinition> forms = new ArrayList<>();
            ObjectDefinition def = objects.getObject(id);
            if (def == null) {
                return forms;
            }
            forms.add(def);
            if (def.getConfigChangeDest() != null) {
                for (int form : def.getConfigChangeDest()) {
                    ObjectDefinition other = form >= 0 ? objects.getObject(form) : null;
                    if (other != null) {
                        forms.add(other);
                    }
                }
            }
            return forms;
        }

        private List<int[]> around(int x, int y, int z, int reach) {
            List<int[]> found = new ArrayList<>();
            // Footprints reach up to a few tiles east and north of their bucket.
            for (int bx = (x - reach - 8) >> 3; bx <= (x + reach) >> 3; bx++) {
                for (int by = (y - reach - 8) >> 3; by <= (y + reach) >> 3; by++) {
                    for (int[] placed : byBucket.getOrDefault(bucket(bx, by, z), List.of())) {
                        if (distance(x, y, placed) <= reach) {
                            found.add(placed);
                        }
                    }
                }
            }
            return found;
        }

        /** Chebyshev distance from a tile to a placement's footprint. */
        static int distance(int x, int y, int[] placed) {
            int dx = Math.max(Math.max(placed[1] - x, 0), x - (placed[1] + placed[4] - 1));
            int dy = Math.max(Math.max(placed[2] - y, 0), y - (placed[2] + placed[5] - 1));
            return Math.max(dx, dy);
        }

        private static long bucket(int bx, int by, int plane) {
            return ((long) bx << 32) | ((long) (by & 0xFFFF) << 8) | plane;
        }

        private static boolean hasAnyAction(ObjectDefinition def) {
            if (def.getOps() == null || def.getOps().ops == null) {
                return false;
            }
            for (net.runelite.cache.EntityOpsDefinition.Op op : def.getOps().ops) {
                if (op != null && op.text != null && !op.text.isBlank()) {
                    return true;
                }
            }
            return false;
        }
    }
}
