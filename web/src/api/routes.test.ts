import { afterEach, describe, expect, it, vi } from "vitest";
import { calculateRoute, RouteApiError } from "./routes";

describe("route API", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("surfaces service errors", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({
      ok: false,
      status: 503,
      headers: { get: () => null },
      json: async () => ({ error: "route service is busy", requestId: "request-123" }),
    })));
    await expect(calculateRoute({} as never)).rejects.toThrow("route service is busy");
    await calculateRoute({} as never).catch(error => {
      expect(error).toBeInstanceOf(RouteApiError);
      expect(error).toMatchObject({ status: 503, requestId: "request-123" });
    });
  });
});
