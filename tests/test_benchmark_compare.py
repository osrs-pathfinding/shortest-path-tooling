"""Threshold assertions for ``scripts/benchmark_compare.py``.

The fixtures pin the real format-2 benchmark envelope (verified against a
live ``./gradlew benchmark --args="--manifest M --output OUT"`` run):
envelope metrics under ``execution_metadata.memory`` plus
``routing_static_build_ms``, and per-observation ``total_ns`` /
``graph_prepare_ns`` joined on ``(suite, scenario)``.  Each positional
argument may also be a directory of ``*.json`` runs — min-of-N and
median-of-N envelope gates pool the files per side.
"""

import copy
import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))
import benchmark_compare as bc  # noqa: E402

FIXTURES = Path(__file__).resolve().parent / "fixtures"
PRE = FIXTURES / "benchmark-pre.json"
POST = FIXTURES / "benchmark-post.json"


def _load(path):
    return json.loads(path.read_text())


def _write(tmp_path, name, obj):
    p = tmp_path / name
    p.write_text(json.dumps(obj))
    return p


def _mutated(src, tmp_path, name, mutate):
    obj = copy.deepcopy(_load(src))
    mutate(obj)
    return _write(tmp_path, name, obj)


# ---------- pass paths ----------


def test_identical_manifest_passes():
    assert bc.main([str(PRE), str(PRE)]) == 0


def test_fixture_pair_within_thresholds_passes(capsys):
    assert bc.main([str(PRE), str(POST)]) == 0
    assert "BREACH" not in capsys.readouterr().out


# ---------- envelope breaches ----------


def test_allocation_breach_names_metric(tmp_path, capsys):
    post = _mutated(POST, tmp_path, "post.json", lambda o: o[
        "execution_metadata"]["memory"].update(
            static_build_allocated_bytes=211000000))
    assert bc.main([str(PRE), str(post)]) != 0
    assert "static_build_allocated_bytes" in capsys.readouterr().out


def test_heap_breach_names_metric(tmp_path, capsys):
    post = _mutated(POST, tmp_path, "post.json", lambda o: o[
        "execution_metadata"]["memory"].update(
            heap_after_prepare_bytes=39000000))
    assert bc.main([str(PRE), str(post)]) != 0
    assert "heap_after_prepare_bytes" in capsys.readouterr().out


def test_build_min_breach_names_metric(tmp_path, capsys):
    post = _mutated(POST, tmp_path, "post.json", lambda o: o[
        "execution_metadata"].update(routing_static_build_ms=1400))
    assert bc.main([str(PRE), str(post)]) != 0
    assert "routing_static_build_ms" in capsys.readouterr().out


def test_build_median_breach_with_min_inside(tmp_path, capsys):
    # Five runs per side via the directory form: the post minimum stays
    # under +10% while the median lands over +15% — the median gate must
    # catch it even though the min gate passes.
    pre_dir = tmp_path / "pre"
    post_dir = tmp_path / "post"
    pre_dir.mkdir()
    post_dir.mkdir()
    for i in range(5):
        _write(pre_dir, f"run{i}.json", _load(PRE))
    build_values = [1310, 1440, 1450, 1460, 1470]
    for i, build_ms in enumerate(build_values):
        _mutated(POST, post_dir, f"run{i}.json", lambda o,
                 v=build_ms: o["execution_metadata"].update(
                     routing_static_build_ms=v))
    assert bc.main([str(pre_dir), str(post_dir)]) != 0
    out = capsys.readouterr().out
    assert "routing_static_build_ms" in out
    assert "median" in out


# ---------- scenario tripwires ----------


def test_scenario_total_ns_tripwire_names_scenario(tmp_path, capsys):
    def bump(obj):
        for obs in obj["observations"]:
            if obs["scenario"] == "gps-natural-0001/maxed":
                obs["total_ns"] = int(obs["total_ns"] * 1.30)
    post = _mutated(POST, tmp_path, "post.json", bump)
    assert bc.main([str(PRE), str(post)]) != 0
    out = capsys.readouterr().out
    assert "total_ns" in out
    assert "gps-natural-0001" in out


