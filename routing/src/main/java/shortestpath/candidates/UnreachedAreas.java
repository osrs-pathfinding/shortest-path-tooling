package shortestpath.candidates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

/**
 * Candidate set 1, unreached areas: a walking area of at least {@link #MIN_AREA_TILES} tiles that no
 * chain of transports reaches from Lumbridge (ignoring requirements), listed with the connections that
 * would join it to reached ground and have no transport row. One item per area.
 *
 * <p>A connection is either a cache connector whose far end a pairing rule found (a ladder and the
 * ladder one plane up, say), or a world map link (a dungeon's entrance icon and the dungeon). It
 * counts when exactly one of its two ends is in the unreached area and the other is on reached
 * ground: inside the area it is the area's <em>way out</em>, outside it the <em>way in</em>.
 *
 * <p>Set aside and counted instead: connectors a transport covers or that are out of scope; ends
 * with no walkable tile beside them; connections whose two ends are both reached (a shortcut, not a
 * gap) or both unreached; areas under {@link #MIN_AREA_TILES} tiles; and areas whose only connectors
 * have no known far end.
 *
 * <p>An area the world map names (a {@link ConnectorSnapshot.Label} within {@link #LABEL_REACH} tiles of
 * a tile in it) is titled by that name, the one nearest its focus.
 *
 * <p>Areas are sorted by the banks, altars and other locations they contain, then named areas before
 * unnamed ones, then by everything they contain (transport origins too), then by size.
 */
final class UnreachedAreas {
    /** Unreached areas smaller than this are counted, not listed. */
    static final int MIN_AREA_TILES = 50;
    /** World map link icons stand off their entrance; their ends are the walkable tiles this close. */
    static final int LINK_REACH = 5;
    /** World map labels are centred on a place, not on a walkable tile; a label names the area this close. */
    static final int LABEL_REACH = 5;
    static final String ORIGINS = "transport origins";

    private final Walking walking;
    private final Scope scope;
    private final int[] sizes;
    private final Map<Integer, List<Report.Candidate>> byArea = new TreeMap<>();
    private int placed, reached, bothUnreached, small, farEndOutOfScope;

    private UnreachedAreas(Walking walking, Scope scope) {
        this.walking = walking;
        this.scope = scope;
        sizes = walking.areaSizes();
    }

    static List<Report.Item> list(ConnectorSnapshot snapshot, Walking walking, Coverage coverage, Scope scope,
                                  Map<Integer, Set<Transport>> transports, Map<String, Set<Integer>> destinations,
                                  Map<String, Integer> funnel) {
        UnreachedAreas areas = new UnreachedAreas(walking, scope);

        funnel.put("snapshot connectors", snapshot.connectors.size());
        int covered = 0, outOfScope = 0, noStand = 0;
        for (Connector c : snapshot.connectors) {
            if (coverage.covers(c)) { covered++; continue; }
            if (scope.excludes(c.x, c.y, c.plane)) { outOfScope++; continue; }
            int stand = walking.stand(c.x, c.y, c.plane, c.sizeX, c.sizeY, 1);
            if (stand == Walking.NONE) { noStand++; continue; }
            areas.place(c, stand, partnerStand(walking, c));
        }
        funnel.put("covered by a transport row", covered);
        funnel.put("out of scope", outOfScope);
        funnel.put("no walkable tile beside it", noStand);

        int linkOffGround = 0;
        int connectorsPlaced = areas.placed;
        for (ConnectorSnapshot.MapLink link : snapshot.mapLinks) {
            int from = walking.stand(link.from.x, link.from.y, link.from.plane, 1, 1, LINK_REACH);
            int to = walking.stand(link.to.x, link.to.y, link.to.plane, 1, 1, LINK_REACH);
            if (from == Walking.NONE || to == Walking.NONE) { linkOffGround++; continue; }
            areas.place(linkConnector(link), from, to);
        }
        funnel.put("map links", snapshot.mapLinks.size());
        funnel.put("map link ends with no walkable tile within " + LINK_REACH, linkOffGround);
        funnel.put("map links that pass the gate", areas.placed - connectorsPlaced);

        funnel.put("both ends already reached", areas.reached);
        funnel.put("both ends unreached", areas.bothUnreached);
        funnel.put("unreached area under " + MIN_AREA_TILES + " tiles", areas.small);
        funnel.put("far end out of scope", areas.farEndOutOfScope);
        int unreached = 0;
        for (int area = 1; area < areas.sizes.length; area++) {
            if (!walking.isStructurallyReachable(area) && areas.sizes[area] >= MIN_AREA_TILES) unreached++;
        }
        funnel.put("unreached areas of " + MIN_AREA_TILES + "+ tiles", unreached);
        funnel.put("unreached areas with a connector", areas.byArea.size());
        funnel.put("areas whose connectors all lead somewhere unknown", areas.dropOpenEndOnly());

        List<Report.Item> items = areas.items(destinations, transports, snapshot.labels);
        funnel.put("listed areas", items.size());
        return items;
    }

