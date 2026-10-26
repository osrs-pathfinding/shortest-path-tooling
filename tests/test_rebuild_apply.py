"""Tests for ``scripts/rebuild_apply.py``.

Mirrors ``test_rebuild_fragments.py``: git and gradle calls go through
the module's single ``git``/``gradle`` seams, monkeypatched with
argv-bucketing fakes — no test touches the real submodule, the rebuild
worktree, or the network.  The script is loaded via importlib because
``scripts/`` has no ``__init__.py``.  Fixtures are ``=== key ===``
sectioned files plus ``tests/fixtures/rebuild_apply_state.json``.
"""

import importlib.util
import json
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "rebuild_apply.py"
FIXTURES = Path(__file__).resolve().parent / "fixtures"

spec = importlib.util.spec_from_file_location(
    "rebuild_apply", SCRIPT_PATH)
ra = importlib.util.module_from_spec(spec)
sys.modules["rebuild_apply"] = ra
spec.loader.exec_module(ra)

FILE_F = "src/main/java/x/F.java"


def cp(cmd, stdout="", stderr="", rc=0):
    return subprocess.CompletedProcess(cmd, rc, stdout, stderr)


def load_state_fixture():
    return json.loads((FIXTURES / "rebuild_apply_state.json").read_text())


def make_fragment(**over):
    frag = {
        "id": "aaaa1111-1of2",
        "commit": "aaaa1111",
        "subject": "refactor(x): commit a subject",
        "ordinal": "1/2",
        "file": FILE_F,
        "segment": "v1.22.2",
        "minus_runs": [],
        "plus_text": "",
        "companions": [],
    }
    frag.update(over)
    return frag


def run_spec(lines, text, before="", after=""):
    return {"lines": lines, "text": text,
            "context_before": before, "context_after": after}


# ---------- parse_range / cluster_runs ----------


def test_parse_range_single_and_span():
    assert ra.parse_range("40") == (40, 40)
    assert ra.parse_range("10-12") == (10, 12)


def test_cluster_runs_groups_contiguous_ranges_into_one_hunk():
    # [10-12] then [13-14] tile parent lines 10-14 contiguously — one hunk.
    runs = [run_spec("10-12", "a\nb\nc\n"), run_spec("13-14", "d\ne\n")]
    groups = ra.cluster_runs(runs)
    assert len(groups) == 1
    assert groups[0] == runs


def test_cluster_runs_splits_on_gaps():
    runs = [run_spec("10-12", "a\nb\nc\n"),
            run_spec("13-14", "d\ne\n"),
            run_spec("50-51", "x\ny\n")]
    groups = ra.cluster_runs(runs)
    assert [len(g) for g in groups] == [2, 1]


# ---------- anchored locate ----------


def test_locate_unique_match():
    content = "one\ntwo\nthree\nfour\n"
    assert ra.locate_positions(content, "two\nthree\n") == [4]


def test_locate_zero_and_multiple():
    content = "a\nx\nb\nx\nc\n"
    assert ra.locate_positions(content, "zz\n") == []
    assert ra.locate_positions(content, "x\n") == [2, 6]


def test_locate_with_context_disambiguates():
    # 'x\n' occurs twice; pinning the spec's context lines picks one.
    content = "p\nx\nq\n" + "m\nx\nn\n"
    positions = ra.locate_positions(content, "m\nx\nn\n")
    assert positions == [6]


def test_resolve_match_unique_context_and_conflict():
    # unique on text alone
    r = ra.resolve_match("aa\nbb\ncc\n", "bb\n", "", "")
    assert r["status"] == "found" and r["count"] == 1
    # ambiguous on text, unique once context pinned
    content = "q\nx\nw\n" + "m\nx\nn\n"
    r = ra.resolve_match(content, "x\n", "m\n", "n\n")
    assert r["status"] == "found" and r["count"] == 1
    # still ambiguous after context — conflict
    content = "x\nx\n"
    r = ra.resolve_match(content, "x\n", "", "")
    assert r["status"] == "ambiguous" and r["count"] == 2
    # absent — caller decides already-applied vs conflict
    r = ra.resolve_match("a\nb\n", "zz\n", "", "")
    assert r["status"] == "absent" and r["count"] == 0


# ---------- per-hunk plus assignment ----------


