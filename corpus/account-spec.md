Implement the canonical benchmark account-profile generator in `shortest-path-corpus`.

The current canonical fixture:

```text
accounts/account-profiles-v1.json
```

contains concrete levels, quests, varbits, varplayers, items, POH state and runtime state consumed by the Haskell and Java pathfinders.

At present too much of that low-level state is effectively authored as concrete game variables. Replace that authoring model with semantic Java profile definitions using RuneLite's API/constants.

The desired architecture is:

```text
semantic Java profile definitions
        |
        | RuneLite Quest / Skill / ItemID /
        | VarbitID / VarPlayerID constants
        v
semantic profile compiler
        |
        v
concrete canonical fixture
accounts/account-profiles-v1.json
        |
        +--------------------+
        |                    |
        v                    v
shortest-path-model      Java shortest-path
Haskell loader           canonical loader
```

The Java semantic source becomes authoritative.

The JSON remains a checked-in, language-neutral generated fixture.

This generator belongs in `shortest-path-corpus` because its sole purpose is to define and generate canonical corpus account data.

It must **not** become a general RuneLite account-modelling library.

# 1. Create a small Java subproject

Add something approximately like:

```text
shortest-path-corpus/
├── accounts/
│   └── account-profiles-v1.json
│
├── profile-generator/
│   ├── build.gradle
│   ├── settings.gradle
│   └── src/
│       ├── main/java/shortestpath/corpus/profiles/
│       └── test/java/shortestpath/corpus/profiles/
│
├── corpus/
├── oracle/
├── manifest.json
└── README.md
```

A different compact Java layout is fine if the corpus repository already has a build structure.

Keep all generator implementation under:

```text
profile-generator/
```

rather than turning the entire corpus repository into a general Java application.

Add a Gradle wrapper if the repository does not already have one.

# 2. Depend on RuneLite directly

Use the RuneLite API as the source for game identifiers and semantic enums.

At minimum the generator should use RuneLite equivalents of:

```text
Quest
Skill
ItemID
VarbitID
VarPlayerID
```

Do not guess package names or dependency versions.

Inspect the sibling:

```text
../shortest-path
```

and use the same RuneLite revision/version and compatible Java toolchain already used by that project.

Pin the dependency/revision explicitly.

Prefer depending only on RuneLite API modules required for constants/enums.

Do not depend on the RuneLite client unless there is a concrete semantic API which is unavailable from the API artifact.

The profile generator must not depend on:

```text
shortest-path-model
shortest-path-tooling
gps-plugin
```

The dependency direction is deliberately:

```text
RuneLite
   ↓
profile-generator
   ↓
canonical JSON
   ↓
all routing implementations
```

# 3. Preserve the current JSON contract

Do not redesign `account-profiles-v1.json` in this task.

The generator must render the existing v1 structure consumed by `ShortestPath.BenchmarkProfiles`.

The root shape is:

```json
{
  "formatVersion": 1,
  "benchmarkNowMinutes": 100000000,
  "profiles": {
    "early": {},
    "mid": {},
    "end": {},
    "maxed": {}
  }
}
```

Each generated profile must continue to contain:

```text
levels
completedQuests
varbits
varplayers
inventory
equipment
runePouch
bank
diaries
poh
plantedSpiritTrees
fairyRingsUnlocked
runtime
```

The existing POH shape must remain equivalent to:

```json
{
  "location": "...",
  "jewelleryBox": "...",
  "portals": {
    "mode": "all|selected",
    "destinations": []
  },
  "fairyRing": true,
  "spiritTree": true,
  "obelisk": true,
  "mountedGlory": true,
  "mountedXerics": true,
  "mountedDigsite": true,
  "mountedMythical": true
}
```

The existing runtime shape remains:

```json
{
  "spellbook": "Standard|Ancient|Lunar|Arceuus",
  "minigameTeleport": {
    "state": "ready|usedAt",
    "minutes": 123
  },
  "arriveInsidePoh": true
}
```

`minutes` should only be rendered where appropriate.

Do not require changes to the Haskell fixture parser merely to accommodate the generator.

# 4. Separate semantic specification from rendered fixture types

Do not define profiles directly using the JSON DTO.

