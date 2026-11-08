"""Tests for ``scripts/chain_repick.py``.

Conflict classification and resolution are tested on inline strings.  The
sequencer loop and the one to one map run real git commands on throw-away
repositories (what they decide is exactly what git reports); the only faked
git output is `git range-diff`, whose rows are scripted so each only-old /
only-new case is exact.  The script is loaded via importlib because
``scripts/`` has no ``__init__.py``.
"""

import importlib.util
import json
import os
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "chain_repick.py"
MINI = Path(__file__).resolve().parent / "fixtures" / "wildcard_universe_mini.tsv"

spec = importlib.util.spec_from_file_location("chain_repick", SCRIPT_PATH)
cr = importlib.util.module_from_spec(spec)
sys.modules["chain_repick"] = cr
spec.loader.exec_module(cr)

FOO = ("package acme;\n"
       "\n"
       "import acme.util.Alpha;\n"
       "import acme.util.Beta;\n"
       "\n"
       "class Foo\n"
       "{\n"
       "\tAlpha a;\n"
       "\tBeta b;\n"
       "}\n")
FOO_WILD = FOO.replace("import acme.util.Alpha;\nimport acme.util.Beta;\n",
                       "import acme.util.*;\n")
BAR = "package acme;\n\nclass Bar\n{\n\tint v = 1;\n}\n"
MAIN_FOO = "src/main/java/acme/Foo.java"
MAIN_BAR = "src/main/java/acme/Bar.java"
TEST_FOO = "src/test/java/acme/FooTest.java"

ENV = dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@example.com",
           GIT_COMMITTER_NAME="t", GIT_COMMITTER_EMAIL="t@example.com")


def g(repo, *args, input=None, env=None):
    p = subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True,
                       text=True, env=dict(ENV, **(env or {})), input=input)
    return p.stdout.strip()


def commit(repo, files, message, author=("Ann", "ann@example.com"), allow_empty=False):
    for name, text in files.items():
        p = Path(repo) / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text)
    g(repo, "add", "-A")
    args = ["commit", "-q", "-m", message] + (["--allow-empty"] if allow_empty else [])
    g(repo, *args, env={"GIT_AUTHOR_NAME": author[0], "GIT_AUTHOR_EMAIL": author[1],
                        "GIT_AUTHOR_DATE": "2026-01-02T03:04:05+00:00"})
    return g(repo, "rev-parse", "HEAD")


@pytest.fixture
def repo(tmp_path):
    r = tmp_path / "repo"
    r.mkdir()
    g(r, "init", "-q", "-b", "main")
    commit(r, {MAIN_FOO: FOO, MAIN_BAR: BAR, ".gitignore": "build/\n"}, "base")
    return r


def pick(repo, rng, log, *extra):
    return cr.main(["pick", "--worktree", str(repo), "--range", rng, "--log", str(log),
                    "--universe", str(MINI), *extra])


# ---------- conflict blocks ----------

CONFLICTED = ("package acme;\n"
              "\n"
              "<<<<<<< HEAD\n"
              "import acme.util.*;\n"
              "=======\n"
              "import acme.util.Alpha;\n"
              "import acme.util.Gamma;\n"
              ">>>>>>> abc1234 (pick)\n"
              "\n"
              "class Foo\n"
              "{\n"
              "}\n")


def test_conflict_blocks_reads_ours_theirs_and_positions():
    (block,) = cr.conflict_blocks(CONFLICTED)
    assert (block["start"], block["end"]) == (2, 7)
    assert block["ours"] == ["import acme.util.*;"]
    assert block["theirs"] == ["import acme.util.Alpha;", "import acme.util.Gamma;"]
    assert block["base"] is None


def test_conflict_blocks_reads_a_base_part():
    text = CONFLICTED.replace("=======", "||||||| base\nimport acme.util.Alpha;\n=======")
    (block,) = cr.conflict_blocks(text)
    assert block["base"] == ["import acme.util.Alpha;"]


def test_an_unclosed_block_is_malformed():
    with pytest.raises(cr.MalformedConflict):
        cr.conflict_blocks("<<<<<<< HEAD\nx\n=======\ny\n")


