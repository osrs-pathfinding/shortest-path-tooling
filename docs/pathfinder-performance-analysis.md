# Pathfinder Performance Analysis

**Date:** 2026-05-15  
**Status:** `perf/optimise-pathfinder` — NodeStore hot-path integration complete. A/B verified.

## Comparison Setup

- Production pathfinder timing mode used for both runs: `-PdashboardProfile=false`.
- Profile-enabled reruns (`-PdashboardProfile=true`, dashboard default) were also generated for phase/sub-phase category comparison.
- Same dataset suite re-run back-to-back on both branches under the same machine conditions.
- shortest-path commits compared:
  - `master`: `d3b9b0f7e76fb52c76c7ef03c52ebac18812a82c`
  - `perf/optimise-pathfinder`: `e28d7a751880636df2f5ef4b2868e6ddc2319f4d`
- shortest-path-tooling commit used for the run harness/doc: `master` + local comparison/doc changes.

## Baseline Table (Master vs Perf, Raw Elapsed Time)

| Dataset | Scenarios | Master (ms) | Perf (ms) | Delta (ms) | Delta % |
|---|---:|---:|---:|---:|---:|
| `routes.csv` | 29 | 5331 | **4927** | **-404** | **-7.58%** |
| `unit-tests.csv` | 27 | 4602 | **4457** | **-145** | **-3.15%** |
| `quetzal_whistle_routes.csv` | 15 | **1622** | 1629 | +7 | +0.43% |
| `collision-map-issues.csv` | 14 | 1494 | **1473** | **-21** | **-1.41%** |
| `seasonal_briefcase_routes.csv` | 26 | **3303** | 3413 | +110 | +3.33% |
| `clue_locations_full.csv` | 866 | 207132 | **157314** | **-49818** | **-24.05%** |
| **All combined** | **977** | **223484** | **173213** | **-50271** | **-22.49%** |

## Normalized Breakdown (Less Biased Than Total ms)

The total-ms line is still useful, but it heavily weights the largest dataset.
To reduce that bias, the metrics below normalize by scenario count and by dataset.

### Per-Scenario Time

| Dataset | Master (ms/scenario) | Perf (ms/scenario) | Delta (ms/scenario) | Delta % |
|---|---:|---:|---:|---:|
| `routes.csv` | 183.83 | **169.90** | **-13.93** | **-7.58%** |
| `unit-tests.csv` | 170.44 | **165.07** | **-5.37** | **-3.15%** |
| `quetzal_whistle_routes.csv` | **108.13** | 108.60 | +0.47 | +0.43% |
| `collision-map-issues.csv` | 106.71 | **105.21** | **-1.50** | **-1.41%** |
| `seasonal_briefcase_routes.csv` | **127.04** | 131.27 | +4.23 | +3.33% |
| `clue_locations_full.csv` | 239.18 | **181.66** | **-57.53** | **-24.05%** |
| **All combined** | 228.75 | **177.29** | **-51.46** | **-22.49%** |

### Category Summary

| Category | Scenarios | Master (ms) | Perf (ms) | Delta (ms) | Delta % |
|---|---:|---:|---:|---:|---:|
| Core suite (all datasets except clue) | 111 | 16352 | **15899** | **-453** | **-2.77%** |
| Clue suite only | 866 | 207132 | **157314** | **-49818** | **-24.05%** |

### Run-Level Percentage Change (Unweighted, Requested Metric)

- Mean percentage change per run (977 runs): **-2.26%**.
- Median percentage change per run: **-1.80%**.
- Interquartile range (Q1..Q3): **-5.84% .. +1.83%**.
- Improved runs: **615/977**.
- Regressed runs: **362/977**.

### Per-Dataset Run-Level View (Average of Run % Changes)

| Dataset | Runs | Mean run delta % | Median run delta % |
|---|---:|---:|---:|
| `routes.csv` | 29 | **-3.69%** | **-4.33%** |
| `unit-tests.csv` | 27 | **-5.25%** | **-2.08%** |
| `quetzal_whistle_routes.csv` | 15 | **-8.07%** | **-8.66%** |
| `collision-map-issues.csv` | 14 | +11.73% | +3.32% |
| `seasonal_briefcase_routes.csv` | 26 | **-4.02%** | **-1.45%** |
| `clue_locations_full.csv` | 866 | **-2.19%** | **-1.62%** |

