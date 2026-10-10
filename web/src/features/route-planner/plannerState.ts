import { gzipSync, gunzipSync, strFromU8, strToU8 } from "fflate";
import type { AccountBuild, Location, RoutePolicy } from "../../domain/contracts";
import { locationFromParam, locationParam } from "../../data/places";
import {
  isAvoidableTransportType, isCurrencyThreshold, isDeclaredUnlock, isThresholdTransportType, isTransportThreshold,
  type ThresholdTransportType,
} from "../../domain/routeOptions";

/** A route policy with every optional setting filled in, in a canonical order. */
export type PlannerPolicy = Required<Omit<RoutePolicy, "currencyThreshold">> & Pick<RoutePolicy, "currencyThreshold">;

export const defaultPolicy: PlannerPolicy = {
  avoidWilderness: true,
  banking: "allow",
  resources: "fastest",
  avoidedTransportTypes: [],
  teleportItems: "owned",
  transportThresholds: {},
  declaredUnlocks: [],
};

/** The URL search parameters that hold route policy. */
export const policyParams = ["wilderness", "banking", "resources", "avoid", "items", "fare", "thresholds", "unlocks"];
const bankingValues: PlannerPolicy["banking"][] = ["allow", "avoid", "never"];
const resourceValues: PlannerPolicy["resources"][] = ["fastest", "preserve-consumables", "permanent-only"];
const teleportItemValues: PlannerPolicy["teleportItems"][] = ["owned", "any"];

const profilePrefix = "v1.";
export const maxShareProfileLength = 8 * 1024;
const maxDecodedProfileBytes = 128 * 1024;

export class SharedProfileError extends Error {}

export interface PlannerUrlState {
  start?: Location;
  destination?: Location;
  accountId: string;
  policy: PlannerPolicy;
}

function oneOf<T extends string>(values: readonly T[], value: unknown, fallback: T): T {
  return values.includes(value as T) ? value as T : fallback;
}

function uniqueSorted<T extends string>(values: unknown[], guard: (value: unknown) => value is T): T[] {
  return Array.from(new Set(values.filter(guard))).sort();
}

function canonicalThresholds(entries: [unknown, unknown][]): PlannerPolicy["transportThresholds"] {
  return Object.fromEntries(entries
    .filter((entry): entry is [ThresholdTransportType, number] =>
      isThresholdTransportType(entry[0]) && isTransportThreshold(entry[1]) && entry[1] > 0)
    .sort(([left], [right]) => left.localeCompare(right)));
}

/** Builds a complete, canonical policy from untrusted input, using defaults for anything invalid. */
export function normalizePolicy(value: unknown): PlannerPolicy {
  const stored = value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
  const thresholds = stored.transportThresholds;
  return {
    avoidWilderness: typeof stored.avoidWilderness === "boolean" ? stored.avoidWilderness : defaultPolicy.avoidWilderness,
    banking: oneOf(bankingValues, stored.banking, defaultPolicy.banking),
    resources: oneOf(resourceValues, stored.resources, defaultPolicy.resources),
    avoidedTransportTypes: Array.isArray(stored.avoidedTransportTypes)
      ? uniqueSorted(stored.avoidedTransportTypes, isAvoidableTransportType) : [],
    teleportItems: oneOf(teleportItemValues, stored.teleportItems, defaultPolicy.teleportItems),
    ...isCurrencyThreshold(stored.currencyThreshold) ? { currencyThreshold: stored.currencyThreshold } : {},
    transportThresholds: thresholds && typeof thresholds === "object" && !Array.isArray(thresholds)
      ? canonicalThresholds(Object.entries(thresholds)) : {},
    declaredUnlocks: Array.isArray(stored.declaredUnlocks) ? uniqueSorted(stored.declaredUnlocks, isDeclaredUnlock) : [],
  };
}

export function readStoredPolicy(value: string | null): PlannerPolicy {
  if (!value) return normalizePolicy(null);
  try { return normalizePolicy(JSON.parse(value)); }
  catch { return normalizePolicy(null); }
}

export function isDefaultPolicy(policy: PlannerPolicy): boolean {
  return JSON.stringify(normalizePolicy(policy)) === JSON.stringify(defaultPolicy);
}

function splitList(value: string | null): string[] {
  return value ? value.split(",").map(item => item.trim()).filter(Boolean) : [];
}

function parseThresholds(value: string | null): [string, number][] {
  return splitList(value).map(item => {
    const [type, amount = ""] = item.split(":");
    return [type, /^\d+$/.test(amount) ? Number(amount) : Number.NaN];
  });
}

function parseFare(value: string | null): number | undefined {
  return value && /^\d+$/.test(value) && isCurrencyThreshold(Number(value)) ? Number(value) : undefined;
}

export function readPlannerUrl(params: URLSearchParams, fallbackPolicy: PlannerPolicy = defaultPolicy): PlannerUrlState {
  const read = <T,>(key: string, parse: (value: string | null) => T, fallback: T) => params.has(key) ? parse(params.get(key)) : fallback;
  const fare = read("fare", parseFare, fallbackPolicy.currencyThreshold);
  return {
    start: locationFromParam(params.get("from")),
    destination: locationFromParam(params.get("to")),
    accountId: params.get("account") || "mid",
    policy: normalizePolicy({
      avoidWilderness: read("wilderness", value => value !== "allow", fallbackPolicy.avoidWilderness),
      banking: read("banking", value => value, fallbackPolicy.banking),
      resources: read("resources", value => value, fallbackPolicy.resources),
      avoidedTransportTypes: read("avoid", splitList, fallbackPolicy.avoidedTransportTypes),
      teleportItems: read("items", value => value, fallbackPolicy.teleportItems),
      currencyThreshold: fare,
      transportThresholds: read("thresholds", value => Object.fromEntries(parseThresholds(value)), fallbackPolicy.transportThresholds),
      declaredUnlocks: read("unlocks", splitList, fallbackPolicy.declaredUnlocks),
    }),
  };
}

