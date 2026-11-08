#!/usr/bin/env python3
"""Fix what checkstyle reports, and prove a cleanup changed nothing else.

Two subcommands:

  fix --worktree WT [--summary FILE]
      Run checkstyleMain and checkstyleTest in a clean plugin worktree and
      repair exactly what the reports name:
        * UnusedImports: the import line is deleted;
        * "No trailing whitespace": trailing spaces and tabs are stripped;
        * LeftCurly: when the `{` at the reported column is the last
          non-whitespace character of its line, the brace and the whitespace
          before it leave the line and a new line holding the original line's
          leading tabs plus `{` follows it.
      Anything else (another check, a brace in the middle of a line) is not
      touched; it is listed as file:line:check:message and the command exits
      1, so the caller fixes it by hand and records the fix.  Edits go
      bottom-up within a file, so reported line numbers stay valid, and bytes
      outside the edited lines (line endings included) are left alone.  The
      checkstyle run is repeated, at most three fix rounds, until no error
      remains.  Checkstyle depends on compileJava: a tree that does not
      compile writes no report, and a missing or stale report is never read as
      "clean" (exit 2).  The old report files are deleted before every run so
      Gradle cannot skip the task and leave a previous report behind.

  check-diff --repo R BASE HEAD [--imports-subset] [--base-patch FILE]
             [--summary FILE]
      BASE and HEAD are tree-ishes of R.  With --base-patch the patch is
      applied to BASE first (a temporary index, no working tree is touched).
      Every .java path that differs is an offence when it was added or
      deleted, or when its normalised text differs.  The normalised text has
      the import statement lines removed and the whitespace outside string and
      char literals removed (a space is kept only where deleting it would fuse
      two tokens, and the newline ending a // comment is kept, so code cannot
      slide into a comment unnoticed).  With --imports-subset the import lines
      of HEAD must also be a subset of those of BASE.  The summary JSON counts
      the changed lines per class: import, whitespace and brace.

Exit codes: 0 done / no offence, 1 unhandled errors left / offences listed,
2 unusable input (dirty worktree, no fresh report, report path outside the
worktree's src, patch that does not apply, git failure).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORTS = ("build/reports/checkstyle/main.xml", "build/reports/checkstyle/test.xml")
CHECKSTYLE_TASKS = ("checkstyleMain", "checkstyleTest")
TAB_WIDTH = 8
MAX_ROUNDS = 3

IDENT = r"[A-Za-z_$][A-Za-z0-9_$]*"
_IMPORT_BODY = (r"import\s+(?:static\s+)?" + IDENT + r"(?:\s*\.\s*" + IDENT
                + r")*(?:\s*\.\s*\*)?\s*;")
IMPORT_LINE = re.compile(r"[ \t]*" + _IMPORT_BODY + r"[ \t]*\r?(?:\n|\Z)")
IMPORT_STATEMENT = re.compile(r"^[ \t]*" + _IMPORT_BODY + r"[ \t]*\r?$")
WS = " \t\r\n\f"
OPERATOR_CHARS = frozenset("+-&|<>=!/*%^")


class UnusableInput(Exception):
    """Input that cannot be processed safely; mapped to exit code 2."""


# ---------- seams ----------

def run(argv, cwd=None, env=None, input=None):
    """Run a command given as an argument list; stdout and stderr are bytes."""
    return subprocess.run(argv, check=False, capture_output=True, cwd=cwd, env=env,
                          input=input)


def clock():
    return time.time()


def _text(data):
    if isinstance(data, bytes):
        return data.decode("utf-8", errors="surrogateescape")
    return data or ""


def git(repo, *args, env=None, input=None):
    """stdout (text) of `git -C repo args...`; a failing git is unusable input."""
    p = run(["git", "-C", str(repo), *args], env=env, input=input)
    if p.returncode != 0:
        raise UnusableInput(f"git -C {repo} {' '.join(args)} failed with exit code "
                            f"{p.returncode}:\n{_text(p.stderr).strip()}")
    return _text(p.stdout)


def gradle(worktree, *tasks):
    """Run Gradle on `worktree`; a red build is data.  Returns (output, rc)."""
    p = run([str(ROOT / "gradlew"), "-p", str(worktree), *tasks, "--continue", "-q"],
            cwd=str(ROOT))
    return _text(p.stdout) + _text(p.stderr), p.returncode


# ---------- checkstyle reports ----------

@dataclass(frozen=True)
class Error:
    path: str
    line: int
    column: int | None
    message: str
    source: str  # the check's class name, for example UnusedImportsCheck


def parse_report(path):
    """The <error> elements of a checkstyle XML report, in file order."""
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as e:
        raise UnusableInput(f"cannot read the checkstyle report {path}: {e}") from e
    errors = []
    for file_el in root.iter("file"):
        for err in file_el.iter("error"):
            try:
                line = int(err.get("line", ""))
            except ValueError:
                raise UnusableInput(f"{path}: an error of {file_el.get('name')} "
                                    "carries no line number") from None
            column = err.get("column")
            errors.append(Error(
                file_el.get("name", ""), line,
                int(column) if column and column.isdigit() else None,
                err.get("message", ""), (err.get("source") or "").rsplit(".", 1)[-1]))
    return errors


def classify(err):
    """The fix class of an error, or None when the helper does not handle it."""
    if err.source == "UnusedImportsCheck":
        return "UnusedImports"
    if err.source == "RegexpSinglelineJavaCheck" and err.message == "No trailing whitespace":
        return "TrailingWhitespace"
    if err.source == "LeftCurlyCheck":
        return "LeftCurly"
    return None


def scope_of(path):
    return "test" if "/src/test/" in path.replace("\\", "/") else "main"


def _expanded_column(line, index):
    """1-based tab-expanded column of the character at `index`; the column
    checkstyle puts in the XML report counts a tab as TAB_WIDTH."""
    col = 0
    for ch in line[:index]:
        col = (col // TAB_WIDTH + 1) * TAB_WIDTH if ch == "\t" else col + 1
    return col + 1


def _brace_ends_line(line, column):
    """Index of the `{` that is the last non-whitespace character of `line`
    and sits at the reported column, else None."""
    body = line.rstrip(" \t\r")
    if not body.endswith("{") or column is None:
        return None
    index = len(body) - 1
    if column in (_expanded_column(body, index), index + 1):
        return index
    return None


def _has_trailing_blank(line):
    body = line[:-1] if line.endswith("\r") else line
    return body != body.rstrip(" \t")


def _plan_line(err, kind, line):
    """Whether this error can be fixed on this line (a check before any edit)."""
    if kind == "UnusedImports":
        return IMPORT_STATEMENT.match(line) is not None
    if kind == "TrailingWhitespace":
        return _has_trailing_blank(line)
    if kind == "LeftCurly":
        return _brace_ends_line(line, err.column) is not None
    return False


def _fix_line(line, kinds):
    """The replacement lines for one source line carrying the given fix kinds."""
    if "UnusedImports" in kinds:
        return []
    out = [line]
    if "LeftCurly" in kinds:
        body = line.rstrip(" \t\r")
        index = len(body) - 1
        tail = line[len(body):]
        head = body[:index].rstrip(" \t")
        indent = re.match(r"\t*", line).group(0)
        cr = "\r" if line.endswith("\r") else ""
        out = [head + cr, indent + "{" + tail]
    if "TrailingWhitespace" in kinds:
        last = out[-1]
        cr = "\r" if last.endswith("\r") else ""
        out[-1] = (last[:-1] if cr else last).rstrip(" \t") + cr
    return out


def _resolve_inside(worktree, name):
    src = (Path(worktree) / "src").resolve()
    path = Path(name).resolve()
    if not path.is_relative_to(src) or not path.is_file():
        raise UnusableInput(f"the report names {name}, which is not a file inside {src}")
    return path


def apply_fixes(worktree, errors):
    """Apply every fixable error; returns (Counter of (scope, class), unhandled errors).

    Every path is checked before the first edit, so a bad report changes nothing.
    """
    by_file = {}
    for err in errors:
        by_file.setdefault(_resolve_inside(worktree, err.path), []).append(err)
    applied, unhandled = Counter(), []
    pending = []
    for path, errs in sorted(by_file.items()):
        text = _text(path.read_bytes())
        lines = text.split("\n")
        plan = {}  # line number -> set of fix kinds
        for err in errs:
            kind = classify(err)
            if not 1 <= err.line <= len(lines):
                raise UnusableInput(f"{err.path}:{err.line}: the report is stale "
                                    f"(the file has {len(lines)} lines)")
            if kind and _plan_line(err, kind, lines[err.line - 1]):
                plan.setdefault(err.line, set()).add(kind)
                applied[(scope_of(err.path), kind)] += 1
            else:
                unhandled.append(err)
        pending.append((path, lines, plan))
    for path, lines, plan in pending:
        if not plan:
            continue
        for number in sorted(plan, reverse=True):
            lines[number - 1:number] = _fix_line(lines[number - 1], plan[number])
        path.write_bytes("\n".join(lines).encode("utf-8", errors="surrogateescape"))
    return applied, unhandled


def run_checkstyle(worktree):
    """Run both checkstyle tasks and return the errors of the fresh reports."""
    worktree = Path(worktree)
    reports = [worktree / r for r in REPORTS]
    for report in reports:
        report.unlink(missing_ok=True)
    start = clock()
    output, rc = gradle(worktree, *CHECKSTYLE_TASKS)
    for report in reports:
        if not report.is_file() or report.stat().st_mtime < start:
            tail = "\n".join(output.strip().splitlines()[-15:])
            raise UnusableInput(f"{report} was not written by this run (gradle exit {rc}); "
                                f"a tree that does not compile writes no report:\n{tail}")
    errors = []
    for report in reports:
        errors += parse_report(report)
    return errors


def _describe(counter):
    parts = []
    for scope in ("main", "test"):
        kinds = {k: n for (s, k), n in counter.items() if s == scope}
        text = ", ".join(f"{k}={n}" for k, n in sorted(kinds.items())) or "none"
        parts.append(f"{scope}: {text}")
    return "; ".join(parts)


def _unhandled_line(worktree, err):
    try:
        shown = Path(err.path).resolve().relative_to(Path(worktree).resolve())
    except ValueError:
        shown = err.path
    return f"{shown}:{err.line}:{err.source}:{err.message}"


def cmd_fix(args):
    worktree = Path(args.worktree).resolve()
    if git(worktree, "status", "--porcelain").strip():
        raise UnusableInput(f"{worktree} is not clean; commit or discard first")
    total, rounds, unhandled = Counter(), 0, []
    while True:
        errors = run_checkstyle(worktree)
        if not errors:
            unhandled = []
            break
        if rounds == MAX_ROUNDS:
            unhandled = errors
            break
        applied, unhandled = apply_fixes(worktree, errors)
        if not applied:
            break
        rounds += 1
        total.update(applied)
        print(f"round {rounds}: {_describe(applied)}")
    summary = {"rounds": rounds,
               "applied": {f"{s}/{k}": n for (s, k), n in sorted(total.items())},
               "unhandled": [_unhandled_line(worktree, e) for e in unhandled]}
    if args.summary:
        Path(args.summary).write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(f"fixed in {rounds} round(s): {_describe(total)}")
    if unhandled:
        print(f"{len(unhandled)} error(s) left for a hand fix:")
        for err in unhandled:
            print(f"  {_unhandled_line(worktree, err)}")
        return 1
    print("checkstyle is clean")
    return 0


# ---------- normalisation ----------

def _keeps_space(before, after):
    """Deleting the whitespace between these characters would fuse two tokens."""
    if (before.isalnum() or before in "_$") and (after.isalnum() or after in "_$"):
        return True
    return before in OPERATOR_CHARS and after in OPERATOR_CHARS


def _scan(text):
    """(normalised text, import statement lines) of a Java source."""
    out, imports = [], []
    pending = False
    state = "code"  # code | line | block | str | chr | text
    i, n = 0, len(text)

    def emit(ch):
        nonlocal pending
        if pending and out and _keeps_space(out[-1], ch):
            out.append(" ")
        pending = False
        out.append(ch)

    while i < n:
        c = text[i]
        if state == "code" and (i == 0 or text[i - 1] == "\n"):
            m = IMPORT_LINE.match(text, i)
            if m:
                imports.append(" ".join(m.group(0).split()))
                pending = True
                i = m.end()
                continue
        if state == "code" or state in ("line", "block"):
            if c in WS:
                if state == "line" and c == "\n":
                    out.append("\n")
                    pending = False
                    state = "code"
                else:
                    pending = True
                i += 1
            elif state == "code" and text.startswith("//", i):
                emit("/")
                emit("/")
                state = "line"
                i += 2
            elif state == "code" and text.startswith("/*", i):
                emit("/")
                emit("*")
                state = "block"
                i += 2
            elif state == "block" and text.startswith("*/", i):
                emit("*")
                emit("/")
                state = "code"
                i += 2
            elif state == "code" and text.startswith('"""', i):
                for ch in '"""':
                    emit(ch)
                state = "text"
                i += 3
            elif state == "code" and c == '"':
                emit(c)
                state = "str"
                i += 1
            elif state == "code" and c == "'":
                emit(c)
                state = "chr"
                i += 1
            else:
                emit(c)
                i += 1
        elif state == "text":
            out.append(c)
            if c == "\\" and i + 1 < n:
                out.append(text[i + 1])
                i += 2
            elif text.startswith('"""', i):
                out.append('""')
                state = "code"
                i += 3
            else:
                i += 1
        else:  # str or chr
            out.append(c)
            quote = '"' if state == "str" else "'"
            if c == "\\" and i + 1 < n:
                out.append(text[i + 1])
                i += 2
                continue
            if c == quote or c == "\n":
                state = "code"
            i += 1
    return "".join(out), imports


