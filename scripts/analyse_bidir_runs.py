#!/usr/bin/env python3
"""Analyse per-scenario JSONL output from BidirectionalPathfinderABTest.

Each input directory contains <dataset>.jsonl files where each line is a JSON
record with pf* (production Pathfinder) and bpf* (BidirectionalPathfinder)
timings/results for one scenario.

Usage:
    analyse_bidir_runs.py JSONL_DIR [--label-baseline X] [--label-candidate Y]
"""
from __future__ import annotations

import argparse
import json
import statistics
import sys
from pathlib import Path
from typing import Dict, List, Tuple


def load_jsonl(path: Path) -> List[Dict]:
    rows = []
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line:
            continue
        rows.append(json.loads(line))
    return rows


def pct(p: float, xs: List[float]) -> float:
    if not xs:
        return float("nan")
    xs = sorted(xs)
    if len(xs) == 1:
        return xs[0]
    k = (len(xs) - 1) * p
    f, c = int(k), min(int(k) + 1, len(xs) - 1)
    if f == c:
        return xs[f]
    return xs[f] + (xs[c] - xs[f]) * (k - f)


def fmt_ms(x: float) -> str:
    if x != x:
        return "—"
    if x >= 100:
        return f"{x:.0f}"
    if x >= 10:
        return f"{x:.1f}"
    return f"{x:.2f}"


def fmt_pct(x: float) -> str:
    if x != x:
        return "—"
    sign = "+" if x >= 0 else ""
    return f"{sign}{x:.1f}%"


def to_rows(records: List[Dict]) -> List[Tuple[str, float, float, float, bool, bool]]:
    """(name, pf_ms, bpf_ms, pct_change, pf_reached, bpf_reached)"""
    out = []
    for r in records:
        pf_ms = r["pfElapsedNanos"] / 1_000_000.0
        bpf_ms = r["bpfElapsedNanos"] / 1_000_000.0
        delta = (bpf_ms - pf_ms) / pf_ms * 100.0 if pf_ms > 0 else float("nan")
        out.append((r["name"], pf_ms, bpf_ms, delta, bool(r["pfReached"]), bool(r["bpfReached"])))
    return out


def summarise(rows, reachable_bucket: bool):
    sub = [r for r in rows if r[4] == reachable_bucket and r[5] == reachable_bucket]
    bms = [r[1] for r in sub]
    cms = [r[2] for r in sub]
    pcts = [r[3] for r in sub if r[3] == r[3]]
    return {
        "count": len(sub),
        "baseline_median_ms": pct(0.5, bms),
        "candidate_median_ms": pct(0.5, cms),
        "baseline_p95_ms": pct(0.95, bms),
        "candidate_p95_ms": pct(0.95, cms),
        "baseline_max_ms": max(bms) if bms else float("nan"),
        "candidate_max_ms": max(cms) if cms else float("nan"),
        "median_pct": pct(0.5, pcts),
        "mean_pct": statistics.fmean(pcts) if pcts else float("nan"),
    }


