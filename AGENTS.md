# AGENTS.md

Instructions for agents using this repo to develop the
[`shortest-path`](https://github.com/Skretzo/shortest-path) RuneLite plugin
(OSRS pathfinding).

## What this is

Plugin work happens on branches in the `shortest-path/` submodule (work on a
feature branch, PRs go to `upstream` = Skretzo/shortest-path). This repo wraps
the plugin via a Gradle composite build and adds the pathfinder dashboard
generator, OSRS cache dumpers, and Python maintenance/validation scripts
around it.

## Layout

| Path | Contents |
|------|----------|
| `shortest-path/` | Git submodule (pinned commit). Plugin sources + data in `src/main/resources/` (`collision-map.zip`, `destinations/`, `transports/`, `leagues/`). |
| `accounts/` | Gradle subproject: `Account` (an account as a player describes it: levels, quests, diaries, unlocks, items, house), `AccountCompiler` (the only place facts become the varbits/varplayers in a `ClientState`), `HeadlessClient` (a RuneLite `Client` answering from a `ClientState`), and the canonical profiles (`canonical/CanonicalAccounts`). |
| `routing/` | Gradle subproject (main sources): `shortestpath.profiles` (profiles, `PluginSettings`, `Setup`, `AccountPathfinderConfig`), `shortestpath.pathfinder.PluginResources` (world data loaded once per JVM), and `shortestpath.routeapi` (the web API: `AccountJson`, `PlannerSettings` (plugin settings as JSON, from the plugin's own `@ConfigItem`s), `Catalog`, `RoutePlans`). Tooling tests and the route service both build on it. |
| `web/` | The public route planner: React + TypeScript + Vite frontend (npm project), deploy and infra files. See `web/README.md`. |
| `web/service/` | Gradle subproject `:service`: the route service's HTTP layer (Javalin) on top of `routing/`. |
| `corpus/` | Canonical routes and the JSON schemas (route API, account) the service and frontend share (see `corpus/README.md`). |
| `src/test/java/shortestpath/` | The remaining tooling Java lives under *test* sources: `scenarios/` (scenario suites), `dashboard/` (site generator), `benchmark/` (benchmark adapter), `route/` (`route` CLI); model in `docs/scenarios.md`, `dump/` (cache dumpers), `pathfinder/` (profiling). |
| `src/test/resources/` | Dashboard web assets, scenario data + expected lengths under `scenarios/`, region TSVs. |
| `gradle/` | Task definitions: `dashboards.gradle`, `cache-dumpers.gradle`. |
| `scripts/` | Python orchestration (see Scripts map below). |
| `tests/` | pytest suite for the Python scripts (`fixtures/` for test data). |
| `collision-map-update/` | Standalone upstream pipeline pieces: `download-latest-cache.sh`, `CollisionMapDumper.java`, `build.gradle.kts.patch`. |
| `docs/` | `maintenance.md` runbook, `dashboard-design.md`, performance analysis. |

## Toolchain

- Java 11 (CI: temurin 11). Gradle via `./gradlew` wrapper only — never a
  system `gradle`. One build: the root project (tooling tests), `:accounts`,
  `:routing` and `:service`, plus the plugin as an included build.
- Node 24 + npm for `web/` only.
- Python 3 + pytest for `tests/` and `scripts/`.
- RuneLite deps resolve as `runeLiteVersion` from `gradle.properties`
  (`latest.release`) — upstream RuneLite releases can break compilation
  without any local change. `-PruneLiteVersion=<v>` pins one build.
- `cache/` and `keys.json` are gitignored; the cache dumpers need a local
  OSRS cache. Get one with `python3 scripts/maintenance.py cache`.

## Common commands

```bash
./gradlew compileTestJava          # compile gate (what CI runs)
./gradlew test                     # JUnit suite incl. dashboard scenarios (8g heap configured)
./gradlew dashboard                # build dashboard → build/reports/pathfinder-dashboard
python -m http.server --directory build/reports/pathfinder-dashboard 8000
python3 -m pip install -r requirements.txt   # pytest, pyyaml, tqdm (homebrew python needs --break-system-packages or a venv)
python3 -m pytest tests/           # Python script tests (no network, all mocked)
python3 scripts/maintenance.py verify    # full gate: compile → submodule test → dashboard sweep → edge diff
python3 scripts/maintenance.py validate  # data validation: hard gate + advisory tiers
./gradlew :routing:test :service:test    # routing module + route service
./gradlew :service:run                   # route service on :8080 (PORT to change)
(cd web && npm install && npm run dev)   # planner on :5173, proxies /api to the service
```

Dashboard options are `-P` properties: `dashboardSuite` (default
`routes`; `./gradlew -q scenarioIndex` lists every suite; `-PdashboardTier=smoke`
selects a canonical tier),
`dashboardFilter` (only scenarios whose name or category contains it),
`dashboardDataset` (a route JSON file to run instead),
`dashboardBundle`, `dashboardTitle`,
`dashboardSubtitle`, `dashboardProfile` (default: **auto** — profiling runs
only for datasets of ≤ 200 scenarios; pass `true`/`false` to force),
`dashboardHeatmap` (default true; only applies when profiling is on),
`dashboardSeasonal`, `dashboardF2p`, `dashboardThreads` (parallel scenario
workers; default `availableProcessors()-3`), `dashboardBackend` (`legacy`
default or `exact`; exact runs are unprofiled). Other tasks: `captureExpectedLengths` (writes actual lengths
into `src/test/resources/scenarios/expected-lengths/<suite>.json`) and the cache dumpers/probes in `gradle/cache-dumpers.gradle`
(`bankTileDump`, `sailingAmenityVarbitDump`, `leagueRegionDump`,
`f2pRegionDump`, `leagueIdProbe`, `transportAnchorDrift`, the `briefcase*`
scans, …). Dumpers take `-P<name>CacheDir=` and `-P<name>XteaPath=` props.

Profiling is expensive — check the `[profiled|unprofiled]` tag on the
"Running N scenario(s)" banner to confirm what a run is doing:

- **Large datasets auto-run unprofiled** (>200 scenarios, e.g.
  `clue-locations-full` — ~1 min vs tens of minutes). Pass
  `-PdashboardProfile=true` only when you specifically need profiled data
  for a big dataset.
- **Small datasets stay profiled by default** (debugging keeps its
  counters); force `-PdashboardProfile=false` for quick A/B determinism
  runs on any dataset.
- **`dashboardHeatmap=false`** drops the per-tile visit map — the dominant
  profiling allocation — while keeping the profiler's counters, phase
  timings, and time series. Use it when you need profiling on a dataset
  where the heatmap itself isn't the point.
- Produce profiled bundles for the dashboard site explicitly with
  `-PdashboardProfile=true` — don't rely on the auto default.

`verify` streams tier progress (per-dataset header + tqdm bar driven by the
`[i/N]` scenario heartbeats in `DashboardTest`). Expect ~15–30 min: the
dashboard tier launches one cold JVM per committed CSV (8+ datasets, the
largest ~860 scenarios). `--skip-compile/--skip-lint/--skip-dashboard/
--skip-diff` narrow the gate for fast iteration.

`VarAccessProbeTest` scans cache clientscripts for var/operand access —
`-Dtile.probe.vars=<ids>` (scripts reading/writing those varps/varbits),
`-Dtile.probe.scripts=<ids>` (full disassembly), `-Dtile.probe.strings=<text>`
(scripts containing a string operand), `-Dtile.probe.iops=<ints>` (raw int
operands, e.g. packed coords). Opcodes: GET_VARP=1, SET_VARP=2,
GET_VARBIT=25, SET_VARBIT=27; branch ops are `Opcodes.java` IF_ICMP*.
Varbit/varplayer names are in
`build/runelite-work/runelite/runelite-api/.../gameval/{VarbitID,VarPlayerID}.java`.
This is how requirement candidates get confirmed without an account — e.g.
the minigame-teleport eligibility script exposed MTA (varbit 1499) and the
Keldagrim "visited" gate (varbit 571 ≥ 5).

TSV requirement grammar: `Quests` means FINISHED only — partial quest
progress must be expressed via the quest *varbit* in the `Varbits` column
(`id>4`, `id=1`, `id<3`, `id&mask`; `VarPlayers` adds `@` cooldown minutes).
If a requirement is not exposed in any client var, do not drop the data —
it is gated via a config-option mechanism instead.

Dev client: `./gradlew -p shortest-path run` launches RuneLite with the
plugin bundled; on macOS it needs
`JDK_JAVA_OPTIONS="--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED"`
(else `OSXFullScreenAdapter` crashes at startup). In-game dev console
commands `::getvarp <id>` / `::getvarb <id>` print values as chat lines that
land in the Gradle log — usable when the Var Inspector panel can't copy.

## Scripts map

| Script | Purpose |
|--------|---------|
| `scripts/maintenance.py` | Single entry point for upkeep. Subcommands: `cache`, `collision-map`, `regions`, `bank`, `seasonal`, `refresh` (full chain), `probes`, `verify`, `validate`. See `docs/maintenance.md`. |
| `scripts/validate_data.py` | Deterministic checks on committed plugin data (TSV structure, zip structure, walkability, bbox, freshness). |
| `scripts/verify_seasonal_regions.py` | Verify seasonal transport region assignments vs wiki ground truth. |
| `scripts/rebuild_bank_tsv.py` | Merge `bankTileDump` output into submodule `destinations/game_features/bank.tsv`. |
| `scripts/compare_collision_maps.py` | Edge-flag diff between two `collision-map.zip` artifacts. |
| `scripts/collision_zip.py` | Shared reader library for `collision-map.zip` (imported by other scripts). |
| `scripts/analyse_dashboard_runs.py` | Compare two sets of dashboard `report.json` files, per-route deltas. |
| `scripts/import_issues.py` | Sync upstream Skretzo/shortest-path issues/PRs into local datasets. |

### Submodule scripts (`shortest-path/scripts/`)

These live in the plugin repo — run them there, and remember changes to them
belong to the submodule's branch/PR flow, not this repo.

| Script | Purpose |
|--------|---------|
| `check_tsv.py` | Validate transport/destination TSVs: header shape, recognized columns, column counts, whitespace, coordinate format. |
| `tsv-lint.sh` | Shell linter — every TSV line must have the same column count. `./tsv-lint.sh [dir]` (defaults to `src/main/resources`). |
| `dump_transport_coordinates.py` | Dump all transport/destination coordinates as JSON for the coordinate-preview tooling. |
| `diff_coordinate_json.py` | Diff two coordinate dumps, keeping only new or semantically changed tiles. |
| `compute_changed_coordinates.sh` | Orchestrates the merge-base vs PR-head coordinate dumps for the preview workflow. |
| `renumber_config_positions.py` | Fix duplicate `@ConfigItem`/`@ConfigSection` `position` values in a RuneLite config file. |

## Submodule rules (important)

- Remotes inside `shortest-path/`: `upstream` = Skretzo/shortest-path,
  `origin` = your fork.
- **Never** `git submodule update --remote` — it leaves a detached HEAD,
  which the data-writing subcommands refuse. Update via
  `python3 scripts/maintenance.py collision-map [--commit]` instead.
- Regenerated data lands on an `origin` (fork) feature branch and goes
  upstream by PR. Data-writing subcommands refuse `master`, detached HEAD,
  and dirty worktrees — keep the submodule clean before running them.
- Plugin test helpers are shared, not copied: `build.gradle` pulls
  `TestShortestPathConfig.java` straight from `shortest-path/src/test/java`
  into `compileTestJava` (`ConfigParityTest` compares defaults with it).
  Main-source code (`routing/`, the service) never depends on plugin test sources.
- Run submodule tests with `./gradlew -p shortest-path test`.

## Upstream PR workflow

The upstream maintainer reviews at a deliberate pace and has flagged
the submission pace as rushed — requirement-satisfaction logic is
spread across several places, so subtle changes are expensive to review.

- Changes to requirement satisfaction, eligibility, or
  `usable()`/`useTransport` semantics must include adversarial tests:
  null/empty requirement branches, OR-vs-AND combinations, and every
  teleportation-item mode — not just the happy path.
- Transport/quest availability varbits are usually recoverable by
  decompiling cache clientscripts (`VarAccessProbeTest`,
  `-Dtile.probe.*` flags); cross-check with the Quest Helper plugin.
  If a condition genuinely isn't var-exposed, use the config-option
  mechanism rather than dropping the data.

## Verify-command rules

Checks that cannot fail get written every cycle — audit before shipping:

- A verify/check command must be able to fail. No `; cmd` after an `&&`
  chain (the trailing command discards the chain's status). When piping,
  assert the source exit code — capture `out=$(cmd 2>&1); rc=$?` and
  require both `rc -eq 0` and no error lines in `$out`. A crashed
  checker must never read as green.
- Greps must target identifiers the change introduces (new constants,
  enum values, tokens), never pre-existing symbols — a presence check
  that passes on the untouched tree is not a check.
- `gh` invocations must be real: `gh pr list --head` takes a bare
  branch name (filter fork owner via `headRepositoryOwner.login`);
  `gh pr view` has no `--head` flag.
- Every file an action step creates or edits must be declared in that
  step's file list.
- After adding `@ConfigItem`s, `position` values must stay unique:
  `grep -o 'position = [0-9]*' ShortestPathConfig.java | sort | uniq -d`
  → empty. `renumber_config_positions.py` fixes collisions.

## Benchmark corpus

`corpus/` holds the canonical routes and the web API schemas (formerly the
`shortest-path-corpus` repo, history imported); see `corpus/README.md`.

- `corpus/corpus/routes-v1.json` — canonical routes (`id`, `start`/`target`,
  `allowTransports`, `tiers`, hand-curated `negativeProfiles`). Data, edit directly;
  `CanonicalCorpusTest` checks it.
- The canonical accounts are Java only: `accounts/…/canonical/CanonicalAccounts.java`.

Query one route (the `route` CLI; its argument forms and JSON fields are
agent-facing, keep them stable):

```bash
./gradlew route -ProuteArgs="maxed 2411 4434 0 2995 3114 0 --json"   # any profile, incl. presets
./gradlew route -ProuteArgs="gps-natural-0012 maxed --algorithm exact"
./gradlew route -ProuteSuite=routing-issues -ProuteScenario="#140) usable"  # any suite scenario
./gradlew route -ProuteSuite=canonical -ProuteArgs=--list
```

## OSRS wiki lookups

When verifying game data against the wiki (e.g. seasonal transport region
assignments, transport requirements), fetch the machine-readable wikitext
instead of rendered HTML:

- Append `?action=raw` to the page URL, or
- call the API directly:
  `https://oldschool.runescape.wiki/api.php?action=parse&page=<page>&prop=wikitext&format=json`

## Gotchas

- Never hand-edit or `sed` `keys.json` — `maintenance.py cache` patches it
  idempotently (naive sed corrupts `"keys"` → `"keyss"` on re-run).
- Checkstyle is applied but disabled; don't "fix" by enabling it.
- All dashboard/dump Gradle tasks are `Test` tasks with `ignoreFailures =
  true` and `doNotTrackState` — they always re-run and don't fail the build
  on scenario failures. Inspect output/`build/reports/` for real status.
