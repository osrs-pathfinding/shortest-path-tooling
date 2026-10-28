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

`--ref` also accepts a comma-separated list (`--ref a,b,c`): every ref is
measured in argument order and gets its own output block under a
`# ref <ref>` header; the CSV gains a leading `ref` column (ref-major,
in argument order). A single-ref invocation keeps the original output
shape exactly. A ref that fails to resolve fails the whole run non-zero
before anything is emitted — a truncated table never reads as green.

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
    try:
        return subprocess.run(
            ["git", "-C", repo, *args], check=True, capture_output=True, text=True
        ).stdout
    except subprocess.CalledProcessError as e:
        raise RuntimeError(
            f"git -C {repo} {' '.join(args)} failed with exit code "
            f"{e.returncode}:\n{(e.stderr or '').strip()}"
        ) from e


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


def parse_refs(arg):
    """`--ref` value → list of refs; comma-separated lists measure each."""
    refs = [r.strip() for r in arg.split(",")]
    if not all(refs):
        raise ValueError("--ref entries must be non-empty")
    return refs


def report(sizes, top, show_ref=None):
    """Print one ref's table, package rollups and grand total."""
    ranked = sorted(sizes.items(), key=lambda kv: (-kv[1], kv[0]))
    if show_ref is not None:
        print(f"# ref {show_ref}")
    shown = ranked[:top] if top else ranked
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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ref", default="HEAD",
                    help="git ref to measure; a comma-separated list "
                         "measures each ref in turn")
    ap.add_argument("--repo", default="shortest-path")
    ap.add_argument("--csv", default=None)
    ap.add_argument("--top", type=int, default=0, help="only print top N files")
    args = ap.parse_args()
    if args.top < 0:
        ap.error("--top must be >= 0")
    try:
        refs = parse_refs(args.ref)
    except ValueError as e:
        ap.error(str(e))

    # Measure every ref before emitting anything: a ref that fails to
    # resolve fails the whole run non-zero rather than printing a
    # truncated-but-green table.
    all_sizes = {}
    for ref in refs:
        try:
            all_sizes[ref] = measure(args.repo, ref)
        except RuntimeError as e:
            ap.error(f"{ref}: {e}")

    multi = len(refs) > 1
    if args.csv:
        with open(args.csv, "w", newline="") as f:
            w = csv.writer(f)
            if multi:
                w.writerow(["ref", "path", "stripped_chars"])
            else:
                w.writerow(["path", "stripped_chars"])
            for ref in refs:
                ranked = sorted(all_sizes[ref].items(),
                                key=lambda kv: (-kv[1], kv[0]))
                for path, sz in ranked:
                    if multi:
                        w.writerow([ref, path, sz])
                    else:
                        w.writerow([path, sz])

    for ref in refs:
        report(all_sizes[ref], args.top, show_ref=ref if multi else None)


if __name__ == "__main__":
    main()
