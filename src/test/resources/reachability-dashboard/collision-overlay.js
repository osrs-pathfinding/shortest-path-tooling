/**
 * Collision-map overlay
 * ---------------------
 * Renders fully-blocked tiles (all four edges impassable) on the current
 * plane as solid filled squares, and draws wall/door boundary flags as
 * thin orange edge lines on top of the fill.
 *
 * Default: off. Mirrors the transport-layers toggle pattern via the
 * shared window.addMapLayerToggle({label, checked, onChange}) registry.
 *
 * The collision-map.zip is the same binary shipped by the shortest-path
 * plugin (/collision-map.zip in plugin resources, copied next to the
 * dashboard's index.html by PathfinderDashboardAssetWriter). Each zip
 * entry is named "<regionX>_<regionY>" and contains the raw byte payload
 * of a Java BitSet (little-endian, byte-aligned) encoding four edge flags
 * per tile per plane:
 *
 *   flag 0 (FLAG_NORTH)      — movement edge (x, y) -> (x, y+1) is walkable
 *   flag 1 (FLAG_EAST)       — movement edge (x, y) -> (x+1, y) is walkable
 *   flag 2 (FLAG_WALL_NORTH) — north edge is a structural boundary written
 *                              by walls/doors
 *   flag 3 (FLAG_WALL_EAST)  — east edge is a structural boundary written
 *                              by walls/doors
 *
 *   index = (z * 64 * 64 + (y - minY) * 64 + (x - minX)) * 4 + flag
 *
 *   planeCount = bytes.length * 8 / (64 * 64 * 4)   (rounded up)
 *
 * A tile is considered "blocked" (filled red) when none of its four
 * incident edges are walkable on the current plane:
 *   N: flag(x,   y,   N),  S: flag(x,   y-1, N),
 *   E: flag(x,   y,   E),  W: flag(x-1, y,   E).
 * Walkable corridors such as bridges, doorways and roads remain visible
 * as gaps in the fill. Wall/door boundary flags paint as orange lines
 * along the tile's north or east edge; south/west walls surface through
 * the neighbour's north/east flag.
 *
 * The overlay is locked to plane 0 because the dashboard base map only
 * ever renders plane 0 tiles.
 */
