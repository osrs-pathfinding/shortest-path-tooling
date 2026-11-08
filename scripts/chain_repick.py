#!/usr/bin/env python3
"""Replay a release segment one commit at a time and check the result.

Rebuilding a release line on a changed base means replaying every commit of
the old line.  The replay must be mechanical and reproducible, so this tool
does it with the git sequencer, resolves the one kind of conflict a mechanical
import change causes, and proves afterwards that nothing was lost or added.

Subcommands

  pick --worktree WT --range A..B --log FILE [--universe FILE]
  pick --worktree WT --log FILE --continue --verdict TEXT
      Cherry-pick the commits of A..B (oldest first, no merges) onto the
      worktree's HEAD: one new commit per old commit, author, author date and
      message kept, no "cherry picked from" trailer, and a pick that turns
      empty is kept as an empty commit.  Whitespace at line ends is ignored
      when merging.  A conflict is resolved by itself only when every conflict
      block of the file holds nothing but import statements and blank lines on
      every side and lies inside the file's import section (between the
      package statement and the first declaration): the import section of the
      file is then replaced by the import section of the commit being picked,
      git's merged text stays everywhere else, and a src/main/java file is
      re-canonicalised by the wildcard rewriter.  Any other conflict leaves
      the worktree in the middle of the pick, logs the stop and exits 3
      ("hand resolution needed"); after resolving and staging it by hand,
      `--continue --verdict TEXT` records the verdict and resumes.  The log
      lists every pick: old SHA, new SHA, subject and the conflict, if any.

  map --repo R --log FILE --old A..B --new C..D [--inserted SHA[,SHA]]
      [--fix-patch FILE] --out FILE
      Prove the replay is one to one.  The old SHAs of the log(s) must equal
      A..B and the new SHAs plus the inserted commits must equal C..D; each
      pair must have the same subject and author.  A pair is "identical" when
      the two patches have the same stable patch id, "import-or-whitespace"
      when the two patches are equal after dropping import statement lines
      and whitespace, "conflict-recorded" when the log holds a conflict for
      it, otherwise "unexplained" (a hard failure).  The range-diff of the two
      ranges is stored.  Import-only pairing: a commit whose patch only adds
      or removes import lines can replay to a commit that range-diff does not
      pair with the original (on a wildcard tree the replayed diff may be
      empty or touch other import lines); range-diff then shows the original
      as an only-old row and the replay as an only-new row.  Such two rows are
      accepted only when all three hold: the log joins the two commits; the
      original's own patch touches nothing but import lines and whitespace;
      and the replayed commit's tree equals the original's tree plus the fix
      patch (the original's tree as is without one) in every .java file apart
      from import lines and whitespace, and in every other file exactly.
      Every other only-old row, and any surplus of only-new rows over the
      inserted commits, is a failure.

  gate --repo R --worktree WT --tag T --old SHA --new SHA --prev-old SHA
       --prev-new SHA --inserted SHA[,SHA] --fix-patch FILE
       --classpath-file FILE --tags-before FILE --out DIR [--log FILE ...]
      Check every gate of one rebuilt release tag in a single run and write
      DIR/gate.json.  WT is a clean worktree at NEW.
        G1 suite       compileTestJava, test and both checkstyle tasks pass;
                       the resolved RuneLite client version and the number of
                       tests run (above zero) are recorded.
        G2 hygiene     no change to the Gradle files or outside .java files
                       between OLD and NEW; every commit of PREV-NEW..NEW is a
                       logged pick, an inserted commit or the tip as the
                       re-picked marker (empty, OLD's subject and author);
                       inserted messages carry no attribution trailer and no
                       internal planning label; an inserted commit that is
                       neither the cleanup nor the wildcard commit and touches
                       ClientPlayerStateSource.java is the compile fix: its
                       patch id equals the fix patch's and its author is rsbfox.
        G3 cleanup     the inserted `style:` commit passes
                       `style_cleanup.py check-diff --imports-subset`.
        G4 wildcard    NEW is canonical for the wildcard rewriter; the inserted
                       `refactor: use wildcard imports` commit is exactly the
                       rewriter's output on its parent and compiles to the same
                       class files as its parent.
        G5 equivalence OLD plus the fix patch and NEW compile to the same class
                       files and differ in no .java file (tests included)
                       beyond import lines and whitespace.
        G6 tokens      cl100k token totals of OLD, PREV-NEW (when it differs from
                       PREV-OLD), the last pick, every inserted commit and NEW,
                       each keyed by the SHA it was measured on; NEW must be
                       below OLD.
        G7 map         with logs: the one to one map of PREV-OLD..OLD against
                       PREV-NEW..NEW has no error; without logs the old range is
                       the marker alone and PREV-NEW..NEW is exactly the
                       inserted commits plus the re-picked marker.
        G8 remote      the four upstream release tags are as listed in the
                       tags-before file and no rebuild/ branch exists on
                       upstream or origin.
      Exit 0 all gates pass, 1 a gate failed, 2 unusable input (WT dirty or
      not at NEW, a missing file).

  patched-tree --repo R --treeish T --patch FILE
      Print the tree SHA of T with the patch applied (exit 2 when it does not
      apply); no working tree is touched.

Exit codes: 0 ok, 1 a check failed, 2 unusable input, 3 (pick) a conflict
needs a hand resolution.
"""
from __future__ import annotations

import argparse
import contextlib
import importlib.util
import io
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent


def _load(name):
    """Import a sibling script (scripts/ has no __init__.py); a module already
    imported under that name is shared, so exception classes stay the same."""
    if name in sys.modules:
        return sys.modules[name]
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


sc = _load("style_cleanup")
wi = _load("wildcard_imports")
bi = _load("bytecode_identity")

ROOT = SCRIPTS.parent

SRC_MAIN = "src/main/java/"
PICK_FLAGS = ("-X", "ignore-space-at-eol", "--allow-empty", "--keep-redundant-commits")
FIELD, RECORD = "\x1f", "\x1e"


