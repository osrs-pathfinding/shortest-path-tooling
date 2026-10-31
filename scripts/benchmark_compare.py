#!/usr/bin/env python3
"""A/B-compare two benchmark result sets against the L5 perf gate.

Each argument names either a single format-2 benchmark result JSON (what
``./gradlew benchmark --args="--manifest M --output OUT"`` writes) or a
directory of them — a directory pools its ``*.json`` files, so envelope
gates can take min-of-N and median-of-N across runs:

    python3 benchmark_compare.py PRE POST

Hard envelope gates (deterministic, same-hardware):
``static_build_allocated_bytes``/``heap_after_prepare_bytes`` median
<= +5%; ``routing_static_build_ms`` min <= +10%, median <= +15%.
Per-scenario tripwires on ``(suite, scenario)`` joins: ``total_ns`` /
``graph_prepare_ns`` medians <= +20% — the query path is untouched by
the lever, so this is a tripwire, not a budget.

A metric present on only one side is a breach (a crashed or truncated
manifest must not read as green); absent on both, it is skipped.  A
scenario row missing metric keys evaluates only on present metrics; a
scenario with zero comparable metrics is reported.  An empty
scenario-key intersection fails — never a vacuous pass.  Exit 0 when
every gate holds, non-zero otherwise; stdout names every breach.
"""

import argparse
import json
import statistics
import sys
from pathlib import Path

ALLOC_PCT = 5.0
HEAP_PCT = 5.0
BUILD_MIN_PCT = 10.0
BUILD_MEDIAN_PCT = 15.0
SCENARIO_PCT = 20.0
SCENARIO_METRICS = ("total_ns", "graph_prepare_ns")
TRIPWIRE_MIN_SAMPLES = 10


def load_side(path):
    """One benchmark envelope, or every ``*.json`` under a directory."""
    if path.is_dir():
        files = sorted(path.glob("*.json"))
        if not files:
            raise ValueError(f"no benchmark JSON files under {path}")
    elif path.is_file():
        files = [path]
    else:
        raise ValueError(f"no such file or directory: {path}")
    out = []
    for f in files:
        try:
            out.append(json.loads(f.read_text()))
        except (json.JSONDecodeError, OSError) as e:
            raise ValueError(f"cannot read benchmark JSON {f}: {e}")
    return out


def env_metric(env, *path):
    """Nested dict lookup; only plain numbers count as present."""
    cur = env
    for key in path:
        if not isinstance(cur, dict) or key not in cur:
            return None
        cur = cur[key]
    if isinstance(cur, bool) or not isinstance(cur, (int, float)):
        return None
    return cur


def collect(envs, *path):
    """All non-negative values of an envelope metric across one side."""
    return [v for e in envs
            if (v := env_metric(e, *path)) is not None and v >= 0]


def delta_pct(pre, post):
    if pre <= 0:
        # No usable baseline: NaN would make every ``change > limit`` gate
        # read False, so any growth over a zero baseline is an unbounded
        # regression instead (and 0 -> 0 is no change).
        return float("inf") if post > 0 else 0.0
    return (post - pre) / pre * 100.0


