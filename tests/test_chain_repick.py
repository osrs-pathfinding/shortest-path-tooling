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


CFG = "src/main/java/acme/Cfg.java"


def cfg_text(lines, dirty):
    """A small interface file; `dirty` marks the blank line at index 3 with a tab
    (trailing whitespace that a cleanup commit would have removed)."""
    body = "".join((("\t" if dirty and i == 3 and not line else "") + line) + "\n"
                   for i, line in enumerate(lines))
    return "package acme;\n\npublic interface Cfg\n{\n" + body + "}\n"


CFG_LINES = ["\t@Item(", "\t}", "\t@Item(", "", "\t\tposition = 1,"]


def whitespace_apart_chains(repo, old_lines, new_lines, new_last="\t\tposition = 1,"):
    """The old chain adds Cfg.java with trailing whitespace then changes its
    lines 1..3; the new chain adds it cleaned, then makes its own change."""
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    ox = commit(repo, {CFG: cfg_text(CFG_LINES, True)}, "Add Cfg")
    o = commit(repo, {CFG: cfg_text(CFG_LINES[:1] + old_lines + CFG_LINES[4:], True)}, "Change Cfg")
    g(repo, "checkout", "-q", "-b", "new", "base")
    clean = CFG_LINES[:4] + [new_last]
    nx = commit(repo, {CFG: cfg_text(clean, False)}, "Add Cfg")
    n = commit(repo, {CFG: cfg_text(clean[:1] + new_lines + clean[4:], False)}, "Change Cfg")
    return (ox, nx), (o, n)


def patches_of(repo, shas):
    return [cr.git(repo, "show", "--format=", "--no-color", "--no-renames", "-U0", sha)
            for sha in shas]


def map_whitespace_apart(repo, tmp_path, fake_range_diff, add, change):
    fake_range_diff["text"] = (f"1:  {add[0][:7]} = 1:  {add[1][:7]} Add Cfg\n"
                               f"2:  {change[0][:7]} ! 2:  {change[1][:7]} Change Cfg\n")
    return run_map(repo, tmp_path, [(add[0], add[1], None), (change[0], change[1], None)])


def test_hunks_aligned_differently_by_whitespace_apart_parents_still_map(
        repo, tmp_path, fake_range_diff):
    add, change = whitespace_apart_chains(repo, [""], [""])
    diffs = patches_of(repo, change)
    # the scenario is real: the positional comparison cannot tell they agree
    assert cr.significant_changes(diffs[0]) != cr.significant_changes(diffs[1])
    assert cr.net_changes(diffs[0]) == cr.net_changes(diffs[1])
    rc, doc = map_whitespace_apart(repo, tmp_path, fake_range_diff, add, change)
    assert rc == 0, doc["errors"]
    assert doc["pairs"][1]["class"] == "import-or-whitespace"


def test_a_real_token_difference_is_still_unexplained_with_whitespace_apart_parents(
        repo, tmp_path, fake_range_diff):
    add, change = whitespace_apart_chains(repo, [""], ["\t\thidden = true"])
    assert cr.net_changes(patches_of(repo, change)[0]) != cr.net_changes(patches_of(repo, change)[1])
    rc, doc = map_whitespace_apart(repo, tmp_path, fake_range_diff, add, change)
    assert rc == 1 and doc["pairs"][1]["class"] == "unexplained"


def test_equal_net_change_but_a_tree_differing_in_a_kept_line_is_unexplained(
        repo, tmp_path, fake_range_diff):
    # same net lines, hunks aligned differently, but the new tree differs from
    # the old one in a line neither patch touches
    add, change = whitespace_apart_chains(repo, [""], [""], new_last="\t\tposition = 9,")
    diffs = patches_of(repo, change)
    assert cr.significant_changes(diffs[0]) != cr.significant_changes(diffs[1])
    assert cr.net_changes(diffs[0]) == cr.net_changes(diffs[1])
    rc, doc = map_whitespace_apart(repo, tmp_path, fake_range_diff, add, change)
    assert rc == 1 and doc["pairs"][1]["class"] == "unexplained"


