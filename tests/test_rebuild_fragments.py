"""Tests for ``scripts/rebuild_fragments.py``.

Mirrors ``test_attribute_deletions.py``: every git call goes through the
module's single ``git`` seam, monkeypatched with an argv-bucketing fake —
no test touches the real submodule or the network.  The script is loaded
via importlib because ``scripts/`` has no ``__init__.py``.  Fixtures are
``=== key ===`` sectioned files under ``tests/fixtures/``.
"""

import importlib.util
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "rebuild_fragments.py"
FIXTURES = Path(__file__).resolve().parent / "fixtures"

spec = importlib.util.spec_from_file_location(
    "rebuild_fragments", SCRIPT_PATH)
rf = importlib.util.module_from_spec(spec)
sys.modules["rebuild_fragments"] = rf
spec.loader.exec_module(rf)

FILE_F = "src/main/java/x/F.java"
FILE_G = "src/main/java/x/G.java"


def cp(cmd, stdout="", stderr="", rc=0):
    return subprocess.CompletedProcess(cmd, rc, stdout, stderr)


def load_sections(name):
    """Parse `=== key ===` sections of a fixture into {key: text}."""
    out = {}
    cur = None
    for line in (FIXTURES / name).read_text().splitlines(keepends=True):
        stripped = line.strip()
        if stripped.startswith("=== ") and stripped.endswith(" ==="):
            cur = stripped[4:-4].strip()
            out[cur] = ""
        elif cur is not None:
            out[cur] += line
    return out


def make_fake_git(commits, calls=None):
    """argv-bucketing fake for ``rf.git``.

    ``rev-list`` answers the commit list; ``show -U0 <sha>`` the diff
    fixture section keyed by sha; ``show --format= --name-only <sha>``
    the ``name_only_<sha>`` section; ``show <sha>^:<path>`` the blob
    section.  Anything else fails loudly."""
    diffs = load_sections("rebuild_show_u0.diff")
    blobs = load_sections("rebuild_blob.java")

    def fake_git(repo, *args):
        if calls is not None:
            calls.append([repo, *args])
        if args and args[0] == "rev-list":
            return "".join(f"{sha}\t{subj}\n" for sha, subj in commits)
        if args and args[0] == "show":
            joined = [a for a in args if ":" in a]
            if joined and joined[0].endswith(tuple(blobs.keys())
                                             ) is False and False:
                pass
            # `show <sha>^:<path>` — blob read
            for a in args:
                if a in blobs:
                    return blobs[a]
            # `show --format= --name-only <sha>` — companion path list
            if "--name-only" in args:
                sha = args[-1]
                return diffs.get(f"name_only_{sha}", "")
            # `show --format= -U0 <sha> -- <pathspec>` — the src/main diff
            if "-U0" in args:
                return diffs[args[3]]
            return ""
        raise AssertionError(f"unexpected argv: {args}")

    return fake_git


COMMITS = [
    ("aaaa1111", "refactor(x): commit a subject"),
    ("bbbb2222", "test(x): commit b subject"),
    ("eeee5555", "chore(x): commit e subject"),
]


def ledger_path(name="ledger_a"):
    sections = load_sections("rebuild_ledger.txt")
    import tempfile
    f = tempfile.NamedTemporaryFile(
        "w", suffix=".md", delete=False)
    f.write(sections[name])
    f.close()
    return f.name


# ---------- git() seam ----------


def test_git_invokes_subprocess_with_list_argv(monkeypatch):
    calls = {}

    def fake_run(cmd, **kwargs):
        calls["cmd"] = cmd
        calls.update(kwargs)
        return cp(cmd, stdout="ok\n")

    monkeypatch.setattr(rf.subprocess, "run", fake_run)
    assert rf.git("some-repo", "status") == "ok\n"
    assert calls["cmd"] == ["git", "-C", "some-repo", "status"]
    assert isinstance(calls["cmd"], list)
    assert calls["check"] is True
    assert calls["capture_output"] is True
    assert calls["text"] is True
    assert "shell" not in calls


def test_git_surfaces_stderr_on_failure(monkeypatch):
    def fake_run(cmd, **kwargs):
        raise subprocess.CalledProcessError(128, cmd, stderr="fatal: boom\n")

    monkeypatch.setattr(rf.subprocess, "run", fake_run)
    with pytest.raises(RuntimeError, match="fatal: boom"):
        rf.git("some-repo", "status")


# ---------- parse_ledger ----------


def test_parse_ledger_groups_rows_by_sha_section():
    sections = load_sections("rebuild_ledger.txt")
    rows = rf.parse_ledger(sections["ledger_a"])
    assert rows == {
        "aaaa1111": [
            (FILE_F, 10, 12, "v1.22.2"),
            (FILE_F, 13, 14, "v1.22.4"),
            (FILE_F, 40, 40, "v1.22.2"),
            (FILE_G, 50, 51, "v1.22.2"),
            (FILE_G, 60, 60, "v1.22.2"),
        ],
        "bbbb2222": [],
    }


def test_parse_ledger_ignores_prose_and_non_sha_headers():
    sections = load_sections("rebuild_ledger.txt")
    rows = rf.parse_ledger(sections["ledger_a"])
    assert "Tracer" not in rows