def test_split_plus_per_hunk_single_group_takes_all():
    frag = make_fragment(
        minus_runs=[run_spec("10-12", "a\nb\nc\n")],
        plus_text="x\ny\n")
    groups = ra.cluster_runs(frag["minus_runs"])
    plus = ra.split_plus_per_hunk(frag, groups, diff_hunks=None)
    assert plus == ["x\ny\n"]


def test_split_plus_per_hunk_multi_group_uses_diff():
    frag = make_fragment(
        minus_runs=[run_spec("10-12", "a\nb\nc\n"),
                    run_spec("50-51", "m\nn\n")],
        plus_text="x\ny\n")
    diff_hunks = [
        {"minus": [(10, 12)], "plus": ["x"]},
        {"minus": [(50, 51)], "plus": ["y"]},
    ]
    groups = ra.cluster_runs(frag["minus_runs"])
    plus = ra.split_plus_per_hunk(frag, groups, diff_hunks=diff_hunks)
    assert plus == ["x\n", "y\n"]


def test_split_plus_per_hunk_mismatch_fails():
    frag = make_fragment(
        minus_runs=[run_spec("10-12", "a\nb\nc\n"),
                    run_spec("50-51", "m\nn\n")],
        plus_text="x\n")
    diff_hunks = [{"minus": [(10, 12)], "plus": ["x"]},
                  {"minus": [(99, 99)], "plus": []}]
    groups = ra.cluster_runs(frag["minus_runs"])
    with pytest.raises(RuntimeError, match="hunk"):
        ra.split_plus_per_hunk(frag, groups, diff_hunks=diff_hunks)


# ---------- hunk status / idempotent skip ----------


def test_hunk_status_pending():
    st = ra.hunk_status(
        content="keep\nDEL\nme\n", old="DEL\n", plus="",
        era_content="keep\nDEL\nme\n")
    assert st == "pending"


def test_hunk_status_already_applied_pure_deletion():
    # old absent now + present at the era base + empty plus → applied.
    st = ra.hunk_status(
        content="keep\nme\n", old="DEL\n", plus="",
        era_content="keep\nDEL\nme\n")
    assert st == "already-applied"


def test_hunk_status_conflict_when_never_present():
    # absent now AND absent at the era base — spec/divergence, never fuzzy.
    st = ra.hunk_status(
        content="keep\nme\n", old="DEL\n", plus="",
        era_content="keep\nme\n")
    assert st == "conflict"


def test_hunk_status_already_applied_with_plus_present():
    st = ra.hunk_status(
        content="keep\nNEW\nme\n", old="DEL\n", plus="NEW\n",
        era_content="keep\nDEL\nme\n")
    assert st == "already-applied"


def test_hunk_status_conflict_when_deleted_without_plus():
    # old deleted but the replacement never landed — a half-applied hunk
    # is a conflict record, never a silent skip.
    st = ra.hunk_status(
        content="keep\nme\n", old="DEL\n", plus="NEW\n",
        era_content="keep\nDEL\nme\n")
    assert st == "conflict"


# ---------- commit construction ----------


def test_build_subject_ordinal_suffix():
    frag = make_fragment(ordinal="3/5")
    assert ra.build_subject(frag) == \
        "refactor(x): commit a subject [3/5]"


def test_build_body_self_contained_no_planning_refs():
    frag = make_fragment(commit="deadbeef", ordinal="2/4")
    body = ra.build_body(frag)
    assert "Applied fragment 2/4 of deadbeef" in body
    assert "refactor(x): commit a subject" in body
    assert "v1.22.2" in body
    for banned in ("36-0", "plan", "ledger", "gsd", "D-0"):
        assert banned not in body


def test_provisional_amend_detection():
    frag = make_fragment(subject="refactor(x): s", ordinal="1/2")
    assert ra.provisional_amend_needed(
        "refactor(x): s [1/?]", frag) is True
    assert ra.provisional_amend_needed(
        "refactor(x): s [1/2]", frag) is False
    assert ra.provisional_amend_needed(
        "unrelated subject", frag) is False


# ---------- conflict classification ----------


