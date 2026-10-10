import type { Setting, Settings } from "./contracts";

/** A setting's value: a boolean, a whole number, or a choice's id. */
export type SettingValue = boolean | number | string;

/**
 * Settings that differ from the planner's defaults, by plugin config key. Only these travel in
 * route requests and links, so a link stays short and follows any later change of defaults.
 */
export type ChangedSettings = Record<string, SettingValue>;

export function settingValue(setting: Setting, changed: ChangedSettings): SettingValue {
  return changed[setting.key] ?? setting.defaultValue;
}

/** `changed` with the setting set, or removed when `value` is its default. */
export function changeSetting(changed: ChangedSettings, setting: Setting, value: SettingValue): ChangedSettings {
  const { [setting.key]: _previous, ...rest } = changed;
  return value === setting.defaultValue ? rest : { ...rest, [setting.key]: value };
}

export function isValidSetting(setting: Setting, value: unknown): value is SettingValue {
  if (setting.type === "boolean") return typeof value === "boolean";
  if (setting.type === "integer") return Number.isInteger(value) && (value as number) >= 0;
  return setting.choices?.some(choice => choice.id === value) ?? false;
}

/** Keeps the valid changed settings and names the keys it dropped. */
export function validSettings(changed: ChangedSettings, catalog: Setting[]): { settings: ChangedSettings; dropped: string[] } {
  const byKey = new Map(catalog.map(setting => [setting.key, setting]));
  const settings: ChangedSettings = {};
  const dropped: string[] = [];
  for (const [key, value] of Object.entries(changed)) {
    const setting = byKey.get(key);
    if (setting && isValidSetting(setting, value)) {
      if (value !== setting.defaultValue) settings[key] = value;
    } else dropped.push(key);
  }
  return { settings, dropped };
}

export function asSettings(changed: ChangedSettings): Settings {
  return Object.fromEntries(Object.entries(changed).sort(([left], [right]) => left.localeCompare(right)));
}
