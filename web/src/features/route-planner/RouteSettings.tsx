import { useEffect, useState } from "react";
import type { Setting } from "../../domain/contracts";
import { changeSetting, settingValue, type ChangedSettings } from "../../domain/settings";

/** Shown above the groups: the settings most routes are planned around. */
const everyday = ["avoidWilderness", "includeBankPath", "useTeleportationItems"];

/**
 * The plugin's own settings, as the route service's catalog lists them, in four groups: transport
 * toggles (`use…`), unlocks the game doesn't report (`unlock…`, `respawn…`), transport thresholds,
 * and everything else as advanced.
 */
function groups(settings: Setting[]): { title: string; settings: Setting[] }[] {
  const rest = settings.filter(setting => !everyday.includes(setting.key));
  const transports = rest.filter(setting => setting.section === "Settings" && setting.type === "boolean" && setting.key.startsWith("use"));
  const unlocks = rest.filter(setting => /^(unlock|respawn)/.test(setting.key));
  const thresholds = rest.filter(setting => setting.section === "Transport Thresholds");
  const grouped = new Set([...transports, ...unlocks, ...thresholds]);
  return [
    { title: "Transports", settings: transports },
    { title: "Unlocks the game doesn't report", settings: unlocks },
    { title: "Transport thresholds (ticks a transport must save)", settings: thresholds },
    { title: "Advanced", settings: rest.filter(setting => !grouped.has(setting)) },
  ];
}

/** A whole-number input that only reports valid values. */
function NumberField({ setting, value, onChange }: { setting: Setting; value: number; onChange(value: number): void }) {
  const [text, setText] = useState(String(value));
  useEffect(() => setText(String(value)), [value]);
  const valid = /^\d+$/.test(text) && Number(text) <= 2_147_483_647;
  return <input type="number" inputMode="numeric" min={0} step={1} aria-label={setting.name} aria-invalid={!valid}
    value={text} onChange={event => {
      setText(event.target.value);
      if (/^\d+$/.test(event.target.value) && Number(event.target.value) <= 2_147_483_647) onChange(Number(event.target.value));
    }}
    onBlur={() => { if (!valid) setText(String(value)); }} />;
}

function SettingField({ setting, changed, onChange }: { setting: Setting; changed: ChangedSettings; onChange(changed: ChangedSettings): void }) {
  const value = settingValue(setting, changed);
  const set = (next: boolean | number | string) => onChange(changeSetting(changed, setting, next));
  if (setting.type === "boolean") {
    return <label title={setting.description}><input type="checkbox" checked={value === true}
      onChange={event => set(event.target.checked)} /> {setting.name}</label>;
  }
  if (setting.type === "integer") {
    return <label title={setting.description}>{setting.name} <NumberField setting={setting} value={value as number} onChange={set} /></label>;
  }
  return <label title={setting.description}>{setting.name} <select value={value as string} onChange={event => set(event.target.value)}>
    {setting.choices?.map(choice => <option key={choice.id} value={choice.id}>{choice.name}</option>)}
  </select></label>;
}

export function RouteSettings({ settings, changed, onChange }: {
  settings: Setting[];
  changed: ChangedSettings;
  onChange(changed: ChangedSettings): void;
}) {
  const pinned = everyday.map(key => settings.find(setting => setting.key === key)).filter((setting): setting is Setting => Boolean(setting));
  return <div className="route-options">
    {pinned.map(setting => <SettingField key={setting.key} setting={setting} changed={changed} onChange={onChange} />)}
    {groups(settings).filter(group => group.settings.length).map(group => {
      const count = group.settings.filter(setting => setting.key in changed).length;
      return <details className="more-options" key={group.title}>
        <summary>{group.title}{count > 0 && <span className="option-count">{count} changed</span>}</summary>
        <div className="more-options-body">
          {group.settings.map(setting => <SettingField key={setting.key} setting={setting} changed={changed} onChange={onChange} />)}
        </div>
      </details>;
    })}
    {Object.keys(changed).length > 0 && <button type="button" className="text-button reset-policy"
      onClick={() => onChange({})}>Reset route settings</button>}
  </div>;
}
