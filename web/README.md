# shortest-path-web

Greenfield public frontend for the OSRS travel planner.

```sh
npm install
cd service && ./gradlew run
# In another terminal, from the repository root:
npm run dev
```

Open <http://localhost:5173/route>. `/` redirects there.

Account presets can be customized from the planner. Custom builds contain semantic skills,
quests, unlocks, and POH settings and are saved in the browser; raw routing variables remain hidden.

The existing developer viewer is intentionally not imported, mounted, or exposed by this application. Add shared code only when a public feature demonstrates a need for it.

Skill sprites are provided by [@dava96/osrs-icons](https://github.com/Dava96/osrs-icons)
under CC BY-NC-SA 3.0. Canonical item names and sprites come from the OSRS Wiki's
[Chisel item database](https://chisel.weirdgloop.org/moid/item_id.html).

Use established ecosystem libraries for UI, routing, map rendering, forms, API state, validation, and server implementation where they are the idiomatic choice. Do not replace them with local substitutes merely because the first version looks small.

`npm run contracts` regenerates TypeScript declarations and public presets from the adjacent
`shortest-path-corpus` checkout. Override its location with `SHORTEST_PATH_CORPUS_DIR`.

## Deploy

Keep the production `shortest-path` checkout beside this repository, then run:

```sh
docker compose up --build -d
curl http://localhost:8080/api/ready
```

The public site is served on `HTTP_PORT` (default `8080`). The service is private to the
Compose network. Set `SERVICE_HEAP_MIN` and `SERVICE_HEAP_MAX` for the host; the defaults are
`512m` and `4g`.
