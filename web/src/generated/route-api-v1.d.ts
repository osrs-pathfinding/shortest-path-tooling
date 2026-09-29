/* Generated from shortest-path-corpus. Do not edit. */

export type RouteAPIV1 = RouteRequest | RoutePlan;

export interface RouteRequest {
  account: AccountBuildV1;
  start: Location;
  destination: Location;
  policy: RoutePolicyV1;
}
export interface AccountBuildV1 {
  schemaVersion: 1;
  id: string;
  name: string;
  benchmarkNowMinutes: number;
  levels: LevelMap;
  completedQuests: string[];
  diaries: {
    [k: string]: "NoDiary" | "Easy" | "Medium" | "Hard" | "Elite";
  };
  inventory: ItemMap;
  equipment: ItemMap;
  runePouch: ItemMap;
  bank: ItemMap;
  fairyRingsUnlocked: boolean;
  plantedSpiritTrees: string[];
  poh: Poh;
  runtime: Runtime;
  routingVariables: RoutingVariables;
}
export interface LevelMap {
  [k: string]: number;
}
export interface ItemMap {
  [k: string]: number;
}
export interface Poh {
  location: string;
  jewelleryBox: "NoJewelleryBox" | "FancyJewelleryBox" | "OrnateJewelleryBox";
  portals: {
    mode: "all" | "selected";
    destinations: string[];
  };
  fairyRing: boolean;
  spiritTree: boolean;
  obelisk: boolean;
  mountedGlory: boolean;
  mountedXerics: boolean;
  mountedDigsite: boolean;
  mountedMythical: boolean;
}
export interface Runtime {
  arriveInsidePoh: boolean;
  spellbook: "Standard" | "Ancient" | "Lunar" | "Arceuus";
  minigameTeleport:
    | {
        state: "ready";
      }
    | {
        state: "usedAt";
        minutes: number;
      };
}
/**
 * Compiled compatibility state. Public editors must not expose these raw identifiers.
 */
export interface RoutingVariables {
  varbits: IntegerMap;
  varplayers: IntegerMap;
}
export interface IntegerMap {
  [k: string]: number;
}
export interface Location {
  placeId?: string;
  name?: string;
  coordinate: WorldPoint;
}
export interface WorldPoint {
  x: number;
  y: number;
  plane: number;
}
export interface RoutePolicyV1 {
  avoidWilderness: boolean;
  banking: "allow" | "avoid" | "never";
  resources: "fastest" | "preserve-consumables";
  avoidedTransportTypes: string[];
}
export interface RoutePlan {
  apiVersion: "v1";
  reachable: boolean;
  costTicks: number | null;
  start: Location;
  destination: Location;
  segments: (WalkSegment | TravelSegment | BankSegment)[];
  metadata: {
    worldDataVersion: string;
    accountSchemaVersion: 1;
    routingEngineVersion: string;
  };
}
export interface WalkSegment {
  kind: "walk";
  costTicks: number;
  /**
   * @minItems 1
   */
  path: [WorldPoint, ...WorldPoint[]];
}
export interface TravelSegment {
  kind: "teleport" | "transport";
  transportId: string;
  name: string;
  from: WorldPoint;
  to: WorldPoint;
  costTicks: number;
  requirements: Capability[];
}
export interface Capability {
  kind: "quest" | "diary" | "skill" | "item" | "unlock";
  name: string;
}
export interface BankSegment {
  kind: "bank";
  location: WorldPoint;
  costTicks: number;
}
