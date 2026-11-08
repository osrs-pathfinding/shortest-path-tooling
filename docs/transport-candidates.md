# Transport candidates

Places where the game cache shows a connection the plugin has no transport for, in three
lists:

- **Unreached areas:** a ladder, staircase, trapdoor, cave entrance or world map link that
  would join an area nothing reaches.
- **Agility shortcuts:** the game's shortcut map icon with no transport nearby.
- **Dungeon entrances:** the game's dungeon entrance map icon with no transport nearby.

All three lists come from a program, not from hand-curation. The criteria are fixed in code,
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
| `WorldMapLabels` | Reads the world map's place names |
| `ShortcutIcons` | Finds the agility shortcut icon and matches each icon to RuneLite's shortcut list |
| `DungeonIcons` | Finds the dungeon entrance icon, with the connectors beside each icon |
| `MapIcons` | Finds which map icon marks a kind of place, from the rows it is placed beside |

Judgement (`routing/src/main/java/shortestpath/candidates/`) needs no cache. The workbench
calls it live:

| Class | What it does |
|---|---|
| `TransportCandidates` | Entry point: judges a snapshot against loaded plugin data |
| `UnreachedAreas` | Candidate set 1: unreached areas and the connectors and links into them |
| `AgilityShortcuts` | Candidate set 2: shortcut icons with no transport nearby |
| `DungeonEntrances` | Candidate set 3: dungeon entrance icons with no transport nearby |
| `Coverage` | Whether a transport row already covers a connector |
| `Scope` | Map boxes left out (`candidate_scope.tsv`), each with a reason |
| `Walking` | The plugin's walking areas and their reachability from Lumbridge |
| `ConnectorSnapshot`, `Connector` | The snapshot format |
| `Report` | The judged items and the funnel |

Data (`routing/src/main/resources/candidates/`):

| File | Contents |
|---|---|
| `connectors.json` | The snapshot of cache facts: connectors, map links, shortcut and dungeon icons, and place names, one per line |
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

An area the world map names is titled by that name, e.g. "Ancient Forge (unreached area
near 1744 5321 1)". The name is a world map label (a map element whose map area has a name;
1,058 on cache 2757) whose nearest walkable tile within 5 is in the area. When several are,
the one nearest the item's focus wins. On cache 2757, 35 of the 127 listed areas are named.

Items are sorted by the locations they contain (banks, altars, anvils, apothecaries),
then named areas before unnamed ones, then by everything they contain, including
transport origins, then by size.

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

Each list has its own budget: at most 150 areas, and 50 each of shortcuts and dungeon
entrances. If a list
grows past its budget, tighten its rule rather than paging through it.

## World map links

Clicking some icons on the world map jumps its view to another map: a dungeon's entrance
icon opens the dungeon, and an icon in the dungeon jumps back to the surface. The game keeps
these in clientscript 1705, one switch case per link that pushes the far end and calls
script 1706, and `WorldMapManager.getIntermapLinks` reads them. There are 406 links, counting
both directions. The library's loader asserts that the script has a default case, which
current caches don't have, so the dumper turns assertions off for that class. Decoding the
script directly gives the same 406 links.

A link's ends are icon positions, not exact tiles. The gate uses the walkable tile nearest
each icon, within 5 tiles. A link is a pair with rule `map-link`, so it can be the way in
or out of an unreached area like any connector. On cache 2757, 90 links pass that gate.

Links between two reached areas aren't listed. A link is how the world map is navigated, not
a playable edge:

- It can sum up a journey through several transports. Ectofuntus's basement icon links to
  the surface through four, and Forthos's catacombs link to the surface through a passage and
  a staircase.
- It can point at a nearby place rather than the entrance. The Chasm of Fire links to the
  Shayzien crypt's icon, 122 tiles from the rope that actually leads out.
- Many are one-way.

An earlier set listed links that no single transport near the icon modelled. Of its 9
items on cache 2757, 7 joined areas that a chain of up to 4 transports already connects. The
other 2, 1957 4952 → 3081 3420 and 2856 10087 → 2796 3718, had no such chain and are
unreviewed. Don't add a transport from a link's two ends.

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

## Dungeon entrances

The dungeon entrance icon is found the same way as the shortcut icon. It's the map icon,
other than the shortcut's, most often placed within 3 tiles of the origin of a
`transports.tsv` row that goes down or in (`Climb-down`, `Walk-down`, `Enter`, `Go-down`).
That's `mapAreaId=13`.

The game draws dungeon entrances with two icons: `mapAreaId=12` (sprite 1460) and
`mapAreaId=13` (sprite 1534). Both have map area category 1062. So the vote's winner is
widened to every map area in its category. The shortcut icon (71) has no other area in its
category, so its set is unchanged.

Of the 321 placements, 136 are within 5 tiles of such a row. Each icon keeps the connectors
within 5 tiles, as the objects it most likely marks.

The live list keeps one icon per entrance, merging icons on other planes or within 3 tiles.
It drops:
- icons with a transport origin within 5 tiles, or a covered connector beside them;
- icons within 10 tiles of a world map link's icon: the map already joins that dungeon, and
  a link into an unreached area is judged there;
- icons whose connectors are already an unreached area's way in;
- icons with no walkable tile within 5 on reached ground.

Most entrances are modelled or are also map links. On cache 2757:

| Step | Count |
|---|---|
| icons | 321 |
| a transport within 5 tiles | 187 |
| beside a world map link | 65 |
| already an unreached area's way in | 2 |
| no walkable tile within 5 | 1 |
| in unreached areas | 29 |
| **listed** | **37** |

Before `mapAreaId=12` was included, only one was listed: a `Climb-down` ladder (58029) at
3046 2980. Its map link leads into an unreached area under 50 tiles, so no other list has it.
Most of the 36 the second icon adds have no map link. One example is the `Climb-down`
staircase (41922) at 1464 3568, whose far end (1444 9960) is 19 tiles off in x, so no
pairing rule finds it either.

RuneLite's `DungeonLocation` names would make better titles, but that class is
package-private, so items are titled by tile.

## Ways to widen the criteria later

Add each step only when the list is worked down and a 20-item precision sample of the step
passes (at least 80% real gaps):

1. Smaller unreached areas (below 50 tiles).
2. Areas whose connectors lead somewhere unknown (the 560), ranked by contents.
3. Local joins: areas reached only through a teleport or a long way round.
4. Family signal: the same object id is a transport elsewhere but not here.
5. Weaker pairing rules (`id` or `offset` alone) once they're made more precise.
6. Shortcuts in unreached areas, and RuneLite entries with no icon nearby (70).
