import type { RoutePlan, RouteRequest } from "../domain/contracts";

export class RouteApiError extends Error {
  constructor(message: string, readonly status: number, readonly requestId?: string) {
    super(message);
    this.name = "RouteApiError";
  }
}

export async function calculateRoute(request: RouteRequest, signal?: AbortSignal): Promise<RoutePlan> {
  const response = await fetch("/api/v1/route", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(request),
    signal,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { error?: string; requestId?: string } | null;
    throw new RouteApiError(body?.error || `Route service returned ${response.status}`, response.status,
      body?.requestId || response.headers?.get?.("X-Request-ID") || undefined);
  }
  return response.json() as Promise<RoutePlan>;
}
