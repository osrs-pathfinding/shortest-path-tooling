# Web Frontend Phase 2 — Complete the Route Planner

> Implementation status (2026-09-29): the in-scope route-planner work is implemented. Slice 2 (expanded place
> search/catalogue) and Slice 5 (broader local account management) remain explicitly deferred.

## Outcome

Turn the current working route-planner vertical slice into a complete public-facing `/route` experience before starting the heavier `/explore` analysis surface.

At the end of this phase a player should be able to find two useful locations, adjust them directly on the map, understand the returned journey, change the account and routing policy, and share or recover the same planner state. The phase should preserve the current Java exact-routing backend and canonical account contract.

This is deliberately a frontend-product phase. It includes small API additions needed by the UI, but not the accessibility engine, account comparison, login, cloud account storage, a rewrite of the map stack, the full place-search/catalogue work, or broader custom-account management.

## Current baseline

Already working:

- `/` redirects to `/route`;
- canonical Early, Mid, End, and Maxed account presets;
- a semantic custom-account editor with local persistence and JSON import/export;
- exact Java-backed route requests with cancellation through `AbortSignal`;
- start and destination selection from a small place list or by clicking the map;
- URL persistence for endpoints and the selected account;
- route policy controls for Wilderness, banking, and consumables;
- a semantic itinerary and map polyline from the same `RoutePlan`;
- responsive desktop/mobile layouts;
- bounded service execution, route/account caches, health endpoints, request IDs, and basic metrics;
- passing frontend tests and production TypeScript/Vite build.

Important gaps:

- place discovery is a 12-entry HTML datalist rather than a useful search experience;
- endpoint markers cannot be dragged, swapped, cleared independently, or set explicitly from a map context action;
- policies are neither durable nor represented in the URL;
- a shared `account=custom` URL only works in the sender's browser;
- itinerary steps are not interactive and do not show requirements or useful from/to context;
- teleports and transports are drawn as parts of one continuous polyline;
- loading, stale-result, empty, unreachable, timeout, saturation, and invalid-link states need distinct UX;
- `App.tsx` currently owns search, URL state, policy, account state, request orchestration, layout, and itinerary rendering;
- tests cover primitives but not the complete planner journey.

## Scope and sequence

### Slice 1 — Planner state and feature boundaries

Refactor before adding more interaction so the route page does not become a second monolith.

Create boundaries along these lines:

```text
src/features/route-planner/
    RoutePlannerPage.tsx
    usePlannerState.ts
    RouteToolbar.tsx
    RouteStatus.tsx

src/features/place-search/
    PlaceCombobox.tsx
    placeSearch.ts

src/features/itinerary/
    Itinerary.tsx
    ItineraryStep.tsx

src/features/account-editor/
    AccountPanel.tsx

src/map/
    OsrsMap.tsx
    EndpointLayer.tsx
    RouteLayer.tsx
```

Keep domain values free of Leaflet types. `RoutePlan` remains the sole input to both itinerary and route rendering.

Move planner state into a reducer or focused hook with explicit actions such as `setStart`, `setDestination`, `swapEndpoints`, `setPolicy`, and `selectAccount`. Do not add Redux.

Define a versioned URL codec and make the URL the canonical source for shareable planner state:

- `from` and `to` as place IDs or coordinate triples;
- `account` for canonical presets;
- compact policy parameters only when they differ from defaults;
- invalid or unsupported values ignored with a visible, non-fatal notice;
- browser back/forward restores the planner, rather than being overwritten by one-way local state.

Persist policy and the last selected preset locally, with URL values taking precedence.

#### Portable custom-account links

“Copy route link” must produce one self-contained link. If the selected account is custom, the link must include that complete profile; it must never depend on a matching `localStorage` entry in the recipient's browser.

Use a versioned payload in the URL fragment, conceptually:

```text
/route?from=grand-exchange&to=barrows&account=shared#profile=v1.<base64url-compressed-account>
```

The query string remains the canonical representation of endpoints and policy. The fragment payload contains:

```text
share format version
canonical AccountBuild
```

Requirements:

- serialize a canonical, deterministically ordered representation;
- compress it, then encode it using URL-safe base64 without padding;
- use a small, audited browser compression implementation rather than a handwritten codec;
- keep the account in the fragment so it is not included in normal HTTP request targets, reverse-proxy access logs, referrer headers, or server-rendered analytics;
- treat the data as portable user input, not trusted data: cap encoded and decoded sizes, reject malformed compression streams, validate the decoded object against `account-build-v1`, and reject unknown share versions;
- do not automatically save a received profile over the user's local custom account; load it as a temporary “Shared build” and offer an explicit save action;
- show that the link contains the account profile before copying, since anyone with the link can inspect its contents;
- preserve account name, levels, quests, diaries, items, POH, runtime state, and compatibility routing variables so the recipient calculates the same route;
- show a clear “This route link is invalid or unsupported” state on decode/validation failure, without partially applying the payload;
- set a tested maximum link size. The current full profiles compress to approximately 2.2–5.8 KB before route parameters, so an 8 KB target is realistic; if a custom build exceeds the supported size, explain that it cannot yet be shared as one link rather than copying a broken or incomplete URL.
- provide a visible “Copy route link” action, clipboard success/failure feedback, and a manual copy fallback when the Clipboard API is unavailable;
- construct the link from the current committed endpoints, policy, and account rather than copying `window.location` while state or route calculation is stale.