Have two distinct layers:

```text
ProfileSpec
    semantic source

CompiledProfile / ProfileFixture
    low-level rendered representation
```

For example:

```java
final class ProfileSpec
{
    String name;
    Map<Skill, Integer> levels;
    Set<Quest> completedQuests;
    Map<Diary, DiaryTier> diaries;

    ItemLoadout carried;
    Map<Integer, Integer> bank;

    PohSpec poh;

    Set<PlantedSpiritTree> plantedSpiritTrees;
    boolean fairyRingsUnlocked;

    Set<QuestMilestone> questMilestones;
    Set<QuetzalPlatform> quetzalPlatforms;
    Set<HotAirBalloonDestination> hotAirBalloonDestinations;
    Set<CatacombsEntrance> catacombsEntrances;
    Set<PermanentUnlock> permanentUnlocks;

    RuntimeSpec runtime;
}
```

Exact Java class structure is flexible.

The important property is:

> `ProfileSpec` contains semantic facts, not the derived varbit/varplayer representation.

# 5. There must be no general raw-varbit API in `ProfileSpec`

Do **not** expose profile-authoring methods such as:

```java
.varbit(4458, 1)
.varplayer(888, 99999979)
.rawVarbit(...)
.rawVarplayer(...)
```

Likewise do not allow:

```java
Map<Integer, Integer> varbits
Map<Integer, Integer> varplayers
```

as normal profile-source fields.

The whole purpose of this change is to prevent canonical accounts from being specified in terms of derived implementation state.

Profile definitions should say things like:

```java
.completedQuest(Quest.DRAGON_SLAYER_I)
.diary(Diary.KARAMJA, DiaryTier.MEDIUM)
.unlock(PermanentUnlock.MUSEUM_KUDOS_153)
.hotAirBalloonDestination(...)
.plantedSpiritTree(...)
```

not:

```java
.varbit(1234, 7)
```

If migration discovers a current fixture value which cannot be described semantically, stop and classify it.

Either:

1. introduce an appropriately named semantic concept; or
2. determine that it is runtime/transient state and omit/model it under `RuntimeSpec`; or
3. document it as unresolved.

Do not solve migration mismatches by adding a generic raw-var escape hatch.

# 6. Use RuneLite types in the profile definitions

Use RuneLite's enums/constants wherever they represent the semantic concept directly.

Examples:

```java
Skill.AGILITY
Skill.MAGIC
Skill.CONSTRUCTION

Quest.DRAGON_SLAYER_I
Quest.SONG_OF_THE_ELVES
Quest.MONKEY_MADNESS_II

ItemID.LAW_RUNE
ItemID.AIR_RUNE
...
```

Do not author:

```java
"Dragon Slayer I"
"AGILITY"
556
4458
```

where RuneLite already provides an appropriate symbolic identifier.

Rendering may convert those values into the existing language-neutral JSON representation.

# 7. Define corpus-specific semantic enums only where RuneLite has no such concept

Some routing state is more specialised than RuneLite's generic account APIs.

Introduce small corpus-owned semantic enums/types where required.

Port the concepts already established in Haskell `ShortestPath.AccountSemantics`, including:

```text
QuestMilestone
QuetzalPlatform
HotAirBalloonDestination
CatacombsEntrance
PermanentUnlock
PlantedSpiritTree
```

Examples of legitimate semantic concepts include:

```text
LandOfTheGoblinsYuBiuskAccess
SinsOfTheFatherSlepeBoatAccess

CamTorum
ColossalWyrmRemains
OuterFortis
FortisColosseum
SalvagerOverlook
Kastori

BalloonEntrana
BalloonTaverley
BalloonCastleWars
BalloonGrandTree
BalloonCraftingGuild
BalloonVarrock

CatacombsForthosDungeon
CatacombsSurfaceEntrances
CatacombsGiantsDen
```

and the existing durable unlocks currently represented by the Haskell `PermanentUnlock` type.

Do not collapse these back to named varbits.

# 8. Port the current Haskell semantic compiler

Use the current:

```text
shortest-path-model/src/ShortestPath/AccountSemantics.hs
```

as the migration specification.

Port its semantic derivations to Java.

This includes, where still relevant:

```text
quest-derived varbits
quest-derived varplayers
quest milestones
diary completion varbits
diary-derived secondary varbits
Quetzal platform bitmask
hot-air-balloon unlock state
Catacombs entrance state
permanent unlock state
POH location state
spellbook state
POH teleport mode
minigame teleport cooldown state
default routing-relevant variables
```

Do not mechanically copy numeric IDs from Haskell if RuneLite now supplies a named constant.

Replace constructs such as:

```haskell
VarbitId 4458
```

with the corresponding RuneLite symbolic constant.

If RuneLite genuinely has no symbolic constant for a required ID, define the numeric compatibility constant in **one central Java location**, with a comment explaining what it represents and where it came from.

Do not scatter magic varbit IDs through profile/compiler code.

# 9. Prefer RuneLite quest metadata where it is authoritative

Where RuneLite's `Quest` metadata provides the variable and completion-state semantics required to represent ordinary quest completion, use that rather than maintaining a second hand-written quest-completion table.

However, distinguish:

```text
quest completed
```

from:

```text
a specific intermediate/permanent unlock reached during that quest
```

For example, a transport may require a quest milestone which is not equivalent to the quest's normal completion value.

Those should remain explicit semantic `QuestMilestone` rules.

Do not infer arbitrary milestone values simply from quest completion.

# 10. Compile diaries semantically

Profile definitions should use:

```java
diary(Diary.ARDOUGNE, DiaryTier.HARD)
```

or equivalent.

Define the corpus diary enum if RuneLite does not expose a suitable one:

```text
Ardougne
Desert
Falador
Fremennik
Kandarin
Karamja
KourendKebos
LumbridgeDraynor
Morytania
Varrock
WesternProvinces
Wilderness
```

with tiers:

```text
NONE
EASY
MEDIUM
HARD
ELITE
```

The compiler should derive all relevant RuneLite diary-completion varbits.

Do not make each profile enumerate four diary varbits itself.

Retain secondary diary-derived state currently compiled by Haskell, e.g. routing-relevant reward/unlock variables.

# 11. Compile runtime state using the synthetic benchmark clock

Keep:

```text
benchmarkNowMinutes = 100000000
```

as a single root-level fixture/compiler constant for v1.

Do not derive benchmark cooldown state from wall-clock time.

Represent minigame teleport state semantically:

```java
MinigameTeleportState.ready()
```

or:

```java
MinigameTeleportState.usedAt(minutes)
```

For a ready cooldown, render/derive the same concrete state currently produced by Haskell:

```text
benchmarkNowMinutes - 21
```

where required by the transport variable representation.

This must preserve the synthetic-clock fix which prevents Java and Haskell benchmarks from disagreeing based on real current time.

Never call:

```java
Instant.now()
System.currentTimeMillis()
```

to compile canonical profiles.

# 12. Keep default game state in the compiler, not in every profile

The current Haskell compiler defines several routing-relevant variables with a normal default value, for example teleport-destination toggles and related state.

Port those to a central compiler rule such as:

```java
RoutingGameStateDefaults
```

or equivalent.

Do not make all four profiles spell out:

```text
some toggle = 0
another toggle = 0
...
```

These are properties of how the canonical account model interprets unspecified normal game state, not meaningful progression differences between Early/Mid/End/Maxed.

# 13. Detect conflicting semantic derivations

Port the existing Haskell `mergeCompiledVars` safety property.

When two semantic rules assign the same varbit/varplayer:

```text
same value
    -> okay

different values
    -> generator failure
```

The error should identify:

* variable symbolic name;
* numeric ID;
* old value;
* new value;
* semantic source of each assignment.

For example:

```text
Conflicting VARBIT SOME_NAME (1234):
  quest Dragon Slayer I -> 1
  permanent unlock X    -> 2
```

Do not silently use last-write-wins.

# 14. Define items semantically using RuneLite ItemID constants

Profile source should describe actual owned items using RuneLite item constants.

For example:

```java
inventory(ItemID.LAW_RUNE, 100)
bank(ItemID.AIR_RUNE, 10000)
equipment(ItemID.STAFF_OF_AIR, 1)
```

Avoid profile-source strings such as:

```text
"AIR_RUNE"
"AXE"
```