def test_expected_modify_delete_set():
    assert ra.expected_modify_delete(
        "src/main/java/shortestpath/pathfinder/Pathfinder.java")
    assert ra.expected_modify_delete(
        "src/main/java/shortestpath/scheduler/PendingTask.java")
    assert ra.expected_modify_delete(
        "src/main/java/shortestpath/config/"
        "PathfinderConfigurationMigration.java")
    assert ra.expected_modify_delete(
        "src/main/java/shortestpath/OrdinalDirection.java")
    assert ra.expected_modify_delete(
        "src/main/java/shortestpath/pathfinder/PathfinderBackendAbc.java")
    assert not ra.expected_modify_delete(
        "src/main/java/shortestpath/Util.java")
    assert not ra.expected_modify_delete(
        "src/main/java/shortestpath/PathfinderX.java")


def test_replay_conflict_verdict():
    verdict = ra.replay_conflict_verdict([
        "src/main/java/shortestpath/pathfinder/Pathfinder.java",
        "src/main/java/shortestpath/scheduler/PendingTask.java"])
    assert verdict == "keep-deleted"
    verdict = ra.replay_conflict_verdict([
        "src/main/java/shortestpath/pathfinder/Pathfinder.java",
        "src/main/java/shortestpath/Util.java"])
    assert verdict == "stop"


# ---------- records ----------


def test_conflict_record_shape():
    rec = ra.make_conflict_record(
        fragment="aaaa1111-1of2", file=FILE_F,
        reason="anchor absent", match_count=0)
    assert rec["fragment"] == "aaaa1111-1of2"
    assert rec["file"] == FILE_F
    assert rec["reason"] == "anchor absent"
    assert rec["match-count"] == 0


def test_bump_record_shape():
    rec = ra.make_bump_record(
        fragment="aaaa1111-1of2", from_seg="v1.22.2", to_seg="v1.22.4",
        missing_symbol="OverlayDrawing",
        compile_line="error: cannot find symbol OverlayDrawing")
    assert rec["fragment"] == "aaaa1111-1of2"
    assert rec["from-segment"] == "v1.22.2"
    assert rec["to-segment"] == "v1.22.4"
    assert rec["missing-symbol"] == "OverlayDrawing"
    assert "cannot find symbol" in rec["compile-line"]


def test_ledger_row_shape():
    row = ra.make_ledger_row(
        fragment="aaaa1111-1of2", commit="bbbb2222", file=FILE_F,
        orig_segment="v1.22.2", applied_segment="v1.22.2",
        position=7, match_counts=[1, 1], disposition="applied")
    assert row.startswith("| aaaa1111-1of2 |")
    assert "v1.22.2" in row and "applied" in row and "| 7 |" in row


# ---------- state ----------


def test_state_round_trip(tmp_path):
    state = load_state_fixture()
    out = tmp_path / "state.json"
    ra.save_state(out, state)
    again = ra.load_state(out)
    assert again == state
    assert "9771243e-1of2" in again["fragments"]


def test_load_state_missing_returns_empty(tmp_path):
    state = ra.load_state(tmp_path / "nope.json")
    assert state["fragments"] == {}
    assert state["picks"] == []


# ---------- git() seam ----------


def test_git_invokes_subprocess_with_list_argv(monkeypatch):
    calls = {}

    def fake_run(cmd, **kwargs):
        calls["cmd"] = cmd
        calls.update(kwargs)
        return cp(cmd, stdout="ok\n")

    monkeypatch.setattr(ra.subprocess, "run", fake_run)
    assert ra.git("some-repo", "status") == "ok\n"
    assert calls["cmd"] == ["git", "-C", "some-repo", "status"]
    assert calls["check"] is True
    assert calls["capture_output"] is True
    assert calls["text"] is True
    assert "shell" not in calls


def test_git_surfaces_stderr_on_failure(monkeypatch):
    def fake_run(cmd, **kwargs):
        raise subprocess.CalledProcessError(128, cmd, stderr="fatal: boom\n")

    monkeypatch.setattr(ra.subprocess, "run", fake_run)
    with pytest.raises(RuntimeError, match="fatal: boom"):
        ra.git("some-repo", "status")


# ---------- gradle seam ----------


def test_gradle_uses_check_false_for_inspectable_red(monkeypatch):
    calls = {}

    def fake_run(cmd, **kwargs):
        calls["cmd"] = cmd
        calls.update(kwargs)
        return cp(cmd, stdout="FAILURE: boom\n", rc=1)

    monkeypatch.setattr(ra.subprocess, "run", fake_run)
    out, rc = ra.gradle("/tmp/wt", "compileJava", "compileTestJava")
    assert rc == 1 and "FAILURE" in out
    assert calls["cmd"] == ["./gradlew", "compileJava", "compileTestJava"]
    assert calls["check"] is False
    assert calls["capture_output"] is True
    assert calls.get("cwd") == "/tmp/wt"


