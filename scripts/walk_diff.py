#!/usr/bin/env python3
"""Classify the walks of two ``pathDump`` files into the D-13 tiers.

``./gradlew pathDump`` writes one JSON line per canonical scenario after a header line.  This
script compares the dump of an old plugin checkout with the dump of a new one, scenario by
scenario, and puts every (route, account) into one of three tiers:

* strict: steps, click points and kept step indices are identical;
* equivalent: same verdict, cost, termination and length, the same leg boundaries and leg
  starts, identical non-walking steps, and the new leg ends are proven to serve the next action
  (see below); only interior walking tiles, click points or leg end tiles differ;
* divergent: anything else.  Each divergent case carries its own reason.

A *leg* is a maximal run of walking moves: consecutive steps with no transport flag, the same
bank state, the same plane and a Chebyshev distance of one.  A changed leg end is equivalent
only when the dump proves the new end tile still serves the next action: the tile before a
local transport must be a usable origin of it (``usableFromPrevious``), and the tile before a
global teleport must be one it can be cast from (``castableFromPrevious``).

Equal cost does not make a walk one the game takes, so both dumps are also scanned for
*zigzags*: two consecutive diagonal walking moves that turn by 90 degrees (north-east then
north-west, south-east then north-east, ...), a dogleg the game's own walking never draws on
open ground.  The count, the scenarios it occurs in and whether the middle tile is a click, a
kept step or an interior tile are printed and written to the summary for both sides; more
zigzags in the new dump are reported but do not change the exit status.

Exit status: 0 when nothing is divergent, 1 when at least one case is, 2 when the input is
unusable (a missing or empty file, a header or line that is not valid, differing tiers, or
differing scenario sets).  ``--summary PATH`` writes a JSON summary; ``--list`` prints every
non-strict scenario with its reason.
"""

import argparse
import collections
import json
import sys

UNDEFINED = -1

TRANSPORT_LABEL = "leg end tile differs before a transport"
TELEPORT_LABEL = "leg end tile differs before a global teleport"
INTERIOR_LABEL = "interior tiles/clicks only"

REASON_USABLE = "new leg end cannot use the next transport (usableFromPrevious false or missing)"
REASON_CASTABLE = "new leg end cannot cast the next global teleport (castableFromPrevious false or missing)"
REASON_NEXT_ACTION = "next action after a changed leg end differs"
REASON_FOLLOWS = "new follows is not true on a reached row"
REASON_END_NOT_CLICK = "a new leg end is neither a click nor a kept step"

REQUIRED_ROW_KEYS = ("scenario", "reached", "termination", "steps", "clicks", "kept")
TRANSPORT_FIELDS = ("type", "destination", "duration", "displayInfo", "objectInfo")


class Unusable(Exception):
    """The input cannot be compared (exit status 2)."""


class Result:
    """The verdict of one (route, account): tier, divergent reason, detail labels, end counts."""

    def __init__(self, tier, reason="", labels=None, ends=None):
        self.tier = tier
        self.reason = reason
        self.labels = labels or []
        self.ends = ends or {"transport": 0, "teleport": 0}


def divergent(reason):
    return Result("divergent", reason)


def unpack(packed):
    """(x, y, plane) of a packed world point, read as an unsigned 32-bit value."""
    packed &= 0xFFFFFFFF
    return packed & 0x7FFF, (packed >> 15) & 0x7FFF, (packed >> 30) & 3


def is_walk(a, b):
    """Whether step ``b`` is a walking move from step ``a``; steps are [packed, bank, transport]."""
    if b[2] or a[1] != b[1]:
        return False
    ax, ay, aplane = unpack(a[0])
    bx, by, bplane = unpack(b[0])
    return aplane == bplane and max(abs(ax - bx), abs(ay - by)) == 1


def legs(steps):
    """Maximal runs of walking moves as (start index, end index) pairs."""
    out = []
    start = 0
    for i in range(1, len(steps) + 1):
        if i < len(steps) and is_walk(steps[i - 1], steps[i]):
            continue
        if i - 1 > start:
            out.append((start, i - 1))
        start = i
    return out