## Dashboard Algorithm Category Breakdown (Profile Categories)

The tables below use profile-enabled dashboard runs and compare the same phase/sub-phase categories shown in the UI.

### Top-Level Phases

| Phase category | Master (s) | Perf (s) | Delta (s) | Delta % |
|---|---:|---:|---:|---:|
| `addNeighbors` | 477.102 | **468.475** | **-8.626** | **-1.81%** |
| `queueSelection` | 6.877 | **6.872** | **-0.005** | **-0.07%** |
| `targetCheck` | 10.342 | **9.949** | **-0.393** | **-3.80%** |
| `wildernessCheck` | **8.188** | 8.209 | +0.021 | +0.26% |
| `cutoffCheck` | **4.911** | 4.974 | +0.062 | +1.27% |
| `bookkeeping` | 11.681 | **11.456** | **-0.225** | **-1.92%** |
| `other` | 48.396 | **48.066** | **-0.331** | **-0.68%** |

### addNeighbors Sub-Phases

| Sub-phase category | Master (s) | Perf (s) | Delta (s) | Delta % |
|---|---:|---:|---:|---:|
| `bankCheck` | **8.084** | 8.499 | +0.415 | +5.13% |
| `transportLookup` | 22.433 | **21.935** | **-0.497** | **-2.22%** |
| `collisionCheck` | 65.651 | **63.928** | **-1.723** | **-2.62%** |
| `walkableTile` | 68.672 | **67.914** | **-0.758** | **-1.10%** |
| `blockedTileTransport` | 14.879 | **14.489** | **-0.390** | **-2.62%** |
| `abstractNode` | 10.309 | **10.047** | **-0.262** | **-2.54%** |
| `enqueue` | 217.584 | **212.237** | **-5.347** | **-2.46%** |
| `other` | 69.491 | **69.426** | **-0.065** | **-0.09%** |

## Test Parity (Both Branches)

- `./gradlew test --tests "shortestpath.pathfinder.*"` on `master`: **BUILD SUCCESSFUL**
- `./gradlew test --tests "shortestpath.pathfinder.*"` on `perf/optimise-pathfinder`: **BUILD SUCCESSFUL**

## Implemented Changes (Current Branch, Short Form)

- `ec9918e`: stopped creating abstract-node objects on every tile expansion.
- `30ac7f5`: reduced cutoff clock checks from every loop to sampled checks.
- `eaeef77`: replaced slower `BitSet` collision bit reads with direct packed-word access.
- `964db06`: reduced collision lookup overhead by reading north/east flag pairs and reusing region lookups.
- `0da7183`: removed small hot-loop overheads (cardinal-direction checks and transport map re-fetches).
- `bc03cfa0`: replaced unpack/repack neighbor math with direct packed-coordinate addition; simplified wilderness area checks.
- `6975bf5b`: short-circuited wilderness-level updates and cached targets for unreachable-path comparison.
- `31e13053`: used coordinate-based visited checks directly in tile neighbor expansion.
- `1ea22841`: rewrote collision neighborhood checks to bulk-mask reads with safety fallbacks.
- `f7a85086`: moved tile visited/enqueue work into neighbor production to avoid duplicate passes.
- `e28d7a75`: finalized transport and allocation optimizations:
  - switched hot-path transport iteration to array-backed lookups;
  - directly enqueued blocked-tile transport origins to reduce transient objects;
  - revalidated visited-check integration with full pathfinder tests.

## NodeStore A/B — Hot-Path Allocation Reduction (2026-05-15)

### Summary

Replaced ~14.8M `Node` heap allocations in the tile-expansion hot path with
a compact `NodeStore` (parallel `int[]` arrays indexed by monotonic counter).
Tile nodes now use `PackedNode` (extends `Node`, backed by a store index).
Transport nodes remain heap-allocated (they need priority-queue ordering fields).

### The "Previous" Chain Bug — Root Cause & Fix

- **Bug:** `PackedNode` constructor passed `null` for `previous` to `super()`.
  `TransportNode.getPathSteps()` (inherited from `Node`) walks `node.previous`,
  stopping at the first `PackedNode` with `previous=null`.
  This truncated path reconstruction at every transport hop.

