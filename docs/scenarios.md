# Scenarios, suites and the three runners

Every route this repository plans — a regression test, a canonical benchmark case, a clue
location, an agent's one-off query — is a **scenario**, and every scenario runs through the same
code. This page is the model; the dashboard UI is in [dashboard-design.md](dashboard-design.md).

## Concepts

| Concept | What it is | Code |
|---|---|---|
| **Account** | An account as a player would describe it: levels, quests, diaries, unlocks, items, house, spellbook, clock. `AccountCompiler` turns it into the `ClientState` (varbits, varplayers, items) the plugin reads | `accounts/` → `shortestpath.accounts.Account` |
| **Profile** | A named starting point: an account plus plugin settings. Knows nothing about routes | `shortestpath.profiles.Profiles` |
| **Scenario** | What to run and what counts as correct: a route (start, target, transports allowed) + a profile + account and settings overrides + an expectation. Runs nothing itself | `shortestpath.scenarios.Scenario` |
| **Suite** | A named list of scenarios, some tagged with tiers | `shortestpath.scenarios.Suites` |
| **Runner** | The only code that calls a pathfinder: runs a scenario on the legacy or A* backend (and the A* backend's session modes) and returns an `Observation` | `shortestpath.scenarios.ScenarioRunner` |
| **Observation** | One run's result. `isReached()` and `check()` (reachability, minimum length, exact expected length) are the only definitions of *reached* and *correct* | `shortestpath.scenarios.Observation` |

**Profiles.** The canonical accounts `early`, `mid`, `end`, `maxed` are defined in
`accounts/src/main/java/shortestpath/accounts/canonical/CanonicalAccounts.java`. The presets
`ALL`, `NONE`, `UNIT_TEST`, `SEASONAL` share the harness account (every skill 99, every quest
finished, fairy rings, nothing carried) and differ in settings, plus the Lumbridge elite diary and,
for `SEASONAL`, a seasonal world. `PluginSettings` is the plugin settings object every profile and
scenario uses.

**Expectations.** A scenario is expected reachable or unreachable, and may set a minimum path
length. *Reached* is the engine's verdict, `PathfinderResult.isReached()` (for a target that
cannot be stood on, the plugin routes to its fallback tiles before searching);
`Observation.isReached()` is the only definition. A suite may also keep exact
lengths in `scenarios/src/main/resources/scenarios/expected-lengths/<suite>.json`, which only the dashboard
checks and `captureExpectedLengths` rewrites.

## Suites

A suite is Java or route data. Write a **Java scenario** when the route needs anything beyond a named
profile: an account fact or raw variable, a setting, a minimum length, a sailing speed. Write a
**JSON route** when it is one of many uniform routes run with named profiles as they are (the canonical
corpus, the clue locations, an ad-hoc file for `-PdashboardDataset`); route files have no override
fields.

| Suite | Source | Scenarios |
|---|---|---|
| `routes`, `unit-tests`, `routing-issues`, `collision-map-issues`, `f2p-routes`, `seasonal-briefcase-routes`, `quetzal-whistle-routes`, `sailing-routes` | Java (`*Scenarios.define(Suite)`) | hand-written, with overrides |
| `clue-locations-full` | route data `scenarios/src/main/resources/scenarios/clue-locations.json` × `ALL` | clue locations |
| `canonical` | route data `corpus/corpus/routes-v1.json` × `early`/`mid`/`end`/`maxed` | the canonical corpus, tagged `smoke`/`standard`/`full` |

`./gradlew -q scenarioIndex` prints every suite's scenarios as JSON; scripts read suites only from it.

### Java suites

Anything with an override is Java:

```java
static void define(Suite suite) {
    suite.scenario("Mage arena tele (#140) usable after guardian talk", "routing-issue-140")
        .from(3163, 3485, 0).to(3363, 3295, 0)
        .profile(UNIT_TEST)
        .account(a -> a.varbit(VarbitID.MAGICTRAINING_ENTRA_NOOB, 1))
        .settings(s -> {
            s.setUseTeleportationMinigames(true);
            s.setBypassVarbitChecks(false);
        });
}
```