export function plannerUrlWarnings(params: URLSearchParams, validAccountIds: string[]): string[] {
  const warnings: string[] = [];
  const invalid = (key: string, valid: (value: string) => boolean) => params.has(key) && !valid(params.get(key) || "");
  if (params.has("from") && !locationFromParam(params.get("from"))) warnings.push("The shared start location was invalid and has been ignored.");
  if (params.has("to") && !locationFromParam(params.get("to"))) warnings.push("The shared destination was invalid and has been ignored.");
  const account = params.get("account");
  if (account && !validAccountIds.includes(account)) warnings.push("The requested account was unavailable; the Mid game preset is being used.");
  if (invalid("wilderness", value => ["avoid", "allow"].includes(value))) warnings.push("An invalid Wilderness policy was ignored.");
  if (invalid("banking", value => bankingValues.includes(value as PlannerPolicy["banking"]))) warnings.push("An invalid banking policy was ignored.");
  if (invalid("resources", value => resourceValues.includes(value as PlannerPolicy["resources"]))) warnings.push("An invalid resource policy was ignored.");
  if (invalid("avoid", value => splitList(value).every(isAvoidableTransportType))) warnings.push("Unknown avoided transports were ignored.");
  if (invalid("items", value => teleportItemValues.includes(value as PlannerPolicy["teleportItems"]))) warnings.push("An invalid teleport item setting was ignored.");
  if (invalid("fare", value => parseFare(value) !== undefined)) warnings.push("An invalid fare limit was ignored.");
  if (invalid("thresholds", value => parseThresholds(value).every(([type, amount]) => isThresholdTransportType(type) && isTransportThreshold(amount)))) {
    warnings.push("Invalid transport thresholds were ignored.");
  }
  if (invalid("unlocks", value => splitList(value).every(isDeclaredUnlock))) warnings.push("Unknown unlocks were ignored.");
  return warnings;
}

export function writePlannerUrl(state: PlannerUrlState): URLSearchParams {
  const params = new URLSearchParams();
  const policy = normalizePolicy(state.policy);
  if (state.start) params.set("from", locationParam(state.start));
  if (state.destination) params.set("to", locationParam(state.destination));
  if (state.accountId !== "mid") params.set("account", state.accountId);
  if (!policy.avoidWilderness) params.set("wilderness", "allow");
  if (policy.banking !== defaultPolicy.banking) params.set("banking", policy.banking);
  if (policy.resources !== defaultPolicy.resources) params.set("resources", policy.resources);
  if (policy.avoidedTransportTypes.length) params.set("avoid", policy.avoidedTransportTypes.join(","));
  if (policy.teleportItems !== defaultPolicy.teleportItems) params.set("items", policy.teleportItems);
  if (policy.currencyThreshold !== undefined) params.set("fare", String(policy.currencyThreshold));
  const thresholds = Object.entries(policy.transportThresholds);
  if (thresholds.length) params.set("thresholds", thresholds.map(([type, amount]) => `${type}:${amount}`).join(","));
  if (policy.declaredUnlocks.length) params.set("unlocks", policy.declaredUnlocks.join(","));
  return params;
}

export function encodeSharedProfile(account: AccountBuild): string {
  const json = JSON.stringify(sortObject(account));
  const decoded = strToU8(json);
  if (decoded.byteLength > maxDecodedProfileBytes) {
    throw new SharedProfileError("This account is too large to share as a single link.");
  }
  const encoded = bytesToBase64Url(gzipSync(decoded, { level: 9 }));
  const result = profilePrefix + encoded;
  if (result.length > maxShareProfileLength) {
    throw new SharedProfileError("This account is too large to share as a single link.");
  }
  return result;
}

export async function decodeSharedProfile(value: string | null): Promise<AccountBuild> {
  if (!value?.startsWith(profilePrefix)) throw new SharedProfileError("This route link has an unsupported account format.");
  if (value.length > maxShareProfileLength) throw new SharedProfileError("This route link contains an account that is too large.");
  try {
    const compressed = base64UrlToBytes(value.slice(profilePrefix.length));
    const decodedSize = gzipDecodedSize(compressed);
    if (decodedSize > maxDecodedProfileBytes) throw new SharedProfileError("This route link contains an account that is too large.");
    const decoded = gunzipSync(compressed, { out: new Uint8Array(decodedSize) });
    if (decoded.byteLength > maxDecodedProfileBytes) throw new SharedProfileError("This route link contains an account that is too large.");
    const { validateAccount } = await import("../../domain/accountValidation");
    const account = validateAccount(JSON.parse(strFromU8(decoded)));
    return { ...account, id: "shared" };
  } catch (error) {
    if (error instanceof SharedProfileError) throw error;
    throw new SharedProfileError("This route link contains an invalid account profile.");
  }
}

export async function profileFromHash(hash: string): Promise<AccountBuild> {
  const params = new URLSearchParams(hash.replace(/^#/, ""));
  return decodeSharedProfile(params.get("profile"));
}

export function buildShareUrl(base: string, state: PlannerUrlState, account: AccountBuild): string {
  const url = new URL(base);
  url.pathname = "/route";
  url.search = writePlannerUrl({ ...state, accountId: account.id === "custom" || account.id === "shared" ? "shared" : account.id }).toString();
  url.hash = account.id === "custom" || account.id === "shared"
    ? new URLSearchParams({ profile: encodeSharedProfile(account) }).toString()
    : "";
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