when what the account really owns is a concrete item.

For the initial migration, preserve the **compiled account contents** of the existing fixture.

If the existing fixture uses one of the Haskell routing item aliases, inspect:

```text
ShortestPath.Items.routingItemVariations
```

and use the concrete item ID currently selected by the fixture loader unless there is a deliberate semantic correction.

Do not opportunistically upgrade an Early account from one item variant to a better one.

## JSON item representation

The JSON v1 schema represents item-count maps using string keys.

Prefer rendering concrete item IDs as decimal strings, e.g.:

```json
{
  "556": 10000,
  "563": 1000
}
```

rather than Java constant names.

Before adopting this representation, verify all current Haskell and Java corpus consumers accept numeric string item IDs.

The Haskell loader currently does.

If another consumer does not, update that consumer in the same migration or retain the existing output representation temporarily.

Do not bump the fixture format merely for cosmetic item-key changes.

# 15. Define POH state semantically

Create a `PohSpec` equivalent to the current model:

```text
location
jewellery box tier
portal access
fairy ring
spirit tree
obelisk
mounted glory
mounted Xeric's talisman
mounted digsite pendant
mounted mythical cape
```

Use semantic enum values such as:

```text
RIMMINGTON
TAVERLEY
POLLNIVNEACH
RELLEKKA
BRIMHAVEN
YANILLE
PRIFDDINAS
HOSIDIUS
ALDARIN
```

and:

```text
NO_JEWELLERY_BOX
FANCY_JEWELLERY_BOX
ORNATE_JEWELLERY_BOX
```

The compiler must derive any required low-level POH varbits from this `PohSpec`, including house location.

The renderer must also preserve the existing explicit `poh` JSON object consumed by pathfinders.

This duplication is intentional:

```text
semantic fixture POH data
+
derived routing varbits
```

must agree because some transports are represented through structured POH capabilities and some through RuneLite variable requirements.

# 16. Define planted spirit trees semantically

Keep planted spirit trees as explicit semantic profile state.

The profile definitions should encode the intended progression:

```text
early:
    none

mid:
    Farming Guild

end:
    Farming Guild
    Port Sarim

maxed:
    all currently modelled planted spirit trees
```

The currently modelled set is:

```text
Farming Guild
Port Sarim
Etceteria
Brimhaven
Hosidius
```

Use a corpus enum such as:

```java
PlantedSpiritTree.FARMING_GUILD
```

rather than varbits.

Render the existing canonical JSON names expected by consumers:

```text
FARMING_GUILD
PORT_SARIM
ETCETERIA
BRIMHAVEN
HOSIDIUS
```

# 17. Profiles must describe realistic semantic states

Define exactly four canonical profiles:

```text
early
mid
end
maxed
```

The existing `account-profiles-v1.json` is the migration baseline.

Do not invent a new progression model in this task.

Translate each current profile into semantic Java declarations while preserving its intended account capabilities.

In particular:

* preserve levels;
* preserve completed quests;
* preserve diaries;
* preserve inventory/equipment/rune pouch/bank;
* preserve POH capabilities;
* preserve spellbook/runtime state;
* preserve planted spirit trees;
* preserve Fairy Ring access;
* preserve durable unlocks.

`maxed` does **not** mean:

```text
set every observed transport varbit to an enabling value
```

Some state is:

* mutually exclusive;
* transient;
* location/configuration-dependent;
* special-mode-only;
* consumable;
* intentionally unmodelled.

Only derive variables justified by the semantic account description.

# 18. Preserve intentionally unmodelled state

Use the existing Haskell audit/documentation as the migration guide for variables intentionally excluded from static profiles.

Examples include current classifications such as:

```text
KARAM_DUNGEON_ENTRYFEE
    runtime/visit state

VEOS_MEMOIR_CHARGES
    consumable runtime state

TAPOYAUIK_RUINS_FAILED_WALLSLIDE
TAPOYAUIK_FAILED_STEPPING_STONES
    unresolved persistence semantics

LEAGUE_COMBAT_MASTERY_PATHS
    special mode

HAUNTED
    semantics unresolved
```

Do not assign convenient values merely to make transports available.

