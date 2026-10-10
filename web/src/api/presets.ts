import type { Preset } from "../domain/contracts";

export async function loadPresets(): Promise<Preset[]> {
  const response = await fetch("/api/v1/presets");
  if (!response.ok) throw new Error("Could not load the account presets");
  return response.json() as Promise<Preset[]>;
}