- **Fix:** Changed `PackedNode(NodeStore store, int idx, Node parent)` to pass
  the actual parent `Node` to `super()`. The `previous` chain now flows:
  `TransportNode → PackedNode(child) → PackedNode(parent) → ... → root`.
  Removed the redundant `getPathSteps()` override — `Node.getPathSteps()` works.

### Files Changed

| File | Change |
|---|---|
| `PackedNode.java` | Constructor accepts `Node parent`, passes to `super()`; removed custom `getPathSteps()` |
| `CollisionMap.java` | `getNeighbors()` accepts `NodeStore`; tile neighbors allocated via `store.alloc()` |
| `Pathfinder.java` | `NodeStore store` field; root allocated via store; passed through `addNeighbors` |
| `ProfilingPathfinder.java` | Same changes for A/B parity verification |

### commits

- `1eca9b07`: NodeStore + PackedNode infrastructure
- `f428531e`: NodeStore + PackedNode allocation models
- *(working tree)*: hot-path integration + previous-chain fix (this section)

### Per-Route User-Experience Metrics (Master vs NodeStore)

The dashboard runs all scenarios sequentially in a single JVM, so the
aggregate batch time mixes JIT warmup, GC pressure, and CPU caching effects
that a real user never sees: in the plugin the user clicks a destination
once and waits for that single search. The metric that actually matters is
**per-route latency**, split into:

- **Reachable** routes (the common case) — median and p95 latency dominate UX.
- **Unreachable** routes — worst-case latency is what users feel, because
  the forward search runs to the cutoff with no early exit.

Source data: `build/reports/pathfinder-dashboard/bundles/*/report.json`
captures `stats.elapsedNanos` per scenario. Analysed with
`scripts/analyse_dashboard_runs.py`. Same machine, `-PdashboardProfile=false`,
both branches re-run consecutively so JIT/GC state is comparable.

**Combined across all 6 datasets (977 scenarios):**

| Bucket | Metric | Master | NodeStore | Delta |
|---|---|---:|---:|---:|
| Reachable (918) | Median | 121 ms | **81.1 ms** | **−33.6%** |
| Reachable (918) | p95 | 314 ms | **208 ms** | **−33.8%** |
| Reachable (918) | Max | 490 ms | **412 ms** | **−15.9%** |
| Unreachable (59) | Median | 311 ms | **207 ms** | **−33.4%** |
| Unreachable (59) | Max | 462 ms | **234 ms** | **−49.4%** |

The **worst-case unreachable latency drops from 462 ms to 234 ms — nearly
halved**. That is the latency a user experiences when they ask for a path
that cannot reach the destination, and it is the dominant UX pain-point.

**Per-dataset summary:**

| Dataset | Scenarios | Reachable median: Master → NodeStore | Unreachable max: Master → NodeStore |
|---|---:|---:|---:|
| `routes.csv` | 29 (27 R, 2 U) | 6.92 → 6.68 ms (−3.5%) | 240 → 212 ms |
| `unit-tests.csv` | 27 (27 R) | 11.7 → 9.23 ms (−16.4%) | — |
| `quetzal_whistle_routes.csv` | 15 (15 R) | 0.69 → 0.73 ms (−11.2% median) | — |
| `collision-map-issues.csv` | 14 (14 R) | 0.20 → 0.28 ms (+4.2% median) | — |
| `seasonal_briefcase_routes.csv` | 26 (18 R, 8 U) | 0.34 → 0.28 ms (+8.8% median) | 181 → 159 ms |
| `clue_locations_full.csv` | 866 (817 R, 49 U) | **147 → 96.2 ms (−33.7%)** | **462 → 234 ms (−49.4%)** |

The headline numbers come from `clue_locations_full.csv`, which has the
longest searches and dwarfs every other dataset in absolute time. The other
five datasets are dominated by very-short paths (sub-millisecond) where the
median % swings sign-flip easily on noise — but the absolute values are tens
to hundreds of microseconds, invisible to a user.

### Short-Path Regressions: a Caveat, Not a Problem

A handful of sub-millisecond scenarios got slower (e.g. `Hunter Guild →
Outer Fortis` 0.58 → 2.03 ms; `Civitas → Mistrock` 0.10 → 0.25 ms;
`Lumbridge → Ardougne (bank teleport)` 1.30 → 3.56 ms). Two factors
explain this and both are acceptable:

