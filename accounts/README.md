# Accounts

Every account a route is planned for goes through this project.

- `AccountBuild` is an account as `corpus/schemas/account-build-v1` describes it: levels, quests,
  diaries, items, POH, spellbook, plus compatibility `routingVariables`. The web planner edits
  these and sends them to the route service.
- `AccountBuilds.toAccount` compiles a build into an `Account`, the game state the plugin reads
  (varbits, varplayers, quest states, items). It is the only place semantic state becomes
  variables; the route service and the tooling profiles both call it.
- `canonical/` defines the benchmark profiles (`early`, `mid`, `end`, `maxed`) in Java
  (`CanonicalProfiles`) and writes each as an `AccountBuild` (`CanonicalAccounts.build`). Those
  builds are rendered to `../corpus/accounts/` and `../corpus/profiles/` for non-Java consumers and
  for the web planner's presets. The published `routingVariables` include the values
  `AccountBuilds` derives from the semantic state, so consumers that read only variables agree.

```sh
./gradlew :accounts:generateAccountProfiles   # rewrite the generated JSON
./gradlew :accounts:check                     # tests + byte-for-byte check of the committed JSON
```

`verifyAccountProfiles` (part of `check`) never rewrites the fixture. The RuneLite API version is
the one tooling builds against; a RuneLite update that changes the fixtures fails `check`. The
plugin is a dependency only for item names (`ItemVariations`) in builds.
