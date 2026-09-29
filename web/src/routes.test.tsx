import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { PublicRoutes } from "./routes";

describe("public routes", () => {
  it("redirects the root to the route planner", async () => {
    render(<MemoryRouter initialEntries={["/"]}><PublicRoutes /></MemoryRouter>);
    expect(await screen.findByRole("heading", { name: "Where are you heading?" })).toBeTruthy();
  });

  it("does not mount the old debug viewer", () => {
    render(<MemoryRouter initialEntries={["/debug"]}><PublicRoutes /></MemoryRouter>);
    expect(screen.getByRole("heading", { name: "Page not found" })).toBeTruthy();
  });
});