class UnusableInput(Exception):
    """Input that cannot be processed safely; mapped to exit code 2."""


# ---------- seams ----------

def run(argv, cwd=None, env=None, input=None):
    """Run a command given as an argument list; stdout and stderr are bytes."""
    return subprocess.run(argv, check=False, capture_output=True, cwd=cwd, env=env,
                          input=input)


def _text(data):
    if isinstance(data, bytes):
        return data.decode("utf-8", errors="surrogateescape")
    return data or ""


def git_rc(repo, *args, input=None, env=None):
    """(returncode, stdout, stderr) of `git -C repo args...`; no exception."""
    if isinstance(input, str):
        input = input.encode("utf-8", errors="surrogateescape")
    full_env = dict(os.environ, GIT_EDITOR="true", **(env or {}))
    p = run(["git", "-C", str(repo), *args], env=full_env, input=input)
    return p.returncode, _text(p.stdout), _text(p.stderr)


def git(repo, *args, input=None, env=None):
    """stdout of `git -C repo args...`; a failing git is unusable input."""
    rc, out, err = git_rc(repo, *args, input=input, env=env)
    if rc != 0:
        raise UnusableInput(f"git -C {repo} {' '.join(args)} failed with exit code "
                            f"{rc}:\n{err.strip()}")
    return out


def rev_list(repo, rng, no_merges=False):
    args = ["rev-list", "--reverse"] + (["--no-merges"] if no_merges else [])
    return git(repo, *args, rng).split()


def commit_info(repo, shas):
    """Author, author date, subject and message of each sha, in the given order."""
    if not shas:
        return []
    fmt = FIELD.join(["%H", "%an", "%ae", "%aI", "%s", "%B"]) + RECORD
    out = git(repo, "log", "--no-walk=unsorted", f"--format={fmt}", *shas)
    infos = []
    for rec in out.split(RECORD):
        if not rec.strip():
            continue
        sha, name, email, date, subject, body = rec.lstrip("\n").split(FIELD, 5)
        infos.append({"sha": sha, "author": f"{name} <{email}>", "date": date,
                      "subject": subject, "message": body.strip()})
    return infos


def patched_tree(repo, treeish, patch):
    """Tree SHA of `treeish` with `patch` applied through a temporary index."""
    try:
        return sc.patched_tree(repo, treeish, patch)
    except sc.UnusableInput as e:
        raise UnusableInput(str(e)) from e


def check_diff(repo, base, head, imports_subset=False, base_patch=None):
    try:
        return sc.check_diff(repo, base, head, imports_subset, base_patch)
    except sc.UnusableInput as e:
        raise UnusableInput(str(e)) from e


# ---------- conflicts ----------

_MARKER = re.compile(r"^(?:<{7}|>{7}|\|{7})(?: .*)?$|^={7}$")
_PACKAGE = re.compile(r"^\s*package\s+[A-Za-z_$][\w$]*(?:\s*\.\s*[A-Za-z_$][\w$]*)*\s*;")


class MalformedConflict(Exception):
    pass


def conflict_blocks(text):
    """The conflict blocks of a merged file: dicts with the 0-based `start` and
    `end` line indexes (of the opening and closing marker) and the lines of
    each side: `ours`, `base` (None without a ||||||| part) and `theirs`."""
    lines = text.split("\n")
    blocks, current, side = [], None, None
    for number, raw in enumerate(lines):
        line = raw.rstrip("\r")
        if line.startswith("<<<<<<<") and (len(line) == 7 or line[7] == " "):
            if current is not None:
                raise MalformedConflict(f"line {number + 1}: a conflict block inside one")
            current = {"start": number, "ours": [], "base": None, "theirs": []}
            side = "ours"
        elif current is not None and line.startswith("|||||||") and (
                len(line) == 7 or line[7] == " "):
            current["base"], side = [], "base"
        elif current is not None and line == "=======":
            side = "theirs"
        elif current is not None and line.startswith(">>>>>>>") and (
                len(line) == 7 or line[7] == " "):
            current["end"] = number
            blocks.append(current)
            current, side = None, None
        elif current is not None:
            current[side].append(line)
    if current is not None:
        raise MalformedConflict("a conflict block is never closed")
    return blocks


def import_section(lines):
    """(start, end): the half-open range of line indexes between the package
    statement and the first declaration.  Blank lines, import statements, `//`
    comments, `/* */` comments that are not Javadoc and conflict markers belong
    to it; the first other line (a Javadoc comment, an annotation, a class)
    ends it."""
    n, start, in_block = len(lines), 0, False
    for i, raw in enumerate(lines):
        s = raw.strip()
        if in_block:
            in_block = "*/" not in s
        elif s.startswith("/*"):
            in_block = "*/" not in s[2:]
        elif _PACKAGE.match(raw):
            start = i + 1
            break
        elif s and not s.startswith("//"):
            break
    end, in_block = start, False
    while end < n:
        raw = lines[end]
        s = raw.strip()
        if in_block:
            in_block = "*/" not in s
        elif (not s or s.startswith("//") or _MARKER.match(s)
              or sc.IMPORT_STATEMENT.match(raw)):
            pass
        elif s.startswith("/*") and not s.startswith("/**"):
            in_block = "*/" not in s[2:]
        else:
            break
        end += 1
    return start, end


def hand_reason(text):
    """None when every conflict block of `text` holds only import statements
    and blank lines on every side and lies inside the import section; else the
    reason a person has to resolve the file."""
    try:
        blocks = conflict_blocks(text)
    except MalformedConflict as e:
        return f"malformed conflict: {e}"
    if not blocks:
        return "no conflict block in the file"
    start, end = import_section(text.split("\n"))
    for block in blocks:
        sides = [block["ours"], block["theirs"]] + ([block["base"]] if block["base"] is not None
                                                    else [])
        for lines in sides:
            for line in lines:
                if line.strip() and not sc.IMPORT_STATEMENT.match(line):
                    return f"line {block['start'] + 1}: a conflict block holds code: {line.strip()}"
        if not (start <= block["start"] and block["end"] < end):
            return f"line {block['start'] + 1}: a conflict block outside the import section"
    return None


