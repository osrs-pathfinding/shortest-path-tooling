"""Tests for ``scripts/maintenance.py``.

Every subprocess call goes through the module's single ``run`` seam, so
tests monkeypatch ``mm.run`` (or ``mm.subprocess.run`` for the seam
itself) and redirect ``mm.REPO``/``mm.SUBMODULE`` to ``tmp_path``
subdirs — no test touches the network, the real submodule, or the real
cache.  The script is loaded via importlib because ``scripts/`` has no
``__init__.py``.
"""

import importlib.util
import json
import shutil
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "maintenance.py"
FIXTURES = Path(__file__).resolve().parent / "fixtures"

spec = importlib.util.spec_from_file_location("maintenance", SCRIPT_PATH)
mm = importlib.util.module_from_spec(spec)
sys.modules["maintenance"] = mm
spec.loader.exec_module(mm)


def load_fixture(name):
    return json.loads((FIXTURES / name).read_text())


def cp(cmd, stdout="", stderr="", rc=0):
    return subprocess.CompletedProcess(cmd, rc, stdout, stderr)


def redirect_repo(tmp_path, monkeypatch):
    """Point the module's path constants at a scratch tree."""
    repo = tmp_path / "repo"
    submodule = repo / "shortest-path"
    submodule.mkdir(parents=True)
    monkeypatch.setattr(mm, "REPO", repo)
    monkeypatch.setattr(mm, "SUBMODULE", submodule)
    return repo, submodule


# ---------- run() seam ----------


def test_run_passes_list_argv_and_timeout(monkeypatch):
    calls = {}

    def fake_run(cmd, **kwargs):
        calls["cmd"] = cmd
        calls.update(kwargs)
        return cp(cmd)

    monkeypatch.setattr(mm.subprocess, "run", fake_run)
    mm.run(["git", "status"])
    assert isinstance(calls["cmd"], list)
    assert calls["cmd"] == ["git", "status"]
    assert isinstance(calls["timeout"], (int, float))
    assert calls["timeout"] > 0


def test_run_timeout_exits_cleanly(monkeypatch):
    def fake_run(cmd, **kwargs):
        raise mm.subprocess.TimeoutExpired(cmd, kwargs.get("timeout"))

    monkeypatch.setattr(mm.subprocess, "run", fake_run)
    with pytest.raises(SystemExit):
        mm.run(["git", "status"])


def test_run_binary_mode(monkeypatch):
    calls = {}

    def fake_run(cmd, **kwargs):
        calls.update(kwargs)
        return subprocess.CompletedProcess(cmd, 0, b"bytes", b"")

    monkeypatch.setattr(mm.subprocess, "run", fake_run)
    proc = mm.run(["git", "show", "x:y"], binary=True)
    assert proc.stdout == b"bytes"
    # Binary mode must not decode — no text=True may reach the call.
    assert calls.get("text") is not True


# ---------- keys.json patching ----------


def test_patch_keys_json_renames_fields(tmp_path):
    path = tmp_path / "keys.json"
    path.write_text((FIXTURES / "keys_raw.json").read_text())
    renamed = mm.patch_keys_json(path)
    assert renamed >= 1
    entries = json.loads(path.read_text())
    assert entries == load_fixture("keys_patched.json")
    for entry in entries:
        assert "region" in entry and "keys" in entry
        assert "mapsquare" not in entry and "key" not in entry


def test_patch_keys_json_idempotent(tmp_path):
    path = tmp_path / "keys.json"
    path.write_text((FIXTURES / "keys_raw.json").read_text())
    assert mm.patch_keys_json(path) >= 1
    after_first = path.read_bytes()
    assert mm.patch_keys_json(path) == 0
    assert path.read_bytes() == after_first


def test_patch_keys_json_rejects_malformed(tmp_path):
    path = tmp_path / "keys.json"
    # Top-level object instead of a list.
    path.write_text('{"mapsquare": 1, "key": [1, 2, 3, 4]}')
    before = path.read_bytes()
    with pytest.raises(SystemExit):
        mm.patch_keys_json(path)
    assert path.read_bytes() == before
    # List containing a non-dict entry.
    path.write_text('[{"mapsquare": 1, "key": [1, 2, 3, 4]}, 42]')
    before = path.read_bytes()
    with pytest.raises(SystemExit):
        mm.patch_keys_json(path)
    assert path.read_bytes() == before