Where future investigation establishes durable semantics, add a named semantic concept at that point.

# 19. Profile definitions should be readable without understanding varbits

Create one obvious source file/class which answers:

> What is the Early/Mid/End/Maxed account?

For example:

```text
CanonicalProfiles.java
```

A reviewer should be able to read it and see approximately:

```java
static ProfileSpec mid()
{
    return profile("mid")
        .levels(...)
        .completedQuests(...)
        .diary(...)
        .inventory(...)
        .bank(...)
        .poh(...)
        .plantedSpiritTrees(FARMING_GUILD)
        .fairyRingsUnlocked(true)
        .unlock(...)
        .runtime(...);
}
```

Do not scatter the four profile definitions among varbit compiler classes.

The semantic declarations are the source of truth.

# 20. Do not model progression by mutating the previous profile

Avoid opaque code like:

```java
ProfileSpec mid = early.copy()
    .set(...)
    .remove(...)
    .mutate(...);
```

which makes it difficult to inspect what `mid` actually represents.

Shared helper definitions are fine, for example:

```java
standardMidLevels()
midGameQuestSet()
```

and genuinely monotonic semantic set composition may be used where it improves clarity.

But each profile should remain reviewable as a complete semantic state.

Prefer declarative composition over mutation.

# 21. Render deterministic JSON

Implement a dedicated renderer, e.g.:

```text
ProfileJsonRenderer
```

Use a normal JSON library such as Jackson or the library already standard in the project.

Output must be deterministic.

Requirements:

* fixed top-level property order;
* profiles ordered:

  * `early`
  * `mid`
  * `end`
  * `maxed`;
* map keys deterministically sorted;
* completed quests sorted deterministically;
* planted spirit trees sorted in a stable defined order;
* portal destinations sorted;
* no timestamps/build host information;
* stable indentation;
* newline at EOF.

For numeric varbit/varplayer maps, sort keys numerically, not lexicographically if practical.

Do not allow the output to change because of `HashMap` iteration order.

# 22. Keep compiled fixture DTOs simple

The low-level renderer model may look approximately like:

```java
final class CompiledProfile
{
    Map<String, Integer> levels;
    List<String> completedQuests;

    Map<Integer, Integer> varbits;
    Map<Integer, Integer> varplayers;

    Map<String, Integer> inventory;
    Map<String, Integer> equipment;
    Map<String, Integer> runePouch;
    Map<String, Integer> bank;

    Map<String, String> diaries;

    PohFixture poh;
    List<String> plantedSpiritTrees;
    boolean fairyRingsUnlocked;

    RuntimeFixture runtime;
}
```

This is a rendering structure, not the profile source of truth.

Do not leak this DTO back into `CanonicalProfiles`.

# 23. Render RuneLite quest names carefully

The JSON `completedQuests` field is consumed as textual quest names by the routing implementations.

Derive these names from the RuneLite `Quest` enum using its canonical display name where available.

Do not maintain a second manually typed list such as:

```java
"Dragon Slayer I"
```

next to:

```java
Quest.DRAGON_SLAYER_I
```

Add a test that every rendered quest name is unique and non-empty.

Preserve the exact naming convention expected by transport quest requirements.

# 24. Handle skills similarly

Use RuneLite `Skill` in the semantic source.

The renderer should convert it to the stable skill names expected by the canonical fixture/transport requirements.

Do not include pseudo-skills such as overall/total level unless the current schema requires them.

Validate levels against normal RuneLite bounds where appropriate, but do not impose a false cap on skills for which RuneLite allows virtual/current values beyond 99 if the benchmark model intentionally permits them.

# 25. Add generation and verification tasks

Provide simple commands from the corpus repository.

For example:

```sh
./profile-generator/gradlew \
  -p profile-generator \
  generateAccountProfiles
```

which writes:

```text
accounts/account-profiles-v1.json
```

Also provide:

```sh
./profile-generator/gradlew \
  -p profile-generator \
  verifyAccountProfiles
```

which:

1. renders profiles to a temporary file/in-memory representation;
2. compares them with the checked-in fixture;
3. exits non-zero if they differ;
4. prints a useful diff/path to the generated candidate.

Do not have `verifyAccountProfiles` silently rewrite the checked-in file.

