import type { Account, Catalog, Setting } from "../domain/contracts";
import type { PlannerAccount } from "../domain/accounts";

const setting = (key: string, name: string, type: Setting["type"], defaultValue: Setting["defaultValue"], section = "Settings",
  choices?: Setting["choices"]): Setting => ({ key, name, description: name, section, type, defaultValue, ...choices ? { choices } : {} });

/** A small slice of the route service's catalog. */
export const catalog: Catalog = {
  skills: [{ id: "AGILITY", name: "Agility" }, { id: "ATTACK", name: "Attack" }],
  quests: [{ id: "LOST_CITY", name: "Lost City" }, { id: "DRAGON_SLAYER_I", name: "Dragon Slayer I" }],
  diaries: [{ id: "ARDOUGNE", name: "Ardougne" }, { id: "VARROCK", name: "Varrock" }],
  diaryTiers: ["NONE", "EASY", "MEDIUM", "HARD", "ELITE"].map(id => ({ id, name: id[0] + id.slice(1).toLowerCase() })),
  unlocks: [
    { id: "FAIRY_RINGS", name: "Fairy rings", group: "Transport" },
    { id: "BALLOON_ENTRANA", name: "Balloon entrana", group: "Hot air balloon" },
  ],
  spellbooks: ["STANDARD", "ANCIENT", "LUNAR", "ARCEUUS"].map(id => ({ id, name: id[0] + id.slice(1).toLowerCase() })),
  houseLocations: [{ id: "RIMMINGTON", name: "Rimmington" }, { id: "YANILLE", name: "Yanille" }],
  jewelleryBoxes: ["NONE", "FANCY", "ORNATE"].map(id => ({ id, name: id[0] + id.slice(1).toLowerCase() })),
  portals: [{ id: "Varrock Portal", name: "Varrock" }],
  plantedSpiritTrees: [{ id: "FARMING_GUILD", name: "Farming Guild" }],
  settings: [
    setting("avoidWilderness", "Avoid wilderness", "boolean", true),
    setting("includeBankPath", "Include path to bank", "boolean", true),
    setting("useTeleportationItems", "Use teleportation items", "choice", "INVENTORY_AND_BANK", "Settings",
      [{ id: "NONE", name: "None" }, { id: "INVENTORY_AND_BANK", name: "Inventory and bank" }, { id: "ALL", name: "All" }]),
    setting("useFairyRings", "Use fairy rings", "boolean", true),
    setting("unlockCanoeAxe", "Axe stored at a canoe station", "boolean", false),
    setting("costBoats", "Boat threshold", "integer", 0, "Transport Thresholds"),
    setting("pathfinderBackend", "Pathfinder backend", "choice", "EXACT", "Settings",
      [{ id: "LEGACY", name: "Legacy" }, { id: "EXACT", name: "Exact" }]),
  ],
};

export const account: Account = {
  name: "Mid game",
  levels: { AGILITY: 70, ATTACK: 60 },
  questPoints: 50,
  completedQuests: ["LOST_CITY"],
  diaries: { ARDOUGNE: "MEDIUM" },
  unlocks: ["FAIRY_RINGS"],
  spellbook: "STANDARD",
  nowMinutes: 100000000,
  inventory: { "995": 10 }, runePouch: {}, equipment: {}, bank: {},
  house: {
    location: "RIMMINGTON", jewelleryBox: "NONE", fairyRing: false, spiritTree: false, obelisk: false,
    mountedGlory: false, mountedXerics: false, mountedDigsite: false, mountedMythical: false, portals: [],
  },
  plantedSpiritTrees: [],
};

export const presets: PlannerAccount[] = [
  { id: "mid", name: "Mid game", account },
  { id: "maxed", name: "Maxed", account: { ...account, name: "Maxed" } },
];
