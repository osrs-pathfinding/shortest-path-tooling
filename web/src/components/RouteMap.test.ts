import { CRS, map as leafletMap } from "leaflet";
import { describe, expect, it } from "vitest";
import { fromLatLng, toLatLng } from "./RouteMap";

describe("route map coordinates", () => {
  it("maps a picked tile back to the same tile", () => {
    const map = leafletMap(document.createElement("div"), { crs: CRS.EPSG3857, maxZoom: 11 });
    for (const point of [{ x: 3222, y: 3218, plane: 0 }, { x: 1744, y: 3599, plane: 1 }]) {
      expect(fromLatLng(map, toLatLng(map, point), point.plane)).toEqual(point);
    }
  });
});
