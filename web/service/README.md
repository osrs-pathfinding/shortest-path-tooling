# Route service

The public HTTP boundary for the `shortest-path` production engine. It runs `ExactPathfinder`
directly; it does not depend on the GPS plugin.

The service is the Gradle project `:service` in the repository's root build, so it compiles against
the pinned `shortest-path` submodule like the rest of the tooling. Route planning itself lives in
[`accounts/`](../../accounts) and [`routing/`](../../routing): `AccountJson` reads the request's
account into an `Account`, which `AccountCompiler` compiles like every other account;
`PlannerSettings` applies the request's changed plugin settings to the planner's defaults; and
`RoutePlans` turns the pathfinder result into a route plan. The settings choose the legacy or exact
backend. This project adds request validation (against `corpus/schemas`),
caching, metrics and the item catalog.

From the repository root:

```sh
./gradlew :service:test
./gradlew :service:run
```

Building the service (including the deploy image) runs `generateRouteItemNames`, which lists every
item the current transport data can require, plus items the pathfinder checks in code, and names them
from the OSRS Wiki's [Chisel item database](https://chisel.weirdgloop.org/moid/item_id.html). The
build fails if a route item has no name. Pass `-PitemNamesSource=/path/to/itemsmin.js` to build
without network access.

The service listens on `PORT` (default `8080`) and exposes `POST /v1/route`, `GET /v1/presets`,
`GET /v1/catalog`, `GET /v1/items`, `GET /live`, `GET /ready`, and `GET /metrics`.
