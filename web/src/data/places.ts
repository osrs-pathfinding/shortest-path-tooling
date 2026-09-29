import type { Location } from "../domain/contracts";

export const places: Location[] = [
  { placeId: "lumbridge", name: "Lumbridge", coordinate: { x: 3222, y: 3218, plane: 0 } },
  { placeId: "varrock", name: "Varrock", coordinate: { x: 3210, y: 3424, plane: 0 } },
  { placeId: "grand-exchange", name: "Grand Exchange", coordinate: { x: 3165, y: 3487, plane: 0 } },
  { placeId: "falador", name: "Falador", coordinate: { x: 2964, y: 3378, plane: 0 } },
  { placeId: "edgeville", name: "Edgeville", coordinate: { x: 3087, y: 3496, plane: 0 } },
  { placeId: "draynor-village", name: "Draynor Village", coordinate: { x: 3105, y: 3251, plane: 0 } },
  { placeId: "al-kharid", name: "Al Kharid", coordinate: { x: 3293, y: 3183, plane: 0 } },
  { placeId: "ardougne", name: "Ardougne", coordinate: { x: 2662, y: 3305, plane: 0 } },
  { placeId: "camelot", name: "Camelot", coordinate: { x: 2757, y: 3477, plane: 0 } },
  { placeId: "catherby", name: "Catherby", coordinate: { x: 2804, y: 3434, plane: 0 } },
  { placeId: "canifis", name: "Canifis", coordinate: { x: 3495, y: 3487, plane: 0 } },
  { placeId: "hosidius", name: "Hosidius", coordinate: { x: 1744, y: 3599, plane: 0 } },
];

export function locationParam(location: Location): string {
  return location.placeId || `${location.coordinate.x},${location.coordinate.y},${location.coordinate.plane}`;
}

export function locationFromParam(value: string | null): Location | undefined {
  if (!value) return undefined;
  const place = places.find(candidate => candidate.placeId === value);
  if (place) return place;
  const coordinates = value.split(",").map(Number);
  if (coordinates.length !== 3 || coordinates.some(coordinate => !Number.isInteger(coordinate))) return undefined;
  const [x, y, plane] = coordinates;
  if (x < 0 || x > 32767 || y < 0 || y > 32767 || plane < 0 || plane > 3) return undefined;
  return {
    name: `${x}, ${y}${plane ? `, plane ${plane}` : ""}`,
    coordinate: { x, y, plane },
  };
}