# ---------- cache subcommand ----------


def test_cache_invokes_download_with_repo_cwd(tmp_path, monkeypatch):
    repo, _ = redirect_repo(tmp_path, monkeypatch)
    calls = []

    def fake_run(cmd, *, cwd=None, timeout=None, binary=False):
        calls.append((cmd, cwd, timeout))
        # The script drops ./cache and ./keys.json into its cwd.
        (repo / "cache").mkdir(exist_ok=True)
        (repo / "keys.json").write_text(
            (FIXTURES / "keys_raw.json").read_text())
        return cp(cmd)

    monkeypatch.setattr(mm, "run", fake_run)
    monkeypatch.setattr(mm, "check_tools", lambda names: None)
    rc = mm.main(["cache"])
    assert rc == 0
    assert len(calls) == 1
    cmd, cwd, timeout = calls[0]
    assert cmd == [
        str(repo / "collision-map-update" / "download-latest-cache.sh")]
    assert isinstance(cmd, list)
    assert cwd == repo
    assert timeout > 0
    # The raw fixture keys got patched in place by the subcommand.
    entries = json.loads((repo / "keys.json").read_text())
    assert entries == load_fixture("keys_patched.json")


def test_cache_missing_keys_json_fails(tmp_path, monkeypatch, capsys):
    repo, _ = redirect_repo(tmp_path, monkeypatch)

    def fake_run(cmd, *, cwd=None, timeout=None, binary=False):
        # Download "succeeds" but never produces keys.json.
        return cp(cmd)

    monkeypatch.setattr(mm, "run", fake_run)
    monkeypatch.setattr(mm, "check_tools", lambda names: None)
    rc = mm.main(["cache"])
    assert rc != 0
    assert "keys.json" in capsys.readouterr().err


def test_cache_download_failure_returns_1(tmp_path, monkeypatch, capsys):
    repo, _ = redirect_repo(tmp_path, monkeypatch)

    def fake_run(cmd, *, cwd=None, timeout=None, binary=False):
        return cp(cmd, stderr="curl: (6) could not resolve host", rc=22)

    monkeypatch.setattr(mm, "run", fake_run)
    monkeypatch.setattr(mm, "check_tools", lambda names: None)
    rc = mm.main(["cache"])
    assert rc == 1
    assert "could not resolve host" in capsys.readouterr().err


def test_cache_help_exits_zero():
    with pytest.raises(SystemExit) as exc:
        mm.main(["cache", "--help"])
    assert exc.value.code == 0


# ---------- collision-map (primary path) ----------


def collision_kind(cmd):
    """Bucket a recorded argv into the pipeline step it represents."""
    if cmd[:4] == ["git", "-C", "shortest-path", "status"]:
        return "status"
    if cmd[:4] == ["git", "-C", "shortest-path", "rev-parse"]:
        return "rev-parse-short" if "--short" in cmd else "rev-parse"
    if cmd[:4] == ["git", "-C", "shortest-path", "fetch"]:
        return "fetch"
    if cmd[:4] == ["git", "-C", "shortest-path", "merge"]:
        return "merge"
    if cmd[:4] == ["git", "-C", "shortest-path", "show"]:
        return "show"
    if cmd[:2] == ["git", "add"]:
        return "add"
    if cmd[:2] == ["git", "commit"]:
        return "commit"
    if cmd[0] == sys.executable:
        return "compare"
    return f"other:{cmd}"