def test_the_same_lines_added_in_another_order_are_unexplained(repo, tmp_path):
    g(repo, "branch", "base")
    g(repo, "checkout", "-q", "-b", "old")
    o = commit(repo, {MAIN_BAR: BAR.replace("\tint v = 1;\n", "\tint v = 1;\n\tint w = 2;\n\tint z = 3;\n")},
               "Bar two")
    g(repo, "checkout", "-q", "-b", "new", "base")
    n = commit(repo, {MAIN_BAR: BAR.replace("\tint v = 1;\n", "\tint v = 1;\n\tint z = 3;\n\tint w = 2;\n")},
               "Bar two")
    diffs = patches_of(repo, (o, n))
    assert cr.net_changes(diffs[0]) == cr.net_changes(diffs[1])
    rc, doc = run_map(repo, tmp_path, [(o, n, None)])
    assert rc == 1 and doc["pairs"][0]["class"] == "unexplained"


def test_net_changes_ignores_imports_whitespace_and_balanced_lines():
    diff = ("diff --git a/x/A.java b/x/A.java\n@@ -1,2 +1,2 @@\n"
            "-import a.B;\n-  keep  ;\n+ keep ;\n+int  x;\n")
    assert cr.net_changes(diff) == {"x/A.java": {"int x;": 1}}


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


# ---------- the per-tag gate ----------

CPS_BASE = ("package shortestpath.requirement;\n\npublic final class ClientPlayerStateSource\n"
            "{\n\tint COL_QUESTPOINTS = 1;\n}\n")
CPS_FIXED = CPS_BASE.replace("COL_QUESTPOINTS", "COL_QUESTPOINTS_OVERRIDE")
CPS = cr.FIX_FILE
GATE_FOO_BASE = ("package acme;\n\nimport acme.util.Alpha;\nimport acme.util.Beta;\n"
                 "import acme.util.Gamma;\n\nclass Foo\n{\n\tAlpha a; \n\tBeta b;\n}\n")
GATE_FOO_CLEAN = GATE_FOO_BASE.replace("import acme.util.Gamma;\n", "").replace("a; \n", "a;\n")
GATE_FOO_WILD = GATE_FOO_CLEAN.replace("import acme.util.Alpha;\nimport acme.util.Beta;\n",
                                       "import acme.util.*;\n")
RSBFOX = ("rsbfox", "aaron.crts@gmail.com")
CLEANUP_SUBJECT = "style: remove unused imports and trailing whitespace"
WILDCARD_SUBJECT = "refactor: use wildcard imports where a file imports two or more types"
MARKER_SUBJECT = "Attributed source reductions for v1.22.2"
MAINTAINER = ("Mae", "mae@example.com")


class Scene:
    pass