    /** Decides a connection by where its two ends are. {@code partner} is null when the far end is unknown. */
    private void place(Connector c, int stand, Integer partner) {
        int area = walking.area(stand);
        boolean areaReached = walking.isStructurallyReachable(area);
        int partnerArea = partner == null ? -1 : walking.area(partner);
        if (!areaReached && partner != null && walking.isStructurallyReachable(partnerArea)) {
            if (sizes[area] < MIN_AREA_TILES) { small++; return; }
            add(area, new Report.Candidate("way-out", c, Report.Point.of(stand), Report.Point.of(partner)));
        } else if (areaReached && partner != null && !walking.isStructurallyReachable(partnerArea)) {
            if (sizes[partnerArea] < MIN_AREA_TILES) { small++; return; }
            if (scope.excludes(partner)) { farEndOutOfScope++; return; }
            add(partnerArea, new Report.Candidate("way-in", c, Report.Point.of(stand), Report.Point.of(partner)));
        } else if (!areaReached && partner == null) {
            if (sizes[area] < MIN_AREA_TILES) { small++; return; }
            add(area, new Report.Candidate("open-end", c, Report.Point.of(stand), null));
        } else if (areaReached) {
            reached++;
        } else {
            bothUnreached++;
        }
    }

    private void add(int area, Report.Candidate candidate) {
        if (!candidate.role.equals("open-end")) placed++;
        byArea.computeIfAbsent(area, k -> new ArrayList<>()).add(candidate);
    }

    /** Areas whose only connectors have an unknown far end are counted, not listed, until the list is worked down. */
    private int dropOpenEndOnly() {
        int dropped = 0;
        for (var it = byArea.values().iterator(); it.hasNext();) {
            List<Report.Candidate> candidates = it.next();
            candidates.removeIf(c -> c.role.equals("open-end"));
            if (candidates.isEmpty()) { dropped++; it.remove(); }
        }
        return dropped;
    }

    private List<Report.Item> items(Map<String, Set<Integer>> destinations, Map<Integer, Set<Transport>> transports,
                                    List<ConnectorSnapshot.Label> labels) {
        Map<Integer, int[]> bounds = bounds();
        Map<Integer, Map<String, Integer>> contents = contents(destinations, transports);
        List<Report.Item> items = new ArrayList<>();
        for (Map.Entry<Integer, List<Report.Candidate>> entry : byArea.entrySet()) {
            List<Report.Candidate> candidates = entry.getValue();
            candidates.sort(Comparator.comparing((Report.Candidate c) -> c.role).thenComparingInt(c -> c.connector.objectId)
                .thenComparingInt(c -> c.connector.x).thenComparingInt(c -> c.connector.y).thenComparingInt(c -> c.connector.plane));
            Report.Item item = new Report.Item();
            item.kind = "area";
            item.paired = true;
            item.areaTiles = sizes[entry.getKey()];
            item.bounds = bounds.get(entry.getKey());
            item.contains = contents.get(entry.getKey());
            item.connectors = candidates;
            Report.Candidate first = candidates.get(0);
            item.focus = first.role.equals("way-in") ? first.partnerStand : first.stand;
            item.label = label(entry.getKey(), item.focus, labels);
            String near = "nreached area near " + item.focus.x + " " + item.focus.y + " " + item.focus.plane;
            item.title = item.label == null ? "U" + near : item.label + " (u" + near + ")";
            item.id = id(candidates);
            items.add(item);
        }
        items.sort(Comparator.comparing((Report.Item i) -> -locations(i.contains))
            .thenComparing(i -> i.label == null)
            .thenComparing(i -> -i.contains.values().stream().mapToInt(Integer::intValue).sum())
            .thenComparing(i -> -i.areaTiles));
        return items;
    }

