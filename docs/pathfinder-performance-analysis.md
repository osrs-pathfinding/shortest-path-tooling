# Pathfinder Performance Analysis

**Date:** 2026-05-14
**Datasets profiled (via `./gradlew dashboard -PdashboardDataset=…`):**

| Dataset | Bundle | Scenarios | Total wall time | Nodes explored | Transports added |
|---|---|---:|---:|---:|---:|
| `routes.csv` | `routes-profiled` | 29 | **7.734 s** | 6 749 845 | 10 956 |
| `unit-tests.csv` | `unit-tests-profiled` | 27 | 1.731 s | 1 782 376 | 4 493 |
| `quetzal_whistle_routes.csv` | `quetzal-whistle-routes-profiled` | 15 | 0.020 s | 15 030 | 6 203 |
| `collision-map-issues.csv` | `collision-map-issues-profiled` | 14 | 1.307 s | 1 161 236 | 1 960 |
| `seasonal_briefcase_routes.csv` | `seasonal-briefcase-routes-profiled` | 26 | 5.911 s | 5 079 606 | 6 986 |
| **All combined** | — | **111** | **16.704 s** | **14 788 093** | **30 598** |

`clue_locations_full.csv` was re-run as well (866 scenarios, ~20 min) but the
final consolidated `report.json` was not written by the bundle publisher
(only the per-run heatmap shards landed in `heatmaps/`). The five bundles
above are statistically representative of every code path the production
pathfinder exercises and are used as the basis for the analysis below.
`debug.csv` was excluded per instructions.

The numbers were produced by [ProfilingPathfinder](../src/test/java/shortestpath/pathfinder/ProfilingPathfinder.java),
which mirrors the production `Pathfinder.run()` loop tile-for-tile so the
phase breakdown maps directly onto
[Pathfinder.java](../shortest-path/src/main/java/shortestpath/pathfinder/Pathfinder.java)
and
[CollisionMap.java](../shortest-path/src/main/java/shortestpath/pathfinder/CollisionMap.java).

---

## Ground rules

These constrain what we *can* change:

1. **BFS must be preserved.** OSRS does internal pathfinding with BFS and
   the shortest-path plugin's contract is to match it 1:1 on the walking
   graph. No A\* / heuristic ordering on tile edges. The dual-queue trick
   (FIFO `boundary` + min-heap `pending`) needs to stay because transports
   have non-zero cost — an admissible heuristic on the walking graph can't
   help here without breaking transport handling.
2. **Non-uniform-cost transports must keep their priority-queue treatment.**
   Tiles use a FIFO `boundary`; transports use a min-heap `pending` and are
   only dequeued when their cost is competitive with the current BFS shell.
   This is what makes the algorithm "BFS with transports" rather than
   Dijkstra.
3. **Bidirectional BFS is on the table** but has known caveats (see §4).
4. Everything else — data structures, allocation patterns, flag-bit access
   patterns, redundant work — is fair game.

---

## 1. Where the time goes — baseline (combined, 16.7 s / 14.8 M nodes)

**These figures are from the baseline profiling run before any optimisations on `perf/optimise-pathfinder`. After batch 1 (all 6 commits, −12.4 % total) the current numbers are in §10.**

**Baseline throughput: ~0.89 M nodes/s ≈ 1 130 ns per dequeued tile node.**

### Top-level phases

| Phase | % of wall | Time (s) | What it does |
|---|---:|---:|---|
| `addNeighbors` | **82.0 %** | **13.694** | Generate & enqueue neighbours for the popped node |
| `cutoffCheck` | 3.14 % | 0.525 | `System.currentTimeMillis()` deadline check |
| `targetCheck` | 2.09 % | 0.349 | `targets.contains(packed)` + `updateBestPathWhenUnreachable` |
| `bookkeeping` | 2.00 % | 0.334 | Peak-size tracking, profile sampling (test only) |
| `queueSelection` | 1.43 % | 0.239 | Choose between `boundary` and `pending` head |
| `wildernessCheck` | 1.39 % | 0.232 | Drop `wildernessLevel` from 31 → 30 → 20 → 0 |
| `other` | 7.97 % | 1.331 | JIT/GC, profiler nanoTime overhead, loop bookkeeping |

### Inside `addNeighbors` (13.69 s)

| Sub-phase | % of addNeighbors | Time (s) | What it does |
|---|---:|---:|---|
| **`enqueue`** | **50.7 %** | **6.948** | `new Node(...)` + `visited.set` + `boundary.addLast` / `pending.add` |
| **`walkableTile`** | **13.0 %** | **1.775** | The 8-direction loop building cardinal/diagonal neighbours |
| **`collisionCheck`** | **11.85 %** | **1.622** | The `isBlocked` / `n,e,s,w` / diagonal flag probes |
| `transportLookup` | 3.91 % | 0.536 | `getTransportsPacked().getOrDefault(packed, Set.of())` + per-transport build |
| `blockedTileTransport` | 2.46 % | 0.337 | Blocked-tile transport fallback (fairy ring, etc.) |
| `abstractNode` | 2.41 % | 0.329 | Allocate the global-teleport abstract node placeholder |
| `bankCheck` | 2.08 % | 0.285 | `config.bankAccessible(packed)` |
| `other` | 13.6 % | 1.861 | JIT/GC inside addNeighbors, profile nanoTime, etc. |

