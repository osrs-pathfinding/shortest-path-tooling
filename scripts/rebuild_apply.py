#!/usr/bin/env python3
"""Apply/replay driver for the segment-attributed history rebuild.

Purpose: apply fragment specs content-anchored onto the rebuilt chain
worktree, replay real upstream commits between segments, gate every
rebuilt commit with ``compileJava compileTestJava``, and keep a
resumable state file + fragment ledger.  The driver only builds local
history — it never moves refs (no push/tag operations exist here).

Mechanism:
  1. A spec fragment's ``minus_runs`` tile into hunks: runs whose
     parent-line ranges are contiguous belong to one ``-U0`` hunk (a
     ``-U0`` hunk deletes every old-side line, so a gap in the ranges is
     a hunk boundary).  The hunk's old side is the concatenation of its
     run texts; its new side is the hunk's ``+`` lines.
  2. Single-hunk fragments take the spec's whole ``plus_text``.
     Multi-hunk fragments re-parse ``git show --format= -U0 <commit> --
     <file>`` and match spec run ranges to diff hunks exactly to recover
     each hunk's share of the flattened ``plus_text`` (the concatenation
     must equal the spec's, else the spec and diff disagree).
  3. Anchored locate: count the verbatim old-side text in the current
     file.  ``1`` → apply (replace with the hunk's new side).  ``>1`` →
     pin the stored ±2 context lines, then extended context read from
     the parent blob; still ambiguous → conflict record, STOP.  ``0`` →
     already-applied check: the text must have been present at the era
     base (the segment's real tag — anchor math guarantees it) and the
     hunk's new side must be satisfied (empty, or present in the file);
     otherwise it is a conflict record, never a fuzzy apply.
  4. Commit per fragment: subject ``<original subject> [<i>/<n>]``; a
     tip commit carrying the provisional ``[<i>/?]`` suffix is amended
     (the tracer fold), never stacked behind a duplicate.
  5. Gate: ``./gradlew compileJava compileTestJava`` in the worktree,
     ``check=False`` so a red gate is inspectable.  On red: companion
     pull-forward when the failures are test-side and the commit's
     companions are unapplied; otherwise the missing-symbol protocol —
     recompile the untouched segment base to rule out dependency drift,
     reorder against a pending same-segment fragment providing the
     symbol, or dependency-bump this fragment to a later segment —
     every outcome recorded in the fragment ledger.
  6. Replay: ``git rev-list --reverse <old>..<new>`` then
     ``git cherry-pick --empty=keep`` per commit (author/date preserved;
     every pick produces a commit, keeping the 1:1 range-diff mapping).
     Conflicts limited to the expected modify/delete set resolve
     keep-deleted via ``git rm``; anything else stops for human
     judgment.  A pick that lands empty is kept and ledgered.
  7. Companions (a commit's non-src/main paths) apply with their
     max-segment fragment as a 3-way patch; a diff already satisfied
     (reverse-check applies) records a skip instead of re-applying.

Caveats:
  - Nothing here force-applies: a 0-match that is not already-applied is
    a ledger/diff/tree divergence — write the conflict record and stop.
  - The driver writes files only inside the named worktree and commits
    only there; the state file and ledger live under .planning/.

Usage:
  python3 rebuild_apply.py apply-fragment <fragment-id> [--worktree P]
  python3 rebuild_apply.py run-segment v1.22.2 [--worktree P]
  python3 rebuild_apply.py replay v1.22.2..v1.22.3 [--worktree P]
  python3 rebuild_apply.py status [--spec P] [--state P]
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
PHASE_DIR = (ROOT / ".planning" / "phases" /
             "shortest-path-tooling-36-attribution-rebuild-ship")
DEFAULT_SPEC = PHASE_DIR / "36-FRAGMENTS.json"
DEFAULT_STATE = PHASE_DIR / "36-APPLY-STATE.json"
DEFAULT_LEDGER = PHASE_DIR / "36-FRAGMENT-LEDGER.md"
DEFAULT_WORKTREE = "/tmp/sp36-rebuild"

# Upstream commits replayed onto a tree where these files were deleted
# produce modify/delete conflicts; keep-deleted is the only sanctioned
# auto-resolution.  The three prefix entries are glob-tolerant.
EXPECTED_MODIFY_DELETE_EXACT = {
    "src/main/java/shortestpath/pathfinder/Pathfinder.java",
    "src/main/java/shortestpath/scheduler/PendingTask.java",
}
EXPECTED_MODIFY_DELETE_PREFIX = (
    "src/main/java/shortestpath/config/PathfinderConfigurationMigration",
    "src/main/java/shortestpath/OrdinalDirection",
    "src/main/java/shortestpath/pathfinder/PathfinderBackend",
)


def git(repo, *args, check=True, input=None):
    try:
        return subprocess.run(
            ["git", "-C", repo, *args], check=check, capture_output=True,
            text=True, input=input).stdout
    except subprocess.CalledProcessError as e:
        raise RuntimeError(
            f"git -C {repo} {' '.join(args)} failed with exit code "
            f"{e.returncode}:\n{(e.stderr or '').strip()}") from e


def gradle(worktree, *tasks):
    """Run ./gradlew with check=False — a red gate is data, not an
    exception.  Returns (combined output, return code)."""
    p = subprocess.run(
        ["./gradlew", *tasks], cwd=worktree, check=False,
        capture_output=True, text=True)
    return (p.stdout or "") + (p.stderr or ""), p.returncode


def _parse_u0(diff_text):
    """Reuse the sibling generator's -U0 hunk parser (importlib — the
    house pattern for sibling-script seam reuse)."""
    spec = importlib.util.spec_from_file_location(
        "rebuild_fragments", ROOT / "scripts" / "rebuild_fragments.py")
    rf = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(rf)
    return rf.parse_show_u0_hunks(diff_text)


# ---------- pure helpers ----------


def parse_range(s):
    """'10-12' -> (10, 12); '40' -> (40, 40)."""
    a, _, b = s.partition("-")
    return int(a), int(b or a)


def cluster_runs(minus_runs):
    """Group spec minus_runs into -U0 hunks: runs whose parent-line
    ranges tile contiguously belong to one hunk (a -U0 hunk deletes
    every old-side line, so a range gap is a hunk boundary)."""
    groups = []
    for run in sorted(minus_runs, key=lambda r: parse_range(r["lines"])):
        start, end = parse_range(run["lines"])
        if groups:
            _, prev_end = parse_range(groups[-1][-1]["lines"])
            if start == prev_end + 1:
                groups[-1].append(run)
                continue
        groups.append([run])
    return groups


def locate_positions(content, needle):
    """Character offsets of every verbatim occurrence."""
    return [m.start() for m in re.finditer(re.escape(needle), content)]


def resolve_match(content, old, before, after):
    """Locate the old-side text.  {'status','count','positions'} —
    found (count 1), absent (0), ambiguous (>1 even context-pinned)."""
    positions = locate_positions(content, old)
    if len(positions) == 1:
        return {"status": "found", "count": 1,
                "positions": positions, "pinned": False}
    if not positions:
        return {"status": "absent", "count": 0, "positions": []}
    pinned = before + old + after
    positions = locate_positions(content, pinned)
    if len(positions) == 1:
        return {"status": "found", "count": 1,
                "positions": positions, "pinned": True}
    return {"status": "ambiguous", "count": len(positions),
            "positions": positions}


def split_plus_per_hunk(fragment, groups, diff_hunks=None):
    """Assign the flattened plus_text back to hunks.

    One hunk → the whole plus_text.  Many → match each group's parent-
    line ranges to a diff hunk's minus-run ranges exactly; the matched
    hunk's plus lines (newline-joined) are its new side.  The union must
    reproduce the spec's plus_text, else the spec and diff disagree."""
    if not fragment["plus_text"].strip():
        return [""] * len(groups)
    if len(groups) == 1:
        return [fragment["plus_text"]]
    if diff_hunks is None:
        raise RuntimeError(
            f"{fragment['id']}: multi-hunk fragment needs the -U0 "
            "diff re-parse for per-hunk plus assignment")
    plus = []
    for group in groups:
        ranges = [parse_range(r["lines"]) for r in group]
        match = [h for h in diff_hunks if h["minus"] == [
            tuple(t) for t in ranges]]
        if len(match) != 1:
            raise RuntimeError(
                f"{fragment['id']}: spec runs {ranges} match "
                f"{len(match)} diff hunks")
        plus.append("".join(l + "\n" for l in match[0]["plus"]))
    if "".join(plus) != fragment["plus_text"]:
        raise RuntimeError(
            f"{fragment['id']}: recovered per-hunk plus disagrees "
            "with the spec's flattened plus_text")
    return plus