    /** The world map's name for a place in {@code area}, the label nearest {@code focus}, or null. */
    private String label(int area, Report.Point focus, List<ConnectorSnapshot.Label> labels) {
        ConnectorSnapshot.Label best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (ConnectorSnapshot.Label label : labels) {
            int stand = walking.stand(label.x, label.y, label.plane, 1, 1, LABEL_REACH);
            if (stand == Walking.NONE || walking.area(stand) != area) continue;
            int distance = Math.max(Math.abs(label.x - focus.x), Math.abs(label.y - focus.y));
            if (distance < bestDistance || distance == bestDistance && label.name.compareTo(best.name) < 0) {
                best = label;
                bestDistance = distance;
            }
        }
        return best == null ? null : best.name;
    }

    /** A world map link as a connector: no object (id -1), its icon's tile, and the other icon as its partner. */
    static Connector linkConnector(ConnectorSnapshot.MapLink link) {
        Connector c = new Connector();
        c.objectId = -1;
        c.ids = new int[0];
        c.name = "World map link";
        c.options = List.of("Map link");
        c.x = link.from.x; c.y = link.from.y; c.plane = link.from.plane;
        c.sizeX = 1; c.sizeY = 1;
        Connector.Partner p = new Connector.Partner();
        p.objectId = -1;
        p.name = "World map link";
        p.option = "Map link";
        p.x = link.to.x; p.y = link.to.y; p.plane = link.to.plane;
        p.sizeX = 1; p.sizeY = 1;
        p.rule = "map-link";
        c.partner = p;
        return c;
    }

    /** Where the partner is used from, or null without a partner or a walkable tile beside it. */
    static Integer partnerStand(Walking walking, Connector c) {
        Connector.Partner p = c.partner;
        if (p == null) return null;
        int tile = walking.stand(p.x, p.y, p.plane, p.sizeX, p.sizeY, p.rule.equals("map-link") ? LINK_REACH : 1);
        return tile == Walking.NONE ? null : tile;
    }

    /** The item id: a hash of its sorted connector placements, so it survives cache builds. */
    private static String id(List<Report.Candidate> candidates) {
        StringBuilder text = new StringBuilder();
        candidates.stream().map(c -> c.connector.objectId + " " + c.connector.x + " " + c.connector.y + " " + c.connector.plane)
            .sorted().forEach(line -> text.append(line).append('\n'));
        return Report.id("area", text.toString());
    }

    /** Banks, altars and other locations, not counting transport origins. */
    private static int locations(Map<String, Integer> contains) {
        return contains.entrySet().stream().filter(e -> !e.getKey().equals(ORIGINS)).mapToInt(Map.Entry::getValue).sum();
    }

    /** {minX, minY, maxX, maxY, plane} of each listed area. */
    private Map<Integer, int[]> bounds() {
        Map<Integer, int[]> bounds = new HashMap<>();
        for (int i = 0; i < walking.tileCount(); i++) {
            int area = walking.naturalComponent(i);
            if (!byArea.containsKey(area)) continue;
            int tile = walking.tile(i);
            int x = WorldPointUtil.unpackWorldX(tile), y = WorldPointUtil.unpackWorldY(tile);
            int[] b = bounds.computeIfAbsent(area, k -> new int[] {x, y, x, y, WorldPointUtil.unpackWorldPlane(tile)});
            b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.max(b[2], x); b[3] = Math.max(b[3], y);
        }
        return bounds;
    }

    /** Locations by kind (bank, altar, …) and transport origins inside each listed area. */
    private Map<Integer, Map<String, Integer>> contents(Map<String, Set<Integer>> destinations, Map<Integer, Set<Transport>> transports) {
        Map<Integer, Map<String, Integer>> contents = new HashMap<>();
        for (int area : byArea.keySet()) contents.put(area, new TreeMap<>());
        for (Map.Entry<String, Set<Integer>> entry : new TreeMap<>(destinations).entrySet()) {
            String kind = entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1).replace(".tsv", "");
            for (int tile : entry.getValue()) count(contents, tile, kind);
        }
        for (int origin : transports.keySet()) {
            if (origin != Transport.UNDEFINED_ORIGIN) count(contents, origin, ORIGINS);
        }
        return contents;
    }

    private void count(Map<Integer, Map<String, Integer>> contents, int tile, String kind) {
        int index = walking.indexOf(tile);
        if (index < 0) return;
        Map<String, Integer> counts = contents.get(walking.naturalComponent(index));
        if (counts != null) counts.merge(kind, 1, Integer::sum);
    }
}