def normalise(text):
    """Import lines removed, whitespace outside string and char literals removed."""
    return _scan(text)[0]


# ---------- trees and patches ----------

def tree_of(repo, treeish):
    return git(repo, "rev-parse", "--verify", f"{treeish}^{{tree}}").strip()


def patched_tree(repo, treeish, patch):
    """The tree SHA of `treeish` with the patch file applied.

    Uses a temporary index file; no working tree and no ref is touched.
    Raises UnusableInput when the patch does not apply.
    """
    patch = str(Path(patch).resolve())
    with tempfile.TemporaryDirectory(prefix="patched-tree-") as work:
        env = dict(os.environ, GIT_INDEX_FILE=str(Path(work) / "index"))
        git(repo, "read-tree", treeish, env=env)
        try:
            git(repo, "apply", "--cached", patch, env=env)
        except UnusableInput as e:
            raise UnusableInput(f"{patch} does not apply to {treeish}: {e}") from e
        return git(repo, "write-tree", env=env).strip()


def changed_java(repo, base_tree, head_tree):
    """[(status, path)] of the .java paths that differ between two trees."""
    out = git(repo, "diff-tree", "-r", "--no-renames", "--name-status", "-z",
              base_tree, head_tree)
    parts = out.split("\0")
    rows = []
    for k in range(0, len(parts) - 1, 2):
        if parts[k + 1].endswith(".java"):
            rows.append((parts[k], parts[k + 1]))
    return rows


