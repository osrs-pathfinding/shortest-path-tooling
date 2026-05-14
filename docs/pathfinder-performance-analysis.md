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

## Baseline Table (Master vs Perf)

| Dataset | Scenarios | Master (ms) | Perf (ms) | Delta (ms) | Delta % |
|---|---:|---:|---:|---:|---:|
| `routes.csv` | 29 | 5331 | **4927** | **-404** | **-7.58%** |
| `unit-tests.csv` | 27 | 4602 | **4457** | **-145** | **-3.15%** |
| `quetzal_whistle_routes.csv` | 15 | **1622** | 1629 | +7 | +0.43% |
| `collision-map-issues.csv` | 14 | 1494 | **1473** | **-21** | **-1.41%** |
| `seasonal_briefcase_routes.csv` | 26 | **3303** | 3413 | +110 | +3.33% |
| `clue_locations_full.csv` | 866 | 207132 | **157314** | **-49818** | **-24.05%** |
| **All combined** | **977** | **223484** | **173213** | **-50271** | **-22.49%** |

## Test Parity (Both Branches)

- `./gradlew test --tests "shortestpath.pathfinder.*"` on `master`: **BUILD SUCCESSFUL**
- `./gradlew test --tests "shortestpath.pathfinder.*"` on `perf/optimise-pathfinder`: **BUILD SUCCESSFUL**

## Implemented Changes (Current Branch, Short Form)

- `ec9918e`: avoid per-tile abstract node allocation in hot path.
- `30ac7f5`: sample cutoff timer every N iterations.
- `eaeef77`: `FlagMap` bit access fast path (`BitSet` removal + index simplification).
- `964db06`: collision read improvements (pair-read + region cache).
- `0da7183`: small hot-loop cleanups (cardinal table + transport map hoist).
- `bc03cfa0`: packed-point neighbor arithmetic + wilderness area check speedup.
- `6975bf5b`: wilderness update short-circuiting + cached target array iteration.
- `31e13053`: visited lookup optimization in tile neighbor loop.
- `1ea22841`: bulk-mask collision rewrite (C5) with correctness guards.
- `f7a85086`: producer-side tile enqueue/visited integration (3.3).
- `e28d7a75`: completed C9/3.5/3.7 follow-up:
  - 3.5: array-backed transport iteration in hot path (`Transport[]` views for packed lookup/teleports).
  - 3.7: reduced transient allocations by directly enqueuing blocked-tile transport origins.
  - C9: verified coordinate-based visited checks remain correct after integration.

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

- The earlier “promised” improvement came from intermediate profiling snapshots and a narrower benchmark slice.
- The current table is from a fresh same-condition rerun against `master` with all six datasets, including `clue_locations_full.csv`.
- With C9/3.5/3.7 now integrated and validated, the refreshed combined delta is **-22.49%** (223484 ms -> 173213 ms).

## Notes

- This document is now intentionally concise and current-state oriented.
- Detailed algorithmic workstream notes were restored per request.