def hunk_status(content, old, plus, era_content):
    """pending | already-applied | conflict for one hunk."""
    if old in content:
        return "pending"
    if old in era_content:
        if not plus.strip() or plus in content:
            return "already-applied"
        return "conflict"  # deleted but its replacement never landed
    return "conflict"  # absent at the era base too — spec/divergence


def build_subject(fragment):
    return f"{fragment['subject']} [{fragment['ordinal']}]"


def build_body(fragment):
    return (f"Applied fragment {fragment['ordinal']} of "
            f"{fragment['commit']} ({fragment['subject']}).\n\n"
            f"Attributed segment: {fragment['segment']}.")


def provisional_amend_needed(head_subject, fragment):
    """The tip commit carries the tracer's provisional [<i>/?] suffix —
    fold this fragment's remaining work into it via amend."""
    i = fragment["ordinal"].split("/")[0]
    return head_subject == f"{fragment['subject']} [{i}/?]"


def expected_modify_delete(path):
    if path in EXPECTED_MODIFY_DELETE_EXACT:
        return True
    return any(path.startswith(p)
               for p in EXPECTED_MODIFY_DELETE_PREFIX)


def replay_conflict_verdict(paths):
    """keep-deleted if every conflicted path is in the expected
    modify/delete set, else stop."""
    if paths and all(expected_modify_delete(p) for p in paths):
        return "keep-deleted"
    return "stop"


