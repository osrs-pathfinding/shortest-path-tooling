"""Tests for ``scripts/wildcard_imports.py``.

Java sources are inline strings; the universe is the small pinned fixture
``tests/fixtures/wildcard_universe_mini.tsv``.  The module's ``git`` seam is
replaced by a fake that serves a dict of files, so no test touches a real
repository.  The script is loaded via importlib because ``scripts/`` has no
``__init__.py``.
"""

import importlib.util
import json
import subprocess
import sys
import zipfile
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "wildcard_imports.py"
FIXTURES = Path(__file__).resolve().parent / "fixtures"
MINI = FIXTURES / "wildcard_universe_mini.tsv"

spec = importlib.util.spec_from_file_location("wildcard_imports", SCRIPT_PATH)
wi = importlib.util.module_from_spec(spec)
sys.modules["wildcard_imports"] = wi
spec.loader.exec_module(wi)

SRC = "src/main/java"


def cp(cmd, stdout="", stderr="", rc=0):
    return subprocess.CompletedProcess(cmd, rc, stdout, stderr)


def universe():
    return wi.load_universe(MINI)


def java(pkg, imports, uses=(), name="A", extra=""):
    """A compilation unit importing `imports` and mentioning every name in `uses`."""
    refs = ", ".join(f"{u}.class" for u in uses)
    body = f"    Object[] o = {{ {refs} }};\n" if uses else ""
    imp = "\n".join(imports)
    return (f"package {pkg};\n\n{imp}\n\npublic class {name} {{\n{body}{extra}}}\n")


def path_of(pkg, name):
    return f"{SRC}/{pkg.replace('.', '/')}/{name}.java"


def rewrite(pkg, imports, uses=(), others=None, name="A", **kw):
    """Rewrite one file (plus optional {path: text} siblings); returns Result."""
    text = java(pkg, imports, uses, name)
    path = path_of(pkg, name)
    texts = {path: text, **(others or {})}
    env = wi.make_env(universe(), texts)
    return wi.rewrite_text(text, path, env, **kw)


def import_block(text):
    return [ln for ln in text.splitlines() if ln.startswith("import ")]


def fake_git(files):
    """A `git` seam serving {path: text} for ls-tree and show."""
    def fake(repo, *args):
        if args[0] == "ls-tree":
            return "\n".join(sorted(files)) + "\n"
        if args[0] == "show":
            return files[args[1].split(":", 1)[1]]
        raise AssertionError(f"unexpected git call {args}")
    return fake


# ---------- source analysis ----------

def test_mask_source_blanks_comments_and_literals_keeping_lines():
    text = 'int a; // import x.Y;\n/* b\n c */ String s = "//no";\nchar c = \'"\';\n'
    code, docs = wi.mask_source(text)
    assert code.count("\n") == text.count("\n")
    assert "import" not in code and "no" not in code and "b" not in code.replace("int", "")
    assert docs == []


def test_javadoc_reference():
    text = ("package p;\n\nimport java.util.Set;\n\n/** See {@link Map} and "
            "{@see Optional}.\n * @throws IOException on failure\n */\nclass A {}\n")
    src = wi.analyse("A.java", text)
    assert {"Map", "Optional", "IOException"} <= src.refs
    assert "Set" not in src.refs  # an import line is not a mention


def test_references_skip_names_after_a_dot():
    src = wi.analyse("A.java", "package p;\n\nclass A { Object x = Foo.Bar.baz(); }\n")
    assert "Foo" in src.refs and "Bar" not in src.refs and "baz" not in src.refs


def test_commented_import():
    text = ("package p;\n\n// import java.util.Map;\n/*\nimport java.util.Set;\n*/\n"
            "import java.util.List;\n\nclass A { List l; }\n")
    src = wi.analyse("A.java", text)
    assert [i.qualifier for i in src.imports] == ["java.util.List"]


def test_trailing_comment():
    text = "package p;\n\nimport java.util.List; // why\n\nclass A {}\n"
    with pytest.raises(wi.UnusableInput, match=r"A\.java:3.*comment"):
        wi.analyse("A.java", text)


def test_second_statement_on_a_line():
    text = "package p;\n\nimport java.util.List; import java.util.Map;\n\nclass A {}\n"
    with pytest.raises(wi.UnusableInput, match=r"A\.java:3"):
        wi.analyse("A.java", text)


@pytest.mark.parametrize("stmt", ["import java.util.Li-st;", "import java.util..List;",
                                  "import java.util.List", "import 1java.List;"])
def test_bad_qualifier(stmt):
    text = f"package p;\n\n{stmt}\n\nclass A {{}}\n"
    with pytest.raises(wi.UnusableInput, match=r"A\.java:3"):
        wi.analyse("A.java", text)


def test_text_block_is_unusable():
    with pytest.raises(wi.UnusableInput, match="text blocks"):
        wi.analyse("A.java", 'package p;\nclass A { String s = """\nx"""; }\n')


