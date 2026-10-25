"""Tests for ``scripts/measure_tokens.py``.

The script importlib-loads ``measure_footprint.py`` (``mt.mf``) so every
git call flows through the same single ``git`` seam — tests monkeypatch
``mt.mf.git`` with argv-bucketing fakes, exactly like
``test_measure_footprint.py``.  No test touches the real submodule or the
network.  The module is loaded via importlib because ``scripts/`` has no
``__init__.py``.

The whole module is skipped when ``tiktoken`` is absent — the suite must
never hard-fail pre-install.
"""

import csv
import importlib.util
import io
import sys
from pathlib import Path

import pytest

tiktoken = pytest.importorskip("tiktoken")

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "measure_tokens.py"
FIXTURES = Path(__file__).resolve().parent / "fixtures"

spec = importlib.util.spec_from_file_location("measure_tokens", SCRIPT_PATH)
mt = importlib.util.module_from_spec(spec)
sys.modules["measure_tokens"] = mt
spec.loader.exec_module(mt)

ENC = tiktoken.get_encoding("cl100k_base")


def make_fake_git(listing, blobs, calls=None):
    """argv-bucketing fake for ``mt.mf.git`` — ``ls-tree`` answers the
    canned listing, ``show <ref>:<path>`` answers the blob map, anything
    else fails loudly."""

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


def _stripped_tokens(enc, text):
    return len(enc.encode(mt.mf.strip_comments(text)))


# ---------- measure ----------


def test_measure_counts_tokens_on_stripped_text(monkeypatch):
    listing = "src/main/java/shortestpath/A.java\n"
    blob = "int a; // gone\nint b;\n"
    blobs = {"src/main/java/shortestpath/A.java": blob}
    calls = []
    monkeypatch.setattr(mt.mf, "git", make_fake_git(listing, blobs, calls))
    counts = mt.measure("repo", "ref1", ENC)
    expected = _stripped_tokens(ENC, blob)
    assert counts == {"src/main/java/shortestpath/A.java": expected}
    # Comment removal must shrink the token count, not just the chars.
    assert expected < len(ENC.encode(blob))
    assert calls[1] == [
        "repo", "show", "ref1:src/main/java/shortestpath/A.java"
    ]


def test_measure_filters_to_java_paths(monkeypatch):
    listing = (
        "src/main/java/shortestpath/A.java\n"
        "src/main/resources/data.tsv\n"
    )
    blobs = {"src/main/java/shortestpath/A.java": "int a;\n"}
    monkeypatch.setattr(mt.mf, "git", make_fake_git(listing, blobs))
    counts = mt.measure("repo", "r", ENC)
    assert list(counts) == ["src/main/java/shortestpath/A.java"]


def test_strip_fixture_tokens_fewer_stripped_than_raw():
    raw = (FIXTURES / "strip_fixture.java").read_text()
    raw_tokens = len(ENC.encode(raw))
    stripped_tokens = _stripped_tokens(ENC, raw)
    assert 0 < stripped_tokens < raw_tokens


# ---------- main() ----------


def _fixture_blobs():
    blob_a = (FIXTURES / "measure_blob_a.java").read_text()
    blob_b = (FIXTURES / "measure_blob_b.java").read_text()
    listing = (FIXTURES / "measure_ls_tree.txt").read_text()
    blobs = {
        "src/main/java/shortestpath/alpha/BlobA.java": blob_a,
        "src/main/java/shortestpath/beta/BlobB.java": blob_b,
    }
    return listing, blobs, blob_a, blob_b


def test_main_prints_total_and_writes_csv(tmp_path, monkeypatch, capsys):
    listing, blobs, blob_a, blob_b = _fixture_blobs()
    calls = []
    monkeypatch.setattr(mt.mf, "git", make_fake_git(listing, blobs, calls))
    csv_path = tmp_path / "out.csv"
    monkeypatch.setattr(
        sys, "argv",
        ["measure_tokens.py", "--ref", "r", "--csv", str(csv_path)])
    mt.main()

    out = capsys.readouterr().out
    total_a = _stripped_tokens(ENC, blob_a)
    total_b = _stripped_tokens(ENC, blob_b)
    last = out.strip().splitlines()[-1]
    assert last == f"TOTAL\t{total_a + total_b} tokens\t2 files"
    assert "# package rollup" in out

    rows = list(csv.reader(io.StringIO(csv_path.read_text())))
    assert rows[0] == ["path", "tokens"]
    expected = sorted(
        [("src/main/java/shortestpath/alpha/BlobA.java", total_a),
         ("src/main/java/shortestpath/beta/BlobB.java", total_b)],
        key=lambda kv: (-kv[1], kv[0]))
    assert rows[1:] == [[p, str(t)] for p, t in expected]
    # One ls-tree plus one show per .java file — the non-.java entry in
    # the listing is filtered before any blob read.
    assert calls[0][:2] == ["shortest-path", "ls-tree"]
    assert [c[1] for c in calls[1:]] == ["show", "show"]


def test_main_top_flag_limits_stdout_not_csv(tmp_path, monkeypatch, capsys):
    listing, blobs, *_ = _fixture_blobs()
    monkeypatch.setattr(mt.mf, "git", make_fake_git(listing, blobs))
    csv_path = tmp_path / "out.csv"
    monkeypatch.setattr(
        sys, "argv",
        ["measure_tokens.py", "--ref", "r", "--csv", str(csv_path),
         "--top", "1"])
    mt.main()

    out = capsys.readouterr().out
    shown = [ln for ln in out.splitlines() if ".java" in ln]
    # --top limits the printed table but the CSV still gets every row.
    assert len(shown) == 1
    rows = list(csv.reader(io.StringIO(csv_path.read_text())))
    assert len(rows) == 3


def test_main_rejects_negative_top(monkeypatch):
    monkeypatch.setattr(
        sys, "argv", ["measure_tokens.py", "--top", "-1"])
    with pytest.raises(SystemExit):
        mt.main()
