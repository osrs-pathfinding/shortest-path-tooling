# Pathfinder Performance Analysis

**Date:** 2026-05-14  
**Status:** `perf/optimise-pathfinder` is now the current performance baseline.

## Comparison Setup

- Production pathfinder timing mode used for both runs: `-PdashboardProfile=false`.
- Same dataset suite re-run back-to-back on both branches under the same machine conditions.
- shortest-path commits compared:
  - `master`: `d3b9b0f7e76fb52c76c7ef03c52ebac18812a82c`
  - `perf/optimise-pathfinder`: `f7a850863879c553033e2344c256c3a361882b74`
- shortest-path-tooling commit used for the run harness/doc: `master` + local comparison/doc changes.

## Baseline Table (Master vs Perf)

| Dataset | Scenarios | Master (ms) | Perf (ms) | Delta (ms) | Delta % |
|---|---:|---:|---:|---:|---:|
| `routes.csv` | 29 | 5132 | **4863** | **-269** | **-5.24%** |
| `unit-tests.csv` | 27 | 4531 | **4514** | **-17** | **-0.38%** |
| `quetzal_whistle_routes.csv` | 15 | **1615** | 1636 | +21 | +1.30% |
| `collision-map-issues.csv` | 14 | 1508 | **1459** | **-49** | **-3.25%** |
| `seasonal_briefcase_routes.csv` | 26 | 3324 | **3321** | **-3** | **-0.09%** |
| **All combined** | **111** | **16110** | **15793** | **-317** | **-1.97%** |

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

## Remaining Work

- C9 follow-up verification/tuning after latest integrated runs.
- 3.5 value representation migration (`Set<Transport>` -> primitive array form) was attempted and reverted; revisit only with strict correctness guardrails.
- 3.7 allocation model refactor (pool/parallel arrays) still open.
- Algorithmic branch work stays separate from this branch:
  - bidirectional BFS (`perf/bidir-jps`)
  - JPS experiments (`perf/bidir-jps`)
  - region-graph precompute (`perf/region-graph`)

## Notes

- This document is now intentionally concise and current-state oriented.
- Older exploratory analysis and speculative recommendations were pruned.