(function () {
  "use strict";

  const REGION_SIZE = 64;
  const FLAG_NORTH = 0;
  const FLAG_EAST = 1;
  const FLAG_WALL_NORTH = 2;
  const FLAG_WALL_EAST = 3;
  const FLAG_COUNT = 4;
  const FILL_COLOR = "rgba(220, 38, 38, 0.45)";
  const WALL_COLOR = "rgba(245, 158, 11, 0.9)";

  // Minimum on-screen pixels per world tile before tiles are drawn;
  // below this the overlay would be a meaningless wash of colour.
  const MIN_PX_PER_TILE = 2;

  let enabled = false;
  let layer = null;
  let zipPromise = null;
  /** @type {Map<string, Uint8Array | "pending" | "missing">} */
  const regionCache = new Map();
  let pendingRedraw = false;

  /** Mirrors net.runelite.api.Constants.REGION_SIZE indexing in FlagMap. */
  function regionKey(rx, ry) {
    return rx + "_" + ry;
  }

  /** Parse a BitSet payload (java.util.BitSet.toByteArray ordering) into a bit at the given index. */
  function bitGet(bytes, idx) {
    const byteIdx = idx >>> 3;
    if (byteIdx >= bytes.length) return false;
    return (bytes[byteIdx] & (1 << (idx & 7))) !== 0;
  }

  function planeCount(bytes) {
    const stride = REGION_SIZE * REGION_SIZE * FLAG_COUNT;
    return Math.ceil((bytes.length * 8) / stride);
  }

  /** Returns the flag bit for tile (x, y, z, flag) within a region's bytes. minX/minY are the region origin. */
  function flagBit(bytes, minX, minY, x, y, z, flag) {
    const idx = ((z * REGION_SIZE * REGION_SIZE) + ((y - minY) * REGION_SIZE) + (x - minX)) * FLAG_COUNT + flag;
    return bitGet(bytes, idx);
  }

  function loadZip() {
    if (zipPromise) return zipPromise;
    if (typeof JSZip === "undefined") {
      console.warn("[collision-overlay] JSZip not available; overlay disabled");
      zipPromise = Promise.reject(new Error("JSZip unavailable"));
      return zipPromise;
    }
    // collision-map.zip is published at the dashboard root (not per-bundle). Each dashboard run rewrites it, so
    // check it's still current rather than trusting the browser's cache.
    const url = "collision-map.zip";
    zipPromise = fetch(url, { cache: "no-cache" })
      .then(r => {
        if (!r.ok) throw new Error("HTTP " + r.status + " fetching " + url);
        return r.arrayBuffer();
      })
      .then(buf => JSZip.loadAsync(buf))
      .catch(err => {
        console.warn("[collision-overlay] Failed to load " + url + ":", err);
        throw err;
      });
    return zipPromise;
  }

  /** Returns Uint8Array if cached; null otherwise (kicks off an async load and a redraw on completion). */
  function getRegion(rx, ry) {
    const key = regionKey(rx, ry);
    const cached = regionCache.get(key);
    if (cached === "missing") return null;
    if (cached === "pending") return null;
    if (cached instanceof Uint8Array) return cached;

    regionCache.set(key, "pending");
    loadZip().then(zip => {
      const entry = zip.file(key);
      if (!entry) {
        regionCache.set(key, "missing");
        return;
      }
      return entry.async("uint8array").then(bytes => {
        regionCache.set(key, bytes);
        scheduleRedraw();
      });
    }).catch(() => {
      regionCache.set(key, "missing");
    });
    return null;
  }

  function scheduleRedraw() {
    if (pendingRedraw || !layer) return;
    pendingRedraw = true;
    requestAnimationFrame(() => {
      pendingRedraw = false;
      if (layer) layer.redraw();
    });
  }

  /** Custom GridLayer that renders blocked edges per 256-pixel map tile. */
  function createGridLayer() {
    const CollisionEdgeLayer = L.GridLayer.extend({
      options: {
        tileSize: 256,
        minZoom: -4,
        maxZoom: 11,
        updateWhenZooming: false,
        className: "collision-edge-overlay"
      },

      createTile(coords) {
        const size = this.options.tileSize;
        const canvas = document.createElement("canvas");
        canvas.width = size;
        canvas.height = size;

        const b = this._tileCoordsToBounds(coords);
        const wxMin = b.getWest();
        const wxMax = b.getEast();
        const wyMin = b.getSouth();
        const wyMax = b.getNorth();
        const worldW = wxMax - wxMin;
        const worldH = wyMax - wyMin;
        if (worldW <= 0 || worldH <= 0) return canvas;

        const pxPerTileX = size / worldW;
        const pxPerTileY = size / worldH;
        if (pxPerTileX < MIN_PX_PER_TILE || pxPerTileY < MIN_PX_PER_TILE) {
          return canvas;
        }

        // The base map only ever renders plane 0, so anchor the overlay
        // to plane 0 as well rather than tracking currentPlane.
        const plane = 0;
        const ctx = canvas.getContext("2d");
        ctx.fillStyle = FILL_COLOR;

        const x0 = Math.floor(wxMin) - 1;
        const x1 = Math.ceil(wxMax);
        const y0 = Math.floor(wyMin) - 1;
        const y1 = Math.ceil(wyMax);

        // Look up the flag bit for (x, y, z, flag); transparently spans
        // region boundaries by fetching the right region cache entry.
        const edge = (x, y, flag) => {
          const rx = Math.floor(x / REGION_SIZE);
          const ry = Math.floor(y / REGION_SIZE);
          const bytes = getRegion(rx, ry);
          if (!bytes) return false;
          const pc = planeCount(bytes);
          if (plane < 0 || plane >= pc) return false;
          return flagBit(bytes, rx * REGION_SIZE, ry * REGION_SIZE, x, y, plane, flag);
        };

        for (let x = x0; x <= x1; x++) {
          for (let y = y0; y <= y1; y++) {
            // Flag bits encode passability (set = walkable edge), so a tile
            // is fully blocked iff none of its four incident edges are set.
            if (edge(x, y, FLAG_NORTH)) continue;
            if (edge(x, y, FLAG_EAST)) continue;
            if (edge(x, y - 1, FLAG_NORTH)) continue;
            if (edge(x - 1, y, FLAG_EAST)) continue;

            const px = (x - wxMin) * pxPerTileX;
            const py = (wyMax - (y + 1)) * pxPerTileY;
            ctx.fillRect(px, py, pxPerTileX, pxPerTileY);
          }
        }

        // Second pass: wall/door boundary flags as thin orange edge lines,
        // drawn after every fill so a later tile's translucent fill never
        // dims an earlier tile's edge line. South/west walls need no code
        // of their own: they are the neighbour's north/east flag and the
        // ±1 tile margin already iterates those neighbours. Thin fillRects
        // are used instead of strokes to avoid half-pixel stroke blur.
        const wallT = Math.max(1, Math.min(3, Math.round(Math.min(pxPerTileX, pxPerTileY) / 4)));
        ctx.fillStyle = WALL_COLOR;
        for (let x = x0; x <= x1; x++) {
          for (let y = y0; y <= y1; y++) {
            const px = (x - wxMin) * pxPerTileX;
            const py = (wyMax - (y + 1)) * pxPerTileY;
            if (edge(x, y, FLAG_WALL_NORTH)) {
              ctx.fillRect(px, py - wallT / 2, pxPerTileX, wallT);
            }
            if (edge(x, y, FLAG_WALL_EAST)) {
              ctx.fillRect(px + pxPerTileX - wallT / 2, py, wallT, pxPerTileY);
            }
          }
        }

        return canvas;
      }
    });

    return new CollisionEdgeLayer();
  }

  function setEnabled(on) {
    enabled = !!on;
    const map = window._dashboardMap;
    if (!map) return;
    if (enabled) {
      if (!layer) layer = createGridLayer();
      layer.addTo(map);
      // Eagerly start the zip fetch so the first redraw has data.
      loadZip().catch(() => {});
    } else if (layer) {
      map.removeLayer(layer);
    }
  }

  // Plane changes are imperative in app.js; the helper there now fires
  // a "planechange" event on the map after baseLayer.redraw().
  function attachPlaneListener() {
    // No-op: the overlay is locked to plane 0 because the base map tiles
    // are only ever rendered for plane 0.
  }

  function init() {
    if (typeof window.addMapLayerToggle !== "function") {
      // Layer-toggle registry not ready yet; retry on next frame.
      requestAnimationFrame(init);
      return;
    }
    attachPlaneListener();
    window.addMapLayerToggle({
      label: "Collision map",
      checked: false,
      onChange: setEnabled
    });
  }

  if (document.readyState === "complete" || document.readyState === "interactive") {
    init();
  } else {
    document.addEventListener("DOMContentLoaded", init);
  }
})();
