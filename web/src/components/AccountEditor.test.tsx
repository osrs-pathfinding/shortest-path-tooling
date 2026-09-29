import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import type { AccountBuild } from "../domain/contracts";
import { AccountEditor } from "./AccountEditor";

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute("open", ""); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute("open"); };
});
afterEach(() => vi.unstubAllGlobals());

describe("semantic account editor", () => {
  it("saves semantic changes without exposing routing variables", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, json: async () => [{ key: "995", name: "Coins" }] }));
    const onSave = vi.fn();
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AccountEditor account={account} quests={["Lost City"]} open onClose={() => {}} onSave={onSave} />
    </QueryClientProvider>);

    fireEvent.change(screen.getByLabelText("Build name"), { target: { value: "My build" } });
    fireEvent.change(screen.getByLabelText("Agility"), { target: { value: "80" } });
    fireEvent.change(screen.getByLabelText("Quest points"), { target: { value: "123" } });
    fireEvent.change(screen.getByLabelText("Ardougne diary"), { target: { value: "Elite" } });
    fireEvent.change(screen.getByLabelText("Spellbook"), { target: { value: "Lunar" } });
    fireEvent.click(screen.getByLabelText("Fairy rings"));
    fireEvent.click(screen.getByRole("button", { name: "Save custom build" }));

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
