/* Generated from shortest-path-corpus. Do not edit. */

export interface PlaceV1 {
  id: string;
  name: string;
  category: "bank" | "boss" | "clue" | "quest" | "hub" | "transport" | "other";
  coordinate: WorldPoint;
}
export interface WorldPoint {
  x: number;
  y: number;
  plane: number;
}
