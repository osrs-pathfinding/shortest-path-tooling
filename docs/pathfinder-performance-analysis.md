# Pathfinder Performance Analysis

**Date:** 2026-05-14  
**Status:** `perf/optimise-pathfinder` is now the current performance baseline.

## Comparison Setup

- Production pathfinder timing mode used for both runs: `-PdashboardProfile=false`.
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

### Dataset-Level Balance (Unweighted)

- Mean dataset delta (simple average across 6 datasets): **-5.41%**.
- Median dataset delta: **-2.28%**.
- Improved datasets: **4/6**.
- Regressed datasets: **2/6** (`quetzal_whistle_routes.csv`, `seasonal_briefcase_routes.csv`).

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
- Algorithmic branch work stays separate from this branch:
  - bidirectional BFS (`perf/bidir-jps`)
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

## Delta Reconciliation

- The earlier “promised” improvement came from intermediate snapshots and narrower slices.
- The current numbers are from a fresh same-condition rerun against `master` across all six datasets.
- Combined total is **-22.49%** (223484 ms -> 173213 ms), but this is dominated by clue routes.
- On the non-clue core suite alone, improvement is **-2.77%** (16352 ms -> 15899 ms).
- Two datasets still regress slightly, which explains why the result may look weaker than expected depending on which category you care about.

## Notes

- This document is now intentionally concise and current-state oriented.
- Detailed algorithmic workstream notes were restored per request.
