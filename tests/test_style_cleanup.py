"""Tests for ``scripts/style_cleanup.py``.

``fix`` runs against throw-away git repositories standing in for a plugin
worktree; the Gradle seam is replaced by a fake that writes the checkstyle
reports a scripted run would produce, so no build is started.  ``check-diff``
runs real git commands on tiny repositories, because what it decides is
exactly what git reports.  The script is loaded via importlib because
``scripts/`` has no ``__init__.py``.
"""

import importlib.util
import json
import os
import subprocess
import sys
import time
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "style_cleanup.py"
FIXTURE = Path(__file__).resolve().parent / "fixtures" / "checkstyle_report_sample.xml"

spec = importlib.util.spec_from_file_location("style_cleanup", SCRIPT_PATH)
sc = importlib.util.module_from_spec(spec)
sys.modules["style_cleanup"] = sc
spec.loader.exec_module(sc)

A_JAVA = ("package p;\n"
          "\n"
          "import java.util.List;\n"
          "import java.util.Map;\n"
          "\n"
          "public class A  \n"
          "{\n"
          "\tvoid f(int x) {\n"
          "\t}\n"
          "}\n")
A_FIXED = ("package p;\n"
           "\n"
           "import java.util.Map;\n"
           "\n"
           "public class A\n"
           "{\n"
           "\tvoid f(int x)\n"
           "\t{\n"
           "\t}\n"
           "}\n")
B_JAVA = ("package p;\n"
          "\n"
          "import java.util.Set;\n"
          "\n"
          "class BTest\n"
          "{\n"
          "\tvoid g()\n"
          "\t{\n"
          "\t\tUtil.each(\"x\", (line, cols) -> { });\n"
          "\t\tint y=1;\n"
          "\t}\n"
          "}\n")
B_FIXED = B_JAVA.replace("import java.util.Set;\n\n", "\n")

GIT_ENV = dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@example.com",
               GIT_COMMITTER_NAME="t", GIT_COMMITTER_EMAIL="t@example.com")


def git(repo, *args, input=None):
    p = subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True,
                       text=True, env=GIT_ENV, input=input)
    return p.stdout.strip()


def git_diff(repo):
    """The raw `git diff` text (a patch needs its final newline)."""
    return subprocess.run(["git", "-C", str(repo), "diff", "--no-color"], check=True,
                          capture_output=True, text=True).stdout


def commit_all(repo, message="c"):
    git(repo, "add", "-A")
    git(repo, "commit", "-q", "--allow-empty", "-m", message)
    return git(repo, "rev-parse", "HEAD")


def write(repo, files):
    for name, text in files.items():
        p = Path(repo) / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(text.encode())


def make_repo(path, files):
    path.mkdir(parents=True, exist_ok=True)
    git(path, "init", "-q")
    write(path, {".gitignore": "build/\n", **files})
    commit_all(path, "base")
    return path


def report_xml(entries):
    """entries: {path: [(line, column, message, source)]} as checkstyle writes them."""
    out = ['<?xml version="1.0" encoding="UTF-8"?>', '<checkstyle version="9.3">']
    for path, errors in entries.items():
        out.append(f'<file name="{path}">')
        for line, column, message, source in errors:
            col = f' column="{column}"' if column else ""
            out.append(f'<error line="{line}"{col} severity="error" '
                       f'message="{message}" source="com.puppycrawl.{source}"/>')
        out.append("</file>")
    out.append("</checkstyle>")
    return "\n".join(out) + "\n"


UNUSED = "checks.imports.UnusedImportsCheck"
TRAILING = "checks.regexp.RegexpSinglelineJavaCheck"
CURLY = "checks.blocks.LeftCurlyCheck"
AROUND = "checks.whitespace.WhitespaceAroundCheck"


class FakeGradle:
    """Writes the scripted reports of run k (main, test dicts) and counts runs."""

    def __init__(self, runs, rc=1):
        self.runs = runs
        self.rc = rc
        self.calls = []

    def __call__(self, worktree, *tasks):
        self.calls.append((str(worktree), tasks))
        main, test = self.runs[min(len(self.calls) - 1, len(self.runs) - 1)]
        if main is not None:
            self._write(worktree, "main.xml", main)
        if test is not None:
            self._write(worktree, "test.xml", test)
        return "BUILD FAILED\n", self.rc

    @staticmethod
    def _write(worktree, name, entries):
        d = Path(worktree) / "build" / "reports" / "checkstyle"
        d.mkdir(parents=True, exist_ok=True)
        (d / name).write_text(report_xml(entries))