def test_own_packages_reads_top_level_types():
    a = wi.analyse("a/A.java", "package p;\nclass A { class Inner {} }\ninterface B {}\n")
    b = wi.analyse("q/C.java", "package q;\nenum C { X }\n")
    assert wi.own_packages([a, b]) == {"p": {"A", "B"}, "q": {"C"}}


def test_member_names_lists_declared_members_only():
    code, _ = wi.mask_source(
        "package p;\npublic class C {\n  public static final int X = 1, Y;\n"
        "  static int f(int local, String arg) { int inner = 2; return inner; }\n"
        "  static class Nested { int deep; }\n}\n")
    names = wi.member_names(code)
    assert {"X", "Y", "f", "Nested"} <= names
    assert not {"local", "arg", "inner", "deep", "static", "int"} & names


# ---------- universe file ----------

def test_universe_without_the_pin_is_refused(tmp_path):
    bad = tmp_path / "u.tsv"
    bad.write_text("# wildcard-type-universe v1\n# runelite: 1.13.5\njava.util\tList\n")
    with pytest.raises(wi.UnusableInput, match="1.13.6"):
        wi.load_universe(bad)


def test_universe_with_a_wrong_first_line_is_refused(tmp_path):
    bad = tmp_path / "u.tsv"
    bad.write_text("# runelite: 1.13.6\njava.util\tList\n")
    with pytest.raises(wi.UnusableInput, match="first line"):
        wi.load_universe(bad)


def test_rewrite_exits_2_on_an_unpinned_universe(tmp_path, capsys):
    bad = tmp_path / "u.tsv"
    bad.write_text("# wildcard-type-universe v1\njava.lang\tObject\n")
    (tmp_path / SRC).mkdir(parents=True)
    (tmp_path / SRC / "A.java").write_text("class A {}\n")
    assert wi.main(["rewrite", "--repo", str(tmp_path), "--universe", str(bad)]) == 2
    assert "1.13.6" in capsys.readouterr().err


# ---------- clash-safe collapsing ----------

def test_list_clash():
    res = rewrite("shortestpath", [
        "import java.awt.Color;", "import java.awt.Dimension;",
        "import java.util.ArrayList;", "import java.util.List;", "import java.util.Map;"],
        uses=["Color", "Dimension", "ArrayList", "List", "Map"])
    assert import_block(res.new) == [
        "import java.awt.*;", "import java.util.*;", "import java.util.List;"]
    assert res.new.splitlines()[2:6] == [
        "import java.awt.*;", "import java.util.*;", "import java.util.List;", ""]
    assert {"name": "List", "package": "java.util", "reason": "clash:java.awt"} \
        in res.report["kept_explicit"]


def test_point_clash():
    res = rewrite("shortestpath", [
        "import java.awt.Color;", "import java.awt.Dimension;",
        "import net.runelite.api.Client;", "import net.runelite.api.Player;",
        "import net.runelite.api.Point;"],
        uses=["Color", "Dimension", "Client", "Player", "Point"])
    assert import_block(res.new) == [
        "import java.awt.*;", "import net.runelite.api.*;", "import net.runelite.api.Point;"]


def test_gameval_clash():
    res = rewrite("shortestpath", [
        "import net.runelite.api.Client;", "import net.runelite.api.Player;",
        "import net.runelite.api.gameval.ItemID;", "import net.runelite.api.gameval.NpcID;",
        "import net.runelite.api.gameval.ObjectID;"],
        uses=["Client", "Player", "ItemID", "NpcID", "ObjectID"])
    assert import_block(res.new) == [
        "import net.runelite.api.*;", "import net.runelite.api.gameval.ItemID;",
        "import net.runelite.api.gameval.*;"]


def test_package_below_the_clash_adjusted_threshold_stays_explicit():
    # gameval keeps ItemID (api has one) and is left with one name: all explicit
    res = rewrite("shortestpath", [
        "import net.runelite.api.Client;", "import net.runelite.api.Player;",
        "import net.runelite.api.gameval.ItemID;", "import net.runelite.api.gameval.NpcID;"],
        uses=["Client", "Player", "ItemID", "NpcID"])
    assert import_block(res.new) == [
        "import net.runelite.api.*;", "import net.runelite.api.gameval.ItemID;",
        "import net.runelite.api.gameval.NpcID;"]


def test_single_import_of_a_package_stays_explicit():
    imports = ["import java.util.List;", "import net.runelite.api.Client;"]
    res = rewrite("shortestpath", imports, uses=["List", "Client"])
    assert res.new == java("shortestpath", imports, ["List", "Client"])
    assert not res.changed


