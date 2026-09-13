# Maintenance workflows

## Purpose

Weekly OSRS game updates can break collision data, transport availability,
and region access. This repo orchestrates the upkeep — the plugin itself is
unchanged. `scripts/maintenance.py` is the single entry point: each
maintenance surface is one subcommand that shells out to the existing
Gradle dumpers, shell scripts, and Python helpers, so step ordering and
preconditions live in exactly one place.

Regenerated data files land in the `shortest-path` submodule's
`src/main/resources/` on a `myfork` feature branch and go upstream by PR —
the established dev cycle. Data-writing subcommands refuse to run on
`master`, detached HEAD, or a dirty worktree, so a maintenance run can
never commit to the wrong place.

## Quick reference

| Subcommand | What it wraps |
|---|---|
| `cache` | `collision-map-update/download-latest-cache.sh` + the keys.json field patch |
| `collision-map` | submodule fetch + ff-merge to `origin/master`, `compare_collision_maps.py`; the local runelite pipeline on `--local` |
| `regions` | `leagueRegionDump` + `f2pRegionDump` Gradle tasks |
| `bank` | `bankTileDump` + `scripts/rebuild_bank_tsv.py` |
| `seasonal` | `scripts/verify_seasonal_regions.py` |
| `refresh` | the ordered chain: collision-map -> cache -> regions -> bank -> seasonal |
| `probes` | the 11 season-discovery dump tasks (8 default + 3 name-driven) |
| `verify` | `compileTestJava` + submodule `test` + `dashboard` sweep + edge-diff |

Run any subcommand with `--help` for its flags.

## Subcommands

### `cache`

```bash
python3 scripts/maintenance.py cache
```

Downloads the latest OSRS cache and `keys.json` from archive.openrs2.org
via `download-latest-cache.sh` (into `./cache` and `./keys.json`), then
patches the key file's `mapsquare`/`key` fields into the `region`/`keys`
shape the RuneLite XteaKeyManager expects. The patch is JSON-level and
idempotent — never `sed` the file by hand.

### `collision-map`

```bash
python3 scripts/maintenance.py collision-map [--local] [--commit]
```

Primary path: fast-forwards the submodule to `origin/master` (upstream's
weekly `ExtractCollisionMap.yml` regenerates the zip every Wednesday), then
prints the `compare_collision_maps.py` edge diff between the previous
artifact (extracted from git history) and the new one. Prints the gitlink
commit hint, or stages it directly with `--commit`.

`--local` regenerates the zip through the runelite pipeline instead
(clone -> dumper inject -> `:cache:shadowJar` -> `java -jar` -> zip), for
when upstream's automation lags or breaks. See Notes for first-run cost.

### `regions`

```bash
python3 scripts/maintenance.py regions [--f2p]
```

Runs `leagueRegionDump` then `f2pRegionDump` against `./cache`, copies
`leagues/regions.tsv` into the submodule unconditionally, and copies
`f2p/regions.tsv` only when a plugin-side consumer exists (an `f2p/`
resources dir or `F2p*.java` class) or `--f2p` forces it — otherwise the
output stays staged in `build/f2p-regions/`.

Preconditions: cache present, myfork feature branch, clean submodule
worktree.

### `bank`

```bash
python3 scripts/maintenance.py bank
```

Runs `bankTileDump` at its default output path, then merges placements
into `destinations/game_features/bank.tsv` via `rebuild_bank_tsv.py`
(additive merge — curated stand tiles win). Same write preconditions as
`regions`.

### `seasonal`

```bash
python3 scripts/maintenance.py seasonal
```

Read-only check: compares seasonal transport region assignments in the
submodule's `seasonal_transports.tsv` against wiki ground truth and
prints a per-section summary. No branch gate.

### `refresh`

```bash
python3 scripts/maintenance.py refresh [--skip-collision] [--local] [--f2p]
```

The derivable update chain as one command, in fixed order:
collision-map -> cache -> regions -> bank -> seasonal. The order matters —
`rebuild_bank_tsv.py` walks the submodule's current collision-map.zip for
stand-tile reachability, so the map update must come first. Aborts on the
first failing step. The write-branch and clean-worktree gates run up
front, before even the collision step.

### `probes`

```bash
python3 scripts/maintenance.py probes [--names-file names.txt]
```

Runs every season-discovery dumper sequentially (each wants an 8 GB
heap): `leagueIdProbe`, `leagueTeleportItemDump`, `leagueAreaStructDump`,
`leagueScriptScan`, `briefcaseEnumProbe`, `briefcaseParamScriptScan`,
`briefcaseTeleportTables`, `sailingAmenityVarbitDump`, plus the three
name-driven scans (`briefcaseDestOverlapScan`, `briefcaseStructHunt`,
`briefcaseDbRowScan`) when `--names-file` supplies a canonical destination
list. Writes to `build/` and stdout for human reading; a failing probe is
reported without masking the rest.

### `verify`

