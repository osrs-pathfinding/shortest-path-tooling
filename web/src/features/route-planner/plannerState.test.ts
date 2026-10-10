import { describe, expect, it } from "vitest";
import early from "../../../public/data/profiles/early.json";
import type { AccountBuild } from "../../domain/contracts";
import {
  buildShareUrl, decodeSharedProfile, defaultPolicy, encodeSharedProfile, profileFromHash,
  isDefaultPolicy, normalizePolicy, plannerUrlWarnings, readPlannerUrl, readStoredPolicy, SharedProfileError, writePlannerUrl,
} from "./plannerState";

const account = early as AccountBuild;

describe("planner URL state", () => {
  it("round trips endpoints and non-default policy", () => {
    const state = {
      start: { name: "Custom", coordinate: { x: 3200, y: 3201, plane: 1 } },
      destination: { name: "Lumbridge", placeId: "lumbridge", coordinate: { x: 3222, y: 3218, plane: 0 } },
      accountId: "early",
      policy: { ...defaultPolicy, avoidWilderness: false, banking: "never" as const },
    };
    expect(readPlannerUrl(writePlannerUrl(state))).toEqual({ ...state, start: { ...state.start, name: "3200, 3201, plane 1" } });
  });

  it("embeds a complete custom account in a self-contained link", async () => {
    const custom = { ...account, id: "custom", name: "My build" };
    const url = new URL(buildShareUrl("https://osrs.travel/route", {
      accountId: "custom", policy: defaultPolicy,
      start: { name: "Lumbridge", placeId: "lumbridge", coordinate: { x: 3222, y: 3218, plane: 0 } },
    }, custom));
    expect(url.searchParams.get("account")).toBe("shared");
    await expect(profileFromHash(url.hash)).resolves.toMatchObject({ id: "shared", name: "My build", levels: custom.levels });
  });

  it("rejects malformed and oversized profiles", async () => {
    await expect(decodeSharedProfile("v1.not-gzip")).rejects.toThrow(SharedProfileError);
    await expect(decodeSharedProfile("v2.payload")).rejects.toThrow("unsupported");
    const huge = { ...account, id: "custom", name: "x".repeat(20_000) };
    // Highly repetitive content may compress well, so use incompressible-enough item keys.
    huge.bank = Object.fromEntries(Array.from({ length: 5000 }, (_, index) => [`ITEM_${index.toString(36).toUpperCase()}_${(index * 7919).toString(36).toUpperCase()}`, index + 1]));
    expect(() => encodeSharedProfile(huge)).toThrow("too large");
    expect(() => encodeSharedProfile({ ...account, name: "x".repeat(130_000) })).toThrow("too large");
  });

  it("reports invalid shared state instead of silently accepting it", () => {
    expect(plannerUrlWarnings(new URLSearchParams("from=bad&account=missing&banking=sometimes"), ["mid"]))
      .toEqual(expect.arrayContaining([expect.stringContaining("start location"), expect.stringContaining("account"), expect.stringContaining("banking")]));
  });

  it("sanitizes stored policy instead of trusting browser storage", () => {
    expect(readStoredPolicy('{"avoidWilderness":false,"banking":"sometimes","avoidedTransportTypes":["BOAT","BOAT",4]}'))
      .toEqual({ ...defaultPolicy, avoidWilderness: false, avoidedTransportTypes: ["BOAT"] });
    expect(readStoredPolicy("broken")).toEqual(defaultPolicy);
    expect(isDefaultPolicy(readStoredPolicy(null))).toBe(true);
  });

  it("round trips every plugin option through the URL in canonical form", () => {
    const policy = normalizePolicy({
      ...defaultPolicy, banking: "avoid", resources: "permanent-only", teleportItems: "any", currencyThreshold: 0,
      avoidedTransportTypes: ["SPIRIT_TREE", "CANOE"], declaredUnlocks: ["XERICS_HONOUR", "CANOE_AXE"],
      transportThresholds: { TELEPORTATION_BOX: 10000, FAIRY_RING: 12, BOAT: 0 },
    });
    const params = writePlannerUrl({ accountId: "mid", policy });
    expect(params.get("avoid")).toBe("CANOE,SPIRIT_TREE");
    expect(params.get("thresholds")).toBe("FAIRY_RING:12,TELEPORTATION_BOX:10000");
    expect(params.get("fare")).toBe("0");
    expect(readPlannerUrl(params).policy).toEqual(policy);
    expect(plannerUrlWarnings(params, ["mid"])).toEqual([]);
    expect(isDefaultPolicy(readPlannerUrl(new URLSearchParams()).policy)).toBe(true);
  });

  it("drops and reports invalid plugin options from shared links", () => {
    const params = new URLSearchParams("avoid=BOAT,TELEPORTATION_BOX&thresholds=BOAT:5,SHIP:-1,SEASONAL_TRANSPORTS:2,MINECART:10001"
      + "&fare=lots&unlocks=CANOE_AXE,EVERYTHING&items=some");
    expect(readPlannerUrl(params).policy).toEqual({
      ...defaultPolicy, avoidedTransportTypes: ["BOAT"], transportThresholds: { BOAT: 5 }, declaredUnlocks: ["CANOE_AXE"],
    });
    expect(plannerUrlWarnings(params, ["mid"])).toEqual([
      "Unknown avoided transports were ignored.", "An invalid teleport item setting was ignored.",
      "An invalid fare limit was ignored.", "Invalid transport thresholds were ignored.", "Unknown unlocks were ignored.",
    ]);
  });

  it("rejects coordinates outside the public route contract", () => {
    for (const from of ["1.5,2,0", "Infinity,2,0", "-1,2,0", "1,2,4"]) {
      const params = new URLSearchParams({ from });
      expect(readPlannerUrl(params).start).toBeUndefined();
      expect(plannerUrlWarnings(params, ["mid"])).toContain("The shared start location was invalid and has been ignored.");
    }
  });
});
