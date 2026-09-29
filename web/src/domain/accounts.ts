import type { AccountBuild } from "./contracts";

const presetLabels: Record<string, string> = {
  early: "Early game", mid: "Mid game", end: "End game", maxed: "Maxed",
};

// The in-game skills tab order, read left to right.
const skillOrder = [
  "Attack", "Hitpoints", "Mining", "Strength", "Agility", "Smithing", "Defence", "Herblore",
  "Fishing", "Ranged", "Thieving", "Cooking", "Prayer", "Crafting", "Firemaking", "Magic",
  "Fletching", "Woodcutting", "Runecraft", "Slayer", "Farming", "Construction", "Hunter", "Sailing",
];
const derivedLevels = new Set(["Total", "Quest"]);

export function accountLabel(account: AccountBuild): string {
  return account.id === "custom" ? account.name : presetLabels[account.id] || account.name;
}

export function skillNames(levels: AccountBuild["levels"]): string[] {
  const rank = (skill: string) => {
    const index = skillOrder.indexOf(skill);
    return index < 0 ? skillOrder.length : index;
  };
  return Object.keys(levels).filter(name => !derivedLevels.has(name))
    .sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
}

export function totalLevel(levels: AccountBuild["levels"]): number {
  return skillNames(levels).reduce((total, skill) => total + (Number(levels[skill]) || 0), 0);
}
