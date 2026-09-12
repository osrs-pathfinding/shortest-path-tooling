"""Tests for ``scripts/import_issues.py``.

All tests run against recorded ``gh ... --json`` fixtures in
``tests/fixtures/`` — no network access.  The script is loaded via
importlib because ``scripts/`` has no ``__init__.py``.
"""
from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

import pytest
import yaml

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "import_issues.py"
FIXTURES = Path(__file__).resolve().parent / "fixtures"

spec = importlib.util.spec_from_file_location("import_issues", SCRIPT_PATH)
ii = importlib.util.module_from_spec(spec)
sys.modules["import_issues"] = ii
spec.loader.exec_module(ii)


def load_fixture(name):
    return json.loads((FIXTURES / name).read_text())


def run_sync(tmp_path, monkeypatch, issues=None, extra_args=None):
    if issues is None:
        issues = load_fixture("gh_issue_list.json")
    monkeypatch.setattr(ii, "gh_json", lambda args: issues)
    argv = ["sync", "--output-dir", str(tmp_path)]
    if extra_args:
        argv.extend(extra_args)
    return ii.main(argv)


def frontmatter_and_body(path):
    text = path.read_text()
    assert text.startswith("---"), "shadow file must start with YAML frontmatter"
    _, fm, body = text.split("---", 2)
    return yaml.safe_load(fm), body


def test_sync_writes_shadow_file(tmp_path, monkeypatch):
    issues = load_fixture("gh_issue_list.json")
    rc = run_sync(tmp_path, monkeypatch, issues)
    assert rc == 0
    files = sorted(tmp_path.glob("ISSUE-*.md"))
    assert len(files) == len(issues)
    fm, body = frontmatter_and_body(files[0])
    number = int(files[0].stem.split("-")[1])
    assert fm["upstream"] == f"Skretzo/shortest-path#{number}"
    assert fm["status"] == "reported"
    assert isinstance(fm["history"], list) and len(fm["history"]) >= 1
    assert "## Upstream Report" in body
    assert "UNTRUSTED external content" in body


def test_sync_filename_is_issue_number(tmp_path, monkeypatch):
    issues = load_fixture("gh_issue_list.json")
    run_sync(tmp_path, monkeypatch, issues)
    names = sorted(p.name for p in tmp_path.iterdir())
    expected = sorted(f"ISSUE-{r['number']}.md" for r in issues)
    assert names == expected
    # The hostile title ("../../evil ...") must not influence the filename.
    assert (tmp_path / "ISSUE-99990.md").is_file()


def test_sync_dry_run_writes_nothing(tmp_path, monkeypatch, capsys):
    rc = run_sync(tmp_path, monkeypatch, extra_args=["--dry-run"])
    assert rc == 0
    assert list(tmp_path.iterdir()) == []
    out = capsys.readouterr().out
    assert "ISSUE-549.md" in out


def test_sync_empty_body_and_comments(tmp_path, monkeypatch):
    run_sync(tmp_path, monkeypatch)
    path = tmp_path / "ISSUE-99991.md"
    assert path.is_file()
    fm, _body = frontmatter_and_body(path)
    assert fm["status"] == "reported"
