import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { AppProviders } from "./app/AppProviders";
import { PublicRoutes } from "./routes";

const renderRoute = (path: string) => render(
  <AppProviders><MemoryRouter initialEntries={[path]}><PublicRoutes /></MemoryRouter></AppProviders>,
);

describe("public routes", () => {
  it("redirects the root to the route planner", async () => {
    renderRoute("/");
    expect(await screen.findByRole("heading", { name: "Where are you heading?" })).toBeTruthy();
  });

  it("does not mount the old debug viewer", () => {
    renderRoute("/debug");
    expect(screen.getByRole("heading", { name: "Page not found" })).toBeTruthy();
  });
});
