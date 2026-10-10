#!/usr/bin/env python3
"""Look up OSRS locations on the OSRS Wiki: where a monster, NPC, object or
place is, or what the wiki puts near a tile.

The method and its pitfalls are in ``docs/wiki-locations.md``.  Output is
JSON on stdout.

Subcommands:
    page TITLE...           location groups on wiki pages, read from page
                            source: ``{{LocLine}}`` / ``{{ObjectLocLine}}``
                            spawn lists (labelled) and ``{{Map}}`` pins
    search TEXT             wiki page titles matching TEXT (full-text search)
    near X Y PLANE          wiki spawns and bucketed map pins within
                            ``--radius`` tiles, nearest first (Bucket API;
                            downloads ~4 MB, cached for a day)

Standard library only.  Requests are throttled and carry a descriptive
User-Agent, as the wiki asks.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Dict, Iterator, List, Optional

API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "shortest-path-tooling wiki_locations.py (github.com/Skretzo/shortest-path)"
THROTTLE_SECONDS = 0.3
BATCH = 50
CACHE_TTL_SECONDS = 24 * 3600

_last_request = 0.0


def api(**params) -> dict:
    """GET api.php with JSON format 2, throttled, retrying 429/5xx."""
    global _last_request
    query = urllib.parse.urlencode({"format": "json", "formatversion": "2", **params})
    request = urllib.request.Request(f"{API}?{query}", headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        wait = _last_request + THROTTLE_SECONDS - time.monotonic()
        if wait > 0:
            time.sleep(wait)
        _last_request = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                body = json.load(response)
        except urllib.error.HTTPError as error:
            if error.code == 429 or error.code >= 500:
                time.sleep(2 * (attempt + 1))
                continue
            raise
        if "error" in body:
            raise RuntimeError(f"wiki API error: {body['error']}")
        return body
    raise RuntimeError("wiki API kept failing; try again later")


def bucket(query: str) -> List[dict]:
    """Run one Bucket query, e.g. ``bucket('locline').select(...).run()``."""
    return api(action="bucket", query=query)["bucket"]


def bucket_all(base: str, page_size: int = 5000) -> List[dict]:
    """All rows of ``base`` (a query without limit/offset/run), paged."""
    rows: List[dict] = []
    while True:
        page = bucket(f"{base}.limit({page_size}).offset({len(rows)}).run()")
        rows += page
        if len(page) < page_size:
            return rows


# ---------------------------------------------------------------- wikitext


def templates(text: str, names: List[str]) -> Iterator[str]:
    """Bodies (after the first ``|``) of ``{{Name|...}}``, nested braces handled."""
    for match in re.finditer(r"\{\{\s*(%s)\s*\|" % "|".join(names), text, re.IGNORECASE):
        depth, i = 0, match.start()
        while i < len(text):
            if text.startswith("{{", i):
                depth += 1
                i += 2
            elif text.startswith("}}", i):
                depth -= 1
                i += 2
                if depth == 0:
                    yield text[match.end():i - 2]
                    break
            else:
                i += 1


def split_params(body: str) -> List[str]:
    """Split a template body on ``|`` outside nested templates and links."""
    parts, current, depth = [], "", 0
    for ch in body:
        depth += (ch in "{[") - (ch in "}]")
        if ch == "|" and depth == 0:
            parts.append(current.strip())
            current = ""
        else:
            current += ch
    return parts + [current.strip()]


def plain(text: str) -> str:
    """Wikitext to a readable label: links, refs and templates resolved or dropped."""
    text = re.sub(r"<ref[^>]*?(/>|>.*?</ref>)", "", text, flags=re.DOTALL)
    text = re.sub(r"\{\{\s*FloorNumber\s*\|\s*uk\s*=\s*(\d)\s*\}\}", r"UK floor \1", text, flags=re.I)
    text = re.sub(r"\{\{\s*Fairycode\s*\|\s*(\w+)\s*\}\}", lambda m: m.group(1).upper(), text, flags=re.I)
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\{\{[^{}]*\}\}", "", text)
    text = re.sub(r"<[^>]+>", "", text)
    return re.sub(r"\s*\(\s*\)", "", text).strip()


# "3212,3219", "x:3212,y:3219", "x : 3212,y : 3219,plane : 1", "2927.5,3405"
COORD = re.compile(
    r"(?:x\s*:\s*)?(\d{3,5}(?:\.\d+)?)\s*,\s*(?:y\s*:\s*)?(\d{3,5}(?:\.\d+)?)(?:.*?plane\s*:\s*(\d))?")
NAMED = re.compile(r"\s*([A-Za-z_]+\d*)\s*")


def centre(tiles: List[List[int]]) -> Optional[List[int]]:
    """The given tile nearest the centroid (a polygon's vertex average is rounded instead)."""
    if not tiles:
        return None
    cx = sum(t[0] for t in tiles) / len(tiles)
    cy = sum(t[1] for t in tiles) / len(tiles)
    return min(tiles, key=lambda t: (t[0] - cx) ** 2 + (t[1] - cy) ** 2)


def groups(text: str) -> Iterator[dict]:
    """One group per ``{{LocLine}}``/``{{ObjectLocLine}}`` (spawns) and per ``{{Map}}`` (pin or shape)."""
    for kind, names in (("spawns", ["LocLine", "ObjectLocLine"]), ("map", ["Map"])):
        for body in templates(text, names):
            named: Dict[str, str] = {}
            raw = []
            for param in split_params(body):
                key, eq, value = param.partition("=")
                if eq and NAMED.fullmatch(key):
                    named[key.strip().lower()] = value.strip()
                elif not eq:
                    match = COORD.search(param)
                    if match:
                        raw.append(match.groups())
            if "x" in named and "y" in named:
                raw.append((named["x"], named["y"], None))
            plane = int(named.get("plane") or 0)
            tiles = [[round(float(x)), round(float(y)), int(p) if p else plane] for x, y, p in raw]
            shape = "points" if kind == "spawns" else (named.get("mtype") or "pin")
            point = tiles[0] if len(tiles) == 1 else None
            if shape == "polygon":
                point = [round(sum(t[0] for t in tiles) / len(tiles)),
                         round(sum(t[1] for t in tiles) / len(tiles)), plane] if tiles else None
            elif point is None:
                point = centre(tiles)
            yield {
                "kind": kind,
                "label": plain(named.get("location") or named.get("name") or ""),
                "shape": shape,
                "mapID": named.get("mapid"),
                "members": named.get("members"),
                "point": point,
                "tiles": tiles,
            }


def page_locations(titles: List[str]) -> List[dict]:
    """Location groups for each title, with the revision id for provenance."""
    out = []
    for start in range(0, len(titles), BATCH):
        batch = titles[start:start + BATCH]
        result = api(action="query", prop="revisions", rvprop="ids|content", rvslots="main",
                     redirects=1, titles="|".join(batch))["query"]
        for page in result["pages"]:
            if page.get("missing") or page.get("invalid"):
                out.append({"page": page["title"], "missing": True})
                continue
            revision = page["revisions"][0]
            text = revision["slots"]["main"]["content"]
            found = list(groups(text))
            anchor = page["title"].replace(" ", "_")
            for group in found:
                section = "Locations" if group["kind"] == "spawns" else "map"
                group["source"] = f"oldschool-wiki:{anchor}@{revision['revid']}#{section}"
            entry = {"page": page["title"], "revid": revision["revid"], "groups": found}
            if not found:
                infobox_location = re.search(r"^\|\s*location\d*\s*=\s*(.+)$", text, re.MULTILINE)
                entry["hint"] = ("no coordinates on this page"
                                 + (f"; its infobox location is {plain(infobox_location.group(1))!r},"
                                    " look that page up" if infobox_location
                                    else "; search for the area it is in (instances have no pin)"))
            out.append(entry)
    return out


# ---------------------------------------------------------------- near


def cache_dir() -> Path:
    base = os.environ.get("XDG_CACHE_HOME") or os.path.join(Path.home(), ".cache")
    return Path(base) / "osrs-wiki-locations"


def cached(name: str, fetch) -> list:
    path = cache_dir() / name
    if path.exists() and time.time() - path.stat().st_mtime < CACHE_TTL_SECONDS:
        return json.loads(path.read_text())
    rows = fetch()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(rows))
    return rows