def test_scenario_graph_prepare_tripwire(tmp_path, capsys):
    def bump(obj):
        for obs in obj["observations"]:
            obs["graph_prepare_ns"] = int(obs["graph_prepare_ns"] * 1.30)
    post = _mutated(POST, tmp_path, "post.json", bump)
    assert bc.main([str(PRE), str(post)]) != 0
    assert "graph_prepare_ns" in capsys.readouterr().out


# ---------- structural failures ----------


def test_empty_intersection_fails(tmp_path, capsys):
    post = _mutated(POST, tmp_path, "post.json", lambda o: [
        obs.update(suite="other-suite") for obs in o["observations"]])
    assert bc.main([str(PRE), str(post)]) != 0
    assert "no common scenarios" in capsys.readouterr().out


def test_one_sided_envelope_metric_fails(tmp_path, capsys):
    # A metric present on one side only must not silently pass — a
    # crashed or truncated manifest reads as a breach, never green.
    post = _mutated(POST, tmp_path, "post.json", lambda o: o[
        "execution_metadata"]["memory"].pop(
            "static_build_allocated_bytes"))
    assert bc.main([str(PRE), str(post)]) != 0
    assert "static_build_allocated_bytes" in capsys.readouterr().out


def test_missing_scenario_metric_narrows_and_reports(tmp_path, capsys):
    def strip(obj):
        for obs in obj["observations"]:
            obs.pop("graph_prepare_ns", None)
            if obs["scenario"] == "gps-natural-0012/early":
                obs["total_ns"] = None
    post = _mutated(POST, tmp_path, "post.json", strip)
    assert bc.main([str(PRE), str(post)]) == 0
    out = capsys.readouterr().out
    # graph_prepare_ns is absent on the post side everywhere: the gate
    # narrows to total_ns only. gps-natural-0012/early loses its last
    # comparable metric and is reported, not silently skipped.
    assert "gps-natural-0012/early" in out


def test_bad_json_fails(tmp_path, capsys):
    bad = tmp_path / "bad.json"
    bad.write_text("{not json")
    assert bc.main([str(PRE), str(bad)]) != 0


def test_empty_directory_fails(tmp_path):
    empty = tmp_path / "empty"
    empty.mkdir()
    assert bc.main([str(PRE), str(empty)]) != 0


# ---------- zero baselines ----------


def test_delta_pct_zero_baseline_is_unbounded_not_nan():
    assert bc.delta_pct(0, 10**9) == float("inf")
    assert bc.delta_pct(0, 0) == 0.0


def test_zero_baseline_envelope_metric_still_breaches(tmp_path, capsys):
    # A baseline reporting 0 allocated bytes (counter unavailable) must not
    # let a huge post value read green via a NaN comparison.
    pre = _mutated(PRE, tmp_path, "pre.json", lambda o: o[
        "execution_metadata"]["memory"].update(
            static_build_allocated_bytes=0))
    post = _mutated(POST, tmp_path, "post.json", lambda o: o[
        "execution_metadata"]["memory"].update(
            static_build_allocated_bytes=10**9))
    assert bc.main([str(pre), str(post)]) != 0
    assert "static_build_allocated_bytes" in capsys.readouterr().out


def test_zero_baseline_scenario_metric_still_breaches(tmp_path, capsys):
    def zero(obj):
        for obs in obj["observations"]:
            obs["graph_prepare_ns"] = 0
    pre = _mutated(PRE, tmp_path, "pre.json", zero)
    assert bc.main([str(pre), str(POST)]) != 0
    assert "graph_prepare_ns" in capsys.readouterr().out


def test_zero_baseline_with_zero_post_passes(tmp_path):
    def zero(obj):
        for obs in obj["observations"]:
            obs["graph_prepare_ns"] = 0
    pre = _mutated(PRE, tmp_path, "pre.json", zero)
    post = _mutated(POST, tmp_path, "post.json", zero)
    assert bc.main([str(pre), str(post)]) == 0
