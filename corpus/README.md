# shortest-path-corpus

This repository is the implementation-neutral benchmark specification for the
OSRS pathfinding projects. It owns the canonical routes, four explicit account
profiles (`early`, `mid`, `end`, and `maxed`), exclusions, negative cases,
benchmark runtime state, and checked-in oracle answers.

Routes and accounts are inputs. `oracle/oracle-v1.json` is a derived artifact;
Haskell `shortest-path-model` currently generates it with exact/reference
Dijkstra. The adjacent metadata records the generator and input versions so a
future independent reference implementation can replace it.

The v1 account file is an explicit serialization of compiled account state.
Consumers decode it into their own runtime account types; neither consumer
defines the profile contents.

## Validate

Format JSON with `jq` before committing changes:

```sh
nix-shell -p jq --run 'tools/format-json.sh'
nix-shell -p jq --run 'tools/format-json.sh --check'
```

The `--check` form is suitable for CI and fails if any JSON file is not the
direct output of `jq .`.

```sh
node tools/validate.js
```

The manifest and schema files define the format/version boundary. The validator
adds the cross-file checks: profile names, unique route IDs, complete oracle
keys, and route expectation/oracle agreement.

## Consumer checkout

Keep one checkout alongside the consumers, for example:

```text
~/src/shortest-path/
~/src/shortest-path-model/
~/src/shortest-path-tooling/
~/src/shortest-path-corpus/
```

Canonical tools accept an explicit corpus directory. Otherwise they use
`SHORTEST_PATH_CORPUS_DIR`, then the local `../shortest-path-corpus` sibling.
Production RuneLite builds do not require this repository.

## Regenerate the oracle

From `shortest-path-model`, point `route-bench` at this checkout and write to
its oracle file:

```sh
nix-shell --run 'cabal run route-bench -- --corpus-dir ../shortest-path-corpus \
    --write-oracle --oracle ../shortest-path-corpus/oracle/oracle-v1.json'
```

The command also writes `oracle/oracle-v1.json.metadata.json` with the corpus
versions, model revision, and resource-data revision when available. Validate
the corpus before committing regenerated results.

Generate account profiles from the semantic Java source with:

```sh
./profile-generator/gradlew -p profile-generator generateAccountProfiles
./profile-generator/gradlew -p profile-generator verifyAccountProfiles
```

Edit `profile-generator/src/main/java/shortestpath/corpus/profiles/CanonicalProfiles.java`,
not the generated JSON. The generator uses the latest RuneLite API release and keeps
the JSON as a checked-in, language-neutral fixture; consumers do not need Java
or RuneLite to load it. Run the generator tests, corpus validator, and both
cross-language consumer test suites before committing source and fixture changes.