A direct CLI is also fine:

```text
account-profile-generator generate [--output PATH]
account-profile-generator verify
```

but Gradle tasks should remain convenient.

# 26. Migration must start from the existing fixture

Before replacing the checked-in JSON:

1. load the existing `account-profiles-v1.json`;
2. implement semantic definitions for all four profiles;
3. compile them;
4. compare generated output to the existing fixture.

The initial goal is **semantic parity**, not profile redesign.

Prefer exact normalized fixture equality.

If formatting/map ordering differs, compare parsed normalized JSON.

If concrete item representation changes from aliases to numeric IDs, compare the resulting compiled account states rather than textual keys.

For every substantive difference in:

```text
levels
quests
varbits
varplayers
items
diaries
POH
spirit trees
runtime
```

either:

* fix the generator; or
* identify an actual bug in the old fixture and explicitly document the intentional correction.

Do not accept unexplained differences.

# 27. Cross-check against the current Haskell semantic compiler

During migration, compare Java output against:

```text
shortest-path-model/src/ShortestPath/AccountSemantics.hs
```

for all currently implemented semantic mappings.

The Java implementation should replace hard-coded numeric IDs with RuneLite constants where possible, but should produce equivalent concrete state.

Specifically compare:

```text
compileProgressionVars
compileDiaryDerivedVarbits
compileQuestDerivedVarbits
compileQuestDerivedVarPlayers
compileHotAirBalloonVars
compileCatacombsEntranceVars
compilePermanentUnlockVars
compileQuetzalVars
compileDefaultVars
compileRuntimeVars
compilePohVars
```

Do not delete or modify the Haskell implementation as part of this task.

Once parity is established, removal of Haskell profile-authoring machinery can be a separate cleanup task.

# 28. Cross-language consumer verification

After generating the fixture, verify it with both existing consumers.

At minimum:

### Haskell

From `shortest-path-model`:

```sh
cabal test all
```

and/or the relevant account-profile / benchmark-profile tests.

Run a small `route-bench` smoke tier to establish that all four profiles load.

### Java

Run the canonical account/profile loader tests in:

```text
shortest-path-tooling
```

or the current Java benchmark project.

Verify all four profiles compile successfully.

Do not change the fixture until both language consumers accept it.

# 29. Test the compiler independently

Add unit tests for the semantic compiler.

At minimum:

### Quests

A completed quest should:

* appear in `completedQuests`;
* derive the appropriate routing vars where applicable.

### Quest milestones

A milestone should derive its special routing state without pretending the entire quest is complete unless that is actually implied.

### Diaries

For each tier:

```text
NONE
EASY
MEDIUM
HARD
ELITE
```

verify cumulative completion bits are correct.

### Quetzal platforms

Verify the bitmask for individual and combined platforms.

### Balloons / Catacombs / permanent unlocks

Verify representative semantic unlocks derive the expected RuneLite variables.

### POH

Verify every POH location maps correctly and structured POH state agrees with derived location state.

### Runtime

Verify:

```text
READY at benchmarkNowMinutes = 100000000
```

compiles to the expected synthetic cooldown state.

Verify `usedAt` preserves the specified synthetic timestamp.

### Conflicts

Construct two semantic sources which assign incompatible values to one variable and verify compilation fails.

### Determinism

Compile/render the same profiles multiple times and assert byte-identical output.

# 30. Add profile-specific regression tests

Add tests for the intended high-level profile characteristics.

At minimum:

```text
early planted spirit trees
    = none

mid
    = Farming Guild

end
    = Farming Guild + Port Sarim

maxed
    = Farming Guild + Port Sarim + Etceteria + Brimhaven + Hosidius
```

Add equivalent checks for important intended distinctions already encoded in the existing fixture, such as:

* quest progression;
* skill progression;
* diary progression;
* POH facilities;
* spellbook/runtime state;
* bank/inventory capabilities.

Do not invent new distinctions merely for test coverage.

# 31. The generated fixture is checked in

Continue committing:

```text
accounts/account-profiles-v1.json
```

Consumers must not need Java/RuneLite/Gradle merely to use the corpus.

The workflow is:

