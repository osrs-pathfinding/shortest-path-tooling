# Route service

The public HTTP boundary for the `shortest-path` production engine. It runs
`ExactPathfinder` directly; it does not depend on the GPS plugin.

The sibling checkouts are expected to be arranged as:

```text
osrs-pathfinding/
├── shortest-path/
└── shortest-path-web/
    └── service/
```

Use `-PshortestPathDir=/path/to/shortest-path` for another layout.

```sh
./gradlew test
./gradlew run
```

Building the service (including the deploy image) runs `generateRouteItemNames`, which lists every
item the current transport data can require, plus items the pathfinder checks in code, and names them
from the OSRS Wiki's [Chisel item database](https://chisel.weirdgloop.org/moid/item_id.html). The
build fails if a route item has no name. Pass `-PitemNamesSource=/path/to/itemsmin.js` to build
without network access.

The service listens on `PORT` (default `8080`) and exposes `POST /v1/route`,
`GET /live`, `GET /ready`, and `GET /metrics`.