- `.account(a -> ...)` overrides the profile's `Account.Builder` with facts: `quest`, `diary`,
  `unlock`/`lock`, `spellbook`, `minigameTeleportUsedAt`, `inventory`, `equipment`, `bank`, `level`,
  `world`, `location`, `nowMinutes`. A fact sets every variable it implies, so `a.quest(X, FINISHED)`
  also sets X's progress varbits.
- `varbit`/`varplayer` set raw variables, for what no fact describes (league area picks, a quest
  half done). They are applied last and override the facts. Use RuneLite's `gameval` constants
  (`ItemID`, `VarbitID`, `VarPlayerID`).
- `.settings(s -> ...)` overrides `PluginSettings`.
- `Overrides` names the repeated ones: `.account(leagueAreas(LeagueRegion.ASGARNIA, ...))`,
  `.account(eliteDiaries())`, `.settings(bankTeleports())`. Overrides apply in order after the profile.
- `.expectUnreachable()`, `.minimumLength(n)`.
- `.sailing(speed, boat)` makes a route at sea: the dashboard also runs the experimental sailing search
  at that speed, keeping the boat's hull clear (`sailing-routes`; see [dashboard-design.md](dashboard-design.md#sailing)).
- The presets bypass varbit and varplayer *transport* requirements unless a scenario calls
  `s.setBypassVarbitChecks(false)` / `s.setBypassVarPlayerChecks(false)`; the canonical profiles never
  bypass. Bank destination requirements are never bypassed.

Names must be unique within a suite; reports, expected lengths and the issue store use them.

### Variable overrides and the bypass flags

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

### Quests

`.quest(Quest, QuestState)` overrides one quest. The dashboard presets default every other quest to
`FINISHED` (the canonical profiles default to `NOT_STARTED`), and only `FINISHED` satisfies a quest
requirement. Quest states gate more than individual transports: `THE_GRAND_TREE` not finished
disables the whole gnome-glider transport type, `BONE_VOYAGE` gates all magic mushtrees, and
`TREE_GNOME_VILLAGE` gates all spirit trees — plus quest requirements on `bank.tsv` destinations.

### Route data

A route file is a JSON array in the canonical corpus route format:

```json
{"id": "gps-natural-0012", "name": "Cauldron of Thunder → Ice Queen's Lair",
 "start": [2895, 9833, 0], "target": [2861, 9947, 0], "allowTransports": true,
 "tiers": ["smoke", "standard", "full"], "negativeProfiles": ["early"]}
```