def test_import_section_runs_from_the_package_to_the_first_declaration():
    lines = FOO.split("\n")
    assert cr.import_section(lines) == (1, 5)
    documented = ("package a;\n\nimport a.B;\n// note\n/* block */\n\n"
                  "/** doc */\n@Annotation\nclass X {}\n").split("\n")
    start, end = cr.import_section(documented)
    assert (start, end) == (1, 6)


def test_import_section_without_a_package_starts_at_the_top():
    assert cr.import_section("import a.B;\n\nclass X {}\n".split("\n")) == (0, 2)


def test_import_section_skips_a_leading_licence_comment():
    text = "/*\n * licence\n */\npackage a;\nimport a.B;\nclass X {}\n"
    assert cr.import_section(text.split("\n")) == (4, 5)


# ---------- eligibility ----------

def test_a_block_of_import_lines_inside_the_import_section_is_eligible():
    assert cr.hand_reason(CONFLICTED) is None


def test_a_base_part_with_only_imports_is_eligible():
    text = CONFLICTED.replace("=======", "||||||| base\nimport acme.util.Alpha;\n=======")
    assert cr.hand_reason(text) is None


def test_a_block_holding_a_method_line_is_not_eligible():
    text = CONFLICTED.replace("import acme.util.Gamma;", "void f() {}")
    assert "holds code" in cr.hand_reason(text)


def test_a_block_holding_code_only_in_the_base_part_is_not_eligible():
    text = CONFLICTED.replace("=======", "||||||| base\nint x;\n=======")
    assert "holds code" in cr.hand_reason(text)


def test_a_block_below_the_first_type_declaration_is_not_eligible():
    text = ("package acme;\n\nimport acme.util.Alpha;\n\nclass Foo\n{\n"
            "<<<<<<< HEAD\nimport acme.util.Beta;\n=======\nimport acme.util.Gamma;\n"
            ">>>>>>> x\n}\n")
    assert "outside the import section" in cr.hand_reason(text)


def test_one_ineligible_block_among_eligible_ones_makes_the_file_ineligible():
    text = CONFLICTED + "<<<<<<< HEAD\nint a;\n=======\nint b;\n>>>>>>> x\n"
    assert cr.hand_reason(text) is not None


def test_a_file_without_a_block_is_not_eligible():
    assert cr.hand_reason(FOO) is not None


def test_modify_delete_and_add_add_stage_sets_are_not_resolved(tmp_path):
    for stages in ({1, 2}, {1, 3}, {2, 3}):
        assert cr.try_resolve(tmp_path, ["src/main/java/acme/Foo.java"],
                              {"src/main/java/acme/Foo.java": stages}, {}) is None
    assert cr.try_resolve(tmp_path, ["data.tsv"], {"data.tsv": {1, 2, 3}}, {}) is None


# ---------- resolution ----------

def test_resolution_takes_the_picks_import_section_and_gits_text_elsewhere():
    merged = CONFLICTED.replace("class Foo\n{\n}\n", "class Foo\n{\n\tint merged;\n}\n")
    theirs = ("package acme;\n\nimport acme.util.Alpha;\nimport acme.util.Gamma;\n\n"
              "class Foo\n{\n\tint theirs;\n}\n")
    out = cr.resolve_import_conflict(merged, theirs)
    assert out == ("package acme;\n\nimport acme.util.Alpha;\nimport acme.util.Gamma;\n\n"
                   "class Foo\n{\n\tint merged;\n}\n")


def universe():
    return cr.wi.load_universe(MINI)


def test_a_main_file_is_recanonicalised_and_a_test_file_keeps_the_section():
    section = "package acme;\n\nimport acme.util.Alpha;\nimport acme.util.Beta;\n\n"
    body = "class X\n{\n\tAlpha a;\n\tBeta b;\n}\n"
    out = cr.canonical_texts(
        {MAIN_FOO: FOO},
        {"src/main/java/acme/X.java": section + body,
         "src/test/java/acme/XTest.java": section + body},
        universe())
    assert out["src/main/java/acme/X.java"] == "package acme;\n\nimport acme.util.*;\n\n" + body
    assert out["src/test/java/acme/XTest.java"] == section + body


# ---------- the sequencer loop, on real repositories ----------

