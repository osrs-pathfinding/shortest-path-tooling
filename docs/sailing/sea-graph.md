# Sailing data: the sea graph

How the `sailingData` task builds the data the plugin's sailing routes need, and why it is built that way. The plugin's `docs/astar-pathfinder.md` describes the model the routes implement; this page is for changing or regenerating the data. [Sailing mechanics](mechanics.md) describes how boats move in the game.

```
./gradlew sailingData -PshortestPathDir=<plugin checkout>
```

It takes about a minute, almost all of it sailing the runs (below), and writes into the plugin's `collision-map.zip`. Run it after the collision map dumper and before `routingCuts`, whose fingerprint covers the whole zip. The plugin's `SailingDataTest` fails while the committed data is stale.

## The layers

| Entry | Written by | Read by | What |
|---|---|---|---|
| `sea/<region>`, `sea/legend` | the collision map dumper | this task only | A byte per plane-0 tile: 0 off the sea, else its area. The collision flags can't tell water from land, so the sea is read from the floor's water textures: each restricted area (Tempor Tantrum, the icy seas, crystal-flecked, fetid and kelp water) has its own family of textures, and coastal shallows and the dark water past the world edge count as open sea. The plugin doesn't keep this layer at runtime. |
| `sailing/tiles` | `SailingLayerBuilder` | the plugin, per tile | A number per sea tile packing its area and, for each boat kind, its body of water. |
| `sailing/index` | `SailingLayerBuilder` | the plugin | The fingerprint of the inputs, then each kind's bodies of water, their areas and the bodies they link to. |
| `sailing/graph-<KIND>` | `SeaGraphBuilder` | the plugin | Each boat kind's sea graph. |

The fingerprint (`SailingDataFingerprint`, compiled from the plugin's test sources) covers the collision map except its sailing layer, the moorings, and the sea graph's version and settings, so a new map, a changed mooring or a new setting marks the data stale.

## Bodies of water

A kind's bodies of water come from every move its hull clears from a tile's centre, at any speed up to 8 tiles a tick: each such move joins its two ends into one body within one area, and a move from one area into another links the two bodies. Only bodies of at least 100 tiles that a boat can reach from some berth are kept, about eight per kind. Coastal pockets, and water such as the Elid Delta or the Lum lagoon for boats bigger than a raft, belong to no body, so no route ever sails there. The plugin follows the links to rule out a crossing without searching.

## The sea graph

A route over open water runs straight until it has to bend round a headland, and then it bends at a corner of the coast. So each kind's sea is a graph whose nodes are those corners, and whose edges are the straight runs between corners that see each other. `SeaGraphBuilder` makes one per kind, from the kind's water with every part fitted, using the settings on `SeaGraph`.

### Coast

The coast is the water's outline, traced along the tiles' edges and smoothed by Douglas-Peucker with a tolerance of up to `TOLERANCE` (6) tiles. Smoothing follows three rules:

- **It only ever cuts into land.** Shaving land off makes a passage wider, never narrower, so no channel a boat can use is lost. Smoothing both ways narrowed channels.
- **It never happens within `NEAR` (16) tiles of a berth.** Harbours keep their exact shape, so boats come in to the berth properly.
- **A smoothed edge never crosses another stretch of coast.**

Rocks smaller than the smoothing tolerance are dropped. Without smoothing, tile staircases make nearly every corner a tangent point, and the graph has many times as many nodes.

### Nodes

- **Corners** are the coast's convex vertices, nudged `MARGIN` (2.5) tiles out into the water so routes keep off the coast. Where a passage is too narrow for that, the nudge drops to 1.5, then 0.5 tiles. The corners of the open water round each restricted area are nodes too, for boats without the part to enter it.
- **Launches**: each mooring the kind fits has one, the tile in its docking box where the boat has the most room round it, outside restricted areas where possible. Boarding puts the boat there, and a boat arriving there docks.

### Runs

Two nodes are joined by a run when they see each other past the smoothed coast, and the line is tangent to the coast at each corner it joins: a route never bends at a corner it could cut.

Every run is then **sailed on the tiles** by a boat of the kind at its top speed with every part fitted, within `CORRIDOR` (8) tiles of the line, slowing to a tile a tick where it must. The sail goes from the water by the first node to the water by the second (from the launch itself, and until docked, for a launch). Runs far from the coast are straight by construction and aren't sailed. Most others sail straight along, holding a course between two headings; the rest are found by a search on ticks alone, turning freely. About 70% of runs need the search, because they cross shaved land or dropped rocks, or graze a corner. A run that can't be sailed is dropped, so every run in the graph is one a boat can sail.

A run costs the boat's open-water bound for its length plus the extra ticks sailing it took, as round a tight bend, past a pier, or over land the smoothing shaved off. Each run also records the restricted areas under it and under the way it was sailed, so a boat without the part never takes it.

For each pair of moorings, the graph also stores the length of the shortest way between their launches (`legTiles`). At a boat's top speed this is a lower bound on its time, which the plugin gives its estimate.

## Checking the data

- `SailingTileModelTest` sails sampled legs with the exact tile solver (`SailingSolver`) and holds the graph's times to within about 10% of it at full speed. It also checks that boats leave their berths, and arrive where their hull passes over a target.
- `SailingDockBuoysTest` dumps the dock buoys round each berth from a cache. The plugin's docking box, 8 tiles each way from the berth, approximates the areas they mark (13 to about 20 tiles across).
- On the canonical sailing tier, route costs on the graph match the tile model at the median and are within 2% for nine routes in ten.

## Tried and dropped

- **Smoothing both ways** (into the water as well as into land) narrowed channels.
- **Dropping a corner where the chord between its neighbours is clear** lost passages through narrow channels. Safe reductions (guarding inlets, checking against the graph) removed only 5–25% of corners.
- **Slow-water zones**, marking water near the coast as slow by its clearance, were too aggressive and too complex. Letting a boat always slow down, and sailing each run at build time, replaced them.
- **Drawing routes as dog-legs between graph corners** turned more than the tile model's fewest-turn route, because graph corners aren't where that route bends. Drawing sails the tiles instead.
- **Sailing every tile in the search**, with per-mooring floods or leg tables, was up to 15 times slower than the graph for about the same costs.