def test_gate_red_detection():
    assert ra.gate_failed(0, "BUILD SUCCESSFUL\n") is False
    assert ra.gate_failed(1, "FAILURE: build failed\n") is True
    assert ra.gate_failed(0, "error: cannot find symbol\n") is True
    assert ra.gate_failed(0, "FAILURE: x\n") is True


def test_upstream_red_skip():
    # Test-side-only errors qualify (relative and absolute paths).
    assert ra.upstream_red_skip(["src/test/java/x/T.java"])
    assert ra.upstream_red_skip(["/tmp/wt/src/test/java/x/T.java"])
    # Any src/main error disqualifies the skip.
    assert not ra.upstream_red_skip(["src/main/java/x/T.java"])
    assert not ra.upstream_red_skip(
        ["/tmp/wt/src/test/java/x/T.java",
         "/tmp/wt/src/main/java/x/T.java"])
    # No error paths (OOM, infra) never qualifies.
    assert not ra.upstream_red_skip([])


# ---------- driver never moves refs ----------


def test_driver_has_no_push_argv_literal():
    src = SCRIPT_PATH.read_text()
    import re
    assert not re.search(r"[\"']push[\"']", src)


# ---------- insertions ('+'-only hunks) ----------


def make_insertion(**over):
    ins = {"file": FILE_F, "pos": 2,
           "before": "l1\nl2\n", "after": "l3\nl4\n",
           "text": "NEW\n"}
    ins.update(over)
    return ins


def test_resolve_insertion_pending_unique_anchor():
    content = "l1\nl2\nl3\nl4\n"
    r = ra._resolve_insertion(content, make_insertion(), [])
    assert r["status"] == "pending"
    assert r["before"] == "l1\nl2\n" and r["after"] == "l3\nl4\n"


def test_resolve_insertion_satisfied_when_text_present():
    content = "l1\nl2\nNEW\nl3\nl4\n"
    r = ra._resolve_insertion(content, make_insertion(), [])
    assert r["status"] == "satisfied"


def test_resolve_insertion_absent_anchor():
    r = ra._resolve_insertion("a\nb\n", make_insertion(), [])
    assert r["status"] == "absent"


def test_resolve_insertion_ambiguous_then_widened():
    # 'v\nw\nb\nc\n' occurs twice — the widened anchor from the parent
    # blob disambiguates to the second site.
    content = "v\nw\nb\nc\n" + "l1\nl2\nv\nw\nb\nc\nz\n"
    blob = ["l1\n", "l2\n", "v\n", "w\n", "b\n", "c\n", "z\n"]
    r = ra._resolve_insertion(
        content,
        make_insertion(pos=4, before="v\nw\n", after="b\nc\n"),
        blob)
    assert r["status"] == "pending"
    assert r["before"] == "l1\nl2\nv\nw\n"
    assert r["after"] == "b\nc\nz\n"


def test_resolve_insertion_ambiguous_conflict():
    content = "l1\nl2\nl3\nl4\n" * 2
    r = ra._resolve_insertion(content, make_insertion(), [])
    assert r["status"] == "ambiguous" and r["count"] == 2


def test_resolve_insertion_single_sided_fallback():
    # 'after' no longer matches — an earlier fragment changed the
    # anchored declaration; 'before' alone stays a unique locate.
    ins = make_insertion(before="@Getter\n",
                         after="public class X\n{\n",
                         text="@EqualsAndHashCode\n")
    content = "a\n@Getter\npublic final class X\n{\nb\n"
    r = ra._resolve_insertion(content, ins, [])
    assert r == {"status": "pending",
                 "before": "@Getter\n", "after": ""}
    # apply-time replacement still lands the text correctly
    assert content.replace(
        r["before"] + r["after"],
        r["before"] + ins["text"] + r["after"], 1
    ) == "a\n@Getter\n@EqualsAndHashCode\npublic final class X\n{\nb\n"


# ---------- fragment paths / provider search ----------