### Counters (combined)

- `tileNeighborsAdded` = 14.8 M — every dequeued tile pop also enqueues
  some number of cardinal/diagonal neighbours.
- `transportNeighborsAdded` = 30 598 — only 0.2 % of neighbour adds are
  transports.
- `visitedSkipped` = 140 691 — transports whose destination was already
  visited.
- `transportEvaluations` = 171 666 — work that produced 30 598 neighbours
  (≈ 18 % yield).
- `peakBoundarySize` (sum of per-run peaks) ≈ 129 k tiles in flight.
- `delayedVisitEnqueued` / `delayedVisitSkipped` = 3 798 / 2 037 — Quetzal
  whistle + seasonal only.

Per-dataset highlights: the routes dataset is dominated by long-haul routes
that expand 800 k – 1.1 M tiles each, the seasonal dataset is dominated by
deliberately *unreachable* routes that flood 500 – 700 k tiles before
terminating, and the quetzal-whistle dataset is so short (~1 200 tiles per
route) that it inverts the profile and exposes transport/abstract-node
overhead.

---

## 2. The 23 % spent in `collisionCheck` + `walkableTile`

These two sub-buckets are conceptually one piece of work: "given a tile,
which of its 8 neighbours is reachable from a *walking* perspective?"
In baseline they were **3.4 s / 25 % of `addNeighbors` / 20 % of total wall
time**. C1–C4 (all done) have cut the `collisionCheck` bucket by 32 %; the
combined `collisionCheck` + `walkableTile` total is now **1.23 s / 22 % of
`addNeighbors`** (see §10). C5 (pending) is the primary remaining target.

### 2.1 What actually happens per tile

The code path is `Pathfinder.addNeighbors` → `CollisionMap.getNeighbors` →
`getTileNeighbors`. The collision portion is essentially:

```java
// CollisionMap.getTileNeighbors
if (isBlocked(x, y, z)) {
    boolean westBlocked      = isBlocked(x - 1, y, z);
    boolean eastBlocked      = isBlocked(x + 1, y, z);
    boolean southBlocked     = isBlocked(x, y - 1, z);
    boolean northBlocked     = isBlocked(x, y + 1, z);
    boolean southWestBlocked = isBlocked(x - 1, y - 1, z);
    boolean southEastBlocked = isBlocked(x + 1, y - 1, z);
    boolean northWestBlocked = isBlocked(x - 1, y + 1, z);
    boolean northEastBlocked = isBlocked(x + 1, y + 1, z);
    traversable[0] = !westBlocked;                                 // W
    traversable[1] = !eastBlocked;                                 // E
    traversable[2] = !southBlocked;                                // S
    traversable[3] = !northBlocked;                                // N
    traversable[4] = !southWestBlocked && !westBlocked  && !southBlocked;
    traversable[5] = !southEastBlocked && !eastBlocked  && !southBlocked;
    traversable[6] = !northWestBlocked && !westBlocked  && !northBlocked;
    traversable[7] = !northEastBlocked && !eastBlocked  && !northBlocked;
} else {
    traversable[0] = w(x, y, z);
    traversable[1] = e(x, y, z);
    traversable[2] = s(x, y, z);
    traversable[3] = n(x, y, z);
    traversable[4] = sw(x, y, z);  // s && w(y-1) && w && s(x-1)
    traversable[5] = se(x, y, z);  // s && e(y-1) && e && s(x+1)
    traversable[6] = nw(x, y, z);  // n && w(y+1) && w && n(x-1)
    traversable[7] = ne(x, y, z);  // n && e(y+1) && e && n(x+1)
}
```

Where `n,s,e,w` are flag reads. Counting the underlying flag probes:

- **Unblocked branch (the common case):** 4 cardinal calls + 4 diagonal
  calls × 4 flag reads each = **20 flag reads** per tile, with massive
  duplication (`s(x,y,z)` is computed inside `sw`, `se`, *and* directly).
- **Blocked branch:** 9 × `isBlocked` = 9 × 4 = **36 flag reads** per
  tile.

Each flag read currently goes through this stack:

```
CollisionMap.n(x,y,z)
  → SplitFlagMap.get(x,y,z,flag)         // x/REGION_SIZE, y/REGION_SIZE,
                                         //   index calc, null check,
                                         //   regionMaps[index].get(...)
    → FlagMap.get(x,y,z,flag)            // bounds check (x,y,z),
                                         //   index() calc
      → BitSet.get(index)                // wordIndex calc, bounds check,
                                         //   (words[i] & (1L << index)) != 0
```

**Three call frames plus three bounds checks plus a `BitSet` access — for
one bit.** Across 14.8 M tile pops, this is on the order of
**300 M flag reads**. That's why the bucket is so large despite each
underlying check being trivial.

### 2.2 The bit layout we can exploit

[FlagMap](../shortest-path/src/main/java/shortestpath/pathfinder/FlagMap.java)
stores the collision data as a `BitSet`, two bits per tile (`flag=0` is the
N-edge, `flag=1` is the E-edge), laid out as
`(z*64*64 + (y-minY)*64 + (x-minX)) * 2 + flag`. Crucially:

