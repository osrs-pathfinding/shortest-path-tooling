export interface ItemOption { key: string; name: string }

export async function findItems(query: string): Promise<ItemOption[]> {
  const response = await fetch(`/api/v1/items?q=${encodeURIComponent(query)}`);
  if (!response.ok) throw new Error("Could not search items");
  return response.json() as Promise<ItemOption[]>;
}

export async function lookupItems(keys: string[]): Promise<ItemOption[]> {
  if (!keys.length) return [];
  const response = await fetch(`/api/v1/items?ids=${encodeURIComponent(keys.join(","))}`);
  if (!response.ok) throw new Error("Could not load item names");
  return response.json() as Promise<ItemOption[]>;
}