def make_collision_run(calls, *, status_out="", heads=("oldsha", "newsha"),
                       fetch_rc=0, merge_rc=0, show_bytes=b"ZIPBYTES",
                       show_rc=0, diff_stdout="EDGE TOTALS\n",
                       diff_rc=0, short="newsha1"):
    """fake mm.run for the primary submodule-bump path."""
    remaining = list(heads)

    def fake_run(cmd, *, cwd=None, timeout=None, binary=False):
        calls.append(list(cmd))
        kind = collision_kind(cmd)
        if kind == "status":
            return cp(cmd, status_out)
        if kind == "rev-parse":
            head = remaining.pop(0) if len(remaining) > 1 \
                else remaining[0]
            return cp(cmd, head)
        if kind == "rev-parse-short":
            return cp(cmd, short)
        if kind == "fetch":
            return cp(cmd, rc=fetch_rc)
        if kind == "merge":
            return cp(cmd, stderr="fatal: Not possible to "
                                  "fast-forward", rc=merge_rc)
        if kind == "show":
            assert binary is True, "git show of a zip must be binary"
            return subprocess.CompletedProcess(
                cmd, show_rc, show_bytes, b"")
        if kind in ("add", "commit"):
            return cp(cmd)
        if kind == "compare":
            return cp(cmd, diff_stdout, rc=diff_rc)
        raise AssertionError(f"unexpected argv: {cmd}")

    return fake_run


def test_collision_map_bump_diff_sequence(tmp_path, monkeypatch, capsys):
    repo, submodule = redirect_repo(tmp_path, monkeypatch)
    calls = []
    monkeypatch.setattr(
        mm, "run",
        make_collision_run(calls, heads=["oldsha0000", "newsha1111"]))
    rc = mm.main(["collision-map"])
    assert rc == 0
    assert [collision_kind(c) for c in calls] == [
        "status", "rev-parse", "fetch", "merge", "rev-parse",
        "show", "compare"]
    show = calls[5]
    assert show[3] == "show"
    assert show[4] == "oldsha0000:src/main/resources/collision-map.zip"
    old_zip = repo / "build" / "old-collision-map.zip"
    assert old_zip.read_bytes() == b"ZIPBYTES"
    compare = calls[6]
    assert compare[1].endswith("compare_collision_maps.py")
    assert compare[2] == str(old_zip)
    assert compare[3] == str(
        submodule / "src" / "main" / "resources" / "collision-map.zip")
    assert "EDGE TOTALS" in capsys.readouterr().out


def test_collision_map_already_up_to_date(tmp_path, monkeypatch, capsys):
    repo, _ = redirect_repo(tmp_path, monkeypatch)
    calls = []
    monkeypatch.setattr(
        mm, "run",
        make_collision_run(calls, heads=["samesha", "samesha"]))
    rc = mm.main(["collision-map"])
    assert rc == 0
    kinds = [collision_kind(c) for c in calls]
    assert kinds == ["status", "rev-parse", "fetch", "merge",
                     "rev-parse"]
    assert "show" not in kinds and "compare" not in kinds
    assert "up to date" in capsys.readouterr().out


def test_collision_map_dirty_worktree_refused(tmp_path, monkeypatch):
    repo, _ = redirect_repo(tmp_path, monkeypatch)
    calls = []
    monkeypatch.setattr(
        mm, "run", make_collision_run(calls, status_out=" M file"))
    with pytest.raises(SystemExit):
        mm.main(["collision-map"])
    kinds = [collision_kind(c) for c in calls]
    assert kinds == ["status"]


def test_collision_map_diverged_fails(tmp_path, monkeypatch, capsys):
    repo, _ = redirect_repo(tmp_path, monkeypatch)
    calls = []
    monkeypatch.setattr(
        mm, "run", make_collision_run(calls, merge_rc=128))
    rc = mm.main(["collision-map"])
    assert rc == 1
    err = capsys.readouterr().err
    assert "merge or rebase" in err


def test_collision_map_commit_flag(tmp_path, monkeypatch):
    repo, _ = redirect_repo(tmp_path, monkeypatch)
    calls = []
    monkeypatch.setattr(
        mm, "run", make_collision_run(calls, short="abc1234"))
    rc = mm.main(["collision-map", "--commit"])
    assert rc == 0
    kinds = [collision_kind(c) for c in calls]
    assert kinds[-3:] == ["rev-parse-short", "add", "commit"]
    add = calls[-2]
    commit = calls[-1]
    assert add == ["git", "add", "shortest-path"]
    assert commit == ["git", "commit", "-m",
                      "chore: update shortest-path submodule to abc1234"]


