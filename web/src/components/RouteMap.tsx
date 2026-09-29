import { useEffect } from "react";
import { divIcon, latLngBounds, type LatLng, type Map as LeafletMap } from "leaflet";
import { MapContainer, Marker, Polyline, TileLayer, useMap, useMapEvents } from "react-leaflet";
import type { Location, RoutePlan, WorldPoint } from "../domain/contracts";
import "leaflet/dist/leaflet.css";

const MAP_HEIGHT = 364544;
const TILE_SIZE = 32;
const OFFSET_X = 960;
const OFFSET_Y = 6208;

// Leaflet's default marker finds its image through CSS, which breaks once Vite hashes assets.
const endpointIcon = (label: string, kind: string) => divIcon({
  className: `route-marker ${kind}`,
  html: `<span>${label}</span>`,
  iconSize: [30, 40],
  iconAnchor: [15, 40],
});
const startIcon = endpointIcon("A", "start");
const destinationIcon = endpointIcon("B", "destination");

// Game tile (x, y) covers one TILE_SIZE square at max zoom; y grows northwards.
export function toLatLng(map: LeafletMap, point: WorldPoint): LatLng {
  const x = (point.x - OFFSET_X + 0.5) * TILE_SIZE;
  const y = MAP_HEIGHT - (point.y - OFFSET_Y + 0.5) * TILE_SIZE;
  return map.unproject([x, y], map.getMaxZoom());
}

export function fromLatLng(map: LeafletMap, latlng: LatLng, plane: number): WorldPoint {
  const projected = map.project(latlng, map.getMaxZoom());
  const x = Math.floor(projected.x / TILE_SIZE) + OFFSET_X;
  const y = Math.floor((MAP_HEIGHT - projected.y) / TILE_SIZE) + OFFSET_Y;
  return { x, y, plane };
}

function MapContents({ start, destination, route, onPick }: RouteMapProps) {
  const map = useMap();
  // The account sidebar resizes the map without resizing the window.
  useEffect(() => {
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(() => map.invalidateSize());
    observer.observe(map.getContainer());
    return () => observer.disconnect();
  }, [map]);
  // Bring both endpoints into view when they change, without refitting on every render.
  const startKey = start && JSON.stringify(start.coordinate);
  const destinationKey = destination && JSON.stringify(destination.coordinate);
  useEffect(() => {
    const points = [start, destination].flatMap(location => location ? [toLatLng(map, location.coordinate)] : []);
    if (!points.length) return;
    const bounds = latLngBounds(points);
    if (!map.getBounds().pad(-0.1).contains(bounds)) map.fitBounds(bounds, { padding: [80, 80], maxZoom: 9 });
  }, [map, startKey, destinationKey]);
  useMapEvents({ click: event => onPick(fromLatLng(map, event.latlng, start?.coordinate.plane || 0)) });
  const line = route?.segments.flatMap(segment => {
    if (segment.kind === "walk") return segment.path;
    if (segment.kind === "bank") return [segment.location];
    return [segment.from, segment.to];
  }).map(point => toLatLng(map, point)) || [];
  return <>
    {start && <Marker position={toLatLng(map, start.coordinate)} icon={startIcon} title={`Start: ${start.name}`} keyboard={false} />}
    {destination && <Marker position={toLatLng(map, destination.coordinate)} icon={destinationIcon}
      title={`Destination: ${destination.name}`} keyboard={false} />}
    {line.length > 1 && <Polyline positions={line} pathOptions={{ color: "#d27c2c", weight: 5 }} />}
  </>;
}

interface RouteMapProps {
  start?: Location;
  destination?: Location;
  route?: RoutePlan;
  onPick(point: WorldPoint): void;
}

export function RouteMap(props: RouteMapProps) {
  return <MapContainer className="route-map" center={[-79, -137]} zoom={7} minZoom={4} maxZoom={11}>
    <TileLayer
      url="https://raw.githubusercontent.com/Explv/osrs_map_tiles/master/0/{z}/{x}/{y}.png"
      minZoom={4}
      maxZoom={11}
      tms
      noWrap
      attribution='Map tiles: <a href="https://github.com/Explv/osrs_map_tiles">Explv</a> / Jagex'
    />
    <MapContents {...props} />
  </MapContainer>;
}
