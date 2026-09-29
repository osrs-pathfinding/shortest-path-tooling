import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import type { AccountBuild } from "../domain/contracts";
import { AccountEditor } from "./AccountEditor";

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute("open", ""); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute("open"); };
});

describe("semantic account editor", () => {
  it("saves semantic changes without exposing routing variables", async () => {
    const onSave = vi.fn();
    render(<AccountEditor account={account} quests={["Lost City"]} open onClose={() => {}} onSave={onSave} />);

    fireEvent.change(screen.getByLabelText("Build name"), { target: { value: "My build" } });
    fireEvent.change(screen.getByLabelText("Agility"), { target: { value: "80" } });
    fireEvent.click(screen.getByLabelText("Fairy rings"));
    fireEvent.click(screen.getByRole("button", { name: "Save custom build" }));

    await waitFor(() => expect(onSave).toHaveBeenCalled());
    const saved = onSave.mock.calls[0][0] as AccountBuild;
    expect(saved).toMatchObject({ id: "custom", name: "My build", fairyRingsUnlocked: false });
    expect(saved.levels.Agility).toBe(80);
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
  inventory: {}, equipment: {}, runePouch: {}, bank: {},
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
