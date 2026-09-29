import type { RoutePlan, RouteRequest } from "../domain/contracts";

export async function calculateRoute(request: RouteRequest, signal?: AbortSignal): Promise<RoutePlan> {
  const response = await fetch("/api/v1/route", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(request),
    signal,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { error?: string } | null;
    throw new Error(body?.error || `Route service returned ${response.status}`);
  }
  return response.json() as Promise<RoutePlan>;
}
