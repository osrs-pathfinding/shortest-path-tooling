# Finding locations on the OSRS Wiki

How to turn "Abyssal demons in the Slayer Tower" into `[3415, 3569, 2]`, and a tile back into the
things near it. This is for agents writing scenarios or checking a route's endpoints. The helper is
`scripts/wiki_locations.py` (standard library only, JSON on stdout).

## 1. Look locally first

Places the plugin or this repository already know need no wiki call:

```bash
grep -ri "seers" shortest-path/src/main/resources/destinations/        # banks, altars, anvils, shops
grep -ri "zul-andra" shortest-path/src/main/resources/transports/      # teleport and transport ends
grep -i "slayer tower" corpus/corpus/routes-v1.json                    # named endpoints + sources
grep -i "anagram" src/test/resources/scenarios/clue-locations.json     # clue steps
```

The transport TSVs name where teleports land. A destination tile there is known to work in the
pathfinder.

## 2. Ask the wiki

```bash
python3 scripts/wiki_locations.py search "zulrah shrine"          # page titles
python3 scripts/wiki_locations.py page "Abyssal demon" Hans       # coordinates on those pages
python3 scripts/wiki_locations.py near 3222 3218 0 --radius 6     # what is near a tile
```

`page` reads the page source and returns one **group** per spawn list or map pin:

```json
{"page": "Abyssal demon", "revid": 15357583, "groups": [
  {"kind": "spawns", "label": "Slayer Tower (UK floor 2)", "shape": "points", "mapID": "0",
   "point": [3415, 3569, 2], "tiles": [[3408, 3573, 2], "..."],
   "source": "oldschool-wiki:Abyssal_demon@15357583#Locations"}]}
```

| Field | Meaning |
|---|---|
| `kind` | `spawns`: a `{{LocLine}}` / `{{ObjectLocLine}}` row of the page's Locations table (monsters, NPCs, objects). `map`: a `{{Map}}` pin or shape, usually the infobox map (NPCs, places, shops) |
| `label` | The location text of that row (`Slayer Tower (UK floor 2)`, `Seers' Village`), or the pin's `name`. Choose a group by its label |
| `shape` | `points` for spawns. For `{{Map}}`: `pin`, `square`, `rectangle` (the pin is the centre), `polygon` (`tiles` are the corners), `line`, `circle` |
| `point` | One tile for the group: the tile itself for a pin; the given tile nearest the centre for spawns; the corner average for a polygon. **It may not be walkable** |
| `tiles` | Every coordinate the template gives, `[x, y, plane]` |
| `mapID` | The wiki map layer. `0` (or absent) is the surface; other values are dungeons and other maps, but the coordinates are still game coordinates |
| `source` | Provenance in the corpus format: `oldschool-wiki:<Page>@<revid>#<section>` |

A page without coordinates gets a `hint`. That usually means an instance (Zulrah, most raids,
minigame lobbies): look up the place you enter it from (`search` finds `Zul-Andra`) and route
there.

`near` uses the wiki's Bucket API: all spawn tiles plus the map pins editors have opted in. It
downloads about 4 MB once and caches it for a day under `~/.cache/osrs-wiki-locations/`. Most
infobox pins (NPCs like Hans, places like Lumbridge Castle) are **not** in Bucket (see Pitfalls),
so `near` finds monsters, objects and some NPCs and shops, not every named place.

## 3. Check the tile and write it down

A wiki tile is where the thing is, not always a tile you can stand on (a bank booth, an NPC
behind a counter, an altar). Before you commit an endpoint, run it through the pathfinder:

```bash
./gradlew -q route -ProuteArgs="UNIT_TEST 3222 3218 0 3415 3569 2 --json"   # "reached": true
```

If it is not reached, use a neighbouring tile from `tiles`, another group, or the place's
entrance. For blocked targets the plugin already routes to the tiles next to them, so a booth is
usually fine.

