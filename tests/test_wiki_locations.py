"""Tests for ``scripts/wiki_locations.py``.

Wikitext and Bucket rows are inline excerpts of real OSRS Wiki pages — no
network access.  The script is loaded via importlib because ``scripts/`` has
no ``__init__.py``.
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "wiki_locations.py"

spec = importlib.util.spec_from_file_location("wiki_locations", SCRIPT_PATH)
wl = importlib.util.module_from_spec(spec)
sys.modules["wiki_locations"] = wl
spec.loader.exec_module(wl)

ABYSSAL_DEMON = """
==Locations==
{{LocTableHead}}
{{LocLine
|name = Abyssal demon
|location = [[Slayer Tower]] ({{FloorNumber|uk=2}})
|levels = 124
|members = Yes
|mapID = 0
|plane = 2
|x:3408,y:3573|x:3411,y:3564|x:3420,y:3569
|leagueRegion = Morytania
}}
{{LocLine
|location = [[Abyssal Area]] ({{Fairycode|alr}})<ref>Only on task.</ref>
|x:3025,y:4916|3016,4891
}}
{{LocTableBottom}}
"""

BANK_BOOTH = """{{ObjectLocLine
|name = Bank booth
|location = [[Seers' Village]]
|x : 2721,y : 3494,plane : 1|x : 2728,y : 3494,plane : 1
}}"""

HANS = "|map = {{Map|name=Hans|3212,3219|rectX=23|rectY=31|mtype=rectangle}}"
DURADEL = "|map = {{Map|x=2869|y=2982|plane=1|r=3|mtype=square|bucket=yes}}"
HALF_TILE = "|map = {{Map|x=2927.5|y=3405|mtype=pin}}"
SQUARE_POLYGON = "|map = {{Map|mtype=polygon|3200,3200|3210,3200|3210,3210|3200,3210}}"


def test_locline_groups_keep_label_plane_and_points():
    first, second = list(wl.groups(ABYSSAL_DEMON))
    assert first["kind"] == "spawns"
    assert first["shape"] == "points"
    assert first["label"] == "Slayer Tower (UK floor 2)"
    assert first["tiles"] == [[3408, 3573, 2], [3411, 3564, 2], [3420, 3569, 2]]
    assert first["point"] == [3411, 3564, 2]  # the spawn nearest the centroid
    assert first["mapID"] == "0"
    assert second["label"] == "Abyssal Area (ALR)"
    assert second["tiles"] == [[3025, 4916, 0], [3016, 4891, 0]]


def test_object_locline_per_tile_plane():
    (group,) = wl.groups(BANK_BOOTH)
    assert group["label"] == "Seers' Village"
    assert group["tiles"] == [[2721, 3494, 1], [2728, 3494, 1]]


def test_map_positional_and_named_pins():
    (hans,) = wl.groups(HANS)
    assert (hans["kind"], hans["shape"], hans["point"]) == ("map", "rectangle", [3212, 3219, 0])
    (duradel,) = wl.groups(DURADEL)
    assert (duradel["shape"], duradel["point"]) == ("square", [2869, 2982, 1])


def test_half_tile_pin_rounds():
    (group,) = wl.groups(HALF_TILE)
    assert group["point"] == [2928, 3405, 0]


def test_polygon_point_is_vertex_average():
    (group,) = wl.groups(SQUARE_POLYGON)
    assert group["shape"] == "polygon"
    assert len(group["tiles"]) == 4
    assert group["point"] == [3205, 3205, 0]


def test_map_features_tolerate_trailing_commas():
    text = '[{"geometry":{"coordinates":[[[1,2],[3,4],]],"type":"Polygon"},"properties":{"plane":1}},]'
    (feature,) = wl.features(text)
    assert list(wl.flatten(feature["geometry"]["coordinates"])) == [(1, 2), (3, 4)]
    assert wl.features("not json") == []


def test_near_keeps_nearest_article_point_per_group(monkeypatch):
    points = [
        {"page": "Man", "sub": "Man", "kind": "spawns", "x": 3221, "y": 3219, "plane": 0},
        {"page": "Man", "sub": "Man", "kind": "spawns", "x": 3225, "y": 3219, "plane": 0},
        {"page": "User:Someone/Sandbox", "sub": "x", "kind": "spawns", "x": 3222, "y": 3218, "plane": 0},
        {"page": "Rat", "sub": "Rat#Regular", "kind": "spawns", "x": 3222, "y": 3215, "plane": 0},
        {"page": "Duke Horacio", "sub": "Duke Horacio", "kind": "map", "x": 3210, "y": 3220, "plane": 1},
        {"page": "Goblin", "sub": "Goblin", "kind": "spawns", "x": 3250, "y": 3250, "plane": 0},
    ]
    monkeypatch.setattr(wl, "bucket_points", lambda: iter(points))
    result = wl.near(3222, 3218, 0, radius=5)
    assert [(r["page"], r["distance"]) for r in result] == [("Man", 1), ("Rat", 3)]