def gate_scene(tmp_path, fix_author=RSBFOX, fix_message="fix shortest path (#929)",
               cleanup_files=None, wildcard_files=None, cleanup_message=CLEANUP_SUBJECT,
               foreign=False, marker_author=MAINTAINER, old_pick=False):
    """A tiny rebuilt .2: P -> OLD(marker) on the old line; P -> FIX -> CLEANUP -> WILDCARD
    -> MARKER on the new line."""
    s = Scene()
    tmp_path.mkdir(parents=True, exist_ok=True)
    r = s.repo = tmp_path / "gate-repo"
    r.mkdir()
    g(r, "init", "-q", "-b", "main")
    s.prev = commit(r, {CPS: CPS_BASE, "src/main/java/acme/Foo.java": GATE_FOO_BASE,
                        "build.gradle": "plugins {}\n", ".gitignore": "build/\n"}, "previous")
    g(r, "checkout", "-q", "-b", "old")
    if old_pick:
        s.old_pick = commit(r, {"src/main/java/acme/Other.java": "package acme;\nclass Other {}\n"},
                            "an old pick")
    s.old = commit(r, {}, MARKER_SUBJECT, MAINTAINER, allow_empty=True)
    g(r, "checkout", "-q", "-b", "new", s.prev)
    s.fix = commit(r, {CPS: CPS_FIXED}, fix_message, fix_author)
    s.fix_patch = tmp_path / "fix.patch"
    s.fix_patch.write_text(subprocess.run(["git", "-C", str(r), "show", "--format=", s.fix],
                                          capture_output=True, text=True, check=True).stdout)
    s.cleanup = commit(r, cleanup_files or {"src/main/java/acme/Foo.java": GATE_FOO_CLEAN},
                       cleanup_message)
    s.wildcard = commit(r, wildcard_files or {"src/main/java/acme/Foo.java": GATE_FOO_WILD},
                        WILDCARD_SUBJECT)
    if foreign:
        s.foreign = commit(r, {"src/main/java/acme/Other.java": "package acme;\nclass Other {}\n"},
                           "an unrelated commit")
    s.new = commit(r, {}, MARKER_SUBJECT, marker_author, allow_empty=True)
    s.inserted = [s.fix, s.cleanup, s.wildcard]
    s.classpath = tmp_path / "cp.txt"
    s.classpath.write_text("/m2/lombok-1.18.30.jar\n")
    s.tags_before = tmp_path / "tags-before.txt"
    s.tags_before.write_text("aaa\trefs/tags/v1.22.2\nbbb\trefs/tags/v1.22.3\n")
    s.out = tmp_path / "gate-out"
    s.worktree = tmp_path / "gate-wt"
    g(r, "worktree", "add", "-q", "--detach", str(s.worktree), s.new)
    return s


@pytest.fixture
def seams(monkeypatch):
    """Fake the slow and the remote seams; each can be tuned through the box."""
    box = {"gradle_rc": 0, "tests": 12, "deps": "+--- net.runelite:client:latest.release -> 1.13.6\n",
           "bytecode_rc": 0, "totals": None, "remote": "aaa\trefs/tags/v1.22.2\nbbb\trefs/tags/v1.22.3\n",
           "rebuild_branches": "", "remote_rc": 0, "calls": []}

    def gradle(worktree, *tasks):
        box["calls"].append(tasks)
        if tasks[0] == "dependencies":
            return box["deps"], 0
        d = Path(worktree) / "build/test-results/test"
        d.mkdir(parents=True, exist_ok=True)
        (d / "TEST-a.xml").write_text(f'<testsuite name="a" tests="{box["tests"]}"/>')
        return "BUILD", box["gradle_rc"]

    def tokens(repo, refs):
        # the first ref is OLD (largest), the last NEW (smallest)
        totals = box["totals"] or {ref: 1000 - 10 * i for i, ref in enumerate(refs)}
        out = "".join(f"# ref {ref}\n\nTOTAL\t{totals[ref]} tokens\t3 files\n" for ref in refs)
        return out, 0

    def bytecode(repo, base, head, classpath, work):
        return box["bytecode_rc"], "identical" if box["bytecode_rc"] == 0 else "class files differ"

    def remote(repo, name, *patterns):
        if box["remote_rc"]:
            return box["remote_rc"], "", "network down"
        if "--heads" in patterns:
            return 0, box["rebuild_branches"], ""
        return 0, box["remote"], ""

    monkeypatch.setattr(cr, "gradle_run", gradle)
    monkeypatch.setattr(cr, "measure_tokens", tokens)
    monkeypatch.setattr(cr, "bytecode_identity", bytecode)
    monkeypatch.setattr(cr, "ls_remote", remote)
    monkeypatch.setattr(cr, "UNIVERSE", MINI)
    return box


def ctx_of(s, **over):
    args = dict(repo=s.repo, worktree=s.worktree, tag="v1.22.2", old=s.old, new=s.new,
                prev_old=s.prev, prev_new=s.prev, inserted=s.inserted, fix_patch=s.fix_patch,
                classpath_file=s.classpath, tags_before=s.tags_before, out=s.out, logs=[])
    args.update(over)
    return cr.Ctx(**args)


