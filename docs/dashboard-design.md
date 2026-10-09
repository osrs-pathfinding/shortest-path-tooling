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
| `src/test/java/shortestpath/profiles/` | `Profiles`: every named profile (canonical accounts and dashboard presets) |
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

## Scenario Suites

A scenario is one route to plan: start and target, the **profile** it starts from, the account and
plugin-setting **overrides** on top of it, and what counts as correct. Profiles and overrides are
always Java; a suite whose rows all use a named profile unchanged may be data.

| Suite | Kind | Contents |
|---|---|---|
| `routes` | Java (`RouteScenarios`) | General-purpose routes: walks, teleports, various transport types |
| `unit-tests` | Java (`UnitTestScenarios`) | Regression cases for specific logic (bank branching, gating, wilderness, spells) |
| `routing-issues` | Java (`RoutingIssueScenarios`) | Regressions for upstream routing issues; the category names the issue |
| `collision-map-issues` | Java (`CollisionMapIssueScenarios`) | Regressions for upstream collision-map issues |
| `f2p-routes` | Java (`F2pRouteScenarios`) | Free-to-play routes |
| `seasonal-briefcase-routes` | Java (`SeasonalBriefcaseScenarios`) | Demonic Pacts League routes, including the briefcase |
| `quetzal-whistle-routes` | Java (`QuetzalWhistleScenarios`) | Quetzal whistle and primo-quetzal routes |
| `clue-locations-full` | data (`scenarios/clue-locations-full.csv`) | Full clue-step reachability corpus |
| `canonical-smoke`, `canonical-standard`, `canonical` | the canonical corpus (`corpus/corpus/routes-v1.json`) | Each tier's routes × `early`/`mid`/`end`/`maxed`, named `<route id>/<profile>` like the benchmark cases; a profile in the route's `negativeProfiles` must not reach it |

`Suites` registers them; `./gradlew -q scenarioSuites` lists them. A suite may have
`src/test/resources/scenarios/expected-lengths/<suite>.json`, scenario name → exact path length,
which `captureExpectedLengths` rewrites.

Every suite runs the same ways: on the dashboard (`-PdashboardSuite=`, narrowed with
`-PdashboardFilter=`), and one scenario at a time with the route CLI:

```bash
./gradlew route -ProuteSuite=routing-issues -ProuteScenario="#140) usable"   # name or unique part of it
./gradlew route -ProuteSuite=canonical -ProuteScenario=gps-natural-0012/maxed -ProuteArgs="--algorithm exact --json"
./gradlew route -ProuteSuite=canonical-smoke -ProuteArgs=--list
```

### Java scenarios

```java
static void define(Suite suite) {
    suite.scenario("Digsite gate (#139) kudos 153+ crosses gate", "routing-issue-139")
        .from(3293, 3428, 0).to(3350, 3415, 0)
        .profile(UNIT_TEST)
        .account(a -> a.varbit(VarbitID.VM_KUDOS, 153))
        .settings(s -> s.setBypassVarbitChecks(false))
        .minimumLength(40);

    suite.scenario("Civitas → Catherby (Kandarin pick)", "briefcase")
        .from(1735, 3093, 0).to(2807, 3442, 0)
        .profile(SEASONAL)
        .account(leagueAreas(LeagueRegion.ASGARNIA, LeagueRegion.KANDARIN))
        .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1));
}
```