def test_own_package_shadow():
    others = {path_of("shortestpath.x", "Bar"): "package shortestpath.x;\nclass Bar {}\n"}
    for n in ("Bar", "Baz", "Qux"):
        others[path_of("shortestpath.y", n)] = f"package shortestpath.y;\nclass {n} {{}}\n"
    res = rewrite("shortestpath.x", [
        "import shortestpath.y.Bar;", "import shortestpath.y.Baz;",
        "import shortestpath.y.Qux;"], uses=["Bar", "Baz", "Qux"], others=others)
    assert import_block(res.new) == ["import shortestpath.y.Bar;", "import shortestpath.y.*;"]
    assert res.report["kept_explicit"][0]["reason"] == "own-package"


def test_java_lang_keep():
    res = rewrite("shortestpath", [
        "import acme.util.Alpha;", "import acme.util.Beta;", "import acme.util.Number;"],
        uses=["Alpha", "Beta", "Number"])
    assert import_block(res.new) == ["import acme.util.*;", "import acme.util.Number;"]
    assert res.report["kept_explicit"][0]["reason"] == "java.lang"


def test_java_lang_shadow_skip():
    # the file means java.lang.Number; acme.util.* would make it ambiguous
    imports = ["import acme.util.Alpha;", "import acme.util.Beta;"]
    res = rewrite("shortestpath", imports, uses=["Alpha", "Beta", "Number"])
    assert not res.changed
    assert res.report["skipped"] == [{"package": "acme.util", "reason": "java.lang:Number"}]


def test_nested_qualifier():
    imports = ["import java.util.AbstractMap.SimpleEntry;",
               "import java.util.AbstractMap.SimpleImmutableEntry;"]
    res = rewrite("shortestpath", imports, uses=["SimpleEntry", "SimpleImmutableEntry"])
    assert not res.changed
    assert res.report["collapsed"] == []


def test_threshold_one_collapses_a_single_import():
    res = rewrite("shortestpath", ["import net.runelite.api.Client;"], uses=["Client"],
                  threshold=1)
    assert import_block(res.new) == ["import net.runelite.api.*;"]


# ---------- existing wildcards ----------

def test_existing_wildcard_fold():
    res = rewrite("shortestpath", ["import java.util.*;", "import java.util.Set;"],
                  uses=["Set", "Map"])
    assert import_block(res.new) == ["import java.util.*;"]


def test_existing_wildcard_expand():
    res = rewrite("shortestpath", ["import java.util.*;"], uses=["List"])
    assert import_block(res.new) == ["import java.util.List;"]
    assert res.report["expanded"] == [{"package": "java.util", "members": 1}]


def test_existing_wildcard_with_two_mentioned_members_is_unchanged():
    text = ("package shortestpath;\n\nimport java.util.*;\n\n/** Uses {@link Set}. */\n"
            "public class A { Object o = Map.class; }\n")
    env = wi.make_env(universe(), {path_of("shortestpath", "A"): text})
    res = wi.rewrite_text(text, path_of("shortestpath", "A"), env)
    assert not res.changed  # Map and Set are both mentioned, so the wildcard resolves them


def test_javadoc_reference_keeps_a_wildcard_member_during_expansion():
    text = ("package shortestpath;\n\nimport java.util.*;\n\n/** Uses {@link Set}. */\n"
            "public class A {}\n")
    env = wi.make_env(universe(), {path_of("shortestpath", "A"): text})
    res = wi.rewrite_text(text, path_of("shortestpath", "A"), env)
    assert import_block(res.new) == ["import java.util.Set;"]


def test_unresolved_wildcard():
    res = rewrite("shortestpath", ["import java.util.*;"], uses=["Color"])
    assert res.unresolved == ["java.util.*"]
    assert import_block(res.new) == []
    assert res.report["removed_unresolved"] == ["java.util"]


def test_ambiguous_wildcards():
    with pytest.raises(wi.UnusableInput, match="List is provided by both"):
        rewrite("shortestpath", ["import java.awt.*;", "import java.util.*;"],
                uses=["List"])


def test_expansion_keeps_a_name_that_would_clash():
    # java.util.* is existing; java.awt gets collapsed from singles; List is a
    # use of java.util's List, so it must stay explicit next to java.awt.*
    res = rewrite("shortestpath", [
        "import java.awt.Color;", "import java.awt.Dimension;", "import java.util.*;"],
        uses=["Color", "Dimension", "List", "Map", "Set"])
    assert import_block(res.new) == [
        "import java.awt.*;", "import java.util.*;", "import java.util.List;"]


NESTED_SHADOW = (
    "package shortestpath;\n\nimport java.util.*;\n\n"
    "class A { class List {} java.util.List<String> l; }\n"
    "class Z { Map<String, String> m; List<String> q; }\n")


def rewrite_text_alone(text, pkg="shortestpath", name="A", **kw):
    path = path_of(pkg, name)
    return wi.rewrite_text(text, path, wi.make_env(universe(), {path: text}), **kw)


def test_expansion_keeps_a_name_that_only_a_nested_type_shadows():
    # a nested List shadows the import inside A alone; Z still uses
    # java.util.List, so the expansion must keep it and the wildcard stays.
    res = rewrite_text_alone(NESTED_SHADOW)
    assert res.new == NESTED_SHADOW and res.unresolved == []