Optional fields: `start` (absent: the Grand Exchange), `allowTransports` (default true), `tiers`,
`negativeProfiles` (profiles that must not reach it), `profiles` (instead of the suite's),
`category` (default: the id without its number), and the provenance strings `startName`,
`targetName`, `startSource`, `targetSource`. Unknown fields are rejected — overrides belong in Java.
Each route runs with each profile as scenario `<id>/<profile>`.

## Running scenarios

The three runners select scenarios the same way and run them through `ScenarioRunner`.

**Route CLI** — one scenario, printed (human-readable or `--json`):

```bash
./gradlew route -ProuteSuite=routing-issues -ProuteScenario="#140) usable"     # any suite; name or unique part
./gradlew route -ProuteSuite=canonical -ProuteScenario=gps-natural-0012/maxed -ProuteArgs="--algorithm astar --json"
./gradlew route -ProuteSuite=canonical -ProuteArgs=--list
./gradlew route -ProuteArgs="gps-natural-0012 maxed"                          # = canonical gps-natural-0012/maxed
./gradlew route -ProuteArgs="UNIT_TEST 3222 3218 0 3105 3251 0 --json"         # coordinates with any profile
```

The argument forms and JSON fields are an agent-facing interface; keep them stable.

**Dashboard** — a suite, published as a bundle under `build/reports/pathfinder-dashboard/`:

```bash
./gradlew dashboard -PdashboardSuite=routing-issues
./gradlew dashboard -PdashboardSuite=routing-issues -PdashboardFilter=issue-140   # name or category
./gradlew dashboard -PdashboardSuite=canonical -PdashboardTier=smoke -PdashboardBackend=astar
./gradlew dashboard -PdashboardDataset=my-routes.json                             # an ad-hoc route file (ALL)
./gradlew captureExpectedLengths -PdashboardSuite=routes [-PdashboardFilter=...]
```

**Benchmark** — `./gradlew benchmark --args="--manifest M --output OUT"`, called by
collisionlab/osrs-benchmarks. A manifest names scenarios of any suite by suite and name, with
repetitions and optional warm-up scenarios; each runs as the other two runners run it and is judged by
`Observation.check()`. The task adds the repetitions, warm-up, A* session modes
(`cold`/`account`/`target`) and timings (the search alone, after the account is compiled).

The scenarios, routes and accounts are all this checkout's, and the result's execution metadata
records the tooling and plugin commits, so those commits and the manifest identify everything a run
used. To benchmark other scenarios, check out the tooling revision that has them.

## Expected lengths

Exact path lengths are fixtures, not part of a scenario: `scenarios/src/main/resources/scenarios/expected-lengths/<suite>.json`
maps scenario name to path length (steps).

- **Capture**: `./gradlew captureExpectedLengths -PdashboardSuite=S` runs the suite and rewrites the file
  with every reached scenario's length; with `-PdashboardFilter=F` it rewrites only the matching entries.
  It is the only command that writes the file.
- **Review**: read the diff. Each changed length should follow from the change being made (a plugin
  re-pin, a scenario or account change); a scenario no longer reached drops out of the file.
- **Verify**: the dashboard checks every run against its expected length (`Observation.check()`) and marks
  mismatches in the bundle's `report.json`; `python3 scripts/maintenance.py verify` sweeps every suite.
  `ScenariosTest` only checks that each file names real scenarios.

## What CI checks

| Tests | Run routes? | What they check |
|---|---|---|
| `:accounts:test` | no | the account compiler and the canonical accounts |
| `ScenariosTest`, `CanonicalCorpusTest`, `ProfilesTest`, `ConfigParityTest` | no | every suite loads and compiles its accounts; names are unique; expected-length files name real scenarios; routes are well formed; profiles and overrides reach the pathfinder config; every plugin setting has a `PluginSettings` twin |
| `RouteCliTest`, `BenchmarkMainTest` | a few canonical routes and one Java scenario, legacy and A* | the CLI's forms, the benchmark task and the suites agree; manifest validation; A* session modes; walking-only routes |
| `scripts/benchmark_smoke.py` | two canonical scenarios and one Java scenario, legacy and A* | `benchmark` run as osrs-benchmarks runs it, and its output as osrs-benchmarks reads it (keep the two repositories in step) |
| Python `tests/`, `maintenance.py validate` | no | the scripts, and the plugin's committed data |

No CI test runs a whole suite against its expectations (reachability, minimum and exact lengths): that is
the dashboard's job, through `maintenance.py verify` or `./gradlew dashboard -PdashboardSuite=…` and the
bundle's `report.json`.

## Common changes

| To | Do |
|---|---|
| Add a regression | a `suite.scenario(...)` in the right Java suite (issues: `RoutingIssueScenarios`, category `<domain>-issue-<N>`), then `-ProuteSuite=… -ProuteScenario=…` or the dashboard with a filter |
| Add a canonical route | edit `corpus/corpus/routes-v1.json`; `CanonicalCorpusTest` validates it |
| Change a canonical account | edit `accounts/…/canonical/CanonicalAccounts.java` (or `CanonicalItems`) |
| Teach accounts a new fact | a field on `Account` (or an `Unlock`) and its variables in `AccountCompiler` |
| Add a preset | a `preset(...)` in `Profiles` |
| Add a suite | a Java `define(Suite)` or a route file, registered in `Suites` |
