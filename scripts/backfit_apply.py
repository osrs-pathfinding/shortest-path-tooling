#!/usr/bin/env python3
"""Back-fit driver for plan 36-16: apply lane fragments at an earlier
release boundary, grouped by source commit.

Each group applies every listed fragment's anchored minus/plus edits
plus companions, commits once under ``<subject> [<ords>/n]``, then runs
the ``compileJava compileTestJava`` gate.  Groups that fail the gate
are reverted intact and re-queued once (providers may land later); a
second failure defers the whole group to the next boundary and it is
recorded in the outcome file.  Anchor conflicts likewise revert the
group untouched.

Usage:
  python3 backfit_apply.py run <cohort.json> [--worktree P] [--outcome P]

cohort.json: {"era_base": "<ref>", "groups": [{"commit": "<sha8>",
              "subject": "<original subject>", "fragments": ["<id>"]}]}
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PD = (ROOT / ".planning" / "phases" /
      "shortest-path-tooling-36-attribution-rebuild-ship")
DEFAULT_SPEC = PD / "36-FRAGMENTS.json"
DEFAULT_WT = "/tmp/sp36-rebuild"

_spec = importlib.util.spec_from_file_location(
    "rebuild_apply", ROOT / "scripts" / "rebuild_apply.py")
ra = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ra)


def git(repo, *args, check=True, input=None):
    return ra.git(repo, *args, check=check, input=input)


def gradle(worktree, *tasks):
    p = subprocess.run(
        ["./gradlew", "--no-daemon", *tasks], cwd=worktree,
        check=False, capture_output=True, text=True)
    return (p.stdout or "") + (p.stderr or ""), p.returncode


def snapshot(wt):
    """(status porcelain lines) for revert detection of new files."""
    return git(wt, "status", "--porcelain")


def revert_group(wt, before_status, touched, pre_head=None):
    """Restore pre-group state: drop the group commit if one was made,
    then restore every path the group touched to HEAD content."""
    if (pre_head and git(wt, "rev-parse", "HEAD").strip() != pre_head):
        git(wt, "reset", "--soft", "HEAD^")
        assert git(wt, "rev-parse", "HEAD").strip() == pre_head
    git(wt, "reset")
    for p in touched:
        try:
            git(wt, "checkout", "HEAD", "--", p)
        except RuntimeError:
            pass
        full = os.path.join(wt, p)
        if (os.path.exists(full)
                and not git(wt, "ls-files", "--", p).strip()):
            os.remove(full)
    # any residual staged/created artifacts
    after = git(wt, "status", "--porcelain").splitlines()
    for line in after:
        p = line[3:]
        if p not in touched and p.startswith("src/"):
            try:
                git(wt, "checkout", "HEAD", "--", p)
            except RuntimeError:
                full = os.path.join(wt, p)
                if os.path.exists(full) and not git(
                        wt, "ls-files", "--", p).strip():
                    os.remove(full)


def group_subject(group):
    frags = group["fragments"]
    n = int(group["fragments"][0].split("of")[1])
    ords = sorted(int(f.split("-")[-1].split("of")[0]) for f in frags)
    if ords == list(range(ords[0], ords[-1] + 1)):
        tag = f"{ords[0]}-{ords[-1]}" if len(ords) > 1 else f"{ords[0]}"
    else:
        tag = ",".join(str(o) for o in ords)
    return f"{group['subject']} [{tag}/{n}]"


def group_body(group, boundary):
    ords = ",".join(
        f.split("-")[-1] for f in
        sorted(group["fragments"],
               key=lambda f: int(f.split("-")[-1].split("of")[0])))
    return (f"Applied fragments {ords} of {group['commit']} "
            f"({group['subject']}).\n\n"
            f"Deleted text verified verbatim-present at the {boundary} "
            "release boundary.")


def touched_paths(frags):
    out = []
    for f in frags:
        out += ra._frag_paths(f)
    return list(dict.fromkeys(out))


def run_group(group, fragmap, wt, era_base, boundary, dry_gate=False):
    """Apply -> companions -> commit -> gate for one source-commit
    group.  Returns (verdict, detail) where verdict is 'applied' /
    'already' / 'conflict' / 'gate-failed'."""
    touched = touched_paths([fragmap[i] for i in group["fragments"]])
    for fid in group.get("companion_sources", []):
        touched += fragmap[fid].get("companions", [])
    touched = list(dict.fromkeys(touched))
    before = snapshot(wt)
    pre_head = git(wt, "rev-parse", "HEAD").strip()
    match_counts, already = [], 0
    for fid in group["fragments"]:
        frag = fragmap[fid]
        res = ra.apply_fragment(frag, wt, era_base, repo=wt)
        if res["status"] == "conflict":
            revert_group(wt, before, touched, pre_head)
            return ("conflict",
                    json.dumps(res["conflicts"])[:400])
        match_counts += res.get("match_counts", [])
        already += res.get("already", 0)
    deferred = []
    # Companions ride the commit's max-segment carrier in the spec, but
    # the deletions landing here need their test updates NOW — apply the
    # union of companions over the group's fragments plus the commit's
    # already-on-chain fragments (their test edits are absent at this
    # boundary but required for a green tree).
    comp_paths = []
    for fid in group.get("companion_sources", group["fragments"]):
        comp_paths += fragmap[fid].get("companions", [])
    comp_paths = list(dict.fromkeys(comp_paths))
    if comp_paths:
        pseudo = {"commit": group["commit"], "companions": comp_paths,
                  "_deferred": []}
        comp = ra.apply_companions(pseudo, wt)
        deferred += pseudo.get("_deferred", [])
        if comp == "conflict":
            revert_group(wt, before, touched, pre_head)
            return ("conflict", "companion apply failed on "
                    + ",".join(comp_paths)[:200])
    if not git(wt, "status", "--porcelain").strip():
        return ("already", "all edits already satisfied")
    subj = group_subject(group)
    git(wt, "commit", "-m", subj + "\n\n" + group_body(group, boundary))
    sha = git(wt, "rev-parse", "--short", "HEAD").strip()
    if dry_gate:
        return ("applied", f"{sha} (gate skipped)")
    out, rc = gradle(wt, "compileJava", "compileTestJava")
    if ra.gate_failed(rc, out):
        first_err = next((l.strip() for l in out.splitlines()
                          if "error:" in l), "")
        syms = ra._missing_symbols(out)
        revert_group(wt, before, touched, pre_head)
        return ("gate-failed",
                f"{first_err[:160]} | missing={','.join(syms) or '-'}")
    detail = sha
    if deferred:
        detail += f" deferred={','.join(deferred)}"
    return ("applied", detail)


def provider_in(pending_groups, fragmap, syms):
    for g in pending_groups:
        for fid in g["fragments"]:
            text = ra._search_text(fragmap[fid])
            if any(s in text for s in syms):
                return g["commit"]
    return None


def cmd_run(args):
    cohort = json.loads(Path(args.cohort).read_text())
    spec = json.loads(Path(args.spec).read_text())
    fragmap = {f["id"]: f for f in spec["fragments"]}
    wt = args.worktree
    era_base = cohort["era_base"]
    boundary = cohort.get("boundary", era_base)
    dirty = git(wt, "status", "--porcelain").strip()
    if dirty:
        print("error: worktree not clean\n" + dirty, file=sys.stderr)
        return 2

    outcome = Path(args.outcome)
    done = set()
    if outcome.exists():
        for r in json.loads(outcome.read_text())["results"]:
            done.add(r["commit"])

    results = (json.loads(outcome.read_text())["results"]
               if outcome.exists() else [])
    queue = [g for g in cohort["groups"] if g["commit"] not in done]
    requeue = []
    for g in queue:
        verdict, detail = run_group(
            g, fragmap, wt, era_base, boundary)
        rec = {"commit": g["commit"], "verdict": verdict,
               "detail": detail}
        if verdict == "gate-failed":
            syms = detail.split("missing=")[-1].split(",")
            prov = provider_in(
                [x for x in queue if x is not g
                 and x["commit"] not in done],
                fragmap, [s for s in syms if s and s != "-"])
            if prov:
                rec["verdict"] = "requeued"
                rec["provider"] = prov
                requeue.append(g)
                print(f"  {g['commit']}: gate-failed -> requeue "
                      f"(provider {prov}): {detail}")
            else:
                print(f"  {g['commit']}: gate-failed -> defer: {detail}")
        else:
            print(f"  {g['commit']}: {verdict} {detail}")
        results.append(rec)
        Path(outcome).write_text(json.dumps(
            {"era_base": era_base, "boundary": boundary,
             "results": results}, indent=1) + "\n")

    for g in requeue:
        verdict, detail = run_group(
            g, fragmap, wt, era_base, boundary)
        results.append({"commit": g["commit"],
                        "verdict": f"retry:{verdict}",
                        "detail": detail})
        print(f"  retry {g['commit']}: {verdict} {detail}")
        Path(outcome).write_text(json.dumps(
            {"era_base": era_base, "boundary": boundary,
             "results": results}, indent=1) + "\n")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--worktree", default=DEFAULT_WT)
    ap.add_argument("--spec", default=str(DEFAULT_SPEC))
    ap.add_argument("--outcome", default="/tmp/backfit-outcome.json")
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("run")
    p.add_argument("cohort")
    args = ap.parse_args()
    if args.cmd == "run":
        return cmd_run(args)
    return 2


if __name__ == "__main__":
    sys.exit(main())