def test_expansion_keeps_a_nested_shadowed_name_when_the_wildcard_is_replaced():
    text = NESTED_SHADOW.replace("List<String> q; }", "List<String> q; Set<String> s; }")
    res = rewrite_text_alone(text, threshold=4)
    assert import_block(res.new) == [
        "import java.util.List;", "import java.util.Map;", "import java.util.Set;"]


def test_expansion_drops_a_name_that_a_nested_type_shadows_everywhere_it_is_used():
    # every mention of List lies inside A, whose nested List shadows the import
    text = ("package shortestpath;\n\nimport java.util.*;\n\n"
            "class A { class List {} List q; }\nclass Z { Map<String, String> m; "
            "Set<String> s; }\n")
    res = rewrite_text_alone(text, threshold=3)
    assert import_block(res.new) == ["import java.util.Map;", "import java.util.Set;"]


def test_expansion_drops_a_name_that_a_top_level_type_shadows():
    text = ("package shortestpath;\n\nimport java.util.*;\n\n"
            "class List {}\nclass Z { Map<String, String> m; Set<String> s; List q; }\n")
    res = rewrite_text_alone(text, threshold=3)
    assert import_block(res.new) == ["import java.util.Map;", "import java.util.Set;"]


# ---------- statics ----------

POH = ("package shortestpath.poh;\npublic class PohService {\n"
       "    public static final int POH_LANDING_X = 1;\n"
       "    public static final int POH_LANDING_Y = 2;\n    public static int other() { return 0; }\n}\n")
OTHER = ("package shortestpath.other;\npublic class Other {\n"
         "    public static final int A = 1;\n    public static final int B = 2;\n}\n")


def static_file(imports):
    return ("package shortestpath.pathfinder;\n\n" + "\n".join(imports) +
            "\n\npublic class T { int v = POH_LANDING_X + POH_LANDING_Y + A + B; }\n")


def rewrite_static(imports, **kw):
    text = static_file(imports)
    path = path_of("shortestpath.pathfinder", "T")
    texts = {path: text, path_of("shortestpath.poh", "PohService"): POH,
             path_of("shortestpath.other", "Other"): OTHER}
    return wi.rewrite_text(text, path, wi.make_env(universe(), texts), **kw)


def test_static_collapse():
    res = rewrite_static(["import static shortestpath.poh.PohService.POH_LANDING_X;",
                          "import static shortestpath.poh.PohService.POH_LANDING_Y;"])
    assert import_block(res.new) == ["import static shortestpath.poh.PohService.*;"]
    assert res.report["static_collapsed"] == [
        {"class": "shortestpath.poh.PohService", "members": 2}]


def test_single_static_import_stays():
    imports = ["import static shortestpath.poh.PohService.POH_LANDING_X;"]
    res = rewrite_static(imports)
    assert import_block(res.new) == imports


def test_static_two_classes():
    imports = ["import static shortestpath.other.Other.A;",
               "import static shortestpath.other.Other.B;",
               "import static shortestpath.poh.PohService.POH_LANDING_X;",
               "import static shortestpath.poh.PohService.POH_LANDING_Y;"]
    res = rewrite_static(imports)
    assert import_block(res.new) == imports
    assert res.report["static_left_explicit"] == [
        {"class": "shortestpath.other.Other", "members": 2,
         "reason": "two-or-more-static-classes"},
        {"class": "shortestpath.poh.PohService", "members": 2,
         "reason": "two-or-more-static-classes"}]
    totals = wi.summarize([res])["totals"]
    assert totals["static_groups_left_explicit"] == 2


def test_static_threshold_zero_disables_collapsing():
    imports = ["import static shortestpath.poh.PohService.POH_LANDING_X;",
               "import static shortestpath.poh.PohService.POH_LANDING_Y;"]
    res = rewrite_static(imports, static_threshold=0)
    assert import_block(res.new) == imports
    assert res.report["static_left_explicit"] == []


def test_static_group_of_a_class_outside_the_tree_stays_explicit():
    text = ("package shortestpath;\n\nimport static java.lang.Math.max;\n"
            "import static java.lang.Math.min;\n\npublic class A { int v = max(1, min(2, 3)); }\n")
    env = wi.make_env(universe(), {path_of("shortestpath", "A"): text})
    res = wi.rewrite_text(text, path_of("shortestpath", "A"), env)
    assert not res.changed
    assert res.report["static_left_explicit"][0]["reason"] == "class-not-in-tree"


def test_duplicate_import_lines_collapse_together():
    res = rewrite("shortestpath", [
        "import java.awt.Color;", "import java.awt.Color;", "import java.awt.Dimension;"],
        uses=["Color", "Dimension"])
    assert import_block(res.new) == ["import java.awt.*;"]


