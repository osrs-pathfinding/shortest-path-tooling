import type { Account } from "./contracts";

/** An account the planner can select: a preset, the saved custom build, or one opened from a link. */
export interface PlannerAccount {
  id: string;
  name: string;
  account: Account;
}

// The in-game skills tab order, read left to right.
const skillOrder = [
  "ATTACK", "HITPOINTS", "MINING", "STRENGTH", "AGILITY", "SMITHING", "DEFENCE", "HERBLORE",
  "FISHING", "RANGED", "THIEVING", "COOKING", "PRAYER", "CRAFTING", "FIREMAKING", "MAGIC",
  "FLETCHING", "WOODCUTTING", "RUNECRAFT", "SLAYER", "FARMING", "CONSTRUCTION", "HUNTER", "SAILING",
];

/** Skill ids in the in-game skills tab order; skills the tab does not list go last. */
export function orderSkills(skills: string[]): string[] {
  const rank = (skill: string) => {
    const index = skillOrder.indexOf(skill);
    return index < 0 ? skillOrder.length : index;
  };
  return [...skills].sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
}

export function totalLevel(levels: Account["levels"]): number {
  return Object.values(levels).reduce((total, level) => total + (Number(level) || 0), 0);
}
