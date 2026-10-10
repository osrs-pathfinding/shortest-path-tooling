# Web roadmap

The public `/route` planner is implemented and deployable. It currently supports canonical and
custom accounts, portable custom-profile links, policy-backed routing, draggable endpoints,
semantic route layers, an interactive itinerary, responsive layout, explicit request states, and
the Java exact-routing service.

Do not expand the product surface until production measurements exist for route latency, router
memory, frontend errors, and the external map-tile dependency.

## Deferred work

### Place discovery

The current small datalist is intentional. A later place-search phase should:

- publish a versioned place catalogue from `corpus/` rather than extending the local
  array;
- add an accessible keyboard combobox with aliases, categories, clear/swap controls, and recent
  places;
- keep client-side search behind a small repository boundary so it can later move server-side;
- validate duplicate IDs, coordinates, categories, and aliases at generation time.

### Local account management

The current single saved custom build is intentional. A later account phase may add multiple named
builds, duplication, migrations, search, and navigation dirty-state protection. Do not add login or
cloud account storage as part of that work.

### Explore

Before building `/explore`, spike one bounded accessibility-field request and measure server time,
payload size, browser decoding, memory, and map rendering. Use those results to choose between a
Leaflet canvas/raster layer and MapLibre. Start with reachable/unreachable and “within N ticks”
modes; account-difference heatmaps come later.

## Production follow-ups

- Replace or formally approve the external Explv tile source before public launch.
- Add a browser smoke test covering a preset route, endpoint drag, policy change, reload, and a
  shared custom profile in a clean browser context.
- Add external uptime monitoring for `/api/ready` and authenticated host-local collection of the
  existing service metrics.
- Load-test `ccx13` and `cpx32` with representative accounts before changing the default instance
  or JVM limits.