def test_duplicate_wildcards_fold_into_one():
    res = rewrite("shortestpath", ["import java.util.*;", "import java.util.*;"],
                  uses=["Map", "Set"])
    assert import_block(res.new) == ["import java.util.*;"]


def test_static_group_next_to_an_existing_static_wildcard_stays_explicit():
    text = static_file(["import static shortestpath.other.Other.*;",
                        "import static shortestpath.poh.PohService.POH_LANDING_X;",
                        "import static shortestpath.poh.PohService.POH_LANDING_Y;"])
    path = path_of("shortestpath.pathfinder", "T")
    texts = {path: text, path_of("shortestpath.poh", "PohService"): POH,
             path_of("shortestpath.other", "Other"): OTHER}
    res = wi.rewrite_text(text, path, wi.make_env(universe(), texts))
    assert not res.changed
    assert res.report["static_left_explicit"] == [
        {"class": "shortestpath.poh.PohService", "members": 2,
         "reason": "existing-static-wildcard"}]


def test_package_that_falls_below_the_threshold_reports_the_clashes_it_still_has():
    res = rewrite("shortestpath", ["import java.util.List;", "import java.util.Map;",
                                   "import java.awt.Color;", "import java.awt.Dimension;"],
                  uses=["List", "Map", "Color", "Dimension"])
    assert import_block(res.new) == ["import java.util.List;", "import java.util.Map;",
                                     "import java.awt.*;"]
    assert res.report["collapsed"] == [{"package": "java.awt", "members": 2}]
    assert [k["name"] for k in res.report["kept_explicit"]] == ["List"]


def test_import_from_the_default_package_is_unusable():
    with pytest.raises(wi.UnusableInput, match="default package"):
        rewrite("shortestpath", ["import Thing;"], uses=["Thing"])


def test_the_files_option_rejects_a_path_outside_the_tree(tmp_path):
    write_tree(tmp_path, tree_files())
    assert wi.main(["rewrite", "--repo", str(tmp_path), "--universe", str(MINI),
                    "--files", "src/main/java/nope/X.java"]) == 2


# ---------- unusable input ----------

def test_unknown_package():
    with pytest.raises(wi.UnusableInput, match="regenerate the universe"):
        rewrite("shortestpath", ["import com.unknown.Foo;", "import com.unknown.Bar;"],
                uses=["Foo", "Bar"])


def test_import_of_a_name_the_package_lacks():
    with pytest.raises(wi.UnusableInput, match="Nope is not a type of java.util"):
        rewrite("shortestpath", ["import java.util.Nope;"], uses=["Nope"])


# ---------- layout ----------

def test_unreferenced_single_import_is_neither_counted_nor_removed():
    res = rewrite("shortestpath", ["import java.util.List;", "import java.util.Map;"],
                  uses=["List"])
    assert not res.changed


def test_crlf_line_endings_are_preserved():
    text = java("shortestpath", ["import java.awt.Color;", "import java.awt.Dimension;"],
                ["Color", "Dimension"]).replace("\n", "\r\n")
    path = path_of("shortestpath", "A")
    res = wi.rewrite_text(text, path, wi.make_env(universe(), {path: text}))
    assert "import java.awt.*;\r\n" in res.new
    assert "\r\n\r\n" in res.new and "\n" not in res.new.replace("\r\n", "")


# ---------- idempotence ----------

def tree_files():
    files = {
        path_of("shortestpath", "A"): java("shortestpath", [
            "import java.awt.Color;", "import java.awt.Dimension;",
            "import java.util.ArrayList;", "import java.util.List;", "import java.util.Map;",
            "import net.runelite.api.Client;", "import net.runelite.api.Player;",
            "import net.runelite.api.Point;"],
            ["Color", "Dimension", "ArrayList", "List", "Map", "Client", "Player", "Point"]),
        path_of("shortestpath", "B"): java("shortestpath", [
            "import java.util.*;", "import java.util.Set;", "import acme.util.Alpha;",
            "import acme.util.Beta;", "import acme.util.Number;"],
            ["Set", "Map", "Alpha", "Beta", "Number"], name="B"),
        path_of("shortestpath", "C"): java("shortestpath", [
            "import java.util.AbstractMap.SimpleEntry;", "import java.io.File;"],
            ["SimpleEntry", "File"], name="C"),
        path_of("shortestpath.poh", "PohService"): POH,
        path_of("shortestpath.pathfinder", "T"): static_file([
            "import static shortestpath.poh.PohService.POH_LANDING_X;",
            "import static shortestpath.poh.PohService.POH_LANDING_Y;"]),
    }
    return files


def write_tree(root, files):
    for path, text in files.items():
        target = Path(root) / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(text.encode())


