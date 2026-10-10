import type { RoutePolicy } from "./contracts";

export type AvoidableTransportType = RoutePolicy["avoidedTransportTypes"][number];
export type DeclaredUnlock = NonNullable<RoutePolicy["declaredUnlocks"]>[number];
export type ThresholdTransportType = AvoidableTransportType | "TELEPORTATION_BOX";

export interface TransportOption {
  type: ThresholdTransportType;
  label: string;
  /** False for types the planner cannot switch off on their own. */
  avoidable: boolean;
}

const option = (type: ThresholdTransportType, label: string, avoidable = true): TransportOption => ({ type, label, avoidable });

/** Every transport setting the planner exposes, grouped as the plugin's config panel orders them. */
export const transportGroups: { title: string; options: TransportOption[] }[] = [
  { title: "Shortcuts", options: [option("AGILITY_SHORTCUT", "Agility shortcuts"), option("GRAPPLE_SHORTCUT", "Grapple shortcuts")] },
  {
    title: "Boats",
    options: [option("BOAT", "Boats"), option("CANOE", "Canoes"), option("CHARTER_SHIP", "Charter ships"), option("SHIP", "Ships")],
  },
  {
    title: "Travel networks",
    options: [
      option("FAIRY_RING", "Fairy rings"), option("GNOME_GLIDER", "Gnome gliders"), option("HOT_AIR_BALLOON", "Hot air balloons"),
      option("MAGIC_CARPET", "Magic carpets"), option("MAGIC_MUSHTREE", "Magic mushtrees"), option("MINECART", "Minecarts"),
      option("QUETZAL", "Quetzals"), option("SPIRIT_TREE", "Spirit trees"),
    ],
  },
  {
    title: "Teleports",
    options: [
      option("TELEPORTATION_ITEM", "Teleport items"), option("TELEPORTATION_BOX", "Teleport boxes", false),
      option("TELEPORTATION_SPELL", "Teleport spells"), option("TELEPORTATION_SPELL_HOME", "Home teleports"),
      option("TELEPORTATION_MINIGAME", "Minigame teleports"), option("TELEPORTATION_PORTAL", "Portals"),
      option("TELEPORTATION_LEVER", "Levers"), option("WILDERNESS_OBELISK", "Wilderness obelisks"),
    ],
  },
];

const transportOptions = transportGroups.flatMap(group => group.options);
export const avoidableTransportTypes = transportOptions.filter(item => item.avoidable)
  .map(item => item.type as AvoidableTransportType);
export const thresholdTransportTypes = transportOptions.map(item => item.type);
export const maxTransportThreshold = 10_000;
export const maxCurrencyThreshold = 2_147_483_647;

export const declaredUnlocks: { unlock: DeclaredUnlock; label: string; description: string }[] = [
  { unlock: "PRIFDDINAS_RESPAWN", label: "Prifddinas respawn point", description: "Your respawn point is set to Prifddinas." },
  { unlock: "CANOE_AXE", label: "Axe stored at a canoe station", description: "Canoes can be shaped without carrying an axe." },
  { unlock: "XERICS_HONOUR", label: "Xeric's Honour", description: "An ancient tablet has been used on your Xeric's talisman." },
  { unlock: "DRAGONTOOTH_PASSAGE", label: "Dragontooth Island free passage", description: "The Ghosts Ahoy ghost captain has been paid." },
];
const unlockIds = declaredUnlocks.map(item => item.unlock);

export function isAvoidableTransportType(value: unknown): value is AvoidableTransportType {
  return avoidableTransportTypes.includes(value as AvoidableTransportType);
}

export function isThresholdTransportType(value: unknown): value is ThresholdTransportType {
  return thresholdTransportTypes.includes(value as ThresholdTransportType);
}

export function isDeclaredUnlock(value: unknown): value is DeclaredUnlock {
  return unlockIds.includes(value as DeclaredUnlock);
}

export function isTransportThreshold(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) >= 0 && (value as number) <= maxTransportThreshold;
}

export function isCurrencyThreshold(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) >= 0 && (value as number) <= maxCurrencyThreshold;
}
