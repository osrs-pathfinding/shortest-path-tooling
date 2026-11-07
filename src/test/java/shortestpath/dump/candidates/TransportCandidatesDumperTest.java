package shortestpath.dump.candidates;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.fs.Store;
import net.runelite.client.game.AgilityShortcut;
import org.junit.Assume;
import org.junit.Test;
import shortestpath.candidates.Connector;
import shortestpath.candidates.ConnectorSnapshot;
import shortestpath.candidates.Coverage;
import shortestpath.candidates.Report;
import shortestpath.candidates.Scope;
import shortestpath.candidates.TransportCandidates;
import shortestpath.candidates.Walking;
import shortestpath.dump.CacheUtils;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PluginResources;
import shortestpath.pathfinder.astar.RoutingStaticBuilder;
import shortestpath.transport.ResourceLoader;
import shortestpath.transport.Transport;

/**
 * Builds the transport candidates snapshot ({@link ConnectorSnapshot}) from an OSRS cache, then judges
 * it against the plugin ({@link TransportCandidates}) and writes a report, so a run can be checked
 * before its snapshot is committed. The snapshot holds the cache facts the candidate sets need:
 *
 * <ul>
 * <li>uncovered connectors with their far ends ({@link CacheConnectors}, {@link ConnectorPairing});</li>
 * <li>world map links ({@link WorldMapLinks});</li>
 * <li>agility shortcut icons ({@link ShortcutIcons});</li>
 * <li>dungeon entrance icons ({@link DungeonIcons}).</li>
 * </ul>
 *
 * Run via:
 *   ./gradlew transportCandidates \
 *     -PtransportCandidatesCacheDir=$PWD/cache \
 *     -PtransportCandidatesXteaPath=$PWD/keys.json \
 *     -PtransportCandidatesCacheLabel="openrs2 2757, build 242"
 *
 * Output: routing/src/main/resources/candidates/connectors.json (the snapshot) and
 * build/transport-candidates.txt (the report).
 */
public class TransportCandidatesDumperTest {
    @Test
    public void dumpTransportCandidates() throws Exception {
        Assume.assumeTrue("Enable with -Dtransport.candidates.dump=true", Boolean.getBoolean("transport.candidates.dump"));
        String cacheDir = CacheUtils.requiredProperty("transport.candidates.cacheDir");
        String xteaPath = CacheUtils.requiredProperty("transport.candidates.xteaPath");
        String cacheLabel = System.getProperty("transport.candidates.cacheLabel", "unknown");
        String plugin = gitHead(System.getProperty("transport.candidates.pluginDir", "shortest-path"));
        Path snapshotPath = Paths.get(System.getProperty("transport.candidates.output",
            "routing/src/main/resources/candidates/connectors.json"));
        Path scopePath = Paths.get(System.getProperty("transport.candidates.scope", "routing/src/main/resources" + Scope.RESOURCE));
        Path reportPath = Paths.get(System.getProperty("transport.candidates.report", "build/transport-candidates.txt"));

        Map<Integer, Set<Transport>> transports = PluginResources.transports();
        Coverage coverage = new Coverage(transports);
        CacheConnectors cache = new CacheConnectors();
        List<ConnectorSnapshot.MapLink> links;
        try (Store store = CacheUtils.openStore(cacheDir)) {
            ObjectManager objects = new ObjectManager(store);
            objects.load();
            links = WorldMapLinks.read(store);
            cache.scan(objects, CacheUtils.loadRegions(store, CacheUtils.loadXteaKeys(xteaPath)), coverage, ShortcutIcons.obstacleIds());
        }
        ShortcutIcons shortcuts = new ShortcutIcons(cache);
        DungeonIcons dungeons = new DungeonIcons(cache, shortcuts.markerArea);
        Map<String, int[]> precision = new ConnectorPairing(cache.placements).pairAll(cache.placements, transports);

        ConnectorSnapshot snapshot = new ConnectorSnapshot();
        snapshot.cache = cacheLabel;
        snapshot.plugin = plugin;
        snapshot.shortcutMarker = shortcuts.marker;
        snapshot.mapLinks = links;
        snapshot.shortcutIcons = shortcuts.icons;
        snapshot.dungeonMarker = dungeons.marker;
        snapshot.dungeonIcons = dungeons.icons;
        cache.placements.stream().filter(p -> !p.covered).map(p -> p.connector)
            .sorted(Comparator.comparingInt((Connector c) -> c.plane).thenComparingInt(c -> c.x).thenComparingInt(c -> c.y)
                .thenComparingInt(c -> c.objectId))
            .forEach(snapshot.connectors::add);
        snapshot.extraction.put("placements scanned", cache.scanned);
        snapshot.extraction.put("door and gate walls left out", cache.doors);
        snapshot.extraction.put("connector placements", cache.placements.size());
        snapshot.extraction.put("covered by a transport row", (int) cache.placements.stream().filter(p -> p.covered).count());
        snapshot.extraction.put("uncovered (kept)", snapshot.connectors.size());
        snapshot.extraction.put("uncovered with a partner", (int) snapshot.connectors.stream().filter(c -> c.partner != null).count());
        if (snapshotPath.getParent() != null) Files.createDirectories(snapshotPath.getParent());
        Files.writeString(snapshotPath, snapshot.toJson());

        Walking walking = Walking.of(RoutingStaticBuilder.walkingGraph(new CollisionMap(PluginResources.map()), ResourceLoader.transports()));
        Scope scope;
        try (InputStream stream = Files.newInputStream(scopePath)) {
            scope = Scope.read(stream);
        }
        Report report = TransportCandidates.judge(snapshot, walking, transports, PluginResources.destinations(), scope);
        writeReport(reportPath, cacheLabel, plugin, cache, shortcuts, dungeons, links, snapshot, precision, report);
        System.out.println("Snapshot: " + snapshotPath + " (" + snapshot.connectors.size() + " connectors); report: " + reportPath);
        report.funnel.forEach((k, v) -> System.out.println("  " + k + ": " + v));
    }