def make_conflict_record(fragment, file, reason, match_count):
    return {"fragment": fragment, "file": file, "reason": reason,
            "match-count": match_count}


def make_bump_record(fragment, from_seg, to_seg, missing_symbol,
                     compile_line):
    return {"fragment": fragment, "from-segment": from_seg,
            "to-segment": to_seg, "missing-symbol": missing_symbol,
            "compile-line": compile_line}


def make_ledger_row(fragment, commit, file, orig_segment,
                    applied_segment, position, match_counts,
                    disposition):
    matches = ",".join(str(c) for c in match_counts) or "-"
    return (f"| {fragment} | {commit} | {file} | {orig_segment} | "
            f"{applied_segment} | {position} | {matches} | "
            f"{disposition} |")


def load_state(path):
    try:
        return json.loads(Path(path).read_text())
    except (FileNotFoundError, json.JSONDecodeError):
        return {"fragments": {}, "picks": [], "deviations": []}


def save_state(path, state):
    Path(path).write_text(json.dumps(state, indent=2) + "\n")


def gate_failed(rc, out):
    if rc != 0:
        return True
    return bool(re.search(r"error:|FAILURE:", out))


# ---------- apply pipeline ----------


def _diff_hunks_for(repo, commit, path):
    diff = git(repo, "show", "--format=", "-U0", commit, "--", path)
    for f in _parse_u0(diff):
        if f["file"] == path:
            return f["hunks"]
    return []


def _blob(repo, ref, path):
    return git(repo, "show", f"{ref}:{path}")


def _extended_context(blob_lines, first_start, last_end, extra=4):
    """±extra lines beyond the stored ±2 context, read from the parent
    blob (spec 'lines' are parent coordinates)."""
    before = "".join(
        blob_lines[max(0, first_start - 3 - extra):first_start - 3])
    after = "".join(blob_lines[last_end + 2:last_end + 2 + extra])
    return before, after


def _resolve_insertion(content, ins, blob_lines):
    """Locate an insertion's anchor pair in the current file.

    'pending' (anchor pair found exactly once), 'satisfied' (the
    inserted text already sits between the anchors), 'ambiguous'
    (>1 even after widening from the parent blob), 'absent'."""
    before, after, text = ins["before"], ins["after"], ins["text"]
    if before + text + after in content:
        return {"status": "satisfied"}
    positions = locate_positions(content, before + after)
    if len(positions) == 1:
        return {"status": "pending", "before": before, "after": after}
    if len(positions) > 1:
        pos = ins["pos"]
        before2 = "".join(
            blob_lines[max(0, pos - 6):max(0, pos - 2)]) + before
        after2 = after + "".join(blob_lines[pos + 2:pos + 6])
        if before2 + text + after2 in content:
            return {"status": "satisfied"}
        positions = locate_positions(content, before2 + after2)
        if len(positions) == 1:
            return {"status": "pending",
                    "before": before2, "after": after2}
        return {"status": "ambiguous", "count": len(positions)}
    return {"status": "absent"}


def _frag_paths(fragment):
    """Every path the fragment can touch — for undo and staging."""
    paths = [fragment["file"]] + list(fragment.get("companions", []))
    paths += [i["file"] for i in fragment.get("insertions", [])]
    paths += [c["file"] for c in fragment.get("creations", [])]
    return list(dict.fromkeys(paths))


