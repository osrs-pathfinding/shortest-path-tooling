#!/usr/bin/env python3
"""Emit fragment specs for the segment-attributed history rebuild.

Purpose: consume the frozen per-commit ATTRIBUTION ledgers (one
``## <sha> <subject>`` section per series commit; TSV rows
``path<TAB>parent-line-range<TAB>segment``) plus a live
``git show --format= -U0`` re-parse of each series commit's
``src/main/java/**`` diff, and emit the fragment spec set the rebuild
driver applies — one fragment per contiguous same-segment hunk cluster
within one file of one commit, in original series order.

Mechanism:
  1. Enumerate series commits via ``git rev-list``.
  2. Parse each ledger into {commit-abbrev: [(path, start, end,
     segment)]}; rows keyed by the section's sha, resolved against the
     series by prefix. Non-sha ``##`` headers (``## Tracer``, ``##
     Phase close``, …) and prose are ignored.
  3. For each commit with ≥1 row: re-parse the ``-U0`` diff into hunks
     carrying their ``-`` runs (parent-line coords) and verbatim ``+``
     lines. Each ledger row maps to its containing hunk by exact
     parent-line-range match on the hunk's ``-`` runs.
  4. Hunk segment = max over its rows' segments
     (v1.22.2 < v1.22.3 < v1.22.4 < v1.22.5 < post-v1.22.5) — a hunk
     whose rows split across segments lands wholly in the newest era
     its ``+`` text can reference.
  5. A fragment is a maximal run of contiguous same-segment hunks
     within one file of one commit. It carries the verbatim ``-`` run
     texts extracted at the commit's parent (content-anchored locate
     input), ±2 context lines per run, the cluster's combined ``+``
     text, and an ``<i>/<n>`` ordinal where n is the commit's total
     fragment count across ALL segments.
  6. A commit's non-src/main paths (test migrations, build files,
     resources, docs) ride its max-segment fragment as companions.
  7. A commit with zero src/main/java ledger rows routes wholesale
     (post-.5 lane); a wholesale commit whose ``-U0`` diff still has
     ``-`` runs is unattributed — flagged, never silently dropped.
  8. Coverage: every parsed ledger row lands in exactly one emitted
     fragment; a row with no containing hunk, or a hunk ``-`` run with
     no covering row, fails loudly.

Caveats:
  - Ledgers are frozen input — this script never re-runs blame or tag
    resolution; it only re-parses diffs and blobs.
  - A row range that does not exactly equal a ``-`` run range means the
    ledger or the diff moved — the run fails rather than guessing.

Usage:
  python3 rebuild_fragments.py [--ledgers GLOB...] [--repo shortest-path]
      [--series-base 142e73e2] [--branch series-rebuild] [--out PATH]

Output: JSON spec set written to --out (default
.planning/phases/shortest-path-tooling-36-attribution-rebuild-ship/36-FRAGMENTS.json):

  {"fragments": [{"id", "commit", "subject", "ordinal", "file",
                  "segment",
                  "minus_runs": [{"lines", "text",
                                  "context_before", "context_after"}],
                  "plus_text", "companions": [paths]}],
   "wholesale": [{"sha", "subject", "paths"}],
   "unattributed_deletions": [{"sha", "subject", "files"}],
   "stats": {"rows_per_segment": {seg: n},
             "fragments_per_segment": {seg: n},
             "crossing": n, "wholesale": n}}
"""
from __future__ import annotations

import argparse
import glob
import json
import re
import subprocess
import sys

SEGMENT_ORDER = ["v1.22.2", "v1.22.3", "v1.22.4", "v1.22.5", "post-v1.22.5"]
SRC_GLOB = "src/main/java/**"
ROW_RE = re.compile(
    r"^(src/main/java/\S+)\t([0-9]+)(?:-([0-9]+))?\t"
    r"(v1\.22\.[0-9]|post-v1\.22\.5)\s*$")
SHA_HEAD_RE = re.compile(r"^## ([0-9a-f]{7,9})(?:\s|$)")


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


def parse_ledger(text):
    """{sha-abbrev: [(path, start, end, segment)]} from one ledger.

    Rows live under ``## <sha>`` section headers; legend lines, prose,
    code fences, and non-sha ``##`` headers are ignored.
    """
    rows = {}
    cur = None
    for line in text.splitlines():
        head = SHA_HEAD_RE.match(line)
        if head:
            cur = head.group(1)
            rows.setdefault(cur, [])
            continue
        if line.startswith("## "):
            cur = None
            continue
        if cur is None:
            continue
        m = ROW_RE.match(line)
        if m:
            path, start, end, segment = m.groups()
            rows[cur].append(
                (path, int(start), int(end or start), segment))
    return rows


