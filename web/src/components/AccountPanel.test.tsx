import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Account } from "../domain/contracts";
import { account, catalog, presets } from "../test/fixtures";
import { AccountPanel } from "./AccountPanel";

afterEach(() => { vi.unstubAllGlobals(); cleanup(); });

function renderPanel(props: Partial<Parameters<typeof AccountPanel>[0]> = {}) {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, json: async () => [{ key: "995", name: "Coins" }] }));
  const handlers = { onSave: vi.fn(), onSelect: vi.fn(), onClose: vi.fn() };
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <AccountPanel accounts={presets} selected={presets[0]} catalog={catalog} open {...handlers} {...props} />
  </QueryClientProvider>);
  return handlers;
}

describe("account panel", () => {
  it("saves every kind of fact the catalog offers", async () => {
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
    fireEvent.click(screen.getByLabelText("Fairy rings"));
    fireEvent.click(screen.getByLabelText("Balloon entrana"));
    fireEvent.click(screen.getByLabelText("Dragon Slayer I"));
    fireEvent.click(screen.getByRole("button", { name: "Save as custom" }));

    await waitFor(() => expect(onSave).toHaveBeenCalled());
    const saved = onSave.mock.calls[0][0] as Account;
    expect(saved).toMatchObject({ name: "My build", questPoints: 123, spellbook: "LUNAR", unlocks: ["BALLOON_ENTRANA"] });
    expect(saved.levels.AGILITY).toBe(80);
    expect(saved.diaries.ARDOUGNE).toBe("ELITE");
    expect(saved.completedQuests).toEqual(["LOST_CITY", "DRAGON_SLAYER_I"]);
    expect(saved.inventory).toEqual({ "995": 10 });
    expect(saved.house?.portals).toEqual([]);
    expect(saved.minigameTeleportUsedAt).toBeUndefined();
  });

  it("saves a house with every portal, or no house", async () => {
    const { onSave } = renderPanel();
    fireEvent.click(screen.getByLabelText("Every nexus portal"));
    fireEvent.click(screen.getByRole("button", { name: "Save as custom" }));
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect((onSave.mock.calls[0][0] as Account).house).not.toHaveProperty("portals");

    fireEvent.click(screen.getByLabelText("Has a player-owned house"));
    fireEvent.click(screen.getByRole("button", { name: "Save as custom" }));
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(2));
    expect((onSave.mock.calls[1][0] as Account).house).toBeUndefined();
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

  it("starts skills the account does not list at level 1", async () => {
    const { onSave } = renderPanel({ selected: { id: "custom", name: "Mine", account: { ...account, levels: { AGILITY: 70 } } } });
    expect((screen.getByLabelText("Attack") as HTMLInputElement).value).toBe("1");
    fireEvent.change(screen.getByLabelText("Agility"), { target: { value: "75" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(onSave).toHaveBeenCalled());
    expect((onSave.mock.calls[0][0] as Account).levels).toEqual({ AGILITY: 75, ATTACK: 1 });
  });
});
