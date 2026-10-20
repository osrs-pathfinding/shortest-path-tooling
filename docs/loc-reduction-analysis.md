# Shortest-Path Source Token-Reduction Analysis

**Date:** 2026-10-20
**Analysed commit:** upstream `bdae43dbc5fce2cd05d1908460e110104ab13579` (master tip, "Correct transport endpoints and enforce reachable origins (#826)")
**Scope:** `src/main/java` only — 158 files, 39,448 LOC

## Methodology

RuneLite's automated plugin review rejects plugins whose counted source exceeds ~200k tokens; this plugin was reported at 208k. The counting rule stated by the reviewer: **only `src/main/java` code counts** — comments, javadoc, `src/test`, `src/main/resources`, and the readme are all excluded. Deleting comments or splitting/moving files therefore gains nothing; only removing or restructuring *code* counts.

The tip under analysis was extracted read-only (`git archive` → `/tmp` scratch tree); the submodule worktree was never checked out or modified. The checked-out Java tree already equals the tip: `git diff 45f74e76 bdae43db -- src/main/java` is empty (the 4-commit delta is transport TSVs, `collision-map.zip`, tests, and a workflow file).

Estimator (exact command run, from the extraction root):

```bash
find src/main/java -name '*.java' -exec cat {} + \
  | grep -vE '^\s*(//|\*|/\*)' | grep -vE '^\s*$' | wc -c
```

This strips full-line `//`, `/*`, and `*` comment/javadoc lines plus blank lines — a close approximation of "code only" that matches the reviewer's rule. chars/4 is the standard rough token estimate.

| Measure | Chars | Est. tokens (chars/4) |
|---|---:|---:|
| Raw `src/main/java` (everything) | 1,283,688 | ~320,922 |
| Comment/blank-stripped (working estimate) | 925,352 | **~231,338** |

**The stale 208k figure is superseded:** measured working estimate is **~231k tokens**, consistent with the reported ~240k current-tip claim (a real tokenizer counts differently than chars/4, but the conclusion is the same: the plugin is ~30–40k tokens over the line, not ~8k). A `tiktoken` cross-check was not possible (module not installed locally; no installs performed). All savings figures below are chars/4-derived and stated with the same estimator so ratios stay comparable.

## Token inventory

Per-directory stripped-char totals (sorted desc; `root` = the 19 `shortestpath/*.java` files):

| Directory | Files | Stripped chars | Est. tokens | Share |
|---|---:|---:|---:|---:|
| `pathfinder/` (all backends) | 60 | 361,531 | ~90,383 | 39.1% |
| `root` (`shortestpath/*.java`) | 19 | 171,900 | ~42,975 | 18.6% |
| `requirement/` | 17 | 83,803 | ~20,951 | 9.1% |
| `overlay/` | 10 | 74,030 | ~18,508 | 8.0% |
| `settings/` | 6 | 54,985 | ~13,746 | 5.9% |
| `transport/` | 14 | 54,852 | ~13,713 | 5.9% |
| `scheduler/` | 7 | 25,217 | ~6,304 | 2.7% |
| `api/` | 3 | 21,697 | ~5,424 | 2.3% |
| `poh/` | 5 | 19,887 | ~4,972 | 2.1% |
| `presentation/` | 2 | 15,504 | ~3,876 | 1.7% |
| `spirittree/` | 3 | 11,593 | ~2,898 | 1.3% |
| `widgets/` | 2 | 10,245 | ~2,561 | 1.1% |
| `items/` | 3 | 8,306 | ~2,077 | 0.9% |
| `sailing/` (root pkg) | 3 | 6,566 | ~1,642 | 0.7% |
| `leagues/` | 4 | 5,236 | ~1,309 | 0.6% |

Within `pathfinder/`: `astar/` = 203,608 chars (~50,902 tok, 56% of the package), `sailing/` = 33,687 (~8,422), legacy/shared root files = 124,236 (~31,059).

Top 30 files (stripped chars; share of the 925,352-char total):

