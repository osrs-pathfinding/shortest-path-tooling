"""Tests for ``scripts/measure_footprint.py``.

Every git call goes through the module's single ``git`` seam, so tests
monkeypatch ``mf.git`` (or ``mf.subprocess.run`` for the seam itself)
with argv-bucketing fakes — no test touches the real submodule or the
network.  The script is loaded via importlib because ``scripts/`` has no
``__init__.py``.
"""

import csv
import importlib.util
import io
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "measure_footprint.py"
FIXTURES = Path(__file__).resolve().parent / "fixtures"

spec = importlib.util.spec_from_file_location("measure_footprint", SCRIPT_PATH)
mf = importlib.util.module_from_spec(spec)
sys.modules["measure_footprint"] = mf
spec.loader.exec_module(mf)


def cp(cmd, stdout="", stderr="", rc=0):
    return subprocess.CompletedProcess(cmd, rc, stdout, stderr)


def make_fake_git(listing, blobs, calls=None):
    """argv-bucketing fake for ``mf.git`` — ``ls-tree`` answers the canned
    listing, ``show <ref>:<path>`` answers the blob map, anything else
    fails loudly."""

    def fake_git(repo, *args):
        if calls is not None:
            calls.append([repo, *args])
        if args and args[0] == "ls-tree":
            return listing
        if args and args[0] == "show":
            _, _, path = args[1].partition(":")
            return blobs[path]
        raise AssertionError(f"unexpected argv: {args}")

    return fake_git


# ---------- strip_comments / stripped_size ----------


def test_strip_removes_line_comments():
    src = "int a; // gone\nint b;\n"
    assert mf.strip_comments(src) == "int a; \nint b;\n"


def test_strip_removes_block_and_javadoc_comments():
    src = "int a; /* gone */ int b;\n/** doc\n * more\n */\nint c;\n"
    assert mf.strip_comments(src) == "int a;  int b;\n\nint c;\n"


def test_strip_preserves_double_slash_inside_string_literal():
    src = 'String u = "http://x//y"; // real comment\n'
    assert mf.strip_comments(src) == 'String u = "http://x//y"; \n'


def test_strip_preserves_slash_inside_char_literal():
    src = "char c = '/'; // real comment\n"
    assert mf.strip_comments(src) == "char c = '/'; \n"


def test_strip_escaped_quote_does_not_end_string():
    src = 'String q = "a\\"b"; // c\n'
    assert mf.strip_comments(src) == 'String q = "a\\"b"; \n'


def test_strip_block_comment_adjacent_to_line_comment():
    # A `/* */` immediately followed by `//` strips both.
    src = "int a; /* block */ // line\nint b;\n"
    assert mf.strip_comments(src) == "int a;  \nint b;\n"


def test_strip_line_comment_at_eof_without_newline():
    src = "int a;\n// trailing comment with no newline"
    assert mf.strip_comments(src) == "int a;\n"


def test_strip_empty_input():
    assert mf.strip_comments("") == ""
    assert mf.stripped_size("") == 0


def test_comment_only_file_contributes_zero():
    assert mf.stripped_size("// only a comment\n/* and a block */\n") == 0


def test_stripped_size_counts_newline_per_kept_line():
    # Convention: every kept line counts its characters plus its newline
    # (wc -c of the comment-free text).
    assert mf.stripped_size("int x;\n") == 7
    assert mf.stripped_size("int x;\nint y;\n") == 14
    # Blank / whitespace-only lines after stripping are dropped.
    assert mf.stripped_size("int x;\n\n   \nint y;\n") == 14


def test_strip_fixture_exercises_every_branch():
    src = (FIXTURES / "strip_fixture.java").read_text()
    stripped = mf.strip_comments(src)
    assert '"http://example.com//tail"' in stripped
    assert "'/'" in stripped
    assert "leading line comment" not in stripped
    assert "Javadoc" not in stripped
    assert "adjacent" not in stripped
    assert "eof comment" not in stripped
    kept = [ln for ln in stripped.split("\n") if ln.strip()]
    assert kept == [
        "package shortestpath.strip;",
        "public class StripFixture {",
        '    String url = "http://example.com//tail"; ',
        "    char slash = '/';",
        "    int x;  ",
        "}",
    ]


# ---------- git() seam ----------


def test_git_invokes_subprocess_with_list_argv(monkeypatch):
    calls = {}

    def fake_run(cmd, **kwargs):
        calls["cmd"] = cmd
        calls.update(kwargs)
        return cp(cmd, stdout="ok\n")

    monkeypatch.setattr(mf.subprocess, "run", fake_run)
    assert mf.git("some-repo", "status") == "ok\n"
    assert calls["cmd"] == ["git", "-C", "some-repo", "status"]
    assert isinstance(calls["cmd"], list)
    assert calls["check"] is True
    assert calls["capture_output"] is True
    assert calls["text"] is True
    assert "shell" not in calls


def test_git_surfaces_stderr_on_failure(monkeypatch):
    def fake_run(cmd, **kwargs):
        raise subprocess.CalledProcessError(128, cmd, stderr="fatal: boom\n")

    monkeypatch.setattr(mf.subprocess, "run", fake_run)
    with pytest.raises(RuntimeError, match="fatal: boom"):
        mf.git("some-repo", "status")


# ---------- files_at_ref / measure / package_of ----------


def test_files_at_ref_filters_to_java(monkeypatch):
    listing = (FIXTURES / "measure_ls_tree.txt").read_text()
    calls = []
    monkeypatch.setattr(mf, "git", make_fake_git(listing, {}, calls))
    files = mf.files_at_ref("repo", "testref")
    assert files == [
        "src/main/java/shortestpath/alpha/BlobA.java",
        "src/main/java/shortestpath/beta/BlobB.java",
    ]
    assert calls == [
        ["repo", "ls-tree", "-r", "--name-only", "testref", "--",
         "src/main/java"]
    ]


