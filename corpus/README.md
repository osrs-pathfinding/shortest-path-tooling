# Canonical corpus

The canonical routes and the web API schemas. This directory was the `shortest-path-corpus`
repository; its history was imported here.

| Path | Contents |
|---|---|
| `corpus/routes-v1.json` | canonical routes, run by the `canonical` suite with every canonical account |
| `corpus/excluded-routes-v1.json` | routes excluded from the corpus, with reasons |
| `schemas/` | the route API, account and place JSON schemas the web planner and service share |

The canonical accounts themselves are Java: `accounts/…/canonical/CanonicalAccounts.java`.

## Route format

Each route in `corpus/routes-v1.json` has these consumer-facing fields:

- `id`: stable route identifier;
- `name`: human-readable route label;
- `start`, `target`: authoritative `[x, y, plane]` coordinates;
- `startName`, `targetName`: human-readable endpoint labels;
- `startSource`, `targetSource`: opaque provenance strings for auditing, not a parseable API;
- `allowTransports`: whether the pathfinder may use transports;
- `tiers`: benchmark suites containing the route (`smoke`, `standard`, `full`);
- `negativeProfiles`: optional account profiles expected to find the route unreachable. These are
  hand-maintained expectations, not derived from any implementation.
