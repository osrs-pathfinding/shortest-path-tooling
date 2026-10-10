import { describe, expect, it } from "vitest";
import { formatDuration, routeErrorMessage } from "./Itinerary";
import { RouteApiError } from "../../api/routes";

describe("itinerary formatting", () => {
  it("formats game ticks as an approximate duration", () => {
    expect(formatDuration(10)).toBe("about 6s");
    expect(formatDuration(100)).toBe("about 1m");
  });

  it("gives saturation and timeout failures useful copy", () => {
    expect(routeErrorMessage(new RouteApiError("failed", 503))).toContain("busy");
    expect(routeErrorMessage(new RouteApiError("failed", 504))).toContain("timed out");
  });
});