def bucket_points() -> Iterator[dict]:
    """Every bucketed spawn tile and map-pin vertex as {page, sub, x, y, plane, source}."""
    loclines = cached("locline.json", lambda: bucket_all(
        "bucket('locline').select('page_name','page_name_sub','plane','coordinates')"
        ".orderBy('page_name','asc')"))
    for row in loclines:
        for value in row.get("coordinates") or []:
            match = COORD.search(value.replace("npcid:", "npcid "))
            if match:
                x, y, p = match.groups()
                yield {"page": row["page_name"], "sub": row.get("page_name_sub"), "kind": "spawns",
                       "x": round(float(x)), "y": round(float(y)),
                       "plane": int(p) if p else int(row.get("plane") or 0)}
    maps = cached("map.json", lambda: bucket_all(
        "bucket('map').select('page_name','page_name_sub','features').orderBy('page_name','asc')"))
    for row in maps:
        for feature in features(row.get("features")):
            plane = int((feature.get("properties") or {}).get("plane") or 0)
            coordinates = (feature.get("geometry") or {}).get("coordinates")
            for x, y in flatten(coordinates):
                yield {"page": row["page_name"], "sub": row.get("page_name_sub"), "kind": "map",
                       "x": round(x), "y": round(y), "plane": plane}


def features(text: Optional[str]) -> list:
    """GeoJSON features of a ``map`` row; some stored values have trailing commas."""
    try:
        return json.loads(re.sub(r",\s*([\]}])", r"\1", text or "[]"))
    except ValueError:
        return []


