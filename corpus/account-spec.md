# Account profile reference

An account profile is a deterministic snapshot of the game state needed to
evaluate corpus routes: skill levels, completed quests, inventory and bank,
diaries, POH facilities, unlocks, RuneLite routing variables, and benchmark
runtime state. It is not a live account export.

The canonical profiles are:

- `early`: introductory progression and medium diaries;
- `mid`: progressed quests and unlocks with hard diaries;
- `end`: a quest-cape account plus end-game POH, trees, and an elite Lumbridge
  diary;
- `maxed`: the quest-cape end-game state with all modeled skills, elite diaries,
  and all modeled planted spirit trees.

Their source definitions are in
`profile-generator/src/main/java/shortestpath/corpus/profiles/CanonicalProfiles.java`.
Item inventories and banks use the reusable data in `CanonicalItems.java`.
`ProfileCompiler` turns a `ProfileSpec` into the checked-in
`accounts/account-profiles-v1.json`; `RoutingVariables` translates semantic
state and keyed raw baselines into varbits and varplayers with conflict checks.
Compilation is deterministic and profile names are labels only.

## Maintainer workflow

From the repository root:

```sh
./profile-generator/gradlew -p profile-generator generateAccountProfiles
./profile-generator/gradlew -p profile-generator verifyAccountProfiles
node tools/validate.js
```

Generation is the explicit update operation. Verification compares fresh
in-memory output with the committed fixture and must leave the working tree
unchanged. CI performs verification, never regeneration.

## Invariants and consumer contract

Keep the four profile labels and the v1 JSON field meanings stable. Keep
benchmark time deterministic; do not use wall-clock time. A routing variable
may receive the same value from multiple semantic sources, but conflicting
values must fail compilation. Downstream implementations should consume the
generated JSON as the account-state contract and should not infer progression
from profile names.
