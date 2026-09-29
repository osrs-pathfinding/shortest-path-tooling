#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const assert = require("node:assert/strict");
const Ajv = require("ajv/dist/2020");
const root = path.resolve(__dirname, "..");
const read = file => JSON.parse(fs.readFileSync(path.join(root, file), "utf8"));
const fail = message => { throw new Error(message); };
const profiles = new Set(["early", "mid", "end", "maxed"]);
const isObject = value => value !== null && typeof value === "object" && !Array.isArray(value);
const isIntegerMap = (value, label, keyPattern = /^\d+$/) => {
  if (!isObject(value)) fail(`${label} must be an object`);
  for (const [key, entry] of Object.entries(value)) {
    if (!keyPattern.test(key) || !Number.isInteger(entry)) fail(`${label}.${key} must be an integer entry`);
  }
};
const isStringArray = (value, label) => {
  if (!Array.isArray(value) || value.some(entry => typeof entry !== "string")) fail(`${label} must be a string array`);
};
const isCoordinate = value => Array.isArray(value) && value.length === 3 && value.every(Number.isInteger);
const ajv = new Ajv({ allErrors: true });
const accountSchema = read("schemas/account-build-v1.schema.json");
const policySchema = read("schemas/route-policy-v1.schema.json");
ajv.addSchema(accountSchema).addSchema(policySchema);
ajv.compile(read("schemas/place-v1.schema.json"));
ajv.compile(read("schemas/route-api-v1.schema.json"));
const validateAccount = ajv.getSchema(accountSchema.$id);

const manifest = read("manifest.json");
if (manifest.formatVersion !== 2) fail("manifest formatVersion must be 2");
for (const field of ["accountProfilesVersion", "routesVersion"]) {
  if (manifest[field] !== 1) fail(`manifest ${field} must be 1`);
}
if (manifest.accountSchemaVersion !== 1) fail("manifest accountSchemaVersion must be 1");
const accounts = read("accounts/account-profiles-v1.json");
if (accounts.formatVersion !== manifest.accountProfilesVersion) fail("account profile version disagrees with manifest");
if (Object.keys(accounts.profiles || {}).length !== 4 || [...profiles].some(name => !accounts.profiles[name])) {
  fail("profiles must be exactly early, mid, end, maxed");
}
for (const [name, profile] of Object.entries(accounts.profiles)) {
  if (!isObject(profile)) fail(`${name} must be an object`);
  for (const field of ["levels", "completedQuests", "varbits", "varplayers", "inventory", "equipment",
    "runePouch", "bank", "diaries", "poh", "plantedSpiritTrees", "fairyRingsUnlocked", "runtime"]) {
    if (!(field in profile)) fail(`${name}: missing ${field}`);
  }
  isIntegerMap(profile.levels, `${name}.levels`, /^.+$/);
  isStringArray(profile.completedQuests, `${name}.completedQuests`);
  if (new Set(profile.completedQuests).size !== profile.completedQuests.length) fail(`${name}: duplicate completed quest`);
  isIntegerMap(profile.varbits, `${name}.varbits`);
  isIntegerMap(profile.varplayers, `${name}.varplayers`);
  for (const field of ["inventory", "equipment", "runePouch", "bank"]) isIntegerMap(profile[field], `${name}.${field}`);
  if (!isObject(profile.diaries) || Object.values(profile.diaries).some(value =>
    !["NoDiary", "Easy", "Medium", "Hard", "Elite"].includes(value))) fail(`${name}.diaries has invalid tiers`);
  isStringArray(profile.plantedSpiritTrees, `${name}.plantedSpiritTrees`);
  if (typeof profile.fairyRingsUnlocked !== "boolean") fail(`${name}.fairyRingsUnlocked must be boolean`);
  const poh = profile.poh;
  if (!isObject(poh) || typeof poh.fairyRing !== "boolean" || typeof poh.jewelleryBox !== "string"
    || typeof poh.location !== "string" || typeof poh.mountedDigsite !== "boolean"
    || typeof poh.mountedGlory !== "boolean" || typeof poh.mountedMythical !== "boolean"
    || typeof poh.mountedXerics !== "boolean" || typeof poh.obelisk !== "boolean"
    || !isObject(poh.portals) || !["all", "selected"].includes(poh.portals.mode)
    || !Array.isArray(poh.portals.destinations) || poh.portals.destinations.some(value => typeof value !== "string")
    || typeof poh.spiritTree !== "boolean") fail(`${name}.poh has invalid shape`);
  const runtime = profile.runtime;
  if (!isObject(runtime) || typeof runtime.arriveInsidePoh !== "boolean" || typeof runtime.spellbook !== "string"
    || !isObject(runtime.minigameTeleport) || !["ready", "usedAt"].includes(runtime.minigameTeleport.state)
    || (runtime.minigameTeleport.state === "usedAt" && !Number.isInteger(runtime.minigameTeleport.minutes))) {
    fail(`${name}.runtime has invalid shape`);
  }
}
for (const name of profiles) {
  const preset = read(`profiles/${name}.json`);
  if (!validateAccount(preset)) fail(`${name} preset: ${ajv.errorsText(validateAccount.errors)}`);
  if (preset.id !== name) fail(`${name} preset ID must match its filename`);
  const { schemaVersion, id, name: label, benchmarkNowMinutes, routingVariables, ...semantic } = preset;
  assert.equal(schemaVersion, 1);
  assert.equal(benchmarkNowMinutes, accounts.benchmarkNowMinutes);
  assert.deepEqual({ ...semantic, varbits: routingVariables.varbits, varplayers: routingVariables.varplayers },
    accounts.profiles[name], `${name} preset differs from the benchmark fixture`);
}
const routes = read("corpus/routes-v1.json");
const ids = routes.map(route => route.id);
if (ids.some(id => typeof id !== "string" || !id) || new Set(ids).size !== ids.length) fail("route IDs must be unique non-empty strings");
for (const route of routes) {
  for (const field of ["name", "start", "target", "startName", "targetName", "startSource", "targetSource",
    "allowTransports", "tiers"]) if (!(field in route)) fail(`${route.id}: missing ${field}`);
  for (const field of ["name", "startName", "targetName", "startSource", "targetSource"]) {
    if (typeof route[field] !== "string" || !route[field]) fail(`${route.id}: ${field} must be a non-empty string`);
  }
  if (!isCoordinate(route.start) || !isCoordinate(route.target)) fail(`${route.id}: start and target must be integer [x, y, plane] coordinates`);
  if (typeof route.allowTransports !== "boolean" || !Array.isArray(route.tiers)) fail(`${route.id}: invalid transport or tiers value`);
  if (!route.tiers.includes("full") || route.tiers.some(tier => !["smoke", "standard", "full"].includes(tier))) fail(`${route.id}: invalid tiers`);
  if (route.negativeProfiles !== undefined && !Array.isArray(route.negativeProfiles)) fail(`${route.id}: negativeProfiles must be an array`);
  if ((route.negativeProfiles || []).some(name => !profiles.has(name))) fail(`${route.id}: invalid negative profile`);
}
const routeProfileCases = routes.length * profiles.size;
console.log(`valid corpus: ${routes.length} routes, ${routeProfileCases} route/profile cases`);