@pytest.fixture(autouse=True)
def early_clock(monkeypatch):
    # reports written by a fake count as fresh: the run "started" five seconds ago
    monkeypatch.setattr(sc, "clock", lambda: time.time() - 5)


@pytest.fixture
def wt(tmp_path):
    return make_repo(tmp_path / "wt", {"src/main/java/p/A.java": A_JAVA,
                                       "src/test/java/p/BTest.java": B_JAVA})


def a_path(wt):
    return str(Path(wt).resolve() / "src/main/java/p/A.java")


def b_path(wt):
    return str(Path(wt).resolve() / "src/test/java/p/BTest.java")


def a_errors(wt):
    return {a_path(wt): [(3, 8, "Unused import - java.util.List.", UNUSED),
                         (6, None, "No trailing whitespace", TRAILING),
                         (8, 23, "'{' at column 16 should be on a new line.", CURLY)]}


def run_fix(monkeypatch, wt, runs, *extra):
    fake = FakeGradle(runs)
    monkeypatch.setattr(sc, "gradle", fake)
    rc = sc.main(["fix", "--worktree", str(wt), *extra])
    return rc, fake


# ---------- report parsing ----------

def test_parse_report_reads_the_checkstyle_shape():
    errors = sc.parse_report(FIXTURE)
    assert [(Path(e.path).name, e.line, e.column, e.source) for e in errors] == [
        ("A.java", 3, 8, "UnusedImportsCheck"),
        ("A.java", 6, None, "RegexpSinglelineJavaCheck"),
        ("A.java", 8, 23, "LeftCurlyCheck"),
        ("BTest.java", 3, 8, "UnusedImportsCheck"),
        ("BTest.java", 9, 48, "LeftCurlyCheck"),
        ("BTest.java", 10, 22, "WhitespaceAroundCheck")]
    assert errors[1].message == "No trailing whitespace"


def test_parse_report_rejects_a_broken_file(tmp_path):
    bad = tmp_path / "main.xml"
    bad.write_text("<checkstyle><file")
    with pytest.raises(sc.UnusableInput):
        sc.parse_report(bad)


def test_classify_names_the_three_handled_classes_only():
    e = sc.parse_report(FIXTURE)
    assert [sc.classify(x) for x in e] == [
        "UnusedImports", "TrailingWhitespace", "LeftCurly", "UnusedImports", "LeftCurly", None]
    other = sc.Error("x", 1, 1, "Indent must use tab characters",
                     "RegexpSinglelineJavaCheck")
    assert sc.classify(other) is None


# ---------- each fix class, applied bottom-up ----------

def fixture_errors(wt):
    text = FIXTURE.read_text().replace("@WT@", str(Path(wt).resolve()))
    p = Path(wt) / "report.xml"
    p.write_text(text)
    return sc.parse_report(p)


def test_the_three_edits_of_a_file_are_applied_bottom_up(wt):
    errors = [e for e in fixture_errors(wt) if e.path.endswith("A.java")]
    applied, unhandled = sc.apply_fixes(wt, errors)
    assert (wt / "src/main/java/p/A.java").read_text() == A_FIXED
    assert unhandled == []
    assert applied == {("main", "UnusedImports"): 1, ("main", "TrailingWhitespace"): 1,
                       ("main", "LeftCurly"): 1}


def test_a_mid_line_brace_and_an_unknown_check_are_listed_and_the_rest_still_applied(wt):
    errors = [e for e in fixture_errors(wt) if e.path.endswith("BTest.java")]
    applied, unhandled = sc.apply_fixes(wt, errors)
    assert (wt / "src/test/java/p/BTest.java").read_text() == B_FIXED
    assert applied == {("test", "UnusedImports"): 1}
    assert [(e.line, e.source) for e in unhandled] == [
        (9, "LeftCurlyCheck"), (10, "WhitespaceAroundCheck")]


def test_brace_move_keeps_leading_tabs_and_drops_the_blanks_before_the_brace(tmp_path):
    root = make_repo(tmp_path / "w", {"src/main/java/p/C.java":
                     "class C\n{\n\t\tfoo((a, b) ->   {   \n\t\t});\n}\n"})
    err = sc.Error(str(root.resolve() / "src/main/java/p/C.java"), 3, 33, "m", "LeftCurlyCheck")
    applied, unhandled = sc.apply_fixes(root, [err])
    assert unhandled == [] and applied[("main", "LeftCurly")] == 1
    assert (root / "src/main/java/p/C.java").read_text() == (
        "class C\n{\n\t\tfoo((a, b) ->\n\t\t{   \n\t\t});\n}\n")