- `.profile(...)` — a `Profiles` constant (see [Profiles](#profiles)).
- `.account(a -> ...)` — `Account.Builder` overrides: `varbit`, `varplayer`, `inventory`, `equipment`,
  `bank`, `level(Skill, n)`, `quest(Quest, QuestState)`, `world`, `location`, `nowMinutes`. Item
  quantities of the same id add up. Use the RuneLite `gameval` constants (`ItemID`, `VarbitID`,
  `VarPlayerID`); `varbits` are bit-packed slices of varplayer state, and e.g. quest points are varp
  `VarPlayerID.QP` while varp `139` is `LEGENDSQUEST` progress — a common mix-up.
- `Overrides` names the repeated ones: `.account(leagueAreas(...))` (league area picks by
  `LeagueRegion`), `.account(eliteDiaries())`, `.settings(bankTeleports())`. `.account` and
  `.settings` may be called several times; they apply in order after the profile.
- `.settings(s -> ...)` — `PluginSettings` setters, the scenario-side twin of every
  `ShortestPathConfig` item (`ConfigParityTest` keeps them in sync).
- `.minimumLength(n)` — checked when the suite has no exact expected length for the scenario.
- `.expectUnreachable()` — an intentional-failure scenario: it passes only when no path is found.

Each scenario is one `suite.scenario(...)...;` statement. Names must be unique within a suite and comma-free; reports, expected lengths and the issue store
refer to scenarios by name.

#### Variable overrides and the bypass flags

`varbit` and `varplayer` feed two *different* client reads — `getVarbitValue` vs `getVarpValue` — and
they interact with transport gating in a way that is easy to miss:

- Transport TSVs under `transports/` carry separate `Varbits` and `VarPlayers` requirement columns,
  evaluated by `varbitChecks`/`varPlayerChecks` in `PathfinderConfig`.
- The dashboard presets **bypass both checks by default** (`bypassVarbitChecks`/`bypassVarPlayerChecks`
  are `true` in `PluginSettings`), so an overridden id only gates a transport when the
  scenario also calls `s.setBypassVarbitChecks(false)` or `s.setBypassVarPlayerChecks(false)`. The
  canonical profiles never bypass.
- Example: the digsite scenarios in `RoutingIssueScenarios` pair `.varbit(VarbitID.VM_KUDOS, 153)`
  / `0` with `setBypassVarbitChecks(false)` so the kudos varbit actually opens/closes the gate.
- Asymmetry: `destinations/game_features/bank.tsv` requirement columns read the client directly and
  are **never bypassed** — account overrides always feed them.
- Turning a bypass off evaluates *every* requirement of that kind against the account — unset ids
  read `0`, so unrelated transports can gate off too. Re-capture expected lengths afterwards.

#### Quests

`.quest(Quest, QuestState)` overrides one quest. The dashboard presets default every other quest to
`FINISHED` (the canonical profiles default to `NOT_STARTED`), and only `FINISHED` satisfies a quest
requirement. Quest states gate more than individual transports: `THE_GRAND_TREE` not finished
disables the whole gnome-glider transport type, `BONE_VOYAGE` gates all magic mushtrees, and
`TREE_GNOME_VILLAGE` gates all spirit trees — plus quest requirements on `bank.tsv` destinations.

### Profiles

`shortestpath.profiles.Profiles` is the only place accounts and settings start from:

| Profile | Account | Settings |
|---|---|---|
| `early`, `mid`, `end`, `maxed` | the canonical accounts, built from the same Java (`accounts/`) that generates `corpus/accounts/` | every transport the route allows, neutral costs, no bypass |
| `ALL` | dashboard baseline | all teleportation items |
| `NONE` | dashboard baseline | no teleportation items |
| `BANK` | dashboard baseline, a bank with every item, Lumbridge elite diary not done | banked teleport items, bank path, no minigame teleports |
| `BANK_PERM` | as `BANK` | non-consumable banked teleport items |
| `INVENTORY` | dashboard baseline | carried teleport items only |
| `INVENTORY_NON_CONSUMABLE` | dashboard baseline | non-consumable carried items only |
| `SEASONAL` | dashboard baseline on a seasonal world | carried items, seasonal transports, avoids wilderness |
| `UNIT_TEST` | dashboard baseline, Lumbridge elite diary not done | every toggle off, no items, 30-tick cutoff — the plugin's `PathfinderTest` baseline |

The dashboard baseline is every skill 99 (total 2277), every quest finished, fairy rings unlocked,
the player on the scenario's start tile, and nothing carried or banked. Preset names are
case-insensitive. The client reports `getRealSkillLevel` as 0 for every profile (as the Mockito
stubs it replaced did), so the plugin computes combat level 3.

### Data-only scenario CSVs

For a long list of routes that all use one profile unchanged (`clue-locations-full`), or an ad-hoc
list run with `-PdashboardDataset=<file>`:

```
name,category,start_x,start_y,start_plane,x,y,plane,profile[,minimum_length][,expect_reachable][,source_file][,source_line]
```

Rows split on bare commas (no quoting); blank and `#` lines are skipped; an empty start means the
default start (the Grand Exchange). There are no override columns: unknown columns are rejected, and
`scripts/validate_data.py scenario-data` checks the committed files.

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
| `dashboardSuite` | `routes` | The suite to run (see `Suites`) |
| `dashboardDataset` | *(empty)* | A data-only scenario CSV file to run instead of a suite |
| `dashboardFilter` | *(empty)* | Run only the scenarios whose name or category contains it (ignoring case), e.g. `issue-140` |
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
| "This destination should be reachable" | Add a scenario to `RouteScenarios` or `UnitTestScenarios`, run `dashboard` |
| Route quality, bank usage, transport choice, exact config control | `UnitTestScenarios` with the `UNIT_TEST` profile and `.settings(...)` |
| An upstream routing issue | `RoutingIssueScenarios`, category `<domain>-issue-<N>` |
| Broad sweep after changing core search behavior | `clue-locations-full`, profiling off |
| Quetzal whistle transport regressions | `quetzal-whistle-routes` |
| Performance analysis (where time is spent, queue sizes) | Any suite with `dashboardProfile=true` |
| Bootstrap or refresh expected path lengths | `captureExpectedLengths` task |

## Suggested Workflow

**For a reported unreachable destination:**

1. Add a scenario to `RouteScenarios` with the start and target coordinates and a profile.
2. Run `./gradlew dashboard -PdashboardSuite=routes`.
3. Serve the output and inspect the route in the dashboard.
4. To verify its length on every run, run `captureExpectedLengths -PdashboardSuite=routes`.

**For a routing regression (bank logic, transport gating, etc.):**

1. Add a scenario to `UnitTestScenarios` with `.profile(UNIT_TEST)` and precise `.settings(...)`.
2. Capture its expected length (and optionally set `.minimumLength(n)`).
3. Run `./gradlew dashboard -PdashboardSuite=unit-tests`.
4. Inspect the route and confirm the path length assertion matches the intended behavior.

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