def resolve_import_conflict(merged, theirs):
    """`merged` with its import section replaced by the one of `theirs` (the
    version of the commit being picked); the text outside it is git's."""
    m_lines, t_lines = merged.split("\n"), theirs.split("\n")
    m_start, m_end = import_section(m_lines)
    t_start, t_end = import_section(t_lines)
    out = "\n".join(m_lines[:m_start] + t_lines[t_start:t_end] + m_lines[m_end:])
    if any(_MARKER.match(ln.rstrip("\r")) for ln in out.split("\n")):
        raise UnusableInput("a conflict marker is left after resolving the import section")
    return out


def canonical_texts(tree_texts, resolved, universe):
    """{path: canonical text} for the resolved src/main/java files, rewritten by
    the wildcard rewriter against the tree (with the resolved texts in place);
    other resolved files keep their text."""
    texts = dict(tree_texts)
    texts.update(resolved)
    env = wi.make_env(universe, {p: t for p, t in texts.items() if p.startswith(SRC_MAIN)})
    out = {}
    for path, text in resolved.items():
        out[path] = wi.rewrite_text(text, path, env).new if path.startswith(SRC_MAIN) else text
    return out


# ---------- the sequencer loop ----------

def unmerged_stages(wt):
    """{path: set of stages} of the unmerged index entries."""
    out = git(wt, "ls-files", "-u", "-z")
    stages = {}
    for entry in filter(None, out.split("\0")):
        meta, _, path = entry.partition("\t")
        stages.setdefault(path, set()).add(int(meta.split()[2]))
    return stages


def read_stop(wt):
    """(sha being picked, unmerged paths) while a pick is stopped, else None."""
    rc, out, _ = git_rc(wt, "rev-parse", "-q", "--verify", "CHERRY_PICK_HEAD")
    if rc != 0:
        return None
    return out.strip(), sorted(unmerged_stages(wt))


def try_resolve(wt, paths, stages, universe):
    """Resolve every path as an import-section conflict and stage it; returns
    the resolution text, or None (nothing touched) when one needs a person."""
    merged = {}
    for path in paths:
        if stages.get(path) != {1, 2, 3} or not path.endswith(".java"):
            return None
        try:
            text = (Path(wt) / path).read_bytes().decode("utf-8")
        except (OSError, UnicodeDecodeError):
            return None
        if hand_reason(text) is not None:
            return None
        merged[path] = text
    resolved = {}
    for path, text in merged.items():
        theirs = git(wt, "show", f":3:{path}")
        resolved[path] = resolve_import_conflict(text, theirs)
    tree = wi.DiskTree(wt)
    tree_texts = {p: tree.read(p) for p in tree.paths() if p not in merged}
    try:
        final = canonical_texts(tree_texts, resolved, universe)
    except wi.UnusableInput as e:
        raise UnusableInput(str(e)) from e
    for path, text in final.items():
        (Path(wt) / path).write_bytes(text.encode("utf-8"))
    git(wt, "add", "--", *sorted(final))
    return ("import section of the picked commit; "
            "src/main/java files canonicalised by the wildcard rewriter")


