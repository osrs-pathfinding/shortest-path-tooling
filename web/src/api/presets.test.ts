import { afterEach, describe, expect, it, vi } from "vitest";
import { loadPresets } from "./presets";

describe("account presets", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("loads the presets from the route service", async () => {
    const fetch = vi.fn(async () => ({ ok: true, json: async () => [{ id: "early" }, { id: "mid" }] }));
    vi.stubGlobal("fetch", fetch);

    expect((await loadPresets()).map(preset => preset.id)).toEqual(["early", "mid"]);
    expect(fetch).toHaveBeenCalledWith("/api/v1/presets");
  });
});