Preset routes stay short and use `account=early|mid|end|maxed`; do not embed a canonical preset unnecessarily. Opening a shared custom build and pressing “Copy route link” again must produce an equivalent self-contained link.

Acceptance:

- opening, editing, copying, reloading, and navigating back/forward produce the same endpoints, account, and policy;
- a custom-account link opened in a clean browser profile calculates with the embedded account, not a local fallback;
- decoding is atomic, size-limited, schema-validated, and covered by round-trip and hostile-input tests;
- existing custom-account storage and account import/export continue to work;
- `App.tsx` becomes routing/layout composition rather than the implementation of every feature;
- reducer/codec tests cover defaults, malformed parameters, and round trips.

### Slice 2 — Deferred: real place search and endpoint controls

Do not implement this slice in Phase 2. Keep the existing place list and endpoint inputs for now. The following remains the design target for a later phase.

Replace the datalist with an accessible combobox supporting keyboard navigation, result highlighting, clear buttons, and an explicit empty state.

Expand the canonical place data rather than growing a hand-maintained TypeScript array. Add categories to the place schema and generated type, then publish a versioned place catalogue from `shortest-path-corpus`. Initial useful categories:

- cities and settlements;
- banks;
- bosses and activities;
- quest and clue destinations;
- transport hubs;
- fairy rings and other named transport endpoints.

For the first catalogue, client-side normalized token search is sufficient. Rank exact name, prefix, token prefix, alias, then substring matches. Keep the search interface behind a repository so it can move to `/v1/places` later without replacing the combobox.

Add endpoint controls:

- swap start and destination;
- clear either endpoint;
- display named places differently from custom tiles;
- choose whether the next map click sets A or B instead of relying on implicit sequencing;
- retain a recent-place list locally;
- show coordinates as secondary information where useful, never as the primary label for named places.

Later-phase acceptance:

- a keyboard-only user can search, select, clear, and swap endpoints;
- aliases such as `GE` can resolve to Grand Exchange;
- a map-selected tile remains round-trippable through the URL;
- search remains responsive with the full place catalogue;
- place-catalogue validation rejects duplicate IDs, invalid coordinates, categories, and aliases.

### Slice 3 — Direct map manipulation and correct route layers

Make both endpoint markers draggable.

- update marker position immediately while dragging;
- recompute a moved start after a short debounce if measured latency permits;
- recompute a moved destination on drag end initially, because target preparation can be more expensive;
- expose keyboard-accessible alternatives to dragging through endpoint controls;
- preserve the endpoint plane unless the user explicitly selects a location on another plane.

Split route rendering by segment:

- walking paths use solid lines following their path geometry;
- teleports use destination pulses or a dashed curved connector, not a walking line;
- transports use a visually distinct connector and endpoint markers;
- bank steps get a small map marker;
- no line should connect unrelated adjacent segments merely because their points were flattened into one array.

Add route framing rules: fit once when a complete route arrives, do not fight manual panning, and provide a “fit route” control to recover the full journey.

Acceptance:

- dragging A or B updates the URL and produces only the latest applicable result;
- superseded requests are aborted and cannot replace a newer result;
- teleport/transport geometry is visually distinguishable from walking;
- changing or clearing endpoints never leaves stale route geometry on the map;
- coordinate and interaction tests cover both planes and drag completion.

### Slice 4 — Itinerary that explains the journey

Upgrade the sidebar from a segment list to an inspectable itinerary.

Add:

- total ticks plus an approximate duration, clearly labelled as an estimate;
- start and destination bookends;
- step numbering and consistent walk, teleport, transport, and bank icons;
- transport from/to names or coordinates;
- requirement chips from `segment.requirements`;
- collapsed consecutive low-value walking detail while preserving exact cost;
- click/focus a step to highlight and fit that segment on the map;
- hover/focus a map segment to highlight the corresponding itinerary step;
- a copyable concise text itinerary.

Before depending on requirement display, complete the service mapping so capabilities are populated for transports. Keep the UI valid when an older response has an empty requirements list.

Represent request states explicitly:

```text
incomplete input
loading with no previous route
refreshing with previous route visible
reachable
unreachable
invalid request
timed out
service saturated
service unavailable
```