def zigzags(row):
    """(middle index, role) of every zigzag in ``row``: two diagonal walking moves turning 90 degrees.

    The role says what the middle tile is: ``click``, ``kept`` (a kept step) or ``interior``.
    """
    steps = row["steps"]
    clicks, kept = set(row["clicks"]), set(row["kept"])
    out = []
    for i in range(1, len(steps) - 1):
        if not (is_walk(steps[i - 1], steps[i]) and is_walk(steps[i], steps[i + 1])):
            continue
        (ax, ay, _), (bx, by, _), (cx, cy, _) = (unpack(steps[j][0]) for j in (i - 1, i, i + 1))
        first, second = (bx - ax, by - ay), (cx - bx, cy - by)
        if 0 in first or 0 in second:
            continue
        if (first[0] == -second[0]) != (first[1] == -second[1]):
            out.append((i, "click" if i in clicks else "kept" if i in kept else "interior"))
    return out


def zigzag_counts(rows):
    """Total zigzags, scenarios with one, totals by middle-tile role, and the first cases."""
    total, scenarios, roles, cases = 0, 0, collections.Counter(), []
    for name in sorted(rows):
        found = zigzags(rows[name])
        total += len(found)
        scenarios += 1 if found else 0
        for index, role in found:
            roles[role] += 1
            if len(cases) < 20:
                cases.append({"scenario": name, "index": index, "role": role})
    return {"zigzags": total, "scenarios": scenarios, "roles": dict(roles), "cases": cases}


def records_by_index(row):
    return {record["index"]: record for record in row.get("transports") or []}


def same_transport(old, new, compare_origin):
    if old is None or new is None:
        return old is new
    fields = TRANSPORT_FIELDS + (("origin",) if compare_origin else ())
    return all(old.get(field) == new.get(field) for field in fields)


def classify(old, new):
    """The tier of one scenario's walk in ``new`` against ``old``."""
    if old["reached"] != new["reached"]:
        return divergent("reached differs")
    if old.get("cost") != new.get("cost"):
        return divergent("cost differs")
    if old["termination"] != new["termination"]:
        return divergent("termination differs")
    so, sn = old["steps"], new["steps"]
    if len(so) != len(sn):
        return divergent("path length differs")
    lo, ln = legs(so), legs(sn)
    if lo != ln:
        return divergent("leg boundaries differ")
    for start, _ in lo:
        if so[start] != sn[start]:
            return divergent("leg start differs")

    walked = {i for start, end in lo for i in range(start + 1, end + 1)}
    changed_ends = {end for _, end in lo if so[end] != sn[end]}
    to, tn = records_by_index(old), records_by_index(new)
    for i in range(len(so)):
        if i in walked:
            continue
        after_change = (i - 1) in changed_ends
        reason = REASON_NEXT_ACTION if after_change else "non-walking step differs"
        if so[i] != sn[i]:
            return divergent(reason)
        if so[i][2]:
            compare_origin = i == 0 or so[i - 1] == sn[i - 1]
            if not same_transport(to.get(i), tn.get(i), compare_origin):
                return divergent(reason)

    ends = {"transport": 0, "teleport": 0}
    for _, end in lo:
        if end not in changed_ends:
            continue
        if end == len(so) - 1:
            return divergent("the route's last leg end differs")
        following = end + 1
        if so[following][1] != so[end][1]:
            return divergent("leg end before a bank-state change differs")
        old_record, new_record = to.get(following), tn.get(following)
        if not so[following][2] or old_record is None or new_record is None:
            return divergent(REASON_NEXT_ACTION)
        old_local = old_record.get("origin") != UNDEFINED
        new_local = new_record.get("origin") != UNDEFINED
        if old_local != new_local:
            return divergent(REASON_NEXT_ACTION)
        if new_local:
            if new_record.get("usableFromPrevious") is not True:
                return divergent(REASON_USABLE)
            ends["transport"] += 1
        else:
            if new_record.get("castableFromPrevious") is not True:
                return divergent(REASON_CASTABLE)
            ends["teleport"] += 1

    marked = set(new["clicks"]) | set(new["kept"])
    if any(end not in marked for _, end in ln):
        return divergent(REASON_END_NOT_CLICK)
    if new["reached"] and new.get("follows") is not True:
        return divergent(REASON_FOLLOWS)

    if so == sn and old["clicks"] == new["clicks"] and old["kept"] == new["kept"]:
        return Result("strict")
    labels = []
    if ends["transport"]:
        labels.append(TRANSPORT_LABEL)
    if ends["teleport"]:
        labels.append(TELEPORT_LABEL)
    return Result("equivalent", labels=labels or [INTERIOR_LABEL], ends=ends)