def test_collision_map_no_commit_prints_hint(tmp_path, monkeypatch,
                                             capsys):
    repo, _ = redirect_repo(tmp_path, monkeypatch)
    calls = []
    monkeypatch.setattr(mm, "run", make_collision_run(calls))
    rc = mm.main(["collision-map"])
    assert rc == 0
    kinds = [collision_kind(c) for c in calls]
    assert "add" not in kinds and "commit" not in kinds
    out = capsys.readouterr().out
    assert "git add shortest-path" in out
    assert "chore: update shortest-path submodule" in out


# ---------- collision-map --local (fallback pipeline) ----------


def local_kind(cmd):
    """Bucket a recorded argv into the local-pipeline step it
    represents."""
    if cmd[:4] == ["git", "-C", "shortest-path", "rev-parse"]:
        return "upstream" if "@{u}" in cmd else "branch"
    if cmd[:4] == ["git", "-C", "shortest-path", "status"]:
        return "status"
    if cmd[0].endswith("download-latest-cache.sh"):
        return "download"
    if cmd[:2] == ["git", "clone"]:
        return "clone"
    if cmd[:2] == ["git", "-C"] and "fetch" in cmd:
        return "fetch"
    if cmd[:2] == ["git", "-C"] and "reset" in cmd:
        return "reset"
    if cmd[:3] == ["git", "apply", "--check"]:
        return "apply-check"
    if cmd[:3] == ["git", "apply", "--reverse"]:
        return "apply-reverse-check"
    if cmd[:2] == ["git", "apply"]:
        return "apply"
    if cmd[0].endswith("gradlew"):
        return "shadowJar"
    if cmd[0] == "java":
        return "java"
    if cmd[0] == "zip":
        return "zip"
    if cmd[0] == sys.executable:
        return "compare"
    return f"other:{cmd}"


def make_local_run(repo, calls, *, branch="maint-x",
                   upstream="myfork/maint-x", upstream_rc=0,
                   status_out="", download_rc=0,
                   apply_check_rc=0, reverse_check_rc=1,
                   shadow_rc=0, java_rc=0, zip_rc=0,
                   diff_stdout="EDGE TOTALS\n"):
    """fake mm.run for the --local pipeline; fabricates the filesystem
    effects each step would produce under the tmp repo."""
    runelite = repo / "build" / "runelite-work" / "runelite"
    cache_mod = runelite / "cache"
    download_script = str(
        repo / "collision-map-update" / "download-latest-cache.sh")

    def fake_run(cmd, *, cwd=None, timeout=None, binary=False):
        calls.append((list(cmd), cwd))
        kind = local_kind(cmd)
        if kind == "branch":
            return cp(cmd, branch)
        if kind == "upstream":
            return cp(cmd, upstream if upstream_rc == 0 else "",
                      "" if upstream_rc == 0 else "no upstream",
                      rc=upstream_rc)
        if kind == "status":
            return cp(cmd, status_out)
        if kind == "download":
            (repo / "cache").mkdir(exist_ok=True)
            (repo / "keys.json").write_text(
                (FIXTURES / "keys_raw.json").read_text())
            return cp(cmd, rc=download_rc)
        if kind == "clone":
            target = Path(cmd[-1])
            (target / "cache" / "src" / "main" / "java" / "net" /
             "runelite" / "cache").mkdir(parents=True)
            (target / ".git").mkdir()
            return cp(cmd)
        if kind in ("fetch", "reset"):
            return cp(cmd)
        if kind == "apply-check":
            return cp(cmd, stderr="patch does not apply",
                      rc=apply_check_rc)
        if kind == "apply-reverse-check":
            return cp(cmd, rc=reverse_check_rc)
        if kind == "apply":
            return cp(cmd)
        if kind == "shadowJar":
            libs = cache_mod / "build" / "libs"
            libs.mkdir(parents=True, exist_ok=True)
            (libs / "cache-1.0-all.jar").write_bytes(b"JAR")
            return cp(cmd, rc=shadow_rc)
        if kind == "java":
            return cp(cmd, rc=java_rc)
        if kind == "zip":
            Path(cwd, "collision-map.zip").write_bytes(b"NEWZIP")
            return cp(cmd, rc=zip_rc)
        if kind == "compare":
            return cp(cmd, diff_stdout)
        raise AssertionError(f"unexpected argv: {cmd}")

    return fake_run


