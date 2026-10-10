import { useEffect, useState } from "react";
import {
  declaredUnlocks, isAvoidableTransportType, maxCurrencyThreshold, maxTransportThreshold, transportGroups,
  type TransportOption,
} from "../../domain/routeOptions";
import { defaultPolicy, isDefaultPolicy, normalizePolicy, type PlannerPolicy } from "./plannerState";

const numberFormatter = new Intl.NumberFormat("en");

/** A whole-number input that only reports valid values, and reports blank as undefined. */
function NumberField({ label, value, max, placeholder, disabled, onChange }: {
  label: string;
  value?: number;
  max: number;
  placeholder: string;
  disabled?: boolean;
  onChange(value?: number): void;
}) {
  const [text, setText] = useState(value === undefined ? "" : String(value));
  useEffect(() => setText(value === undefined ? "" : String(value)), [value]);
  const valid = text === "" || (/^\d+$/.test(text) && Number(text) <= max);
  return <input type="number" inputMode="numeric" min={0} max={max} step={1} aria-label={label} aria-invalid={!valid}
    value={text} placeholder={placeholder} disabled={disabled}
    onChange={event => {
      const next = event.target.value;
      setText(next);
      if (next === "") onChange(undefined);
      else if (/^\d+$/.test(next) && Number(next) <= max) onChange(Number(next));
    }}
    onBlur={() => { if (!valid) setText(value === undefined ? "" : String(value)); }} />;
}

function TransportRow({ option, policy, onChange }: { option: TransportOption; policy: PlannerPolicy; onChange(policy: PlannerPolicy): void }) {
  const avoidable = isAvoidableTransportType(option.type);
  const teleportItemsOff = policy.avoidedTransportTypes.includes("TELEPORTATION_ITEM");
  const enabled = avoidable ? !policy.avoidedTransportTypes.includes(option.type as PlannerPolicy["avoidedTransportTypes"][number])
    : !teleportItemsOff;
  const setEnabled = (use: boolean) => {
    if (!isAvoidableTransportType(option.type)) return;
    const type = option.type;
    onChange(normalizePolicy({ ...policy, avoidedTransportTypes: use
      ? policy.avoidedTransportTypes.filter(item => item !== type) : [...policy.avoidedTransportTypes, type] }));
  };
  const setThreshold = (amount?: number) => onChange(normalizePolicy({
    ...policy, transportThresholds: { ...policy.transportThresholds, [option.type]: amount ?? 0 },
  }));
  return <tr>
    <th scope="row">{avoidable
      ? <label><input type="checkbox" checked={enabled} onChange={event => setEnabled(event.target.checked)} /> {option.label}</label>
      : <span className="dependent-option" title="Follows the teleport items setting">{option.label}</span>}</th>
    <td><NumberField label={`${option.label} threshold in ticks`} value={policy.transportThresholds[option.type]}
      max={maxTransportThreshold} placeholder="0" disabled={!enabled} onChange={setThreshold} /></td>
  </tr>;
}

export function RouteOptions({ policy, onChange }: { policy: PlannerPolicy; onChange(policy: PlannerPolicy): void }) {
  const update = (partial: Partial<PlannerPolicy>) => onChange(normalizePolicy({ ...policy, ...partial }));
  const advancedChanges = policy.avoidedTransportTypes.length + Object.keys(policy.transportThresholds).length
    + policy.declaredUnlocks.length + (policy.teleportItems !== defaultPolicy.teleportItems ? 1 : 0)
    + (policy.currencyThreshold !== undefined ? 1 : 0);
  return <div className="route-options">
    <label><input type="checkbox" checked={policy.avoidWilderness}
      onChange={event => update({ avoidWilderness: event.target.checked })} /> Avoid wilderness</label>
    <label>Banking <select value={policy.banking}
      onChange={event => update({ banking: event.target.value as PlannerPolicy["banking"] })}>
      <option value="allow">Allow</option><option value="avoid">Avoid</option><option value="never">Never</option>
    </select></label>
    <label>Resources <select value={policy.resources}
      onChange={event => update({ resources: event.target.value as PlannerPolicy["resources"] })}>
      <option value="fastest">Fastest</option><option value="preserve-consumables">Preserve consumables</option>
      <option value="permanent-only">Permanent teleports only</option>
    </select></label>
    <details className="more-options">
      <summary>More route options{advancedChanges > 0 && <span className="option-count">{advancedChanges} changed</span>}</summary>
      <div className="more-options-body">
        <label>Teleport items <select value={policy.teleportItems}
          onChange={event => update({ teleportItems: event.target.value as PlannerPolicy["teleportItems"] })}>
          <option value="owned">Account's items</option><option value="any">Any item</option>
        </select></label>
        <label>Fare limit <NumberField label="Fare limit per transport" value={policy.currencyThreshold}
          max={maxCurrencyThreshold} placeholder="No limit" onChange={currencyThreshold => update({ currencyThreshold })} /></label>
        <p className="option-help">Most coins or tokens spent on one transport. {policy.currencyThreshold !== undefined
          && `Currently ${numberFormatter.format(policy.currencyThreshold)}.`}</p>

        <table className="transport-options">
          <caption>Transports <span>Untick to avoid. A threshold is how many ticks a transport must save to be used.</span></caption>
          <thead><tr><th scope="col">Transport</th><th scope="col">Threshold</th></tr></thead>
          {transportGroups.map(group => <tbody key={group.title}>
            <tr className="transport-group"><th colSpan={2} scope="rowgroup">{group.title}</th></tr>
            {group.options.map(option => <TransportRow key={option.type} option={option} policy={policy} onChange={onChange} />)}
          </tbody>)}
        </table>

        <fieldset className="unlock-options">
          <legend>Unlocks the game doesn't report</legend>
          {declaredUnlocks.map(item => <label key={item.unlock} title={item.description}>
            <input type="checkbox" checked={policy.declaredUnlocks.includes(item.unlock)} onChange={event => update({
              declaredUnlocks: event.target.checked ? [...policy.declaredUnlocks, item.unlock]
                : policy.declaredUnlocks.filter(value => value !== item.unlock),
            })} /> {item.label}
          </label>)}
        </fieldset>
      </div>
    </details>
    {!isDefaultPolicy(policy) && <button type="button" className="text-button reset-policy"
      onClick={() => onChange(defaultPolicy)}>Reset route policy</button>}
  </div>;
}
