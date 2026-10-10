/* Generated from shortest-path-corpus and service/src/main/resources/schemas. Do not edit. */

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
/**
 * The routing settings of the shortest-path plugin that are not facts about the account. Optional fields default to the behaviour of clients that omit them.
 */
export interface RoutePolicyV1 {
  avoidWilderness: boolean;
  /**
   * Whether routes may withdraw items from the bank. avoid only banks when it saves a lot of time.
   */
  banking: "allow" | "avoid" | "never";
  /**
   * How consumable teleports (tablets, charged jewellery, whistles) are treated. permanent-only never uses them.
   */
  resources: "fastest" | "preserve-consumables" | "permanent-only";
  /**
   * Transport types the route must not use. TELEPORTATION_ITEM also disables teleport boxes.
   */
  avoidedTransportTypes: (
    | "AGILITY_SHORTCUT"
    | "GRAPPLE_SHORTCUT"
    | "BOAT"
    | "CANOE"
    | "CHARTER_SHIP"
    | "SHIP"
    | "FAIRY_RING"
    | "GNOME_GLIDER"
    | "HOT_AIR_BALLOON"
    | "MAGIC_CARPET"
    | "MAGIC_MUSHTREE"
    | "MINECART"
    | "QUETZAL"
    | "SPIRIT_TREE"
    | "TELEPORTATION_ITEM"
    | "TELEPORTATION_LEVER"
    | "TELEPORTATION_MINIGAME"
    | "TELEPORTATION_PORTAL"
    | "TELEPORTATION_SPELL"
    | "TELEPORTATION_SPELL_HOME"
    | "WILDERNESS_OBELISK"
  )[];
  /**
   * owned uses only the account's items; any assumes every teleport item is available.
   */
  teleportItems?: "owned" | "any";
  /**
   * The most coins, trading sticks, ecto-tokens or warrior guild tokens spent on one transport. Omit for no limit.
   */
  currencyThreshold?: number;
  /**
   * How many ticks a transport type must save over the alternatives to be used.
   */
  transportThresholds?: {
    [k: string]: number;
  };
  /**
   * Account state the game does not report to the plugin, declared without verification.
   */
  declaredUnlocks?: ("PRIFDDINAS_RESPAWN" | "CANOE_AXE" | "XERICS_HONOUR" | "DRAGONTOOTH_PASSAGE")[];
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
