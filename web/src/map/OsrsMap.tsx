import { useEffect, useMemo, useState } from "react";
import { divIcon, latLngBounds, type LatLng, type Map as LeafletMap } from "leaflet";
import { MapContainer, Marker, Polyline, TileLayer, useMap, useMapEvents } from "react-leaflet";
import type { Location, RoutePlan, WorldPoint } from "../domain/contracts";
import "leaflet/dist/leaflet.css";

const MAP_HEIGHT = 364544;
const TILE_SIZE = 32;
const OFFSET_X = 960;
const OFFSET_Y = 6208;

const endpointIcon = (label: string, kind: string) => divIcon({
  className: `route-marker ${kind}`,
  html: `<span>${label}</span>`,
  iconSize: [30, 40],
  iconAnchor: [15, 40],
});
const startIcon = endpointIcon("A", "start");
const destinationIcon = endpointIcon("B", "destination");
const bankIcon = divIcon({ className: "bank-marker", html: "<span>◇</span>", iconSize: [24, 24], iconAnchor: [12, 12] });

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

export function segmentPoints(segment: RoutePlan["segments"][number]): WorldPoint[] {
  if (segment.kind === "walk") return segment.path;
  if (segment.kind === "bank") return [segment.location];
  return [segment.from, segment.to];
}

function fitPoints(map: LeafletMap, points: WorldPoint[], maxZoom = 9) {
  if (!points.length) return;
  const bounds = latLngBounds(points.map(point => toLatLng(map, point)));
  if (points.length === 1) map.setView(bounds.getCenter(), maxZoom);
  else map.fitBounds(bounds, { padding: [70, 70], maxZoom });
}

function MapContents({ start, destination, route, selectedSegment, fitRequest, onPick, onMoveStart,
  onMoveDestination, onSelectSegment }: OsrsMapProps & { fitRequest: number }) {
  const map = useMap();
  useEffect(() => {
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(() => map.invalidateSize());
    observer.observe(map.getContainer());
    return () => observer.disconnect();
  }, [map]);

  const routePoints = useMemo(() => route?.segments.flatMap(segmentPoints) || [], [route]);
  const endpointKey = `${start?.coordinate.x},${start?.coordinate.y},${start?.coordinate.plane}|${destination?.coordinate.x},${destination?.coordinate.y},${destination?.coordinate.plane}`;
  useEffect(() => {
    if (routePoints.length) fitPoints(map, routePoints);
    else fitPoints(map, [start?.coordinate, destination?.coordinate].filter(Boolean) as WorldPoint[]);
  }, [map, route, endpointKey]);
  useEffect(() => {
    if (fitRequest) fitPoints(map, routePoints.length ? routePoints : [start?.coordinate, destination?.coordinate].filter(Boolean) as WorldPoint[]);
  }, [fitRequest]);
  useEffect(() => {
    if (selectedSegment === undefined || !route?.segments[selectedSegment]) return;
    fitPoints(map, segmentPoints(route.segments[selectedSegment]), 10);
  }, [map, route, selectedSegment]);

  useMapEvents({ click: event => onPick(fromLatLng(map, event.latlng, start?.coordinate.plane ?? destination?.coordinate.plane ?? 0)) });

  return <>
    {start && <Marker position={toLatLng(map, start.coordinate)} icon={startIcon} title={`Start: ${start.name}`}
      draggable keyboard={false} eventHandlers={{ dragend: event => onMoveStart?.(fromLatLng(map, event.target.getLatLng(), start.coordinate.plane)) }} />}
    {destination && <Marker position={toLatLng(map, destination.coordinate)} icon={destinationIcon} title={`Destination: ${destination.name}`}
      draggable keyboard={false} eventHandlers={{ dragend: event => onMoveDestination?.(fromLatLng(map, event.target.getLatLng(), destination.coordinate.plane)) }} />}
    {route?.segments.map((segment, index) => {
      const selected = selectedSegment === index;
      if (segment.kind === "bank") return <Marker key={`bank-${index}`} position={toLatLng(map, segment.location)} icon={bankIcon}
        title="Bank stop" keyboard={false} eventHandlers={{ click: () => onSelectSegment?.(index) }} />;
      return <Polyline key={`${segment.kind}-${index}`} positions={segmentPoints(segment).map(point => toLatLng(map, point))}
        className={`route-segment ${segment.kind}${selected ? " selected" : ""}`}
        pathOptions={segment.kind === "walk"
          ? { color: selected ? "#ffd36a" : "#d27c2c", weight: selected ? 8 : 5, opacity: .95 }
          : { color: segment.kind === "teleport" ? "#8f62bd" : "#398e82", weight: selected ? 7 : 4, dashArray: "8 9", opacity: .9 }}
        eventHandlers={{ click: () => onSelectSegment?.(index), mouseover: () => onSelectSegment?.(index) }} />;
    })}
  </>;
}

export interface OsrsMapProps {
  start?: Location;
  destination?: Location;
  route?: RoutePlan;
  selectedSegment?: number;
  onPick(point: WorldPoint): void;
  onMoveStart?(point: WorldPoint): void;
  onMoveDestination?(point: WorldPoint): void;
  onSelectSegment?(index?: number): void;
}

export function OsrsMap(props: OsrsMapProps) {
  const [fitRequest, setFitRequest] = useState(0);
  return <>
    <MapContainer className="route-map" center={[-79, -137]} zoom={7} minZoom={4} maxZoom={11}>
      <TileLayer url="https://raw.githubusercontent.com/Explv/osrs_map_tiles/master/0/{z}/{x}/{y}.png"
        minZoom={4} maxZoom={11} tms noWrap
        attribution='Map tiles: <a href="https://github.com/Explv/osrs_map_tiles">Explv</a> / Jagex' />
      <MapContents {...props} fitRequest={fitRequest} />
    </MapContainer>
    <button type="button" className="fit-route-button" onClick={() => setFitRequest(value => value + 1)}>Fit route</button>
  </>;
}
