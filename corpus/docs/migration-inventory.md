# Split inventory

Initial source inventory before the split:

- `shortest-path-model/benchmarks/corpus/`: 724 canonical routes, exclusions,
  sentinels, wiki/support data, reachability/coverage reports, four-profile
  `account-profiles-v1.json`, and 2,896-entry `oracle-v1.json`.
- `shortest-path-model/src/ShortestPath/BenchmarkProfiles.hs`: authored
  `early`, `mid`, `end`, `maxed`, including skills, quests, diaries, POH,
  spirit trees, inventory/bank, runes, and the synthetic clock.
- `shortest-path-model/app/AccountProfile.hs`: exported those Haskell-built
  accounts to the JSON fixture.
- `shortest-path-model/app/RouteBench.hs`: loaded routes/oracles and generated
  reference results.
- `shortest-path-tooling`: `CanonicalCorpusLoader`,
  `CanonicalAccountProfileLoader`, `CanonicalAccountCompiler`,
  `benchmarkCanonicalLegacy`, and scripts/import paths all resolved the
  Haskell repository's `benchmarks/corpus` directory.
- `shortest-path`: contains production routing only; no canonical corpus
  dependency is introduced.
