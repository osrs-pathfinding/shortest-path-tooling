# shortest-path-tooling

Developer dashboards, shared headless routing and OSRS cache dumpers for the [shortest-path](https://github.com/Skretzo/shortest-path) RuneLite plugin.

This repo carries `shortest-path` as a git submodule and uses a Gradle [composite build](https://docs.gradle.org/current/userguide/composite_builds.html) to consume the plugin's sources — so the dashboard and cache-dumper code never pollutes plugin PRs.

New here? [docs/system-overview.md](docs/system-overview.md) explains how the pieces fit together and the workflows they support.

## Looking for the plugin?

This repository contains **developer tooling** — it is not the plugin itself.

- **To use the plugin:** install *Shortest Path* from the RuneLite Plugin
  Hub. The [plugin repo](https://github.com/Skretzo/shortest-path) and its
  [wiki](https://github.com/Skretzo/shortest-path/wiki) cover features,
  examples, and options. Bugs and feature requests go to the plugin's
  [issue tracker](https://github.com/Skretzo/shortest-path/issues), and
  there's a [Discord server](https://discord.gg/uX47xg8u3M) for help and
  discussion.
- **To browse pathfinding results:** the published dashboard is at
  `https://skretzo.github.io/shortest-path/` — no setup needed.
- **To hack on the plugin or its data:** you're in the right place —
  keep reading.

## Quick start

```bash
git clone --recurse-submodules https://github.com/osrs-pathfinding/shortest-path-tooling.git
cd shortest-path-tooling
./gradlew dashboard
python -m http.server --directory build/reports/pathfinder-dashboard 8000
```

Then open `http://localhost:8000`.

The published dashboard is also available on GitHub Pages:

`https://skretzo.github.io/shortest-path/`

## Shared headless routing

The `:routing` Java library exposes the profiles, plugin settings and account-backed pathfinder
configuration used by the dashboard, benchmarks and route CLI. A headless service can use the same
code through a Gradle composite build:

```groovy
includeBuild('shortest-path-tooling') {
    dependencySubstitution {
        substitute module('tooling:routing') using project(':routing')
    }
}
```

The consumer declares `implementation 'tooling:routing'`. The library exposes `:accounts`, the
pinned plugin and the RuneLite client dependency needed for headless execution. Compile an account
with `CompiledAccount.of(account, settings)`; this captures its state on the calling thread, so run
its pathfinder on that same thread. The runtime library uses plugin main sources only. Corpus JSON
loading, scenario runners and reporting remain test-only tooling.

Run `./gradlew :routing:test` to check the shared profiles and account setup.

Account unlocks with `Unlock.Kind.SETTING` declare facts the game does not report, such as a stored
canoe axe. `CompiledAccount` applies them to plugin settings; `AccountCompiler` only writes game
variables. Canonical benchmark accounts retain their existing reported unlocks and settings.

## Using a separate shortest-path checkout

Developers working on the plugin and tooling side by side can point the tooling
build at an external checkout:

```bash
./gradlew -PshortestPathDir=../shortest-path test
./gradlew -PshortestPathDir=../shortest-path dashboard
```

Paths are resolved relative to `shortest-path-tooling`. The `shortestPathDir`
property controls both the Gradle composite build for
`shortestpath:shortest-path` and the directly compiled plugin test helpers.
The default remains the `./shortest-path` Git submodule.

## Available tasks

| Task | Description |
|------|-------------|
| `./gradlew dashboard` | Build the pathfinder dashboard from the default `routes` suite and the `sailing-routes` suite |
| `./gradlew dashboard -PdashboardSuite=clue-locations-full` | Build dashboard from a specific scenario suite (only that one) |
| `./gradlew pagesSite` | Build every bundle of the site the plugin publishes to GitHub Pages (the plugin's Dashboard Pages workflow runs it; change the published bundles in `gradle/dashboards.gradle`) |
| `./gradlew sailingDashboard` | Build only the sailing routes, with the experimental sailing search drawn next to the normal path (needs a plugin checkout with the sailing search) |
| `./gradlew captureExpectedLengths -PdashboardSuite=routes` | Write actual path lengths into the suite's `scenarios/src/main/resources/scenarios/expected-lengths/<suite>.json` |
| `./gradlew sailingBenchmark` | Time the experimental sailing search against the existing search on routes at sea (needs a plugin checkout with the sailing search) |
| `./gradlew bankTileDump -PbankTileCacheDir=<dir> -PbankTileXteaPath=<keys.json>` | Dump bank-object placements from an OSRS cache to TSV |
| `./gradlew sailingAmenityVarbitDump -PsailingAmenityCacheDir=<dir> -PsailingAmenityXteaPath=<keys.json>` | Dump Sailing island amenity varbits from an OSRS cache |
| `./gradlew routingCuts -PkahipNodeSeparator=<path>` | Generate the A* pathfinder's `routing-cuts.bin` with KaHIP; the cuts are an offline input in the plugin's `src/test/resources` (see below) |
| `./gradlew routingCutsReport` | Report how many cuts still apply to the current collision map (`-ProutingCutsFile=<path>`, default: the plugin's committed cuts) |
| `./gradlew routingComponents` | Regenerate the plugin's `routing-components.bin` from its collision map and cuts (see below) |
| `./gradlew routingStaticDigest` | Print a SHA-256 over every field of the plugin's A* routing data; pass `-PshortestPathDir` to compare checkouts |
| `./gradlew benchmark --args="..."` | Run a benchmark manifest's scenarios (used by collisionlab/osrs-benchmarks) |
| `./gradlew route -ProuteArgs="..."` | Query one canonical route and print its selected path |

The route query uses the same profiles and runner as `benchmark`. PROFILE is any
profile (`early`/`mid`/`end`/`maxed` or a dashboard preset such as `UNIT_TEST`), and
`-ProuteSuite=SUITE -ProuteScenario="NAME"` runs one scenario of any suite
(`-ProuteArgs=--list` lists them). It reads the routes in this checkout's `corpus/`; add
`--algorithm astar`, `--json` or `--counters` as needed. Routes can be given by
corpus ID (`--route ID --profile PROFILE`) or by coordinates:

```bash
./gradlew \
  -ProuteArgs='maxed 2411 4434 0 2995 3114 0 --json' \
  route
```

## A* routing components and cuts

The plugin's A* pathfinder ships `routing-components.bin`, a routing component
for every collision-walkable tile, derived from `collision-map.zip` and a small
list of separator cut edges (`routing-cuts.bin`) that splits large walking
areas. The cuts are an offline input in the plugin's `src/test/resources`.
`routingCuts` regenerates them with KaHIP's `node_separator`, using the
plugin's own walking graph:

```bash
./gradlew routingCuts \
  -PshortestPathDir=../shortest-path \
  -PkahipNodeSeparator=$(command -v node_separator) \
  -ProutingCutsOutput=../shortest-path/src/test/resources/routing-cuts.bin
```

`routingComponents` then derives the layer from the collision map and the cuts:

```bash
./gradlew routingComponents \
  -PshortestPathDir=../shortest-path \
  -ProutingCutsFile=../shortest-path/src/test/resources/routing-cuts.bin \
  -ProutingComponentsOutput=../shortest-path/src/main/resources/routing-components.bin
```

KaHIP is not a Gradle dependency. Use `nix-shell -p kahip` or build KaHIP with
CMake (`-DNOMPI=On`) and pass the binary path. The plugin's weekly
`ExtractCollisionMap` workflow runs both tasks after dumping a new collision
map and commits all three files together, and the plugin's
`RoutingComponentsFreshnessTest` fails when the layer is stale. Stale cuts only
slow preparation down; they never make exact routes wrong.

## Maintenance

`scripts/maintenance.py` orchestrates post-update data maintenance —
`cache`, `collision-map`, `regions`, `bank`, `seasonal`, `refresh`,
`probes`, and `verify` subcommands wrap the cache download, Gradle
dumpers, and verification tiers in one entry point. See
[docs/maintenance.md](docs/maintenance.md) for the runbook.

The sailing tools in `src/sailing/java` are only compiled when the plugin checkout (`-PshortestPathDir`) has
the experimental sailing search.

## Dashboard options

All options are passed via `-P`:

| Option | Default | Description |
|--------|---------|-------------|
| `dashboardSuite` | `routes` | Scenario suite to run (`./gradlew -q scenarioIndex` lists them; see [docs/scenarios.md](docs/scenarios.md)) |
| `dashboardFilter` | *(empty)* | Run only scenarios whose name or category contains it |
| `dashboardDataset` | *(empty)* | A route JSON file (the canonical route format, run with `ALL`) to run instead of a suite |
| `dashboardBundle` | derived from the suite name | Bundle name in the output site |
| `dashboardTitle` | derived from the suite name | Title shown in the UI |
| `dashboardSubtitle` | *(empty)* | Subtitle shown in the UI |
| `dashboardProfile` | `true` | Whether to enable the profiler |

For the datasets and when to use each, see [docs/dashboard-design.md](docs/dashboard-design.md).

## Benchmarks

`benchmark` is the integration point for
[collisionlab/osrs-benchmarks](https://github.com/collisionlab/osrs-benchmarks), which runs it nightly
over every scenario of every suite and records the results. It runs the scenarios a manifest names
(by suite and scenario name, with repetitions and optional warm-up scenarios) in one JVM, for the
legacy or A* backend:

```bash
./gradlew benchmark --args="--manifest manifest.json --output build/benchmarks/result.json"
```

Each scenario runs exactly as the route CLI and the dashboard run it, through `ScenarioRunner`, and
is judged by its own expectations (`Observation.check()`: reachability, then the exact or minimum
length); see [docs/scenarios.md](docs/scenarios.md). The manifest format is documented on
`BenchmarkMain.loadPlan`. The scenarios, routes and accounts all come from this checkout, so the
tooling and plugin commits in the result's execution metadata identify everything a case ran with.

CI runs a light benchmark through it (`python3 scripts/benchmark_smoke.py`: three scenarios on each
backend, checked the way osrs-benchmarks reads them). If you change its arguments, manifest format
or output, update osrs-benchmarks to match.

## Keeping up with the plugin

The `shortest-path` submodule is pinned to a specific commit. To update it to the latest plugin master:

```bash
python3 scripts/maintenance.py collision-map
```

This fast-forwards the submodule to `upstream/master`, prints an edge diff
of the new `collision-map.zip` for review, and stages the gitlink bump
with `--commit`. Do not use `git submodule update --remote` — it checks
out a detached HEAD, which the data-writing subcommands refuse.

## Cache dumpers

The cache dumpers require a local OSRS cache. Download one first:

```bash
python3 scripts/maintenance.py cache
```

The `cache` subcommand downloads the cache and patches `keys.json`
idempotently — never `sed` the file by hand (the BSD/GNU `-i` syntax
differs, and `s/key/keys/g` corrupts `"keys"` into `"keyss"` on re-run).

Then run the desired task (see table above).

The `rebuild_bank_tsv.py` script merges the bankTileDump output into `shortest-path/src/main/resources/destinations/game_features/bank.tsv`:

```bash
python3 scripts/rebuild_bank_tsv.py
```

## Related repositories

- [`shortest-path`](https://github.com/Skretzo/shortest-path) — the plugin
  itself (carried here as the `shortest-path/` submodule).