def test_measure_reads_each_blob_at_ref(monkeypatch):
    listing = "src/main/java/shortestpath/A.java\n"
    blobs = {"src/main/java/shortestpath/A.java": "int a;\n"}
    calls = []
    monkeypatch.setattr(mf, "git", make_fake_git(listing, blobs, calls))
    assert mf.measure("repo", "ref1") == {
        "src/main/java/shortestpath/A.java": 7
    }
    assert calls[1] == [
        "repo", "show", "ref1:src/main/java/shortestpath/A.java"
    ]


def test_package_of_returns_dir_under_src_root():
    assert (
        mf.package_of("src/main/java/shortestpath/a/B.java")
        == "shortestpath/a"
    )
    assert (
        mf.package_of("src/main/java/shortestpath/C.java") == "shortestpath"
    )


def test_blob_fixtures_have_expected_stripped_sizes():
    # Anchors for the table-driven tests below — blob sizes must differ
    # so the size-desc order is observable.
    assert (
        mf.stripped_size((FIXTURES / "measure_blob_a.java").read_text())
        == 66
    )
    assert (
        mf.stripped_size((FIXTURES / "measure_blob_b.java").read_text())
        == 49
    )


# ---------- main() ----------


def test_main_writes_csv_and_prints_total(tmp_path, monkeypatch, capsys):
    blob_a = (FIXTURES / "measure_blob_a.java").read_text()
    blob_b = (FIXTURES / "measure_blob_b.java").read_text()
    listing = (FIXTURES / "measure_ls_tree.txt").read_text()
    blobs = {
        "src/main/java/shortestpath/alpha/BlobA.java": blob_a,
        "src/main/java/shortestpath/beta/BlobB.java": blob_b,
    }
    calls = []
    monkeypatch.setattr(mf, "git", make_fake_git(listing, blobs, calls))
    csv_path = tmp_path / "out.csv"
    monkeypatch.setattr(
        sys, "argv",
        ["measure_footprint.py", "--ref", "pin1234", "--csv",
         str(csv_path)])
    mf.main()

    rows = list(csv.reader(io.StringIO(csv_path.read_text())))
    assert rows[0] == ["path", "stripped_chars"]
    assert rows[1:] == [
        ["src/main/java/shortestpath/alpha/BlobA.java", "66"],
        ["src/main/java/shortestpath/beta/BlobB.java", "49"],
    ]
    last = capsys.readouterr().out.strip().splitlines()[-1]
    assert last == "TOTAL\t115\t2 files"
    # One ls-tree plus one show per .java file — the non-.java entry in
    # the listing is filtered before any blob read.
    assert calls[0][:2] == ["shortest-path", "ls-tree"]
    assert [c[1] for c in calls[1:]] == ["show", "show"]


def test_main_orders_size_desc_then_path_ascending(tmp_path, monkeypatch,
                                                   capsys):
    blob_a = (FIXTURES / "measure_blob_a.java").read_text()
    blob_b = (FIXTURES / "measure_blob_b.java").read_text()
    # A second blob of identical stripped size under a path that sorts
    # before the fixture's — equal sizes must break ties on path asc.
    listing = (
        "src/main/java/shortestpath/mmm/Big.java\n"
        "src/main/java/shortestpath/zzz/Last.java\n"
        "src/main/java/shortestpath/aaa/First.java\n"
    )
    blobs = {
        "src/main/java/shortestpath/mmm/Big.java": blob_a,
        "src/main/java/shortestpath/zzz/Last.java": blob_b,
        "src/main/java/shortestpath/aaa/First.java": blob_b,
    }
    monkeypatch.setattr(mf, "git", make_fake_git(listing, blobs))
    csv_path = tmp_path / "out.csv"
    monkeypatch.setattr(
        sys, "argv",
        ["measure_footprint.py", "--ref", "r", "--csv", str(csv_path)])
    mf.main()

    rows = list(csv.reader(io.StringIO(csv_path.read_text())))
    assert [r[0] for r in rows[1:]] == [
        "src/main/java/shortestpath/mmm/Big.java",
        "src/main/java/shortestpath/aaa/First.java",
        "src/main/java/shortestpath/zzz/Last.java",
    ]
    last = capsys.readouterr().out.strip().splitlines()[-1]
    assert last == "TOTAL\t164\t3 files"


def test_main_top_flag_limits_stdout_not_csv(tmp_path, monkeypatch,
                                              capsys):
    blob_a = (FIXTURES / "measure_blob_a.java").read_text()
    blob_b = (FIXTURES / "measure_blob_b.java").read_text()
    listing = (FIXTURES / "measure_ls_tree.txt").read_text()
    blobs = {
        "src/main/java/shortestpath/alpha/BlobA.java": blob_a,
        "src/main/java/shortestpath/beta/BlobB.java": blob_b,
    }
    monkeypatch.setattr(mf, "git", make_fake_git(listing, blobs))
    csv_path = tmp_path / "out.csv"
    monkeypatch.setattr(
        sys, "argv",
        ["measure_footprint.py", "--ref", "r", "--csv", str(csv_path),
         "--top", "1"])
    mf.main()

    out = capsys.readouterr().out
    shown = [ln for ln in out.splitlines() if "BlobA" in ln
             or "BlobB" in ln]
    # --top limits the printed table but the CSV still gets every row.
    assert len(shown) == 1 and "BlobA" in shown[0]
    rows = list(csv.reader(io.StringIO(csv_path.read_text())))
    assert len(rows) == 3