def flatten(coordinates) -> Iterator[tuple]:
    if not coordinates:
        return
    if isinstance(coordinates[0], (int, float)):
        yield coordinates[0], coordinates[1]
        return
    for inner in coordinates:
        yield from flatten(inner)


def near(x: int, y: int, plane: int, radius: int) -> List[dict]:
    """Nearest bucketed point per (page, sub, kind) within ``radius`` (Chebyshev), nearest first."""
    best: Dict[tuple, dict] = {}
    for point in bucket_points():
        # User sandboxes and other namespaces copy real data; keep articles only.
        if point["plane"] != plane or ":" in point["page"]:
            continue
        distance = max(abs(point["x"] - x), abs(point["y"] - y))
        if distance > radius:
            continue
        key = (point["page"], point["sub"], point["kind"])
        if key not in best or distance < best[key]["distance"]:
            best[key] = {**point, "distance": distance}
    return sorted(best.values(), key=lambda p: (p["distance"], p["page"]))


# ---------------------------------------------------------------- main


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    sub = parser.add_subparsers(dest="command", required=True)
    page = sub.add_parser("page", help="location groups on wiki pages")
    page.add_argument("titles", nargs="+")
    search = sub.add_parser("search", help="wiki page titles matching text")
    search.add_argument("text")
    search.add_argument("--limit", type=int, default=10)
    around = sub.add_parser("near", help="bucketed wiki locations near a tile")
    around.add_argument("x", type=int)
    around.add_argument("y", type=int)
    around.add_argument("plane", type=int)
    around.add_argument("--radius", type=int, default=10)
    args = parser.parse_args(argv)

    if args.command == "page":
        result = page_locations(args.titles)
    elif args.command == "search":
        hits = api(action="query", list="search", srsearch=args.text, srlimit=args.limit)
        result = [hit["title"] for hit in hits["query"]["search"]]
    else:
        result = near(args.x, args.y, args.plane, args.radius)
    json.dump(result, sys.stdout, indent=1)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
