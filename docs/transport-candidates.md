# Transport candidates

Places where the game cache shows a connection the plugin has no transport for, in two
lists:

- **Unreached areas:** a ladder, staircase, trapdoor, cave entrance or world map link that
  would join an area nothing reaches.
- **Agility shortcuts:** the game's shortcut map icon with no transport nearby.

Both lists come from a program, not from hand-curation. The criteria are fixed in code,
and the same cache, plugin data and scope file always produce the same list. They are
re-judged against whatever plugin data is loaded, so the lists follow the data as rows are
added.

The lists are meant to be short and trustworthy. Criteria are widened only once a list has
been worked down and a precision sample of the new criterion passes (see the end of this
doc). The workbench shows them at `/candidates`.

## Pieces

Extraction (`src/test/java/shortestpath/dump/candidates/`) needs a downloaded cache. It runs
as `./gradlew transportCandidates`:

| Class | What it does |
|---|---|
| `TransportCandidatesDumperTest` | Runs the pieces below, writes the snapshot, and writes a report |
| `CacheConnectors` | One pass over every cache placement: connectors, map icons, shortcut obstacles |
| `ConnectorPairing` | Finds each connector's far end, and measures every pairing rule against the plugin's rows |
| `WorldMapLinks` | Reads the world map's dungeon links |
| `ShortcutIcons` | Finds the agility shortcut icon and matches each icon to RuneLite's shortcut list |

Judgement (`routing/src/main/java/shortestpath/candidates/`) needs no cache. The workbench
calls it live:

| Class | What it does |
|---|---|
| `TransportCandidates` | Entry point: judges a snapshot against loaded plugin data |
| `UnreachedAreas` | Candidate set 1: unreached areas and the connectors and links into them |
| `AgilityShortcuts` | Candidate set 2: shortcut icons with no transport nearby |
| `Coverage` | Whether a transport row already covers a connector |
| `Scope` | Map boxes left out (`candidate_scope.tsv`), each with a reason |
| `Walking` | The plugin's walking areas and their reachability from Lumbridge |
| `ConnectorSnapshot`, `Connector` | The snapshot format |
| `Report` | The judged items and the funnel |

Data (`routing/src/main/resources/candidates/`):

| File | Contents |
|---|---|
| `connectors.json` | The snapshot of cache facts: connectors, map links and shortcut icons, one per line |
| `candidate_scope.tsv` | The scope boxes |

Regenerate after a cache or plugin update:

```sh
python3 scripts/maintenance.py cache
./gradlew transportCandidates -PtransportCandidatesCacheDir=$PWD/cache \
  -PtransportCandidatesXteaPath=$PWD/keys.json \
  "-PtransportCandidatesCacheLabel=openrs2 <id>, build <n>"
```

The report at `build/transport-candidates.txt` shows the following, so each run can be
checked before its snapshot is committed:

- the option lexicon
- the agility-shortcut marker check
- the extraction counts
- the precision of each pairing rule
- the live funnel
- every listed item

## Extraction

The dumper takes every cache placement of an object whose menu offers a **connector
option**. Those are the options the plugin's `transports.tsv` and `agility_shortcuts.tsv`
rows use at least 3 times, minus a deny-list: opening, searching, skilling, boarding and
so on.

Doors and gates are left out: wall objects with `wallOrDoor` set. Multi-loc objects
contribute their children's options and ids. Planes are lowered under bridge tiles, as in
the collision dumper.

A placement is **covered** when either:
- any transport origin lies within 2 tiles of its footprint on the same plane, or
- a row anchored on one of its ids lies within 3 tiles.

The snapshot keeps the uncovered placements.

## Pairing

A connector's far end is another connector with the complementary option: up with down,
enter with exit, and a two-way `Climb` with either.

| Rule | Definition | Kept |
|---|---|---|
| `vertical` | footprint within 2 tiles, one plane up or down | yes |
| `id+offset` | object id within ±4, placed at most 5 times, and about 6,400 tiles north or south (±8) | yes |
| `id` | the id rule alone | no |
| `offset` | the offset rule alone | no |