def compare_envelope(pre_envs, post_envs, breaches, notes):
    """execution_metadata gates; one-sided presence is a breach."""
    for path, limit in (
            (("memory", "static_build_allocated_bytes"), ALLOC_PCT),
            (("memory", "heap_after_prepare_bytes"), HEAP_PCT)):
        key = path[-1]
        pre = collect(pre_envs, "execution_metadata", *path)
        post = collect(post_envs, "execution_metadata", *path)
        if not pre and not post:
            notes.append(f"skip {key}: absent on both sides")
        elif not pre or not post:
            breaches.append(f"BREACH: {key} present on only one side")
        else:
            change = delta_pct(statistics.median(pre),
                               statistics.median(post))
            if change > limit:
                breaches.append(
                    f"BREACH: {key} median {change:+.1f}% > +{limit:.0f}%")

    key = "routing_static_build_ms"
    pre = collect(pre_envs, "execution_metadata", key)
    post = collect(post_envs, "execution_metadata", key)
    if not pre and not post:
        notes.append(f"skip {key}: absent on both sides")
    elif not pre or not post:
        breaches.append(f"BREACH: {key} present on only one side")
    else:
        change = delta_pct(min(pre), min(post))
        if change > BUILD_MIN_PCT:
            breaches.append(
                f"BREACH: {key} min {change:+.1f}% > +{BUILD_MIN_PCT:.0f}%")
        change = delta_pct(statistics.median(pre), statistics.median(post))
        if change > BUILD_MEDIAN_PCT:
            breaches.append(
                f"BREACH: {key} median {change:+.1f}% "
                f"> +{BUILD_MEDIAN_PCT:.0f}%")


def scenario_rows(envs):
    """(suite, scenario) -> observation rows, pooled across envelopes."""
    rows = {}
    for env in envs:
        for obs in env.get("observations") or []:
            if not isinstance(obs, dict):
                continue
            key = (obs.get("suite"), obs.get("scenario"))
            rows.setdefault(key, []).append(obs)
    return rows


def _samples(rows, metric):
    return [o[metric] for o in rows
            if isinstance(o.get(metric), (int, float))
            and not isinstance(o.get(metric), bool)]


def compare_scenarios(pre_rows, post_rows, breaches, warnings):
    """Per-scenario metric-median tripwires over the common key set."""
    common = sorted(set(pre_rows) & set(post_rows))
    for key in pre_rows:
        if key not in post_rows:
            warnings.append(f"WARN: scenario only in pre: {key[0]}/{key[1]}")
    for key in post_rows:
        if key not in pre_rows:
            warnings.append(f"WARN: scenario only in post: {key[0]}/{key[1]}")
    for key in common:
        label = f"{key[0]}/{key[1]}"
        comparable = 0
        for metric in SCENARIO_METRICS:
            pre = _samples(pre_rows[key], metric)
            post = _samples(post_rows[key], metric)
            if not pre or not post:
                continue
            comparable += 1
            if len(pre) < TRIPWIRE_MIN_SAMPLES or len(post) < TRIPWIRE_MIN_SAMPLES:
                warnings.append(
                    f"WARN: {label} {metric}: {len(pre)}/{len(post)} "
                    f"samples < {TRIPWIRE_MIN_SAMPLES}")
            change = delta_pct(statistics.median(pre),
                               statistics.median(post))
            if change > SCENARIO_PCT:
                breaches.append(
                    f"BREACH: {label} {metric} median {change:+.1f}% "
                    f"> +{SCENARIO_PCT:.0f}%")
        if comparable == 0:
            warnings.append(f"WARN: {label}: no comparable metrics")
    return common


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("pre", type=Path,
                    help="baseline result JSON or directory of them")
    ap.add_argument("post", type=Path,
                    help="candidate result JSON or directory of them")
    args = ap.parse_args(argv)
    try:
        pre_envs = load_side(args.pre)
        post_envs = load_side(args.post)
    except ValueError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1

    breaches, warnings, notes = [], [], []
    compare_envelope(pre_envs, post_envs, breaches, notes)
    common = compare_scenarios(scenario_rows(pre_envs),
                               scenario_rows(post_envs),
                               breaches, warnings)
    if not common:
        breaches.append(
            "BREACH: no common scenarios between the two manifests")

    for line in warnings:
        print(line)
    for line in notes:
        print(f"note: {line}")
    for line in breaches:
        print(line)
    if breaches:
        print(f"\nFAIL: {len(breaches)} breach(es)")
        return 1
    print(f"\nOK: {len(common)} scenario(s) compared, envelope gates hold")
    return 0


if __name__ == "__main__":
    sys.exit(main())
