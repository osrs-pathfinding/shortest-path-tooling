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


def run_sync(tmp_path, monkeypatch, issues=None, prs=None, extra_args=None):
    def dispatch(args):
        if args[:2] == ["pr", "list"]:
            return prs if prs is not None else load_fixture("gh_pr_list.json")
        return issues if issues is not None else load_fixture("gh_issue_list.json")
    monkeypatch.setattr(ii, "gh_json", dispatch)
    argv = ["sync", "--output-dir", str(tmp_path)]
    if extra_args:
        argv.extend(extra_args)
    return ii.main(argv)


def stub_prs(monkeypatch):
    monkeypatch.setattr(
        ii, "gh_json", lambda args: load_fixture("gh_pr_list.json"))
    return ii.fetch_fix_candidates()


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


def test_fix_candidate_confirmed_link(monkeypatch):
    out = stub_prs(monkeypatch)
    assert 509 in out
    entry = next(e for e in out[509] if e["pr"] == 541)
    assert entry["link"] == "confirmed"
    assert entry["url"] == "https://github.com/Skretzo/shortest-path/pull/541"
    assert entry["author"] == "pr0f3ss"
    assert entry["branch"] == "fix/509-quest-cape-all-quests"


def test_fix_candidate_heuristic_body_link(monkeypatch):
    # Live-verified gap: body says "Fixes issue #504" but GitHub produced
    # no closingIssuesReferences entry.
    out = stub_prs(monkeypatch)
    assert 504 in out
    entry = next(e for e in out[504] if e["pr"] == 539)
    assert entry["link"] == "heuristic"


def test_fix_candidate_heuristic_branch_link(monkeypatch):
    out = stub_prs(monkeypatch)
    assert 99992 in out
    entry = next(e for e in out[99992] if e["pr"] == 99993)
    assert entry["link"] == "heuristic"


def test_fix_candidate_unrelated_pr_absent(monkeypatch):
    out = stub_prs(monkeypatch)
    all_prs = [e["pr"] for entries in out.values() for e in entries]
    assert 99994 not in all_prs


def test_sync_populates_fix_candidates_frontmatter(tmp_path, monkeypatch):
    run_sync(tmp_path, monkeypatch)
    fm, _body = frontmatter_and_body(tmp_path / "ISSUE-549.md")
    prs = [e["pr"] for e in fm["fix_candidates"]]
    assert 99995 in prs
    entry = next(e for e in fm["fix_candidates"] if e["pr"] == 99995)
    assert entry["link"] == "confirmed"


def test_shadow_schema_fields(tmp_path, monkeypatch):
    run_sync(tmp_path, monkeypatch)
    fm, _body = frontmatter_and_body(tmp_path / "ISSUE-549.md")
    assert set(fm.keys()) == {
        "upstream", "url", "title", "upstream_state",
        "upstream_state_reason", "labels", "author", "created_at",
        "updated_at", "synced_at", "status", "phase", "fix_candidates",
        "scenario_rows", "verification", "history",
    }
    v = fm["verification"]
    assert isinstance(v, dict)
    assert set(v.keys()) == {
        "command", "dataset_rows", "report", "fix_commit", "fix_pr",
        "verifier", "verified_at",
    }


def test_shadow_body_sections(tmp_path, monkeypatch):
    run_sync(tmp_path, monkeypatch)
    _fm, body = frontmatter_and_body(tmp_path / "ISSUE-549.md")
    headings = [
        "## Upstream Report", "## Upstream Comments",
        "## Normalized Scenario", "## Triage Notes",
        "## Requirements", "## Acceptance Criteria",
        "## Canonical References",
    ]
    idx = [body.index(h) for h in headings]
    assert idx == sorted(idx)


def test_shadow_upstream_text_fenced(tmp_path, monkeypatch):
    run_sync(tmp_path, monkeypatch)
    _fm, body = frontmatter_and_body(tmp_path / "ISSUE-549.md")
    issue = next(r for r in load_fixture("gh_issue_list.json")
                 if r["number"] == 549)
    title = issue["title"]
    body_line = next(l for l in (issue.get("body") or "").splitlines()
                     if l.strip())
    report_idx = body.index("## Upstream Report")
    scenario_idx = body.index("## Normalized Scenario")
    upstream_region = body[report_idx:scenario_idx]
    assert "UNTRUSTED external content" in upstream_region
    assert title in upstream_region
    assert body_line in upstream_region
    # Upstream text must not leak into the maintainer sections.
    assert title not in body[scenario_idx:]
    assert body_line not in body[scenario_idx:]


def test_shadow_normalized_scenario_table(tmp_path, monkeypatch):
    run_sync(tmp_path, monkeypatch)
    _fm, body = frontmatter_and_body(tmp_path / "ISSUE-549.md")
    section = body.split("## Normalized Scenario", 1)[1].split("## ", 1)[0]
    for field in ("name", "category", "start", "target", "preset",
                  "config_overrides", "expected"):
        assert f"| {field} |" in section
    assert "CSV row ref:" in section
    assert "scenarios.csv" in section