Precision on cache 2757 (build 242), measured on covered connectors: is the partner where
the plugin's rows from that connector land?

| Rule | Right | Wrong |
|---|---|---|
| vertical | 1,033 | 47 |
| id+offset | 54 | 11 |
| id | 79 | 65 |
| offset | 36 | 28 |

So only `vertical` and `id+offset` partners are kept in the snapshot.

## The gate

A connector (or map link) is listed only when its stand tile and its partner's stand tile
are walkable,
**and** one of them is in an unreached area while the other is on reached ground:

- **Unreached area:** a natural walking component of at least 50 tiles that
  `RoutingStaticBuilder.walkingGraph` doesn't reach from Lumbridge, ignoring requirements.
- **Stand tile:** a walkable tile on or beside the footprint.

Roles:
- A connector inside the area is its **way out**.
- A connector on reached ground whose partner is inside the area is a **way in**.

Connectors are grouped into one item per unreached area. The item id hashes the sorted
placements, so a review decision survives cache updates.

Items are sorted by the locations they contain (banks, altars, anvils, apothecaries),
then by everything they contain, including transport origins, then by size.

Everything else is counted in the funnel, not listed. That includes areas whose
connectors have no known far end.

### Numbers (cache 2757, plugin `a4aa30ab`)

| Funnel step | Count |
|---|---|
| placements scanned | 5,021,872 |
| connector placements | 8,724 |
| covered | 2,999 |
| uncovered snapshot | 5,725 (2,268 with a kept partner) |
| unreached areas of 50+ tiles | 2,753 |
| areas with any connector or link | 687 |
| areas whose connectors all lead somewhere unknown | 560 |
| **listed areas** | **127** |

Each list has its own budget: at most 150 areas and 50 shortcuts. If a list grows past its
budget, tighten its rule rather than paging through it.

## World map links

The world map links each dungeon's entrance icon to the dungeon's own map
(`WorldMapManager.getIntermapLinks`). There are 406 links, counting both directions. The
library's loader trips an internal assertion on current caches, so the dumper turns
assertions off for that class; the links it reads are unaffected.

A link's ends are icon positions, not exact tiles. The gate uses the walkable tile nearest
each icon, within 5 tiles. A link is a pair with rule `map-link`, so it can be the way in
or out of an unreached area like any connector.

As a sanity check, 186 of the 406 links join two walking areas that a transport row
already joins directly. On cache 2757, 90 links pass the gate.

## Agility shortcuts

The game draws the agility shortcut icon with invisible map-function objects placed beside
the shortcut, not on the obstacle itself. The dumper takes the map icon most often placed
within 3 tiles of an `agility_shortcuts.tsv` origin: `mapAreaId=71` (object 17030). Of its
240 placements, 156 are within 5 tiles of a shortcut row.

Each icon is matched to the nearest entry within 10 tiles of RuneLite's
`net.runelite.client.game.AgilityShortcut` enum (BSD-2; used at build time only). That adds
a description, a level and obstacle ids: 182 icons match. The icon's obstacles are the
placements of those ids within 10 tiles, or else the connectors within 5 tiles.

The live list keeps one icon per shortcut. Icons on other planes, within 3 tiles of each
other, or sharing an obstacle are merged. It drops icons with any transport origin within
5 tiles, and icons with no walkable tile within 5 on reached ground. Shortcuts RuneLite
knows come first.

On cache 2757:

| Step | Count |
|---|---|
| icons | 240 |
| repeating another icon | 21 |
| covered by a transport | 170 |
| in unreached areas | 24 |
| **listed** | **25** |

## Ways to widen the criteria later

Add each step only when the list is worked down and a 20-item precision sample of the step
passes (at least 80% real gaps):

1. Smaller unreached areas (below 50 tiles).
2. Areas whose connectors lead somewhere unknown (the 532), ranked by contents.
3. Local joins: areas reached only through a teleport or a long way round.
4. Family signal: the same object id is a transport elsewhere but not here.
5. Weaker pairing rules (`id` or `offset` alone) once they're made more precise.
6. Shortcuts in unreached areas, and RuneLite entries with no icon nearby (70).
