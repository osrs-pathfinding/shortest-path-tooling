/**
 * Collision-map overlay
 * ---------------------
 * Draws blocked tile edges (NORTH / EAST flags) from the runtime
 * collision-map.zip on top of the Leaflet map.
 *
 * Default: off. Mirrors the transport-layers toggle pattern via the
 * shared window.addMapLayerToggle({label, checked, onChange}) registry.
 *
 * The collision-map.zip is the same binary shipped by the shortest-path
 * plugin (/collision-map.zip in plugin resources, copied next to the
 * dashboard's index.html by PathfinderDashboardAssetWriter). Each zip
 * entry is named "<regionX>_<regionY>" and contains the raw byte payload
 * of a Java BitSet (little-endian, byte-aligned) encoding two flags per
 * tile per plane:
 *
 *   flag 0 (FLAG_NORTH) — edge blocked between (x, y) and (x, y+1)
 *   flag 1 (FLAG_EAST)  — edge blocked between (x, y) and (x+1, y)
 *
 *   index = (z * 64 * 64 + (y - minY) * 64 + (x - minX)) * 2 + flag
 *
 *   planeCount = bytes.length * 8 / (64 * 64 * 2)   (rounded up)
 *
 * Plane changes are signalled by a custom "planechange" map event fired
 * from app.js whenever currentPlane is reassigned.
 */
(function () {
  "use strict";

  const REGION_SIZE = 64;
  const FLAG_NORTH = 0;
  const FLAG_EAST = 1;
  const EDGE_COLOR = "#dc2626";

  // Minimum on-screen pixels per world tile before any edges are drawn;
  // below this the overlay would be visual noise.
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
    const stride = REGION_SIZE * REGION_SIZE * 2;
    return Math.ceil((bytes.length * 8) / stride);
  }

  /** Returns the flag bit for tile (x, y, z, flag) within a region's bytes. minX/minY are the region origin. */
  function flagBit(bytes, minX, minY, x, y, z, flag) {
    const idx = ((z * REGION_SIZE * REGION_SIZE) + ((y - minY) * REGION_SIZE) + (x - minX)) * 2 + flag;
    return bitGet(bytes, idx);
  }

  function loadZip() {
    if (zipPromise) return zipPromise;
    if (typeof JSZip === "undefined") {
      console.warn("[collision-overlay] JSZip not available; overlay disabled");
      zipPromise = Promise.reject(new Error("JSZip unavailable"));
      return zipPromise;
    }
    const base = (typeof window.currentBundleBase === "string" ? window.currentBundleBase : "");
    // collision-map.zip is published at the dashboard root (not per-bundle).
    const url = "collision-map.zip";
    zipPromise = fetch(url)
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

        const plane = (typeof currentPlane === "number") ? currentPlane : 0;
        const ctx = canvas.getContext("2d");
        ctx.strokeStyle = EDGE_COLOR;
        ctx.lineWidth = Math.max(1, Math.min(2, pxPerTileX / 6));
        ctx.lineCap = "butt";

        const x0 = Math.floor(wxMin) - 1;
        const x1 = Math.ceil(wxMax);
        const y0 = Math.floor(wyMin) - 1;
        const y1 = Math.ceil(wyMax);

        const rxMin = Math.floor(x0 / REGION_SIZE);
        const rxMax = Math.floor(x1 / REGION_SIZE);
        const ryMin = Math.floor(y0 / REGION_SIZE);
        const ryMax = Math.floor(y1 / REGION_SIZE);

        ctx.beginPath();
        for (let rx = rxMin; rx <= rxMax; rx++) {
          for (let ry = ryMin; ry <= ryMax; ry++) {
            const bytes = getRegion(rx, ry);
            if (!bytes) continue;
            const pc = planeCount(bytes);
            if (plane < 0 || plane >= pc) continue;

            const minX = rx * REGION_SIZE;
            const minY = ry * REGION_SIZE;
            const ax = Math.max(x0, minX);
            const bx = Math.min(x1, minX + REGION_SIZE - 1);
            const ay = Math.max(y0, minY);
            const by = Math.min(y1, minY + REGION_SIZE - 1);

            for (let x = ax; x <= bx; x++) {
              for (let y = ay; y <= by; y++) {
                if (flagBit(bytes, minX, minY, x, y, plane, FLAG_NORTH)) {
                  // edge between (x,y) and (x,y+1) — runs west→east at lat y+1
                  const px0 = (x - wxMin) * pxPerTileX;
                  const px1 = (x + 1 - wxMin) * pxPerTileX;
                  const py = (wyMax - (y + 1)) * pxPerTileY;
                  ctx.moveTo(px0, py);
                  ctx.lineTo(px1, py);
                }
                if (flagBit(bytes, minX, minY, x, y, plane, FLAG_EAST)) {
                  // edge between (x,y) and (x+1,y) — runs south→north at lng x+1
                  const px = (x + 1 - wxMin) * pxPerTileX;
                  const py0 = (wyMax - y) * pxPerTileY;
                  const py1 = (wyMax - (y + 1)) * pxPerTileY;
                  ctx.moveTo(px, py0);
                  ctx.lineTo(px, py1);
                }
              }
            }
          }
        }
        ctx.stroke();

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
    const map = window._dashboardMap;
    if (!map) return;
    map.on("planechange", () => {
      if (layer && enabled) layer.redraw();
    });
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
