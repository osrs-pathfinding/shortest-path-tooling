# Canonical account profile generator

`src/main/java/shortestpath/corpus/profiles/CanonicalProfiles.java` is the
semantic source for the four benchmark accounts. The checked-in JSON is a
generated, language-neutral fixture.

The generator follows the latest RuneLite API release, matching the adjacent
Java pathfinder checkout.

From `shortest-path-corpus`:

```sh
./profile-generator/gradlew -p profile-generator generateAccountProfiles
./profile-generator/gradlew -p profile-generator verifyAccountProfiles
```

`verifyAccountProfiles` never rewrites the fixture.
