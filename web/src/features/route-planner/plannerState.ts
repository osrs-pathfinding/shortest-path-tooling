import { gzipSync, gunzipSync, strFromU8, strToU8 } from "fflate";
import type { AccountBuild, Location, RoutePolicy } from "../../domain/contracts";
import { locationFromParam, locationParam } from "../../data/places";

export const defaultPolicy: RoutePolicy = {
  avoidWilderness: true,
  banking: "allow",
  resources: "fastest",
  avoidedTransportTypes: [],
};

const profilePrefix = "v1.";
export const maxShareProfileLength = 8 * 1024;
const maxDecodedProfileBytes = 128 * 1024;

export class SharedProfileError extends Error {}

export interface PlannerUrlState {
  start?: Location;
  destination?: Location;
  accountId: string;
  policy: RoutePolicy;
}

export function readStoredPolicy(value: string | null): RoutePolicy {
  if (!value) return { ...defaultPolicy, avoidedTransportTypes: [] };
  try {
    const stored = JSON.parse(value) as Partial<RoutePolicy>;
    return {
      avoidWilderness: typeof stored.avoidWilderness === "boolean" ? stored.avoidWilderness : defaultPolicy.avoidWilderness,
      banking: stored.banking === "never" || stored.banking === "allow" ? stored.banking : defaultPolicy.banking,
      resources: stored.resources === "preserve-consumables" || stored.resources === "fastest"
        ? stored.resources : defaultPolicy.resources,
      avoidedTransportTypes: Array.isArray(stored.avoidedTransportTypes)
        ? Array.from(new Set(stored.avoidedTransportTypes
          .filter(value => typeof value === "string" && value.trim())
          .map(value => value.trim()))).sort()
        : [],
    };
  } catch {
    return { ...defaultPolicy, avoidedTransportTypes: [] };
  }
}

export function isDefaultPolicy(policy: RoutePolicy): boolean {
  return policy.avoidWilderness === defaultPolicy.avoidWilderness
    && policy.banking === defaultPolicy.banking
    && policy.resources === defaultPolicy.resources
    && policy.avoidedTransportTypes.length === 0;
}

export function readPlannerUrl(params: URLSearchParams, fallbackPolicy: RoutePolicy = defaultPolicy): PlannerUrlState {
  return {
    start: locationFromParam(params.get("from")),
    destination: locationFromParam(params.get("to")),
    accountId: params.get("account") || "mid",
    policy: {
      avoidWilderness: params.has("wilderness") ? params.get("wilderness") !== "allow" : fallbackPolicy.avoidWilderness,
      banking: params.has("banking") ? params.get("banking") === "never" ? "never" : "allow" : fallbackPolicy.banking,
      resources: params.has("resources") ? params.get("resources") === "preserve-consumables" ? "preserve-consumables" : "fastest" : fallbackPolicy.resources,
      avoidedTransportTypes: params.has("avoid") ? parseAvoidedTypes(params.get("avoid")) : fallbackPolicy.avoidedTransportTypes,
    },
  };
}

export function plannerUrlWarnings(params: URLSearchParams, validAccountIds: string[]): string[] {
  const warnings: string[] = [];
  if (params.has("from") && !locationFromParam(params.get("from"))) warnings.push("The shared start location was invalid and has been ignored.");
  if (params.has("to") && !locationFromParam(params.get("to"))) warnings.push("The shared destination was invalid and has been ignored.");
  const account = params.get("account");
  if (account && !validAccountIds.includes(account)) warnings.push("The requested account was unavailable; the Mid game preset is being used.");
  if (params.has("wilderness") && !["avoid", "allow"].includes(params.get("wilderness") || "")) warnings.push("An invalid Wilderness policy was ignored.");
  if (params.has("banking") && !["allow", "never"].includes(params.get("banking") || "")) warnings.push("An invalid banking policy was ignored.");
  if (params.has("resources") && !["fastest", "preserve-consumables"].includes(params.get("resources") || "")) warnings.push("An invalid resource policy was ignored.");
  return warnings;
}

export function writePlannerUrl(state: PlannerUrlState): URLSearchParams {
  const params = new URLSearchParams();
  if (state.start) params.set("from", locationParam(state.start));
  if (state.destination) params.set("to", locationParam(state.destination));
  if (state.accountId !== "mid") params.set("account", state.accountId);
  if (!state.policy.avoidWilderness) params.set("wilderness", "allow");
  if (state.policy.banking !== defaultPolicy.banking) params.set("banking", state.policy.banking);
  if (state.policy.resources !== defaultPolicy.resources) params.set("resources", state.policy.resources);
  if (state.policy.avoidedTransportTypes.length) params.set("avoid", [...state.policy.avoidedTransportTypes].sort().join(","));
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

function parseAvoidedTypes(value: string | null): string[] {
  return value ? Array.from(new Set(value.split(",").map(item => item.trim()).filter(Boolean))).sort() : [];
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
