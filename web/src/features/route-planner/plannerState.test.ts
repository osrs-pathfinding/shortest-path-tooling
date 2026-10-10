import { describe, expect, it } from "vitest";
import { account } from "../../test/fixtures";
import {
  buildShareUrl, decodeSharedProfile, encodeSharedProfile, plannerUrlWarnings, profileFromHash, readPlannerUrl,
  readStoredSettings, SharedProfileError, writePlannerUrl,
} from "./plannerState";

describe("planner URL state", () => {
  it("round trips endpoints and changed settings", () => {
    const state = {
      start: { name: "Custom", coordinate: { x: 3200, y: 3201, plane: 1 } },
      destination: { name: "Lumbridge", placeId: "lumbridge", coordinate: { x: 3222, y: 3218, plane: 0 } },
      accountId: "early",
      settings: { avoidWilderness: false, costBoats: 12, useTeleportationItems: "ALL" },
    };
    const params = writePlannerUrl(state);
    expect(params.get("costBoats")).toBe("12");
    expect(readPlannerUrl(params)).toEqual({ ...state, start: { ...state.start, name: "3200, 3201, plane 1" } });
  });

  it("uses the stored settings only when the URL has no planner state", () => {
    expect(readPlannerUrl(new URLSearchParams(), { useFairyRings: false }).settings).toEqual({ useFairyRings: false });
    expect(readPlannerUrl(new URLSearchParams("account=mid"), { useFairyRings: false }).settings).toEqual({});
  });

  it("embeds a complete custom account in a self-contained link", async () => {
    const url = new URL(buildShareUrl("https://osrs.travel/route", {
      accountId: "custom", settings: {},
      start: { name: "Lumbridge", placeId: "lumbridge", coordinate: { x: 3222, y: 3218, plane: 0 } },
    }, "custom", { ...account, name: "My build" }));
    expect(url.searchParams.get("account")).toBe("shared");
    await expect(profileFromHash(url.hash)).resolves.toMatchObject({ name: "My build", levels: account.levels });
  });

  it("names presets in links instead of embedding them", () => {
    const url = new URL(buildShareUrl("https://osrs.travel/route", { accountId: "maxed", settings: {} }, "maxed", account));
    expect(url.searchParams.get("account")).toBe("maxed");
    expect(url.hash).toBe("");
  });

  it("rejects malformed and oversized profiles", async () => {
    await expect(decodeSharedProfile("v1.not-gzip")).rejects.toThrow(SharedProfileError);
    await expect(decodeSharedProfile("v2.payload")).rejects.toThrow("unsupported");
    const huge = { ...account, bank: Object.fromEntries(Array.from({ length: 20000 }, (_, index) => [String(index), (index * 7919) % 99991 + 1])) };
    expect(() => encodeSharedProfile(huge)).toThrow("too large");
    expect(() => encodeSharedProfile({ ...account, name: "x".repeat(140_000) })).toThrow("too large");
  });

  it("reports invalid shared state instead of silently accepting it", () => {
    expect(plannerUrlWarnings(new URLSearchParams("from=bad&account=missing"), ["mid"]))
      .toEqual([expect.stringContaining("start location"), expect.stringContaining("account")]);
  });

  it("keeps only well-typed stored settings", () => {
    expect(readStoredSettings('{"useFairyRings":false,"costBoats":3,"broken":[1],"half":1.5}'))
      .toEqual({ useFairyRings: false, costBoats: 3 });
    expect(readStoredSettings("broken")).toEqual({});
  });

  it("rejects coordinates outside the public route contract", () => {
    for (const from of ["1.5,2,0", "Infinity,2,0", "-1,2,0", "1,2,4"]) {
      const params = new URLSearchParams({ from });
      expect(readPlannerUrl(params).start).toBeUndefined();
      expect(plannerUrlWarnings(params, ["mid"])).toContain("The shared start location was invalid and has been ignored.");
    }
  });
});