def load(path):
    """(header, rows by scenario) of a dump; raises Unusable."""
    try:
        with open(path, encoding="utf-8") as handle:
            lines = [(number, line) for number, line in enumerate(handle, 1) if line.strip()]
    except (OSError, UnicodeDecodeError) as error:
        raise Unusable("%s: %s" % (path, error))
    if not lines:
        raise Unusable("%s: empty dump" % path)
    parsed = []
    for number, line in lines:
        try:
            parsed.append((number, json.loads(line)))
        except ValueError:
            raise Unusable("%s:%d: line is not JSON" % (path, number))
    _, first = parsed[0]
    if not isinstance(first, dict) or not isinstance(first.get("header"), dict):
        raise Unusable("%s: the first line is not a header row" % path)
    rows = {}
    for number, row in parsed[1:]:
        where = "%s:%d" % (path, number)
        if not isinstance(row, dict) or any(key not in row for key in REQUIRED_ROW_KEYS):
            raise Unusable("%s: not a scenario row" % where)
        if not isinstance(row["scenario"], str):
            raise Unusable("%s: scenario is not a string" % where)
        for key in ("steps", "clicks", "kept"):
            if not isinstance(row[key], list):
                raise Unusable("%s: %s is not a list" % (where, key))
        for key in ("clicks", "kept"):
            if not all(isinstance(index, int) for index in row[key]):
                raise Unusable("%s: %s holds a non-integer" % (where, key))
        for entry in row["steps"]:
            if not (isinstance(entry, list) and len(entry) == 3):
                raise Unusable("%s: malformed step %r" % (where, entry))
        if row["scenario"] in rows:
            raise Unusable("%s: duplicate scenario %s" % (where, row["scenario"]))
        rows[row["scenario"]] = row
    if not rows:
        raise Unusable("%s: the dump has no rows" % path)
    return first["header"], rows


def has_bank_change(row):
    steps = row["steps"]
    return any(steps[i - 1][1] != steps[i][1] for i in range(1, len(steps)))


def follows_counts(rows):
    reached = [row for row in rows.values() if row["reached"]]
    return len(reached), sum(1 for row in reached if row.get("follows") is True)


def summarise(old_header, new_header, old, new, results):
    tiers = collections.Counter(result.tier for result in results.values())
    detail = collections.Counter(label for result in results.values() for label in result.labels)
    end_cases = []
    end_counts = {"transport": 0, "teleport": 0}
    for name in sorted(results):
        for kind in ("transport", "teleport"):
            if results[name].ends[kind]:
                end_cases.append({"scenario": name, "kind": kind})
                end_counts[kind] += 1
    old_reached, old_follows = follows_counts(old)
    new_reached, new_follows = follows_counts(new)
    by_clicks = sorted(new.values(), key=lambda row: (-len(row["clicks"]), row["scenario"]))
    return {
        "old": old_header,
        "new": new_header,
        "old_count": len(old),
        "new_count": len(new),
        "total": len(results),
        "tiers": {tier: tiers.get(tier, 0) for tier in ("strict", "equivalent", "divergent")},
        "detail": dict(detail),
        "divergent": [{"scenario": name, "reason": results[name].reason}
                      for name in sorted(results) if results[name].tier == "divergent"],
        "end_tile_cases": end_cases,
        "end_tile_counts": end_counts,
        "clicks": {"old": sum(len(row["clicks"]) for row in old.values()),
                   "new": sum(len(row["clicks"]) for row in new.values())},
        "kept": {"old": sum(len(row["kept"]) for row in old.values()),
                 "new": sum(len(row["kept"]) for row in new.values())},
        "follows": {"old_reached": old_reached, "old_follows": old_follows,
                    "new_reached": new_reached, "new_follows": new_follows},
        "most_clicks": [{"scenario": row["scenario"], "clicks": len(row["clicks"])}
                        for row in by_clicks[:5] if row["clicks"]],
        "kept_cases": [{"scenario": name, "kept": len(new[name]["kept"])}
                       for name in sorted(new) if new[name]["kept"]][:20],
        "bank_cases": [{"scenario": name} for name in sorted(new) if has_bank_change(new[name])][:20],
        "zigzags": {"old": zigzag_counts(old), "new": zigzag_counts(new)},
    }


