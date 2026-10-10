# Route planner

The public frontend for the OSRS travel planner, and its route service in [`service/`](service/README.md).

```sh
# From the repository root: the route service on :8080
./gradlew :service:run
# In another terminal, from web/:
npm install
npm run dev
```

Open <http://localhost:5173/route>. `/` redirects there. The dev server proxies `/api` to the service.
`npm test` runs the frontend tests and `npm run build` type-checks and builds `dist/`.

Everything that affects a route can be configured, and the editors are built from the route service's
`GET /v1/catalog`, so a new quest, unlock or plugin setting appears without frontend changes:

- **Account** (sidebar): levels, quest points, completed quests, diaries, unlocks, items, house, spellbook,
  minigame teleport cooldown. Start from a preset (`GET /v1/presets`, the canonical accounts) and save a
  custom build in the browser. The format is `../corpus/schemas/account-v1.schema.json`.
- **Route settings**: every route-affecting item of the plugin's own config (`ShortestPathConfig`), with
  the plugin's labels: transport toggles and thresholds, teleport items, unlocks the game doesn't
  report, and advanced options such as the legacy or exact backend. Requests and links carry only the
  settings that differ from the planner's defaults (`routing/` `PlannerSettings`).

Routes are URL-backed: endpoint, account and settings changes survive reload and browser navigation. Use
**Copy route link** to share the current setup. Custom accounts are gzip-compressed into a versioned URL fragment,
so one link carries both the route and account without depending on the recipient's browser storage. Received
accounts open as temporary **shared** accounts and are only saved locally when the recipient explicitly chooses
to save them. Links are size-limited and schema-validated before use.

Endpoint markers are draggable. Walking, teleport, transport, and bank steps are rendered as distinct map layers;
selecting an itinerary step focuses the corresponding layer. On narrow screens the itinerary is a collapsible
bottom sheet.

The existing developer viewer is intentionally not imported, mounted, or exposed by this application. Add shared code only when a public feature demonstrates a need for it.

Skill sprites are provided by [@dava96/osrs-icons](https://github.com/Dava96/osrs-icons)
under CC BY-NC-SA 3.0. Canonical item names and sprites come from the OSRS Wiki's
[Chisel item database](https://chisel.weirdgloop.org/moid/item_id.html).

The current map tiles are loaded from Explv's public GitHub-hosted tile set. Treat that as a development dependency;
choose and document a first-party hosting/cache policy before a public production launch.

Use established ecosystem libraries for UI, routing, map rendering, forms, API state, validation, and server implementation where they are the idiomatic choice. Do not replace them with local substitutes merely because the first version looks small.

The API contracts live in `../corpus/schemas`. `npm run contracts` copies the account schema into
`src/generated/` and generates the TypeScript types; `dev`, `build` and `test` run it first, and its
outputs are not committed. Override the corpus location with `SHORTEST_PATH_CORPUS_DIR`.

## Deploy

Local Docker Compose deployment (both images build from the repository root, so the
`shortest-path` submodule must be checked out):

```sh
docker compose up --build -d
curl http://localhost:8080/api/ready
```

The public site is served on `HTTP_PORT` (default `8080`). The service is private to the
Compose network. Set `SERVICE_HEAP_MIN` and `SERVICE_HEAP_MAX` for the host; the defaults are
`512m` and `4g`.

Production is designed for a Hetzner Cloud `ccx13` or `cpx32` VM, with Hetzner Object Storage
for OpenTofu state and optional Hetzner DNS. See the
[production deployment runbook](infra/README.md) for server installation, firewall/DNS IaC,
host hardening, deployment, verification, and rollback.
