#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
if (process.argv.length !== 3) throw new Error("usage: compare-corpus.js OTHER_CORPUS_DIR");
const here = path.resolve(__dirname, "..");
const other = path.resolve(process.argv[2]);
const read = (root, file) => JSON.parse(fs.readFileSync(path.join(root, file), "utf8"));
const keySet = routes => new Set(routes.flatMap(route => ["early", "mid", "end", "maxed"].map(profile => `${route.id}/${profile}`)));
const leftRoutes = read(here, "corpus/routes-v1.json");
const rightRoutes = read(other, "corpus/routes-v1.json");
const leftKeys = keySet(leftRoutes);
const rightKeys = keySet(rightRoutes);
const missingKeys = [...leftKeys].filter(key => !rightKeys.has(key)).sort();
const extraKeys = [...rightKeys].filter(key => !leftKeys.has(key)).sort();
console.log(JSON.stringify({routeCount: [leftRoutes.length, rightRoutes.length], caseCount: [leftKeys.size, rightKeys.size], missingKeys, extraKeys}, null, 2));
process.exitCode = missingKeys.length || extraKeys.length ? 1 : 0;