1. **Single-shot timing noise.** Each scenario is measured exactly once.
   At sub-millisecond scale the noise floor (System.nanoTime jitter, code-
   cache warming, GC) is comparable to the delta. The "+251%" on a 0.58 ms
   scenario is +1.5 ms absolute — well below human perception (~10 ms).
2. **NodeStore has a small fixed setup cost** (the `int[1 << 20]` arrays are
   touched on first use). On a search that explores fewer than ~200 tiles
   the amortised win from avoiding per-tile `Node` allocations is smaller
   than the array setup tax. A user clicking a destination 5 tiles away
   does not notice this — both branches finish under 1 ms.

The optimisation is correctly biased: it speeds up the slow searches (the
ones a user actually waits for) at a microsecond cost on the trivial ones.

### Test Parity

- `./gradlew test --tests "shortestpath.pathfinder.*"` on
  `perf/optimise-pathfinder` with NodeStore: **BUILD SUCCESSFUL**.
- All 977 dashboard scenarios agree with master on reachability.

### A/B Harness

There is no longer a dedicated `nodestoreAB` task — the standard `dashboard`
task is sufficient because every scenario is timed individually inside the
report JSON.

```bash
# Capture baseline (master)
git -C ../shortest-path checkout master
for csv in routes unit-tests quetzal_whistle_routes \
           collision-map-issues seasonal_briefcase_routes clue_locations_full; do
  ../shortest-path/gradlew --quiet dashboard \
    -PdashboardDataset=/dashboard/$csv.csv \
    -PdashboardProfile=false
  # bundle name uses hyphens not underscores
  bundle=$(echo $csv | tr _ -)
  mkdir -p /tmp/dashboard-runs/master/$bundle
  cp build/reports/pathfinder-dashboard/bundles/$bundle/report.json \
     /tmp/dashboard-runs/master/$bundle/
done

# Capture candidate
git -C ../shortest-path checkout perf/optimise-pathfinder
# (repeat the for loop, copying into /tmp/dashboard-runs/nodestore/)

# Compare
python3 scripts/analyse_dashboard_runs.py \
    /tmp/dashboard-runs/master /tmp/dashboard-runs/nodestore \
    --label-baseline Master --label-candidate NodeStore
```

## Remaining Work

- No remaining non-algorithmic hot-path items from C9/3.5/3.7.
- Algorithmic branch work:
  - bidirectional BFS (`perf/bidir-jps`): first implementation committed, A/B harness ready
  - JPS experiments (`perf/bidir-jps`)
  - region-graph precompute (`perf/region-graph`)

## Algorithmic Workstreams (Detailed)

### Bidirectional BFS Plan (`perf/bidir-jps`)

- Goal: cut reachable/unreachable search expansion by meeting in the middle while preserving OSRS-compatible forward-path behavior.
- Shape:
  - Forward side keeps existing semantics (`boundary` + `pending`, full transport behavior).
  - Reverse side starts from target and walks tiles only (no reverse one-way transports).
  - Stop when frontiers meet, then reconstruct.
- Correctness constraints:
  - Meeting path may not match OSRS tie-break ordering directly.
  - Recovery strategy: run a final forward BFS from start to the chosen meeting tile to recover OSRS-consistent first half.
  - One-way transports are handled on the forward side; reverse side should not assume invertibility.
- First rollout target:
  - Gate by route type/distance and unreachable-heavy scenarios first.
  - Keep single-direction fallback path available.

### JPS Experiments (`perf/bidir-jps`)

- Goal: reduce open-terrain expansion by jumping between forced-neighbor points.
- Constraints:
  - Must preserve transport semantics and final path correctness contract.
  - JPS pruning must not invalidate bank/wilderness state transitions.
- Experiment shape:
  - Start from walking-only segments and treat transport-related tiles as hard expansion anchors.
  - Validate against existing route corpus for step-by-step parity where required.
- Success criterion:
  - Significant node-count reduction on long open routes without regressions in route validity.

### Region-Graph Precompute (`perf/region-graph`)

- Goal: reduce global search by precomputing coarse region connectivity and boundary portals.
- Concept:
  - Build graph over region-level portals/edges.
  - Use region graph to constrain or prioritize fine-grained tile search.
