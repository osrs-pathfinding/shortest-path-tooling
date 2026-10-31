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


def make_fake_git_multi(listings, blobs, calls=None):
    """ref-keyed fake for ``mt.mf.git`` — ``ls-tree`` answers
    ``listings[ref]``, ``show <ref>:<path>`` answers
    ``blobs[(ref, path)]``, an unknown ref raises ``RuntimeError`` like
    the real ``git`` seam does, anything else fails loudly."""

    def fake_git(repo, *args):
        if calls is not None:
            calls.append([repo, *args])
        if args and args[0] == "ls-tree":
            ref = args[3]
            if ref not in listings:
                raise RuntimeError(
                    f"git -C {repo} ls-tree -r --name-only {ref} failed "
                    f"with exit code 128:\nfatal: bad revision '{ref}'")
            return listings[ref]
        if args and args[0] == "show":
            ref, _, path = args[1].partition(":")
            return blobs[(ref, path)]
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


# ---------- multi-ref --ref a,b,c ----------


def _multi_blobs():
    """Two refs with observably different token totals."""
    blob_a = (FIXTURES / "measure_blob_a.java").read_text()
    blob_b = (FIXTURES / "measure_blob_b.java").read_text()
    listing = (FIXTURES / "measure_ls_tree.txt").read_text()
    path_a = "src/main/java/shortestpath/alpha/BlobA.java"
    path_b = "src/main/java/shortestpath/beta/BlobB.java"
    listings = {"ref1": listing, "ref2": listing}
    blobs = {
        ("ref1", path_a): blob_a,
        ("ref1", path_b): blob_b,
        ("ref2", path_a): blob_b,
        ("ref2", path_b): blob_a,
    }
    return listings, blobs


def test_main_multi_ref_tags_each_ref_block(tmp_path, monkeypatch, capsys):
    listings, blobs = _multi_blobs()
    calls = []
    monkeypatch.setattr(
        mt.mf, "git", make_fake_git_multi(listings, blobs, calls))
    monkeypatch.setattr(
        sys, "argv", ["measure_tokens.py", "--ref", "ref1,ref2"])
    mt.main()

    out = capsys.readouterr().out
    assert "# ref ref1" in out
    assert "# ref ref2" in out
    totals = [ln for ln in out.splitlines() if ln.startswith("TOTAL")]
    assert len(totals) == 2
    assert all("2 files" in ln for ln in totals)


def test_main_multi_ref_csv_carries_ref_column_in_arg_order(
        tmp_path, monkeypatch, capsys):
    listings, blobs = _multi_blobs()
    calls = []
    monkeypatch.setattr(
        mt.mf, "git", make_fake_git_multi(listings, blobs, calls))
    csv_path = tmp_path / "out.csv"
    monkeypatch.setattr(
        sys, "argv",
        ["measure_tokens.py", "--ref", "ref2,ref1", "--csv",
         str(csv_path)])
    mt.main()
    capsys.readouterr()

    rows = list(csv.reader(io.StringIO(csv_path.read_text())))
    assert rows[0] == ["ref", "path", "tokens"]
    # ref-major ordering in argument order (ref2 first), token-desc inside.
    assert [r[0] for r in rows[1:]] == ["ref2", "ref2", "ref1", "ref1"]
    path_a = "src/main/java/shortestpath/alpha/BlobA.java"
    path_b = "src/main/java/shortestpath/beta/BlobB.java"
    expected = {
        ("ref2", path_a): _stripped_tokens(
            ENC, blobs[("ref2", path_a)]),
        ("ref2", path_b): _stripped_tokens(
            ENC, blobs[("ref2", path_b)]),
        ("ref1", path_a): _stripped_tokens(
            ENC, blobs[("ref1", path_a)]),
        ("ref1", path_b): _stripped_tokens(
            ENC, blobs[("ref1", path_b)]),
    }
    for ref, path, tokens in rows[1:]:
        assert int(tokens) == expected[(ref, path)]
    # size-desc inside each ref's run: blob_a (66 stripped chars) outranks
    # blob_b (49) whichever path carries it.
    assert rows[1][1] == path_b
    assert rows[3][1] == path_a


def test_main_multi_ref_reads_src_main_java_blobs_per_ref(
        tmp_path, monkeypatch, capsys):
    listings, blobs = _multi_blobs()
    calls = []
    monkeypatch.setattr(
        mt.mf, "git", make_fake_git_multi(listings, blobs, calls))
    monkeypatch.setattr(
        sys, "argv", ["measure_tokens.py", "--ref", "ref1,ref2"])
    mt.main()
    capsys.readouterr()

    lstrees = [c for c in calls if c[1] == "ls-tree"]
    assert [c[4] for c in lstrees] == ["ref1", "ref2"]
    for c in lstrees:
        assert c[6] == "src/main/java"
    shows = [c[2] for c in calls if c[1] == "show"]
    assert all(
        s.split(":", 1)[0] in ("ref1", "ref2")
        and s.split(":", 1)[1].startswith("src/main/java/")
        for s in shows)


def test_main_multi_ref_bad_ref_exits_nonzero(
        tmp_path, monkeypatch, capsys):
    listings, blobs = _multi_blobs()
    monkeypatch.setattr(
        mt.mf, "git", make_fake_git_multi(listings, blobs))
    csv_path = tmp_path / "out.csv"
    monkeypatch.setattr(
        sys, "argv",
        ["measure_tokens.py", "--ref", "ref1,bogus", "--csv",
         str(csv_path)])
    with pytest.raises(SystemExit):
        mt.main()
    captured = capsys.readouterr()
    assert "bogus" in captured.err
    assert captured.out == ""
    assert not csv_path.exists()


# ---------- empty measurement ----------


def test_measure_rejects_a_ref_with_no_java_files(monkeypatch):
    monkeypatch.setattr(mt.mf, "git", make_fake_git("", {}))
    with pytest.raises(RuntimeError, match="no src/main/java/.*at ref1"):
        mt.measure("repo", "ref1", ENC)


def test_main_empty_measurement_exits_nonzero(tmp_path, monkeypatch, capsys):
    monkeypatch.setattr(mt.mf, "git", make_fake_git("", {}))
    csv_path = tmp_path / "out.csv"
    monkeypatch.setattr(
        sys, "argv",
        ["measure_tokens.py", "--ref", "ref1", "--csv", str(csv_path)])
    with pytest.raises(SystemExit) as exc:
        mt.main()
    assert exc.value.code != 0
    captured = capsys.readouterr()
    assert "no src/main/java" in captured.err
    assert "ref1" in captured.err
    assert captured.out == ""
    assert not csv_path.exists()