def prepare_local(tmp_path, monkeypatch, *, cache_ready=True,
                  existing_zip=True, **run_kwargs):
    """Common --local setup: redirected repo, stubbed tool check, fake
    run seam.  Returns (repo, submodule, calls)."""
    repo, submodule = redirect_repo(tmp_path, monkeypatch)
    monkeypatch.setattr(mm, "check_tools", lambda names: None)
    # The pipeline copies real inputs out of collision-map-update/ —
    # mirror the directory into the scratch repo.
    shutil.copytree(ROOT / "collision-map-update",
                    repo / "collision-map-update")
    resources = submodule / "src" / "main" / "resources"
    resources.mkdir(parents=True)
    if existing_zip:
        (resources / "collision-map.zip").write_bytes(b"OLDZIP")
    if cache_ready:
        (repo / "cache").mkdir()
        (repo / "keys.json").write_text(
            (FIXTURES / "keys_patched.json").read_text())
    calls = []
    monkeypatch.setattr(
        mm, "run", make_local_run(repo, calls, **run_kwargs))
    return repo, submodule, calls


def test_collision_map_local_sequence(tmp_path, monkeypatch, capsys):
    # Cache absent -> the pipeline downloads it first; existing
    # submodule zip -> snapshotted as the diff baseline.
    repo, submodule, calls = prepare_local(
        tmp_path, monkeypatch, cache_ready=False)
    rc = mm.main(["collision-map", "--local"])
    assert rc == 0
    kinds = [local_kind(c) for c, _ in calls]
    assert kinds == [
        "branch", "upstream", "status", "download", "clone",
        "apply-check", "apply", "shadowJar", "java", "zip", "compare"]

    runelite = repo / "build" / "runelite-work" / "runelite"
    build = repo / "build"
    # Clone argv and every scratch path live under build/.
    clone = next(c for c, _ in calls if local_kind(c) == "clone")
    assert clone[:4] == ["git", "clone", "--depth", "1"]
    assert clone[4] == "https://github.com/runelite/runelite"
    assert clone[5] == str(runelite)

    gradle = next(c for c, _ in calls if local_kind(c) == "shadowJar")
    assert gradle == [str(runelite / "gradlew"), ":cache:shadowJar",
                      "-x", "test", "--dependency-verification=off"]
    gradle_cwd = next(w for c, w in calls
                      if local_kind(c) == "shadowJar")
    assert gradle_cwd == runelite

    java = next(c for c, _ in calls if local_kind(c) == "java")
    assert java[:3] == ["java", "-jar", str(build / "cache.jar")]
    assert str(build / "collision-output") in java

    # The new zip landed in the submodule; the old one was preserved.
    new_zip = (submodule / "src" / "main" / "resources" /
               "collision-map.zip")
    assert new_zip.read_bytes() == b"NEWZIP"
    assert (build / "old-collision-map.zip").read_bytes() == b"OLDZIP"
    compare = next(c for c, _ in calls if local_kind(c) == "compare")
    assert compare[2] == str(build / "old-collision-map.zip")
    assert compare[3] == str(new_zip)
    assert "EDGE TOTALS" in capsys.readouterr().out


def test_collision_map_local_refuses_master(tmp_path, monkeypatch):
    repo, _, calls = prepare_local(
        tmp_path, monkeypatch, branch="master")
    with pytest.raises(SystemExit):
        mm.main(["collision-map", "--local"])
    kinds = [local_kind(c) for c, _ in calls]
    assert kinds == ["branch"]


def test_collision_map_local_refuses_detached(tmp_path, monkeypatch):
    repo, _, calls = prepare_local(tmp_path, monkeypatch, branch="HEAD")
    with pytest.raises(SystemExit):
        mm.main(["collision-map", "--local"])
    kinds = [local_kind(c) for c, _ in calls]
    assert kinds == ["branch"]