def test_brace_and_trailing_blanks_on_one_line_both_go(tmp_path):
    root = make_repo(tmp_path / "w", {"src/main/java/p/C.java":
                                      "class C\n{\n\tvoid f() {  \n\t}\n}\n"})
    path = str(root.resolve() / "src/main/java/p/C.java")
    errors = [sc.Error(path, 3, 18, "m", "LeftCurlyCheck"),
              sc.Error(path, 3, None, "No trailing whitespace", "RegexpSinglelineJavaCheck")]
    sc.apply_fixes(root, errors)
    assert (root / "src/main/java/p/C.java").read_text() == (
        "class C\n{\n\tvoid f()\n\t{\n\t}\n}\n")


def test_a_brace_whose_column_does_not_match_the_report_is_left_alone(tmp_path):
    root = make_repo(tmp_path / "w", {"src/main/java/p/C.java":
                                      "class C\n{\n\tvoid f() {\n\t}\n}\n"})
    err = sc.Error(str(root.resolve() / "src/main/java/p/C.java"), 3, 5, "m", "LeftCurlyCheck")
    applied, unhandled = sc.apply_fixes(root, [err])
    assert not applied and unhandled == [err]


def test_line_endings_and_bytes_outside_the_edited_lines_are_untouched(tmp_path):
    original = "package p;\r\nimport java.util.List;\r\nclass A \r\n{\r\n}\r\n\t \r\n"
    root = make_repo(tmp_path / "w", {"src/main/java/p/A.java": original})
    path = str(root.resolve() / "src/main/java/p/A.java")
    errors = [sc.Error(path, 2, 8, "Unused import - java.util.List.", "UnusedImportsCheck"),
              sc.Error(path, 3, None, "No trailing whitespace", "RegexpSinglelineJavaCheck")]
    sc.apply_fixes(root, errors)
    assert (root / "src/main/java/p/A.java").read_bytes() == (
        b"package p;\r\nclass A\r\n{\r\n}\r\n\t \r\n")


def test_an_unused_import_error_on_a_line_that_is_not_an_import_is_unhandled(wt):
    err = sc.Error(a_path(wt), 6, 8, "Unused import - x.", "UnusedImportsCheck")
    applied, unhandled = sc.apply_fixes(wt, [err])
    assert not applied and unhandled == [err]
    assert (wt / "src/main/java/p/A.java").read_text() == A_JAVA


def test_a_report_line_beyond_the_file_is_a_stale_report(wt):
    err = sc.Error(a_path(wt), 99, 1, "m", "LeftCurlyCheck")
    with pytest.raises(sc.UnusableInput, match="stale"):
        sc.apply_fixes(wt, [err])


# ---------- path containment ----------

def test_a_report_path_outside_the_worktree_changes_nothing_and_exits_2(
        monkeypatch, wt, tmp_path, capsys):
    outside = tmp_path / "elsewhere" / "src" / "X.java"
    outside.parent.mkdir(parents=True)
    outside.write_text("import a.B;\nclass X {}\n")
    entries = a_errors(wt)
    entries[str(outside)] = [(1, 8, "Unused import - a.B.", UNUSED)]
    rc, _ = run_fix(monkeypatch, wt, [(entries, {})])
    assert rc == 2
    assert "not a file inside" in capsys.readouterr().err
    assert outside.read_text() == "import a.B;\nclass X {}\n"
    assert (wt / "src/main/java/p/A.java").read_text() == A_JAVA


def test_a_report_path_inside_the_worktree_but_outside_src_exits_2(monkeypatch, wt):
    (wt / "build.gradle").write_text("x\n")
    commit_all(wt)
    entries = {str(wt.resolve() / "build.gradle"): [(1, 1, "m", UNUSED)]}
    rc, _ = run_fix(monkeypatch, wt, [(entries, {})])
    assert rc == 2


def test_a_dotdot_path_that_leaves_src_exits_2(monkeypatch, wt):
    sneaky = str(wt.resolve() / "src" / ".." / "build.gradle")
    (wt / "build.gradle").write_text("x\n")
    commit_all(wt)
    rc, _ = run_fix(monkeypatch, wt, [({sneaky: [(1, 1, "m", UNUSED)]}, {})])
    assert rc == 2


# ---------- never reading a missing or stale report as clean ----------