def load_log(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        raise UnusableInput(f"cannot read the pick log {path}: {e}") from e


def save_log(path, state):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")


def finish(wt, state):
    """Pair the old commits with the new ones and verify author and message."""
    olds = state["old"]
    news = rev_list(wt, f"{state['onto']}..HEAD")
    if len(news) != len(olds):
        raise UnusableInput(f"{len(olds)} commits picked but {len(news)} new commits "
                            f"follow {state['onto']}")
    old_info, new_info = commit_info(wt, olds), commit_info(wt, news)
    picks = []
    for o, n in zip(old_info, new_info):
        for key in ("author", "date", "message"):
            if o[key] != n[key]:
                raise UnusableInput(f"{o['sha']} -> {n['sha']}: the {key} was not kept")
        picks.append({"old": o["sha"], "new": n["sha"], "subject": o["subject"],
                      "conflict": state["conflicts"].get(o["sha"])})
    state["picks"] = picks
    state["status"] = "complete"
    state["stopped"] = None
    state.pop("conflicts", None)
    state.pop("old", None)


def drive(wt, state, log, universe, argv):
    rc, _, err = git_rc(wt, *argv)
    while rc != 0:
        stop = read_stop(wt)
        if stop is None:
            raise UnusableInput(f"git {' '.join(argv)} failed:\n{err.strip()}")
        old, paths = stop
        if not paths:
            raise UnusableInput(f"the pick of {old} stopped without a conflict:\n{err.strip()}")
        stages = unmerged_stages(wt)
        resolution = try_resolve(wt, paths, stages, universe)
        if resolution is None:
            state["status"] = "stopped"
            state["stopped"] = {"old": old, "files": paths}
            save_log(log, state)
            print(f"hand resolution needed: {old} {' '.join(paths)}")
            return 3
        state["conflicts"][old] = {"files": paths, "class": "import-section",
                                   "resolution": resolution}
        rc, _, err = git_rc(wt, "-c", "core.editor=true", "cherry-pick", "--continue")
    finish(wt, state)
    save_log(log, state)
    resolved = sum(1 for p in state["picks"] if p["conflict"])
    print(f"picked {len(state['picks'])} commits onto {state['onto']}; "
          f"{resolved} with a recorded conflict")
    return 0


def require_clean(wt):
    if git(wt, "status", "--porcelain").strip():
        raise UnusableInput(f"{wt} is not clean")


def cmd_pick(args):
    wt = Path(args.worktree)
    universe = wi.load_universe(args.universe or wi.DEFAULT_UNIVERSE)
    if args.continue_:
        if args.verdict is None:
            raise UnusableInput("--continue needs --verdict TEXT")
        state = load_log(args.log)
        stopped = state.get("stopped")
        if state.get("status") != "stopped" or not stopped:
            raise UnusableInput(f"{args.log} holds no stopped pick")
        stop = read_stop(wt)
        if stop is None or stop[0] != stopped["old"]:
            raise UnusableInput(f"the worktree is not stopped at {stopped['old']}")
        if stop[1]:
            raise UnusableInput("unmerged paths remain: " + " ".join(stop[1]))
        state["conflicts"][stopped["old"]] = {"files": stopped["files"], "class": "hand",
                                              "resolution": args.verdict}
        return drive(wt, state, args.log, universe,
                     ["-c", "core.editor=true", "cherry-pick", "--continue"])
    if not args.range:
        raise UnusableInput("--range A..B is required")
    require_clean(wt)
    shas = rev_list(wt, args.range, no_merges=True)
    if shas != rev_list(wt, args.range):
        raise UnusableInput(f"{args.range} contains merge commits; they would be dropped")
    if not shas:
        raise UnusableInput(f"{args.range} holds no commits")
    onto = git(wt, "rev-parse", "HEAD").strip()
    state = {"range": args.range, "onto": onto, "status": "running", "stopped": None,
             "old": shas, "conflicts": {}}
    save_log(args.log, state)
    return drive(wt, state, args.log, universe, ["cherry-pick", *PICK_FLAGS, *shas])


# ---------- the one to one map ----------

_RANGE_ROW = re.compile(
    r"^ *(\d+|-):\s+([0-9a-f]+|-+)\s+([=!<>])\s+(\d+|-):\s+([0-9a-f]+|-+)\s+(.*)$")


def parse_range_diff(text):
    """[(marker, old abbreviation or None, new abbreviation or None)] of the
    header rows of `git range-diff` output."""
    rows = []
    for line in text.split("\n"):
        m = _RANGE_ROW.match(line)
        if m:
            old = None if m.group(2).startswith("-") else m.group(2)
            new = None if m.group(5).startswith("-") else m.group(5)
            rows.append((m.group(3), old, new))
    return rows


def patch_id(repo, sha):
    out = git(repo, "show", "--format=", "--no-color", "--no-renames", sha)
    ident = git(repo, "patch-id", "--stable", input=out)
    return ident.split()[0] if ident.strip() else ""


def significant_changes(diff):
    """{file: (removed text, added text)} of a -U0 diff after dropping import
    statement lines and whitespace.

    The changed lines of a file are normalised and joined, then the common
    beginning and end of the two texts is cut away, so a line that changed
    only in its whitespace counts as unchanged.  A file with nothing left is
    omitted.
    """
    minus, plus, path, in_hunk = {}, {}, None, False
    for line in diff.split("\n"):
        if line.startswith("diff --git "):
            path, in_hunk = line.rsplit(" b/", 1)[-1], False
        elif line.startswith("@@"):
            in_hunk = True
        elif in_hunk and line[:1] in ("+", "-"):
            (plus if line[0] == "+" else minus)[path] = (
                (plus if line[0] == "+" else minus).get(path, "") + sc.normalise(line[1:]))
    out = {}
    for path in sorted(set(minus) | set(plus)):
        a, b = minus.get(path, ""), plus.get(path, "")
        head = 0
        while head < min(len(a), len(b)) and a[head] == b[head]:
            head += 1
        a, b = a[head:], b[head:]
        tail = 0
        while tail < min(len(a), len(b)) and a[-1 - tail] == b[-1 - tail]:
            tail += 1
        a, b = a[:len(a) - tail], b[:len(b) - tail]
        if a or b:
            out[path] = (a, b)
    return out


def classify_pair(repo, old, new, conflict=None):
    """How a replayed commit relates to its original.

    "identical": the two patches have the same stable patch id.
    "import-or-whitespace": after dropping import statement lines and
    whitespace the two patches make the same change.
    "conflict-recorded": the pick log holds a conflict for it (a resolved
    import section or a verdict written by hand).  Anything else is
    "unexplained", which fails the gate.
    """
    if patch_id(repo, old) == patch_id(repo, new):
        return "identical"
    diffs = [git(repo, "show", "--format=", "--no-color", "--no-renames", "-U0", sha)
             for sha in (old, new)]
    if significant_changes(diffs[0]) == significant_changes(diffs[1]):
        return "import-or-whitespace"
    if conflict:
        return "conflict-recorded"
    return "unexplained"


def accept_range_diff_pair(repo, old, new, joined, fix_patch=None):
    """Accept an only-old row for `old` and an only-new row for `new` of the
    range-diff as one legitimately import-only pick, or return None.

    All three must hold: the pick log joins exactly these two commits; the old
    commit's own patch touches nothing but import lines and whitespace; and the
    new commit's tree equals the old commit's tree plus the fix patch (the old
    tree as is when there is none) in every .java file apart from import lines
    and whitespace, and exactly in every other file.  A fix patch that does not
    apply to the old commit leaves the pair unaccepted.
    """
    if not joined:
        return None
    rc, _, _ = git_rc(repo, "diff", "--quiet", f"{old}^", old, "--", ".", ":(exclude)*.java")
    if rc != 0:
        return None
    try:
        if check_diff(repo, f"{old}^", old)[0]:
            return None
        if check_diff(repo, old, new, base_patch=fix_patch)[0]:
            return None
    except UnusableInput:
        return None
    rc, _, _ = git_rc(repo, "diff", "--quiet", old, new, "--", ".", ":(exclude)*.java")
    if rc != 0:
        return None
    return {"old": old, "new": new, "old_patch": "imports-or-whitespace",
            "tree": "equal-outside-imports"}


def build_map(repo, logs, old_range, new_range, inserted, fix_patch):
    """The map document; `errors` lists every failed check."""
    errors = []
    old_list, new_list = rev_list(repo, old_range), rev_list(repo, new_range)
    picks = []
    for path in logs:
        state = load_log(path)
        if state.get("status") != "complete":
            raise UnusableInput(f"{path}: the pick log is not complete")
        picks += state["picks"]
    if [p["old"] for p in picks] != old_list:
        errors.append(f"the log's old commits differ from {old_range}")
    expected_new = [p["new"] for p in picks] + list(inserted)
    if sorted(expected_new) != sorted(new_list) or len(set(new_list)) != len(new_list):
        errors.append(f"the log's new commits plus the inserted ones differ from {new_range}")
    old_info = {i["sha"]: i for i in commit_info(repo, [p["old"] for p in picks])}
    new_info = {i["sha"]: i for i in commit_info(repo, [p["new"] for p in picks])}
    pairs, counts = [], {}
    for p in picks:
        o, n = old_info.get(p["old"]), new_info.get(p["new"])
        if o is None or n is None:
            errors.append(f"{p['old']} -> {p['new']}: a commit cannot be read")
            continue
        for key in ("subject", "author"):
            if o[key] != n[key]:
                errors.append(f"{p['old']} -> {p['new']}: the {key} differs")
        cls = classify_pair(repo, p["old"], p["new"], p.get("conflict"))
        if cls == "unexplained":
            errors.append(f"{p['old']} -> {p['new']}: unexplained difference")
        counts[cls] = counts.get(cls, 0) + 1
        pairs.append({"old": p["old"], "new": p["new"], "subject": p["subject"],
                      "class": cls,
                      "conflict": (p.get("conflict") or {}).get("class")})
    rd = git(repo, "range-diff", "--no-color", old_range, new_range)
    rows = parse_range_diff(rd)
    marks = {m: sum(1 for r in rows if r[0] == m) for m in "=!<>"}
    joined = {p["old"]: p["new"] for p in picks}
    accepted, only_new = [], [r[2] for r in rows if r[0] == ">"]
    for marker, old_abbrev, _ in rows:
        if marker != "<":
            continue
        old = next((s for s in old_list if s.startswith(old_abbrev)), None)
        new = joined.get(old)
        in_new = new is not None and any(new.startswith(a) for a in only_new)
        got = accept_range_diff_pair(repo, old, new, in_new, fix_patch) if old else None
        if got:
            accepted.append(got)
        else:
            errors.append(f"range-diff only-old row {old_abbrev} is not an accepted "
                          "import-only pairing")
    if marks[">"] - len(accepted) != len(inserted):
        errors.append(f"range-diff shows {marks['>']} only-new rows, {len(accepted)} of them "
                      f"accepted pairs, for {len(inserted)} inserted commits")
    return {"pairs": pairs, "counts": counts, "range_diff": marks,
            "range_diff_accepted": accepted, "errors": errors}


def cmd_map(args):
    inserted = [s for s in (args.inserted or "").split(",") if s]
    doc = build_map(args.repo, args.log, args.old, args.new, inserted, args.fix_patch)
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    print(f"{len(doc['pairs'])} pairs: "
          + ", ".join(f"{k}={v}" for k, v in sorted(doc["counts"].items()))
          + f"; range-diff {doc['range_diff']}; "
          f"{len(doc['range_diff_accepted'])} import-only pairing(s) accepted")
    for e in doc["errors"]:
        print(f"error: {e}")
    return 1 if doc["errors"] else 0


def cmd_patched_tree(args):
    print(patched_tree(args.repo, args.treeish, args.patch))
    return 0


# ---------- the per-tag gate ----------

GATE_NAMES = ("G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8")
FIX_AUTHOR = "rsbfox <aaron.crts@gmail.com>"
FIX_FILE = "src/main/java/shortestpath/requirement/ClientPlayerStateSource.java"
BUILD_PATHS = ("build.gradle", "settings.gradle", "gradle", "gradlew", "gradlew.bat", "config")
RELEASE_TAGS = ("v1.22.2", "v1.22.3", "v1.22.4", "v1.22.5")
CLEANUP_PREFIX = "style:"
WILDCARD_PREFIX = "refactor: use wildcard imports"
ATTRIBUTION = re.compile(r"(?im)^(?:co-authored-by|generated[ -]with|signed-off-by)\b|"
                         r"cherry picked from|generated with \[?claude")
# bracketed letters keep this file free of the very labels it looks for
PLANNING_LABEL = re.compile(r"\b[D]-[0-9]{2}\b|[P]hase [0-9]|\b3[6]\.[0-9]|\b[G]SD\b|"
                            r"\.[p]lanning|\b[V]-0[1]\b")
UNIVERSE = wi.DEFAULT_UNIVERSE


def gradle_run(worktree, *tasks):
    """Run Gradle on `worktree`; a red build is data.  Returns (output, rc)."""
    p = run([str(ROOT / "gradlew"), "-p", str(Path(worktree).resolve()), *tasks, "--no-daemon"],
            cwd=str(ROOT))
    return _text(p.stdout) + _text(p.stderr), p.returncode


def measure_tokens(repo, refs):
    """(output, rc) of scripts/measure_tokens.py over the refs."""
    p = run([sys.executable, str(SCRIPTS / "measure_tokens.py"), "--ref", ",".join(refs),
             "--repo", str(repo)])
    return _text(p.stdout), p.returncode


def quiet(func, *args):
    """(return value, captured output) of an in-process script call."""
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
        rc = func(*args)
    return rc, buf.getvalue()


def bytecode_identity(repo, base, head, classpath_file, work):
    """(returncode, output) of the bytecode identity proof of two tree-ishes."""
    return quiet(bi.main, ["--repo", str(repo), "--base", base, "--head", head,
                           "--classpath-file", str(classpath_file), "--work", str(work)])


def wildcard_main(*args):
    return quiet(wi.main, [*args, "--universe", str(UNIVERSE)])


def ls_remote(repo, remote, *patterns):
    return git_rc(repo, "ls-remote", remote, *patterns)


class Ctx:
    """The inputs of one gate run, with a few lookups shared by the gates."""

    def __init__(self, repo, worktree, tag, old, new, prev_old, prev_new, inserted,
                 fix_patch, classpath_file, tags_before, out, logs):
        self.repo, self.worktree, self.tag = repo, worktree, tag
        self.old, self.new, self.prev_old, self.prev_new = old, new, prev_old, prev_new
        self.inserted, self.fix_patch = list(inserted), fix_patch
        self.classpath_file, self.tags_before, self.out = classpath_file, tags_before, Path(out)
        self.logs = list(logs)
        self._picks = None

    def picks(self):
        if self._picks is None:
            self._picks = []
            for path in self.logs:
                state = load_log(path)
                if state.get("status") != "complete":
                    raise UnusableInput(f"{path}: the pick log is not complete")
                self._picks += state["picks"]
        return self._picks

    def info(self, sha):
        return commit_info(self.repo, [sha])[0]

    def inserted_by_kind(self):
        """{"cleanup": [...], "wildcard": [...], "other": [...]} of the inserted SHAs."""
        kinds = {"cleanup": [], "wildcard": [], "other": []}
        for sha in self.inserted:
            subject = self.info(sha)["subject"]
            if subject.startswith(CLEANUP_PREFIX):
                kinds["cleanup"].append(sha)
            elif subject.startswith(WILDCARD_PREFIX):
                kinds["wildcard"].append(sha)
            else:
                kinds["other"].append(sha)
        return kinds


def is_re_picked_marker(ctx, sha):
    """The tip as the re-picked marker: empty, with OLD's subject and author."""
    mine, old = ctx.info(sha), ctx.info(ctx.old)
    empty = (git(ctx.repo, "rev-parse", f"{sha}^{{tree}}").strip()
             == git(ctx.repo, "rev-parse", f"{sha}~1^{{tree}}").strip())
    return empty and mine["subject"] == old["subject"] and mine["author"] == old["author"]


def fix_patch_id(ctx):
    ident = git(ctx.repo, "patch-id", "--stable", input=Path(ctx.fix_patch).read_text())
    return ident.split()[0] if ident.strip() else ""


def gate_g1(ctx):
    detail = {}
    shutil.rmtree(Path(ctx.worktree) / "build" / "test-results" / "test", ignore_errors=True)
    out, rc = gradle_run(ctx.worktree, "compileTestJava", "test", "checkstyleMain",
                         "checkstyleTest")
    detail["gradle_exit"] = rc
    if rc != 0:
        detail["tail"] = out.strip().splitlines()[-20:]
    deps, deps_rc = gradle_run(ctx.worktree, "dependencies", "--configuration",
                               "compileClasspath", "-q")
    m = re.search(r"net\.runelite:client:(\S+)(?: -> (\S+))?", deps) if deps_rc == 0 else None
    detail["runelite_client"] = (m.group(2) or m.group(1)) if m else "unresolved"
    tests = 0
    results = Path(ctx.worktree) / "build" / "test-results" / "test"
    for xml in sorted(results.glob("*.xml")):
        try:
            tests += int(ET.parse(xml).getroot().get("tests", "0"))
        except (ET.ParseError, ValueError, OSError):
            detail.setdefault("unreadable_results", []).append(xml.name)
    detail["tests"] = tests
    return rc == 0 and tests > 0 and "unreadable_results" not in detail, detail


def gate_g2(ctx):
    problems, detail = [], {}
    changed = git(ctx.repo, "diff", "--name-only", ctx.old, ctx.new, "--",
                  *BUILD_PATHS).split()
    if changed:
        problems.append("build files differ between OLD and NEW: " + ", ".join(changed))
    outside = git(ctx.repo, "diff", "--name-only", ctx.old, ctx.new, "--", ".",
                  ":(exclude)*.java").split()
    if outside:
        problems.append("non-java files differ between OLD and NEW: " + ", ".join(outside))
    in_range = rev_list(ctx.repo, f"{ctx.prev_new}..{ctx.new}")
    logged = {p["new"] for p in ctx.picks()}
    foreign = []
    for sha in in_range:
        if sha in logged or sha in ctx.inserted:
            continue
        if sha == ctx.new and is_re_picked_marker(ctx, sha):
            continue
        foreign.append(sha)
    if foreign:
        problems.append("commits that are neither picked nor inserted: " + ", ".join(foreign))
    for sha in ctx.inserted:
        if sha not in in_range:
            problems.append(f"inserted commit {sha} is not in {ctx.prev_new}..{ctx.new}")
    fix_id = fix_patch_id(ctx)
    kinds = ctx.inserted_by_kind()
    for sha in ctx.inserted:
        info = ctx.info(sha)
        if ATTRIBUTION.search(info["message"]):
            problems.append(f"{sha}: the message carries an attribution trailer")
        if PLANNING_LABEL.search(info["message"]):
            problems.append(f"{sha}: the message carries an internal planning label")
    for sha in kinds["other"]:
        touched = git(ctx.repo, "diff-tree", "--no-commit-id", "--name-only", "-r",
                      sha).split()
        if FIX_FILE not in touched:
            continue
        info = ctx.info(sha)
        if info["author"] != FIX_AUTHOR:
            problems.append(f"{sha}: the compile fix is authored by {info['author']}, "
                            f"not {FIX_AUTHOR}")
        if patch_id(ctx.repo, sha) != fix_id:
            problems.append(f"{sha}: the compile fix differs from the fix patch")
        detail.setdefault("fix_commits", []).append(sha)
    detail["problems"] = problems
    detail["range_commits"] = len(in_range)
    return not problems, detail


def gate_g3(ctx):
    cleanup = ctx.inserted_by_kind()["cleanup"]
    if len(cleanup) != 1:
        return False, {"error": f"{len(cleanup)} inserted `style:` commits, expected one"}
    sha = cleanup[0]
    offences, summary = check_diff(ctx.repo, f"{sha}^", sha, imports_subset=True)
    return not offences, {"commit": sha, "offences": offences, "lines": summary["lines"],
                          "files": summary["files"]}


def gate_g4(ctx):
    detail, problems = {}, []
    rc, out = wildcard_main("check", "--repo", str(ctx.repo), "--ref", ctx.new)
    detail["check_exit"] = rc
    if rc != 0:
        problems.append("the rewriter's check fails at NEW: " + " | ".join(
            out.strip().splitlines()[-3:]))
    commits = ctx.inserted_by_kind()["wildcard"]
    if len(commits) != 1:
        problems.append(f"{len(commits)} inserted wildcard commits, expected one")
        detail["problems"] = problems
        return False, detail
    commit = commits[0]
    detail["commit"] = commit
    work = ctx.out / "g4"
    shutil.rmtree(work, ignore_errors=True)
    try:
        bi.export_tree(ctx.repo, f"{commit}^", work / "parent")
        bi.export_tree(ctx.repo, commit, work / "commit")
    except bi.UnusableInput as e:
        raise UnusableInput(str(e)) from e
    rc, out = wildcard_main("rewrite", "--repo", str(work / "parent"))
    if rc != 0:
        problems.append("the rewriter fails on the parent: " + out.strip()[-200:])
    else:
        rc, lines = bi.compare_dirs(work / "parent" / "src" / "main" / "java",
                                    work / "commit" / "src" / "main" / "java")
        detail["rewrite_diff_exit"] = rc
        if rc != 0:
            problems.append("the wildcard commit is not the rewriter's output on its parent: "
                            + "; ".join(lines[:5]))
    rc, out = bytecode_identity(ctx.repo, f"{commit}^", commit, ctx.classpath_file,
                                work / "bytecode")
    detail["bytecode_exit"] = rc
    if rc != 0:
        problems.append("class files differ from the parent's: " + out.strip()[-300:])
    detail["problems"] = problems
    return not problems, detail


def gate_g5(ctx):
    detail, problems = {}, []
    tree = patched_tree(ctx.repo, ctx.old, ctx.fix_patch)
    detail["old_plus_fix_tree"] = tree
    rc, out = bytecode_identity(ctx.repo, tree, ctx.new, ctx.classpath_file,
                                ctx.out / "g5-bytecode")
    detail["bytecode_exit"] = rc
    if rc != 0:
        problems.append("class files differ: " + out.strip()[-300:])
    offences, summary = check_diff(ctx.repo, ctx.old, ctx.new, base_patch=ctx.fix_patch)
    detail["java_files_differing"] = summary["files"]
    detail["lines"] = summary["lines"]
    if offences:
        problems.append("files differ beyond imports and whitespace: " + "; ".join(offences[:10]))
    detail["problems"] = problems
    return not problems, detail


def token_totals(output, refs):
    """{ref: total} from measure_tokens output over `refs`."""
    totals, current = {}, None
    for line in output.split("\n"):
        if line.startswith("# ref "):
            current = line[len("# ref "):].strip()
        elif line.startswith("TOTAL\t"):
            number = int(line.split("\t")[1].split()[0])
            totals[current if current is not None else refs[0]] = number
    return totals


def gate_g6(ctx):
    steps = [("old", ctx.old)]
    if ctx.prev_new != ctx.prev_old:
        steps.append(("prev_new", ctx.prev_new))
    picks = ctx.picks()
    if picks:
        steps.append(("last_pick", picks[-1]["new"]))
    steps += [(f"inserted: {ctx.info(s)['subject'][:60]}", s) for s in ctx.inserted]
    steps.append(("new", ctx.new))
    resolved = []
    for label, ref in steps:
        sha = git(ctx.repo, "rev-parse", "--verify", f"{ref}^{{commit}}").strip()
        if sha not in [r[1] for r in resolved]:
            resolved.append((label, sha))
    out, rc = measure_tokens(ctx.repo, [sha for _, sha in resolved])
    if rc != 0:
        return False, {"error": f"measure_tokens.py exited {rc}", "tail": out.strip()[-300:]}
    totals = token_totals(out, [sha for _, sha in resolved])
    if set(totals) != {sha for _, sha in resolved}:
        return False, {"error": "measure_tokens.py did not report every ref", "totals": totals}
    old = git(ctx.repo, "rev-parse", ctx.old).strip()
    new = git(ctx.repo, "rev-parse", ctx.new).strip()
    detail = {"totals": totals, "old_ref": old, "new_ref": new,
              "steps": [{"label": label, "ref": sha, "total": totals[sha]}
                        for label, sha in resolved]}
    return totals[new] < totals[old], detail


def gate_g7(ctx):
    old_range, new_range = f"{ctx.prev_old}..{ctx.old}", f"{ctx.prev_new}..{ctx.new}"
    if ctx.logs:
        doc = build_map(ctx.repo, ctx.logs, old_range, new_range, ctx.inserted, ctx.fix_patch)
        (ctx.out).mkdir(parents=True, exist_ok=True)
        (ctx.out / "map.json").write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
        return not doc["errors"], {"counts": doc["counts"], "range_diff": doc["range_diff"],
                                   "range_diff_accepted": doc["range_diff_accepted"],
                                   "errors": doc["errors"]}
    problems = []
    if rev_list(ctx.repo, old_range) != [ctx.old]:
        problems.append(f"{old_range} holds more than the marker but no pick log was given")
    in_range = rev_list(ctx.repo, new_range)
    if sorted(in_range) != sorted([*ctx.inserted, ctx.new]):
        problems.append(f"{new_range} is not exactly the inserted commits plus the "
                        "re-picked marker")
    elif not is_re_picked_marker(ctx, ctx.new):
        problems.append("the tip is not an empty re-pick of OLD's marker")
    return not problems, {"inserted": len(ctx.inserted), "problems": problems}


def normalised_refs(text):
    return sorted(" ".join(line.split()) for line in text.splitlines() if line.strip())


def gate_g8(ctx):
    problems, detail = [], {}
    rc, out, err = ls_remote(ctx.repo, "upstream", *[f"refs/tags/{t}" for t in RELEASE_TAGS])
    if rc != 0:
        return False, {"error": f"git ls-remote upstream failed: {err.strip()}"}
    before = Path(ctx.tags_before).read_text(encoding="utf-8")
    if normalised_refs(out) != normalised_refs(before):
        problems.append("the upstream release tags moved: now "
                        + " | ".join(normalised_refs(out)))
    for remote in ("upstream", "origin"):
        rc, out, err = ls_remote(ctx.repo, remote, "--heads", "refs/heads/rebuild/*")
        if rc != 0:
            problems.append(f"git ls-remote {remote} failed: {err.strip()}")
        elif out.strip():
            problems.append(f"{remote} has rebuild/ branches: " + " | ".join(
                normalised_refs(out)))
    detail["problems"] = problems
    return not problems, detail


GATES = {"G1": gate_g1, "G2": gate_g2, "G3": gate_g3, "G4": gate_g4, "G5": gate_g5,
         "G6": gate_g6, "G7": gate_g7, "G8": gate_g8}


def run_gates(ctx):
    results = {}
    for name in GATE_NAMES:
        try:
            ok, detail = GATES[name](ctx)
        except (UnusableInput, sc.UnusableInput, wi.UnusableInput, bi.UnusableInput) as e:
            ok, detail = False, {"error": str(e)}
        results[name] = {"pass": bool(ok), "detail": detail}
        print(f"{name}: {'pass' if ok else 'FAIL'}")
    return results


def cmd_gate(args):
    for label, path in (("fix patch", args.fix_patch), ("classpath file", args.classpath_file),
                        ("tags-before file", args.tags_before)):
        if not Path(path).is_file():
            raise UnusableInput(f"the {label} {path} does not exist")
    wt = Path(args.worktree).resolve()
    if git(wt, "status", "--porcelain").strip():
        raise UnusableInput(f"{wt} is not clean")
    new = git(args.repo, "rev-parse", "--verify", f"{args.new}^{{commit}}").strip()
    if git(wt, "rev-parse", "HEAD").strip() != new:
        raise UnusableInput(f"{wt} is not at {args.new}")
    resolve = lambda ref: git(args.repo, "rev-parse", "--verify", f"{ref}^{{commit}}").strip()
    inserted = [resolve(s) for s in (args.inserted or "").split(",") if s]
    ctx = Ctx(args.repo, wt, args.tag, resolve(args.old), new, resolve(args.prev_old),
              resolve(args.prev_new), inserted, args.fix_patch, args.classpath_file,
              args.tags_before, args.out, args.log or [])
    ctx.out.mkdir(parents=True, exist_ok=True)
    gates = run_gates(ctx)
    doc = {"tag": args.tag, "old": ctx.old, "new": ctx.new, "prev_old": ctx.prev_old,
           "prev_new": ctx.prev_new, "inserted": inserted, "gates": gates,
           "pass": all(g["pass"] for g in gates.values())}
    (ctx.out / "gate.json").write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    failed = [n for n, g in gates.items() if not g["pass"]]
    print(f"{args.tag}: " + ("all gates pass" if not failed else "failed " + ", ".join(failed)))
    return 1 if failed else 0


# ---------- entry point ----------

def build_parser():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = p.add_subparsers(dest="cmd", required=True)

    k = sub.add_parser("pick", help="replay a range one commit at a time",
                       description="Cherry-pick A..B oldest first, resolving conflicts that "
                       "only touch the import section; exit 3 when a person has to resolve one.")
    k.add_argument("--worktree", required=True)
    k.add_argument("--range")
    k.add_argument("--log", required=True)
    k.add_argument("--continue", dest="continue_", action="store_true")
    k.add_argument("--verdict")
    k.add_argument("--universe")

    m = sub.add_parser("map", help="prove a replay is one to one",
                       description="Pair the old and the new commits through the pick log(s), "
                       "classify every pair and compare with git range-diff.  An only-old "
                       "range-diff row and the only-new row of its replay are accepted only "
                       "for a legitimately import-only pick: the log joins the two commits, "
                       "the original's patch touches nothing but import lines and whitespace, "
                       "and the replay's tree equals the original's tree plus the fix patch "
                       "outside import lines and whitespace.  Every other only-old row is a "
                       "failure.")
    m.add_argument("--repo", required=True)
    m.add_argument("--log", action="append", required=True)
    m.add_argument("--old", required=True)
    m.add_argument("--new", required=True)
    m.add_argument("--inserted")
    m.add_argument("--fix-patch")
    m.add_argument("--out", required=True)

    gt = sub.add_parser("gate", help="check every gate of one rebuilt release tag",
                        description="Run the suite, hygiene, cleanup, wildcard, equivalence, "
                        "token, map and remote gates and write DIR/gate.json.")
    for flag in ("--repo", "--worktree", "--tag", "--old", "--new", "--prev-old", "--prev-new",
                 "--fix-patch", "--classpath-file", "--tags-before", "--out"):
        gt.add_argument(flag, required=True)
    gt.add_argument("--inserted", default="")
    gt.add_argument("--log", action="append")

    t = sub.add_parser("patched-tree", help="print the tree of a tree-ish with a patch applied")
    t.add_argument("--repo", required=True)
    t.add_argument("--treeish", required=True)
    t.add_argument("--patch", required=True)
    return p


def main(argv=None):
    args = build_parser().parse_args(argv)
    try:
        return {"pick": cmd_pick, "map": cmd_map, "gate": cmd_gate,
                "patched-tree": cmd_patched_tree}[args.cmd](args)
    except (UnusableInput, wi.UnusableInput, sc.UnusableInput, bi.UnusableInput) as e:
        print(f"error: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
