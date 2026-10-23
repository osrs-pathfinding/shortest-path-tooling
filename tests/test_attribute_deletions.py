"""Tests for ``scripts/attribute_deletions.py``.

Every git call goes through the module's single ``git`` seam, so tests
monkeypatch ``ad.git`` (or ``ad.subprocess.run`` for the seam itself)
with argv-bucketing fakes — no test touches the real submodule or the
network.  The script is loaded via importlib because ``scripts/`` has no
``__init__.py``.
"""

import importlib.util
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "attribute_deletions.py"
FIXTURES = Path(__file__).resolve().parent / "fixtures"

spec = importlib.util.spec_from_file_location(
    "attribute_deletions", SCRIPT_PATH)
ad = importlib.util.module_from_spec(spec)
sys.modules["attribute_deletions"] = ad
spec.loader.exec_module(ad)

TAGS = ["v1.22.2", "v1.22.3", "v1.22.4", "v1.22.5"]
POST = "post-v1.22.5"

FILE_A = "src/main/java/shortestpath/alpha/FileA.java"
FILE_B = "src/main/java/shortestpath/beta/FileB.java"
SHA_LATE = "4f3a2b1c4f3a2b1c4f3a2b1c4f3a2b1c4f3a2b1c"  # earliest tag v1.22.4
SHA_EARLY = "0de5c0de0de5c0de0de5c0de0de5c0de0de5c0de"  # earliest tag v1.22.2
SHA_POST = "beefbeefbeefbeefbeefbeefbeefbeefbeefbeef"  # in no release tag


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


def attr_kind(args):
    """Bucket one git(argv) call into the pipeline step it performs."""
    if args and args[0] == "log":
        return "subject"
    if args and args[0] == "show":
        return "diff"
    if args and args[0] == "blame":
        return "blame"
    if args and args[0] == "tag":
        return "tag"
    return f"other:{args}"


def make_fake_git(calls=None, diff_text=None, subject="cafebabe smoke commit"):
    """argv-bucketing fake for ``ad.git`` — ``log`` answers the canned
    subject line, ``show -U0`` the diff fixture, ``blame --porcelain``
    the per-file porcelain sections, ``tag --contains`` the per-sha tag
    lists.  Anything else fails loudly."""
    diff = (FIXTURES / "attr_show_u0.diff").read_text() \
        if diff_text is None else diff_text
    blame = load_sections("attr_blame_porcelain.txt")
    tags = load_sections("attr_tag_contains.txt")

    def fake_git(repo, *args):
        if calls is not None:
            calls.append([repo, *args])
        kind = attr_kind(args)
        if kind == "subject":
            return subject + "\n"
        if kind == "diff":
            return diff
        if kind == "blame":
            return blame[args[-1]]
        if kind == "tag":
            return tags[args[-1]]
        raise AssertionError(f"unexpected argv: {args}")

    return fake_git


# ---------- git() seam ----------


def test_git_invokes_subprocess_with_list_argv(monkeypatch):
    calls = {}

    def fake_run(cmd, **kwargs):
        calls["cmd"] = cmd
        calls.update(kwargs)
        return cp(cmd, stdout="ok\n")

    monkeypatch.setattr(ad.subprocess, "run", fake_run)
    assert ad.git("some-repo", "status") == "ok\n"
    assert calls["cmd"] == ["git", "-C", "some-repo", "status"]
    assert isinstance(calls["cmd"], list)
    assert calls["check"] is True
    assert calls["capture_output"] is True
    assert calls["text"] is True
    assert "shell" not in calls


# ---------- parse_show_u0 ----------


def test_parse_show_u0_collects_contiguous_runs_per_file():
    rows = ad.parse_show_u0((FIXTURES / "attr_show_u0.diff").read_text())
    assert rows == [
        (FILE_A, [(10, 12), (40, 40)]),
        (FILE_B, [(7, 7)]),
    ]


