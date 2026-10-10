# Dashboard Guide

## Purpose

The dashboard is a developer tool for inspecting pathfinder behavior visually.

It exists for cases where a pass/fail assertion is not enough:

- a route is unexpectedly unreachable
- a route reaches the target but uses the wrong transport
- a bank-state transition happens in the wrong place
- a user reports a bad destination and we want a lightweight regression case
- profiling data needs visual inspection (phase timings, heatmaps, queue sizes)

Instead of looking only at a failing JUnit assertion, the dashboard lets you inspect:

- the rendered route on a Leaflet map
- path statistics (nodes checked, transports checked, elapsed time)
- transports used along the path
- where banked state begins
- profiler phase breakdowns, sub-phase timings, and time-series charts
- tile visit heatmaps showing search distribution
- multiple datasets through one shared UI with a bundle selector

## Project Architecture

The dashboard tooling lives in its own Gradle project (`shortest-path-tooling`), separate from the plugin.
The `shortest-path` plugin is included as a [composite build](https://docs.gradle.org/current/userguide/composite_builds.html)
via `settings.gradle`, so `testImplementation 'shortestpath:shortest-path'` resolves to the local submodule.

### Key source paths

| Path | Purpose |
|---|---|
| `src/test/java/shortestpath/dashboard/` | Dashboard test runner and report writing |
| `src/test/java/shortestpath/scenarios/` | Scenario suites (Java) and the `Scenario` model |
| `routing/src/main/java/shortestpath/profiles/` | `Profiles`: every named profile (canonical accounts and dashboard presets), `PluginSettings` |
| `src/test/java/shortestpath/pathfinder/` | `ProfilingPathfinder`, `PathfinderProfile` (test-only instrumented pathfinder) |
| `src/test/java/shortestpath/dump/` | Cache dumpers |
| `src/test/resources/scenarios/` | Data-only scenario CSVs and each suite's `expected-lengths/<suite>.json` |
| `src/test/resources/reachability-dashboard/` | Static frontend assets (`index.html`, `app.js`, `profiler.js`, `styles.css`) |
| `gradle/dashboards.gradle` | Gradle task definitions for all dashboard and capture tasks |
| `gradle/cache-dumpers.gradle` | Gradle task definitions for cache dump tasks |

The `ProfilingPathfinder` and `PathfinderProfile` classes live in this tooling repo, not in the plugin.
They are compiled alongside the dashboard test code and are not shipped as part of the RuneLite plugin.

Two additional files from the plugin's test tree are pulled into compilation via a `compileTestJava.source`
override in `build.gradle`:

- `shortest-path/src/test/java/shortestpath/pathfinder/TestPathfinderConfig.java`
- `shortest-path/src/test/java/shortestpath/TestShortestPathConfig.java`

## Site Model

The dashboard site is a static bundle written to:

`build/reports/pathfinder-dashboard`

It contains one shared frontend plus multiple report bundles:

```text
build/reports/pathfinder-dashboard/
  index.html
  app.js
  profiler.js
  styles.css
  bundles/
    index.json
    <bundle-name>/
      report.json
      heatmaps/
        <run-name>.json     (tile visit counts, one per route)
```

`bundles/index.json` is the registry the frontend reads first. Each bundle directory contains a `report.json`
with all route results and, when profiling is enabled, a `heatmaps/` directory with per-route tile visit count data.

To serve the site locally after building:

```bash
python -m http.server --directory build/reports/pathfinder-dashboard 8000
# Then open: http://localhost:8000
```

## Scenarios

The dashboard runs scenario suites. Scenarios, profiles, suites and the route data format are
described in [scenarios.md](scenarios.md); this page covers the dashboard itself.

## Gradle Tasks

### `dashboard` (default task)

Runs `DashboardTest` against one suite and writes a bundle into the output site.

```bash
./gradlew dashboard \
  -PdashboardSuite=routes \
  -PdashboardBundle=routes-profiled \
  -PdashboardTitle="Routes Profiled"
```

| Property | Default | Description |
|---|---|---|
| `dashboardSuite` | `routes` | The suite to run (`./gradlew -q scenarioIndex` lists them) |
| `dashboardDataset` | *(empty)* | A route JSON file to run instead of a suite (routes without `profiles` run with `ALL`) |
| `dashboardFilter` | *(empty)* | Run only the scenarios whose name or category contains it (ignoring case), e.g. `issue-140` |
| `dashboardTier` | *(empty)* | Run only scenarios tagged with this tier, e.g. `canonical` with `smoke` |
| `dashboardBackend` | `legacy` | `exact` runs the exact pathfinder (unprofiled); the bundle gets an `-exact` suffix and the title an `Exact` suffix |
| `dashboardBundle` | auto-derived from the suite name + profile flag | Bundle directory name |
| `dashboardTitle` | auto-derived from the suite name | Title shown in the UI |
| `dashboardSubtitle` | *(empty)* | Subtitle shown in the UI |
| `dashboardProfile` | auto (on up to 200 scenarios) | Enable profiling (heatmaps, phase timings) |

The bundle name and title are auto-derived when not overridden. For example:
- suite `clue-locations-full`, profiling on → bundle `clue-locations-full-profiled`, title `Clue Locations Full Profiled`
- suite `routes`, profiling off → bundle `routes`, title `Routes`

To build without profiling (faster, no heatmaps):

```bash
./gradlew dashboard -PdashboardProfile=false -PdashboardSuite=clue-locations-full
```

### `captureExpectedLengths`

Like `dashboard` but writes actual path lengths of reached scenarios into
`src/test/resources/scenarios/expected-lengths/<suite>.json`. Use this to bootstrap or refresh
expected lengths after changing pathfinding behavior.

```bash
./gradlew captureExpectedLengths -PdashboardSuite=routes
```

## Profiler

### Architecture

The profiler is a test-only pathfinder that mirrors the production search loop with `System.nanoTime()`
instrumentation at each phase boundary.

Key files:

- `src/test/java/shortestpath/pathfinder/ProfilingPathfinder.java` — mirrors `Pathfinder.java`'s loop, each phase wrapped in nanoTime calls
- `src/test/java/shortestpath/pathfinder/PathfinderProfile.java` — data class collecting all profiling measurements
- `src/test/resources/reachability-dashboard/profiler.js` — frontend rendering (bar charts, time-series, heatmap overlay)

### What It Measures

**Top-level phases** (accumulated nanos per search loop iteration):
- `queueSelection` — dequeuing the next node from the priority queue
- `addNeighbors` — expanding tile and transport neighbors
- `targetCheck` — checking if the current node is a target
- `wildernessCheck` — wilderness level boundary handling
- `cutoffCheck` — cost cutoff evaluation
- `bookkeeping` — iteration counter updates and sampling

**Sub-phases within addNeighbors**:
- `bankCheck` — checking bank proximity for state transitions
- `transportLookup` — looking up transports at the current position
- `collisionCheck` — collision map queries
- `walkableTile` — processing walkable tile neighbors
- `blockedTileTransport` — checking transports on blocked tiles
- `abstractNode` — abstract node expansion

**Counters**:
- `tileNeighborsAdded`, `transportNeighborsAdded` — neighbor counts
- `visitedSkipped` — nodes skipped because already visited
- `transportEvaluations` — total transport evaluations attempted
- `blockedTileTransportChecks` — blocked-tile transport lookups
- `bankTransitions`, `wildernessLevelChanges` — state change counts
- `peakBoundarySize`, `peakPendingSize` — high-water marks for queue sizes

**Time series** (sampled every 2000 iterations):
- boundary queue size, pending queue size, current cost, elapsed nanos

**Heatmap**:
- sparse map of `packedPosition → visitCount` for every tile visited during search

### Keeping the Profiler in Sync

`PathfinderTest.profilingDoesNotAffectResults` verifies that `ProfilingPathfinder` produces identical
results to `Pathfinder` across multiple route types. It checks:

- path equality (every step's position and bank-visited flag)
- result metadata (reached, terminationReason, nodesChecked, transportsChecked)
- profiling data validity (phase nanos > 0, sub-phase nanos > 0, counter consistency, heatmap non-empty)

When modifying `Pathfinder.java`'s search loop, run this test to confirm the profiler still mirrors it.

## How To Choose A Suite

| Situation | Suite / task |
|---|---|
| A new regression or an upstream issue | add a scenario ([scenarios.md](scenarios.md#common-changes)), run its suite with `-PdashboardFilter` |
| Broad sweep after changing core search behavior | `clue-locations-full` or `canonical`, profiling off |
| Legacy vs exact | the same suite with `-PdashboardBackend=exact` |
| Performance analysis (where time is spent, queue sizes) | any suite with `dashboardProfile=true` |
| Bootstrap or refresh expected path lengths | `captureExpectedLengths` |

## Suggested Workflow

**For a performance investigation:**

1. Run `./gradlew dashboard` (profiling on by default).
2. Inspect phase timings, sub-phase breakdowns, and heatmaps in the profiler tab.

For profiling after a pathfinder change:

1. Run profiled bundles for the datasets you care about.
2. Compare heatmaps and phase breakdowns against the pre-change baselines.
3. Use `scripts/analyze_heatmap_counts.py` to get distribution statistics.

## Serving The Site

Open the generated site through a local webserver, not `file://`, because the frontend loads bundle JSON dynamically.

```bash
python -m http.server --directory build/reports/pathfinder-dashboard 8000
```

Then open `http://localhost:8000`.

## Scripts

Helper scripts in `scripts/`:

| Script | Purpose |
|---|---|
| `analyze_heatmap_counts.py` | Analyze tile visit count distributions from heatmap JSON files in a bundle |
| `check_tsv.py` | Validate TSV transport files for format errors |
| `tsv-lint.sh` | Lint all transport TSV files |
| `compute_changed_coordinates.sh` | Diff coordinate changes between branches |
| `diff_coordinate_json.py` | Compare coordinate JSON files |
| `dump_transport_coordinates.py` | Extract transport coordinates from TSV files |
| `gen_clue_csv.py` | Generate the clue locations CSV from source data |

## Implementation Split

The implementation is split into:

1. `PathfinderDashboardReportWriter` — builds the report payload (route results, profiler data, metadata)
2. `DashboardBundlePublisher` — publishes a named bundle into the shared site root and updates `bundles/index.json`
3. `PathfinderDashboardAssetWriter` — writes the shared frontend assets (`index.html`, `app.js`, `profiler.js`, `styles.css`)
