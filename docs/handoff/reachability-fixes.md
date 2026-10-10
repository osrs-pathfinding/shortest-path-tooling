# Handoff: reachability fixes (clue steps and seasonal-only areas)

Written 2026-10-10. This is a working note for continuing these branches; delete it before
merging. Nothing here has been checked in game, and no PRs are open yet.

## Branches

Two stacked pairs. The tooling branches pin the plugin branches through the `shortest-path`
submodule, so each tooling branch needs its plugin branch available.

| Repo | Branch | Base | Contents |
|---|---|---|---|
| plugin (`mpickering/shortest-path`) | `fix/clue-reachability` | upstream `master` `cbfc16a` | `77588d4` blocked-target sealed pockets, `b4ee045` avoid-wilderness change (**on hold, see #744**), `168e950` + `3391322` transports into closed areas |
| plugin | `fix/seasonal-only-areas` | `fix/clue-reachability` | `5b99ee3` transports into seasonal-only areas, `096e55e` "Soul rift unlocked" setting |
| tooling (`osrs-pathfinding/shortest-path-tooling`) | `fix/clue-reachability` | `merge/web` | clue suite in Java with item overrides, clue data fixes, expectations, allowlist and `useSailingMoves` twin; pins plugin `3391322` |
| tooling | `fix/seasonal-only-areas` | tooling `fix/clue-reachability` | routing-issues scenarios per fixed area, soul rift twin, allowlist; pins plugin `096e55e`; this note |

The submodule commits exist only on `mpickering/shortest-path`. To build tooling, either fetch that
fork into the submodule or pass `-PshortestPathDir=<plugin checkout>`.

## Upstream issues

| Issue | Status |
|---|---|
| #675 #676 #677 #678 #684 #685 #686 #687 #688 #689 #691 #692 #693 | Fixed on plugin `fix/seasonal-only-areas` |
| #603 walking over teleport exits | Already fixed by #668; can be closed |
| #679 Stalker Den | Already fixed by #666; can be closed |
| #602 Awowogei | Reached on upstream master; can be closed once the guards are checked in game |
| #680 Barrows tunnels | Left: entered through whichever brother's crypt is hidden, which changes every run |
| #681 Motherlode Mine section | Left: the mine is split by rockfalls the collision map treats as walls. Needs a decision on how to model rockfalls |
| #682 Vardorvis arena | Left: the arena is an instance (wiki), so the static area is unreachable by design |
| #683 Waterbirth crevice | Left: the Waterbirth Island Dungeon is essentially unmodelled |
| #690 Shayzien room | Left: the Banker's Briefcase lands behind the bank counter, as about 16 other briefcase destinations do. Needs the real landing tile from the game |
| #744 avoid-wilderness semantics | Open question for you; `b4ee045` implements option A per account and should not go upstream until it is decided |

## Check in game

Each item lists what to confirm. The tile coordinates are the transport rows in
`transports.tsv` (or the file named).

### Seasonal-only areas (`fix/seasonal-only-areas`)

- [ ] **Kourend Castle stairs** (11807/11799 ground↔first, 12536/12538 first↔second): that both
  staircases at y≈3665/3680 and y≈3658/3687 go where the rows say.
- [ ] **Legends' Guild**: staircase 15653/15654 (ground↔first) and ladder 16683/16679 to the top floor.
- [ ] **Alchemical Society, Aldarin**: staircase 54946 at 1388,2915 lands next to 54945 at
  1388,9313; any requirement to go down.
- [ ] **Gauntlet Portal** (3229,6114): the lobby landing tile (3033,6121,1 is a guess) and whether
  the lobby has a way back out besides teleporting.
- [ ] **Woodcutting Guild Ent dungeon**: cave 28855 → landing by the vine (1596,9900); vine back
  to 1606,3508; the five roots are simple step-overs with no requirement.
- [ ] **Camdozaal**: cave entrance 40887 (2947,3506) ↔ Ruins Exit 41446; requirement is Below Ice
  Mountain completed (or started?).
- [ ] **Mole Hole**: digging any mole hill lands at 1760,5191 (the wiki map pin, unverified); the rope
  out (12230 at 1752,5136) is not modelled. Does a light source matter for routing?
- [ ] **Mor Ul Rek hot vent doors**: which capes let you through (rows accept fire cape, fire max
  cape and their trouver versions; is an infernal cape enough?), and that leaving needs nothing.
- [ ] **Viyeldi caves**: winch 2935 "Climb-down" lands at 2377,4712 by the climbing rope; the six
  Rocky Ledge/Rocks obstacles (2959-2964) need no skill; the 96 Agility crevice 53242 now lands at
  2764,9339 (it used to point at a blocked tile).
- [ ] **Hueycoatl**: the Darkfrost slope 55234 (1520,3290) slides from 1524,3290 into the arena at
  1519,3289; whether the arena can be left the same way.
- [ ] **Seasonal destinations**: Map of Alacrity "God wars dungeon: Crack" now lands at 2899,3713 and
  Evil Eye "Moons of Peril" at 1436,3128 (`seasonal_transports.tsv`). Check the real landing tiles
  in a Leagues world if possible.
- [ ] **Soul rift**: that the rift is locked until dark essence is used on the Soul Altar, and that
  the "Soul rift unlocked" setting text is right.

### Clue areas (`fix/clue-reachability`)

- [ ] **Watchtower**: after the quest, the first-floor ladder 2796 lands on the copied top floor at
  2933,4712,2 and the ladder down returns to 2549,3112,1 (wiki says so); before the quest it goes
  to 2549,3112,2 (varp 212 < 14).
- [ ] **Arceuus Library stairs** 27851-27856: the landing ends of all six flights (offsets were
  copied from the stairs of the building east of the library).
- [ ] **God Wars Dungeon ice bridge** 26518: 2885,5333 ↔ 2885,5344 with 70 Hitpoints, duration 5.
- [ ] **Cooks' Guild door** 24958: the accepted headgear and capes list.
- [ ] **Magic axe hut doors** 11726: lockpick and 23 Thieving in; leaving is free.
- [ ] **Hardwood grove doors** 9038/9039: 100 trading sticks in, free out.
- [ ] **Fisher Realm, swamp shed, Villa Lucens entryway, iceberg door 21068, Tarn's Lair wall trap**:
  that the crossings are where the rows say.
- [ ] **Iban's temple**: the emote clue is on plane 1 (RuneLite says 0).

## How to continue

### Find areas only seasonal transports reach

`SeasonalOnlyAreasScratchTest.java.txt` (next to this note) builds the exact backend's structural
reachability with and without `SEASONAL_TRANSPORTS` and prints each area that only seasonal
transports reach, with its size, bounding box and which seasonal transport lands there. Copy it
into the plugin as `src/test/java/shortestpath/pathfinder/exact/SeasonalOnlyAreasScratchTest.java`
and run:

```bash
SEASONAL_SCRATCH=1 ./gradlew test --tests 'shortestpath.pathfinder.exact.SeasonalOnlyAreasScratchTest' -i \
  | grep -E 'SEASONAL-ONLY|tiles'
```

On `fix/seasonal-only-areas` it reports the five areas left above plus the bank-counter pockets.

### Find the objects at an area's edge

The cache is from openrs2 (cache 2735, 2026-10-07). `keys.json` can be empty: map data is no longer
encrypted. Download a cache with `scripts/maintenance.py cache` (needs `unzip`), then probe:

```bash
./gradlew :test --tests shortestpath.dump.TileObjectProbeTest -Dtile.probe=true \
  -Dtile.probe.cacheDir=cache -Dtile.probe.xteaPath=keys.json \
  "-Dtile.probe.boxes=x1 x2 y1 y2;x1 x2 y1 y2" -Dtile.probe.plane=0 -i | grep -P '^\s+\d+ \d+ \d\tid='
```

Each line gives position, object id, type, orientation, name, size and actions. Use `:test`, not
`test`, or the filter also runs in `:accounts` and fails. `scripts/collision_zip.py` reads the
collision map for walkability and flood fills; `scripts/wiki_locations.py` gives wiki coordinates
and labels (`docs/wiki-locations.md`).

### Check that nothing regressed

```bash
# every suite, legacy and exact, against a given plugin checkout
./gradlew -q dashboard -PshortestPathDir=<plugin> -PdashboardSuite=<suite> -PdashboardProfile=false \
  [-PdashboardBackend=exact] -PdashboardBundle=<name>
```

Compare `build/reports/pathfinder-dashboard/bundles/<name>/report.json` runs by name: reached,
`closestReachedPoint` and `len(path)`. All suites take about 1.5 minutes per backend. At these
heads: no reach regressions; canonical routes into Kourend are longer because the soul rift is
now gated (expected, #693).

### Things that break on every plugin re-pin

- `PluginDependencyRuleTest` keys its allowlist by file and line. It reads `shortest-path/` directly
  (not `-PshortestPathDir`), so the submodule must be checked out at the pin. Realign: override reads
  by their key, other sites by matching source text against the previous pin.
- `ConfigParityTest` needs a `PluginSettings` field, getter and setter for every new plugin setting.
  Unlocks not yet upstream keep their getter without `@Override` (see the comments there).
- `SubmoduleValidationTest` fails when the submodule is a git worktree instead of a clone; it passes
  with a real submodule.

### Known failures that predate this work

- routing-issues "SW Ape Atoll (#261)" and "Ape Atoll bridge (#265)" fail at `457f94f` as well.
- Five routing-issues scenarios differ between legacy and exact (Pyramid Plunder #245, Ape Atoll SE
  #265, three MEP2 temple #202); same at the clue pin.
- clue-locations-full has about 118 exact-length drifts that predate this work.

## Landing order

1. Decide #744; drop or rework `b4ee045` on the plugin branch.
2. Plugin PRs upstream (they can be split: the sealed-pocket fix, transport data, the soul rift
   setting).
3. Tooling: land `merge/web`, then these branches, re-pinning the submodule to the upstream commits.