def apply_fragment(fragment, worktree, era_base, repo=None):
    """Apply one fragment to the worktree file.

    Returns {'status': 'applied'|'already-applied'|'conflict',
             'match_counts', 'already', 'conflicts'}.  On 'applied' the
    file edits are written and staged.  Never force-applies."""
    repo = repo or worktree
    path = os.path.join(worktree, fragment["file"])
    content = Path(path).read_text()
    era_content = _blob(repo, era_base, fragment["file"])
    parent_blob_lines = _blob(
        repo, f"{fragment['commit']}^", fragment["file"]
    ).splitlines(keepends=True)

    groups = cluster_runs(fragment["minus_runs"])
    diff_hunks = None
    if len(groups) > 1 and fragment["plus_text"].strip():
        diff_hunks = _diff_hunks_for(
            repo, fragment["commit"], fragment["file"])
    plus = split_plus_per_hunk(fragment, groups, diff_hunks=diff_hunks)

    statuses, match_counts, edits, conflicts = [], [], [], []
    for group, new_text in zip(groups, plus):
        old = "".join(r["text"] for r in group)
        before = group[0]["context_before"]
        after = group[-1]["context_after"]
        res = resolve_match(content, old, before, after)
        if res["status"] == "ambiguous":
            first_start, _ = parse_range(group[0]["lines"])
            _, last_end = parse_range(group[-1]["lines"])
            eb, ea = _extended_context(
                parent_blob_lines, first_start, last_end)
            res2 = resolve_match(content, old, eb + before,
                                 after + ea)
            if res2["status"] == "found":
                res = res2
        if res["status"] == "found":
            statuses.append("pending")
            match_counts.append(1)
            edits.append((old, new_text,
                          before if res["pinned"] else "",
                          after if res["pinned"] else ""))
            continue
        st = hunk_status(content, old, new_text, era_content)
        statuses.append(st)
        match_counts.append(res["count"])
        if st == "conflict":
            conflicts.append(make_conflict_record(
                fragment["id"], fragment["file"],
                "anchor absent and not already-applied"
                if res["status"] == "absent"
                else "anchor ambiguous beyond extended context",
                res["count"]))

    # '+'-only insertions: anchored on the surrounding lines; a missing
    # anchor that is not already satisfied is a conflict, never a guess.
    ins_edits = []  # (file, before, text, after)
    for ins in fragment.get("insertions", []):
        fpath = os.path.join(worktree, ins["file"])
        if ins["file"] == fragment["file"]:
            icontent = content
        else:
            icontent = Path(fpath).read_text() \
                if os.path.exists(fpath) else ""
        iblob = parent_blob_lines if ins["file"] == fragment["file"] \
            else _blob(repo, f"{fragment['commit']}^", ins["file"]) \
            .splitlines(keepends=True)
        res = _resolve_insertion(icontent, ins, iblob)
        match_counts.append({"insertion": res["status"]})
        if res["status"] == "satisfied":
            statuses.append("already-applied")
        elif res["status"] == "pending":
            statuses.append("pending")
            ins_edits.append(
                (ins["file"], res["before"], ins["text"],
                 res["after"]))
        else:
            statuses.append("conflict")
            conflicts.append(make_conflict_record(
                fragment["id"], ins["file"],
                "insertion anchor absent and not satisfied"
                if res["status"] == "absent"
                else "insertion anchor ambiguous beyond context",
                res.get("count", 0)))

    # Creations: a whole new file lands with its carrier fragment.
    cre_writes = []  # (file, content)
    for cre in fragment.get("creations", []):
        fpath = os.path.join(worktree, cre["file"])
        if not os.path.exists(fpath):
            statuses.append("pending")
            cre_writes.append((cre["file"], cre["content"]))
        elif Path(fpath).read_text() == cre["content"]:
            statuses.append("already-applied")
        else:
            statuses.append("conflict")
            conflicts.append(make_conflict_record(
                fragment["id"], cre["file"],
                "creation target exists with different content", 0))

    if conflicts:
        return {"status": "conflict", "conflicts": conflicts,
                "match_counts": match_counts}
    if all(s == "already-applied" for s in statuses):
        return {"status": "already-applied",
                "match_counts": match_counts,
                "already": len(statuses)}
    for old, new_text, before, after in edits:
        if before or after:
            content = content.replace(
                before + old + after, before + new_text + after, 1)
        else:
            content = content.replace(old, new_text, 1)
    Path(path).write_text(content)
    git(worktree, "add", "--", fragment["file"])
    for ifile, before, text, after in ins_edits:
        fpath = os.path.join(worktree, ifile)
        icontent = Path(fpath).read_text()
        Path(fpath).write_text(
            icontent.replace(before + after, before + text + after, 1))
        git(worktree, "add", "--", ifile)
    for cfile, ctext in cre_writes:
        fpath = os.path.join(worktree, cfile)
        os.makedirs(os.path.dirname(fpath), exist_ok=True)
        Path(fpath).write_text(ctext)
        git(worktree, "add", "--", cfile)
    return {"status": "applied", "match_counts": match_counts,
            "already": sum(1 for s in statuses
                           if s == "already-applied")}


def apply_companions(fragment, worktree):
    """Apply the commit's non-src/main paths as 3-way patches.

    Per-path diffs: a modification to a file that does not exist at
    this segment cannot apply and cannot be gate-required — it is
    recorded 'deferred' (the file arrives via upstream replay or rides
    a later segment; the ledger names it).  A change already satisfied
    (reverse-check applies) records a skip.

    Returns 'applied' | 'already-applied' | 'conflict' | 'deferred' —
    'applied'/'deferred' may co-occur; deferred paths are reported on
    fragment['_deferred']."""
    paths = fragment["companions"]
    if not paths:
        return "applied"
    deferred, applied = [], []
    for p in paths:
        diff = git(worktree, "show", "--format=", fragment["commit"],
                   "--", p)
        exists = os.path.exists(os.path.join(worktree, p))
        if not exists and "--- /dev/null" not in diff:
            deferred.append(p)
            continue
        try:
            git(worktree, "apply", "--reverse", "--check", input=diff)
            # satisfied already — stage it so an orphaned (unstaged)
            # companion edit still lands in this fragment's commit
            git(worktree, "add", "--", p)
            applied.append(p)
            continue
        except RuntimeError:
            pass
        try:
            git(worktree, "apply", "--3way", input=diff)
            git(worktree, "add", "--", p)
            applied.append(p)
        except RuntimeError:
            return "conflict"
    if deferred:
        fragment["_deferred"] = deferred
        if not applied:
            return "deferred"
    return "applied" if applied else "already-applied"