def parse_show_u0_hunks(diff_text):
    """[{'file': path, 'hunks': [{'minus': [(s,e)], 'plus': [lines],
    'start': parent_lineno}]}] from ``git show --format= -U0`` output.

    Each contiguous run of '-' lines inside a hunk is one removed range
    in parent line numbers; '+' lines are captured verbatim (without
    the leading '+').
    """
    files = []
    cur_file = None
    cur_hunk = None
    run_start = None
    prev_line = None
    old_lineno = None

    def close_run():
        nonlocal run_start, prev_line
        if run_start is not None:
            cur_hunk["minus"].append((run_start, prev_line))
            run_start = None
            prev_line = None

    for raw in diff_text.splitlines():
        if raw.startswith("diff --git "):
            close_run()
            cur_file = {"file": raw.split(" ")[2][2:], "hunks": []}
            files.append(cur_file)
            cur_hunk = None
            old_lineno = None
            continue
        if raw.startswith("@@ ") and cur_file is not None:
            close_run()
            spec = raw.split(" ")[1][1:]  # '-os[,ol]' -> 'os[,ol]'
            old_lineno = int(spec.split(",")[0])
            cur_hunk = {"minus": [], "plus": [], "start": old_lineno}
            cur_file["hunks"].append(cur_hunk)
            continue
        if old_lineno is None or cur_hunk is None:
            continue
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
            cur_hunk["plus"].append(raw[1:])
            continue
        close_run()
        if raw.startswith(" "):
            old_lineno += 1
    close_run()
    return files


def _resolve(abbrev, series_shas):
    hits = [s for s in series_shas if s.startswith(abbrev)]
    if len(hits) != 1:
        raise RuntimeError(
            f"ledger sha {abbrev} matches {len(hits)} series commits")
    return hits[0]


def _series_commits(repo, series_base, branch):
    out = git(repo, "rev-list", "--reverse", "--no-commit-header",
              "--format=%H%x09%s", f"{series_base}..{branch}")
    commits = []
    for line in out.splitlines():
        if line.strip():
            sha, subject = line.split("\t", 1)
            commits.append((sha.strip(), subject.strip()))
    return commits


def _minus_run_spec(lines, blob_lines, start, end):
    """Verbatim deleted text + ±2 context lines for one -run."""
    text = "".join(blob_lines[start - 1:end])
    before = "".join(blob_lines[max(0, start - 3):start - 1])
    after = "".join(blob_lines[end:end + 2])
    return {"lines": f"{start}-{end}" if start != end else str(start),
            "text": text,
            "context_before": before,
            "context_after": after}


