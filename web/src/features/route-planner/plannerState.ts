import { gzipSync, gunzipSync, strFromU8, strToU8 } from "fflate";
import { locationFromParam, locationParam } from "../../data/places";
import type { Account, Location } from "../../domain/contracts";
import type { ChangedSettings, SettingValue } from "../../domain/settings";

const profilePrefix = "v1.";
export const maxShareProfileLength = 8 * 1024;
const maxDecodedProfileBytes = 128 * 1024;

/** URL search parameters that are not plugin settings; every other parameter is one. */
const routeParams = new Set(["from", "to", "account"]);

export class SharedProfileError extends Error {}

export interface PlannerUrlState {
  start?: Location;
  destination?: Location;
  accountId: string;
  settings: ChangedSettings;
}

/** `true`/`false` are booleans, digits are whole numbers, anything else is a choice. */
function parseSettingValue(value: string): SettingValue {
  if (value === "true" || value === "false") return value === "true";
  return /^\d+$/.test(value) ? Number(value) : value;
}

/** The settings the URL changes; with none, `fallback` (the settings this browser last used). */
export function readPlannerUrl(params: URLSearchParams, fallback: ChangedSettings = {}): PlannerUrlState {
  const settings: ChangedSettings = {};
  params.forEach((value, key) => { if (!routeParams.has(key)) settings[key] = parseSettingValue(value); });
  const hasPlannerState = [...params.keys()].length > 0;
  return {
    start: locationFromParam(params.get("from")),
    destination: locationFromParam(params.get("to")),
    accountId: params.get("account") || "mid",
    settings: hasPlannerState ? settings : fallback,
  };
}

export function writePlannerUrl(state: PlannerUrlState): URLSearchParams {
  const params = new URLSearchParams();
  if (state.start) params.set("from", locationParam(state.start));
  if (state.destination) params.set("to", locationParam(state.destination));
  if (state.accountId !== "mid") params.set("account", state.accountId);
  Object.keys(state.settings).sort().forEach(key => params.set(key, String(state.settings[key])));
  return params;
}

export function plannerUrlWarnings(params: URLSearchParams, validAccountIds: string[]): string[] {
  const warnings: string[] = [];
  if (params.has("from") && !locationFromParam(params.get("from"))) warnings.push("The shared start location was invalid and has been ignored.");
  if (params.has("to") && !locationFromParam(params.get("to"))) warnings.push("The shared destination was invalid and has been ignored.");
  const account = params.get("account");
  if (account && !validAccountIds.includes(account)) warnings.push("The requested account was unavailable; the Mid game preset is being used.");
  return warnings;
}

export function readStoredSettings(value: string | null): ChangedSettings {
  try {
    const stored: unknown = JSON.parse(value || "{}");
    if (!stored || typeof stored !== "object" || Array.isArray(stored)) return {};
    return Object.fromEntries(Object.entries(stored).filter(([, item]) =>
      typeof item === "boolean" || typeof item === "string" || Number.isInteger(item))) as ChangedSettings;
  } catch { return {}; }
}

export function encodeSharedProfile(account: Account): string {
  const decoded = strToU8(JSON.stringify(sortObject(account)));
  if (decoded.byteLength > maxDecodedProfileBytes) {
    throw new SharedProfileError("This account is too large to share as a single link.");
  }
  const result = profilePrefix + bytesToBase64Url(gzipSync(decoded, { level: 9 }));
  if (result.length > maxShareProfileLength) {
    throw new SharedProfileError("This account is too large to share as a single link.");
  }
  return result;
}

export async function decodeSharedProfile(value: string | null): Promise<Account> {
  if (!value?.startsWith(profilePrefix)) throw new SharedProfileError("This route link has an unsupported account format.");
  if (value.length > maxShareProfileLength) throw new SharedProfileError("This route link contains an account that is too large.");
  try {
    const compressed = base64UrlToBytes(value.slice(profilePrefix.length));
    const decodedSize = gzipDecodedSize(compressed);
    if (decodedSize > maxDecodedProfileBytes) throw new SharedProfileError("This route link contains an account that is too large.");
    const decoded = gunzipSync(compressed, { out: new Uint8Array(decodedSize) });
    const { validateAccount } = await import("../../domain/accountValidation");
    return validateAccount(JSON.parse(strFromU8(decoded)));
  } catch (error) {
    if (error instanceof SharedProfileError) throw error;
    throw new SharedProfileError("This route link contains an invalid account.");
  }
}

export async function profileFromHash(hash: string): Promise<Account> {
  return decodeSharedProfile(new URLSearchParams(hash.replace(/^#/, "")).get("profile"));
}

/**
 * A link to this route. A preset is named by id; any other account (the custom build, or one
 * opened from a link) is carried in the URL fragment, which never reaches the server.
 */
export function buildShareUrl(base: string, state: PlannerUrlState, accountId: string, account: Account): string {
  const preset = accountId !== "custom" && accountId !== "shared";
  const url = new URL(base);
  url.pathname = "/route";
  url.search = writePlannerUrl({ ...state, accountId: preset ? accountId : "shared" }).toString();
  url.hash = preset ? "" : new URLSearchParams({ profile: encodeSharedProfile(account) }).toString();
  if (url.href.length > maxShareProfileLength) throw new SharedProfileError("This route is too large to share as a single link.");
  return url.href;
}

function sortObject(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sortObject);
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, item]) => [key, sortObject(item)]));
  return value;
}

function bytesToBase64Url(value: Uint8Array): string {
  let binary = "";
  for (let index = 0; index < value.length; index += 0x8000) {
    binary += String.fromCharCode(...value.subarray(index, index + 0x8000));
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlToBytes(value: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error("invalid base64url");
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((value.length + 3) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, character => character.charCodeAt(0));
}

function gzipDecodedSize(value: Uint8Array): number {
  if (value.length < 4) throw new Error("invalid gzip stream");
  const offset = value.length - 4;
  return new DataView(value.buffer, value.byteOffset + offset, 4).getUint32(0, true);
}
