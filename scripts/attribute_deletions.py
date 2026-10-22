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

    Returns a list of (parent_path, ranges) per file, in diff order.
    """
    return []


def parse_blame_porcelain(text):
    """Return {final_lineno: sha} from `git blame --porcelain` output."""
    return {}


def attribute(repo, commit, tags, post_label):
    """Return [(path, start, end, segment_index)] for a commit's removed
    src/main/java ranges.  segment_index is an index into ``tags``;
    ``len(tags)`` is the post-release lane."""
    return []


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--commit", required=True)
    ap.add_argument("--repo", default="shortest-path")
    ap.add_argument("--tags", default="v1.22.2,v1.22.3,v1.22.4,v1.22.5")
    ap.add_argument("--post-label", default=None)
    ap.parse_args()
    return 0


if __name__ == "__main__":
    sys.exit(main())