```text
edit semantic profile source
        ↓
run generator
        ↓
review generated fixture diff
        ↓
run cross-language tests
        ↓
commit source + generated fixture together
```

# 32. Add CI reproducibility checking

Add corpus CI that runs:

```text
test
verifyAccountProfiles
```

or equivalent.

A PR must fail if:

```text
semantic Java source
```

would generate different canonical JSON from the checked-in:

```text
accounts/account-profiles-v1.json
```

This makes generated-data drift impossible.

Do not require Haskell to generate the fixture.

# 33. Record the RuneLite dependency/revision

Because semantic generation depends on RuneLite identifiers, use the same
`latest.release` RuneLite dependency declaration as the adjacent Java
pathfinder project.

The dependency declaration should remain visible in the generator build file.

Also expose it in either:

```text
profile-generator/README.md
```

or a small generated metadata field/file if appropriate.

Do not add a changing current-time build field to the JSON.

A RuneLite upgrade which changes relevant IDs should produce a reviewable generated fixture diff.

# 34. Update corpus documentation

Document:

```text
accounts/account-profiles-v1.json
    generated canonical interchange fixture

profile-generator/
    semantic source of truth
```

Explain that profile authors should edit:

```text
CanonicalProfiles.java
```

or its equivalent, not the JSON directly.

Document:

```sh
generateAccountProfiles
verifyAccountProfiles
```

and the cross-language verification expectations.

Make clear that general world-facts, endpoint-refinement and corpus-maintenance tooling does **not** belong here merely because this generator does.

The reason this code belongs here is specifically:

> it defines canonical corpus account data.

# 35. Non-goals

Do not:

* move world-facts tooling into the corpus repo;
* move endpoint refinement here;
* depend on the Haskell model;
* build a generic RuneLite account-export system;
* connect to a live RuneLite client;
* read the user's real account;
* infer profiles from live game state;
* generate benchmark routes;
* redesign the profile JSON schema;
* change benchmark-time semantics;
* use wall-clock time;
* set every known varbit on `maxed`;
* add raw varbit setters to make migration easy;
* delete the Haskell account semantic compiler yet;
* change routing semantics in Haskell or Java.

# Acceptance criteria

The task is complete when:

1. `shortest-path-corpus` contains a small Java profile-generator subproject.
2. It uses the same `latest.release` RuneLite API dependency declaration as the Java pathfinding ecosystem.
3. `CanonicalProfiles` or equivalent defines `early`, `mid`, `end`, and `maxed` semantically.
4. Profile definitions use RuneLite `Quest`, `Skill`, `ItemID`, etc. rather than raw identifiers.
5. There is no general raw-varbit/raw-varplayer authoring API.
6. Existing Haskell semantic mappings have been ported to a Java semantic compiler.
7. Compiler mappings use RuneLite `VarbitID` / `VarPlayerID` constants wherever available.
8. Conflicting semantic derivations fail loudly.
9. Runtime/minigame cooldown state uses `benchmarkNowMinutes`, never wall clock.
10. POH, diaries, planted spirit trees and permanent unlocks are represented semantically.
11. The planted spirit tree progression is:

    * early: none;
    * mid: Farming Guild;
    * end: Farming Guild + Port Sarim;
    * maxed: all five currently modelled planted trees.
12. Rendering is deterministic.
13. The existing v1 JSON contract remains consumable by both Haskell and Java.
14. Generated profiles have been compared against the previous fixture with every substantive difference explained.
15. Haskell profile loading/tests pass.
16. Java profile loading/tests pass.
17. The canonical generated JSON remains checked into Git.
18. CI fails when generated output and checked-in output drift.

# Final report

Report:

1. Java project structure added;
2. RuneLite dependency/revision selected and where it came from;
3. semantic profile model;
4. semantic unlock types introduced;
5. Haskell `AccountSemantics` rules ported;
6. any RuneLite variables which required numeric compatibility constants and why;
7. old fixture vs generated fixture differences;
8. whether any differences were deliberate bug fixes;
9. Haskell verification results;
10. Java verification results;
11. generator and verification commands;
12. final `git status`;
13. any remaining state which could not be modelled semantically.

Do not proceed to remove the Haskell profile-generation code until this generated fixture has been proven equivalent across both implementations.
