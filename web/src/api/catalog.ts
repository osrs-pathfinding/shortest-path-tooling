import type { Catalog } from "../domain/contracts";

/** Every account fact's possible values and every planner setting, from the route service. */
export async function loadCatalog(): Promise<Catalog> {
  const response = await fetch("/api/v1/catalog");
  if (!response.ok) throw new Error("Could not load the planner options");
  return response.json() as Promise<Catalog>;
}