# ---------- parse_show_u0_hunks ----------


def test_parse_show_u0_hunks_captures_runs_and_plus_lines():
    diffs = load_sections("rebuild_show_u0.diff")
    files = rf.parse_show_u0_hunks(diffs["aaaa1111"])
    assert [f["file"] for f in files] == [FILE_F, FILE_G]
    f_hunks = files[0]["hunks"]
    assert f_hunks[0]["minus"] == [(10, 12), (13, 14)]
    assert f_hunks[0]["plus"] == ["add1"]
    assert f_hunks[1]["minus"] == [(40, 40)]
    assert f_hunks[1]["plus"] == []
    g_hunks = files[1]["hunks"]
    assert [h["minus"] for h in g_hunks] == [[(50, 51)], [(60, 60)]]


def test_parse_show_u0_hunks_empty_diff():
    assert rf.parse_show_u0_hunks("") == []


# ---------- generate() ----------


def test_generate_emits_fragments_wholesale_and_stats(monkeypatch):
    monkeypatch.setattr(rf, "git", make_fake_git(COMMITS))
    doc = rf.generate("repo", "base", "branch", [ledger_path()])

    frags = doc["fragments"]
    assert len(frags) == 3

    # hunk with rows split .2/.4 emits ONE fragment at max segment
    f4 = frags[0]
    assert f4["file"] == FILE_F and f4["segment"] == "v1.22.4"
    assert f4["ordinal"] == "1/3"
    assert [r["lines"] for r in f4["minus_runs"]] == ["10-12", "13-14"]
    assert f4["minus_runs"][0]["text"] == "del10\ndel11\ndel12\n"
    assert f4["minus_runs"][0]["context_before"] == "l8\nl9\n"
    assert f4["minus_runs"][0]["context_after"] == "del13\ndel14\n"
    assert f4["plus_text"] == "add1\n"
    # companions ride the max-segment fragment
    assert f4["companions"] == [
        "src/test/java/x/FTest.java", "build.gradle"]
    assert f4["commit"] == "aaaa1111"
    assert f4["subject"] == "refactor(x): commit a subject"

    f2 = frags[1]
    assert f2["file"] == FILE_F and f2["segment"] == "v1.22.2"
    assert f2["ordinal"] == "2/3" and f2["plus_text"] == ""
    assert f2["companions"] == []

    # contiguous same-segment hunks cluster into one fragment
    g = frags[2]
    assert g["file"] == FILE_G and g["segment"] == "v1.22.2"
    assert g["ordinal"] == "3/3"
    assert [r["lines"] for r in g["minus_runs"]] == ["50-51", "60"]

    # zero-row commits route wholesale
    shas = {w["sha"] for w in doc["wholesale"]}
    assert shas == {"bbbb2222", "eeee5555"}
    b = next(w for w in doc["wholesale"] if w["sha"] == "bbbb2222")
    assert b["paths"] == ["src/test/java/x/FTest.java", "docs/x.md"]

    # a wholesale commit with unattributed src/main deletions is flagged
    assert doc["unattributed_deletions"] == [{
        "sha": "eeee5555", "subject": "chore(x): commit e subject",
        "files": ["src/main/java/x/H.java"],
    }]

    # coverage stats: every parsed row counted by recorded segment
    assert doc["stats"]["rows_per_segment"] == {
        "v1.22.2": 4, "v1.22.4": 1}
    assert doc["stats"]["fragments_per_segment"] == {
        "v1.22.2": 2, "v1.22.4": 1}


def test_generate_fails_on_row_without_containing_hunk(monkeypatch):
    commits = [("cccc3333", "refactor(x): commit c subject")]
    monkeypatch.setattr(rf, "git", make_fake_git(commits))
    with pytest.raises(RuntimeError, match="no containing hunk|disagree"):
        rf.generate("repo", "base", "branch", [ledger_path("ledger_missing_sha")])


def test_generate_empty_diff_commit_emits_no_fragments(monkeypatch):
    commits = [("bbbb2222", "test(x): commit b subject")]
    monkeypatch.setattr(rf, "git", make_fake_git(commits))
    doc = rf.generate(
        "repo", "base", "branch", [ledger_path("ledger_empty")])
    assert doc["fragments"] == []
    assert doc["wholesale"] == [
        {"sha": "bbbb2222", "subject": "test(x): commit b subject",
         "paths": ["src/test/java/x/FTest.java", "docs/x.md"]}]


def test_generate_records_exact_argv(monkeypatch):
    calls = []
    monkeypatch.setattr(rf, "git", make_fake_git(COMMITS, calls=calls))
    rf.generate("repo", "base", "branch", [ledger_path()])
    assert calls[0] == [
        "repo", "rev-list", "--reverse", "--no-commit-header",
        "--format=%H%x09%s", "base..branch"]
    u0 = [c for c in calls if "-U0" in c]
    assert ["repo", "show", "--format=", "-U0", "aaaa1111", "--",
            "src/main/java/**"] in u0
    blob = [c for c in calls if any(":" in a for a in c[2:])]
    assert ["repo", "show", "aaaa1111^:" + FILE_F] in blob
    assert ["repo", "show", "aaaa1111^:" + FILE_G] in blob