def head_subject(worktree):
    return git(worktree, "log", "-1", "--format=%s").strip()


def commit_fragment(fragment, worktree, extra_paths=None):
    """Commit (or amend the provisional tracer tip) the staged work."""
    for p in (extra_paths or []):
        git(worktree, "add", "--", p)
    msg = build_subject(fragment) + "\n\n" + build_body(fragment)
    if provisional_amend_needed(head_subject(worktree), fragment):
        git(worktree, "commit", "--amend", "-m", msg)
        return "amended"
    git(worktree, "commit", "-m", msg)
    return "committed"


def _undo_commit(worktree, paths):
    """Back out the tip fragment commit and its file edits — used only
    by the reorder/bump resolutions (never on an amend).  Reverts every
    path the fragment touched: main file plus any companion paths that
    were applied or staged alongside it (a created companion file is
    removed, a modified one reverted)."""
    git(worktree, "reset", "--soft", "HEAD^")
    git(worktree, "reset")
    for p in paths:
        try:
            git(worktree, "checkout", "--", p)
        except RuntimeError:
            pass
        if git(worktree, "status", "--porcelain", "--", p).strip():
            try:
                git(worktree, "rm", "-f", "--", p)
            except RuntimeError:
                full = os.path.join(worktree, p)
                if os.path.exists(full):
                    os.remove(full)


def _missing_symbols(out):
    syms = re.findall(r"symbol:\s+(?:class|method|variable|interface|"
                      r"constructor)\s+(\w+)", out)
    syms += re.findall(r"cannot find symbol\s+(\w+)", out)
    return list(dict.fromkeys(syms))


def _error_paths(out):
    return [l.split(":")[0].strip() for l in out.splitlines()
            if re.search(r"error:|\.java:\d+:", l)]


def _drift_check(worktree, base, segment):
    """Recompile the untouched segment base in a temp worktree to rule
    out latest.release dependency drift."""
    tmp = f"/tmp/sp36-drift-{segment}"
    try:
        git(worktree, "worktree", "add", "--detach", tmp, base)
        out, rc = gradle(tmp, "compileJava", "compileTestJava")
        return rc != 0 or gate_failed(rc, out), out
    finally:
        git(worktree, "worktree", "remove", "--force", tmp)


def _search_text(f):
    """Everything a fragment can deposit — its '+' replacements plus
    carried insertions and creations — for symbol-provider lookups."""
    parts = [f.get("plus_text", "")]
    parts += [i.get("text", "") for i in f.get("insertions", [])]
    parts += [c.get("content", "") for c in f.get("creations", [])]
    return "\n".join(parts)


def _eff_segment(f, state):
    """The segment a fragment currently belongs to — its bump target
    when bumped, else its spec segment."""
    st = state["fragments"].get(f["id"], {}) if state else {}
    if st.get("status") == "bumped" and st.get("to"):
        return st["to"]
    return f["segment"]


def _symbol_provider(fragments, symbol, exclude_id, segment=None,
                     state=None):
    for f in fragments:
        if f["id"] == exclude_id:
            continue
        if _eff_segment(f, state) != segment:
            continue
        if symbol in _search_text(f):
            return f
    return None


# ---------- ledger ----------


def _ledger_append(ledger_path, rows):
    path = Path(ledger_path)
    if not path.exists():
        path.write_text(
            "# Fragment Apply Ledger\n\n"
            "| fragment | source | file | segment | applied-at | "
            "position | match | disposition |\n"
            "|----------|--------|------|---------|------------|"
            "----------|-------|-------------|\n")
    with open(path, "a") as fh:
        for row in rows:
            fh.write(row + "\n")


# ---------- subcommands ----------


def _load_spec(spec_path):
    return json.loads(Path(spec_path).read_text())


def cmd_apply_fragment(args):
    spec = _load_spec(args.spec)
    frag = next((f for f in spec["fragments"] if f["id"] == args.id),
                None)
    if frag is None:
        print(f"error: fragment {args.id} not in spec", file=sys.stderr)
        return 2
    era_base = args.era_base or frag["segment"]
    res = apply_fragment(frag, args.worktree, era_base)
    print(json.dumps(res, indent=2))
    return 0 if res["status"] in ("applied", "already-applied") else 1