Surface the server request ID on errors behind a “details” disclosure so bug reports can be correlated without cluttering the normal interface.

Acceptance:

- every backend segment kind has an intentional visual treatment;
- selecting an itinerary step highlights only that step's map layer;
- unreachable is not styled or announced as a server failure;
- a failed refresh does not silently present the old route as current;
- route-state and itinerary tests use representative mixed-segment fixtures.

### Slice 5 — Deferred: broader account and policy usability

Do not implement this slice in Phase 2. The only account-related addition currently in scope is the portable custom-account route link specified in Slice 1. Keep the existing single locally saved custom build and editor behavior.

Keep the existing semantic account editor, then improve the highest-friction flows:

- searchable account sections and a compact summary of route-relevant differences from the selected preset;
- reset custom build to a chosen preset;
- duplicate a preset/custom build before editing;
- support multiple locally named custom builds with stable local IDs;
- schema-versioned local-storage migration and a recovery path for invalid stored data;
- clearer inventory/equipment/bank semantics and item quantities;
- dirty-state protection on page navigation as well as profile switching;
- explain route policy beside the route, with defaults and “reset policy”.

Later-phase acceptance:

- users can create, rename, duplicate, switch, and delete local builds without damaging presets;
- storage failures or obsolete schema versions produce a recoverable message;
- account or policy changes invalidate and recompute the route exactly once.

### Slice 6 — Product shell, accessibility, and resilience

Finish the public route surface around the core interaction:

- navigation prepared for `/route` and the later `/explore`, without exposing a dead primary action;
- first-run help that does not block immediate route planning;
- mobile bottom-sheet treatment for itinerary/account rather than a very long document flow;
- reduced-motion support, visible focus, sufficient contrast, semantic landmarks, and live announcements that do not chatter during drag;
- route-level error boundary and a retry action;
- offline/service-unavailable copy that distinguishes frontend availability from route-engine availability;
- lazy-load the account editor and map-heavy code intentionally, then measure bundle impact;
- first-party tile hosting or a documented tile availability/caching decision before public production use.

Acceptance:

- the primary flow works at 360 px, tablet width, and desktop without hiding required controls;
- the planner is operable using keyboard only and sensible with a screen reader;
- prefers-reduced-motion is respected;
- an unavailable route service does not make account editing or endpoint selection unusable;
- a production smoke test covers static serving, `/api/ready`, preset loading, item search, and one known route.

## Cross-cutting test plan

Add tests at the boundary where regressions would be expensive:

- unit: URL/share codec, size limits, schema rejection, planner reducer, route-layer conversion, duration formatting;
- component: shared-build loading/saving, endpoint controls, route states, and itinerary/map selection;
- contract: validate representative route success/error payloads against the generated schema;
- service: schema rejection, unreachable route, mixed semantic segments, request ID, timeout/saturation mapping, cache-key policy separation;
- browser E2E: select GE to an existing destination, drag an endpoint, change policy, select a preset, reload/share, and verify restored state; repeat custom-account sharing in a clean browser context;
- visual: desktop, narrow desktop with account panel, mobile itinerary, unreachable, and service-error states.

The phase gate is not merely “tests pass”: perform one real end-to-end route against the packaged Java service and verify that the itinerary cost and map segment structure match the response.

## Delivery order

Implement in this order to keep every merge usable:

1. state/URL codec, portable custom-account links, and feature extraction;
2. marker dragging and segmented map rendering, using the existing endpoint inputs;
3. interactive itinerary and complete request-state UX;
4. mobile/accessibility/resilience pass and end-to-end tests.

The full place-search/catalogue slice and broader account-management slice are explicitly deferred.

Each slice should land with its tests and remain deployable. Avoid one branch containing the whole phase.

## Phase exit gate

Phase 2 is complete when:

- a new user can plan a route without knowing coordinates;
- endpoint, account, and policy changes behave predictably and survive reload/back/forward;
- preset and supported-size custom-account routes are genuinely shareable as single links;
- a shared custom route reproduces its embedded account in a clean browser and never silently substitutes local state;
- map and itinerary represent semantic route steps consistently;
- all meaningful empty/loading/refresh/error/unreachable states are handled;
- desktop and mobile primary journeys pass automated browser tests;
- frontend unit tests, typecheck, production build, service tests, schema checks, and a packaged-service smoke route pass;
- no internal routing variables, transport-edge IDs, Leaflet types, or debug-viewer concepts leak into the public UI.

## What follows

Only after this gate, begin Phase 3: `/explore`.

That phase should start with an API/renderer spike for a bounded single-source accessibility field, including payload size, server time, browser decode time, memory use, and pan/zoom rendering performance. Use those measurements to decide whether to retain Leaflet with a raster/canvas layer or migrate the public map to MapLibre. Then build reachable/unreachable and “within N ticks” modes before account-difference heatmaps.
