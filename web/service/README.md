# Route service

The public HTTP boundary for the `shortest-path` production engine. It runs `ExactPathfinder`
directly; it does not depend on the GPS plugin.

The service is the Gradle project `:service` in the repository's root build, so it compiles against
the pinned `shortest-path` submodule like the rest of the tooling. Route planning itself lives in
[`accounts/`](../../accounts) and [`routing/`](../../routing): `AccountBuilds` compiles the
request's account build into an `Account` (the same compiler the canonical profiles go through),
`RoutePolicies` turns a route policy into plugin settings, and `RoutePlans` turns the pathfinder
result into a route plan. This project adds request validation (against `corpus/schemas`),
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

The service listens on `PORT` (default `8080`) and exposes `POST /v1/route`, `GET /v1/items`,
`GET /live`, `GET /ready`, and `GET /metrics`.
