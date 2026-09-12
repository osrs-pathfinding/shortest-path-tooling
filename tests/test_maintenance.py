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
