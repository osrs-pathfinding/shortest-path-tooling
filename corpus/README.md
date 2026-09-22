# shortest-path-corpus

This repository is the implementation-neutral benchmark corpus for OSRS
pathfinding. It owns canonical routes, four account profiles (`early`, `mid`,
`end`, `maxed`), exclusions, runtime state, and checked-in oracle answers.
`end` and `maxed` are quest-cape accounts.

The account JSON is generated from explicit Java profile definitions. Consumers
load the JSON as a language-neutral fixture; they do not infer account meaning
from profile names or recreate the generator.

## Route format

Each route in `corpus/routes-v1.json` has these consumer-facing fields:

- `id`: stable route identifier;
- `name`: human-readable route label;
- `start`, `target`: authoritative `[x, y, plane]` coordinates;
- `startName`, `targetName`: human-readable endpoint labels;
- `startSource`, `targetSource`: opaque provenance strings for auditing, not a
  parseable API;
- `allowTransports`: whether the pathfinder may use transports;
- `tiers`: benchmark suites containing the route (`smoke`, `standard`, `full`);
- `negativeProfiles`: optional account profiles expected to find the route
  unreachable.

Coordinate-resolution candidates and other generation diagnostics are not part
of the corpus contract. Route generation must resolve them before writing the
authoritative `start` and `target` coordinates.

## Profile authoring map

Edit the Java source, never `accounts/account-profiles-v1.json`:

- skills, quests, quest points, total level, diaries, POH, milestones, unlocks,
  and planted trees: `profile-generator/src/main/java/shortestpath/corpus/profiles/CanonicalProfiles.java`;
- item quantities in inventory, bank, and rune pouch: `CanonicalItems.java`;
- the selected raw keyed routing baseline and all reusable RuneLite variable
  mappings plus conflict-checked derived assignments: `RoutingVariables.java`;

`ProfileSpec` is the small authoring object. `ProfileCompiler` converts its
explicit state to the v1 JSON shape. `profile.name()` is only the serialized
profile label. RuneLite API version `1.12.39` is pinned in
`profile-generator/build.gradle`; updating it is an intentional change because
RuneLite data can change generated fixtures.

## Account workflow

Run from the repository root:

```sh
./profile-generator/gradlew -p profile-generator test
./profile-generator/gradlew -p profile-generator generateAccountProfiles
./profile-generator/gradlew -p profile-generator verifyAccountProfiles
node tools/validate.js
```

Generation intentionally updates the committed fixture. Verification compiles
fresh output in memory, compares it with the committed file, reports the first
semantic difference, and never writes a candidate file. CI runs tests and
verification, not generation. `tools/format-json.sh --check` is the optional
format check used by CI.

## Other corpus validation

`node tools/validate.js` checks the manifest, route IDs and tiers, oracle
coverage, route expectations, and meaningful nested account-profile shapes.
The corpus does not retain unenforced JSON schemas.

The oracle is derived by the adjacent reference model; regenerate it only when
that workflow is deliberately being changed. The generated account JSON is a
contract: downstream consumers should preserve field meanings and treat
unknown future fields as extension data rather than deriving new semantics from
profile labels.
