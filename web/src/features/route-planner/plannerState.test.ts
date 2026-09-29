import { describe, expect, it } from "vitest";
import early from "../../../public/data/profiles/early.json";
import type { AccountBuild } from "../../domain/contracts";
import {
  buildShareUrl, decodeSharedProfile, defaultPolicy, encodeSharedProfile, profileFromHash,
  plannerUrlWarnings, readPlannerUrl, SharedProfileError, writePlannerUrl,
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
    expect(readPlannerUrl(writePlannerUrl(state))).toEqual({ ...state, start: { ...state.start, name: "3200, 3201" } });
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
  });

  it("reports invalid shared state instead of silently accepting it", () => {
    expect(plannerUrlWarnings(new URLSearchParams("from=bad&account=missing&banking=sometimes"), ["mid"]))
      .toEqual(expect.arrayContaining([expect.stringContaining("start location"), expect.stringContaining("account"), expect.stringContaining("banking")]));
  });
});
