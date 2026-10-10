# Accounts

Every account a route is planned for is an `Account`, and only `AccountCompiler` turns it into
what the plugin reads.

- `Account` describes an account as a player would: skill levels, quest states, diary tiers,
  `Unlock`s (shortcuts, balloon routes, quetzal platforms, respawn points, …), spellbook, minigame
  teleport cooldown, items, house (`Poh`), planted spirit trees, world, location, clock. Raw
  varbits and varplayers are an escape hatch for what no field describes; they override the facts.
- `AccountCompiler.compile` produces a `ClientState`: the varbits, varplayers, quest states, levels
  and item containers the RuneLite client reports. Every variable a fact controls is always set
  (0 when the fact is absent), so an account describes them completely.
- `HeadlessClient` is a RuneLite `Client` that answers the pathfinder's calls from a `ClientState`.
- `canonical/CanonicalAccounts` defines the benchmark profiles `early`, `mid`, `end`, `maxed`.

Profiles (accounts + plugin settings), presets and scenarios live in `../routing` and the tooling
tests; the web planner's JSON form of an account is `../routing` `AccountJson`.

```sh
./gradlew :accounts:test
```
