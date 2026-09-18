#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const read = file => JSON.parse(fs.readFileSync(path.join(root, file), "utf8"));
const fail = message => { throw new Error(message); };
const profiles = new Set(["early", "mid", "end", "maxed"]);

const manifest = read("manifest.json");
for (const field of ["formatVersion", "accountProfilesVersion", "routesVersion", "oracleVersion"]) {
  if (manifest[field] !== 1) fail(`manifest ${field} must be 1`);
}
const accounts = read("accounts/account-profiles-v1.json");
if (accounts.formatVersion !== manifest.accountProfilesVersion) fail("account profile version disagrees with manifest");
if (Object.keys(accounts.profiles || {}).length !== 4 || [...profiles].some(name => !accounts.profiles[name])) {
  fail("profiles must be exactly early, mid, end, maxed");
}
const routes = read("corpus/routes-v1.json");
const ids = routes.map(route => route.id);
if (ids.some(id => typeof id !== "string" || !id) || new Set(ids).size !== ids.length) fail("route IDs must be unique non-empty strings");
for (const route of routes) {
  for (const field of ["name", "category", "start", "target", "allowTransports", "tiers"]) if (!(field in route)) fail(`${route.id}: missing ${field}`);
  if (!route.tiers.includes("full") || route.tiers.some(tier => !["smoke", "standard", "full"].includes(tier))) fail(`${route.id}: invalid tiers`);
  if ((route.negativeProfiles || []).some(name => !profiles.has(name))) fail(`${route.id}: invalid negative profile`);
}
const oracle = read("oracle/oracle-v1.json");
const expected = new Set(routes.flatMap(route => [...profiles].map(profile => `${route.id}/${profile}`)));
if (Object.keys(oracle).length !== expected.size || Object.keys(oracle).some(key => !expected.has(key))) fail("oracle keys do not match route/profile keys");
for (const route of routes) for (const profile of profiles) {
  const key = `${route.id}/${profile}`;
  const row = oracle[key];
  if (!row || typeof row.reachable !== "boolean") fail(`${key}: malformed oracle row`);
  if (row.reachable !== !(route.negativeProfiles || []).includes(profile)) fail(`${key}: route expectation disagrees with oracle`);
  if (row.reachable && !Number.isInteger(row.cost)) fail(`${key}: reachable oracle row needs integer cost`);
}
console.log(`valid corpus: ${routes.length} routes, ${Object.keys(oracle).length} route/profile cases`);
