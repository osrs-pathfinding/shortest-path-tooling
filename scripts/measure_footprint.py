#!/usr/bin/env python3
"""Measure the stripped-source footprint of the plugin's src/main/java.

Stripping rule (shared definition so phases can re-measure):
  - `//` line comments and `/* */` / `/** */` block comments are removed
    (a small state machine respects "..." string literals and '.' char
    literals, so `//` inside a string does not start a comment).
  - Lines that are empty or whitespace-only after comment removal are dropped.
  - All remaining characters count — including indentation and other
    in-code whitespace. Each kept line also counts its newline
    (size = `wc -c` of the stripped file). This convention reproduces
    the recorded v1.22.2 baseline within 1 char (715,864 vs ~715,863).
  - `package-info.java` files count too (they carry annotations/imports).

Usage:
  python3 measure_footprint.py [--ref <git-ref>] [--repo <submodule-path>]

Defaults: --ref HEAD --repo shortest-path  (run from the tooling repo root).
Reads blobs via `git show <ref>:<path>` — no checkout, works on a pinned
submodule and on historical tags alike.

Output: TSV of <stripped-chars>\t<path> sorted by size desc, then package
rollups, then the grand total, on stdout. With --csv FILE also writes the
per-file table.
"""
import argparse
import csv
import os
import subprocess
from collections import defaultdict

SRC_ROOT = "src/main/java"


def strip_comments(text):
    """Remove // and /* */ comments, respecting string/char literals."""
    out = []
    i, n = 0, len(text)
    state = "code"  # code | line | block | str | chr
    while i < n:
        c = text[i]
        if state == "code":
            if c == "/" and i + 1 < n and text[i + 1] == "/":
                state = "line"
                i += 2
            elif c == "/" and i + 1 < n and text[i + 1] == "*":
                state = "block"
                i += 2
            elif c == '"':
                out.append(c)
                state = "str"
                i += 1
            elif c == "'":
                out.append(c)
                state = "chr"
                i += 1
            else:
                out.append(c)
                i += 1
        elif state == "line":
            if c == "\n":
                out.append(c)
                state = "code"
            i += 1
        elif state == "block":
            if c == "*" and i + 1 < n and text[i + 1] == "/":
                state = "code"
                i += 2
            else:
                i += 1
        elif state == "str":
            out.append(c)
            if c == "\\" and i + 1 < n:
                out.append(text[i + 1])
                i += 1
            elif c == '"':
                state = "code"
            i += 1
        elif state == "chr":
            out.append(c)
            if c == "\\" and i + 1 < n:
                out.append(text[i + 1])
                i += 1
            elif c == "'":
                state = "code"
            i += 1
    return "".join(out)


def stripped_size(text):
    """Chars of the comment-free, blank-line-free source.

    Size = sum(len(line) + 1 for line in kept_lines) — every kept line
    counts its characters plus its newline, i.e. `wc -c` of the stripped
    text. This reproduces the previously recorded v1.22.2 baseline of
    ~715,863 within one char (715,864).
    """
    kept = [ln for ln in strip_comments(text).split("\n") if ln.strip()]
    return sum(len(ln) + 1 for ln in kept)


def git(repo, *args):
    return subprocess.run(
        ["git", "-C", repo, *args], check=True, capture_output=True, text=True
    ).stdout


def files_at_ref(repo, ref):
    names = git(repo, "ls-tree", "-r", "--name-only", ref, "--", SRC_ROOT)
    return [n for n in names.splitlines() if n.endswith(".java")]


def measure(repo, ref):
    sizes = {}
    for path in files_at_ref(repo, ref):
        content = git(repo, "show", f"{ref}:{path}")
        sizes[path] = stripped_size(content)
    return sizes


def package_of(path):
    """Package key = directory under src/main/java/, '.' for bare files."""
    d = os.path.dirname(path)
    rel = os.path.relpath(d, SRC_ROOT)
    return rel.replace(os.sep, "/")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ref", default="HEAD")
    ap.add_argument("--repo", default="shortest-path")
    ap.add_argument("--csv", default=None)
    ap.add_argument("--top", type=int, default=0, help="only print top N files")
    args = ap.parse_args()

    sizes = measure(args.repo, args.ref)
    ranked = sorted(sizes.items(), key=lambda kv: (-kv[1], kv[0]))

    if args.csv:
        with open(args.csv, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["path", "stripped_chars"])
            w.writerows(ranked)

    shown = ranked[: args.top] if args.top else ranked
    for path, sz in shown:
        print(f"{sz:>8}\t{path}")

    pkgs = defaultdict(lambda: [0, 0])
    for path, sz in sizes.items():
        pk = pkgs[package_of(path)]
        pk[0] += sz
        pk[1] += 1

    print("\n# package rollup (chars\tfiles\tpackage)")
    for pkg, (sz, cnt) in sorted(pkgs.items(), key=lambda kv: -kv[1][0]):
        print(f"{sz:>8}\t{cnt:>3}\t{pkg}")
    print(f"\nTOTAL\t{sum(sizes.values())}\t{len(sizes)} files")


if __name__ == "__main__":
    main()
