# shortest-path-web

Greenfield public frontend for the OSRS travel planner.

```sh
npm install
cd service && ./gradlew run
# In another terminal, from the repository root:
npm run dev
```

Open <http://localhost:5173/route>. `/` redirects there.

Choose and customize account presets from the planner's account sidebar. Custom builds contain semantic skills,
quests, unlocks, and POH settings and are saved in the browser; raw routing variables remain hidden.

Routes are URL-backed: endpoint, preset, and route-policy changes survive reload and browser navigation. Use
**Copy route link** to share the current setup. Custom accounts are gzip-compressed into a versioned URL fragment,
so one link carries both the route and profile without depending on the recipient's browser storage. Received
profiles open as temporary **Shared builds** and are only saved locally when the recipient explicitly chooses to
save them. Links are size-limited and schema-validated before use.

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

`npm run contracts` regenerates TypeScript declarations and public presets from the adjacent
`shortest-path-corpus` checkout. Override its location with `SHORTEST_PATH_CORPUS_DIR`.

## Deploy

Local Docker Compose deployment:

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