| # | File | LOC | Stripped chars | Est. tok | Share |
|--:|---|---:|---:|---:|---:|
| 1 | `pathfinder/astar/AStarForwardSearch.java` | 1036 | 44,802 | 11,200 | 4.8% |
| 2 | `ShortestPathConfig.java` | 1493 | 34,930 | 8,732 | 3.8% |
| 3 | `pathfinder/astar/RoutingStaticBuilder.java` | 1224 | 34,548 | 8,637 | 3.7% |
| 4 | `overlay/PathTileOverlay.java` | 961 | 28,355 | 7,088 | 3.1% |
| 5 | `ShortestPathPanel.java` | 1165 | 27,922 | 6,980 | 3.0% |
| 6 | `pathfinder/PathfinderConfig.java` | 1030 | 25,492 | 6,373 | 2.8% |
| 7 | `ItemVariations.java` | 849 | 23,215 | 5,803 | 2.5% |
| 8 | `settings/EffectiveConfig.java` | 848 | 22,707 | 5,676 | 2.5% |
| 9 | `transport/ResourceLoader.java` | 735 | 17,288 | 4,322 | 1.9% |
| 10 | `RestrictionListPanel.java` | 769 | 16,668 | 4,167 | 1.8% |
| 11 | `pathfinder/astar/AStarWalkCanonicalizer.java` | 627 | 16,295 | 4,073 | 1.8% |
| 12 | `pathfinder/AStarPathfinder.java` | 534 | 16,237 | 4,059 | 1.8% |
| 13 | `requirement/TransportEligibility.java` | 695 | 15,862 | 3,965 | 1.7% |
| 14 | `settings/Settings.java` | 789 | 15,803 | 3,950 | 1.7% |
| 15 | `settings/ConfigKey.java` | 222 | 15,238 | 3,809 | 1.6% |
| 16 | `ShortestPathHomePanel.java` | 657 | 15,009 | 3,752 | 1.6% |
| 17 | `scheduler/PathScheduler.java` | 630 | 14,198 | 3,549 | 1.5% |
| 18 | `transport/Transport.java` | 624 | 13,954 | 3,488 | 1.5% |
| 19 | `poh/PohService.java` | 645 | 13,795 | 3,448 | 1.5% |
| 20 | `ShortestPathPlugin.java` | 570 | 13,632 | 3,408 | 1.5% |
| 21 | `requirement/Requirements.java` | 594 | 13,316 | 3,329 | 1.4% |
| 22 | `requirement/TeleportRestriction.java` | 683 | 12,881 | 3,220 | 1.4% |
| 23 | `presentation/PathAnnotator.java` | 603 | 12,744 | 3,186 | 1.4% |
| 24 | `pathfinder/astar/PreparedRoutingAccount.java` | 433 | 12,674 | 3,168 | 1.4% |
| 25 | `pathfinder/sailing/SailingPathfinder.java` | 542 | 12,400 | 3,100 | 1.3% |
| 26 | `pathfinder/astar/RoutingStatic.java` | 347 | 12,081 | 3,020 | 1.3% |
| 27 | `overlay/PathMapOverlay.java` | 377 | 11,657 | 2,914 | 1.3% |
| 28 | `api/PluginMessageHandler.java` | 356 | 11,060 | 2,765 | 1.2% |
| 29 | `spirittree/SpiritTreeService.java` | 484 | 11,004 | 2,751 | 1.2% |
| 30 | `pathfinder/astar/PreparedHeuristic.java` | 354 | 10,918 | 2,729 | 1.2% |

**Concentration:** the top 10 files hold 275,927 stripped chars = **29.8% of the counted total**; the top 30 hold ~57%. The codebase is broad, not lumpy — there is no single file whose restructuring alone clears the bar.

## Ranked opportunities

Ranked by estimated token savings (chars/4, same estimator). "Evidence" cites `src/main/java/shortestpath/…` paths at `bdae43db`. Effort: S/M/L. Acceptability judges whether Skretzo would plausibly merge it as a behaviour-preserving PR (no reflection, no new dependencies, Java 11).

