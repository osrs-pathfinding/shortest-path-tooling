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

The service listens on `PORT` (default `8080`) and exposes `POST /v1/route`,
`GET /live`, `GET /ready`, and `GET /metrics`.
