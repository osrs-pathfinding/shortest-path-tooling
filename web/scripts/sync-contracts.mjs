import { mkdir, copyFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { compileFromFile } from "json-schema-to-typescript";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const corpus = path.resolve(process.env.SHORTEST_PATH_CORPUS_DIR || path.join(root, "../shortest-path-corpus"));
const generated = path.join(root, "src/generated");
const publicProfiles = path.join(root, "public/data/profiles");
const schemas = ["account-build-v1", "route-policy-v1", "place-v1", "route-api-v1"];
const profiles = ["early", "mid", "end", "maxed"];

await mkdir(generated, { recursive: true });
await mkdir(publicProfiles, { recursive: true });

for (const name of schemas) {
  const source = path.join(corpus, `schemas/${name}.schema.json`);
  const types = await compileFromFile(source, {
    bannerComment: "/* Generated from shortest-path-corpus. Do not edit. */",
    cwd: path.join(corpus, "schemas"),
    style: { singleQuote: false },
  });
  await writeFile(path.join(generated, `${name}.d.ts`), types);
}

for (const name of profiles) {
  await copyFile(path.join(corpus, `profiles/${name}.json`), path.join(publicProfiles, `${name}.json`));
}
