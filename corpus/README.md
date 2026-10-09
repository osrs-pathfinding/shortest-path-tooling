# Canonical corpus

Implementation-neutral routes and account profiles for OSRS pathfinding. This directory was the
`shortest-path-corpus` repository; its history was imported here. Non-Java consumers (the
shortest-path-benchmarks adapters, the Haskell model, GPS tooling, the web frontend) read it as a
directory, so its layout and file formats are a contract:

| Path | Contents | Source of truth |
|---|---|---|
| `manifest.json` | format and file versions | hand-maintained |
| `corpus/routes-v1.json` | canonical routes | hand-maintained data |
| `corpus/excluded-routes-v1.json` | routes excluded from the corpus, with reasons | hand-maintained data |
| `accounts/account-profiles-v1.json` | the `early`, `mid`, `end`, `maxed` profiles | **generated** from `accounts/` Java |
| `profiles/*.json` | the same profiles as AccountBuild v1 documents for the web | **generated** from `accounts/` Java |
| `schemas/` | AccountBuild, route API, route policy and place JSON schemas | hand-maintained |
| `account-spec.md` | what an account profile contains | hand-maintained |

`end` and `maxed` are quest-cape accounts.

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

## Account profiles

Never edit the generated JSON. The profiles are defined in Java under
`accounts/src/main/java/shortestpath/corpus/profiles/`:

- skills, quests, quest points, total level, diaries, POH, milestones, unlocks and planted trees:
  `CanonicalProfiles.java`;
- item quantities in inventory, bank and rune pouch: `CanonicalItems.java`;
- the raw routing-variable baseline and the mappings from semantic state to varbits/varplayers:
  `RoutingVariables.java`.

`routingVariables` exists for exact engine compatibility and must not be exposed as public
account-editor fields. Consumers must treat `schemaVersion` as the wire compatibility boundary,
must not infer meaning from profile names, and should treat unknown fields as extension data.

From the repository root:

```sh
./gradlew :accounts:generateAccountProfiles   # rewrite the generated JSON
./gradlew :accounts:check                     # tests, byte-for-byte fixture check, data/schema checks
```

CI runs `:accounts:check`, never generation. The RuneLite API version is the one tooling builds
against, so a RuneLite update can change the generated fixtures; `check` reports it.