def gate_argv(s, *extra):
    return ["gate", "--repo", str(s.repo), "--worktree", str(s.worktree), "--tag", "v1.22.2",
            "--old", s.old, "--new", s.new, "--prev-old", s.prev, "--prev-new", s.prev,
            "--inserted", ",".join(s.inserted), "--fix-patch", str(s.fix_patch),
            "--classpath-file", str(s.classpath), "--tags-before", str(s.tags_before),
            "--out", str(s.out), *extra]


def test_gate_passes_all_eight_gates_and_writes_gate_json(tmp_path, seams, capsys):
    s = gate_scene(tmp_path)
    assert cr.main(gate_argv(s)) == 0
    doc = json.loads((s.out / "gate.json").read_text())
    assert list(doc["gates"]) == ["G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8"]
    assert all(v["pass"] for v in doc["gates"].values()), doc["gates"]
    assert doc["tag"] == "v1.22.2" and doc["old"] == s.old and doc["new"] == s.new
    assert doc["inserted"] == s.inserted and doc["pass"] is True
    g1 = doc["gates"]["G1"]["detail"]
    assert g1["tests"] == 12 and g1["runelite_client"] == "1.13.6"
    assert "all gates pass" in capsys.readouterr().out


def test_the_gradle_seam_gets_an_absolute_worktree_path(tmp_path, monkeypatch):
    calls = []
    monkeypatch.setattr(cr, "run", lambda argv, **kw: calls.append(argv) or
                        subprocess.CompletedProcess(argv, 0, b"", b""))
    monkeypatch.chdir(tmp_path)
    cr.gradle_run(Path("some-wt"), "test")
    argv = calls[0]
    assert Path(argv[argv.index("-p") + 1]) == (tmp_path / "some-wt").resolve()
    assert "--no-daemon" in argv


def test_gate_g1_runs_the_four_tasks_without_the_daemon(tmp_path, seams):
    s = gate_scene(tmp_path)
    cr.main(gate_argv(s))
    assert ("compileTestJava", "test", "checkstyleMain", "checkstyleTest") in seams["calls"]


def test_a_failing_gate_makes_the_command_exit_1_but_the_others_still_run(tmp_path, seams):
    seams["remote"] = "ccc\trefs/tags/v1.22.2\nbbb\trefs/tags/v1.22.3\n"
    s = gate_scene(tmp_path)
    assert cr.main(gate_argv(s)) == 1
    doc = json.loads((s.out / "gate.json").read_text())
    assert [k for k, v in doc["gates"].items() if not v["pass"]] == ["G8"]
    assert doc["pass"] is False


def test_a_dirty_worktree_or_one_not_at_new_exits_2(tmp_path, seams, capsys):
    s = gate_scene(tmp_path)
    (s.worktree / "src/main/java/acme/Foo.java").write_text("dirty\n")
    assert cr.main(gate_argv(s)) == 2
    assert "is not clean" in capsys.readouterr().err
    g(s.worktree, "checkout", "-q", "--", ".")
    g(s.worktree, "checkout", "-q", "--detach", s.fix)
    assert cr.main(gate_argv(s)) == 2
    assert "is not at" in capsys.readouterr().err
    assert not (s.out / "gate.json").exists()


def test_a_missing_input_file_exits_2(tmp_path, seams, capsys):
    s = gate_scene(tmp_path)
    s.fix_patch.unlink()
    assert cr.main(gate_argv(s)) == 2
    assert "fix patch" in capsys.readouterr().err


# ---- G1

def test_g1_fails_on_a_red_build_and_on_zero_tests(tmp_path, seams):
    s = gate_scene(tmp_path)
    seams["gradle_rc"] = 1
    ok, detail = cr.gate_g1(ctx_of(s))
    assert not ok and detail["gradle_exit"] == 1
    seams["gradle_rc"], seams["tests"] = 0, 0
    ok, detail = cr.gate_g1(ctx_of(s))
    assert not ok and detail["tests"] == 0


