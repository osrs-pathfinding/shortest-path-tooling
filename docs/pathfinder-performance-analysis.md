# Pathfinder Performance Analysis

**Date:** 2026-05-14  
**Status:** `perf/optimise-pathfinder` is now the current performance baseline.

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

- First implementation committed on `perf/bidir-jps` (`91d6226d`).
- Approach: hybrid forward (full production search) + rate-limited reverse (walking-only, targets-only seed) for dead-end detection.
- A/B test harness available: `./gradlew bidirAB -PdashboardDataset=/dashboard/routes.csv`
- Results on `routes.csv` (29 scenarios):
  - Total time: 1186ms → 1162ms (-2.1%)
  - Total nodes: 6.76M → 5.98M (-11.5%)
  - Unreachable routes: near-instant (Brimhaven→Port Khazard: 195ms→0.1ms, White Knight 2F: 145ms→1.6ms)
  - 22/29 routes agree on reachability; 3 disagree on path length (bidir finds shorter paths in some edge cases)
  - Median per-run delta: +31.5% (reverse overhead on small reachable routes)
- Key limitation: global transport-destination seeding caused 36M-node explosion; resolved with rate-limited, targets-only seeding
- Next steps: path-length parity for disagreeing routes, reduce reverse overhead on reachable routes

## Delta Reconciliation

- The earlier “promised” improvement came from intermediate snapshots and narrower slices.
- The current numbers are from fresh same-condition reruns against `master` across all six datasets.
- Raw combined total is **-22.49%** (223484 ms -> 173213 ms), but this is heavily affected by the largest suite.
- Unweighted per-run mean change is **-2.26%** (the metric requested above), with median **-1.80%**.
- Dashboard phase-category deltas are mostly modest (roughly low single digits).
- The largest category wins are in `addNeighbors` overall, `enqueue`, and `collisionCheck`.
- Some phase buckets are slightly worse (`bankCheck`, `cutoffCheck`, `wildernessCheck`), which is why the overall gain can feel smaller than expected when looking at category-level bars.

## Notes

- This document is now intentionally concise and current-state oriented.
- Detailed algorithmic workstream notes were restored per request.
