// Generates the frontend's copies of the contracts in ../corpus: the route API types, the account
// schema used to validate shared builds, and the preset profiles served from /data/profiles.
// npm runs this before dev, build and test; the outputs are not committed.
import { copyFile, mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { compileFromFile } from "json-schema-to-typescript";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const corpus = path.resolve(process.env.SHORTEST_PATH_CORPUS_DIR || path.join(root, "../corpus"));
const schemas = path.join(corpus, "schemas");
const generated = path.join(root, "src/generated");
const publicProfiles = path.join(root, "public/data/profiles");
const profiles = ["early", "mid", "end", "maxed"];

await mkdir(generated, { recursive: true });
await mkdir(publicProfiles, { recursive: true });

await copyFile(path.join(schemas, "account-build-v1.schema.json"), path.join(generated, "account-build-v1.schema.json"));
const types = await compileFromFile(path.join(schemas, "route-api-v1.schema.json"), {
  bannerComment: "/* Generated from corpus/schemas by scripts/sync-contracts.mjs. Do not edit. */",
  cwd: schemas,
  style: { singleQuote: false },
});
await writeFile(path.join(generated, "route-api-v1.d.ts"), types);

for (const name of profiles) {
  await copyFile(path.join(corpus, `profiles/${name}.json`), path.join(publicProfiles, `${name}.json`));
}
