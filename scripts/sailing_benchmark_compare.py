#!/usr/bin/env python3
"""Compare the deterministic fields of two ``sailingBenchmark`` JSON files.

``./gradlew sailingBenchmark`` writes ``build/reports/sailing-benchmark/<suite>.json`` with one
row per (route, search) in its ``searches`` array.  This script compares two such files, for
example one per plugin checkout (``-PshortestPathDir``), and exits 0 only when every
deterministic field of every row is identical: reached, nodesChecked, pathLength, pathUnit,
legs, distanceTiles, pathSha256, pathCost, closestTile and termination.  Timing fields
(medianMs, fastestMs, medianWallMs, runsMs) are ignored.

Exit status: 0 identical, 1 at least one row differs or is on one side only, 2 unusable input
(a file that cannot be read, has no ``searches`` rows, or has rows without ``pathSha256``).
"""

import argparse
import json
import sys

FIELDS = ("reached", "nodesChecked", "pathLength", "pathUnit", "legs", "distanceTiles",
          "pathSha256", "pathCost", "closestTile", "termination")


class Unusable(Exception):
    """The input cannot be compared (exit status 2)."""


def load(path):
    """The rows of a benchmark file keyed by (route, search); raises Unusable."""
    try:
        with open(path, encoding="utf-8") as handle:
            document = json.load(handle)
    except (OSError, ValueError) as error:
        raise Unusable("%s: %s" % (path, error))
    searches = document.get("searches") if isinstance(document, dict) else None
    if not isinstance(searches, list) or not searches:
        raise Unusable("%s: no searches rows" % path)
    rows = {}
    for row in searches:
        if not isinstance(row, dict) or "route" not in row or "search" not in row:
            raise Unusable("%s: a searches entry has no route and search" % path)
        if not row.get("pathSha256"):
            raise Unusable("%s: row (%s, %s) has no pathSha256 (an older benchmark?)"
                           % (path, row["route"], row["search"]))
        key = (row["route"], row["search"])
        if key in rows:
            raise Unusable("%s: duplicate row (%s, %s)" % (path, key[0], key[1]))
        rows[key] = row
    return rows


def compare(old, new):
    """Human-readable differences between two row maps; empty when identical."""
    differences = []
    for key in sorted(old.keys() | new.keys()):
        label = "(%s, %s)" % key
        if key not in new:
            differences.append("%s: only in old" % label)
        elif key not in old:
            differences.append("%s: only in new" % label)
        else:
            for field in FIELDS:
                if old[key].get(field) != new[key].get(field):
                    differences.append("%s: %s %r -> %r"
                                       % (label, field, old[key].get(field), new[key].get(field)))
    return differences


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("old", help="sailingBenchmark JSON of the old plugin checkout")
    parser.add_argument("new", help="sailingBenchmark JSON of the new plugin checkout")
    args = parser.parse_args(argv)
    try:
        old = load(args.old)
        new = load(args.new)
    except Unusable as error:
        print("sailing_benchmark_compare: %s" % error, file=sys.stderr)
        return 2
    differences = compare(old, new)
    for difference in differences:
        print(difference)
    if differences:
        print("%d difference(s) across %d rows" % (len(differences), len(old.keys() | new.keys())))
        return 1
    print("%d rows identical" % len(old))
    return 0


if __name__ == "__main__":
    sys.exit(main())
