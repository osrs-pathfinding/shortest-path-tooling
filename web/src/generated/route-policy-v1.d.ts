/* Generated from shortest-path-corpus. Do not edit. */

export interface RoutePolicyV1 {
  avoidWilderness: boolean;
  banking: "allow" | "avoid" | "never";
  resources: "fastest" | "preserve-consumables";
  avoidedTransportTypes: string[];
}
