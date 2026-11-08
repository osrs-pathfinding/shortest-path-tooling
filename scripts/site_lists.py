#!/usr/bin/env python3
"""Regenerate the Forestry call location and shooting star site lists.

Both are places players are sent to that the plugin should be able to route to. Each list is read
from a RuneLite plugin that keeps it, at a pinned commit, and written one site per line in the
clue list's shape (id, name, category, target, targetSource), so the workbench checks them the
same way:

- Forestry: peanubnutter/scoutingplugin CallLocation.java (BSD-2-Clause, Copyright (c) 2023,
  esparano), synced from the loghunters Discord bot's predefined locations. The tile is the call's
  centre; an ent can be anywhere within 40 tiles of it.
- Shooting stars: zodaz/star-calling-assist StarLocationDetails.java (BSD-2-Clause, Copyright (c)
  2022, zodaz). The tile is the south-west corner of the 2x2 star.

All sites are on floor 0. Bump a pin to refresh a list:

    python3 scripts/site_lists.py
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "scenarios/src/main/resources/locations"

SOURCES = {
    "forestry": {
        "repo": "peanubnutter/scoutingplugin",
        "commit": "c8c1f716e9ba5425f4fb9610dcd9a3e174d9b773",
        "path": "src/main/java/com/scouting/pathfinder/CallLocation.java",
        "out": "forestry-call-locations.json",
    },
    "star": {
        "repo": "zodaz/star-calling-assist",
        "commit": "a156da016b19d6a9df7cf69dce2222f1bd717de8",
        "path": "src/main/java/com/starcallingassist/enums/StarLocationDetails.java",
        "out": "shooting-star-sites.json",
    },
}

# ARCEUUS("Arceuus", 1683, 3746, "Book of the Dead 5"), or with a radius before the teleport text.
FORESTRY = re.compile(r'^\s*([A-Z][A-Z0-9_]*)\("([^"]+)",\s*(\d+),\s*(\d+),', re.M)
# RIMMINGTON_MINE("Rimmington mine", Region.ASGARNIA, new Point(2974, 3241), null,
STAR = re.compile(r'^\s*([A-Z][A-Z0-9_]*)\("([^"]+)",\s*Region\.(\w+),\s*new Point\((\d+),\s*(\d+)\)', re.M)


def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def forestry_sites(java, source):
    return [{"id": f"forestry-{slug(constant)}", "name": name, "category": "forestry",
             "target": [int(x), int(y), 0], "targetSource": f"{source} {constant}"}
            for constant, name, x, y in FORESTRY.findall(java)]


def star_sites(java, source):
    return [{"id": f"star-{slug(constant)}", "name": name, "category": f"star {region.lower()}",
             "target": [int(x), int(y), 0], "targetSource": f"{source} {constant}"}
            for constant, name, region, x, y in STAR.findall(java)]


def render(sites):
    """One site per line, so a refresh diffs by site."""
    return "[\n" + ",\n".join("  " + json.dumps(site) for site in sites) + "\n]\n"


def fetch(source):
    url = f"https://raw.githubusercontent.com/{source['repo']}/{source['commit']}/{source['path']}"
    with urllib.request.urlopen(url, timeout=30) as response:
        return response.read().decode("utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for kind, parse in (("forestry", forestry_sites), ("star", star_sites)):
        source = SOURCES[kind]
        label = f"{source['repo']}@{source['commit'][:8]} {Path(source['path']).name}"
        sites = parse(fetch(source), label)
        if not sites:
            sys.exit(f"no {kind} sites parsed from {label}")
        ids = [site["id"] for site in sites]
        if len(ids) != len(set(ids)):
            sys.exit(f"duplicate {kind} ids from {label}")
        (OUT / source["out"]).write_text(render(sites), encoding="utf-8")
        print(f"{kind}: {len(sites)} sites -> {(OUT / source['out']).relative_to(REPO)}")


if __name__ == "__main__":
    main()
