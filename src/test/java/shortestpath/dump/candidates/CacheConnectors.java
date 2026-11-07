package shortestpath.dump.candidates;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.runelite.cache.EntityOpsDefinition;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import shortestpath.candidates.Connector;
import shortestpath.candidates.Coverage;
import shortestpath.dump.CacheUtils;
import shortestpath.transport.ResourceLoader;

/**
 * One pass over every object placement in the cache, collecting what the candidate sets are built from:
 *
 * <ul>
 * <li><b>Connectors:</b> placements of objects a player moves through. Their menu offers a
 * <em>connector option</em>: an option the plugin's {@code transports.tsv} and
 * {@code agility_shortcuts.tsv} rows use at least {@link #LEXICON_MIN_USES} times, minus
 * {@link #DENY}. Doors and gates (walls with {@code wallOrDoor}) are left out. Each is marked
 * covered when a transport row covers it ({@link Coverage}).</li>
 * <li><b>Map icons:</b> placements of objects with a map icon, from which {@link ShortcutIcons} picks
 * the agility shortcut icon.</li>
 * <li><b>Obstacles:</b> placements of the object ids in {@code obstacleIds} (RuneLite's shortcut obstacles).</li>
 * </ul>
 *
 * Multi-loc objects contribute their children's ids and options. Planes are lowered under bridge
 * tiles, as the collision dumper does, so placements line up with the collision map.
 */
final class CacheConnectors {
    /** Options used at least this often in the plugin's rows join the lexicon. */
    static final int LEXICON_MIN_USES = 3;
    /** Options that never mean "this moves you": skilling, looking, talking, doors. */
    static final Set<String> DENY = Set.of("open", "close", "use", "search", "mine", "chop-down", "channel", "board",
        "travel", "talk-to", "take", "pay", "pay-fare", "investigate", "read", "push", "pull", "pick-lock", "unlock",
        "knock-at", "ring", "operate", "examine", "inspect", "check", "look-at", "pray", "pray-at", "teleport", "activate",
        "quick-travel", "trade", "bank", "collect", "deposit", "fish", "cut", "chop", "excavate", "loot", "steal-from",
        "pick", "harvest", "light", "walk-here");

    /** A connector placement, with its pairing direction and whether a transport row covers it. */
    static final class Placement {
        final Connector connector;
        /** {@code ascend}, {@code descend}, {@code both}, or null when no pairing rule applies. */
        final String direction;
        final boolean covered;

        Placement(Connector connector, boolean covered) {
            this.connector = connector;
            this.direction = ConnectorPairing.direction(connector.options);
            this.covered = covered;
        }
    }

    /** Option (lower case) to uses in the plugin's rows, and the connector options among them. */
    final Map<String, Integer> lexiconUses = new TreeMap<>();
    final Set<String> lexicon = new HashSet<>();
    final List<Placement> placements = new ArrayList<>();
    /** {mapAreaId, x, y, plane} of each placement with a map icon. */
    final List<int[]> icons = new ArrayList<>();
    final Map<Integer, List<Connector>> obstaclesById = new HashMap<>();
    int scanned, doors;

    CacheConnectors() throws IOException {
        for (String file : List.of("/transports/transports.tsv", "/transports/agility_shortcuts.tsv")) {
            for (String[] cells : rows(file)) {
                if (cells.length < 3 || cells[2].isBlank()) continue;
                lexiconUses.merge(cells[2].trim().split("\\s+")[0].toLowerCase(), 1, Integer::sum);
            }
        }
        lexiconUses.forEach((option, uses) -> { if (uses >= LEXICON_MIN_USES && !DENY.contains(option)) lexicon.add(option); });
    }

    void scan(ObjectManager objects, RegionLoader regions, Coverage coverage, Set<Integer> obstacleIds) {
        for (Region region : regions.getRegions()) {
            if (region.getLocations() == null) continue;
            for (Location loc : region.getLocations()) {
                scanned++;
                ObjectDefinition def = objects.getObject(loc.getId());
                if (def == null) continue;
                List<ObjectDefinition> defs = family(objects, def);
                if (def.getMapAreaId() >= 0) {
                    Connector at = connector(loc, region, def, defs, List.of());
                    icons.add(new int[] {def.getMapAreaId(), at.x, at.y, at.plane});
                }
                if (obstacleIds.contains(loc.getId())) {
                    List<String> all = new ArrayList<>();
                    for (ObjectDefinition d : defs) all.addAll(ops(d));
                    obstaclesById.computeIfAbsent(loc.getId(), k -> new ArrayList<>()).add(connector(loc, region, def, defs, all));
                }
                Set<String> options = new LinkedHashSet<>();
                for (ObjectDefinition d : defs) for (String op : ops(d)) if (lexicon.contains(op.toLowerCase())) options.add(op);
                if (options.isEmpty()) continue;
                boolean wall = loc.getType() >= 0 && loc.getType() <= 3;
                if (wall && defs.stream().anyMatch(d -> d.getWallOrDoor() != 0)) { doors++; continue; }
                Connector c = connector(loc, region, def, defs, new ArrayList<>(options));
                placements.add(new Placement(c, coverage.covers(c)));
            }
        }
    }

    /** A placement as a connector, on the plane the collision map uses (bridge tiles lowered). */
    static Connector connector(Location loc, Region region, ObjectDefinition def, List<ObjectDefinition> defs, List<String> options) {
        Connector c = new Connector();
        c.objectId = loc.getId();
        c.ids = defs.stream().mapToInt(ObjectDefinition::getId).distinct().toArray();
        c.name = defs.stream().map(ObjectDefinition::getName).filter(n -> n != null && !n.equals("null")).findFirst().orElse("null");
        c.options = options;
        int lx = loc.getPosition().getX() - region.getBaseX(), ly = loc.getPosition().getY() - region.getBaseY();
        int z = loc.getPosition().getZ();
        boolean bridge = (region.getTileSetting(1, lx, ly) & 2) != 0;
        c.x = loc.getPosition().getX();
        c.y = loc.getPosition().getY();
        c.plane = bridge && z > 0 ? z - 1 : z;
        c.sizeX = CacheUtils.effectiveSizeX(def, loc.getOrientation());
        c.sizeY = CacheUtils.effectiveSizeY(def, loc.getOrientation());
        return c;
    }

    /** The definition and every definition it turns into. */
    static List<ObjectDefinition> family(ObjectManager objects, ObjectDefinition def) {
        List<ObjectDefinition> defs = new ArrayList<>();
        defs.add(def);
        if (def.getConfigChangeDest() != null) {
            for (int id : def.getConfigChangeDest()) {
                ObjectDefinition child = id < 0 ? null : objects.getObject(id);
                if (child != null && !defs.contains(child)) defs.add(child);
            }
        }
        return defs;
    }

    static List<String> ops(ObjectDefinition def) {
        List<String> ops = new ArrayList<>();
        EntityOpsDefinition entity = def.getOps();
        if (entity == null || entity.ops == null) return ops;
        for (EntityOpsDefinition.Op op : entity.ops) if (op != null && op.text != null && !op.text.isBlank()) ops.add(op.text);
        return ops;
    }

    /** The data rows of a plugin TSV, split into cells. */
    static List<String[]> rows(String resource) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream stream = Objects.requireNonNull(ResourceLoader.class.getResourceAsStream(resource), resource)) {
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                rows.add(line.split("\t", -1));
            }
        }
        return rows;
    }
}
