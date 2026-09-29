# shortest-path-web

Greenfield public frontend for the OSRS travel planner.

```sh
npm install
npm run dev
```

Open <http://localhost:5173/route>. `/` redirects there.

The existing developer viewer is intentionally not imported, mounted, or exposed by this application. Add shared code only when a public feature demonstrates a need for it.

Use established ecosystem libraries for UI, routing, map rendering, forms, API state, validation, and server implementation where they are the idiomatic choice. Do not replace them with local substitutes merely because the first version looks small.
