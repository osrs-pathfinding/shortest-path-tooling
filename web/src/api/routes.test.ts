import { afterEach, describe, expect, it, vi } from "vitest";
import { calculateRoute } from "./routes";

describe("route API", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("surfaces service errors", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({
      ok: false,
      status: 503,
      json: async () => ({ error: "route service is busy" }),
    })));
    await expect(calculateRoute({} as never)).rejects.toThrow("route service is busy");
  });
});