def emit_dataset(name: str, rows, lab_b: str, lab_c: str) -> str:
    r_reach = summarise(rows, True)
    r_unreach = summarise(rows, False)
    disagree = [r for r in rows if r[4] != r[5]]
    out = []
    out.append(f"### `{name}`  ({len(rows)} scenarios: {r_reach['count']} reachable, {r_unreach['count']} unreachable"
               + (f", {len(disagree)} disagree" if disagree else "") + ")")
    out.append("")
    if disagree:
        out.append("**Reachability disagreements:**")
        out.append("")
        out.append(f"| Scenario | {lab_b} reached | {lab_c} reached | {lab_b} ms | {lab_c} ms |")
        out.append("|---|:---:|:---:|---:|---:|")
        for n_, b_, c_, _, br, cr in disagree:
            out.append(f"| {n_} | {br} | {cr} | {fmt_ms(b_)} | {fmt_ms(c_)} |")
        out.append("")
    out.append("**Reachable routes (the common case):**")
    out.append("")
    out.append(f"| Metric | {lab_b} | {lab_c} | Delta |")
    out.append("|---|---:|---:|---:|")
    out.append(f"| Median latency | {fmt_ms(r_reach['baseline_median_ms'])} ms | {fmt_ms(r_reach['candidate_median_ms'])} ms | {fmt_pct(r_reach['median_pct'])} |")
    out.append(f"| p95 latency | {fmt_ms(r_reach['baseline_p95_ms'])} ms | {fmt_ms(r_reach['candidate_p95_ms'])} ms | — |")
    out.append(f"| Max latency | {fmt_ms(r_reach['baseline_max_ms'])} ms | {fmt_ms(r_reach['candidate_max_ms'])} ms | — |")
    out.append("")
    if r_unreach["count"] > 0:
        out.append("**Unreachable routes (worst-case latency dominates):**")
        out.append("")
        out.append(f"| Metric | {lab_b} | {lab_c} | Delta |")
        out.append("|---|---:|---:|---:|")
        out.append(f"| Median latency | {fmt_ms(r_unreach['baseline_median_ms'])} ms | {fmt_ms(r_unreach['candidate_median_ms'])} ms | {fmt_pct(r_unreach['median_pct'])} |")
        out.append(f"| Max latency | {fmt_ms(r_unreach['baseline_max_ms'])} ms | {fmt_ms(r_unreach['candidate_max_ms'])} ms | — |")
        out.append("")
        unreach = [r for r in rows if not r[4] and not r[5]]
        unreach.sort(key=lambda r: -r[1])
        out.append(f"| Scenario | {lab_b} | {lab_c} | Delta |")
        out.append("|---|---:|---:|---:|")
        for n_, b_, c_, p_, _, _ in unreach[:10]:
            out.append(f"| {n_} | {fmt_ms(b_)} ms | {fmt_ms(c_)} ms | {fmt_pct(p_)} |")
        out.append("")
    regressions = [r for r in rows if r[4] and r[5] and r[3] == r[3] and r[3] > 5]
    regressions.sort(key=lambda r: -r[3])
    if regressions[:5]:
        out.append("**Top reachable-route regressions (>+5%):**")
        out.append("")
        out.append(f"| Scenario | {lab_b} | {lab_c} | Delta |")
        out.append("|---|---:|---:|---:|")
        for n_, b_, c_, p_, _, _ in regressions[:5]:
            out.append(f"| {n_} | {fmt_ms(b_)} ms | {fmt_ms(c_)} ms | {fmt_pct(p_)} |")
        out.append("")
    improves = [r for r in rows if r[4] and r[5] and r[3] == r[3] and r[3] < -5]
    improves.sort(key=lambda r: r[3])
    if improves[:5]:
        out.append("**Top reachable-route improvements (<-5%):**")
        out.append("")
        out.append(f"| Scenario | {lab_b} | {lab_c} | Delta |")
        out.append("|---|---:|---:|---:|")
        for n_, b_, c_, p_, _, _ in improves[:5]:
            out.append(f"| {n_} | {fmt_ms(b_)} ms | {fmt_ms(c_)} ms | {fmt_pct(p_)} |")
        out.append("")
    return "\n".join(out)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("jsonl_dir", type=Path)
    ap.add_argument("--label-baseline", default="Pathfinder")
    ap.add_argument("--label-candidate", default="Bidir")
    args = ap.parse_args()

    files = sorted(args.jsonl_dir.glob("*.jsonl"))
    if not files:
        print(f"No .jsonl files in {args.jsonl_dir}", file=sys.stderr)
        return 1

    print(f"# Per-route UX comparison: {args.label_baseline} vs {args.label_candidate} (bidir A/B harness)\n")
    all_rows = []
    for f in files:
        name = f.stem
        records = load_jsonl(f)
        rows = to_rows(records)
        print(emit_dataset(name, rows, args.label_baseline, args.label_candidate))
        all_rows.extend(rows)

    print("### Combined across all bundles\n")
    r_reach = summarise(all_rows, True)
    r_unreach = summarise(all_rows, False)
    print(f"- Reachable routes: {r_reach['count']} total — median latency {fmt_ms(r_reach['baseline_median_ms'])} ms → {fmt_ms(r_reach['candidate_median_ms'])} ms ({fmt_pct(r_reach['median_pct'])} per route median); p95 {fmt_ms(r_reach['baseline_p95_ms'])} ms → {fmt_ms(r_reach['candidate_p95_ms'])} ms; max {fmt_ms(r_reach['baseline_max_ms'])} ms → {fmt_ms(r_reach['candidate_max_ms'])} ms.")
    print(f"- Unreachable routes: {r_unreach['count']} total — median latency {fmt_ms(r_unreach['baseline_median_ms'])} ms → {fmt_ms(r_unreach['candidate_median_ms'])} ms; max {fmt_ms(r_unreach['baseline_max_ms'])} ms → {fmt_ms(r_unreach['candidate_max_ms'])} ms.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
