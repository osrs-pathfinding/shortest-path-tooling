// Generates the frontend's copies of the contracts in ../corpus/schemas: the route API types and
// the account schema used to validate imported and shared accounts. npm runs this before dev,
// build and test; the outputs are not committed.
import { copyFile, mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { compileFromFile } from "json-schema-to-typescript";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const corpus = path.resolve(process.env.SHORTEST_PATH_CORPUS_DIR || path.join(root, "../corpus"));
const schemas = path.join(corpus, "schemas");
const generated = path.join(root, "src/generated");

await mkdir(generated, { recursive: true });
await copyFile(path.join(schemas, "account-v1.schema.json"), path.join(generated, "account-v1.schema.json"));
const types = await compileFromFile(path.join(schemas, "route-api-v1.schema.json"), {
  bannerComment: "/* Generated from corpus/schemas by scripts/sync-contracts.mjs. Do not edit. */",
  cwd: schemas,
  style: { singleQuote: false },
});
await writeFile(path.join(generated, "route-api-v1.d.ts"), types);