def test_collision_map_local_refuses_origin_upstream(tmp_path,
                                                     monkeypatch):
    repo, _, calls = prepare_local(
        tmp_path, monkeypatch, upstream="origin/master")
    with pytest.raises(SystemExit):
        mm.main(["collision-map", "--local"])
    kinds = [local_kind(c) for c, _ in calls]
    assert kinds == ["branch", "upstream"]
    assert "clone" not in kinds and "download" not in kinds


def test_collision_map_local_no_upstream_warns_but_allows(
        tmp_path, monkeypatch, capsys):
    repo, _, calls = prepare_local(
        tmp_path, monkeypatch, upstream_rc=1, existing_zip=False)
    rc = mm.main(["collision-map", "--local"])
    assert rc == 0
    assert "no upstream" in capsys.readouterr().err
    kinds = [local_kind(c) for c, _ in calls]
    assert "clone" in kinds


def test_collision_map_local_existing_clone_refreshed(tmp_path,
                                                      monkeypatch):
    repo, _, calls = prepare_local(
        tmp_path, monkeypatch, existing_zip=False)
    runelite = repo / "build" / "runelite-work" / "runelite"
    cache_mod = runelite / "cache"
    (cache_mod / "src" / "main" / "java" / "net" / "runelite" /
     "cache").mkdir(parents=True)
    (runelite / ".git").mkdir()
    rc = mm.main(["collision-map", "--local"])
    assert rc == 0
    kinds = [local_kind(c) for c, _ in calls]
    assert "clone" not in kinds
    assert "fetch" in kinds and "reset" in kinds
    assert kinds.index("fetch") < kinds.index("reset") < \
        kinds.index("apply-check")


def test_collision_map_local_patch_already_applied(tmp_path,
                                                   monkeypatch, capsys):
    repo, _, calls = prepare_local(
        tmp_path, monkeypatch, existing_zip=False,
        apply_check_rc=1, reverse_check_rc=0)
    rc = mm.main(["collision-map", "--local"])
    assert rc == 0
    kinds = [local_kind(c) for c, _ in calls]
    assert "apply-check" in kinds
    assert "apply-reverse-check" in kinds
    assert "apply" not in kinds
    assert "shadowJar" in kinds
    assert "already applied" in capsys.readouterr().out


def test_collision_map_local_old_zip_preserved(tmp_path, monkeypatch):
    repo, submodule, calls = prepare_local(tmp_path, monkeypatch)
    rc = mm.main(["collision-map", "--local"])
    assert rc == 0
    old_zip = repo / "build" / "old-collision-map.zip"
    assert old_zip.read_bytes() == b"OLDZIP"
    compare = next(c for c, _ in calls if local_kind(c) == "compare")
    assert compare[2] == str(old_zip)


def test_collision_map_local_missing_cache_downloads_first(
        tmp_path, monkeypatch):
    repo, _, calls = prepare_local(
        tmp_path, monkeypatch, cache_ready=False, existing_zip=False)
    rc = mm.main(["collision-map", "--local"])
    assert rc == 0
    kinds = [local_kind(c) for c, _ in calls]
    assert kinds.index("download") < kinds.index("clone")


# ---------- regions + bank subcommands ----------


def write_gate_kind(cmd):
    """Bucket a recorded argv into the write-gate probe it answers."""
    if cmd[:4] == ["git", "-C", "shortest-path", "rev-parse"]:
        return "upstream" if "@{u}" in cmd else "branch"
    if cmd[:4] == ["git", "-C", "shortest-path", "status"]:
        return "status"
    return f"other:{cmd}"