| Rank | Opportunity | Category | Est. savings | Evidence | Effort | Upstream-acceptability |
|---:|---|---|---:|---|---|---|
| 1 | Generate the mechanical config surface from a compact spec (EffectiveConfig + ConfigKey + Settings snapshot block → build-time codegen) | codegen | ~9,500 | `settings/EffectiveConfig.java:272`, `settings/ConfigKey.java:38-146` | L | Conditional — adds a build step; needs maintainer buy-in |
| 2 | `ItemVariations` enum → `item_variations.tsv` resource + loader | data extraction | ~5,300 | `ItemVariations.java:5-849` | M | Good — matches existing TSV-resource convention |
| 3 | Collapse 94 canonical EffectiveConfig overrides to one-line delegations (non-codegen alternative to part of #1) | boilerplate | ~2,300 | `settings/EffectiveConfig.java:272-278` | S | High — obvious mechanical simplification |
| 4 | Replace trivial hand-written getters with Lombok `@Accessors(fluent=true) @Getter` | boilerplate | ~2,200 | 163 methods tree-wide, e.g. `requirement/RoutingPolicy.java`, `transport/TransportType.java` | S | High — Lombok already a compile dep; `TileCounter`/`TileStyle` precedent |
| 5 | Shorten `ShortestPathConfig` `name`/`description` strings | verbosity (UX-visible) | ~1,700 | `ShortestPathConfig.java` — 115 name strings (2,172 c), 115 descriptions (6,788 c) | S | Borderline — changes user-visible config text |
| 6 | Extract the legacy/A* walking-core shared by `SailingPathfinder` and `Pathfinder` | dedup | ~1,300 | `pathfinder/sailing/SailingPathfinder.java` (130 shared 6-line windows, ~37% of the file) | M-L | Medium — touches pathfinding core |
| 7 | Shared base for the three highlight overlays | dedup | ~1,200 | `overlay/{BankItem,Inventory,Spellbook}HighlightOverlay.java` — 8+ windows common to all three | M | Good — `AbstractHighlightOverlay` already exists |
| 8 | POH data enums → resources (`PohNexusPortal`, `PohService.NAME_ALIASES`, `PohMountedItem`) | data extraction | ~1,000 | `poh/PohNexusPortal.java:8-` (44 rows), `poh/PohService.java:207-233` | S | Good — same TSV pattern |
| 9 | Consolidate panel row/section builders (`CollapsibleEnumGroup`, `TransportFamilyCard`, `RestrictionListPanel`, `CategoryPanel`, `ShortestPathHomePanel`) | dedup | ~1,000 | 18 shared windows `CollapsibleEnumGroup↔TransportFamilyCard`, 13 `CategoryPanel↔ShortestPathHomePanel` | M | Good — Swing-only, low runtime risk |
| 10 | Dedup the map-overlay trio | dedup | ~900 | `overlay/{PathMap,PathTile,PathMinimap}Overlay.java` — 6+4+2 shared windows | M | Good |
| 11 | Collapse `AStarForwardSearch`'s telescoping `search(...)` overloads into an options parameter | verbosity | ~600 | `pathfinder/astar/AStarForwardSearch.java:29-86` (7 overloads, ~1.8k chars) | S | High |

### 1. Build-time codegen of the config surface (~9.5k tokens)

The config surface redeclares each of ~110 logical options across at least four files:

- `ShortestPathConfig` — `@ConfigItem` interface method with `keyName`/`name`/`description`/`section`/`position` (~298 chars median per item; 110 items ≈ 33.9k chars). This file is **irreducible**: RuneLite's `ConfigManager` requires a concrete annotated interface, and Java 11 has no source-level way to declare 110 items more compactly.
- `ConfigKey` — enum entry per key (`settings/ConfigKey.java:38-146`, 108 entries, 15,238 chars total).
- `EffectiveConfig` — a hand-written `@Override` per item: `Object value = overrides.get(ConfigKey.X.getKey()); return value instanceof T ? … : base.x();` (94 methods in the canonical ~201-char shape, e.g. `EffectiveConfig.java:272-278`; the file is 22,707 chars and ~98 of its 110 methods are this shape).
- `Settings`/`PathfinderConfig` — field + constructor/refresh assignment per subscribed option (`Settings.java:770-790`, `PathfinderConfig.java:437-`).

Everything except the `ShortestPathConfig` interface is mechanical. A small generator (a Java class under `buildSrc/` or a Gradle task source — neither lives in `src/main/java`, so neither is counted) driven by a compact spec table (key, type, coercion, effects — roughly what each `ConfigKey` row already is, in TSV) can emit `EffectiveConfig`, `ConfigKey`, and the `Settings` snapshot block into `build/generated/sources`, wired via `sourceSets.main.java.srcDir`. Behaviour-identical output is verifiable by diffing generated files against the originals before deletion.

- **Counted:** −~38k chars of `src/main/java`, replaced by a spec + generator outside the counted tree ≈ **~9.5k tokens net** (spec text is in `src/main/resources` or build config — uncounted).
- **Tradeoff:** a code-generation step in a community plugin is a real readability/maintenance change; Skretzo may reasonably prefer explicit Java. Also splits "where is this defined" across spec and generated output. This is the single biggest lever available short of feature removal — recommend sounding out the maintainer before building it.
- **Partial variant:** generating `EffectiveConfig` alone saves ~5k tokens.

### 2. `ItemVariations` → resource (~5.3k tokens)

`ItemVariations.java` is 849 lines: 60 enum constants listing ~739 `ItemID.X` references, plus two lookup switches (`staves`, `offhands`) and `fromName` (`ItemVariations.java:7`, `~800-849`). Call sites are thin: `ItemRequirementParser.java:117-123`, `TransportEligibility.java:59`, `InventoryHighlightOverlay.java:95` — they need `getIds()`, `fromName`, `staves`, `offhands`, all trivially served by a parsed table.

The plugin already loads TSV data the same way (`transport/ResourceLoader.java:78`, `:220` — `getResourceAsStream` + `Util.readAllBytes`); a `item_variations.tsv` with columns `NAME \t STAFF \t OFFHAND \t ItemID…` plus a ~50-line loader is drop-in.

- **The ItemID-constants-class problem, resolved:** `net.runelite.api.gameval.ItemID` is a `static final int` constants class, not an enum — there is no `valueOf`, and RuneLite forbids reflection. Two honest options: **(a) store raw ints** in the TSV — loses compile-time name checking entirely (a mistyped id silently resolves to a wrong item); **(b) store symbolic names plus a generated `name→id` properties resource**, produced once per release by a tiny build-side generator that reads `ItemID.class` values (the generator itself can use reflection freely — it runs at build time, not inside the client). Option (b) preserves name-level correctness at data-generation time and fails loudly on a renamed constant; it costs one extra generated resource. Recommend (b) — compile-safety is replaced by build-time safety rather than abandoned.
- **Counted:** −23,215 chars, +~1.5–2k-char loader ≈ **~5,300 tokens**.
- **Tradeoff:** names-in-resource lose javac checking (mitigated by (b)); data edits move from Java to TSV — consistent with how transports/destinations already work upstream.

### 3. EffectiveConfig one-line delegation (~2.3k tokens; low-risk subset of #1)

If codegen is rejected, the same file collapses manually: a private typed helper (`private <T> T override(ConfigKey key, T fallback)`) reduces each of the 94 canonical overrides from ~201 chars (`@Override` + signature + braces + `Object value = …` + `instanceof` cast) to a signature + one return (~100 chars). Non-canonical overrides (enum-coerced getters like `showTileCounter`, `EffectiveConfig.java:303+`) stay hand-written. Behaviour-identical; purely mechanical; ~2.3k tokens. Cheap enough to land as its own PR regardless of the codegen decision.

### 4. Lombok getters (~2.2k tokens)

163 methods tree-wide are the trivial three-line shape `public T name() { return field; }` ≈ 9,781 chars (heaviest: `RoutingPolicy` 13, `TransportType` 10, `RoutingStatic` 9, `ReverseLabels` 10, `Settings` 9). Lombok is already a `compileOnly` + `annotationProcessor` dep (`build.gradle:24-25`) and already used (`TileCounter.java:3`, `TileStyle.java:3`, `ShortestPathPlugin.java:7`). `@Accessors(fluent = true) @Getter` on the class emits the same `name()` signatures, so call sites don't change. Where a class mixes trivial and non-trivial getters, annotate per-field. ≈ **~2,200 tokens**, effort S, low review risk — though it's ~2k tokens of diff noise, so it's best bundled with other config/settings edits.

### 5. Config description tightening (~1.7k tokens; borderline)

String literals **are** counted code. `ShortestPathConfig` carries 2,172 chars of `name` strings, 6,788 of `description`, 1,955 of `keyName` — 15,520 chars of literals total (~3.9k tok). Terse rewrite of the wordiest descriptions could recover ~1.5–2k tokens, but it edits user-facing config text — include only if the total still misses the line after structural work. `keyName`s are unchangeable (persisted config keys).

### 6. SailingPathfinder ↔ Pathfinder shared core (~1.3k tokens)

The mechanical window scan found **130 shared 6-line windows** between `pathfinder/Pathfinder.java` and `pathfinder/sailing/SailingPathfinder.java` — ~37% of SailingPathfinder's 387 code lines is a clone of the legacy engine's walking/cost bookkeeping. Extracting a shared package-private base (movement emission, cost accounting, visit bookkeeping) removes ~4.5–5k chars ≈ ~1.3k tokens. Medium effort and real care needed — this is pathfinding core; safe only with the scenario suite green. Note this is *dedup*, not backend removal: see the dual-backend verdict below.

### 7–10. Dedup clusters (overlays, panels) (~4.1k combined)

The scan found 242 normalized 6-line windows shared across ≥2 files. Beyond #6, the real clusters: the three highlight overlays share 8+ windows (a fuller `AbstractHighlightOverlay` — it exists at 2,153 chars and the trio still duplicates); the map-overlay trio shares ~12 windows (`PathMapOverlay`↔`PathTileOverlay` 6, `PathMapOverlay`↔`PathMapTooltipOverlay` 4, `PathMinimapOverlay`↔`PathTileOverlay` 2); the Swing panel helpers share ~40 windows across five files. Each extraction is a familiar move — pull the repeated block into the existing/parent abstraction. Combined ~4.1k tokens; individually small, but they're the safest M-effort work in the list.

### 8. POH data → resources (~1k tokens)

`PohNexusPortal` (44 string-tuple rows, ~4.4k chars) and `PohService.NAME_ALIASES` (20 `aliases.put` lines, `PohService.java:207-233`) are pure data in Java; `PohMountedItem` adds 4 more rows. Same TSV-plus-loader move as #2 with no ItemID problem (all plain strings). ~1k tokens, effort S.

### 11. AStarForwardSearch overloads (~0.6k)

Seven telescoping `search(...)` overloads (`AStarForwardSearch.java:29-86`, ~1.8k chars) forward through default arguments; a small package-private options value (Java 11 — a plain final class, not a record) collapses them to one method. ~0.6k tokens, effort S.

### Dual-backend verdict: both live — not dead code

`pathfinder/` carries 39% of counted tokens across **two** full routing engines, which makes "delete one" the obvious tempting move. Evidence says neither is dead:

- **LEGACY is the default backend** (`PathfinderConfig.java:279` `pathfinderBackend = PathfinderBackend.LEGACY`; `ShortestPathConfig.java:52-54` default `LEGACY`).
- **Plugin-message API queries always run the legacy engine** regardless of the user's backend choice — `PathScheduler.java:403` (`new Pathfinder(pathfinderConfig, start, targets)` inside `queryPath`), with an explicit comment that external callers get "the proven engine's routes regardless of the experiment the user has enabled." Removing legacy breaks the `api/` surface (`PluginMessageHandler`, 2.8k tok, depends on it).
- **ASTAR is live but opt-in**: instantiated only under `getPathfinderBackend() == ASTAR` (`PathScheduler.java:180-204`).

So this is a **finding, not a recommendation**: dropping the experimental A* tree (`astar/` ≈ ~51k tokens) would clear the 200k line by itself, but it deletes a shipped, selectable feature — a maintainer product decision, outside the behaviour-preserving constraint every other row satisfies. Conversely the sailing engine *reuses* legacy internals (#6 is the safe slice of that relationship).

### Totals and the 200k line

| Path | Savings | Resulting est. total |
|---|---:|---:|
| All rows **without** codegen (#2–#11, manual #3 instead of #1) | ~17,800 | ~213,500 — **still over 200k** |
| All rows **with** codegen (#1 + #2 + #4–#11) | ~25,000 | ~206,300 — **still over 200k** |
| Above + extending codegen to the `ShortestPathConfig` interface itself (spec → generated interface) | ~+7–9k | ~197–199k — under, but thin |
| Any path + retiring the A* backend (feature decision) | +~51k | ~155–175k — real headroom |

**Plainly: behaviour-preserving refactors alone do not clear 200k with headroom.** They buy ~18–25k tokens, landing at ~206–213k — better, still non-compliant. To get real headroom (≥40k removed, ~191k or below) the plugin additionally needs one of:

1. **Codegen the whole config surface including the `@ConfigItem` interface** — a compact spec (one row per option: key, type, section, position, name, description) driving a build-time generator that emits `ShortestPathConfig`, `ConfigKey`, `EffectiveConfig`, and the `Settings` snapshot. Combined with the other rows this reaches roughly ~190–197k. Legitimate and dependency-free, but it converts the plugin's most-touched file into generated output — upstream acceptability must be established first.
2. **A feature-level decision**: retire the opt-in A* backend (~51k) or accept plugin-hub rejection. No behaviour-preserving change makes this optional.
3. **Move genuinely mechanical code out of `src/main/java`** generally — any build-time-generated source escapes counting; the config surface is simply the largest mechanical body.

A scan for *dead* code found nothing worth a row: zero classes referenced only by their own file, zero unused public methods in the utility types, and every large file is algorithm/UI mass rather than embedded tables. (Caveat applied: in a RuneLite plugin, `@Subscribe`/`@Provides`/`PluginDescriptor` entry points have no Java call sites — absence-of-callers alone can't prove deadness, so only high-confidence items were checked.)

## Non-starters

Changes that **do not** reduce the counted total — no effort should be spent on them:

- **Deleting comments/javadoc** — comments are already excluded by the counting rule; deleting ~356k chars of them removes zero counted tokens.
- **File splitting / moving code between files** — the count is over `src/main/java` content, not file count or layout. Splitting `AStarForwardSearch` (the largest file, ~11.2k tok) changes nothing.
- **Moving or editing `src/main/resources`, `src/test`, `README`** — none of it is counted; changes there can't help (they're still worth doing for data-extraction moves, which is the point of rows #2/#8).
- **Formatting/whitespace churn** — blank lines are excluded, and reformatting doesn't remove tokens that matter.
- **Removing "unused" methods found by call-site greps alone** — no qualifying dead code exists, and reflective entry points make naive scans unsafe.

## Recommended sequence

Ordered by savings-per-unit-risk for upstream PRs:

1. **Land first (self-contained, obviously behaviour-preserving):** #3 EffectiveConfig delegation, #4 Lombok getters, #8 POH tables → resource, #2 ItemVariations → resource (option (b): names + generated name→id map). Each is a single-purpose PR with a mechanical diff. ~10.8k combined.
2. **Next (dedup extractions — need the scenario suite green):** #7 highlight-overlay base, #10 map-overlay dedup, #9 panel builders, #11 overload collapse, then #6 the sailing/legacy shared core (largest, touches pathfinding). ~4.7k combined.
3. **Needs maintainer buy-in before building:** #1 config codegen (and its extension to the `ShortestPathConfig` interface) — the only lever large enough to matter short of features; pitch it as "spec-driven config surface" with a generated-output review story.
4. **Feature decision, last resort:** A* backend retirement — present the measured 51k figure and the legacy-default/plugin-query evidence; let the maintainer choose.
5. **Skip unless still short:** #5 description tightening (user-visible text churn for ~1.7k).