    private static void writeReport(Path path, String cacheLabel, String plugin, CacheConnectors cache, ShortcutIcons shortcuts,
                                    DungeonIcons dungeons,
                                    List<ConnectorSnapshot.MapLink> links, ConnectorSnapshot snapshot,
                                    Map<String, int[]> precision, Report report) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            out.println("# Transport candidates: " + cacheLabel + ", plugin " + plugin);
            out.println("\n## Lexicon (option: uses in plugin rows)");
            cache.lexiconUses.forEach((option, uses) -> out.println((cache.lexicon.contains(option) ? "  + " : "  - ") + option + ": " + uses));
            out.println("\n## Agility shortcut icon");
            out.println("  " + shortcuts.marker.field + ": " + shortcuts.marker.icons + " placements, " + shortcuts.marker.iconsNearRows
                + " within " + ShortcutIcons.ROW_REACH + " tiles of a shortcut row's origin");
            out.println("  RuneLite AgilityShortcut entries: " + AgilityShortcut.values().length + "; icons matched to one: "
                + shortcuts.matched + "; entries with no icon within " + ShortcutIcons.RUNELITE_REACH + ": "
                + (AgilityShortcut.values().length - shortcuts.known.size()));
            out.println("\n## Dungeon entrance icon");
            out.println("  " + dungeons.marker.field + ": " + dungeons.marker.icons + " placements, " + dungeons.marker.iconsNearRows
                + " within " + DungeonIcons.ROW_REACH + " tiles of a going-down or going-in row's origin");
            out.println("\n## World map links");
            out.println("  links: " + links.size() + " (both directions)");
            out.println("\n## Extraction");
            snapshot.extraction.forEach((k, v) -> out.println("  " + k + ": " + v));
            out.println("\n## Pairing rules on covered connectors (partner where the row lands / elsewhere)");
            precision.forEach((rule, n) -> out.println("  " + rule + ": " + n[0] + " right, " + n[1] + " wrong"));
            out.println("\n## Live judgement funnel");
            report.funnel.forEach((k, v) -> out.println("  " + k + ": " + v));
            out.println("\n## Items");
            for (Report.Item item : report.items) {
                out.println("- " + item.id + " " + item.title + (item.areaTiles == null ? "" : " (" + item.areaTiles + " tiles, bounds "
                    + Arrays.toString(item.bounds) + ", " + item.contains + ")"));
                for (Report.Candidate c : item.connectors) {
                    Connector k = c.connector;
                    out.println("    " + c.role + " " + k.option() + " " + k.name + " " + k.objectId + " at " + k.x + " " + k.y + " "
                        + k.plane + (k.partner == null ? "" : " -> " + k.partner.option + " " + k.partner.name + " " + k.partner.objectId
                        + " at " + k.partner.x + " " + k.partner.y + " " + k.partner.plane + " [" + k.partner.rule + "]"));
                }
            }
        }
    }

    private static String gitHead(String dir) {
        try {
            Process git = new ProcessBuilder("git", "-C", dir, "rev-parse", "HEAD").redirectErrorStream(true).start();
            String out = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return git.waitFor() == 0 ? out : "unknown";
        } catch (IOException e) {
            return "unknown";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }
}