def classify_line(content):
    if IMPORT_STATEMENT.match(content):
        return "import"
    if content.strip() == "{":
        return "brace"
    return "whitespace"


def count_changed_lines(repo, base_tree, head_tree, paths):
    counts = Counter({"import": 0, "whitespace": 0, "brace": 0})
    if not paths:
        return dict(counts)
    out = git(repo, "diff", "--no-renames", "--no-color", "-U0", base_tree, head_tree,
              "--", *paths)
    in_hunk = False
    for line in out.split("\n"):
        if line.startswith("diff --git"):
            in_hunk = False
        elif line.startswith("@@"):
            in_hunk = True
        elif in_hunk and line[:1] in ("+", "-"):
            counts[classify_line(line[1:])] += 1
    return dict(counts)


def check_diff(repo, base, head, imports_subset=False, base_patch=None):
    """(offences, summary) of HEAD against BASE; see the module docstring."""
    base_tree = patched_tree(repo, base, base_patch) if base_patch else tree_of(repo, base)
    head_tree = tree_of(repo, head)
    offences, java = [], changed_java(repo, base_tree, head_tree)
    for status, path in java:
        if status == "A":
            offences.append(f"{path}: file added")
            continue
        if status == "D":
            offences.append(f"{path}: file deleted")
            continue
        old_norm, old_imports = _scan(git(repo, "cat-file", "blob", f"{base_tree}:{path}"))
        new_norm, new_imports = _scan(git(repo, "cat-file", "blob", f"{head_tree}:{path}"))
        if old_norm != new_norm:
            offences.append(f"{path}: changes more than import lines and whitespace")
        if imports_subset:
            for imp in sorted(set(new_imports) - set(old_imports)):
                offences.append(f"{path}: adds an import: {imp}")
    summary = {"base_tree": base_tree, "head_tree": head_tree,
               "files": len(java), "offences": offences,
               "lines": count_changed_lines(repo, base_tree, head_tree, [p for _, p in java])}
    return offences, summary


