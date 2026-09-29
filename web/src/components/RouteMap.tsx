import { useEffect } from "react";
import { MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap, useMapEvents } from "react-leaflet";
import type { LatLngExpression, Map as LeafletMap } from "leaflet";
import type { Location, RoutePlan, WorldPoint } from "../domain/contracts";
import "leaflet/dist/leaflet.css";

const MAP_HEIGHT = 364544;
const TILE_SIZE = 32;
const OFFSET_X = 960;
const OFFSET_Y = 6208;

function toLatLng(map: LeafletMap, point: WorldPoint): LatLngExpression {
  const x = ((point.x - OFFSET_X) * TILE_SIZE) + TILE_SIZE / 4;
  const y = MAP_HEIGHT - ((point.y - OFFSET_Y) * TILE_SIZE);
  return map.unproject([x, y], map.getMaxZoom());
}

function fromLatLng(map: LeafletMap, latlng: { lat: number; lng: number }, plane: number): WorldPoint {
  const projected = map.project(latlng, map.getMaxZoom());
  const y = Math.round((MAP_HEIGHT - projected.y - TILE_SIZE) / TILE_SIZE) + OFFSET_Y;
  const x = Math.round((projected.x - TILE_SIZE) / TILE_SIZE) + OFFSET_X;
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
  useMapEvents({ click: event => onPick(fromLatLng(map, event.latlng, start?.coordinate.plane || 0)) });
  const line = route?.segments.flatMap(segment => {
    if (segment.kind === "walk") return segment.path;
    if (segment.kind === "bank") return [segment.location];
    return [segment.from, segment.to];
  }).map(point => toLatLng(map, point)) || [];
  return <>
    {start && <Marker position={toLatLng(map, start.coordinate)}><Tooltip permanent>A</Tooltip></Marker>}
    {destination && <Marker position={toLatLng(map, destination.coordinate)}><Tooltip permanent>B</Tooltip></Marker>}
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