def test_g1_records_the_unpinned_client_version_it_resolved(tmp_path, seams):
    s = gate_scene(tmp_path)
    seams["deps"] = "+--- net.runelite:client:1.13.7\n"
    assert cr.gate_g1(ctx_of(s))[1]["runelite_client"] == "1.13.7"
    seams["deps"] = "nothing useful\n"
    assert cr.gate_g1(ctx_of(s))[1]["runelite_client"] == "unresolved"


# ---- G2

def test_g2_passes_on_the_expected_shape(tmp_path, seams):
    s = gate_scene(tmp_path)
    ok, detail = cr.gate_g2(ctx_of(s))
    assert ok, detail
    assert detail["fix_commits"] == [s.fix]


def test_g2_flags_an_attribution_trailer_in_an_inserted_commit(tmp_path, seams):
    s = gate_scene(tmp_path, cleanup_message=CLEANUP_SUBJECT + "\n\nCo-Authored-By: Someone <s@x>")
    ok, detail = cr.gate_g2(ctx_of(s))
    assert not ok and any("attribution trailer" in p for p in detail["problems"])


def test_g2_flags_an_internal_planning_label(tmp_path, seams):
    s = gate_scene(tmp_path, cleanup_message=CLEANUP_SUBJECT + "\n\nDone for D" + "-12.")
    ok, detail = cr.gate_g2(ctx_of(s))
    assert not ok and any("planning label" in p for p in detail["problems"])


def test_g2_flags_a_compile_fix_with_another_author(tmp_path, seams):
    s = gate_scene(tmp_path, fix_author=("Ann", "ann@example.com"))
    ok, detail = cr.gate_g2(ctx_of(s))
    assert not ok and any("authored by Ann" in p for p in detail["problems"])


def test_g2_flags_a_compile_fix_that_differs_from_the_fix_patch(tmp_path, seams):
    s = gate_scene(tmp_path)
    s.fix_patch.write_text(s.fix_patch.read_text().replace("COL_QUESTPOINTS_OVERRIDE", "OTHER"))
    ok, detail = cr.gate_g2(ctx_of(s))
    assert not ok and any("differs from the fix patch" in p for p in detail["problems"])


def test_g2_flags_a_commit_that_is_neither_picked_nor_inserted(tmp_path, seams):
    s = gate_scene(tmp_path, foreign=True)
    ok, detail = cr.gate_g2(ctx_of(s))
    assert not ok and any(s.foreign in p for p in detail["problems"])


def test_g2_flags_a_tip_that_is_not_an_empty_remark_of_the_old_marker(tmp_path, seams):
    s = gate_scene(tmp_path, marker_author=("Zed", "zed@example.com"))
    ok, detail = cr.gate_g2(ctx_of(s))
    assert not ok and any(s.new in p for p in detail["problems"])


def test_g2_flags_a_change_to_a_gradle_file_and_to_a_non_java_file(tmp_path, seams):
    s = gate_scene(tmp_path, cleanup_files={"src/main/java/acme/Foo.java": GATE_FOO_CLEAN,
                                            "build.gradle": "plugins { id 'x' }\n",
                                            "data.tsv": "a\n"})
    ok, detail = cr.gate_g2(ctx_of(s))
    assert not ok
    assert any("build files differ" in p for p in detail["problems"])
    assert any("non-java files differ" in p for p in detail["problems"])


def test_g2_accepts_logged_picks_between_the_previous_tip_and_the_new_one(tmp_path, seams):
    s = gate_scene(tmp_path)
    log = tmp_path / "picks.json"
    write_log(log, [(s.old, s.new, None)])
    ok, detail = cr.gate_g2(ctx_of(s, logs=[str(log)], inserted=s.inserted))
    assert ok, detail


# ---- G3, G4, G5

def test_g3_passes_a_cleanup_that_only_removes_imports_and_whitespace(tmp_path, seams):
    s = gate_scene(tmp_path)
    ok, detail = cr.gate_g3(ctx_of(s))
    assert ok and detail["files"] == 1


