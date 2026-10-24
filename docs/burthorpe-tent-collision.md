# Burthorpe tent entrance correction

In-game checks confirmed that the clue boxes at (2885,3540,0) are usable only
from (2886,3540,0), and that the entrance tile (2887,3539,0) is walkable.

The existing `TileObjectProbeTest` found this placement in the local game cache:

```text
2887 3539 0  id=3673  type=9  orient=2  interact=0  wallOrDoor=0  size=1x1
2885 3540 0  id=3686  type=11 orient=3  name=Boxes  ops=Search
```

The dumper treats type-9 objects as diagonal walls without checking
`interactType`. For object 3673 this closes all four edges of the opening,
leaving the two interior tiles (2886,3540,0) and (2887,3540,0) disconnected.
The correction uses the existing placement-scoped exclusion mechanism rather
than changing other type-9 objects or other placements of object 3673.

## Validation

Both `CollisionMapDumperTest` cases fail without the correction and pass with
it. The placement-scope test runs without a cache in CI. The real-cache test
is opt-in:

```sh
./gradlew :test --tests shortestpath.dump.CollisionMapDumperTest \
  -Dtile.probe=true \
  -Dtile.probe.cacheDir=/path/to/cache \
  -Dtile.probe.xteaPath=/path/to/keys.json
```

The cache-backed test generates region 45_55 with the actual dumper, verifies
the outside-to-opening-to-interior-to-box-approach connection, and checks that
the surrounding walls, bed and boxes remain blocking. An A/B dump of that
region changes exactly four movement bits, from blocked to open; no structural
wall bits change.

With that generated region substituted into build resources, all 14
`AStarPathfinderBlockedTargetTest` cases on shortest-path PR #846 pass,
including the Burthorpe wrong-side regression. Without it the regression
reports success at the invalid outside tile (2884,3541,0).

This changes the dumper source. The plugin's committed `collision-map.zip`
still needs regeneration to distribute the corrected collision data.