def test_idempotent(tmp_path):
    write_tree(tmp_path, tree_files())
    args = ["rewrite", "--repo", str(tmp_path), "--universe", str(MINI)]
    assert wi.main(args) == 0
    first = {p: (tmp_path / p).read_bytes() for p in tree_files()}
    assert first != {p: t.encode() for p, t in tree_files().items()}
    assert wi.main(args) == 0
    assert {p: (tmp_path / p).read_bytes() for p in tree_files()} == first
    results = wi.process_tree(wi.DiskTree(tmp_path), universe())
    assert not any(r.changed for r in results)


def test_rewrite_writes_only_changed_files_and_never_src_test(tmp_path):
    files = tree_files()
    test_file = f"src/test/java/shortestpath/ATest.java"
    files[test_file] = java("shortestpath", ["import java.awt.Color;", "import java.awt.Dimension;"],
                            ["Color", "Dimension"], name="ATest")
    write_tree(tmp_path, files)
    before_c = (tmp_path / path_of("shortestpath", "C")).stat().st_mtime_ns
    assert wi.main(["rewrite", "--repo", str(tmp_path), "--universe", str(MINI)]) == 0
    assert (tmp_path / test_file).read_text() == files[test_file]
    assert (tmp_path / path_of("shortestpath", "C")).stat().st_mtime_ns == before_c


def test_rewrite_files_option_limits_the_rewrite(tmp_path):
    write_tree(tmp_path, tree_files())
    only = path_of("shortestpath", "A")
    assert wi.main(["rewrite", "--repo", str(tmp_path), "--universe", str(MINI),
                    "--files", only]) == 0
    assert (tmp_path / path_of("shortestpath", "B")).read_text() == tree_files()[
        path_of("shortestpath", "B")]
    assert "import java.awt.*;" in (tmp_path / only).read_text()


# ---------- check ----------

def run_check(monkeypatch, files, *extra):
    monkeypatch.setattr(wi, "git", fake_git(files))
    return wi.main(["check", "--repo", "r", "--ref", "REF", "--universe", str(MINI), *extra])


def canonical_files(tmp_path):
    write_tree(tmp_path, tree_files())
    assert wi.main(["rewrite", "--repo", str(tmp_path), "--universe", str(MINI)]) == 0
    return {p: (tmp_path / p).read_text() for p in tree_files()}


def test_check_exits_0_on_canonical_output(tmp_path, monkeypatch, capsys):
    assert run_check(monkeypatch, canonical_files(tmp_path)) == 0
    assert "canonical" in capsys.readouterr().out


def test_check_exits_1_on_an_unrewritten_tree(monkeypatch, capsys):
    assert run_check(monkeypatch, tree_files()) == 1
    assert "not canonical" in capsys.readouterr().out


def test_check_unresolved_wildcard(monkeypatch, capsys):
    files = {path_of("shortestpath", "A"): java("shortestpath", ["import java.util.*;"],
                                                ["Color"])}
    assert run_check(monkeypatch, files) == 1
    assert "resolves no name" in capsys.readouterr().out


def test_check_ambiguous_wildcards_exit_2(monkeypatch, capsys):
    files = {path_of("shortestpath", "A"): java(
        "shortestpath", ["import java.awt.*;", "import java.util.*;"], ["List"])}
    assert run_check(monkeypatch, files) == 2
    assert "List is provided by both" in capsys.readouterr().err


def test_check_static_wildcard_of_a_foreign_class_exits_2(monkeypatch, capsys):
    text = ("package shortestpath;\n\nimport static java.lang.Math.*;\n\n"
            "public class A { int v = max(1, 2); }\n")
    assert run_check(monkeypatch, {path_of("shortestpath", "A"): text}) == 2
    assert "not a class of the tree" in capsys.readouterr().err


def test_check_static_wildcard_that_resolves_nothing(monkeypatch, capsys):
    files = {path_of("shortestpath.poh", "PohService"): POH,
             path_of("shortestpath", "A"): (
                 "package shortestpath;\n\nimport static shortestpath.poh.PohService.*;\n\n"
                 "public class A { int v = 1; }\n")}
    assert run_check(monkeypatch, files) == 1
    assert "static shortestpath.poh.PohService.*" in capsys.readouterr().out


OUTER = ("package shortestpath.nest;\npublic class Outer {\n"
         "    public static int bar;\n"
         "    public static class Inner { public static int foo; }\n}\n")


def nested_static_files(use):
    return {path_of("shortestpath.nest", "Outer"): OUTER,
            path_of("shortestpath", "A"): (
                "package shortestpath;\n\nimport static shortestpath.nest.Outer.Inner.*;\n\n"
                f"public class A {{ int v = {use}; }}\n")}


def test_check_static_wildcard_of_a_nested_class_resolves_its_own_members(monkeypatch, capsys):
    # foo is a member of Inner; reading Outer's members called it unresolved
    assert run_check(monkeypatch, nested_static_files("foo")) == 0
    assert "canonical" in capsys.readouterr().out


def test_check_static_wildcard_of_a_nested_class_ignores_the_outer_members(monkeypatch, capsys):
    # bar belongs to Outer, which Inner.* does not provide
    assert run_check(monkeypatch, nested_static_files("bar")) == 1
    assert "static shortestpath.nest.Outer.Inner.*" in capsys.readouterr().out


