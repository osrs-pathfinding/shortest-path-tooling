# shortest-path-tooling

Developer dashboards and OSRS cache dumpers for the [shortest-path](https://github.com/Skretzo/shortest-path) RuneLite plugin.

This repo carries `shortest-path` as a git submodule and uses a Gradle [composite build](https://docs.gradle.org/current/userguide/composite_builds.html) to consume the plugin's sources — so the dashboard and cache-dumper code never pollutes plugin PRs.

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

## Available tasks

| Task | Description |
|------|-------------|
| `./gradlew dashboard` | Build the pathfinder dashboard from the default routes dataset |
| `./gradlew dashboard -PdashboardDataset=/dashboard/clue_locations_full.csv` | Build dashboard from a specific dataset |
| `./gradlew captureExpectedLengths` | Write actual path lengths back into the source CSV as `expected_length` |
| `./gradlew bankTileDump -PbankTileCacheDir=<dir> -PbankTileXteaPath=<keys.json>` | Dump bank-object placements from an OSRS cache to TSV |
| `./gradlew sailingAmenityVarbitDump -PsailingAmenityCacheDir=<dir> -PsailingAmenityXteaPath=<keys.json>` | Dump Sailing island amenity varbits from an OSRS cache |

## Dashboard options

All options are passed via `-P`:

| Option | Default | Description |
|--------|---------|-------------|
| `dashboardDataset` | `/dashboard/routes.csv` | Path to the CSV dataset (resolved from test resources) |
| `dashboardBundle` | derived from filename | Bundle name in the output site |
| `dashboardTitle` | derived from filename | Title shown in the UI |
| `dashboardSubtitle` | *(empty)* | Subtitle shown in the UI |
| `dashboardProfile` | `true` | Whether to enable the profiler |

For the datasets and when to use each, see [docs/dashboard-design.md](docs/dashboard-design.md).

## Keeping up with the plugin

The `shortest-path` submodule is pinned to a specific commit. To update it to the latest plugin master:

```bash
git submodule update --remote shortest-path
git add shortest-path
git commit -m "Update shortest-path submodule"
git push
```

## Cache dumpers

The cache dumpers require a local OSRS cache. Download one first:

```bash
shortest-path/collision-map-update/download-latest-cache.sh
sed -i '' 's/mapsquare/region/g; s/key/keys/g' keys.json
```

Then run the desired task (see table above).

The `rebuild_bank_tsv.py` script merges the bankTileDump output into `shortest-path/src/main/resources/bank.tsv`:

```bash
python3 scripts/rebuild_bank_tsv.py
```
