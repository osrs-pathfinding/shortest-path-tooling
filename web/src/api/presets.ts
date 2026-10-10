import type { AccountBuild } from "../domain/contracts";

export const presetIds = ["early", "mid", "end", "maxed"] as const;

export async function loadPresets(): Promise<AccountBuild[]> {
  return Promise.all(presetIds.map(async id => {
    const response = await fetch(`/data/profiles/${id}.json`);
    if (!response.ok) throw new Error(`Could not load ${id} account preset`);
    return response.json() as Promise<AccountBuild>;
  }));
}