def test_check_static_wildcard_of_a_nested_class_that_cannot_be_located_exits_2(
        monkeypatch, capsys):
    files = nested_static_files("foo")
    files[path_of("shortestpath", "A")] = files[path_of("shortestpath", "A")].replace(
        "Outer.Inner.*", "Outer.Missing.*")
    assert run_check(monkeypatch, files) == 2
    assert "not a class of the tree" in capsys.readouterr().err


def test_nested_body_finds_a_direct_member_type_only():
    code, _ = wi.mask_source("class O { class I { int x; class J { int y; } } }\nclass Q { }\n")
    inner = wi.nested_body(code, "I")
    assert "int x" in inner and inner.startswith("{") and inner.endswith("}")
    assert wi.nested_body(code, "J") is None  # J sits in I, not directly in a top-level type
    assert "int y" in wi.nested_body(inner, "J")


def test_check_exits_2_on_a_ref_without_sources(monkeypatch, capsys):
    monkeypatch.setattr(wi, "git", lambda repo, *a: "")
    assert wi.main(["check", "--repo", "r", "--ref", "REF", "--universe", str(MINI)]) == 2


def test_check_report(tmp_path, monkeypatch):
    files = tree_files()
    write_tree(tmp_path / "wt", files)
    rewrite_report = tmp_path / "rewrite.json"
    check_report = tmp_path / "check.json"
    assert run_check(monkeypatch, files, "--report", str(check_report)) == 1
    assert wi.main(["rewrite", "--repo", str(tmp_path / "wt"), "--universe", str(MINI),
                    "--report", str(rewrite_report)]) == 0
    assert json.loads(check_report.read_text()) == json.loads(rewrite_report.read_text())
    totals = json.loads(check_report.read_text())["totals"]
    assert totals["static_collapsed"] == 1 and totals["kept_by_name"]["List"] == 1


# ---------- universe subcommand ----------