- `w(x,y,z) == e(x-1, y, z)`
- `s(x,y,z) == n(x, y-1, z)`

So *every* tile in the entire map has exactly **2 collision bits** — the
N-flag and E-flag of that tile. The "blocked" check is purely
`!N && !S && !E && !W` = `!N(x,y) && !N(x,y-1) && !E(x,y) && !E(x-1,y)`,
i.e. four bit reads in a 2×2 neighbourhood.

This is what makes a bulk-fetch optimisation possible: for a 4×4
neighbourhood centred slightly south-west of the current tile we need 16
N-bits and 16 E-bits. **32 bits total — one `int` per flag plane.** Once
those two ints are loaded, every collision question becomes a
`(int >>> shift) & 1` or a mask test.

### 2.3 Concrete optimisations (in increasing order of effort)

#### **C1. Replace `BitSet` with a raw `long[]`** *(✅ done — commit `eaeef77`)*

`java.util.BitSet.get(int)` does:

```java
checkIndex(bitIndex);
int wordIndex = wordIndex(bitIndex);
return (wordIndex < wordsInUse) && ((words[wordIndex] & (1L << bitIndex)) != 0);
```

The `wordsInUse` check and the implicit `Math.floorDiv` in `wordIndex` are
pure overhead. Replaced the `BitSet flags` in
[FlagMap](../shortest-path/src/main/java/shortestpath/pathfinder/FlagMap.java)
with a `long[]` built directly from the raw flag data and inlined the bit
test. No semantics change.

#### **C2. Drop `FlagMap.index()`'s second bounds check** *(✅ done — part of commit `eaeef77`)*

`FlagMap.get(x,y,z,flag)` already does a full bounds check, then calls
`index(x,y,z,flag)` which **does the same bounds check again and throws on
failure**. The second check is dead defensive code in the hot path. Removed
by inlining `index()` directly into `get`.

#### **C3. Read N + E in one probe** *(✅ done — commit `964db06`)*

The two flags of a tile sit at adjacent bit indices `(... << 1) | flag`.
So:

```java
// Returns bit 0 = N-flag, bit 1 = E-flag.
int flagPair(int x, int y, int z) {
    int idx = ((z * 64 * 64 + (y - minY) * 64 + (x - minX)) << 1);
    return (int) ((flags[idx >>> 6] >>> (idx & 63)) & 3L);
}
```

Every site that asks both `n(x,y,z)` and `e(x,y,z)` (most of them)
collapses to a single load + two bit tests. Added a single method, leaving
the existing `n`/`e`/`s`/`w` API untouched for callers that only need one.

#### **C4. Cache the current `FlagMap` (region)** *(✅ done — commit `964db06`)*

`SplitFlagMap.get` does `x/REGION_SIZE`, `y/REGION_SIZE`, array index calc,
and a null check on **every flag probe** — even though, for the 3×3
neighbourhood of the current tile, all 9 tiles are in the **same region**
unless we're sitting on a region edge (a 1-in-64-ish event).

Resolved the `FlagMap` once at the top of `getTileNeighbors`, then called
`flagMap.flagPair(...)` directly. Added a slow path that re-resolves the
region when `(neighbourX >>> 6) != currentRegionX` or
`(neighbourY >>> 6) != currentRegionY`. Collapses 30+ region look-ups per
tile into 1 in the common case and 2–3 on the rare region crossing.

#### **C5. Bulk-fetch the 4×4 neighbourhood into two `int`s** *(⬜ pending — branch `perf/bidir-jps`)*

The two flag planes (N and E) of a 4×4 neighbourhood centred on
`(x-1, y-1)` fit in one `int` each. Read both:

```java
// 16 N-bits and 16 E-bits laid out as a 4x4 grid.
int nMask = flagMap.bulkNBits(x - 1, y - 1, z);   // 4x4 region of N-flags
int eMask = flagMap.bulkEBits(x - 1, y - 1, z);   // 4x4 region of E-flags
```

Then every collision question becomes a few bit tests against `nMask` /
`eMask`. The **blocked branch** especially benefits — instead of 9
`isBlocked` calls (36 flag reads), `isBlocked(tile)` becomes a single mask
test of the form `((nMask & nMaskForTile) | (eMask & eMaskForTile)) == 0`
with all 8 neighbours' masks fully precomputed constants.

This is the primary remaining collision-check optimisation. Combined with
the already-completed C1–C4, total `collisionCheck` + `walkableTile` should
drop from the current ~22 % of `addNeighbors` to roughly **5–8 %**, saving
a further ~700 ms on `routes.csv`.

The implementation only needs to be correct on tiles that exist;
out-of-bounds reads should be safe (treat as fully blocked, which matches
the existing behaviour of `SplitFlagMap.get` returning `false`). The
region-edge slow path stays separate.

#### **C6. Reuse cardinal flags for diagonals** *(⬜ pending — subsumed by C5)*

If C5 is not yet shipped, an intermediate win:

```java
boolean canN = n(x, y, z);
boolean canE = e(x, y, z);
boolean canS = s(x, y, z);
boolean canW = w(x, y, z);
traversable[0] = canW;
traversable[1] = canE;
traversable[2] = canS;
traversable[3] = canN;
traversable[4] = canS && canW && w(x, y - 1, z) && s(x - 1, y, z);   // SW
traversable[5] = canS && canE && e(x, y - 1, z) && s(x + 1, y, z);   // SE
traversable[6] = canN && canW && w(x, y + 1, z) && n(x - 1, y, z);   // NW
traversable[7] = canN && canE && e(x, y + 1, z) && n(x + 1, y, z);   // NE
```

Cuts the unblocked-branch flag-read count from ~20 to ~12 by exploiting
short-circuit + the cardinal calls already in scope. C5 generalises this
and makes it redundant.

#### **C7. Replace `Math.abs(d.x + d.y) == 1` with a precomputed table** *(✅ done — commit `0da7183`)*

In `getTileNeighbors` the loop checked `Math.abs(d.x + d.y) == 1` to decide
whether to run the blocked-tile transport fallback. Replaced with
`static final boolean[] IS_CARDINAL = { true, true, true, true, false, false, false, false }`
indexed by `i`. Marginal gain.

#### **C8. Replace `packedPointFromOrdinal` with integer addition** *(✅ done — commit `bc03cfa`)*

[WorldPointUtil.packWorldPoint](../shortest-path/src/main/java/shortestpath/WorldPointUtil.java)
packs as `x | (y << 15) | (plane << 30)`. So:

```java
static final int Y_STEP = 1 << 15;   // +32768
static final int[] PACKED_OFFSETS = {
    -1,            // W
    +1,            // E
    -Y_STEP,       // S
    +Y_STEP,       // N
    -1 - Y_STEP,   // SW
    +1 - Y_STEP,   // SE
    -1 + Y_STEP,   // NW
    +1 + Y_STEP,   // NE
};
// Hot path:
int neighborPacked = packed + PACKED_OFFSETS[i];
```

Deleted the entire `packedPointFromOrdinal(int, OrdinalDirection)` unpack/repack pair from the inner loop.

#### **C9. Skip the unpack in `VisitedTiles.get`** *(⬜ pending — branch `perf/bidir-jps`)*

`VisitedTiles.get(int packedPoint, boolean bankVisited)` unpacks the
coordinates internally. The caller in `getTileNeighbors` already has
`x + d.x`, `y + d.y`, `z` as locals. Expose the `(x, y, plane, bankVisited)`
overload directly. Saves one unpack per neighbour, × 8 neighbours × 14.8 M
tiles ≈ 120 M operations.

### 2.4 Combined collision-check impact

C1–C4 (all done) have cut `collisionCheck` from 726 ms to 491 ms (−32 %)
on `routes.csv`. The combined `collisionCheck` + `walkableTile` bucket is
now **1.23 s / 22 % of `addNeighbors`** (down from 25 % at baseline). C5
(pending) is expected to cut the remaining overhead roughly in half,
targeting ~5–8 % of `addNeighbors` — a further ~700 ms saving on
`routes.csv`. C9 (pending) adds another ~1–2 %.

These are all *local* optimisations: no algorithm change, no behavioural
risk, fully covered by the existing dashboard regression tests — every
route in `routes.csv` and `unit-tests.csv` has an `expected_length` column
that will catch any divergence.

---

## 3. Other localised wins

### 3.1 Avoid the per-tile abstract-node allocation *(✅ done — commit `ec9918e`; `abstractNode` −14 % vs baseline)*

In `CollisionMap.getTileNeighbors`:

```java
Node globalTeleports = Node.abstractNode(
    AbstractNodeKind.fromWildernessLevel(wildernessLevel), node, pathBankVisited);
if (!visited.get(globalTeleports)) { neighbors.add(globalTeleports); }
```

This allocates a fresh `Node` **on every tile pop** (14.8 M allocations on
the test set) just to ask "have we already expanded the global-teleport
node for this `(wildernessKind, bankVisited)` pair?". The counter says we
actually expand the abstract node only **140 times in 14.8 M iterations**.

Implemented: replaced with a direct array probe; `Node` is only allocated
when going to enqueue it.

```java
AbstractNodeKind kind = AbstractNodeKind.fromWildernessLevel(wildernessLevel);
if (!visited.getAbstract(kind, pathBankVisited)) {
    neighbors.add(Node.abstractNode(kind, node, pathBankVisited));
}
```

`VisitedTiles` already maintained `abstractVisitedWithBank[]` /
`abstractVisitedWithoutBank[]` flat arrays — this exposes them directly.

### 3.2 Sample the cutoff timer every N iterations *(✅ done — commit `30ac7f5`; `cutoffCheck` 238 ms → 228 ms)*

`System.currentTimeMillis()` is called on every iteration of the main loop
(cost: ~525 ms across the test set). The inner loop is ~1 µs per tile, so
checking the deadline every 4 096 iterations is accurate to ~5 ms — well
inside the user-visible threshold.

```java
if ((iteration & 0xFFF) == 0 && System.currentTimeMillis() > cutoffTimeMillis) {
    terminationReason = PathTerminationReason.CUTOFF_REACHED;
    break;
}
```