def test_frag_paths_covers_insertions_and_creations():
    frag = make_fragment(
        insertions=[make_insertion(file="src/main/java/x/G.java")],
        creations=[{"file": "src/main/java/x/New.java", "content": "x"}],
        companions=["src/test/java/x/FTest.java"])
    paths = ra._frag_paths(frag)
    assert paths[0] == FILE_F
    assert "src/main/java/x/G.java" in paths
    assert "src/main/java/x/New.java" in paths
    assert "src/test/java/x/FTest.java" in paths


def test_search_text_covers_insertions_and_creations():
    frag = make_fragment(
        plus_text="alpha\n",
        insertions=[make_insertion(text="beta\n")],
        creations=[{"file": "x", "content": "gamma\n"}])
    text = ra._search_text(frag)
    assert "alpha" in text and "beta" in text and "gamma" in text


def test_symbol_provider_finds_insertion_and_creation_symbols():
    carrier = make_fragment(
        id="c1-1of1", segment="v1.22.3",
        creations=[{"file": "x", "content": "class OverlayDrawing\n"}])
    ins = make_fragment(
        id="c2-1of1", segment="v1.22.3",
        insertions=[make_insertion(text="import lombok.Getter;\n")])
    assert ra._symbol_provider(
        [carrier], "OverlayDrawing", "none", "v1.22.3") is carrier
    assert ra._symbol_provider(
        [ins], "Getter", "none", "v1.22.3") is ins
    assert ra._symbol_provider(
        [carrier], "OverlayDrawing", "none", "v1.22.2") is None


def test_eff_segment_follows_bump_target():
    frag = make_fragment(id="f1-1of1", segment="v1.22.2")
    state = {"fragments": {"f1-1of1": {"status": "bumped",
                                       "to": "v1.22.4"}}}
    assert ra._eff_segment(frag, state) == "v1.22.4"
    assert ra._eff_segment(frag, {"fragments": {}}) == "v1.22.2"


def test_symbol_provider_matches_bumped_effective_segment():
    frag = make_fragment(id="f1-1of1", segment="v1.22.2",
                         plus_text="TypeX\n")
    state = {"fragments": {"f1-1of1": {"status": "bumped",
                                       "to": "v1.22.4"}}}
    # the fragment no longer reads as a .2 provider — it re-judges
    # where it was bumped.
    assert ra._symbol_provider(
        [frag], "TypeX", "none", "v1.22.2", state=state) is None
    assert ra._symbol_provider(
        [frag], "TypeX", "none", "v1.22.4", state=state) is frag


# ---------- segment scope ----------


def test_in_segment_scope_covers_pending_and_bumped_targets():
    frag = make_fragment(id="f1-1of1", segment="v1.22.3")
    # never-run spec-segment fragment scopes in
    assert ra._in_segment_scope(frag, {}, "v1.22.3") is True
    # terminal states scope out
    for status in ("applied", "already-applied", "bumped"):
        assert ra._in_segment_scope(
            frag, {"status": status}, "v1.22.3") is False
    # a bumped fragment re-scopes only at its bump target
    st = {"status": "bumped", "to": "v1.22.3"}
    assert ra._in_segment_scope(frag, st, "v1.22.3") is True
    assert ra._in_segment_scope(frag, st, "v1.22.4") is False
    # a .2-spec fragment bumped to .3 re-judges in the .3 run
    frag2 = make_fragment(id="f2-1of1", segment="v1.22.2")
    assert ra._in_segment_scope(frag2, st, "v1.22.3") is True
    assert ra._in_segment_scope(frag2, st, "v1.22.2") is False


def test_in_segment_scope_rejudges_same_segment_bump():
    # The provider-gate-fail path writes bumped/to=<same segment> so a
    # re-run of run-segment re-judges the fragment standalone — a bare
    # 'gate-failed' status would strand it (spec segment already ran,
    # no bump target for later segments to pick up).
    frag = make_fragment(id="f1-1of1", segment="v1.22.2")
    st = {"status": "bumped", "to": "v1.22.3"}
    assert ra._in_segment_scope(frag, st, "v1.22.3") is True
    # gate-failed without a target does NOT scope — the driver must
    # never write it bare (regression guard for the stranded-provider
    # bug: 16 fragments stranded by a bare gate-failed status)
    assert ra._in_segment_scope(
        frag, {"status": "gate-failed"}, "v1.22.3") is False