def test_no_report_written_exits_2_not_clean(monkeypatch, wt, capsys):
    rc, fake = run_fix(monkeypatch, wt, [(None, None)])
    assert rc == 2 and len(fake.calls) == 1
    err = capsys.readouterr().err
    assert "was not written by this run" in err


def test_only_one_of_two_reports_written_exits_2(monkeypatch, wt):
    rc, _ = run_fix(monkeypatch, wt, [({}, None)])
    assert rc == 2


def test_a_report_older_than_the_run_start_exits_2(monkeypatch, wt):
    class Stale(FakeGradle):
        def __call__(self, worktree, *tasks):
            out = super().__call__(worktree, *tasks)
            old = time.time() - 1000
            for name in ("main.xml", "test.xml"):
                os.utime(Path(worktree) / "build/reports/checkstyle" / name, (old, old))
            return out

    fake = Stale([({}, {})])
    monkeypatch.setattr(sc, "gradle", fake)
    assert sc.main(["fix", "--worktree", str(wt)]) == 2


def test_an_old_report_is_deleted_before_the_run(monkeypatch, wt):
    d = wt / "build/reports/checkstyle"
    d.mkdir(parents=True)
    (d / "main.xml").write_text(report_xml(a_errors(wt)))
    (d / "test.xml").write_text(report_xml({}))
    rc, _ = run_fix(monkeypatch, wt, [(None, None)])
    assert rc == 2  # the stale pair was not accepted as the run's output


def test_a_dirty_worktree_exits_2_before_any_build(monkeypatch, wt, capsys):
    (wt / "src/main/java/p/A.java").write_text(A_JAVA + "// edit\n")
    rc, fake = run_fix(monkeypatch, wt, [({}, {})])
    assert rc == 2 and fake.calls == []
    assert "not clean" in capsys.readouterr().err


# ---------- the loop ----------

def test_a_clean_tree_exits_0_after_one_run(monkeypatch, wt, capsys):
    rc, fake = run_fix(monkeypatch, wt, [({}, {})])
    assert rc == 0 and len(fake.calls) == 1
    assert fake.calls[0][1] == ("checkstyleMain", "checkstyleTest")
    assert "checkstyle is clean" in capsys.readouterr().out


def test_fix_then_a_clean_rerun_exits_0_and_counts_by_class(monkeypatch, wt, capsys, tmp_path):
    summary = tmp_path / "summary.json"
    rc, fake = run_fix(monkeypatch, wt, [(a_errors(wt), {}), ({}, {})],
                       "--summary", str(summary))
    assert rc == 0 and len(fake.calls) == 2
    assert (wt / "src/main/java/p/A.java").read_text() == A_FIXED
    out = capsys.readouterr().out
    assert "round 1: main: LeftCurly=1, TrailingWhitespace=1, UnusedImports=1; test: none" in out
    data = json.loads(summary.read_text())
    assert data["rounds"] == 1 and data["unhandled"] == []
    assert data["applied"]["main/UnusedImports"] == 1


def test_unhandled_errors_are_listed_and_exit_1_while_handled_ones_are_applied(
        monkeypatch, wt, capsys):
    first_test = {b_path(wt): [(3, 8, "Unused import - java.util.Set.", UNUSED),
                               (9, 48, "'{' at column 34 should be on a new line.", CURLY),
                               (10, 22, "'=' is not followed by whitespace.", AROUND)]}
    second_test = {b_path(wt): [(8, 48, "'{' at column 34 should be on a new line.", CURLY),
                                (9, 22, "'=' is not followed by whitespace.", AROUND)]}
    rc, fake = run_fix(monkeypatch, wt, [({}, first_test), ({}, second_test)])
    assert rc == 1 and len(fake.calls) == 2
    assert (wt / "src/test/java/p/BTest.java").read_text() == B_FIXED
    out = capsys.readouterr().out
    assert "src/test/java/p/BTest.java:8:LeftCurlyCheck:'{' at column 34" in out
    assert "src/test/java/p/BTest.java:9:WhitespaceAroundCheck" in out
    assert "2 error(s) left for a hand fix" in out


def test_fix_stops_after_three_rounds_with_errors_left(monkeypatch, tmp_path, capsys):
    lines = "".join(f"int v{i}; \n" for i in range(1, 6))
    root = make_repo(tmp_path / "w", {"src/main/java/p/D.java": lines})
    path = str(root.resolve() / "src/main/java/p/D.java")
    runs = [({path: [(i, None, "No trailing whitespace", TRAILING)]}, {})
            for i in range(1, 6)]
    rc, fake = run_fix(monkeypatch, root, runs)
    assert rc == 1 and len(fake.calls) == 4
    assert "round 3" in capsys.readouterr().out
    text = (root / "src/main/java/p/D.java").read_text().split("\n")
    assert text[:3] == ["int v1;", "int v2;", "int v3;"] and text[3] == "int v4; "


