"""Pins for ``scripts/sailing_benchmark_compare.py``.

The comparer reads the ``searches`` array of two ``sailingBenchmark`` JSON files, keys the rows by
(route, search) and exits 0 only when every deterministic field of every row is identical;
timing fields are ignored.  Files are built in ``tmp_path``.
"""

import copy
import json
import subprocess
import sys
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parent.parent / "scripts"
sys.path.insert(0, str(SCRIPTS))
import sailing_benchmark_compare as sbc  # noqa: E402


def search_row(route="Port Sarim - Mos Le'Harmless", search="sailing 1.5, sloop", **overrides):
    row = {
        "route": route,
        "search": search,
        "reached": True,
        "nodesChecked": 12345,
        "pathLength": 412,
        "pathUnit": "ticks",
        "legs": 9,
        "distanceTiles": 301.7421356237309,
        "medianMs": 12.5,
        "fastestMs": 11.9,
        "medianWallMs": 13.0,
        "runsMs": [12.5, 11.9, 13.2],
        "pathSha256": "a" * 64,
        "pathCost": 412,
        "closestTile": 123456789,
        "termination": "REACHED",
    }
    row.update(overrides)
    return row


def write(path, rows, **extra):
    document = {"searches": rows, "paths": []}
    document.update(extra)
    path.write_text(json.dumps(document))
    return str(path)


def sample_rows():
    return [
        search_row(search="existing", pathUnit="steps", legs=0),
        search_row(search="sailing 1.5, centre only"),
        search_row(route="Another route", search="sailing 1.5, sloop", pathSha256="b" * 64),
    ]


def test_identical_files_exit_0_and_print_the_row_count(tmp_path, capsys):
    old = write(tmp_path / "old.json", sample_rows())
    new = write(tmp_path / "new.json", sample_rows())
    assert sbc.main([old, new]) == 0
    assert "3" in capsys.readouterr().out


def test_timing_fields_are_ignored(tmp_path):
    slower = sample_rows()
    for row in slower:
        row.update(medianMs=99.0, fastestMs=98.0, medianWallMs=100.0, runsMs=[99.0, 98.0, 101.0])
    old = write(tmp_path / "old.json", sample_rows())
    new = write(tmp_path / "new.json", slower)
    assert sbc.main([old, new]) == 0


def test_the_paths_section_is_not_compared(tmp_path):
    old = write(tmp_path / "old.json", sample_rows(), paths=[{"route": "x", "sailingTicks": 1}])
    new = write(tmp_path / "new.json", sample_rows(), paths=[{"route": "x", "sailingTicks": 2}])
    assert sbc.main([old, new]) == 0


@pytest.mark.parametrize("field,value", [
    ("pathSha256", "c" * 64),
    ("nodesChecked", 12346),
    ("pathCost", 413),
    ("closestTile", 5),
    ("termination", "CUTOFF"),
    ("reached", False),
    ("pathLength", 413),
    ("legs", 10),
    ("distanceTiles", 301.7421356237310),
    ("pathUnit", "steps"),
])
def test_a_deterministic_field_that_differs_exits_1_and_names_the_row_and_field(tmp_path, capsys, field, value):
    changed = sample_rows()
    changed[1][field] = value
    old = write(tmp_path / "old.json", sample_rows())
    new = write(tmp_path / "new.json", changed)
    assert sbc.main([old, new]) == 1
    out = capsys.readouterr().out
    assert "sailing 1.5, centre only" in out
    assert field in out


def test_a_row_present_on_one_side_only_exits_1(tmp_path, capsys):
    old = write(tmp_path / "old.json", sample_rows())
    new = write(tmp_path / "new.json", sample_rows()[:2])
    assert sbc.main([old, new]) == 1
    assert "Another route" in capsys.readouterr().out
    assert sbc.main([new, old]) == 1


def test_a_file_without_path_hashes_exits_2(tmp_path):
    older = sample_rows()
    del older[0]["pathSha256"]
    old = write(tmp_path / "old.json", older)
    new = write(tmp_path / "new.json", sample_rows())
    assert sbc.main([old, new]) == 2
    assert sbc.main([new, old]) == 2


def test_an_empty_searches_list_exits_2(tmp_path):
    empty = write(tmp_path / "empty.json", [])
    full = write(tmp_path / "full.json", sample_rows())
    assert sbc.main([empty, full]) == 2
    assert sbc.main([full, empty]) == 2


def test_unusable_files_exit_2(tmp_path):
    full = write(tmp_path / "full.json", sample_rows())
    broken = tmp_path / "broken.json"
    broken.write_text("{not json")
    no_searches = tmp_path / "none.json"
    no_searches.write_text(json.dumps({"paths": []}))
    assert sbc.main([full, str(broken)]) == 2
    assert sbc.main([full, str(no_searches)]) == 2
    assert sbc.main([full, str(tmp_path / "missing.json")]) == 2


def test_a_duplicate_row_exits_2(tmp_path):
    rows = sample_rows() + [copy.deepcopy(sample_rows()[0])]
    old = write(tmp_path / "old.json", rows)
    new = write(tmp_path / "new.json", sample_rows())
    assert sbc.main([old, new]) == 2


def test_the_script_runs_as_a_program(tmp_path):
    old = write(tmp_path / "old.json", sample_rows())
    changed = sample_rows()
    changed[2]["pathCost"] = 1
    new = write(tmp_path / "new.json", changed)
    script = str(SCRIPTS / "sailing_benchmark_compare.py")
    assert subprocess.run([sys.executable, script, old, old], capture_output=True).returncode == 0
    assert subprocess.run([sys.executable, script, old, new], capture_output=True).returncode == 1
    assert subprocess.run([sys.executable, script, old, "missing.json"], capture_output=True).returncode == 2