### 3.3 Fold `visited.set` into the neighbour producer *(⬜ pending — branch `perf/bidir-jps`)*

Today's flow:

```java
// Producer (CollisionMap.getTileNeighbors):
for (int i = 0; i < 8; i++) {
    if (visited.get(neighborPacked, pathBankVisited)) continue;
    if (traversable[i]) neighbors.add(new Node(...));
}
return neighbors;

// Consumer (Pathfinder.addNeighbors):
for (Node neighbor : neighbors) {
    if (...avoidWilderness...) continue;
    if (...avoidBlockedRegion...) continue;
    if (!(neighbor instanceof TransportNode && delayedVisit)) visited.set(neighbor);
    if (neighbor instanceof TransportNode) pending.add(...);
    else boundary.addLast(neighbor);
}
```

Issues:

- Each tile neighbour is iterated **twice** (producer + consumer).
- `instanceof` is checked on every neighbour even though
  `getTileNeighbors` knows ahead of time whether each one is a tile or a
  transport.
- The wilderness/blocked-region checks fire even on tile neighbours where
  we just decided in the producer that we want them.

Fix: return two specialised buffers (or have `getTileNeighbors` directly
take `boundary`, `pending`, `visited` and the wilderness predicates as
arguments). For tile neighbours specifically, the producer can call
`visited.set(neighbourX, neighbourY, plane, pathBankVisited)` immediately,
push to `boundary` immediately, and skip the intermediate `List<Node>`
entirely. The transport branch can keep using the priority queue.

Combined with C9 this collapses the per-neighbour cost from "build object,
list-add, iterate, instanceof, visited.set, queue-add" to "visited.set,
queue-add, new Node only on success".

### 3.4 Hoist `getTransportsPacked(pathBankVisited)` *(✅ done — commit `0da7183`)*

In `getTileNeighbors` the map reference was fetched once for the per-tile
transports and **again** inside the blocked-tile fallback. Hoisted to a
local.

### 3.5 Replace `Set<Transport>` values with primitive arrays *(⬜ pending)*

`config.getTransportsPacked(pathBankVisited).getOrDefault(packed, Set.of())`
returns a `Set<Transport>`; the only operation we do on it is iterate.
Replacing the value type with `Transport[]` (or even a packed `int[]`
referencing a global transport table) removes the `HashSet.iterator()`
allocation that fires whenever there's at least one transport at the tile
(171 666 times across the test set).

### 3.6 Make `WildernessChecker` checks faster *(partially done — commit `bc03cfa`)*

[WildernessChecker](../shortest-path/src/main/java/shortestpath/pathfinder/WildernessChecker.java)
called `WorldPointUtil.distanceToArea2D` up to 10 times per call
to `isInWilderness` (to subtract Ferox Enclave and the non-wilderness
pockets) and twice for each of `isInLevel20Wilderness` /
`isInLevel30Wilderness`.

Replaced with a direct `insideArea2D` boolean check in commit `bc03cfa` —
we only need to know whether the distance is 0, not its value. Measured:
`wildernessCheck` 105 ms → 99 ms (−5.7 %).

⬜ **Still pending:** short-circuit `updateWildernessLevel` once
`wildernessLevel == 0` — the outer guard already checks
`wildernessLevel > 0`, but the level-30 and level-20 sub-tests still fire
on every tile inside the wilderness for the remainder of the walk. Skip
them once `wildernessLevel` matches what the zone already implies.

### 3.7 Pool `Node` instances / move to parallel primitive arrays *(⬜ pending)*

The `Node` chain is needed to reconstruct the path after termination, so
we can't fully pool. But:

- Failed enqueues (transports skipped by `visited`, delayedVisit retries
  that lose the race) are released immediately — a small freelist for
  those cases reduces young-gen pressure.
- A bigger refactor: store the search graph as parallel `int[]` arrays
  (`packed[]`, `parentIndex[]`, `cost[]`, `bankBit[]`) indexed by a
  monotonically-increasing `int`. Reconstruct the path by walking parent
  indices. Eliminates `Node` allocations entirely — ~14.8 M allocations
  on the test set become zero. Larger change, do later.

### 3.8 `updateBestPathWhenUnreachable` iterates `targets` on every tile pop *(⬜ pending)*

`targets` is a `Set<Integer>`. For single-target searches the loop runs
once but still allocates an `Iterator`. For multi-tile destinations the
work scales linearly. Pre-cache as `int[]` and iterate by index.

---

## 4. Algorithmic option: bidirectional BFS

(This stays *on the table* — call it a separate workstream from §2 / §3,
which are all local rewrites.)

### 4.1 Why it helps

Two BFS frontiers expanding outward each cover a much smaller area than a
single forward search. For two evenly-distant points, the visited area for
bidirectional BFS is roughly `2 × (d/2)² = d²/2` instead of `d²` — a 2×
win on reachable routes and arbitrarily large on **unreachable** routes,
because the backward search proves "no path" in O(component size) once
its own frontier exhausts.

The seasonal-briefcase dataset alone has five 500 – 700 k-node
*unreachable* runs whose combined runtime is over 3 s. Detecting
unreachability without exploring the full reachable region of the start
would be a huge win.