def build_import_conflict(repo):
    """old chain on `old`: O1 (Bar body), O2 (Foo gains Gamma), O3 (Bar body);
    base for the replay on `new`: the wildcard commit."""
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    o1 = commit(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 2")}, "Bar two", ("Bob", "bob@example.com"))
    o2 = commit(repo, {MAIN_FOO: FOO.replace("import acme.util.Beta;\n",
                                             "import acme.util.Beta;\nimport acme.util.Gamma;\n")
                       .replace("\tBeta b;\n", "\tBeta b;\n\tGamma g;\n" + "".join(
                           f"\tint f{i} = {i};\n" for i in range(8)))}, "Foo gains Gamma")
    o3 = commit(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 3")}, "Bar three")
    g(repo, "checkout", "-q", "-b", "new", "base")
    w = commit(repo, {MAIN_FOO: FOO_WILD}, "refactor: use wildcard imports")
    return [o1, o2, o3], w


def test_the_cherry_pick_argv_has_the_replay_flags_and_no_trailer_flag(repo, tmp_path, monkeypatch):
    olds, _ = build_import_conflict(repo)
    seen = []
    real = cr.git_rc

    def spy(r, *args, **kw):
        seen.append(args)
        return real(r, *args, **kw)

    monkeypatch.setattr(cr, "git_rc", spy)
    assert pick(repo, "base..old", tmp_path / "log.json") == 0
    (argv,) = [a for a in seen if a[0] == "cherry-pick" and "--continue" not in a]
    assert argv[:5] == ("cherry-pick", "-X", "ignore-space-at-eol", "--allow-empty",
                        "--keep-redundant-commits")
    assert "-x" not in argv and list(argv[5:]) == olds


def test_an_import_section_conflict_is_resolved_and_logged(repo, tmp_path):
    olds, w = build_import_conflict(repo)
    log = tmp_path / "log.json"
    assert pick(repo, "base..old", log) == 0
    data = json.loads(log.read_text())
    assert data["status"] == "complete" and data["onto"] == w
    assert [p["old"] for p in data["picks"]] == olds
    assert [p["conflict"] for p in data["picks"]][0] is None
    conflict = data["picks"][1]["conflict"]
    assert conflict["class"] == "import-section" and conflict["files"] == [MAIN_FOO]
    assert g(repo, "status", "--porcelain") == ""
    # the wildcard stays, Gamma is used, the body of the pick is there
    text = (repo / MAIN_FOO).read_text()
    assert "import acme.util.*;" in text and "\tGamma g;" in text
    assert "Alpha;" not in text.split("class Foo")[0]
    assert g(repo, "rev-list", "--count", f"{w}..HEAD") == "3"


def test_replayed_commits_keep_author_date_and_message_and_get_no_trailer(repo, tmp_path):
    olds, w = build_import_conflict(repo)
    log = tmp_path / "log.json"
    pick(repo, "base..old", log)
    for pk in json.loads(log.read_text())["picks"]:
        fmt = "%an <%ae>|%aI|%B"
        assert g(repo, "log", "-1", f"--format={fmt}", pk["new"]) == \
            g(repo, "log", "-1", f"--format={fmt}", pk["old"])
        assert "cherry picked from" not in g(repo, "log", "-1", "--format=%B", pk["new"])
        assert pk["new"] != pk["old"]
    assert g(repo, "log", "-1", "--format=%an", json.loads(log.read_text())["picks"][0]["new"]) == "Bob"


def test_a_pick_that_becomes_empty_is_kept_as_an_empty_commit(repo, tmp_path):
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    o1 = commit(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 2")}, "Bar two")
    g(repo, "checkout", "-q", "-b", "new", "base")
    commit(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 2")}, "same change made first")
    log = tmp_path / "log.json"
    assert pick(repo, "base..old", log) == 0
    (pk,) = json.loads(log.read_text())["picks"]
    assert pk["old"] == o1 and pk["new"] != o1
    assert g(repo, "rev-parse", f"{pk['new']}^{{tree}}") == g(repo, "rev-parse", f"{pk['new']}~1^{{tree}}")


def test_an_originally_empty_commit_is_replayed(repo, tmp_path):
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    commit(repo, {}, "marker", allow_empty=True)
    g(repo, "checkout", "-q", "-b", "new", "base")
    log = tmp_path / "log.json"
    assert pick(repo, "base..old", log) == 0
    (pk,) = json.loads(log.read_text())["picks"]
    assert g(repo, "log", "-1", "--format=%s", pk["new"]) == "marker"


def test_a_conflict_in_a_test_source_keeps_the_picks_section_verbatim(repo, tmp_path):
    base_test = "package acme;\n\nimport acme.util.Alpha;\nimport acme.util.Beta;\n\nclass FooTest\n{\n\tAlpha a;\n\tBeta b;\n}\n"
    commit(repo, {TEST_FOO: base_test}, "add test")
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    commit(repo, {TEST_FOO: base_test.replace("import acme.util.Beta;\n",
                                              "import acme.util.Beta;\nimport acme.util.Gamma;\n")
                  .replace("\tBeta b;\n", "\tBeta b;\n\tGamma g;\n")}, "test gains Gamma")
    g(repo, "checkout", "-q", "-b", "new", "base")
    commit(repo, {TEST_FOO: base_test.replace("import acme.util.Beta;\n", "")},
           "cleanup drops the Beta import")
    log = tmp_path / "log.json"
    assert pick(repo, "base..old", log) == 0
    text = (repo / TEST_FOO).read_text()
    assert "import acme.util.Alpha;\nimport acme.util.Beta;\nimport acme.util.Gamma;\n" in text
    (pk,) = json.loads(log.read_text())["picks"]
    assert pk["conflict"]["class"] == "import-section"


def test_a_body_conflict_stops_the_pick_logs_it_and_exits_3(repo, tmp_path, capsys):
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    o1 = commit(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 2")}, "Bar two")
    g(repo, "checkout", "-q", "-b", "new", "base")
    commit(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 7")}, "Bar seven")
    log = tmp_path / "log.json"
    assert pick(repo, "base..old", log) == 3
    assert f"hand resolution needed: {o1} {MAIN_BAR}" in capsys.readouterr().out
    data = json.loads(log.read_text())
    assert data["status"] == "stopped" and data["stopped"] == {"old": o1, "files": [MAIN_BAR]}
    # the worktree is left in the middle of the pick
    assert g(repo, "rev-parse", "-q", "--verify", "CHERRY_PICK_HEAD") == o1


def test_continue_with_a_verdict_records_it_and_resumes(repo, tmp_path):
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    o1 = commit(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 2")}, "Bar two")
    o2 = commit(repo, {MAIN_FOO: FOO + "// tail\n"}, "Foo tail")
    g(repo, "checkout", "-q", "-b", "new", "base")
    commit(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 7")}, "Bar seven")
    log = tmp_path / "log.json"
    assert pick(repo, "base..old", log) == 3
    (repo / MAIN_BAR).write_text(BAR.replace("v = 1", "v = 9"))
    g(repo, "add", MAIN_BAR)
    rc = cr.main(["pick", "--worktree", str(repo), "--log", str(log), "--continue",
                  "--verdict", "kept 9 on purpose", "--universe", str(MINI)])
    assert rc == 0
    data = json.loads(log.read_text())
    assert data["status"] == "complete" and [p["old"] for p in data["picks"]] == [o1, o2]
    assert data["picks"][0]["conflict"] == {"files": [MAIN_BAR], "class": "hand",
                                            "resolution": "kept 9 on purpose"}
    assert (repo / MAIN_BAR).read_text() == BAR.replace("v = 1", "v = 9")
    assert g(repo, "status", "--porcelain") == ""


def test_continue_refuses_while_paths_are_still_unmerged(repo, tmp_path, capsys):
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    commit(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 2")}, "Bar two")
    g(repo, "checkout", "-q", "-b", "new", "base")
    commit(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 7")}, "Bar seven")
    log = tmp_path / "log.json"
    pick(repo, "base..old", log)
    rc = cr.main(["pick", "--worktree", str(repo), "--log", str(log), "--continue",
                  "--verdict", "x", "--universe", str(MINI)])
    assert rc == 2 and "unmerged paths remain" in capsys.readouterr().err


def test_a_dirty_worktree_and_a_merge_in_the_range_exit_2(repo, tmp_path, capsys):
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    commit(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 2")}, "Bar two")
    g(repo, "checkout", "-q", "-b", "side", "base")
    commit(repo, {MAIN_FOO: FOO + "// side\n"}, "side")
    g(repo, "checkout", "-q", "old")
    g(repo, "merge", "-q", "--no-ff", "-m", "merge side", "side")
    g(repo, "checkout", "-q", "-b", "new", "base")
    assert pick(repo, "base..old", tmp_path / "l.json") == 2
    assert "merge commits" in capsys.readouterr().err
    (repo / MAIN_BAR).write_text("dirty\n")
    assert pick(repo, "base..side", tmp_path / "l2.json") == 2
    assert "is not clean" in capsys.readouterr().err


# ---------- the one to one map ----------

def write_log(path, pairs, subjects=None):
    picks = [{"old": o, "new": n, "subject": (subjects or {}).get(o, "s"), "conflict": c}
             for o, n, c in pairs]
    path.write_text(json.dumps({"range": "x", "onto": "y", "status": "complete",
                                "picks": picks}))


def test_a_real_replay_maps_with_no_unexplained_pair(repo, tmp_path):
    olds, w = build_import_conflict(repo)
    log = tmp_path / "log.json"
    pick(repo, "base..old", log)
    out = tmp_path / "map.json"
    rc = cr.main(["map", "--repo", str(repo), "--log", str(log), "--old", "base..old",
                  "--new", f"{w}..HEAD", "--out", str(out)])
    assert rc == 0
    doc = json.loads(out.read_text())
    assert [p["class"] for p in doc["pairs"]] == [
        "identical", "import-or-whitespace", "identical"]
    assert doc["pairs"][1]["conflict"] == "import-section"
    assert doc["counts"] == {"identical": 2, "import-or-whitespace": 1}
    assert doc["errors"] == [] and doc["range_diff_accepted"] == []


def two_chains(repo, old_change, new_change, new_subject="Bar two"):
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    o = commit(repo, old_change, "Bar two")
    g(repo, "checkout", "-q", "-b", "new", "base")
    n = commit(repo, new_change, new_subject)
    return o, n


def run_map(repo, tmp_path, pairs, *extra, old="base..old", new="base..new", log_name="log.json"):
    log = tmp_path / log_name
    write_log(log, pairs)
    out = tmp_path / "map.json"
    rc = cr.main(["map", "--repo", str(repo), "--log", str(log), "--old", old, "--new", new,
                  "--out", str(out), *extra])
    return rc, json.loads(out.read_text())


def test_equal_patch_ids_classify_identical(repo, tmp_path):
    change = {MAIN_BAR: BAR.replace("v = 1", "v = 2")}
    o, n = two_chains(repo, change, change)
    rc, doc = run_map(repo, tmp_path, [(o, n, None)])
    assert rc == 0 and doc["pairs"][0]["class"] == "identical"


def test_a_body_change_without_a_log_entry_is_unexplained_and_exits_1(repo, tmp_path, capsys):
    o, n = two_chains(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 2")},
                      {MAIN_BAR: BAR.replace("v = 1", "v = 3")})
    rc, doc = run_map(repo, tmp_path, [(o, n, None)])
    assert rc == 1 and doc["pairs"][0]["class"] == "unexplained"
    assert "unexplained difference" in capsys.readouterr().out


def test_the_same_difference_with_a_logged_conflict_is_conflict_recorded(repo, tmp_path):
    o, n = two_chains(repo, {MAIN_BAR: BAR.replace("v = 1", "v = 2")},
                      {MAIN_BAR: BAR.replace("v = 1", "v = 3")})
    conflict = {"files": [MAIN_BAR], "class": "hand", "resolution": "chose 3"}
    rc, doc = run_map(repo, tmp_path, [(o, n, conflict)])
    assert rc == 0 and doc["pairs"][0]["class"] == "conflict-recorded"


def test_a_pair_differing_only_in_import_lines_and_whitespace_is_import_or_whitespace(
        repo, tmp_path, fake_range_diff):
    o, n = two_chains(repo, {MAIN_FOO: FOO.replace("import acme.util.Beta;\n",
                                                   "import acme.util.Beta;\nimport acme.util.Gamma;\n")
                             .replace("\tBeta b;\n", "\tBeta b;\n\tGamma g;\n")},
                      {MAIN_FOO: FOO_WILD.replace("\tBeta b;\n", "\tBeta b; \n\tGamma g;\n")})
    fake_range_diff["text"] = f"1:  {o[:7]} ! 1:  {n[:7]} Bar two\n"
    rc, doc = run_map(repo, tmp_path, [(o, n, None)])
    assert rc == 0 and doc["pairs"][0]["class"] == "import-or-whitespace"


def test_a_missing_old_commit_in_the_log_exits_1(repo, tmp_path, capsys):
    change = {MAIN_BAR: BAR.replace("v = 1", "v = 2")}
    o, n = two_chains(repo, change, change)
    rc, doc = run_map(repo, tmp_path, [])
    assert rc == 1
    assert any("old commits differ" in e for e in doc["errors"])


def test_a_subject_mismatch_exits_1(repo, tmp_path):
    change = {MAIN_BAR: BAR.replace("v = 1", "v = 2")}
    o, n = two_chains(repo, change, change, new_subject="another subject")
    rc, doc = run_map(repo, tmp_path, [(o, n, None)])
    assert rc == 1 and any("subject differs" in e for e in doc["errors"])


def test_an_author_mismatch_exits_1(repo, tmp_path):
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    change = {MAIN_BAR: BAR.replace("v = 1", "v = 2")}
    o = commit(repo, change, "Bar two", ("Ann", "ann@example.com"))
    g(repo, "checkout", "-q", "-b", "new", "base")
    n = commit(repo, change, "Bar two", ("Zed", "zed@example.com"))
    rc, doc = run_map(repo, tmp_path, [(o, n, None)])
    assert rc == 1 and any("author differs" in e for e in doc["errors"])


def test_a_new_commit_missing_from_the_range_exits_1(repo, tmp_path):
    change = {MAIN_BAR: BAR.replace("v = 1", "v = 2")}
    o, n = two_chains(repo, change, change)
    extra = commit(repo, {MAIN_FOO: FOO + "// more\n"}, "an extra commit")
    rc, doc = run_map(repo, tmp_path, [(o, n, None)])
    assert rc == 1 and any("new commits plus the inserted" in e for e in doc["errors"])
    rc, doc = run_map(repo, tmp_path, [(o, n, None)], "--inserted", extra)
    assert rc == 0


def test_an_incomplete_log_is_unusable(repo, tmp_path, capsys):
    log = tmp_path / "log.json"
    log.write_text(json.dumps({"status": "stopped", "picks": []}))
    rc = cr.main(["map", "--repo", str(repo), "--log", str(log), "--old", "HEAD..HEAD",
                  "--new", "HEAD..HEAD", "--out", str(tmp_path / "m.json")])
    assert rc == 2 and "not complete" in capsys.readouterr().err


# ---------- the range-diff row parser ----------

RD = """1:  1111111 = 1:  aaaaaaa Bar two
2:  2222222 ! 2:  bbbbbbb Foo gains Gamma
    @@ Metadata
     ## Metadata ##
    +    1: not a header row
3:  3333333 < -:  ------- Foo imports
-:  ------- > 3:  ccccccc Foo imports
"""


def test_parse_range_diff_reads_header_rows_only():
    assert cr.parse_range_diff(RD) == [
        ("=", "1111111", "aaaaaaa"), ("!", "2222222", "bbbbbbb"),
        ("<", "3333333", None), (">", None, "ccccccc")]


# ---------- the import-only pairing of range-diff rows ----------

def import_only_scene(repo, old_change, new_change, earlier=None):
    """O: the original (an import-only patch unless `old_change` says more); N:
    its replay on the wildcard line; both subjects equal.  `earlier` is a
    commit of the new line before the wildcard commit (a compile fix).
    Returns (O, N)."""
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    o = commit(repo, old_change, "Foo imports")
    g(repo, "checkout", "-q", "-b", "new", "base")
    if earlier:
        commit(repo, earlier, "earlier change on the new line")
    commit(repo, {MAIN_FOO: FOO_WILD}, "refactor: use wildcard imports")
    n = commit(repo, new_change, "Foo imports", allow_empty=not new_change)
    return o, n


GAMMA_IMPORT = {MAIN_FOO: FOO.replace("import acme.util.Beta;\n",
                                      "import acme.util.Beta;\nimport acme.util.Gamma;\n")}


@pytest.fixture
def fake_range_diff(monkeypatch):
    """Script `git range-diff`; every other git call is the real one."""
    box = {"text": ""}
    real = cr.git

    def fake(r, *args, **kw):
        if args and args[0] == "range-diff":
            return box["text"]
        return real(r, *args, **kw)

    monkeypatch.setattr(cr, "git", fake)
    return box


def rows(o, n):
    return f"1:  {o[:7]} < -:  ------- Foo imports\n-:  ------- > 1:  {n[:7]} Foo imports\n"


def map_pair(repo, tmp_path, o, n, *extra, joined_new=None):
    log = tmp_path / "log.json"
    write_log(log, [(o, joined_new or n, None)], {o: "Foo imports"})
    out = tmp_path / "map.json"
    new_range = f"{n}~1..{n}"
    rc = cr.main(["map", "--repo", str(repo), "--log", str(log), "--old", f"{o}~1..{o}",
                  "--new", new_range, "--out", str(out), *extra])
    return rc, json.loads(out.read_text())


def test_range_diff_import_only_pair_accepted(repo, tmp_path, fake_range_diff):
    o, n = import_only_scene(repo, GAMMA_IMPORT, {})
    fake_range_diff["text"] = rows(o, n)
    rc, doc = map_pair(repo, tmp_path, o, n)
    assert rc == 0, doc["errors"]
    assert doc["range_diff_accepted"] == [{"old": o, "new": n,
                                           "old_patch": "imports-or-whitespace",
                                           "tree": "equal-outside-imports"}]
    assert doc["range_diff"]["<"] == 1 and doc["range_diff"][">"] == 1


def test_range_diff_import_only_pair_accepted_with_a_fix_patch(repo, tmp_path, fake_range_diff):
    o, n = import_only_scene(repo, GAMMA_IMPORT, {},
                             earlier={MAIN_BAR: BAR.replace("v = 1", "v = 4")})
    fix = tmp_path / "fix.patch"
    fix.write_text("--- a/src/main/java/acme/Bar.java\n+++ b/src/main/java/acme/Bar.java\n"
                   "@@ -3,4 +3,4 @@\n class Bar\n {\n-\tint v = 1;\n+\tint v = 4;\n }\n")
    fake_range_diff["text"] = rows(o, n)
    rc, doc = map_pair(repo, tmp_path, o, n, "--fix-patch", str(fix))
    assert rc == 0, doc["errors"]
    assert len(doc["range_diff_accepted"]) == 1


def test_range_diff_import_only_pair_rejected_when_the_old_patch_changes_a_non_import_line(
        repo, tmp_path, fake_range_diff):
    old_change = {MAIN_FOO: GAMMA_IMPORT[MAIN_FOO].replace("\tBeta b;\n", "\tBeta b;\n\tint x;\n")}
    o, n = import_only_scene(repo, old_change, {MAIN_FOO: FOO_WILD.replace("\tBeta b;\n",
                                                                           "\tBeta b;\n\tint x;\n")})
    fake_range_diff["text"] = rows(o, n)
    rc, doc = map_pair(repo, tmp_path, o, n)
    assert rc == 1 and doc["range_diff_accepted"] == []
    assert any("not an accepted import-only pairing" in e for e in doc["errors"])


def test_range_diff_import_only_pair_rejected_when_the_old_patch_touches_a_non_java_file(
        repo, tmp_path, fake_range_diff):
    o, n = import_only_scene(repo, {**GAMMA_IMPORT, "data.tsv": "a\tb\n"}, {"data.tsv": "a\tb\n"})
    fake_range_diff["text"] = rows(o, n)
    rc, doc = map_pair(repo, tmp_path, o, n)
    assert rc == 1 and doc["range_diff_accepted"] == []


def test_range_diff_import_only_pair_rejected_when_the_new_tree_differs_in_a_non_import_line(
        repo, tmp_path, fake_range_diff):
    o, n = import_only_scene(repo, GAMMA_IMPORT, {},
                             earlier={MAIN_BAR: BAR.replace("v = 1", "v = 4")})
    fake_range_diff["text"] = rows(o, n)
    rc, doc = map_pair(repo, tmp_path, o, n)
    assert rc == 1 and doc["range_diff_accepted"] == []
    assert doc["pairs"][0]["class"] == "import-or-whitespace"


def test_range_diff_import_only_pair_rejected_when_the_log_joins_another_sha(
        repo, tmp_path, fake_range_diff):
    o, n = import_only_scene(repo, GAMMA_IMPORT, {})
    other = commit(repo, {MAIN_FOO: FOO_WILD + "// other\n"}, "Foo imports")
    fake_range_diff["text"] = rows(o, n)
    log = tmp_path / "log.json"
    write_log(log, [(o, other, None)], {o: "Foo imports"})
    out = tmp_path / "map.json"
    rc = cr.main(["map", "--repo", str(repo), "--log", str(log), "--old", f"{o}~1..{o}",
                  "--new", f"{n}~1..{n}", "--out", str(out)])
    doc = json.loads(out.read_text())
    assert rc == 1 and doc["range_diff_accepted"] == []


def test_range_diff_import_only_pair_rejected_when_the_fix_patch_does_not_apply(
        repo, tmp_path, fake_range_diff):
    o, n = import_only_scene(repo, GAMMA_IMPORT, {})
    fix = tmp_path / "fix.patch"
    fix.write_text("--- a/src/main/java/acme/Bar.java\n+++ b/src/main/java/acme/Bar.java\n"
                   "@@ -3,4 +3,4 @@\n class Bar\n {\n-\tint absent = 1;\n+\tint v = 4;\n }\n")
    fake_range_diff["text"] = rows(o, n)
    rc, doc = map_pair(repo, tmp_path, o, n, "--fix-patch", str(fix))
    assert rc == 1 and doc["range_diff_accepted"] == []


def test_an_only_new_row_beyond_the_inserted_commits_exits_1(repo, tmp_path, fake_range_diff):
    o, n = import_only_scene(repo, GAMMA_IMPORT, {})
    fake_range_diff["text"] = (f"1:  {o[:7]} = 1:  {n[:7]} Foo imports\n"
                               f"-:  ------- > 2:  {'f' * 7} surplus\n")
    rc, doc = map_pair(repo, tmp_path, o, n)
    assert rc == 1 and any("only-new rows" in e for e in doc["errors"])
    fake_range_diff["text"] = (f"1:  {o[:7]} = 1:  {n[:7]} Foo imports\n"
                               f"-:  ------- > 2:  {n[:7]} surplus\n")
    rc, doc = map_pair(repo, tmp_path, o, n, "--inserted", n)
    assert rc == 1  # the inserted commit is also a logged replay: set mismatch


def test_an_inserted_commit_accounts_for_its_only_new_row(repo, tmp_path, fake_range_diff):
    change = {MAIN_BAR: BAR.replace("v = 1", "v = 2")}
    o, n = two_chains(repo, change, change)
    ins = commit(repo, {MAIN_FOO: FOO + "// more\n"}, "inserted")
    fake_range_diff["text"] = (f"1:  {o[:7]} = 1:  {n[:7]} Bar two\n"
                               f"-:  ------- > 2:  {ins[:7]} inserted\n")
    rc, doc = run_map(repo, tmp_path, [(o, n, None)], "--inserted", ins)
    assert rc == 0, doc["errors"]


# ---------- patched-tree ----------

def test_patched_tree_prints_the_tree_and_exits_2_when_the_patch_does_not_apply(
        repo, tmp_path, capsys):
    (repo / MAIN_BAR).write_text(BAR.replace("v = 1", "v = 5"))
    patch = tmp_path / "p.patch"
    patch.write_text(subprocess.run(["git", "-C", str(repo), "diff"], capture_output=True,
                                    text=True).stdout)
    g(repo, "checkout", "-q", "--", MAIN_BAR)
    head = g(repo, "rev-parse", "HEAD")
    assert cr.main(["patched-tree", "--repo", str(repo), "--treeish", head,
                    "--patch", str(patch)]) == 0
    sha = capsys.readouterr().out.strip()
    assert g(repo, "cat-file", "-p", f"{sha}:{MAIN_BAR}") == BAR.replace("v = 1", "v = 5").strip()
    bad = tmp_path / "bad.patch"
    bad.write_text("--- a/x\n+++ b/x\n@@ -1 +1 @@\n-no\n+yes\n")
    assert cr.main(["patched-tree", "--repo", str(repo), "--treeish", head,
                    "--patch", str(bad)]) == 2