def cmd_run_segment(args):
    spec = _load_spec(args.spec)
    state = load_state(args.state)
    wt = args.worktree
    segment = args.segment

    dirty = git(wt, "status", "--porcelain").strip()
    if dirty:
        print("error: worktree not clean\n" + dirty, file=sys.stderr)
        return 2
    base = git(wt, "rev-parse", "HEAD").strip()
    era_base = args.era_base or segment

    scoped = []
    for f in spec["fragments"]:
        st = state["fragments"].get(f["id"], {})
        status = st.get("status")
        if (f["segment"] == segment and status
                not in ("applied", "already-applied", "bumped")):
            scoped.append(f)
        elif status == "bumped" and st.get("to") == segment:
            # bumped fragments re-judge at their target segment —
            # post-replay content or a landed provider may now
            # satisfy them; otherwise they bump again.
            scoped.append(f)
    total = len(scoped)
    pending = {f["id"]: f for f in scoped}
    print(f"run-segment {segment}: {total} in scope "
          f"({len(pending)} pending), base {base[:8]}")

    rows = []
    for idx, frag in enumerate(scoped, 1):
        res = apply_fragment(frag, wt, frag["segment"])
        if res["status"] == "conflict":
            for c in res["conflicts"]:
                rows.append(make_ledger_row(
                    frag["id"], frag["commit"], frag["file"],
                    frag["segment"], "-", idx, res["match_counts"],
                    f"conflict: {c['reason']}"))
            _ledger_append(args.ledger, rows)
            save_state(args.state, state)
            print(f"CONFLICT at {frag['id']}: "
                  f"{res['conflicts']}", file=sys.stderr)
            return 1
        if res["status"] == "already-applied":
            rows.append(make_ledger_row(
                frag["id"], frag["commit"], frag["file"],
                frag["segment"], segment, idx,
                res["match_counts"], "already-applied"))
            state["fragments"][frag["id"]] = {
                "status": "already-applied"}
            continue

        # companions land with this fragment (max-segment carrier)
        comp = apply_companions(frag, wt)
        if frag.get("_deferred"):
            dev = {"kind": "companion-deferred",
                   "fragment": frag["id"],
                   "paths": frag.get("_deferred", []),
                   "reason": "companion file absent at segment"}
            state["deviations"].append(dev)
            rows.append(make_ledger_row(
                frag["id"], frag["commit"], frag["file"],
                frag["segment"], segment, idx,
                res["match_counts"],
                "deviation: companion-deferred "
                f"({', '.join(frag.get('_deferred', []))})"))
        if comp == "conflict":
            rows.append(make_ledger_row(
                frag["id"], frag["commit"], frag["file"],
                frag["segment"], "-", idx, res["match_counts"],
                "conflict: companion apply failed"))
            _ledger_append(args.ledger, rows)
            save_state(args.state, state)
            print(f"CONFLICT (companions) at {frag['id']}",
                  file=sys.stderr)
            return 1
        amended = commit_fragment(frag, wt)
        new_head = git(wt, "rev-parse", "--short", "HEAD").strip()

        out, rc = gradle(wt, "compileJava", "compileTestJava")
        if gate_failed(rc, out):
            err_paths = _error_paths(out)
            test_only = err_paths and all(
                p.startswith("src/test/") for p in err_paths)
            fixed = False
            if test_only:
                # companions needed earlier than their max-segment
                # assignment — pull them forward, record the deviation
                comp2 = apply_companions(frag, wt)
                if comp2 == "applied":
                    commit_fragment(frag, wt)
                    out, rc = gradle(
                        wt, "compileJava", "compileTestJava")
                    if not gate_failed(rc, out):
                        fixed = True
                        dev = {"kind": "companion-pull-forward",
                               "fragment": frag["id"],
                               "reason": "test-side compile errors"}
                        state["deviations"].append(dev)
                        rows.append(make_ledger_row(
                            frag["id"], frag["commit"], frag["file"],
                            frag["segment"], segment, idx,
                            res["match_counts"],
                            "deviation: companion-pull-forward"))
            if not fixed:
                # Pitfall 6: a green gate minutes ago says nothing
                # about a red one now — recompile the untouched
                # SEGMENT BASE (the real tag), not HEAD, so a broken
                # chain commit can't masquerade as dependency drift.
                drift, _ = _drift_check(wt, era_base, segment)
                if drift:
                    rows.append(make_ledger_row(
                        frag["id"], frag["commit"], frag["file"],
                        frag["segment"], "-", idx,
                        res["match_counts"],
                        "conflict: latest.release drift at "
                        "segment base"))
                    _ledger_append(args.ledger, rows)
                    save_state(args.state, state)
                    print(f"GATE FAILURE at {frag['id']} "
                          "(latest.release drift at segment base)",
                          file=sys.stderr)
                    return 1
                syms = _missing_symbols(out)
                failed = {i for i, s in state["fragments"].items()
                          if s.get("status") == "gate-failed"}
                provider = next(
                    (f for s in syms
                     for f in [_symbol_provider(
                         [p for p in pending.values()
                          if p["id"] not in failed],
                         s, frag["id"], segment, state=state)]
                     if f), None)
                undone = False
                if provider is not None and amended != "amended":
                    _undo_commit(wt, _frag_paths(frag))
                    undone = True
                    rows.append(make_ledger_row(
                        frag["id"], frag["commit"], frag["file"],
                        frag["segment"], segment, idx,
                        res["match_counts"],
                        f"reorder: needs {provider['id']} first"))
                    state["deviations"].append({
                        "kind": "reorder", "fragment": frag["id"],
                        "first": provider["id"],
                        "compile-line": next(
                            (l for l in out.splitlines()
                             if "error:" in l), "")})
                    save_state(args.state, state)
                    prov_res = _apply_commit_gate(
                        provider, wt, provider["segment"], args,
                        state, rows)
                    if prov_res == 0:
                        pending.pop(provider["id"], None)
                        _ledger_append(args.ledger, rows)
                        return cmd_run_segment(args)
                    # provider can't compile here either — it is
                    # gate-failed, pending its own bump resolution;
                    # this fragment falls through to the bump path
                    state["fragments"][provider["id"]] = {
                        "status": "gate-failed"}
                    save_state(args.state, state)
                target_seg = None
                for s in syms:
                    for seg in _later_segments(segment):
                        hit = _symbol_provider(
                            spec["fragments"], s, frag["id"], seg,
                            state=state)
                        if hit:
                            target_seg = seg
                            break
                    if target_seg:
                        break
                if target_seg is None:
                    # Nothing resolves it at this segment — missing
                    # symbol, signature/caller split across segments,
                    # or +only dependency.  Defer one segment and let
                    # the next run re-judge (replayed upstream content
                    # or a landed provider may satisfy it; otherwise
                    # it bumps again toward the post lane).
                    later = _later_segments(segment)
                    if later:
                        target_seg = later[0]
                if target_seg is not None:
                    if not undone and amended != "amended":
                        _undo_commit(wt, _frag_paths(frag))
                    bump = make_bump_record(
                        frag["id"], segment, target_seg,
                        syms[0] if syms else "?",
                        next((l for l in out.splitlines()
                              if "error:" in l), ""))
                    state["fragments"][frag["id"]] = {
                        "status": "bumped", "to": target_seg,
                        "record": bump}
                    rows.append(make_ledger_row(
                        frag["id"], frag["commit"], frag["file"],
                        frag["segment"], target_seg, idx,
                        res["match_counts"],
                        f"bumped to {target_seg}: "
                        f"missing {bump['missing-symbol']}"))
                    continue
                reason = "unresolvable compile failure"
                rows.append(make_ledger_row(
                    frag["id"], frag["commit"], frag["file"],
                    frag["segment"], "-", idx, res["match_counts"],
                    f"conflict: {reason}"))
                _ledger_append(args.ledger, rows)
                save_state(args.state, state)
                print(f"GATE FAILURE at {frag['id']} ({reason})",
                      file=sys.stderr)
                print("\n".join(out.splitlines()[-25:]),
                      file=sys.stderr)
                return 1

        rows.append(make_ledger_row(
            frag["id"], frag["commit"], frag["file"],
            frag["segment"], segment, idx,
            res["match_counts"],
            "applied" if amended != "amended"
            else "applied (tracer fold, amended)"))
        state["fragments"][frag["id"]] = {
            "status": "applied", "commit": new_head}
        save_state(args.state, state)
        print(f"  [{idx}/{total}] {frag['id']} applied ({new_head})")

    _ledger_append(args.ledger, rows)
    save_state(args.state, state)
    print(f"run-segment {segment}: done, "
          f"{len([r for r in rows if ' applied |' in r or 'applied' in r])}"
          " ledger rows")
    return 0


