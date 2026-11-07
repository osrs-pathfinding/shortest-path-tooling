package shortestpath.candidates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class TransportCandidatesTest {
    /** Component 1 (reached): 10x10 on plane 0 at 100,100. Component 2: 10x10 on plane 1. Component 3: 4 tiles on plane 2. */
    static final class Grid implements Walking {
        final int[] tiles;
        final Map<Integer, Integer> component = new HashMap<>();

        Grid() {
            List<Integer> all = new ArrayList<>();
            for (int x = 100; x < 110; x++) {
                for (int y = 100; y < 110; y++) {
                    add(all, x, y, 0, 1);
                    add(all, x, y, 1, 2);
                }
            }
            for (int x = 100; x < 102; x++) for (int y = 100; y < 102; y++) add(all, x, y, 2, 3);
            tiles = all.stream().mapToInt(Integer::intValue).sorted().toArray();
        }

        private void add(List<Integer> all, int x, int y, int plane, int id) {
            int tile = WorldPointUtil.packWorldPoint(x, y, plane);
            all.add(tile);
            component.put(tile, id);
        }

        @Override public int tileCount() { return tiles.length; }
        @Override public int tile(int index) { return tiles[index]; }
        @Override public int indexOf(int tile) { int i = Arrays.binarySearch(tiles, tile); return i < 0 ? -1 : i; }
        @Override public int naturalComponentCount() { return 3; }
        @Override public int naturalComponent(int index) { return component.get(tiles[index]); }
        @Override public boolean isStructurallyReachable(int c) { return c == 1; }
    }

    static Connector ladder(int id, String option, int x, int y, int plane, Connector partner) {
        Connector c = new Connector();
        c.objectId = id;
        c.ids = new int[] {id};
        c.name = "Ladder";
        c.options = List.of(option);
        c.x = x; c.y = y; c.plane = plane;
        c.sizeX = 1; c.sizeY = 1;
        if (partner != null) {
            Connector.Partner p = new Connector.Partner();
            p.objectId = partner.objectId; p.name = partner.name; p.option = partner.option();
            p.x = partner.x; p.y = partner.y; p.plane = partner.plane; p.sizeX = 1; p.sizeY = 1;
            p.rule = "vertical";
            c.partner = p;
        }
        return c;
    }

    static ConnectorSnapshot snapshot(Connector... connectors) {
        ConnectorSnapshot snapshot = new ConnectorSnapshot();
        snapshot.cache = "test";
        snapshot.connectors.addAll(List.of(connectors));
        return snapshot;
    }

    static Map<Integer, Set<Transport>> transports(int origin, String objectInfo) {
        Transport t = new Transport.TransportBuilder().origin(origin).destination(WorldPointUtil.packWorldPoint(1, 1, 0))
            .type(TransportType.TRANSPORT).objectInfo(objectInfo).build();
        return Map.of(origin, Set.of(t));
    }

    static final Map<String, Set<Integer>> BANK_UPSTAIRS = Map.of("game_features/bank.tsv",
        Set.of(WorldPointUtil.packWorldPoint(108, 108, 1)));

    @Test
    public void listsAnUnreachedAreaByItsWaysInAndOut() {
        Connector up = ladder(10, "Climb-up", 105, 105, 0, null);
        Connector down = ladder(11, "Climb-down", 105, 105, 1, up);
        up = ladder(10, "Climb-up", 105, 105, 0, down);
        Report report = TransportCandidates.judge(snapshot(up, down), new Grid(), Map.of(), BANK_UPSTAIRS, Scope.none());
        assertEquals(1, report.items.size());
        Report.Item item = report.items.get(0);
        assertEquals("area", item.kind);
        assertEquals(Integer.valueOf(100), item.areaTiles);
        assertEquals(Integer.valueOf(1), item.contains.get("bank"));
        assertEquals(List.of("way-in", "way-out"), item.connectors.stream().map(c -> c.role).collect(java.util.stream.Collectors.toList()));
        assertEquals(1, item.focus.plane);
        assertTrue(item.id.startsWith("area-"));

        // The id is the set of placements, whatever order the snapshot lists them in.
        Report reversed = TransportCandidates.judge(snapshot(down, up), new Grid(), Map.of(), BANK_UPSTAIRS, Scope.none());
        assertEquals(item.id, reversed.items.get(0).id);
    }

    @Test
    public void aCoveringRowOrScopeBoxOrSmallAreaRemovesTheCandidate() {
        Connector down = ladder(11, "Climb-down", 105, 105, 1, null);
        Connector up = ladder(10, "Climb-up", 105, 105, 0, down);
        int beside = WorldPointUtil.packWorldPoint(106, 105, 0);
        assertEquals(0, TransportCandidates.judge(snapshot(up), new Grid(), transports(beside, "Climb-up Ladder 99"),
            Map.of(), Scope.none()).items.size());
        // The same id further away still covers it; another id that far does not.
        int near = WorldPointUtil.packWorldPoint(108, 105, 0);
        assertEquals(0, TransportCandidates.judge(snapshot(up), new Grid(), transports(near, "Climb-up Ladder 10"),
            Map.of(), Scope.none()).items.size());
        assertEquals(1, TransportCandidates.judge(snapshot(up), new Grid(), transports(near, "Climb-up Ladder 99"),
            Map.of(), Scope.none()).items.size());

        Scope scope = Scope.read(new ByteArrayInputStream(
            "# comment\n100 100 110 110 *\tTest instance\n".getBytes(StandardCharsets.UTF_8)));
        Report report = TransportCandidates.judge(snapshot(up), new Grid(), Map.of(), Map.of(), scope);
        assertEquals(0, report.items.size());
        assertEquals(Integer.valueOf(1), report.funnel.get("out of scope"));

        Connector attic = ladder(12, "Climb-down", 100, 100, 2, null);
        Connector toAttic = ladder(13, "Climb-up", 101, 100, 1, attic);
        Report small = TransportCandidates.judge(snapshot(toAttic), new Grid(), Map.of(), Map.of(), Scope.none());
        assertEquals(0, small.items.size());
    }

    @Test
    public void connectorsWithNoKnownFarEndAreCountedNotListed() {
        Connector exit = ladder(20, "Exit", 103, 103, 1, null);
        Report report = TransportCandidates.judge(snapshot(exit), new Grid(), Map.of(), Map.of(), Scope.none());
        assertEquals(0, report.items.size());
        assertEquals(Integer.valueOf(1), report.funnel.get("areas whose connectors all lead somewhere unknown"));
    }

    static ConnectorSnapshot.MapLink link(int fx, int fy, int fp, int tx, int ty, int tp) {
        ConnectorSnapshot.MapLink link = new ConnectorSnapshot.MapLink();
        link.from = new ConnectorSnapshot.Tile(fx, fy, fp);
        link.to = new ConnectorSnapshot.Tile(tx, ty, tp);
        return link;
    }

    @Test
    public void aWorldMapLinkIsAWayIntoTheAreaItsOtherIconIsIn() {
        ConnectorSnapshot snapshot = snapshot();
        // The surface icon stands off the walkable ground; its end is the nearest walkable tile.
        snapshot.mapLinks.add(link(98, 105, 0, 104, 104, 1));
        snapshot.mapLinks.add(link(500, 500, 0, 104, 104, 1));
        Report report = TransportCandidates.judge(snapshot, new Grid(), Map.of(), Map.of(), Scope.none());
        assertEquals(1, report.items.size());
        Report.Candidate in = report.items.get(0).connectors.get(0);
        assertEquals("way-in", in.role);
        assertEquals("map-link", in.connector.partner.rule);
        assertEquals(100, in.stand.x);
        assertEquals(Integer.valueOf(1), report.funnel.get("map link ends with no walkable tile within 5"));
    }

    static ConnectorSnapshot.ShortcutIcon icon(int x, int y, int plane, Connector... obstacles) {
        ConnectorSnapshot.ShortcutIcon icon = new ConnectorSnapshot.ShortcutIcon();
        icon.x = x; icon.y = y; icon.plane = plane;
        icon.obstacles.addAll(List.of(obstacles));
        return icon;
    }

    @Test
    public void listsEachUncoveredShortcutOnceBesideReachedGround() {
        Connector rocks = ladder(30, "Climb", 104, 104, 0, null);
        ConnectorSnapshot snapshot = snapshot();
        snapshot.shortcutIcons.add(icon(105, 105, 0, rocks));
        snapshot.shortcutIcons.add(icon(105, 105, 1, rocks)); // the same icon on another plane
        snapshot.shortcutIcons.add(icon(109, 100, 0, rocks)); // another icon for the same obstacle
        snapshot.shortcutIcons.add(icon(100, 100, 2));        // beside the small unreached attic only
        Report report = TransportCandidates.judge(snapshot, new Grid(), Map.of(), Map.of(), Scope.none());
        assertEquals(1, report.items.size());
        Report.Item item = report.items.get(0);
        assertEquals("shortcut", item.kind);
        assertEquals(30, item.connectors.get(0).connector.objectId);
        assertEquals(Integer.valueOf(2), report.funnel.get("shortcut icons repeating another"));
        assertEquals(report.funnel.toString(), Integer.valueOf(1), report.funnel.get("shortcuts in unreached areas"));

        int origin = WorldPointUtil.packWorldPoint(108, 108, 0);
        assertEquals(0, TransportCandidates.judge(snapshot, new Grid(), transports(origin, "Climb Rocks 99"), Map.of(), Scope.none())
            .items.size());
    }

    @Test
    public void snapshotRoundTripsOneConnectorPerLine() {
        Connector down = ladder(11, "Climb-down", 105, 105, 1, null);
        ConnectorSnapshot snapshot = snapshot(ladder(10, "Climb-up", 105, 105, 0, down), down);
        snapshot.shortcutMarker = new ConnectorSnapshot.ShortcutMarker();
        String json = snapshot.toJson();
        assertEquals(2, json.lines().filter(line -> line.startsWith("    {\"objectId\"")).count());
        ConnectorSnapshot read = ConnectorSnapshot.read(new StringReader(json));
        assertEquals(2, read.connectors.size());
        assertEquals("vertical", read.connectors.get(0).partner.rule);
        assertNull(read.connectors.get(1).partner);
    }

    @Test
    public void bundledSnapshotAndScopeLoad() {
        assertTrue(ConnectorSnapshot.bundled().connectors.size() > 0);
        Scope.bundled();
    }
}
