import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountBuild } from "../domain/contracts";
import { AccountPanel } from "./AccountPanel";

afterEach(() => { vi.unstubAllGlobals(); cleanup(); });

function renderPanel(props: Partial<Parameters<typeof AccountPanel>[0]> = {}) {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, json: async () => [{ key: "995", name: "Coins" }] }));
  const handlers = { onSave: vi.fn(), onSelect: vi.fn(), onClose: vi.fn() };
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <AccountPanel accounts={[account, { ...account, id: "maxed", name: "maxed" }]} account={account} quests={["Lost City"]} open
      {...handlers} {...props} />
  </QueryClientProvider>);
  return handlers;
}

describe("account panel", () => {
  it("saves semantic changes without exposing routing variables", async () => {
    const { onSave } = renderPanel();

    expect(screen.getByLabelText("Agility").closest("label")?.querySelector("img")?.src).toMatch(/^data:image\/png;base64,/);
    const coins = await screen.findByRole("button", { name: "Coins, quantity 10" });
    expect(coins.querySelector("img")?.src).toBe("https://chisel.weirdgloop.org/static/img/osrs-sprite/995.png");
    fireEvent.click(coins);
    expect((screen.getByLabelText("Coins quantity") as HTMLInputElement).value).toBe("10");

    fireEvent.change(screen.getByLabelText("Build name"), { target: { value: "My build" } });
    fireEvent.change(screen.getByLabelText("Agility"), { target: { value: "80" } });
    fireEvent.change(screen.getByLabelText("Quest points"), { target: { value: "123" } });
    fireEvent.click(within(screen.getByRole("group", { name: "Ardougne" })).getByLabelText("Elite"));
    fireEvent.click(within(screen.getByRole("group", { name: "Active spellbook" })).getByLabelText("Lunar"));
    fireEvent.click(screen.getByLabelText(/^Fairy rings/));
    fireEvent.click(screen.getByRole("button", { name: "Save as custom" }));

    await waitFor(() => expect(onSave).toHaveBeenCalled());
    const saved = onSave.mock.calls[0][0] as AccountBuild;
    expect(saved).toMatchObject({ id: "custom", name: "My build", fairyRingsUnlocked: false });
    expect(saved.levels.Agility).toBe(80);
    expect(saved.levels.Quest).toBe(123);
    expect(saved.diaries.Ardougne).toBe("Elite");
    expect(saved.runtime.spellbook).toBe("Lunar");
    expect(saved.routingVariables).toEqual(account.routingVariables);
    expect(screen.queryByText("routingVariables")).toBeNull();
  });

  it("switches profiles from the sidebar and confirms before discarding edits", () => {
    const confirm = vi.fn().mockReturnValue(false);
    vi.stubGlobal("confirm", confirm);
    const { onSelect } = renderPanel();
    expect(screen.getByRole("heading", { name: "Mid game" })).toBeTruthy();

    fireEvent.click(screen.getByLabelText(/^Maxed/));
    expect(onSelect).toHaveBeenLastCalledWith("maxed");
    expect(confirm).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText("Agility"), { target: { value: "71" } });
    expect(screen.getByText("Unsaved changes")).toBeTruthy();
    onSelect.mockClear();
    fireEvent.click(screen.getByLabelText(/^Maxed/));
    expect(confirm).toHaveBeenCalled();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it("reports out-of-range levels instead of silently refusing to save", async () => {
    const { onSave } = renderPanel();
    fireEvent.change(screen.getByLabelText("Agility"), { target: { value: "150" } });
    fireEvent.click(screen.getByRole("button", { name: "Save as custom" }));
    expect((await screen.findByRole("alert")).textContent).toMatch(/out of range/);
    expect(screen.getByLabelText("Agility").getAttribute("aria-invalid")).toBe("true");
    expect(onSave).not.toHaveBeenCalled();
  });

  it("keeps a stored total level consistent with edited skills", async () => {
    const { onSave } = renderPanel({ account: { ...account, levels: { Agility: 70, Total: 70 } } });
    fireEvent.change(screen.getByLabelText("Agility"), { target: { value: "75" } });
    fireEvent.click(screen.getByRole("button", { name: "Save as custom" }));
    await waitFor(() => expect(onSave).toHaveBeenCalled());
    expect((onSave.mock.calls[0][0] as AccountBuild).levels.Total).toBe(75);
  });
});

const account: AccountBuild = {
  schemaVersion: 1,
  id: "mid",
  name: "Mid game",
  benchmarkNowMinutes: 100000000,
  levels: { Agility: 70 },
  completedQuests: ["Lost City"],
  diaries: { Ardougne: "Medium" },
  inventory: { "995": 10 }, equipment: {}, runePouch: {}, bank: {},
  fairyRingsUnlocked: true,
  plantedSpiritTrees: [],
  poh: {
    location: "Rimmington", jewelleryBox: "NoJewelleryBox",
    portals: { mode: "selected", destinations: [] }, fairyRing: false, spiritTree: false,
    obelisk: false, mountedGlory: false, mountedXerics: false, mountedDigsite: false, mountedMythical: false,
  },
  runtime: { arriveInsidePoh: false, spellbook: "Standard", minigameTeleport: { state: "ready" } },
  routingVariables: { varbits: { "1": 2 }, varplayers: {} },
};