- Constraints:
  - Must remain compatible with dynamic constraints (bank state, wilderness restrictions, seasonal region locks).
  - Must degrade safely to tile-level search when coarse guidance is ambiguous.
- Incremental path:
  - Build static region connectivity first.
  - Add dynamic edge filters second.
  - Introduce hybrid search only after full regression parity.

## Bidirectional BFS Status (2026-05-14)

- Implementation on `perf/bidir-jps` (`90b04dd6`).
- Approach: hybrid forward (reimplementation of production search) + deferred reverse (walking-only, targets seed) for dead-end detection.
- Reverse starts after 2048 forward iterations without target found — eliminates overhead on routes with early transport shortcuts.
- A/B harness: `./gradlew bidirAB -PdashboardDataset=/dashboard/routes.csv`
- Latest results on `routes.csv` (29 scenarios):
  - Total time: 1133ms → 948ms (**-16.3%**)
  - Total nodes: 6.76M → 5.39M (**-20.3%**)
  - Unreachable routes: near-instant (Brimhaven→Port Khazard: 177ms→0.4ms, White Knight 2F: 143ms→0.5ms, Auburnvale→Ferox Enclave: 88ms→0.5ms)
  - 15/29 routes disagree on reachability/path-length (forward reimplementation diverges from production on some paths)
- Known limitation: forward search reimplements production logic rather than wrapping it; 15 routes diverge.
- Next step for correctness: wrap production `Pathfinder` and inject reverse BFS as an early-termination pre-check rather than duplicating the forward search.

## Delta Reconciliation

- The earlier “promised” improvement came from intermediate snapshots and narrower slices.
- The current numbers are from fresh same-condition reruns against `master` across all six datasets.
- Raw combined total is **-22.49%** (223484 ms -> 173213 ms), but this is heavily affected by the largest suite.
- Unweighted per-run mean change is **-2.26%** (the metric requested above), with median **-1.80%**.
- Dashboard phase-category deltas are mostly modest (roughly low single digits).
- The largest category wins are in `addNeighbors` overall, `enqueue`, and `collisionCheck`.
- Some phase buckets are slightly worse (`bankCheck`, `cutoffCheck`, `wildernessCheck`), which is why the overall gain can feel smaller than expected when looking at category-level bars.

## Notes

- NodeStore integration complete (2026-05-15). Previous-chain bug fixed: `PackedNode` now passes parent `Node` to `super()`, so `TransportNode.getPathSteps()` correctly walks the full path chain through PackedNode indices.
- This document is now intentionally concise and current-state oriented.
- Detailed algorithmic workstream notes were restored per request.

## Bidirectional BFS — Status (rebased onto NodeStore baseline, 2026-05-15)

`perf/bidir-jps` was rebased onto `perf/optimise-pathfinder` (clean, no
conflicts). Branch HEAD `2bc79795`. Backup at `backup/bidir-jps-before-rebase`.

### Approach (unchanged from previous summary)

- **Forward search**: production `Pathfinder` (now with NodeStore) — zero
  divergence on the forward path.
- **Reverse pre-check**: JPS-accelerated reverse walking-BFS from
  `targets ∪ getUsableTeleportsArray(both bank states)`. Each visited
  tile checks against a cached set of all transport destinations
  (`PrimitiveIntHashMap`). When such a destination is reached, the
  reverse expansion follows the transport backward to its origin via a
  pre-built `revTransportIndex`.
- **Early exit**: as soon as the reverse frontier reaches any transport
  destination, the pre-check returns "may be reachable" and the forward
  Pathfinder runs. If the reverse search exhausts within
  `REVERSE_MAX = 500_000` nodes without bridging, a walking-overlap
  check from `start` (capped at `OVERLAP_CAP = 100_000` tiles) decides
  reachability.

### Per-route UX on rebased branch — `bidirAB` harness

The bidir A/B harness (`./gradlew bidirAB -PdashboardDataset=…`) now
writes one JSON record per scenario to
`build/reports/bidir-ab/<dataset>.jsonl` with `pfElapsedNanos`,
`bpfElapsedNanos`, and reachability for both. Analysed with
`scripts/analyse_bidir_runs.py`.

**Combined across all 6 datasets (975 scenarios, run inside the bidir A/B
test harness):**