def generate(repo, series_base, branch, ledger_paths):
    commits = _series_commits(repo, series_base, branch)
    series_shas = [c for c, _ in commits]

    # ledger rows resolved onto full series shas
    rows_by_commit = {}
    for path in ledger_paths:
        try:
            text = open(path).read()
        except FileNotFoundError:
            print(f"warning: ledger {path} missing — skipping",
                  file=sys.stderr)
            continue
        for abbrev, rows in parse_ledger(text).items():
            # A section with no rows attributes nothing — docs-only or
            # outer-repo commits carry an empty block by convention, and
            # resolving their abbrev against the series would spuriously
            # fail (an outer commit is not a series commit).
            if not rows:
                continue
            sha = _resolve(abbrev, series_shas)
            rows_by_commit.setdefault(sha, []).extend(rows)

    fragments = []
    wholesale = []
    unattributed = []
    seg_rows = {}
    seg_frags = {}

    for sha, subject in commits:
        rows = rows_by_commit.get(sha, [])
        paths = [p for p in git(
            repo, "show", "--format=", "--name-only", sha).splitlines()
            if p.strip()]
        if not rows:
            wholesale.append(
                {"sha": sha, "subject": subject, "paths": paths})
            diff = git(repo, "show", "--format=", "-U0", sha, "--",
                       SRC_GLOB)
            flagged = sorted(
                f["file"] for f in parse_show_u0_hunks(diff)
                if any(h["minus"] for h in f["hunks"]))
            if flagged:
                unattributed.append(
                    {"sha": sha, "subject": subject, "files": flagged})
            continue

        diff = git(repo, "show", "--format=", "-U0", sha, "--", SRC_GLOB)
        files = parse_show_u0_hunks(diff)

        # map rows to -runs per file; disagreement fails loudly
        rows_by_file = {}
        for path, start, end, segment in rows:
            rows_by_file.setdefault(path, []).append(
                (start, end, segment))
        covered = {}  # (file, hunk_idx, run_idx) -> row
        for f in files:
            path = f["file"]
            ledger_runs = rows_by_file.get(path, [])
            for hi, hunk in enumerate(f["hunks"]):
                for ri, (start, end) in enumerate(hunk["minus"]):
                    match = [r for r in ledger_runs
                             if r[0] == start and r[1] == end]
                    if not match:
                        raise RuntimeError(
                            f"{sha}: hunk -run {path}:{start}-{end} "
                            "disagrees with the ledger (no row covers it)")
                    if len(match) > 1:
                        raise RuntimeError(
                            f"{sha}: duplicate ledger rows for "
                            f"{path}:{start}-{end}")
                    covered[(path, hi, ri)] = match[0]
        for path, lruns in rows_by_file.items():
            seen = {(c[0], c[1]) for (p, _, _), c in covered.items()
                    if p == path}
            for start, end, _seg in lruns:
                if (start, end) not in seen:
                    raise RuntimeError(
                        f"{sha}: ledger row {path}:{start}-{end} has "
                        "no containing hunk")

        # hunk segment = max over its rows; cluster contiguous same-segment
        blobs = {}
        per_commit = []
        plus_only = {}  # path -> ['+'-only hunks, file order]
        for f in files:
            path = f["file"]
            for hi, hunk in enumerate(f["hunks"]):
                segs = [covered[(path, hi, ri)][2]
                        for ri in range(len(hunk["minus"]))]
                if not segs:
                    # '+'-only hunk — no deletion to attribute, but its
                    # content is still part of the commit and may be
                    # gate-required (imports, new methods, new files).
                    plus_only.setdefault(path, []).append(hunk)
                    continue
                segment = max(segs, key=SEGMENT_ORDER.index)
                if (per_commit and per_commit[-1]["file"] == path
                        and per_commit[-1]["segment"] == segment):
                    per_commit[-1]["hunks"].append(hunk)
                else:
                    per_commit.append(
                        {"file": path, "segment": segment,
                         "hunks": [hunk]})

        # '+'-only content lands somewhere compilable rather than being
        # dropped: an intra-file insertion rides the fragment containing
        # the NEXT attributed hunk in file order (an import belongs to
        # the code it precedes; fallback: the preceding fragment).  A
        # file with no attributed hunks at all attaches to the commit's
        # max-segment fragment — as a creation when the file is new in
        # this commit, else as a cross-file insertion.  The driver's
        # bump machinery re-homes anything that still cannot compile.
        new_files = set(git(
            repo, "show", "--format=", "--diff-filter=A", "--name-only",
            sha, "--", SRC_GLOB).split())
        n_commit = len(per_commit)
        max_idx = (max(range(n_commit), key=lambda i: SEGMENT_ORDER.index(
            per_commit[i]["segment"])) if n_commit else -1)
        insertions = {}  # frag idx -> [insertion records]
        for path, po_hunks in plus_only.items():
            if path in new_files:
                per_commit[max_idx].setdefault("creations", []).append(
                    {"file": path,
                     "content": git(repo, "show", f"{sha}:{path}")})
                continue
            file_frags = sorted(
                (h["start"], i)
                for i, fr in enumerate(per_commit) if fr["file"] == path
                for h in fr["hunks"])
            for hunk in po_hunks:
                host = next(
                    (i for st, i in file_frags if st > hunk["start"]),
                    None)
                if host is None:
                    host = file_frags[-1][1] if file_frags else max_idx
                if path not in blobs:
                    blobs[path] = git(
                        repo, "show", f"{sha}^:{path}"
                    ).splitlines(keepends=True)
                blob = blobs[path]
                pos = hunk["start"]
                insertions.setdefault(host, []).append({
                    "file": path, "pos": pos,
                    "before": "".join(blob[max(0, pos - 2):pos]),
                    "after": "".join(blob[pos:pos + 2]),
                    "text": "".join(l + "\n" for l in hunk["plus"])})

        # Coverage: every non-blank '+' line of this commit's src/main
        # diff lands in exactly one emitted slot — a fragment hunk's
        # plus_text, an insertion, or a creation.  Blank lines ride
        # their hunk verbatim but are noise for accounting.  A silent
        # drop is a spec defect.
        for f in files:
            path = f["file"]
            want = sorted(l for h in f["hunks"] for l in h["plus"]
                          if l.strip())
            got = sorted(
                l for fr in per_commit if fr["file"] == path
                for l in "".join(
                    p + "\n" for h in fr["hunks"] for p in h["plus"])
                .splitlines() if l.strip())
            got += sorted(
                l for ins_list in insertions.values()
                for ins in ins_list if ins["file"] == path
                for l in ins["text"].splitlines() if l.strip())
            got += sorted(
                l for fr in per_commit
                for c in fr.get("creations", []) if c["file"] == path
                for l in c["content"].splitlines() if l.strip())
            missing = sorted(set(want) - set(got))
            if missing:
                raise RuntimeError(
                    f"{sha}: '+'-line coverage gap on {path} — diff "
                    f"lines not emitted anywhere: {missing[:5]}")

        n = len(per_commit)
        companions = [p for p in paths if not p.startswith("src/main/java/")]
        max_idx = max(range(n),
                      key=lambda i: SEGMENT_ORDER.index(
                          per_commit[i]["segment"])) if n else -1
        for i, frag in enumerate(per_commit):
            path = frag["file"]
            if path not in blobs:
                blobs[path] = git(
                    repo, "show", f"{sha}^:{path}").splitlines(keepends=True)
            minus_runs = []
            for hunk in frag["hunks"]:
                for ri, (start, end) in enumerate(hunk["minus"]):
                    hi = f_idx = None
                    for cand_f in files:
                        if cand_f["file"] != path:
                            continue
                        for cand_hi, cand_h in enumerate(cand_f["hunks"]):
                            if cand_h is hunk:
                                hi = cand_hi
                    row = covered[(path, hi, ri)]
                    seg_rows[row[2]] = seg_rows.get(row[2], 0) + 1
                    minus_runs.append(
                        _minus_run_spec(None, blobs[path], start, end))
            plus_text = "".join(
                line + "\n" for hunk in frag["hunks"]
                for line in hunk["plus"])
            seg_frags[frag["segment"]] = seg_frags.get(
                frag["segment"], 0) + 1
            frag_entry = {
                "id": f"{sha[:8]}-{i + 1}of{n}",
                "commit": sha[:8], "subject": subject,
                "ordinal": f"{i + 1}/{n}",
                "file": path, "segment": frag["segment"],
                "minus_runs": minus_runs,
                "plus_text": plus_text,
                "companions": companions if i == max_idx else []}
            if insertions.get(i):
                frag_entry["insertions"] = insertions[i]
            if frag.get("creations"):
                frag_entry["creations"] = frag["creations"]
            fragments.append(frag_entry)

    return {
        "fragments": fragments,
        "wholesale": wholesale,
        "unattributed_deletions": unattributed,
        "stats": {
            "rows_per_segment": seg_rows,
            "fragments_per_segment": seg_frags,
            "crossing": sum(1 for c in commits
                            if rows_by_commit.get(c[0])),
            "wholesale": len(wholesale),
        },
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default="shortest-path")
    ap.add_argument("--series-base", default="142e73e2")
    ap.add_argument("--branch", default="series-rebuild")
    ap.add_argument("--ledgers", nargs="*", default=None,
                    help="ledger paths/globs; default the five phase "
                         "ledgers plus the phase-36 sweep ledger")
    ap.add_argument("--out", default=".planning/phases/"
                    "shortest-path-tooling-36-attribution-rebuild-ship/"
                    "36-FRAGMENTS.json")
    args = ap.parse_args()

    if args.ledgers is None:
        patterns = [".planning/phases/shortest-path-tooling-3?-*/"
                    "3?-ATTRIBUTION.md",
                    ".planning/phases/"
                    "shortest-path-tooling-36-attribution-rebuild-ship/"
                    "36-ATTRIBUTION.md"]
        ledgers = []
        for pat in patterns:
            ledgers.extend(sorted(glob.glob(pat)))
    else:
        ledgers = []
        for pat in args.ledgers:
            ledgers.extend(sorted(glob.glob(pat)) or [pat])

    doc = generate(args.repo, args.series_base, args.branch, ledgers)

    consumed = sum(
        len(f["minus_runs"]) for f in doc["fragments"])
    parsed = sum(doc["stats"]["rows_per_segment"].values())
    if consumed != parsed:
        raise RuntimeError(
            f"coverage assert failed: {parsed} ledger rows vs "
            f"{consumed} emitted -runs")

    with open(args.out, "w") as fh:
        json.dump(doc, fh, indent=2)
        fh.write("\n")

    print(f"fragments: {len(doc['fragments'])} "
          f"({doc['stats']['fragments_per_segment']})")
    print(f"rows: {parsed} ({doc['stats']['rows_per_segment']})")
    print(f"crossing: {doc['stats']['crossing']} "
          f"wholesale: {doc['stats']['wholesale']} "
          f"unattributed: {len(doc['unattributed_deletions'])}")
    for u in doc["unattributed_deletions"]:
        print(f"  unattributed deletions: {u['sha']} {u['subject']} "
              f"({', '.join(u['files'])})")
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