# ---------- normalisation ----------

def test_normalise_ignores_imports_and_whitespace_but_not_code():
    base = "import a.B;\nclass X {\n  int y = 1;  \n}\n"
    same = "class X {\n\tint y=1;\n}\n"
    assert sc.normalise(base) == sc.normalise(same)
    assert sc.normalise(base) != sc.normalise(base.replace("y = 1", "z = 1"))


def test_normalise_keeps_whitespace_inside_string_and_char_literals():
    assert sc.normalise('s = "a b";') != sc.normalise('s = "ab";')
    assert sc.normalise("c = ' ';") != sc.normalise("c = '';")
    assert sc.normalise('s = "a \\" b";') != sc.normalise('s = "a \\"b";')


def test_normalise_never_fuses_two_tokens():
    assert sc.normalise("return x;") != sc.normalise("returnx;")
    assert sc.normalise("a + +b;") != sc.normalise("a ++b;")
    assert sc.normalise("f(x) ->  {") == sc.normalise("f(x) ->\n{")


def test_the_newline_ending_a_line_comment_is_part_of_the_text():
    assert sc.normalise("a();\n// c\nb();\n") != sc.normalise("a();\n// c b();\n")


def test_an_import_inside_a_block_comment_is_not_dropped():
    commented = "/*\nimport a.B;\n*/\nclass X {}\n"
    assert sc.normalise(commented) != sc.normalise("/*\n*/\nclass X {}\n")


# ---------- check-diff ----------

BASE_SRC = ("package p;\n"
            "\n"
            "import java.util.List;\n"
            "import java.util.Map;\n"
            "\n"
            "class A \n"
            "{\n"
            "\tMap<String, String> m = null;\n"
            "\tString s = \"a b\";\n"
            "\tvoid f() {\n"
            "\t}\n"
            "}\n")


def cd(repo, base, head, *extra):
    return sc.main(["check-diff", "--repo", str(repo), base, head, *extra])


@pytest.fixture
def rep(tmp_path):
    repo = make_repo(tmp_path / "r", {"A.java": BASE_SRC, "data.tsv": "a\tb\n"})
    return repo, git(repo, "rev-parse", "HEAD")


def head_with(repo, **files):
    write(repo, files)
    return commit_all(repo, "head")


def test_check_diff_accepts_an_import_removal_plus_whitespace(rep, capsys, tmp_path):
    repo, base = rep
    head = head_with(repo, **{"A.java": BASE_SRC.replace("import java.util.List;\n", "")
                              .replace("class A \n", "class A\n")
                              .replace("void f() {", "void f()\n\t{")})
    summary = tmp_path / "s.json"
    assert cd(repo, base, head, "--imports-subset", "--summary", str(summary)) == 0
    data = json.loads(summary.read_text())
    assert data["lines"]["import"] == 1 and data["lines"]["brace"] == 1
    assert data["lines"]["whitespace"] == 4 and data["files"] == 1
    assert "1 java file(s) differ" in capsys.readouterr().out


def test_check_diff_rejects_a_changed_identifier(rep, capsys):
    repo, base = rep
    head = head_with(repo, **{"A.java": BASE_SRC.replace("m = null", "n = null")})
    assert cd(repo, base, head) == 1
    assert "A.java: changes more than import lines and whitespace" in capsys.readouterr().out


def test_check_diff_rejects_whitespace_removed_inside_a_string_literal(rep):
    repo, base = rep
    head = head_with(repo, **{"A.java": BASE_SRC.replace('"a b"', '"ab"')})
    assert cd(repo, base, head) == 1


def test_check_diff_imports_subset_rejects_an_added_import_only_with_the_flag(rep, capsys):
    repo, base = rep
    head = head_with(repo, **{"A.java": BASE_SRC.replace(
        "import java.util.Map;\n", "import java.util.Map;\nimport java.util.Set;\n")})
    assert cd(repo, base, head) == 0
    assert cd(repo, base, head, "--imports-subset") == 1
    assert "adds an import: import java.util.Set;" in capsys.readouterr().out