def test_g3_fails_a_cleanup_that_adds_an_import_or_changes_code(tmp_path, seams):
    s = gate_scene(tmp_path, cleanup_files={
        "src/main/java/acme/Foo.java": GATE_FOO_CLEAN.replace(
            "import acme.util.Beta;\n", "import acme.util.Beta;\nimport java.io.File;\n")})
    ok, detail = cr.gate_g3(ctx_of(s))
    assert not ok and any("adds an import" in o for o in detail["offences"])
    s2 = gate_scene(tmp_path / "second", cleanup_files={
        "src/main/java/acme/Foo.java": GATE_FOO_CLEAN.replace("Beta b;", "Beta c;")})
    ok, detail = cr.gate_g3(ctx_of(s2))
    assert not ok


def test_g3_needs_exactly_one_style_commit(tmp_path, seams):
    s = gate_scene(tmp_path, cleanup_message="chore: tidy")
    ok, detail = cr.gate_g3(ctx_of(s))
    assert not ok and "expected one" in detail["error"]


def test_g4_passes_when_the_wildcard_commit_is_the_rewriters_output(tmp_path, seams):
    s = gate_scene(tmp_path)
    ok, detail = cr.gate_g4(ctx_of(s))
    assert ok, detail
    assert detail["check_exit"] == 0 and detail["bytecode_exit"] == 0


def test_g4_fails_when_the_wildcard_commit_differs_from_the_rewriters_output(tmp_path, seams):
    s = gate_scene(tmp_path, wildcard_files={
        "src/main/java/acme/Foo.java": GATE_FOO_WILD.replace("import acme.util.*;\n",
                                                             "import acme.util.*;\n\n")})
    ok, detail = cr.gate_g4(ctx_of(s))
    assert not ok
    assert any("not the rewriter's output" in p for p in detail["problems"])


def test_g4_fails_when_new_is_not_canonical(tmp_path, seams):
    s = gate_scene(tmp_path, wildcard_files={"src/main/java/acme/Foo.java": GATE_FOO_CLEAN + "// note\n"})
    ok, detail = cr.gate_g4(ctx_of(s))
    assert not ok and detail["check_exit"] == 1


def test_g4_fails_when_the_bytecode_differs(tmp_path, seams):
    s = gate_scene(tmp_path)
    seams["bytecode_rc"] = 1
    ok, detail = cr.gate_g4(ctx_of(s))
    assert not ok and detail["bytecode_exit"] == 1


def test_g5_passes_for_old_plus_the_fix_against_new(tmp_path, seams):
    s = gate_scene(tmp_path)
    ok, detail = cr.gate_g5(ctx_of(s))
    assert ok, detail
    assert detail["java_files_differing"] >= 1


def test_g5_fails_on_different_bytecode_and_on_a_code_change_in_any_java_file(tmp_path, seams):
    s = gate_scene(tmp_path)
    seams["bytecode_rc"] = 1
    assert not cr.gate_g5(ctx_of(s))[0]
    seams["bytecode_rc"] = 0
    s2 = gate_scene(tmp_path / "second", cleanup_files={
        "src/main/java/acme/Foo.java": GATE_FOO_CLEAN,
        "src/test/java/acme/FooTest.java": "package acme;\nclass FooTest {}\n"})
    ok, detail = cr.gate_g5(ctx_of(s2))
    assert not ok and any("FooTest.java" in p for p in detail["problems"])


# ---- G6

def test_g6_keys_every_total_by_the_sha_it_was_measured_on(tmp_path, seams):
    s = gate_scene(tmp_path)
    ok, detail = cr.gate_g6(ctx_of(s))
    assert ok
    assert detail["old_ref"] == s.old and detail["new_ref"] == s.new
    assert set(detail["totals"]) == {s.old, s.fix, s.cleanup, s.wildcard, s.new}
    assert [x["label"].split(":")[0] for x in detail["steps"]] == [
        "old", "inserted", "inserted", "inserted", "new"]