def describe(header):
    return "plugin %s%s, tooling %s%s, tier %s" % (
        header.get("pluginCommit", "?"), " (dirty)" if header.get("pluginDirty") else "",
        header.get("toolingCommit", "?"), " (dirty)" if header.get("toolingDirty") else "",
        header.get("tier", "?"))


def describe_result(result):
    if result.tier == "divergent":
        return result.reason
    parts = []
    for label in result.labels:
        if label == TRANSPORT_LABEL:
            parts.append("%s (%d legs)" % (label, result.ends["transport"]))
        elif label == TELEPORT_LABEL:
            parts.append("%s (%d legs)" % (label, result.ends["teleport"]))
        else:
            parts.append(label)
    return "; ".join(parts)


def run(args):
    old_header, old = load(args.old)
    new_header, new = load(args.new)
    if old_header.get("tier") != new_header.get("tier"):
        raise Unusable("the dumps are of different tiers: %s vs %s"
                       % (old_header.get("tier"), new_header.get("tier")))
    if old.keys() != new.keys():
        missing = sorted(old.keys() - new.keys())[:3]
        extra = sorted(new.keys() - old.keys())[:3]
        raise Unusable("the scenario sets differ (only in old: %s; only in new: %s)" % (missing, extra))

    results = {name: classify(old[name], new[name]) for name in sorted(old)}
    summary = summarise(old_header, new_header, old, new, results)

    print("old: %s" % describe(old_header))
    print("new: %s" % describe(new_header))
    tiers = summary["tiers"]
    print("%d scenarios: %d strict, %d equivalent, %d divergent"
          % (summary["total"], tiers["strict"], tiers["equivalent"], tiers["divergent"]))
    for label, count in sorted(summary["detail"].items()):
        print("  equivalent  %-52s %d" % (label, count))
    reasons = collections.Counter(entry["reason"] for entry in summary["divergent"])
    for reason, count in sorted(reasons.items()):
        print("  divergent   %-52s %d" % (reason, count))
    zig = summary["zigzags"]
    print("zigzags (two diagonals turning 90 degrees): old %d in %d scenarios, new %d in %d scenarios%s"
          % (zig["old"]["zigzags"], zig["old"]["scenarios"], zig["new"]["zigzags"], zig["new"]["scenarios"],
             "  MORE THAN OLD" if zig["new"]["zigzags"] > zig["old"]["zigzags"] else ""))
    if args.list:
        for name, result in results.items():
            if result.tier != "strict":
                print("%-10s %s: %s" % (result.tier, name, describe_result(result)))
    if args.summary:
        with open(args.summary, "w", encoding="utf-8") as handle:
            json.dump(summary, handle, indent=2)
            handle.write("\n")
    return 1 if tiers["divergent"] else 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("old", help="pathDump file of the old plugin checkout")
    parser.add_argument("new", help="pathDump file of the new plugin checkout")
    parser.add_argument("--summary", metavar="PATH", help="write a JSON summary to PATH")
    parser.add_argument("--list", action="store_true", help="print every non-strict scenario with its reason")
    args = parser.parse_args(argv)
    try:
        return run(args)
    except Unusable as error:
        print("walk_diff: %s" % error, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
