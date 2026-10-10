import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { defaultPolicy, type PlannerPolicy } from "./plannerState";
import { RouteOptions } from "./RouteOptions";

afterEach(cleanup);

function renderOptions(policy: PlannerPolicy = defaultPolicy) {
  const onChange = vi.fn<(policy: PlannerPolicy) => void>();
  render(<RouteOptions policy={policy} onChange={onChange} />);
  return onChange;
}

describe("route options", () => {
  it("avoids transports and sets their thresholds", () => {
    const onChange = renderOptions();
    fireEvent.click(screen.getByRole("checkbox", { name: "Fairy rings" }));
    expect(onChange).toHaveBeenLastCalledWith({ ...defaultPolicy, avoidedTransportTypes: ["FAIRY_RING"] });

    fireEvent.change(screen.getByRole("spinbutton", { name: "Spirit trees threshold in ticks" }), { target: { value: "25" } });
    expect(onChange).toHaveBeenLastCalledWith({ ...defaultPolicy, transportThresholds: { SPIRIT_TREE: 25 } });
  });

  it("disables thresholds for avoided transports and teleport boxes without teleport items", () => {
    renderOptions({ ...defaultPolicy, avoidedTransportTypes: ["TELEPORTATION_ITEM"] });
    const disabled = (name: string) => (screen.getByRole("spinbutton", { name }) as HTMLInputElement).disabled;
    expect(disabled("Teleport items threshold in ticks")).toBe(true);
    expect(disabled("Teleport boxes threshold in ticks")).toBe(true);
    expect(disabled("Boats threshold in ticks")).toBe(false);
  });

  it("sets fare limits, teleport item source and declared unlocks", () => {
    const onChange = renderOptions({ ...defaultPolicy, currencyThreshold: 500 });
    fireEvent.change(screen.getByRole("spinbutton", { name: "Fare limit per transport" }), { target: { value: "" } });
    expect(onChange).toHaveBeenLastCalledWith(defaultPolicy);
    fireEvent.change(screen.getByRole("combobox", { name: "Teleport items" }), { target: { value: "any" } });
    expect(onChange).toHaveBeenLastCalledWith({ ...defaultPolicy, currencyThreshold: 500, teleportItems: "any" });
    fireEvent.click(screen.getByRole("checkbox", { name: "Xeric's Honour" }));
    expect(onChange).toHaveBeenLastCalledWith({ ...defaultPolicy, currencyThreshold: 500, declaredUnlocks: ["XERICS_HONOUR"] });
  });

  it("ignores out-of-range numbers", () => {
    const onChange = renderOptions();
    fireEvent.change(screen.getByRole("spinbutton", { name: "Boats threshold in ticks" }), { target: { value: "10001" } });
    expect(onChange).not.toHaveBeenCalled();
  });
});
