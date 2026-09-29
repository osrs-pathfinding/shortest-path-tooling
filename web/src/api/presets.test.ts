import { afterEach, describe, expect, it, vi } from "vitest";
import { loadPresets } from "./presets";

describe("account presets", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("loads every canonical preset", async () => {
    const fetch = vi.fn(async (url: string) => ({
      ok: true,
      json: async () => ({ id: url.match(/([^/]+)\.json$/)?.[1] }),
    }));
    vi.stubGlobal("fetch", fetch);

    expect((await loadPresets()).map(preset => preset.id)).toEqual(["early", "mid", "end", "maxed"]);
    expect(fetch).toHaveBeenCalledTimes(4);
  });
});
