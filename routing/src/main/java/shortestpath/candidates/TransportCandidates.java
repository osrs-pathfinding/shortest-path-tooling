package shortestpath.candidates;

import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.transport.Transport;

/**
 * Transport candidates: connections the game shows that the plugin has no transport for, found by
 * program from the cache ({@link ConnectorSnapshot}, written by tooling's {@code transportCandidates}
 * task) and judged against whatever plugin data is loaded, so the list follows the data as it changes.
 * See docs/transport-candidates.md.
 *
 * <p>Four candidate sets, each explained on its class:
 * <ul>
 * <li>{@link UnreachedAreas}: areas nothing reaches, with the connectors and world map links that
 * would join them to reached ground;</li>
 * <li>{@link AgilityShortcuts}: agility shortcut map icons with no transport nearby;</li>
 * <li>{@link MapLinks}: world map links between reached areas with no transport from near the icon
 * into the other end's area;</li>
 * <li>{@link DungeonEntrances}: dungeon entrance map icons with no transport nearby.</li>
 * </ul>
 * Everything they set aside is counted in the report's funnel, not listed.
 */
public final class TransportCandidates {
    public static final String RULE = "Lists four things. An unreached area: a walking area of at least "
        + UnreachedAreas.MIN_AREA_TILES + " tiles that no chain of transports reaches from Lumbridge (ignoring requirements), "
        + "with the cache connectors (ladders, stairs, trapdoors, cave entrances) or world map links that would join it to "
        + "reached ground and have no transport row. An agility shortcut: the game's shortcut map icon beside reached ground "
        + "with no transport within " + AgilityShortcuts.COVER + " tiles. An unmodelled map link: a world map link (a dungeon's "
        + "entrance icon and the dungeon) between two reached areas with no transport starting within " + MapLinks.ROW_REACH
        + " tiles of the icon and landing in the other end's area. A dungeon entrance: the game's dungeon map icon on reached "
        + "ground with no transport within " + DungeonEntrances.COVER + " tiles.";

    private TransportCandidates() { }

    /**
     * Judges the snapshot against loaded plugin data: its {@code transports} (origin to transports),
     * its walking graph, and its location files ({@code destinations}, key to packed tiles), which are
     * counted inside unreached areas.
     */
    public static Report judge(ConnectorSnapshot snapshot, Walking walking, Map<Integer, Set<Transport>> transports,
                               Map<String, Set<Integer>> destinations, Scope scope) {
        Report report = new Report();
        report.rule = RULE;
        report.cache = snapshot.cache;
        report.snapshotPlugin = snapshot.plugin;
        report.extraction = snapshot.extraction;
        report.shortcutMarker = snapshot.shortcutMarker;
        report.dungeonMarker = snapshot.dungeonMarker;
        Coverage coverage = new Coverage(transports);
        List<Report.Item> areas = UnreachedAreas.list(snapshot, walking, coverage, scope, transports, destinations, report.funnel);
        report.items.addAll(areas);
        report.items.addAll(AgilityShortcuts.list(snapshot, walking, coverage, scope, report.funnel));
        report.items.addAll(MapLinks.list(snapshot, walking, transports, scope, report.funnel));
        report.items.addAll(DungeonEntrances.list(snapshot, walking, coverage, scope, areas, report.funnel));
        return report;
    }
}