def _later_segments(segment):
    order = ["v1.22.2", "v1.22.3", "v1.22.4", "v1.22.5", "post-v1.22.5"]
    return order[order.index(segment) + 1:]


def _apply_commit_gate(frag, wt, era_base, args, state, rows):
    """One apply→commit→gate cycle for a reorder-providing fragment."""
    res = apply_fragment(frag, wt, era_base)
    if res["status"] == "conflict":
        rows.append(make_ledger_row(
            frag["id"], frag["commit"], frag["file"],
            frag["segment"], "-", 0, res["match_counts"],
            "conflict: anchor during reorder"))
        return 1
    if res["status"] == "already-applied":
        state["fragments"][frag["id"]] = {
            "status": "already-applied"}
        rows.append(make_ledger_row(
            frag["id"], frag["commit"], frag["file"],
            frag["segment"], frag["segment"], 0,
            res["match_counts"], "already-applied"))
        return 0
    if apply_companions(frag, wt) == "conflict":
        return 1
    commit_fragment(frag, wt)
    out, rc = gradle(wt, "compileJava", "compileTestJava")
    if gate_failed(rc, out):
        _undo_commit(wt, _frag_paths(frag))
        return 1
    state["fragments"][frag["id"]] = {
        "status": "applied",
        "commit": git(wt, "rev-parse", "--short", "HEAD").strip()}
    save_state(args.state, state)
    rows.append(make_ledger_row(
        frag["id"], frag["commit"], frag["file"],
        frag["segment"], frag["segment"], 0,
        res["match_counts"], "applied (reorder provider)"))
    return 0