```bash
python3 scripts/maintenance.py verify [--skip-compile] [--skip-lint] \
    [--skip-dashboard] [--skip-diff] [--old-zip a.zip --new-zip b.zip]
```

The post-update compatibility gate — four tiers, all always run (a red
tier never hides the rest), printing `PASS`/`FAIL` per tier and a final
`verify: n/m tiers passed` line (m = the tiers that ran); exit code is
nonzero when any tier fails:

1. `compileTestJava` — the same compile gate CI runs.
2. `./gradlew -p shortest-path test` — the submodule's test suite
   (`TransportDataLintTest` et al.).
3. `dashboard` over every committed CSV under
   `src/test/resources/dashboard/` — enumerated via `git ls-files`, so
   the gitignored `debug.csv` scratch file can never enter the sweep.
   Pass/fail derives solely from each bundle's `report.json` run records
   (`reached` falsy or `assertionPassed` false) — the task sets
   `ignoreFailures = true`, so Gradle's exit code carries no signal and a
   missing or unparseable report fails the tier closed.
4. Collision-map edge-diff: the superproject-pinned gitlink artifact vs
   the worktree zip, compared by `compare_collision_maps.py`. Identical
   zips report "no edge changes" and pass; a non-empty diff is review
   evidence, not a failure. `--old-zip`/`--new-zip` (given together)
   override both sides.

Evidence stays under `build/` — `verify` writes no committed log.

## Suggested workflows

### Weekly refresh

After the Wednesday game update and upstream's nightly collision-map run:

1. `git -C shortest-path checkout -b maint-<date>` — data lands on a
   `myfork` feature branch, never master.
2. `python3 scripts/maintenance.py refresh` — runs the whole derivable
   chain (add `--skip-collision` if the zip is already current).
3. `python3 scripts/maintenance.py verify` — the four-tier gate.
4. Review the edge-diff output and any dashboard failures in
   `build/reports/pathfinder-dashboard/`.
5. `git -C shortest-path add` + `commit` the regenerated resources on the
   feature branch; `git -C shortest-path push myfork` and open a PR
   upstream.
6. `git add shortest-path` + commit the gitlink bump in this repo.

### New league season

Discovery first, then curated edits, then the chain:

1. `python3 scripts/maintenance.py probes` — all eight default-tier
   dumpers; add `--names-file <file>` (one canonical destination name per
   line) to also run the three briefcase name-driven scans.
2. Curate the bounding boxes in `src/test/resources/leagues_regions.tsv`
   and `src/test/resources/f2p_regions.tsv` from `leagueAreaStructDump`
   output.
3. Curate `shortest-path/src/main/resources/transports/seasonal_transports.tsv`
   — item IDs and teleports from `leagueIdProbe`/`leagueTeleportItemDump`
   output. Per-row region exceptions go in the `Region override` column;
   general region gating belongs in code, not in the TSV (see
   `shortest-path/docs/Transport-TSV-format.md` maintenance notes).
4. `python3 scripts/maintenance.py refresh` then
   `python3 scripts/maintenance.py verify`.
5. Commit on the feature branch, push `myfork`, PR upstream.

### Upstream-issue-driven transport edits

For a reported transport/routing bug that resolves to a TSV fix:

1. Edit the relevant `shortest-path/src/main/resources/transports/*.tsv`
   on a `myfork` feature branch.
2. `python3 scripts/maintenance.py verify --skip-diff` as the lint +
   scenario gate.
3. Commit on the feature branch, push `myfork`, PR upstream.

## Notes

- Upstream's `ExtractCollisionMap.yml` (Wednesdays 23:20 UTC) plus this
  repo's `update-submodule.yml` (daily 06:00 UTC) are the primary
  collision-map source — `collision-map` consumes and diffs that artifact.
  Fork-side Actions, if ever added, must be `workflow_dispatch`-only: a
  competing schedule auto-committing the binary to `myfork` master
  guarantees divergence from upstream's own auto-commits.
- `collision-map --local` needs a JDK 11 toolchain — the runelite build
  pins `languageVersion = 11` and Gradle auto-provisions it on first run
  (or install one via sdkman). The first `--local` run downloads multiple
  GB and builds runelite (~10+ min); all scratch state lives under
  `build/runelite-work/`.
- keys.json patching is owned by the CLI (`cache` applies it
  unconditionally — it is idempotent). Never `sed` the file: BSD/GNU
  `-i` syntax differs and `s/key/keys/g` corrupts `"keys"` into `"keyss"`
  on re-run.
- `f2p/regions.tsv` is staged in `build/` until the plugin-side F2P
  consumer merges — `regions --f2p` forces the copy for feature-branch
  work.
- Verification evidence is ephemeral `build/` output — there is no
  committed verification log; weekly verification is for the maintainer's
  own confidence.
- In-game smoke testing is the conditional manual tier: reserve it for
  release gates and API-touching updates. The RuneLite client cannot be
  automated, so it is deliberately not part of `verify`.
