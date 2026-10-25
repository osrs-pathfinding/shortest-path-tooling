#!/usr/bin/env python3
"""Measure the real-tokenizer token count of the plugin's src/main/java.

The plugin-hub auto-reviewer's budget counts tokens on a rule it states
verbatim: "Comments, resources, test sources, and the readme do not
count".  That rule is exactly ``measure_footprint.py``'s comment
state machine applied to ``src/main/java`` before counting, so this
script importlib-loads that module and reuses ``git()``,
``files_at_ref()``, ``strip_comments()``, and ``package_of()``
verbatim — only the unit changes: ``tiktoken`` ``cl100k_base`` tokens
instead of stripped characters.

Calibration: the only published review count is 208k tokens on the
v1.22.2-submission tree at ``2069bf9f`` (749,126 stripped chars, i.e.
~3.6 stripped-chars/token — a real BPE counter, not the chars/4 proxy,
which reads ~11% low on that point).  Treat this harness's absolute
number as calibrated within ~±5% of the reviewer's private counter.

Usage:
  python3 measure_tokens.py [--ref <git-ref>] [--repo <submodule-path>]
                            [--top N] [--csv FILE]

Defaults: --ref HEAD --repo shortest-path  (run from the tooling repo
root).  Reads blobs via `git show <ref>:<path>` — no checkout, works on
a pinned submodule and on historical tags alike.

Output: TSV of <tokens>\t<path> sorted by size desc, then package
rollups, then the grand total (`TOTAL\t<n> tokens\t<k> files`), on
stdout.  With --csv FILE also writes the per-file table.
"""
import argparse
import csv
import importlib.util
from collections import defaultdict
from pathlib import Path

import tiktoken

_MF_PATH = Path(__file__).resolve().parent / "measure_footprint.py"
_spec = importlib.util.spec_from_file_location("measure_footprint", _MF_PATH)
mf = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mf)

ENCODING = "cl100k_base"


def measure(repo, ref, enc):
    """{path: token-count} over the stripped blobs of src/main/java."""
    counts = {}
    for path in mf.files_at_ref(repo, ref):
        content = mf.git(repo, "show", f"{ref}:{path}")
        counts[path] = len(enc.encode(mf.strip_comments(content)))
    return counts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ref", default="HEAD")
    ap.add_argument("--repo", default="shortest-path")
    ap.add_argument("--csv", default=None)
    ap.add_argument("--top", type=int, default=0, help="only print top N files")
    args = ap.parse_args()
    if args.top < 0:
        ap.error("--top must be >= 0")

    enc = tiktoken.get_encoding(ENCODING)
    counts = measure(args.repo, args.ref, enc)
    ranked = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))

    if args.csv:
        with open(args.csv, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["path", "tokens"])
            w.writerows(ranked)

    shown = ranked[: args.top] if args.top else ranked
    for path, n in shown:
        print(f"{n:>8}\t{path}")

    pkgs = defaultdict(lambda: [0, 0])
    for path, n in counts.items():
        pk = pkgs[mf.package_of(path)]
        pk[0] += n
        pk[1] += 1

    print("\n# package rollup (tokens\tfiles\tpackage)")
    for pkg, (n, cnt) in sorted(pkgs.items(), key=lambda kv: -kv[1][0]):
        print(f"{n:>8}\t{cnt:>3}\t{pkg}")
    print(f"\nTOTAL\t{sum(counts.values())} tokens\t{len(counts)} files")


if __name__ == "__main__":
    main()