def cmd_replay(args):
    wt = args.worktree
    state = load_state(args.state)
    shas = [l for l in git(
        wt, "rev-list", "--reverse", args.range).splitlines() if l]
    done = set(state["picks"])
    rows = []
    for idx, sha in enumerate(shas, 1):
        if sha in done:
            continue
        try:
            git(wt, "cherry-pick", "--empty=keep", sha)
        except RuntimeError:
            conflicted = git(
                wt, "diff", "--name-only", "--diff-filter=U"
            ).split()
            verdict = replay_conflict_verdict(conflicted)
            if verdict == "keep-deleted":
                for p in conflicted:
                    git(wt, "rm", "--", p)
                git(wt, "cherry-pick", "--continue")
                rows.append(make_ledger_row(
                    f"pick-{sha[:8]}", sha[:8], " ".join(conflicted),
                    "-", "-", idx, [],
                    "conflict-resolved: keep-deleted"))
            else:
                rows.append(make_ledger_row(
                    f"pick-{sha[:8]}", sha[:8],
                    " ".join(conflicted), "-", "-", idx, [],
                    "conflict: human judgment required"))
                _ledger_append(args.ledger, rows)
                save_state(args.state, state)
                print(f"CONFLICT pick {sha[:8]}: {conflicted}",
                      file=sys.stderr)
                return 1
        new_head = git(wt, "rev-parse", "--short", "HEAD").strip()
        if not git(wt, "diff", "--stat", "HEAD^", "HEAD").strip():
            rows.append(make_ledger_row(
                f"pick-{sha[:8]}", sha[:8], "-", "-", "-", idx, [],
                "empty-pick: kept"))
        else:
            rows.append(make_ledger_row(
                f"pick-{sha[:8]}", sha[:8], "-", "-", "-", idx, [],
                "picked"))
        out, rc = gradle(wt, "compileJava", "compileTestJava")
        if gate_failed(rc, out):
            _ledger_append(args.ledger, rows)
            save_state(args.state, state)
            print(f"GATE FAILURE at pick {sha[:8]} ({new_head})",
                  file=sys.stderr)
            print("\n".join(out.splitlines()[-25:]), file=sys.stderr)
            return 1
        state["picks"].append(sha)
        save_state(args.state, state)
        print(f"  [{idx}/{len(shas)}] picked {sha[:8]} ({new_head})")
    _ledger_append(args.ledger, rows)
    save_state(args.state, state)
    print(f"replay {args.range}: {len(shas)} commits")
    return 0


def cmd_status(args):
    state = load_state(args.state)
    spec = _load_spec(args.spec)
    by_seg = {}
    for f in spec["fragments"]:
        by_seg.setdefault(f["segment"], []).append(f["id"])
    applied = [i for i, s in state["fragments"].items()
               if s.get("status") in ("applied", "already-applied")]
    bumped = [i for i, s in state["fragments"].items()
              if s.get("status") == "bumped"]
    print(f"state: {args.state}")
    for seg, ids in by_seg.items():
        done = [i for i in ids if i in applied]
        print(f"  {seg}: {len(done)}/{len(ids)} applied")
    print(f"applied fragments: {len(applied)}; bumped: {len(bumped)}; "
          f"picks: {len(state['picks'])}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--worktree", default=DEFAULT_WORKTREE)
    ap.add_argument("--spec", default=str(DEFAULT_SPEC))
    ap.add_argument("--state", default=str(DEFAULT_STATE))
    ap.add_argument("--ledger", default=str(DEFAULT_LEDGER))
    ap.add_argument("--era-base", default=None)
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("apply-fragment")
    p.add_argument("id")
    p = sub.add_parser("run-segment")
    p.add_argument("segment")
    p = sub.add_parser("replay")
    p.add_argument("range")
    sub.add_parser("status")
    args = ap.parse_args()

    if args.cmd == "apply-fragment":
        return cmd_apply_fragment(args)
    if args.cmd == "run-segment":
        return cmd_run_segment(args)
    if args.cmd == "replay":
        return cmd_replay(args)
    if args.cmd == "status":
        return cmd_status(args)
    return 2


if __name__ == "__main__":
    sys.exit(main())