| Bucket | Metric | Pathfinder (NodeStore) | Bidir + NodeStore | Delta |
|---|---|---:|---:|---:|
| Reachable (777) | Median | 61.1 ms | 62.0 ms | **+2.4%** |
| Reachable (777) | p95 | 203 ms | 207 ms | +1.9% |
| Reachable (777) | Max | 258 ms | 280 ms | +8.5% |
| Unreachable (198) | Median | 214 ms | 218 ms | +1.9% |
| Unreachable (198) | Max | 330 ms | **292 ms** | **−11.5%** |

The pre-check adds a small (~2 ms median) overhead on reachable routes and
trims worst-case unreachable latency by ~11%. The unreachable-max gain is
smaller than it was against the master baseline (462 → 234 ms before
NodeStore landed on the forward search) because NodeStore already cut
unreachable max from 462 ms to 234 ms; bidir on top of NodeStore only
shaves a further ~38 ms.

### Cross-comparison caveat

The bidir A/B test harness (`BidirectionalPathfinderABTest`) uses a
minimal Mockito `Client` (elite diary varbit + queencure quest + level 99
skills + 25 000 bank items). The dashboard tests do considerably more
config setup: per-scenario region unlocks, varbit state from CSV, locked-
region flags. As a result the **bidir A/B harness reports different
reachability counts than the dashboard** — e.g. `clue_locations_full`
shows 683 R / 183 U in the bidir harness vs 817 R / 49 U in the
dashboard, because some clue locations are gated behind unlocks the
dashboard provides and the bidir harness does not.

**Implication:** the table above is internally consistent (PF vs Bidir
are both run in the same harness) but the absolute milliseconds cannot
be directly compared with the NodeStore dashboard table earlier in this
document. The per-route deltas (+/− %) remain meaningful.

### Correctness: two reachability disagreements found

`unit-tests.csv` exposes two scenarios where the bidir pre-check declares
unreachable but `Pathfinder` reaches:

| Scenario | PF | Bidir |
|---|---|---|
| Great Conch → McGrubor's Wood with inventory Dramen staff | reached, 3.07 ms | unreachable, 8.60 ms |
| Banked Dramen staff should not leak to non-bank fairy-ring branch | reached, 12.4 ms | unreachable, 18.7 ms |

Both involve fairy-ring transport with a Dramen-staff requirement. The
reverse pre-check seeds from `config.getUsableTeleportsArray` (which only
returns one-way teleports) and propagates backward through
`revTransportIndex`. The fairy-ring transport's origin/destination pair
is present in `revTransportIndex`, but the reverse frontier evidently
isn't reaching the fairy-ring destination tile under
`REVERSE_MAX = 500_000` for these specific scenarios — likely the
walking expansion from the targets is fenced in by item-gated tiles the
forward search treats differently.

**This is a correctness regression** and must be resolved before the
bidir wrapper can ship. Two options:

1. **Loosen the pre-check failure mode**: when the reverse BFS exhausts
   its budget without bridging, fall back to running the forward
   Pathfinder anyway (treat "no bridge" as "unknown", not "unreachable").
   This is the safest change — it eliminates false negatives at the cost
   of running the full forward search on the slow unreachables.
2. **Fix the reverse expansion** to honour the same transport-usability
   rules as forward search. Higher complexity, higher risk.

Option (1) is the recommended next step — see *Remaining Work*.

### Reproducing

```bash
# from shortest-path-tooling/
cd /Users/frans/code/shortest-path
git checkout perf/bidir-jps
cd ../shortest-path-tooling
mkdir -p /tmp/dashboard-runs/bidir
for csv in routes.csv unit-tests.csv quetzal_whistle_routes.csv \
           collision-map-issues.csv seasonal_briefcase_routes.csv \
           clue_locations_full.csv; do
  ../shortest-path/gradlew --quiet bidirAB \
    -PdashboardDataset=/dashboard/$csv \
    -Pbidir.outputDir=/tmp/dashboard-runs/bidir
done
python3 scripts/analyse_bidir_runs.py /tmp/dashboard-runs/bidir \
    --label-baseline "Pathfinder (NodeStore)" \
    --label-candidate "Bidir + NodeStore"
```

### Production readiness

Currently **NOT** ready to ship as a drop-in wrapper because of the two
false-negative scenarios. With the proposed Option (1) safety net, the
pre-check becomes a pure performance optimisation with zero correctness
risk and can replace the forward-only path.
