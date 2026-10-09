# Scenarios, suites and the three runners

Every route this repository plans — a regression test, a canonical benchmark case, a clue
location, an agent's one-off query — is a **scenario**, and every scenario runs through the same
code. This page is the model; the dashboard UI is in [dashboard-design.md](dashboard-design.md).

## Concepts

| Concept | What it is | Code |
|---|---|---|
| **Account** | The game state a route is planned for: levels, quests, varbits, varplayers, items, bank, world, location, POH, clock | `accounts/` → `shortestpath.accounts.Account` |
| **Profile** | A named account plus plugin settings to start from | `shortestpath.profiles.Profiles` |
| **Scenario** | A route (start, target, transports allowed) + a profile + account and settings overrides + an expectation | `shortestpath.scenarios.Scenario` |
| **Suite** | A named list of scenarios, some tagged with tiers | `shortestpath.scenarios.Suites` |
| **Runner** | Runs a scenario on the legacy or exact pathfinder and returns an `Observation` | `shortestpath.scenarios.ScenarioRunner` |

**Profiles.** The canonical accounts `early`, `mid`, `end`, `maxed` are defined in
`accounts/src/main/java/shortestpath/accounts/canonical/` (the same Java generates the JSON in
`corpus/` for non-Java consumers). The presets `ALL`, `NONE`, `UNIT_TEST`, `SEASONAL`, `BANK`,
`BANK_PERM`, `INVENTORY`, `INVENTORY_NON_CONSUMABLE` start from the harness baseline (every skill 99,
every quest finished, nothing carried) with preset settings. `PluginSettings` is the plugin
settings object every profile and scenario uses.

**Expectations.** A scenario is expected reachable or unreachable, and may set a minimum path
length. *Reached* means the path ends on the target or next to it (targets such as NPCs and objects
cannot be stood on); `Observation.isReached()` is the only definition. A suite may also keep exact
lengths in `src/test/resources/scenarios/expected-lengths/<suite>.json`, which only the dashboard
checks and `captureExpectedLengths` rewrites.

## Suites

A suite is Java or route data.

| Suite | Source | Scenarios |
|---|---|---|
| `routes`, `unit-tests`, `routing-issues`, `collision-map-issues`, `f2p-routes`, `seasonal-briefcase-routes`, `quetzal-whistle-routes` | Java (`*Scenarios.define(Suite)`) | hand-written, with overrides |
| `clue-locations-full` | route data `src/test/resources/scenarios/clue-locations.json` × `ALL` | clue locations |
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

- `.account(a -> ...)` overrides the profile's `Account.Builder`: `varbit`, `varplayer`, `inventory`,
  `equipment`, `bank`, `level`, `quest`, `world`, `location`, `nowMinutes`. Use RuneLite's `gameval`
  constants (`ItemID`, `VarbitID`, `VarPlayerID`).
- `.settings(s -> ...)` overrides `PluginSettings`.
- `Overrides` names the repeated ones: `.account(leagueAreas(LeagueRegion.ASGARNIA, ...))`,
  `.account(eliteDiaries())`, `.settings(bankTeleports())`. Overrides apply in order after the profile.
- `.expectUnreachable()`, `.minimumLength(n)`.
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
./gradlew route -ProuteSuite=canonical -ProuteScenario=gps-natural-0012/maxed -ProuteArgs="--algorithm exact --json"
./gradlew route -ProuteSuite=canonical -ProuteArgs=--list
./gradlew route -ProuteArgs="gps-natural-0012 maxed"                          # = canonical gps-natural-0012/maxed
./gradlew route -ProuteArgs="UNIT_TEST 3222 3218 0 3105 3251 0 --json"         # coordinates with any profile
```

The argument forms and JSON fields are an agent-facing interface; keep them stable.

**Dashboard** — a suite, published as a bundle under `build/reports/pathfinder-dashboard/`:

```bash
./gradlew dashboard -PdashboardSuite=routing-issues
./gradlew dashboard -PdashboardSuite=routing-issues -PdashboardFilter=issue-140   # name or category
./gradlew dashboard -PdashboardSuite=canonical -PdashboardTier=smoke -PdashboardBackend=exact
./gradlew dashboard -PdashboardDataset=my-routes.json                             # an ad-hoc route file (ALL)
./gradlew captureExpectedLengths -PdashboardSuite=routes [-PdashboardFilter=...]
```

**Benchmark adapter** — `./gradlew benchmarkCanonical --args="--manifest M --corpus DIR --output OUT"`,
called by shortest-path-benchmarks. Each manifest case (`route_id`, `profile`) is the `canonical`
scenario `<route_id>/<profile>`; the adapter adds the synthetic clock, warmup, exact-session modes
(`cold`/`account`/`target`) and timings, and writes the protocol-v1 envelope.

`python3 scripts/maintenance.py verify` sweeps every suite on the dashboard; CI runs the scenario,
profile, route-CLI and benchmark tests, which load every suite and compile every Java suite.

## Common changes

| To | Do |
|---|---|
| Add a regression | a `suite.scenario(...)` in the right Java suite (issues: `RoutingIssueScenarios`, category `<domain>-issue-<N>`), then `-ProuteSuite=… -ProuteScenario=…` or the dashboard with a filter |
| Add a canonical route | edit `corpus/corpus/routes-v1.json`; `./gradlew :accounts:check` validates it |
| Change a canonical account | edit `accounts/…/canonical/CanonicalProfiles.java` (or `CanonicalItems`, `RoutingVariables`), run `./gradlew :accounts:generateAccountProfiles`, commit the regenerated `corpus/` JSON |
| Add a preset | a `preset(...)` in `Profiles` |
| Add a suite | a Java `define(Suite)` or a route file, registered in `Suites` |
