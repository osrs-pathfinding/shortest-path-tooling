/* Generated from shortest-path-corpus. Do not edit. */

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
