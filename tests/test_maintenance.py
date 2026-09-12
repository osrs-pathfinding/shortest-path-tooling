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