In a route JSON entry (format in [scenarios.md](scenarios.md#route-data)), give the name and
source:

```json
{"id": "...", "target": [3415, 3569, 2], "targetName": "Abyssal demons — Slayer Tower top floor",
 "targetSource": "oldschool-wiki:Abyssal_demon@15357583#Locations"}
```

## Pitfalls

- **Floors.** The wiki's `plane` is the game plane. `UK floor N` is plane N; US floors are one
  higher ("2nd floor" US = plane 1).
- **Spawn groups, not spawns.** A monster's Locations table has one row per area. Pick the row
  (label) you mean, then a tile in it. Don't average across rows.
- **Variants.** Pages like `Guard (Deadman Mode)`, `... (PvP World)`, `... (Leagues)`,
  `(historical)` and `(beta)` describe other game modes or removed content. Pages with versions
  show up in `near` as `Page#Version`.
- **Several pins.** Dungeons and altars often pin the inside first and the entrance later. Check
  every `map` group.
- **Wilderness and members areas** are reachable only for some profiles, so check with the profile
  the scenario will use.

## The wiki API, if the script doesn't cover it

Endpoint `https://oldschool.runescape.wiki/api.php`. Send a descriptive `User-Agent`, make one
request at a time, and leave a short pause between them. Wiki content is CC BY-NC-SA 3.0, which is
why committed coordinates carry their `source`.

**Page source** (what `page` uses). Up to 50 titles per request, follows redirects, returns
`revid`:

```bash
curl -sG -A "my-agent (repo url)" https://oldschool.runescape.wiki/api.php \
  --data-urlencode action=query --data-urlencode prop=revisions --data-urlencode 'rvprop=ids|content' \
  --data-urlencode rvslots=main --data-urlencode redirects=1 --data-urlencode formatversion=2 \
  --data-urlencode format=json --data-urlencode 'titles=Hans|Duradel'
```

**Search** (`list=search`, CirrusSearch syntax). `srsearch=hastemplate:"Infobox Location" intitle:castle`
limits the results to place pages.

**Bucket** (structured data):
`action=bucket&format=json&query=bucket('locline').select('page_name','page_name_sub','plane','coordinates').where('page_name','Cow').run()`.

- Builder: `select(fields…)`, `where({field, value})` or `{field, op, value}` (`= != > >= < <=`),
  `Bucket.And/Or/Not`, `join`, `orderBy(field, 'asc')` (the field must be selected), `limit`
  (≤ 5000, default 500), `offset`. A query is killed after 2 s. An error comes back as an
  `error` field.
- Every row has `page_name` and `page_name_sub` (`Page#Version`). Field names are lowercase with
  underscores. The list of buckets is the `Bucket:` namespace (`Special:AllPages`, namespace
  9592), and each bucket page shows its fields.
- Only two buckets hold coordinates. `locline` (≈10.7k rows, one per Locations-table row):
  `coordinates[]` strings like `x:3222,y:3218` / `3222,3218` / `x : N,y : N,plane : N`, plus
  `plane`, `mapid`, `members`, `leagueregion[]`, **but no location label**. `map`: only pins
  written with `{{Map|...|bucket=yes}}` (≈2.2k rows), as GeoJSON `features` (sometimes with
  trailing commas) and `options` `{x, y, plane, mapID}`.
- `infobox_npc`, `infobox_scenery`, `infobox_shop`, `infobox_location`, `clue_info` and `quest`
  hold names, ids and location *text*, but no coordinates. Use them to find pages
  (`where('npc_id', '3106')` doesn't work: `npc_id` is text, sometimes comma-separated), then read
  the page source.
- `where` on a repeated field matches whole values only, so Bucket cannot search by coordinate
  range. That's why `near` downloads everything and filters locally.
- Coverage: fewer than 10% of NPC pages and 2% of place pages have coordinates in Bucket. The
  rest are only in the page source, which is why `page` reads source and not Bucket.

Other projects that do this, for reference: the travel-guide plugin's `tools/generate-npcs.mjs`
(Bucket + page source), OSRSTaskHub's `classify/wiki_cache.py` (LocLine parsing), and
osrs-copilot's `GameArea` table (region IDs → area names, for "which town is this tile in").