def test_g6_fails_when_new_is_not_below_old(tmp_path, seams):
    s = gate_scene(tmp_path)
    seams["totals"] = {s.old: 10, s.fix: 11, s.cleanup: 12, s.wildcard: 13, s.new: 13}
    assert not cr.gate_g6(ctx_of(s))[0]


def test_g6_measures_the_previous_tip_only_when_it_differs_from_the_old_one(tmp_path, seams):
    s = gate_scene(tmp_path)
    labels = [x["label"] for x in cr.gate_g6(ctx_of(s))[1]["steps"]]
    assert "prev_new" not in labels
    ok, detail = cr.gate_g6(ctx_of(s, prev_new=s.fix))
    steps = detail["steps"]
    assert [x["label"] for x in steps][:2] == ["old", "prev_new"]
    assert steps[1]["ref"] == s.fix and len([x for x in steps if x["ref"] == s.fix]) == 1


def test_g6_measures_the_last_pick_when_there_are_logs(tmp_path, seams):
    s = gate_scene(tmp_path)
    log = tmp_path / "picks.json"
    write_log(log, [(s.old, s.cleanup, None)])
    steps = cr.gate_g6(ctx_of(s, logs=[str(log)]))[1]["steps"]
    assert "last_pick" in [x["label"] for x in steps]


# ---- G7

def test_g7_without_picks_wants_exactly_the_inserted_commits_plus_the_marker(tmp_path, seams):
    s = gate_scene(tmp_path)
    assert cr.gate_g7(ctx_of(s))[0]
    s2 = gate_scene(tmp_path / "second", foreign=True)
    ok, detail = cr.gate_g7(ctx_of(s2))
    assert not ok and any("exactly the inserted" in p for p in detail["problems"])


def test_g7_without_logs_fails_when_the_old_segment_holds_picks(tmp_path, seams):
    s = gate_scene(tmp_path, old_pick=True)
    ok, detail = cr.gate_g7(ctx_of(s))
    assert not ok and any("no pick log" in p for p in detail["problems"])


def test_g7_with_logs_runs_the_map_and_copies_the_accepted_pairings(
        tmp_path, seams, fake_range_diff):
    s = gate_scene(tmp_path)
    log = tmp_path / "picks.json"
    write_log(log, [(s.old, s.new, None)], {s.old: MARKER_SUBJECT})
    fake_range_diff["text"] = (
        f"1:  {s.old[:7]} = 4:  {s.new[:7]} {MARKER_SUBJECT}\n"
        + "".join(f"-:  ------- > {i}:  {c[:7]} inserted\n"
                  for i, c in enumerate(s.inserted, 1)))
    ok, detail = cr.gate_g7(ctx_of(s, logs=[str(log)]))
    assert ok, detail
    assert detail["range_diff_accepted"] == [] and detail["errors"] == []
    assert (s.out / "map.json").exists()


# ---- G8

def test_g8_passes_when_the_tags_and_branches_are_untouched(tmp_path, seams):
    s = gate_scene(tmp_path)
    assert cr.gate_g8(ctx_of(s))[0]


def test_g8_fails_when_an_upstream_tag_moved(tmp_path, seams):
    seams["remote"] = "zzz\trefs/tags/v1.22.2\nbbb\trefs/tags/v1.22.3\n"
    ok, detail = cr.gate_g8(ctx_of(gate_scene(tmp_path)))
    assert not ok and "moved" in detail["problems"][0]


def test_g8_fails_when_a_rebuild_branch_exists_or_ls_remote_fails(tmp_path, seams):
    s = gate_scene(tmp_path)
    seams["rebuild_branches"] = "ddd\trefs/heads/rebuild/wildcards\n"
    ok, detail = cr.gate_g8(ctx_of(s))
    assert not ok and any("rebuild/" in p for p in detail["problems"])
    seams["rebuild_branches"], seams["remote_rc"] = "", 128
    ok, detail = cr.gate_g8(ctx_of(s))
    assert not ok and "ls-remote" in detail["error"]