def cmd_check_diff(args):
    offences, summary = check_diff(args.repo, args.base, args.head,
                                   args.imports_subset, args.base_patch)
    if args.summary:
        Path(args.summary).write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    lines = summary["lines"]
    print(f"{summary['files']} java file(s) differ; changed lines: "
          f"import={lines['import']} whitespace={lines['whitespace']} brace={lines['brace']}")
    for offence in offences:
        print(f"offence: {offence}")
    return 1 if offences else 0


def cmd_patched_tree(args):
    print(patched_tree(args.repo, args.treeish, args.patch))
    return 0


# ---------- entry point ----------

def build_parser():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = p.add_subparsers(dest="cmd", required=True)

    f = sub.add_parser("fix", help="repair what checkstyle reports in a clean worktree")
    f.add_argument("--worktree", required=True)
    f.add_argument("--summary")

    c = sub.add_parser("check-diff", help="prove a diff only touches imports and whitespace")
    c.add_argument("--repo", required=True)
    c.add_argument("base")
    c.add_argument("head")
    c.add_argument("--imports-subset", action="store_true")
    c.add_argument("--base-patch")
    c.add_argument("--summary")

    t = sub.add_parser("patched-tree", help="print the tree of a tree-ish with a patch applied")
    t.add_argument("--repo", required=True)
    t.add_argument("--treeish", required=True)
    t.add_argument("--patch", required=True)
    return p


def main(argv=None):
    args = build_parser().parse_args(argv)
    try:
        return {"fix": cmd_fix, "check-diff": cmd_check_diff,
                "patched-tree": cmd_patched_tree}[args.cmd](args)
    except UnusableInput as e:
        print(f"error: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