def test_check_diff_imports_subset_allows_a_reordered_import_block(rep):
    repo, base = rep
    head = head_with(repo, **{"A.java": BASE_SRC.replace(
        "import java.util.List;\nimport java.util.Map;\n",
        "import java.util.Map;\nimport java.util.List;\n")})
    assert cd(repo, base, head, "--imports-subset") == 0


def test_check_diff_with_a_base_patch_accepts_base_plus_the_patch(rep, tmp_path):
    repo, base = rep
    write(repo, {"A.java": BASE_SRC.replace("m = null", "n = null")})
    patch = tmp_path / "fix.patch"
    patch.write_text(git_diff(repo))
    git(repo, "checkout", "-q", "--", "A.java")
    # head: the patched file plus an import removal and a whitespace change
    head = head_with(repo, **{"A.java": BASE_SRC.replace("m = null", "n = null")
                              .replace("import java.util.List;\n", "")
                              .replace("class A \n", "class A\n")})
    assert cd(repo, base, head) == 1
    assert cd(repo, base, head, "--base-patch", str(patch)) == 0


def test_check_diff_with_a_base_patch_still_rejects_other_changes(rep, tmp_path):
    repo, base = rep
    write(repo, {"A.java": BASE_SRC.replace("m = null", "n = null")})
    patch = tmp_path / "fix.patch"
    patch.write_text(git_diff(repo))
    git(repo, "checkout", "-q", "--", "A.java")
    head = head_with(repo, **{"A.java": BASE_SRC.replace("m = null", "o = null")})
    assert cd(repo, base, head, "--base-patch", str(patch)) == 1


def test_check_diff_with_a_patch_that_does_not_apply_exits_2(rep, tmp_path, capsys):
    repo, base = rep
    patch = tmp_path / "bad.patch"
    patch.write_text("--- a/A.java\n+++ b/A.java\n@@ -1 +1 @@\n-nothing like it\n+x\n")
    assert cd(repo, base, base, "--base-patch", str(patch)) == 2
    assert "does not apply" in capsys.readouterr().err


def test_check_diff_flags_an_added_and_a_deleted_java_file(rep, capsys):
    repo, base = rep
    (repo / "A.java").unlink()
    head = head_with(repo, **{"B.java": "class B {}\n"})
    assert cd(repo, base, head) == 1
    out = capsys.readouterr().out
    assert "A.java: file deleted" in out and "B.java: file added" in out


def test_check_diff_looks_at_java_paths_only(rep):
    repo, base = rep
    head = head_with(repo, **{"data.tsv": "a\tc\n"})
    assert cd(repo, base, head) == 0


def test_check_diff_documents_that_it_examines_java_paths_only():
    # neither the help nor the docstring may promise more than the check does
    help_text = " ".join(sc.build_parser().format_help().split())
    assert "the .java files of a diff" in help_text
    assert "Only .java paths are examined" in " ".join(sc.__doc__.split())


def test_check_diff_with_an_unknown_tree_ish_exits_2(rep):
    repo, base = rep
    assert cd(repo, base, "no-such-ref") == 2


def test_check_diff_rejects_code_slid_into_a_line_comment(rep):
    repo, base = rep
    src = BASE_SRC.replace("\tvoid f() {\n", "\tvoid f() {\n\t\ta();\n\t\t// c\n\t\tb();\n")
    base = head_with(repo, **{"A.java": src})
    head = head_with(repo, **{"A.java": src.replace("// c\n\t\tb();", "// c b();")})
    assert cd(repo, base, head) == 1


# ---------- patched-tree ----------

def test_patched_tree_prints_the_tree_of_base_plus_patch(rep, tmp_path, capsys):
    repo, base = rep
    write(repo, {"A.java": BASE_SRC.replace("m = null", "n = null")})
    patch = tmp_path / "fix.patch"
    patch.write_text(git_diff(repo))
    head = commit_all(repo, "with the change")
    assert sc.main(["patched-tree", "--repo", str(repo), "--treeish", base,
                    "--patch", str(patch)]) == 0
    printed = capsys.readouterr().out.strip()
    assert printed == git(repo, "rev-parse", f"{head}^{{tree}}")


def test_patched_tree_touches_neither_the_worktree_nor_the_index(rep, tmp_path):
    repo, base = rep
    write(repo, {"A.java": BASE_SRC.replace("m = null", "n = null")})
    patch = tmp_path / "fix.patch"
    patch.write_text(git_diff(repo))
    git(repo, "checkout", "-q", "--", "A.java")
    before = git(repo, "status", "--porcelain")
    sc.patched_tree(repo, base, patch)
    assert git(repo, "status", "--porcelain") == before == ""
