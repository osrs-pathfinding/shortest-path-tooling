# Account profiles

Java definitions of the canonical benchmark accounts (`early`, `mid`, `end`, `maxed`).
`src/main/java/shortestpath/accounts/canonical/CanonicalProfiles.java` is the semantic source; the
JSON in `../corpus/accounts/` and `../corpus/profiles/` is generated from it for non-Java
consumers.

```sh
./gradlew :accounts:generateAccountProfiles   # rewrite the generated JSON
./gradlew :accounts:check                     # tests + byte-for-byte check of the committed JSON
```

`verifyAccountProfiles` (part of `check`) never rewrites the fixture. The RuneLite API version is
the one tooling builds against; a RuneLite update that changes the fixtures fails `check`.