def make_dump_run(repo, calls, *, branch="maint-x",
                  upstream="myfork/maint-x", upstream_rc=0,
                  status_out="", league_rc=0, f2p_rc=0, bank_rc=0,
                  script_rc=0, script_stdout=""):
    """fake mm.run for the regions/bank subcommands: answers the
    write-gate git probes and fabricates each dumper's build/ output."""
    def fake_run(cmd, *, cwd=None, timeout=None, binary=False):
        calls.append((list(cmd), cwd, timeout))
        kind = write_gate_kind(cmd)
        if kind == "branch":
            return cp(cmd, branch)
        if kind == "upstream":
            return cp(cmd, upstream if upstream_rc == 0 else "",
                      "" if upstream_rc == 0 else "no upstream",
                      rc=upstream_rc)
        if kind == "status":
            return cp(cmd, status_out)
        if cmd[:2] == ["./gradlew", "leagueRegionDump"]:
            out = repo / "build" / "league-regions"
            out.mkdir(parents=True, exist_ok=True)
            (out / "regions.tsv").write_text("1\tVARLAMORE\n")
            return cp(cmd, "league dump ok", rc=league_rc)
        if cmd[:2] == ["./gradlew", "f2pRegionDump"]:
            out = repo / "build" / "f2p-regions"
            out.mkdir(parents=True, exist_ok=True)
            (out / "regions.tsv").write_text("2\tF2P\n")
            return cp(cmd, "f2p dump ok", rc=f2p_rc)
        if cmd[:2] == ["./gradlew", "bankTileDump"]:
            out = repo / "build" / "bank-tiles"
            out.mkdir(parents=True, exist_ok=True)
            (out / "bank_tile_placements.tsv").write_text("P\tL\n")
            return cp(cmd, "bank dump ok", rc=bank_rc)
        if cmd[0] == sys.executable:
            return cp(cmd, script_stdout, rc=script_rc)
        raise AssertionError(f"unexpected argv: {cmd}")

    return fake_run


def prepare_dump_repo(tmp_path, monkeypatch, *, cache_ready=True,
                      f2p_resources=False, f2p_java=False,
                      **run_kwargs):
    """Common regions/bank setup: redirected repo with cache + keys and
    the submodule's existing leagues/ resource dir.  Returns
    (repo, submodule, calls)."""
    repo, submodule = redirect_repo(tmp_path, monkeypatch)
    if cache_ready:
        (repo / "cache").mkdir()
        (repo / "keys.json").write_text(
            (FIXTURES / "keys_patched.json").read_text())
    resources = submodule / "src" / "main" / "resources"
    (resources / "leagues").mkdir(parents=True)
    (resources / "leagues" / "regions.tsv").write_text("OLD\n")
    if f2p_resources:
        (resources / "f2p").mkdir(parents=True)
    if f2p_java:
        pkg = submodule / "src" / "main" / "java" / "shortestpath" / "f2p"
        pkg.mkdir(parents=True)
        (pkg / "F2pRegionChecker.java").write_text(
            "class F2pRegionChecker {}\n")
    calls = []
    monkeypatch.setattr(
        mm, "run", make_dump_run(repo, calls, **run_kwargs))
    return repo, submodule, calls


def gradle_calls(calls):
    return [c for c, _, _ in calls if c[0] == "./gradlew"]


def test_regions_invokes_both_dumps_sequentially(tmp_path, monkeypatch):
    repo, _, calls = prepare_dump_repo(tmp_path, monkeypatch)
    rc = mm.main(["regions"])
    assert rc == 0
    dumps = gradle_calls(calls)
    assert dumps == [
        ["./gradlew", "leagueRegionDump",
         f"-PleagueRegionsCacheDir={repo / 'cache'}",
         f"-PleagueRegionsXteaPath={repo / 'keys.json'}"],
        ["./gradlew", "f2pRegionDump",
         f"-Pf2pRegionsCacheDir={repo / 'cache'}",
         f"-Pf2pRegionsXteaPath={repo / 'keys.json'}"],
    ]
    for cmd, cwd, timeout in calls:
        if cmd[0] == "./gradlew":
            assert cwd == repo
            assert timeout == mm.GRADLE_TIMEOUT_SECONDS


def test_regions_copies_leagues_unconditionally(tmp_path, monkeypatch):
    repo, submodule, calls = prepare_dump_repo(tmp_path, monkeypatch)
    rc = mm.main(["regions"])
    assert rc == 0
    copied = (submodule / "src" / "main" / "resources" / "leagues" /
              "regions.tsv")
    assert copied.read_text() == "1\tVARLAMORE\n"


