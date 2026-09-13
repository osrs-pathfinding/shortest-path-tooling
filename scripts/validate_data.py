#!/usr/bin/env python3
"""Deterministic data-validation checks for committed plugin data.

Inputs (all read-only):
- committed submodule TSVs, enumerated via
  ``git -C shortest-path ls-files src/main/resources`` — never a
  filesystem glob, so gitignored scratch can never leak into a check
- curated league-region bboxes at src/test/resources/leagues_regions.tsv
- the committed collision-map.zip

Outputs: ``file:line`` findings on stdout, a ``Summary:`` trailer, and
an exit code that is nonzero only when a ran hard check produced
findings — advisory checks report but never move the exit code.

Run standalone::

    python3 scripts/validate_data.py [check ...]
"""

import re
import subprocess
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
PLUGIN = REPO / "shortest-path"
RESOURCES = "src/main/resources"
BBOX_TSV = REPO / "src" / "test" / "resources" / "leagues_regions.tsv"
COLLISION_ZIP = PLUGIN / RESOURCES / "collision-map.zip"
REGIONS_TSV = PLUGIN / RESOURCES / "leagues" / "regions.tsv"
SEASONAL_TSV = PLUGIN / RESOURCES / "transports" / "seasonal_transports.tsv"

GIT_TIMEOUT_SECONDS = 120
COORD_RE = re.compile(r"^\d+ \d+ \d+$")

# Canonical transport TSV column names — mirrors
# TransportRecord.Fields in the plugin's Java parser.  The loader maps
# columns by name, so an unknown header cell is silently dropped
# data, not an error — which is exactly what this check must catch.
TRANSPORT_FIELDS = frozenset({
    "Origin", "Destination", "Skills", "Items", "Quests", "Duration",
    "Display info", "Consumable", "Wilderness level",
    "menuOption menuTarget objectID", "Varbits", "VarPlayers",
    "Region override",
})


def _git_ls_files(*pathspecs):
    """Committed submodule files matching the given pathspecs.

    The leaf's own subprocess seam — monkeypatched in tests.  Only
    committed paths come back; an uncommitted or gitignored scratch
    TSV can never enter a check.
    """
    proc = subprocess.run(
        ["git", "-C", "shortest-path", "ls-files", *pathspecs],
        cwd=REPO, capture_output=True, text=True,
        timeout=GIT_TIMEOUT_SECONDS)
    if proc.returncode != 0:
        tail = (proc.stderr or "").strip().splitlines()
        sys.exit("git -C shortest-path ls-files failed: "
                 + (tail[-1] if tail else f"exit {proc.returncode}"))
    return [line for line in proc.stdout.splitlines() if line.strip()]


def _parse_tsv(path):
    """Parse a plugin TSV into (headers, header_lineno, rows).

    The first non-blank line is the header — ``#``-prefixed by
    convention.  Later ``#`` lines and blank lines are comments.
    Data rows come back as (lineno, fields) pairs; Java's loader
    splits on tabs without ``-1``, so trailing empty cells are already
    absent from ``fields``.
    """
    headers = None
    header_lineno = 0
    rows = []
    for lineno, line in enumerate(path.read_text().splitlines(), 1):
        if not line.strip():
            continue
        if headers is None:
            headers = line.lstrip("#").strip().split("\t")
            header_lineno = lineno
            continue
        if line.startswith("#"):
            continue
        rows.append((lineno, line.split("\t")))
    return headers, header_lineno, rows


def check_tsv_structure():
    """Format lint over committed transports/ + destinations/ TSVs.

    Asserts per file: header cells drawn from the canonical
    TransportRecord.Fields set (transports) or carrying the
    Destination column (destinations); every data row reaching its
    file's coordinate columns; every non-empty Origin/Destination
    cell in ``x y z`` form; and permutation rows present on both
    sides or neither — a one-sided set is dead data the loader never
    turns into an edge.
    """
    findings = []
    rels = _git_ls_files(
        f"{RESOURCES}/transports", f"{RESOURCES}/destinations")
    for rel in sorted(r for r in rels if r.endswith(".tsv")):
        headers, hln, rows = _parse_tsv(PLUGIN / rel)
        if headers is None:
            findings.append(f"{rel}:1: no header line")
            continue
        if rel.startswith(f"{RESOURCES}/transports/"):
            for cell in headers:
                if cell not in TRANSPORT_FIELDS:
                    findings.append(
                        f"{rel}:{hln}: unknown header cell {cell!r}")
        if "Destination" not in headers:
            findings.append(f"{rel}:{hln}: missing Destination column")
            continue
        di = headers.index("Destination")
        oi = headers.index("Origin") if "Origin" in headers else -1
        # A row that stops short of a coordinate column parses to a
        # permutation marker or an undefined endpoint — silent loader
        # degradation rather than the intended transport.
        required = max(oi, di) + 1
        perm_origins = 0
        perm_destinations = 0
        for lineno, fields in rows:
            if len(fields) < required:
                findings.append(
                    f"{rel}:{lineno}: row has {len(fields)} fields, "
                    f"needs {required} to cover coordinate columns")
                continue
            origin = fields[oi].strip() if oi >= 0 else ""
            destination = fields[di].strip()
            for label, cell in (("Origin", origin),
                                ("Destination", destination)):
                if cell and not COORD_RE.match(cell):
                    findings.append(
                        f"{rel}:{lineno}: malformed {label} "
                        f"coordinate {cell!r}")
            if oi >= 0:
                if origin and not destination:
                    perm_origins += 1
                elif destination and not origin:
                    perm_destinations += 1
        # A concrete-Origin/blank-Destination row is a permutation
        # anchor that only emits edges when paired with a
        # blank-Origin/concrete-Destination row in the same file (and
        # vice versa) — a one-sided set is dead anchor data.  Files
        # without an Origin column are usable-anywhere teleport lists
        # by design, so the pair rule does not apply to them.
        if oi >= 0 and (perm_origins > 0) != (perm_destinations > 0):
            findings.append(
                f"{rel}:{hln}: one-sided permutation set "
                f"({perm_origins} origin rows, "
                f"{perm_destinations} destination rows)")
    return findings


# name -> check function returning a list of finding strings.
CHECKS = {
    "tsv-structure": check_tsv_structure,
}

# Checks whose findings are reported but never move the exit code.
ADVISORY_CHECKS = frozenset()


def main(argv=None):
    names = list(sys.argv[1:] if argv is None else argv)
    if not names:
        names = list(CHECKS)
    unknown = [n for n in names if n not in CHECKS]
    if unknown:
        sys.exit(f"unknown check {unknown[0]!r} — valid checks: "
                 + ", ".join(CHECKS))
    total = 0
    hard_fail = False
    for name in names:
        findings = CHECKS[name]()
        total += len(findings)
        if name in ADVISORY_CHECKS:
            for finding in findings:
                print(finding)
        else:
            for finding in findings:
                print(f"FAIL {finding}")
            if findings:
                hard_fail = True
    print(f"Summary: {total} findings across {len(names)} checks")
    return 1 if hard_fail else 0


if __name__ == "__main__":
    sys.exit(main())