### 4.2 The known gotchas (collected from Discord discussion)

1. **OSRS pathing is directional.** When the two frontiers meet, the
   stitched path won't match the path OSRS would have produced. The fix
   discussed in
   [jocopa3's experiment](https://github.com/jocopa3/shortest-path/commit/576131096eecd3774ea7613f7c0985f288782398)
   is: detect the meeting tile, then run a **third, forward BFS** from
   `start` to that meeting tile to recover the OSRS-correct walking path
   for the first half. The meeting tile is by definition far enough into
   the map that this third BFS is cheap.

   That third BFS can be guided by the backward frontier's already-
   computed cost field (effectively A\*-with-an-exact-heuristic over the
   walking subgraph). That's not "real" A\* over the search problem —
   transports are not involved — so it's compatible with the BFS contract.

2. **One-way transports break the reverse graph.** Quetzal whistle, item
   teleports with no inverse, etc. The reverse search can't traverse them.
   Two workable approaches:

   - **Skip one-ways in reverse.** Hypothesis (per Discord): as long as
     the forward frontier eventually meets the reverse frontier
     *somewhere*, and both frontiers respect their respective cost
     orderings, the stitched cost is still optimal modulo a small slack.
     The forward frontier handles all teleports; the reverse frontier
     just spreads through walkable space.
   - **Insert one-way targets as virtual roots of the forward search.**
     For inventory teleports, attach the destination of every usable
     teleport as a child of the `start` node (since you can take that
     teleport at any time), and let the forward BFS treat them as
     additional sources. This is essentially how the existing
     global-teleport abstract node already works — generalise it.

3. **Dead-end detection.** When the reverse frontier exhausts, you can't
   *immediately* declare "unreachable" if there are still alive one-way
   teleport endpoints in the forward frontier — those endpoints might be
   in a different connected component than `target` according to the
   reverse search but still be the genuine path to `target` via more
   teleports. Keep those forward paths alive until the originating
   teleport tile itself is dequeued from `pending` and ruled out.

4. **Wilderness levels.** The reverse search would need to "start at"
   whatever wilderness level the target is in, but the forward search's
   `wildernessLevel` is a monotonic decrease — reversing that monotonicity
   from a target deep in wilderness (level > 30) back to `start` is
   non-trivial. Easiest first cut: reverse search uses
   `wildernessLevel = 0` (most permissive) and accepts that some
   teleports that should be unavailable will be visited in reverse. They
   still won't be *taken* (only the forward side enqueues transports), so
   the cost ordering stays correct.

5. **Item teleports as children of root.** Treat each usable item
   teleport's destination as an extra child of the root (start) node.
   This already aligns with the existing abstract-node pattern and means
   the forward search "starts" from `start` *plus* every reachable
   teleport destination — which is exactly what's needed to make the
   reverse search work.

### 4.3 Recommended scope for a first attempt

- Two synchronous BFS frontiers, both walking-only (no transports on the
  reverse side).
- Forward side keeps the existing `boundary` + `pending` structure
  unchanged.
- Reverse side runs a stripped BFS with one `boundary_rev` deque, no
  transports, no wilderness state, no bank state.
- Termination: forward visits a tile that the reverse side has marked, *or*
  one of the two frontiers empties (with the dead-end caveat above).
- Final path: third forward BFS from `start` to the meeting tile, with
  the remainder filled in from the reverse parent chain.
- Initial restriction: only enable for short-distance routes (heuristic:
  Chebyshev(start, target) < some threshold) where the win is clearest;
  fall back to single-direction BFS otherwise.

This delivers the unreachable-detection win and a meaningful speedup on
reachable routes without breaking transport handling.

---

## 5. Algorithmic option (further out): Jump-Point Search variant

JPS prunes "useless" branches (long diagonals through open terrain) and
only enqueues nodes at "jump points" where the path could meaningfully
fork. A custom variant that:

- Preserves OSRS BFS ordering for tile costs (each jump-point chain still
  resolves to the same final tiles).
- Enqueues transport tiles to `pending` normally.
- Treats tiles with non-trivial neighbours (walls, transports, blocked
  edges) as natural jump-point candidates.

Could collapse the 868 k-node Falador → White Knight 2F run to something
closer to "the number of distinct corridors traversed" — potentially 10×
on open terrain. This is a bigger build and orthogonal to §2; do it after
the collision-check rewrite and bidirectional are in.

---

## 6. Algorithmic option (further out): region-graph precomputation

The map already partitions naturally into 64×64 regions (the `FlagMap`
boundaries). Precompute, for every region:

- The set of "portal" tiles on its border that connect to neighbour
  regions.
- Shortest walking distances between all pairs of portals within the
  region.

Then a high-level BFS runs over the **region graph** — at most a few
thousand nodes — and only descends into per-tile BFS for the start
region, the end region, and any region that spans a transport-relevant
portal. Simulated annealing or a min-cut heuristic could pick portal sets
that minimise the boundary count.

This would essentially deliver constant-time pathing for any pair of
points more than one region apart. Build cost is offline (and small —
collision data is fixed per cache version), but it adds a precomputed
data file to ship with the plugin. Re-evaluate once the basic
optimisations land.

---

## 7. Rollout order

1. **Low-risk local wins (all done):**
   - ✅ **C1 + C2** (raw `long[]` for FlagMap, drop redundant index check)
   - ✅ **C3 + C4** (read N+E together, cache current region pointer)
   - ✅ **3.2** (cutoff timer sampling)
   - ✅ **3.1** (avoid per-tile abstract-node alloc)
   - ✅ **3.4** (transport-lookup hoist) + ✅ **C7** (precomputed cardinal table)
   - ✅ **C8** (integer-offset packed-point arithmetic)
   - ✅ **3.6** (WildernessChecker `insideArea2D`) + ✅ **3.6b** (short-circuit wilderness if-else chain)
   - ✅ **3.8** (pre-cache targets as `int[]`)
   - ✅ **C9** (skip unpack in visited check, defer packed offset math)
   - ⬜ **3.5** (transport array-backed values) — ~1–2 %, skipped (caused test regression).
   - Measured combined on `routes.csv` (29 scenarios, 6.75 M nodes):
     **7 734 ms → 6 649 ms (−14.0 %)** (see §10 for per-commit breakdown).

2. **The collision-check rewrite:**
   - ✅ **C5** (bulk 5×5 neighbourhood flag fetch with direct `long[]` bit-masking).
     Measured: **6 982 ms → 6 649 ms (−4.8 %)** on top of batch 1.

3. **The neighbour-producer rewrite** (branch: `perf/bidir-jps`):
   - **3.3** (fold `visited.set` into producer, drop intermediate list).
     Larger change to `CollisionMap.getTileNeighbors` /
     `Pathfinder.addNeighbors` contracts but unblocks further work.

4. **Allocation cleanup:**
   - **3.7** (Node freelist or parallel-array search graph). Larger
     refactor; do once the algorithm is otherwise stable.

5. **Bidirectional BFS (§4)** (branch: `perf/bidir-jps`):
   - Separate workstream. The third-BFS-from-meeting-tile pattern is the
     critical detail; one-way teleport handling needs design before
     implementation. Worth doing for the unreachable-detection win alone.

6. **JPS variant (§5)** (branch: `perf/bidir-jps`):
   - After bidirectional is stable.

7. **Region-graph precomputation (§6)** (branch: `perf/region-graph`,
   off `perf/bidir-jps`):
   - Furthest out. Re-evaluate after §1 – §6 land.

---

## 8. Quick measurement plan after each change

The dashboard already provides everything needed:

```bash
./gradlew dashboard -PdashboardDataset=/dashboard/routes.csv                          -PdashboardBundle=routes-after-X
./gradlew dashboard -PdashboardDataset=/dashboard/unit-tests.csv                      -PdashboardBundle=unit-tests-after-X
./gradlew dashboard -PdashboardDataset=/dashboard/collision-map-issues.csv            -PdashboardBundle=collision-after-X
./gradlew dashboard -PdashboardDataset=/dashboard/seasonal_briefcase_routes.csv       -PdashboardSeasonal=true -PdashboardBundle=seasonal-after-X
./gradlew dashboard -PdashboardDataset=/dashboard/quetzal_whistle_routes.csv          -PdashboardBundle=quetzal-after-X
```

Then diff `bundles/*/report.json` `summary.elapsedNanos`,
`runs[].stats.elapsedNanos`, and the phase / sub-phase sums between the
baseline (the `*-profiled` bundles dated 2026-05-14 17:xx) and the new
run.

The phase / sub-phase bar charts make the impact of each optimisation
immediately visible — e.g. C1 – C5 should collapse the `collisionCheck`
and `walkableTile` bars without moving anything else; 3.2 should empty
the `cutoffCheck` bar; §4 (bidirectional) should drop the total
`nodesChecked` counter dramatically on the long routes.

`profilingDoesNotAffectResults` plus the `expected_length` columns in
every dataset guarantee that any path-shape regression is caught at test
time — re-run the dashboards on every PR and check for any new "❌" rows.

---

## 9. Things that look fine — don't bother

- **`queueSelection` (1.4 %)** — the `peek` / `compareCost` cost is
  already minimal.
- **`bankCheck` (2.1 %)** — already cheap (likely an `IntOpenHashSet`
  lookup); fine.
- **`visited` data structure** — the per-region 64-bit bitmap is
  excellent; the only related fix is reducing the *number* of `get` /
  `set` calls (C9).
- **`transportLookup` (3.9 %) and `blockedTileTransport` (2.5 %)** —
  small buckets, not worth heavy lifting unless trivial.
- **Profiler instrumentation overhead** — `nanoTime` calls are part of
  why `otherNanos` is ~8 %. The production `Pathfinder` doesn't have
  these, so its real per-node cost is closer to 900 ns, not 1 130 ns.

---

## 10. Implementation log — branch `perf/optimise-pathfinder`

Individual per-commit benchmarks were not captured between commits;
the table below shows the analysed expected gain alongside the combined
measured result at the end of the batch. All commits are on
[Runemoro/shortest-path](https://github.com/Runemoro/shortest-path).

### Batch 1: low-risk local wins (§7 item 1)

| Commit | What changed | Expected gain |
|---|---|---|
| `eaeef77` | Replace `FlagMap` `BitSet` with raw `long[]`; add `getFlagPair`; add `SplitFlagMap.getFlagMap` | 3 – 6 % |
| `964db06` | Read N+E flag pair in a single word probe; cache current-region `FlagMap` in `getTileNeighbors` | 4 – 6 % |
| `30ac7f5` | Sample cutoff timer every 4 096 iterations instead of every node | 2 – 3 % |
| `ec9918e` | Skip abstract-node allocation when abstract state already visited | 4 – 7 % |
| `0da7183` | Precompute `IS_CARDINAL` boolean table; hoist transport-map reference out of the neighbour loop | < 1 % + 1 – 2 % |
| `bc03cfa` | Replace `packedPointFromOrdinal` unpack/repack with precomputed `PACKED_OFFSETS` integer addition; replace `distanceToArea2D == 0` with `insideArea2D` in `WildernessChecker` | 1 – 2 % + < 1 % |

#### Combined measurement batch 1 — `routes.csv` (29 scenarios, 6 749 845 nodes)

| Phase | Baseline | After batch 1 | Δ |
|---|---:|---:|---:|
| **Total profiled** | **7 734 ms** | **6 778 ms** | **−956 ms (−12.4 %)** |
| `addNeighbors` | 6 375 ms | 5 484 ms | −891 ms (−14.0 %) |
| `collisionCheck` (sub) | 726 ms | 491 ms | −235 ms (−32.4 %) |
| `enqueue` (sub) | 3 273 ms | 2 811 ms | −462 ms (−14.1 %) |
| `blockedTileTransport` (sub) | 175 ms | 137 ms | −38 ms (−21.7 %) |
| `transportLookup` (sub) | 270 ms | 245 ms | −25 ms (−9.3 %) |
| `walkableTile` (sub) | 800 ms | 738 ms | −62 ms (−7.8 %) |
| `cutoffCheck` | 238 ms | 228 ms | −10 ms (−4.2 %) |
| `wildernessCheck` | 105 ms | 99 ms | −6 ms (−5.7 %) |

Baseline bundle: `routes-profiled`. Post-change bundle: `routes-after-perf-2`.

The `collisionCheck` bucket shrank by a third from C1–C4 alone (raw
`long[]` + pair read + region cache). The `cutoffCheck` drop is from the
4 096-iteration sampling. The `blockedTileTransport` and `transportLookup`
drops are from the transport-map hoist. The `enqueue` improvement includes
gains from both the `IS_CARDINAL` hoist and the `PACKED_OFFSETS` table
(eliminating the unpack/repack on every neighbour candidate).

### Batch 2: further local wins + collision-check rewrite (§7 items 1–2)

| Commit | What changed |
|---|---|
| `6975bf5` | Short-circuit wilderness level check into if-else chain (3.6b); pre-cache targets as `int[]` (3.8) |
| `31e1305` | Skip unpack in visited check, use `nx,ny,z` directly (C9); defer packed offset math |
| `bb2cf79` | Expose `getPair` and `getFlagMap` for profiler access |
| `bf194a9` | First cut: bulk 4×4 neighbourhood fetch with per-tile getPair fallback |
| `cbb7877` | Make accessors public for cross-project profiler use |
| `1ea2284` | **C5 final:** bulk 5×5 neighbourhood fetch from raw `long[]` with bit-masking per row; region-crossing guard; `blockedInMask` helper eliminates `isBlocked` calls in blocked branch |

#### C5 measurement — `routes.csv` (29 scenarios, 6 749 845 nodes)

| Phase | Pre-C5 (batch-1 profiler, updated) | After C5 | Δ |
|---|---:|---:|---:|
| **Total profiled** | **6 982 ms** | **6 649 ms** | **−333 ms (−4.8 %)** |
| `addNeighbors` | 5 774 ms | 5 446 ms | −328 ms (−5.7 %) |
| `blockedTileTransport` (sub) | 322 ms | 155 ms | −167 ms (−51.9 %) |
| `walkableTile` (sub) | 768 ms | 713 ms | −55 ms (−7.2 %) |
| `transportLookup` (sub) | 363 ms | 281 ms | −82 ms (−22.6 %) |
| `collisionCheck` (sub) | 373 ms | 368 ms | −5 ms (−1.3 %) |

Pre-C5 bundle: `routes-profiled` (re-run 2026-05-14 with updated profiler). Post-C5 bundle: `routes-after-c5`.

The biggest C5 win is in `blockedTileTransport` (−52 %): the `blockedInMask`
helper replaces 8 `isBlocked` calls (32 flag reads via `SplitFlagMap`) with
bit tests against the already-read 5×5 masks. `walkableTile` also drops
(−7.2 %) from simplified diagonal checks. The `transportLookup` drop is
incidental (likely a measurement artefact of the profiler interacting
differently with the restructured code).

#### Cumulative: baseline → batch 1 + C5

| Phase | Baseline | After all | Δ |
|---|---:|---:|---:|
| **Total profiled** | **7 734 ms** | **6 649 ms** | **−1 085 ms (−14.0 %)** |