def test_list_outputs_status_lines(tmp_path, monkeypatch, capsys):
    run_sync(tmp_path, monkeypatch)
    capsys.readouterr()  # discard sync output
    rc = ii.main(["list", "--output-dir", str(tmp_path)])
    assert rc == 0
    lines = [l for l in capsys.readouterr().out.splitlines() if l.strip()]
    issues = load_fixture("gh_issue_list.json")
    assert len(lines) == len(issues)
    numbers = [int(l.split()[0].split("-")[1]) for l in lines]
    assert numbers == sorted(numbers)
    assert any(l.startswith("ISSUE-549") and "reported" in l
               for l in lines)


# --------------------------------------------------------------------------
# status subcommand — lifecycle transitions
# --------------------------------------------------------------------------

def make_shadow(tmp_path, number, status="reported", fm_extra=None,
                body_text="\n## Triage Notes\n\nmaintainer note\n"):
    """Write a minimal valid shadow file and return its path."""
    fm = {
        "upstream": f"Skretzo/shortest-path#{number}",
        "url": f"https://example.invalid/issues/{number}",
        "title": "fixture issue",
        "upstream_state": "open",
        "upstream_state_reason": None,
        "labels": ["bug"],
        "author": "reporter",
        "created_at": "2026-01-01T00:00:00Z",
        "updated_at": "2026-01-01T00:00:00Z",
        "synced_at": "2026-01-01T00:00:00Z",
        "status": status,
        "phase": None,
        "fix_candidates": [],
        "scenario_rows": [],
        "verification": {
            "command": None, "dataset_rows": [], "report": None,
            "fix_commit": None, "fix_pr": None, "verifier": None,
            "verified_at": None,
        },
        "history": [{"at": "2026-01-01T00:00:00Z", "event": "imported",
                     "by": "import_issues.py"}],
    }
    if fm_extra:
        fm.update(fm_extra)
    path = tmp_path / f"ISSUE-{number}.md"
    path.write_text(
        "---\n" + yaml.safe_dump(fm, sort_keys=False) + "---\n" + body_text)
    return path


def run_status(tmp_path, *argv):
    return ii.main(["status", "--output-dir", str(tmp_path), *argv])


def test_status_transition_appends_history(tmp_path, capsys):
    path = make_shadow(tmp_path, 1, status="reported")
    before = path.read_text()
    rc = run_status(tmp_path, "1", "triaged", "--by", "tester")
    assert rc == 0
    out = capsys.readouterr().out
    assert "ISSUE-1: reported -> triaged" in out
    fm, body = frontmatter_and_body(path)
    assert fm["status"] == "triaged"
    last = fm["history"][-1]
    assert last["event"] == "status: reported -> triaged"
    assert last["by"] == "tester"
    # Upstream-owned fields and the body must be untouched.
    assert fm["title"] == "fixture issue"
    assert fm["upstream_state"] == "open"
    assert body == before.split("---", 2)[2]


def test_status_note_appends_to_event(tmp_path):
    path = make_shadow(tmp_path, 10, status="reported")
    rc = run_status(tmp_path, "10", "needs_info", "--note", "asked reporter")
    assert rc == 0
    fm, _body = frontmatter_and_body(path)
    assert fm["history"][-1]["event"].endswith("— asked reporter")


def test_status_rejects_unknown_status(tmp_path, capsys):
    path = make_shadow(tmp_path, 2, status="reported")
    before = path.read_text()
    rc = run_status(tmp_path, "2", "bogus")
    assert rc != 0
    assert capsys.readouterr().err
    assert path.read_text() == before


def test_status_rejects_invalid_transition(tmp_path):
    path = make_shadow(tmp_path, 3, status="reported")
    before = path.read_text()
    assert run_status(tmp_path, "3", "verified") != 0
    assert run_status(tmp_path, "3", "closed") != 0
    assert path.read_text() == before


def test_status_verified_rejected_use_verify(tmp_path, capsys):
    # fixed -> verified looks plausible in the map but evidence-bearing
    # verification is a separate gate, so `status` must always refuse it.
    path = make_shadow(tmp_path, 4, status="fixed")
    before = path.read_text()
    rc = run_status(tmp_path, "4", "verified")
    assert rc != 0
    assert "verify" in capsys.readouterr().err
    assert path.read_text() == before


def test_status_phase_linked_requires_phase(tmp_path):
    path = make_shadow(tmp_path, 5, status="triaged")
    before = path.read_text()
    assert run_status(tmp_path, "5", "phase_linked") != 0
    assert path.read_text() == before
    assert run_status(tmp_path, "5", "phase_linked",
                      "--phase", "phases/xyz") == 0
    fm, _body = frontmatter_and_body(path)
    assert fm["status"] == "phase_linked"
    assert fm["phase"] == "phases/xyz"


def test_status_reopened_from_terminal(tmp_path):
    for n, status in ((6, "closed"), (7, "wontfix"), (8, "duplicate")):
        make_shadow(tmp_path, n, status=status)
        assert run_status(tmp_path, str(n), "reopened") == 0
        fm, _body = frontmatter_and_body(tmp_path / f"ISSUE-{n}.md")
        assert fm["status"] == "reopened"


def test_status_blocked_returns_to_active(tmp_path):
    make_shadow(tmp_path, 9, status="blocked")
    assert run_status(tmp_path, "9", "in_progress") == 0
    fm, _body = frontmatter_and_body(tmp_path / "ISSUE-9.md")
    assert fm["status"] == "in_progress"


def test_status_missing_file(tmp_path, capsys):
    rc = run_status(tmp_path, "999", "triaged")
    assert rc != 0
    assert "ISSUE-999.md" in capsys.readouterr().err
