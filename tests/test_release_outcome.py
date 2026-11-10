"""Outcome checks for the shipped 1.22.2 and 1.22.3 releases of the plugin.

These run the real measurement scripts against the plugin submodule's
history. For 1.22.2 they pin the durable result of the footprint reduction:
the re-cut release tag, its stripped-character delta against the
pre-reduction baseline and its exact token count. For 1.22.3 they check that
the tag exists, is annotated, sits on upstream master's history, carries
``version=1.22.3`` and stays within the hub token budget.

Each check is skipped when the submodule history it needs, or ``tiktoken``,
is unavailable.
"""

import re
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
REPO = ROOT / "shortest-path"

BASELINE_REF = "142e73e2"
RELEASE_TAG = "v1.22.2"
RELEASE_COMMIT_PREFIX = "55a57f9f"
BASELINE_CHARS = 859_057
RELEASE_CHARS_DELTA = -131_051
TOKEN_BUDGET = 200_000
RELEASE_TOKENS = 190_890

NEW_RELEASE_TAG = "v1.22.3"
MASTER_REF = "upstream/master"


def _git(*args):
    return subprocess.run(
        ["git", "-C", str(REPO), *args], capture_output=True, text=True
    )


def _have_ref(ref):
    return _git("cat-file", "-e", ref + "^{commit}").returncode == 0


def _needs(*refs):
    return pytest.mark.skipif(
        not (REPO / ".git").exists() or not all(_have_ref(r) for r in refs),
        reason=f"plugin submodule history ({', '.join(refs)}) not available",
    )


def _total(script, ref):
    out = subprocess.run(
        [sys.executable, str(ROOT / "scripts" / script), "--repo", str(REPO), "--ref", ref],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    match = re.search(r"^TOTAL\t(\d+)", out, re.MULTILINE)
    assert match, out[-300:]
    return int(match.group(1))


@_needs(RELEASE_TAG)
def test_release_tag_is_the_recut_commit():
    sha = _git("rev-parse", RELEASE_TAG + "^{commit}").stdout.strip()
    assert sha.startswith(RELEASE_COMMIT_PREFIX)


@_needs(RELEASE_TAG, BASELINE_REF)
def test_release_footprint_delta_against_baseline():
    assert _total("measure_footprint.py", BASELINE_REF) == BASELINE_CHARS
    assert _total("measure_footprint.py", RELEASE_TAG) - BASELINE_CHARS == RELEASE_CHARS_DELTA


@_needs(RELEASE_TAG)
def test_release_token_count_within_hub_budget():
    pytest.importorskip("tiktoken")
    tokens = _total("measure_tokens.py", RELEASE_TAG)
    assert tokens == RELEASE_TOKENS
    assert tokens <= TOKEN_BUDGET


@_needs(NEW_RELEASE_TAG)
def test_v1_22_3_tag_exists():
    assert _git("tag", "-l", NEW_RELEASE_TAG).stdout.strip() == NEW_RELEASE_TAG


@_needs(NEW_RELEASE_TAG)
def test_v1_22_3_tag_is_annotated():
    assert _git("cat-file", "-t", NEW_RELEASE_TAG).stdout.strip() == "tag"


@_needs(NEW_RELEASE_TAG)
def test_v1_22_3_tag_is_on_master_history():
    if not _have_ref(MASTER_REF):
        pytest.skip(f"{MASTER_REF} not available")
    result = _git("merge-base", "--is-ancestor", NEW_RELEASE_TAG + "^{commit}", MASTER_REF)
    if result.returncode not in (0, 1):
        pytest.skip(result.stderr.strip())
    assert result.returncode == 0


@_needs(NEW_RELEASE_TAG)
def test_v1_22_3_version_line():
    out = _git("show", NEW_RELEASE_TAG + ":runelite-plugin.properties").stdout
    assert re.search(r"^version=1\.22\.3$", out, re.MULTILINE)


@_needs(NEW_RELEASE_TAG)
def test_v1_22_3_tokens_within_budget():
    pytest.importorskip("tiktoken")
    assert _total("measure_tokens.py", NEW_RELEASE_TAG) <= TOKEN_BUDGET
