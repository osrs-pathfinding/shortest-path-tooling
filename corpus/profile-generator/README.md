# Canonical account profile generator

`src/main/java/shortestpath/corpus/profiles/CanonicalProfiles.java` is the
semantic source for the four benchmark accounts. The checked-in JSON is a
generated, language-neutral fixture.

The generator pins RuneLite API `1.12.39` in `build.gradle`. Updating that
version is an intentional repository change because it may alter fixture data.

From `shortest-path-corpus`:

```sh
./profile-generator/gradlew -p profile-generator generateAccountProfiles
./profile-generator/gradlew -p profile-generator verifyAccountProfiles
```

`verifyAccountProfiles` never rewrites the fixture.