def test_regions_f2p_skipped_without_consumer(tmp_path, monkeypatch,
                                              capsys):
    repo, submodule, calls = prepare_dump_repo(tmp_path, monkeypatch)
    rc = mm.main(["regions"])
    assert rc == 0
    f2p_out = (submodule / "src" / "main" / "resources" / "f2p" /
               "regions.tsv")
    assert not f2p_out.exists()
    # The dump still ran — only the copy is gated.
    assert any(c[:2] == ["./gradlew", "f2pRegionDump"]
               for c in gradle_calls(calls))
    out = capsys.readouterr().out
    assert "build/f2p-regions/regions.tsv" in out
    assert "--f2p" in out


def test_regions_f2p_copied_when_consumer_present(tmp_path, monkeypatch):
    repo, submodule, calls = prepare_dump_repo(
        tmp_path, monkeypatch, f2p_resources=True)
    rc = mm.main(["regions"])
    assert rc == 0
    copied = (submodule / "src" / "main" / "resources" / "f2p" /
              "regions.tsv")
    assert copied.read_text() == "2\tF2P\n"


def test_regions_f2p_copied_when_java_consumer(tmp_path, monkeypatch):
    repo, submodule, calls = prepare_dump_repo(
        tmp_path, monkeypatch, f2p_java=True)
    rc = mm.main(["regions"])
    assert rc == 0
    copied = (submodule / "src" / "main" / "resources" / "f2p" /
              "regions.tsv")
    assert copied.read_text() == "2\tF2P\n"


def test_regions_f2p_forced_flag(tmp_path, monkeypatch):
    repo, submodule, calls = prepare_dump_repo(tmp_path, monkeypatch)
    rc = mm.main(["regions", "--f2p"])
    assert rc == 0
    copied = (submodule / "src" / "main" / "resources" / "f2p" /
              "regions.tsv")
    assert copied.read_text() == "2\tF2P\n"


def test_regions_refuses_on_master_and_detached(tmp_path, monkeypatch):
    for name, branch in (("a", "master"), ("b", "HEAD")):
        repo, _, calls = prepare_dump_repo(
            tmp_path / name, monkeypatch, branch=branch)
        rc = mm.main(["regions"])
        assert rc == 1
        assert gradle_calls(calls) == []


def test_regions_missing_cache_refuses(tmp_path, monkeypatch, capsys):
    repo, _, calls = prepare_dump_repo(
        tmp_path, monkeypatch, cache_ready=False)
    rc = mm.main(["regions"])
    assert rc == 1
    assert "maintenance.py cache" in capsys.readouterr().err
    assert gradle_calls(calls) == []


def test_bank_sequence(tmp_path, monkeypatch):
    repo, _, calls = prepare_dump_repo(tmp_path, monkeypatch)
    rc = mm.main(["bank"])
    assert rc == 0
    dumps = gradle_calls(calls)
    assert dumps == [[
        "./gradlew", "bankTileDump",
        f"-PbankTileCacheDir={repo / 'cache'}",
        f"-PbankTileXteaPath={repo / 'keys.json'}"]]
    # The dump must use its default output path — the same hardcoded
    # PLACEMENTS_TSV rebuild_bank_tsv.py reads.
    assert not any("bankTileOutput" in arg for arg in dumps[0])
    script_calls = [c for c, _, _ in calls if c[0] == sys.executable]
    assert script_calls == [[
        sys.executable,
        str(repo / "scripts" / "rebuild_bank_tsv.py")]]
    kinds = [("dump" if c[0] == "./gradlew" else "script")
             for c, _, _ in calls if c[0] in ("./gradlew",
                                              sys.executable)]
    assert kinds == ["dump", "script"]


def test_bank_refuses_on_master(tmp_path, monkeypatch):
    repo, _, calls = prepare_dump_repo(
        tmp_path, monkeypatch, branch="master")
    rc = mm.main(["bank"])
    assert rc == 1
    assert gradle_calls(calls) == []


def test_bank_missing_cache_refuses(tmp_path, monkeypatch, capsys):
    repo, _, calls = prepare_dump_repo(
        tmp_path, monkeypatch, cache_ready=False)
    rc = mm.main(["bank"])
    assert rc == 1
    assert "maintenance.py cache" in capsys.readouterr().err
    assert gradle_calls(calls) == []
