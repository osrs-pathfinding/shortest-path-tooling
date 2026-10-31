# Canonical corpus

The canonical routes. This directory was the `shortest-path-corpus` repository; its history was
imported here.

| Path | Contents |
|---|---|
| `corpus/routes-v1.json` | canonical routes, run by the `canonical` suite with every canonical account |
| `corpus/excluded-routes-v1.json` | routes excluded from the corpus, with reasons |
| `schemas/` | the AccountBuild, route API, route policy and place JSON schemas of the standalone web planner |

The canonical accounts `early`, `mid`, `end`, `maxed` are Java only:
`accounts/…/canonical/CanonicalAccounts.java`. `end` and `maxed` are quest-cape accounts.

## Route format

Each route in `corpus/routes-v1.json` has these consumer-facing fields:

- `id`: stable route identifier;
- `name`: human-readable route label;
- `start`, `target`: authoritative `[x, y, plane]` coordinates;
- `startName`, `targetName`: human-readable endpoint labels;
- `startSource`, `targetSource`: opaque provenance strings for auditing, not a parseable API;
- `allowTransports`: whether the pathfinder may use transports. `false` is a walking route: no teleports
  (home teleports and house mounted items included) and no transport network, only what walking needs
  (doors, stairs and the like);
- `tiers`: benchmark suites containing the route (`smoke`, `standard`, `full`). Every route is in
  `full`, except the `sailing` routes (`sailing-natural-*`), which are tagged only `sailing`;
- `negativeProfiles`: optional account profiles expected to find the route unreachable. These are
  hand-maintained expectations, not derived from any implementation.

`CanonicalCorpusTest` checks the file.

## Sailing routes

`sailing-natural-0001`–`0075` need the player's own boat: Sailing-only islands, narrow
waterways, island clue steps, Wyrmscraig, Sailing level, quest and ocean-hazard gates, and routes
that start or end at sea. `0057`–`0065` reach the remaining island moorings; `0066`–`0070` start
aboard at sea (the route's `aboard` boat), at the Barracuda Trials starts beside each trial master,
the bank boat in the Barracuda Belt and a sea-charting spot; `0071`–`0075` end at the bank boat
and the trial starts. A trial start or bank boat tile is the wiki pin, moved where it says onto
open water every boat fits. Their
`negativeProfiles` say what each account can do in the game, not what the pathfinder can do, so
until the pathfinder sails most of them are unreachable for every profile. They are tagged only
`sailing`, which keeps them out of `smoke`/`standard`/`full`; run them with
`-PdashboardTier=sailing` (or the osrs-benchmarks selector `{"suite": "canonical", "tier": "sailing"}`).

Expectations assume each account owns every boat its Sailing level allows (raft, skiff, sloop) and
the best facilities its Sailing and Construction levels allow, all on one hull: early oak (1.5
tiles a tick), mid mahogany (2), end ironwood (2.5) and maxed rosewood (3). The hazard gear thresholds
(Sailing/Construction) are: storm-proof mast 24/11, inoculation station 40/37, adamant keel 66/62
(crystal-flecked waters), adamant helm 72/59 (tangled kelp) and eternal brazier 78/72 (icy seas).
Rapids only speed a boat up or damage it, so they gate nothing. Water targets are sea-charting
spots from the game cache, and island targets are mooring land tiles.