def test_parse_show_u0_keeps_disjoint_runs_separate_within_a_hunk():
    diff = (
        "diff --git a/src/main/java/x/F.java b/src/main/java/x/F.java\n"
        "@@ -5,3 +5,2 @@\n"
        "-a\n+x\n-b\n-c\n+y\n"
    )
    assert ad.parse_show_u0(diff) == [
        ("src/main/java/x/F.java", [(5, 5), (6, 7)])
    ]


def test_parse_show_u0_empty_diff_yields_no_rows():
    assert ad.parse_show_u0("") == []


# ---------- parse_blame_porcelain ----------


def test_parse_blame_porcelain_maps_final_lines_to_shas():
    sections = load_sections("attr_blame_porcelain.txt")
    line_sha = ad.parse_blame_porcelain(sections[FILE_A])
    assert line_sha.get(10) == SHA_EARLY
    assert line_sha.get(11) == SHA_EARLY
    assert line_sha.get(12) == SHA_LATE
    assert line_sha.get(40) == SHA_EARLY


# ---------- attribute() pipeline ----------


def test_attribute_rolls_mixed_era_run_up_to_max_bucket(monkeypatch):
    monkeypatch.setattr(ad, "git", make_fake_git())
    rows = ad.attribute("repo", "c0ffee", TAGS, POST)
    assert rows == [
        (FILE_A, 10, 12, "v1.22.4"),  # sha2/sha2/sha1 -> max bucket
        (FILE_A, 40, 40, "v1.22.2"),  # sha2 -> earliest containing tag
        (FILE_B, 7, 7, POST),         # sha3 in no tag -> post lane
    ]


def test_attribute_blames_parent_and_separates_pathspecs(monkeypatch):
    calls = []
    monkeypatch.setattr(ad, "git", make_fake_git(calls=calls))
    ad.attribute("repo", "c0ffee", TAGS, POST)

    assert calls
    show = calls[0]
    assert show[:5] == ["repo", "show", "--format=", "-U0", "c0ffee"]
    assert show[5] == "--"
    assert show[6:] == ["src/main/java/**"]

    blame_calls = [c for c in calls if c[1:2] == ["blame"]]
    assert blame_calls == [
        ["repo", "blame", "--porcelain", "c0ffee^", "--", FILE_A],
        ["repo", "blame", "--porcelain", "c0ffee^", "--", FILE_B],
    ]

    tag_calls = [c for c in calls if c[1:2] == ["tag"]]
    assert tag_calls == [
        ["repo", "tag", "--contains", SHA_EARLY],
        ["repo", "tag", "--contains", SHA_LATE],
        ["repo", "tag", "--contains", SHA_POST],
    ]


# ---------- main() ----------


def test_main_prints_legend_then_tsv_rows(monkeypatch, capsys):
    monkeypatch.setattr(ad, "git", make_fake_git())
    monkeypatch.setattr(
        sys, "argv", ["attribute_deletions.py", "--commit", "c0ffee"])
    assert ad.main() == 0
    out = capsys.readouterr().out.splitlines()
    assert len(out) == 6
    assert out[0] == "# attribution cafebabe smoke commit"
    assert out[1] == "# file\tparent-lines\tsegment"
    assert out[2] == \
        "# blame=last-touch; mixed-era hunks roll up by max-bucket"
    assert out[3:] == [
        f"{FILE_A}\t10-12\tv1.22.4",
        f"{FILE_A}\t40\tv1.22.2",
        f"{FILE_B}\t7\tpost-v1.22.5",
    ]


def test_main_zero_deletions_prints_header_only(monkeypatch, capsys):
    monkeypatch.setattr(ad, "git", make_fake_git(diff_text=""))
    monkeypatch.setattr(
        sys, "argv", ["attribute_deletions.py", "--commit", "c0ffee"])
    assert ad.main() == 0
    lines = capsys.readouterr().out.splitlines()
    assert len(lines) == 3
    assert all(l.startswith("#") for l in lines)
