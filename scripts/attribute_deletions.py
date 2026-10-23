#!/usr/bin/env python3
"""Attribute a commit's removed src/main/java ranges to release segments.

Purpose: for each deletion commit in the submodule, map every removed
line range to the release segment that introduced the deleted text, so
the segment-attributed tag rebuild can split history by segment (the
emitted table is consumed verbatim — generated FROM the diff, it cannot
drift from what was actually removed).

Mechanism:
  1. `git show --format= -U0 <commit> -- 'src/main/java/**'` yields the
     parent-coordinate hunks; each contiguous run of '-' lines inside a
     hunk is one removed range (parent line numbers).
  2. `git blame --porcelain <commit>^ -- <file>` maps each parent line to
     its last-touch SHA (one blame call per touched file).
  3. `git tag --contains <sha>` intersected with the ordered release tag
     list gives the line's segment: the EARLIEST containing tag wins;
     an empty intersection lands in the post-release lane.
  4. A range's segment is the max over its lines' segments, ordering the
     post-release lane as newest.

Caveats:
  - git blame yields last-touch: lines cosmetically retouched attribute
    to the retoucher's segment — accepted under "the segment that
    introduced the affected code in deleted form".
  - Mixed-era hunks roll up by max-bucket — the only mechanically
    correct placement under the chained rebuild.

Usage:
  python3 attribute_deletions.py --commit <sha> [--repo shortest-path]
      [--tags v1.22.2,v1.22.3,v1.22.4,v1.22.5] [--post-label post-v1.22.5]

Output: '#' legend lines then one TSV row per removed range:
  path<TAB>parent-line-range<TAB>segment
"""
from __future__ import annotations

import argparse
import subprocess
import sys


def git(repo, *args):
    return subprocess.run(
        ["git", "-C", repo, *args], check=True, capture_output=True, text=True
    ).stdout


def parse_show_u0(diff_text):
    """Parse `git show -U0` output into (path, [(start, end), ...]) rows.

    Files are taken from `diff --git a/<path>` headers (the parent-side
    path, which is what blame on the parent resolves).  Each contiguous
    run of '-' lines inside a hunk is one removed range in parent line
    numbers; runs separated by '+' or context lines stay separate rows.
    Files with no removed lines are dropped.
    """
    files = []
    cur_path = None
    cur_ranges = None
    run_start = None
    prev_line = None
    old_lineno = None

    def close_run():
        nonlocal run_start, prev_line
        if run_start is not None:
            cur_ranges.append((run_start, prev_line))
            run_start = None
            prev_line = None

    for raw in diff_text.splitlines():
        if raw.startswith("diff --git "):
            close_run()
            # 'diff --git a/<old> b/<new>' — the a/ side is the path the
            # removed lines had in the parent commit.
            cur_path = raw.split(" ")[2][2:]
            cur_ranges = []
            files.append((cur_path, cur_ranges))
            old_lineno = None
            continue
        if raw.startswith("@@ ") and cur_path is not None:
            close_run()
            spec = raw.split(" ")[1][1:]  # '-os[,ol]' -> 'os[,ol]'
            old_lineno = int(spec.split(",")[0])
            continue
        if old_lineno is None:
            continue  # index/---/+++ lines between 'diff --git' and '@@'
        if raw.startswith("\\"):
            continue  # '\ No newline at end of file'
        if raw.startswith("-"):
            if run_start is None:
                run_start = old_lineno
            prev_line = old_lineno
            old_lineno += 1
            continue
        if raw.startswith("+"):
            close_run()
            continue
        close_run()
        if raw.startswith(" "):
            old_lineno += 1
    close_run()
    return [(p, rs) for p, rs in files if rs]


def parse_blame_porcelain(text):
    """Return {final_lineno: sha} from `git blame --porcelain` output.

    Group headers ('<sha> <orig> <final> [<count>]') carry the SHA; the
    attribute lines and the tab-prefixed content lines are skipped.
    """
    line_sha = {}
    for raw in text.splitlines():
        parts = raw.split(" ")
        if (len(parts) in (3, 4)
                and len(parts[0]) == 40
                and all(c in "0123456789abcdef" for c in parts[0])
                and parts[1].isdigit() and parts[2].isdigit()):
            final = int(parts[2])
            count = int(parts[3]) if len(parts) == 4 else 1
            for ln in range(final, final + count):
                line_sha[ln] = parts[0]
    return line_sha


def segment_index(repo, sha, tags):
    """Index of the earliest tag in ``tags`` containing ``sha``;
    ``len(tags)`` when no tag contains it (the post-release lane)."""
    containing = set(git(repo, "tag", "--contains", sha).split())
    for i, t in enumerate(tags):
        if t in containing:
            return i
    return len(tags)


def attribute(repo, commit, tags, post_label):
    """Return [(path, start, end, segment_label)] for a commit's removed
    src/main/java ranges.  A range's segment is the max over its lines'
    segments — the post-release lane sorts newest."""
    diff = git(repo, "show", "--format=", "-U0", commit, "--",
               "src/main/java/**")
    seg_cache = {}
    rows = []
    for path, ranges in parse_show_u0(diff):
        line_sha = parse_blame_porcelain(
            git(repo, "blame", "--porcelain", f"{commit}^", "--", path))
        for start, end in ranges:
            idx = -1
            for ln in range(start, end + 1):
                sha = line_sha[ln]
                if sha not in seg_cache:
                    seg_cache[sha] = segment_index(repo, sha, tags)
                idx = max(idx, seg_cache[sha])
            rows.append((path, start, end,
                         tags[idx] if idx < len(tags) else post_label))
    return rows


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--commit", required=True)
    ap.add_argument("--repo", default="shortest-path")
    ap.add_argument("--tags", default="v1.22.2,v1.22.3,v1.22.4,v1.22.5")
    ap.add_argument("--post-label", default=None)
    args = ap.parse_args()
    tags = [t.strip() for t in args.tags.split(",") if t.strip()]
    post_label = args.post_label or (
        f"post-{tags[-1]}" if tags else "post-release")

    head = git(args.repo, "log", "-1", "--format=%h %s", args.commit).strip()
    print(f"# attribution {head}")
    print("# file\tparent-lines\tsegment")
    print("# blame=last-touch; mixed-era hunks roll up by max-bucket")

    for path, start, end, label in attribute(
            args.repo, args.commit, tags, post_label):
        rng = str(start) if start == end else f"{start}-{end}"
        print(f"{path}\t{rng}\t{label}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