def make_jar(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as z:
        for e in entries:
            z.writestr(e, b"")
    return path


def gradle_jar(root, group, module, version, entries):
    return make_jar(root / "files-2.1" / group / module / version / ("a" * 40)
                    / f"{module}-{version}.jar", entries)


@pytest.fixture
def universe_env(tmp_path, monkeypatch):
    jars = [
        gradle_jar(tmp_path, "net.runelite", "client", "1.13.6",
                   ["net/runelite/client/ui/PluginPanel.class",
                    "net/runelite/client/ui/PluginPanel$Inner.class",
                    "net/runelite/client/ui/package-info.class"]),
        gradle_jar(tmp_path, "net.runelite", "runelite-api", "1.13.6",
                   ["net/runelite/api/Client.class"]),
        gradle_jar(tmp_path, "net.runelite", "discord", "1.4", ["net/runelite/discord/X.class"]),
        gradle_jar(tmp_path, "org.projectlombok", "lombok", "1.18.30", ["lombok/Getter.class"]),
    ]
    jdk = tmp_path / "jdk"
    make_jar(jdk / "lib" / "ct.sym", [
        "BCDEFG/java.base/java/lang/Object.sig", "BCDEFG/java.base/java/util/List.sig",
        "9A/java.base/java/util/Old.sig", "BCDEFG/java.base/java/util/Map$Entry.sig",
        "BCDEFG/java.desktop/java/awt/Color.sig"])
    cp_file = tmp_path / "cp.txt"
    cp_file.write_text("\n".join(str(j) for j in jars) + "\n")
    base_text = ("package shortestpath;\n\nimport java.util.List;\nimport java.awt.Color;\n"
                 "import net.runelite.api.Client;\nimport net.runelite.client.ui.PluginPanel;\n"
                 "import shortestpath.other.Thing;\n\nclass A {}\n")
    log = ("+import java.util.Map;\n+import static java.lang.Math.max;\n"
           "+import net.runelite.discord.*;\n+import java.util.Map.Entry;\n")

    def fake(repo, *args):
        if args[0] == "ls-tree":
            return f"{SRC}/shortestpath/A.java\n"
        if args[0] == "show":
            return base_text
        if args[0] == "log":
            return log
        if args[0] == "rev-parse":
            return "f" * 40 + "\n"
        raise AssertionError(args)

    monkeypatch.setattr(wi, "git", fake)
    monkeypatch.setattr(wi, "run", lambda argv: cp(argv, stdout="javac 17.0.18\n"))
    args = ["universe", "--repo", "r", "--classpath-file", str(cp_file),
            "--jdk-home", str(jdk), "--base-ref", "base", "--head-refs", "head",
            "--out", str(tmp_path / "u.tsv")]
    return tmp_path, cp_file, jars, args


def test_universe_subcommand(universe_env):
    tmp_path, _, _, args = universe_env
    assert wi.main(args) == 0
    text = (tmp_path / "u.tsv").read_text()
    lines = text.splitlines()
    assert lines[0] == "# wildcard-type-universe v1" and lines[1] == "# runelite: 1.13.6"
    assert "# runelite-pinned: net.runelite:client:1.13.6" in lines
    assert "# runelite-pinned: net.runelite:runelite-api:1.13.6" in lines
    assert any(ln.startswith("# runelite-unpinned: net.runelite:discord:1.4 sha256 ")
               and len(ln.split()[-1]) == 64 for ln in lines)
    assert any(ln.startswith("# jdk-api: release 11 from ct.sym of javac 17.0.18, sha256 ")
               for ln in lines)
    assert "# packages-from: base=" + "f" * 40 + " head=" + "f" * 40 in lines
    rows = [ln for ln in lines if not ln.startswith("#")]
    assert rows == sorted(rows)
    assert "java.util\tList" in rows and "java.util\tMap" not in rows  # Map: not in ct.sym here
    assert "java.util\tOld" not in rows and not any("$" in r for r in rows)
    assert "net.runelite.client.ui\tPluginPanel" in rows
    assert not any(r.startswith("net.runelite.client.ui\tpackage-info") for r in rows)
    assert "net.runelite.discord\tX" in rows
    assert not any(r.startswith("shortestpath") for r in rows)


def test_universe_names_the_javac_of_the_jdk_home_not_the_one_on_path(universe_env, monkeypatch):
    tmp_path, _, _, args = universe_env
    calls = []
    monkeypatch.setattr(wi, "run", lambda argv: calls.append(argv) or cp(argv, stderr="javac 11.0.2\n"))
    assert wi.main(args) == 0
    assert calls == [[str(tmp_path / "jdk" / "bin" / "javac"), "-version"]]
    assert "from ct.sym of javac 11.0.2, sha256 " in (tmp_path / "u.tsv").read_text()


def test_universe_exits_2_when_the_jdk_home_has_no_runnable_javac(universe_env, monkeypatch, capsys):
    tmp_path, _, _, args = universe_env

    def missing(argv):
        raise FileNotFoundError(2, "No such file or directory", argv[0])

    monkeypatch.setattr(wi, "run", missing)
    assert wi.main(args) == 2
    assert "bin/javac" in capsys.readouterr().err
    assert not (tmp_path / "u.tsv").exists()


def test_universe_unknown_package_exits_2(universe_env, tmp_path, monkeypatch, capsys):
    _, _, _, args = universe_env
    base_text = "package shortestpath;\n\nimport com.nowhere.Thing;\n\nclass A {}\n"
    orig = wi.git
    monkeypatch.setattr(wi, "git", lambda repo, *a: base_text if a[0] == "show" else orig(repo, *a))
    assert wi.main(args) == 2
    assert "com.nowhere" in capsys.readouterr().err


def test_universe_release_module_at_another_version_exits_2(universe_env, capsys):
    tmp_path, cp_file, jars, args = universe_env
    bad = gradle_jar(tmp_path / "other", "net.runelite", "client", "1.13.5", [])
    cp_file.write_text("\n".join(str(j) for j in [bad] + jars[1:]) + "\n")
    assert wi.main(args) == 2
    assert "1.13.6" in capsys.readouterr().err


def test_universe_missing_runelite_api_exits_2(universe_env, capsys):
    _, cp_file, jars, args = universe_env
    cp_file.write_text("\n".join(str(j) for j in jars if "runelite-api" not in str(j)) + "\n")
    assert wi.main(args) == 2
    assert "runelite-api" in capsys.readouterr().err


def test_universe_jar_outside_a_known_layout_exits_2(universe_env, tmp_path, capsys):
    _, cp_file, jars, args = universe_env
    stray = make_jar(tmp_path / "stray" / "x.jar", [])
    cp_file.write_text("\n".join(str(j) for j in jars + [stray]) + "\n")
    assert wi.main(args) == 2
    assert "cannot tell the group" in capsys.readouterr().err


def test_jar_coordinates_reads_gradle_and_maven_layouts():
    assert wi.jar_coordinates("/h/.gradle/caches/modules-2/files-2.1/net.runelite.arn/http-api/"
                              "1.2.26/" + "b" * 40 + "/http-api-1.2.26.jar") == (
        "net.runelite.arn", "http-api", "1.2.26")
    assert wi.jar_coordinates("/h/.m2/repository/com/google/code/findbugs/jsr305/3.0.2/"
                              "jsr305-3.0.2.jar") == ("com.google.code.findbugs", "jsr305", "3.0.2")


def test_universe_is_a_pure_function_of_its_inputs(universe_env):
    tmp_path, _, _, args = universe_env
    assert wi.main(args) == 0
    first = (tmp_path / "u.tsv").read_bytes()
    assert wi.main(args) == 0
    assert (tmp_path / "u.tsv").read_bytes() == first
