"""Pins for ``scripts/walk_diff.py``: the D-13 tiers over two ``pathDump`` files.

Rows are built in ``tmp_path`` with small helpers: a header row, walking steps and one
transport step with its transport record, in the schema ``PathDump`` writes.  Every tier, every
divergent kind and every CLI exit code has its own test, because later plans accept or reject
walk changes on this script's exit status.
"""

import copy
import json
import subprocess
import sys
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parent.parent / "scripts"
sys.path.insert(0, str(SCRIPTS))
import walk_diff as wd  # noqa: E402

UNDEFINED = -1


def tile(x, y, plane=0):
    return x | (y << 15) | (plane << 30)


def step(x, y, bank=0, flag=0, plane=0):
    return [tile(x, y, plane), bank, flag]


def header(tier="smoke", plugin="a" * 40):
    return {"header": {"suite": "canonical", "tier": tier, "pluginCommit": plugin, "pluginDirty": False,
                       "toolingCommit": "b" * 40, "toolingDirty": False}}


def record(index, origin, destination, kind="AGILITY_SHORTCUT", duration=2, display=None, obj="Gate",
           usable=None, castable=None):
    return {"index": index, "origin": origin, "destination": destination, "type": kind, "duration": duration,
            "displayInfo": display, "objectInfo": obj, "usableFromPrevious": usable,
            "castableFromPrevious": castable}


def row(name="r1/early", steps=None, transports=(), clicks=(), kept=(), reached=True, cost=10,
        termination="REACHED", follows=True):
    return {"scenario": name, "reached": reached, "cost": cost if reached else None,
            "termination": termination, "steps": steps if steps is not None else [],
            "transports": list(transports), "clicks": list(clicks), "kept": list(kept), "follows": follows}


def straight(end_x=3203):
    return [step(x, 3200) for x in range(3200, end_x + 1)]


def with_overrides(base, overrides):
    overrides = dict(overrides)
    if "name" in overrides:
        base["scenario"] = overrides.pop("name")
    base.update(overrides)
    return base


def walking_row(**overrides):
    base = row(steps=straight(), clicks=[3])
    return with_overrides(base, overrides)


def transport_row(end=(3203, 3200), usable=True, destination=tile(3300, 3300), kind="AGILITY_SHORTCUT",
                  clicks=None, tail=True, **overrides):
    """Walk four tiles to ``end``, take a local transport to ``destination``, walk two more tiles.

    ``tail=False`` ends the route on the transport step, which then starts no leg.
    """
    steps = [step(3200, 3200), step(3201, 3200), step(3202, 3200), step(end[0], end[1]),
             [destination, 0, 1]]
    if tail:
        steps += [step(3301, 3300), step(3302, 3300)]
    if clicks is None:
        clicks = (3, 6) if tail else (3,)
    base = row(steps=steps, clicks=clicks, transports=[
        record(4, tile(*end), destination, kind=kind, usable=usable)])
    return with_overrides(base, overrides)


def teleport_row(end=(3203, 3200), castable=True, clicks=(3, 6), **overrides):
    steps = [step(3200, 3200), step(3201, 3200), step(3202, 3200), step(end[0], end[1]),
             [tile(3212, 3424), 0, 1], step(3213, 3424), step(3214, 3424)]
    base = row(steps=steps, clicks=clicks, transports=[
        record(4, UNDEFINED, tile(3212, 3424), kind="TELEPORTATION_SPELL", display="Varrock Teleport",
               obj=None, castable=castable)])
    return with_overrides(base, overrides)


def bank_row(end=(3203, 3200), clicks=(3, 5)):
    """Walk to ``end``, change bank state in place, walk one more tile."""
    steps = [step(3200, 3200), step(3201, 3200), step(3202, 3200), step(end[0], end[1]),
             step(3203, 3200, bank=1), step(3204, 3200, bank=1)]
    return row(steps=steps, clicks=clicks)


def write(path, rows, head=None):
    lines = [json.dumps(head if head is not None else header())] + [json.dumps(r) for r in rows]
    path.write_text("\n".join(lines) + "\n")
    return str(path)


def classify(old, new):
    return wd.classify(old, new)


def tier_of(old, new):
    return classify(old, new).tier


# ---------------------------------------------------------------- strict and equivalent


def test_identical_rows_are_strict():
    result = classify(transport_row(), transport_row())
    assert result.tier == "strict"
    assert result.labels == []


def test_interior_tiles_and_clicks_only_is_equivalent():
    old = walking_row()
    new = walking_row()
    new["steps"][1] = step(3201, 3201)
    new["clicks"] = [2, 3]
    result = classify(old, new)
    assert result.tier == "equivalent"
    assert result.labels == ["interior tiles/clicks only"]


def test_equal_steps_with_other_clicks_is_equivalent():
    result = classify(walking_row(clicks=[3]), walking_row(clicks=[1, 3]))
    assert result.tier == "equivalent"
    assert result.labels == ["interior tiles/clicks only"]


def test_leg_end_before_a_transport_with_a_usable_origin_is_equivalent():
    old = transport_row()
    new = transport_row(end=(3203, 3201))
    result = classify(old, new)
    assert result.tier == "equivalent"
    assert result.labels == ["leg end tile differs before a transport"]
    assert result.ends == {"transport": 1, "teleport": 0}


def test_leg_end_before_a_global_teleport_that_can_be_cast_is_equivalent():
    old = teleport_row()
    new = teleport_row(end=(3203, 3201), castable=True)
    result = classify(old, new)
    assert result.tier == "equivalent"
    assert result.labels == ["leg end tile differs before a global teleport"]
    assert result.ends == {"transport": 0, "teleport": 1}


def test_a_row_with_both_end_kinds_reports_both():
    def both(end_a, end_b, tele_end):
        steps = [step(3200, 3200), step(3201, 3200), step(end_a[0], end_a[1]),
                 [tile(3300, 3300), 0, 1], step(3301, 3300), step(end_b[0], end_b[1]),
                 [tile(3212, 3424), 0, 1], step(3213, 3424)]
        transports = [record(3, tile(*end_a), tile(3300, 3300), usable=True),
                      record(6, UNDEFINED, tile(3212, 3424), kind="TELEPORTATION_SPELL", display="Varrock",
                             obj=None, castable=True)]
        return row(steps=steps, transports=transports, clicks=[2, 5, 7])

    old = both((3202, 3200), (3302, 3300), None)
    new = both((3202, 3201), (3302, 3301), None)
    result = classify(old, new)
    assert result.tier == "equivalent"
    assert result.ends == {"transport": 1, "teleport": 1}
    assert result.labels == ["leg end tile differs before a transport",
                             "leg end tile differs before a global teleport"]


def test_a_kept_leg_end_satisfies_the_structural_check():
    old = transport_row(clicks=(6,), kept=(3,))
    new = transport_row(end=(3203, 3201), clicks=(6,), kept=(3,))
    assert tier_of(old, new) == "equivalent"


# ---------------------------------------------------------------- divergent: verdicts


def test_reached_differs_is_divergent():
    result = classify(walking_row(), walking_row(reached=False, termination="REACHED"))
    assert (result.tier, result.reason) == ("divergent", "reached differs")


def test_cost_differs_is_divergent():
    result = classify(walking_row(cost=10), walking_row(cost=11))
    assert (result.tier, result.reason) == ("divergent", "cost differs")


def test_termination_differs_is_divergent():
    result = classify(walking_row(), walking_row(termination="CUTOFF"))
    assert (result.tier, result.reason) == ("divergent", "termination differs")


def test_path_length_differs_is_divergent():
    result = classify(walking_row(), walking_row(steps=straight(3204), clicks=[4]))
    assert (result.tier, result.reason) == ("divergent", "path length differs")


# ---------------------------------------------------------------- divergent: structure


def test_a_non_walking_step_that_differs_is_divergent():
    old = transport_row(tail=False)
    new = transport_row(tail=False, destination=tile(3400, 3400))
    result = classify(old, new)
    assert (result.tier, result.reason) == ("divergent", "non-walking step differs")


def test_a_transport_destination_that_starts_a_leg_differs_as_a_leg_start():
    new = transport_row(destination=tile(3400, 3400))
    new["steps"][5] = step(3401, 3400)
    new["steps"][6] = step(3402, 3400)
    result = classify(transport_row(), new)
    assert (result.tier, result.reason) == ("divergent", "leg start differs")


def test_a_transport_record_that_differs_is_divergent():
    result = classify(transport_row(), transport_row(kind="SPIRIT_TREE"))
    assert (result.tier, result.reason) == ("divergent", "non-walking step differs")


def test_a_transport_origin_that_differs_with_the_leg_end_unchanged_is_divergent():
    new = transport_row()
    new["transports"][0]["origin"] = tile(3199, 3200)
    result = classify(transport_row(), new)
    assert (result.tier, result.reason) == ("divergent", "non-walking step differs")


def test_leg_boundaries_that_differ_are_divergent():
    new = walking_row()
    new["steps"][2] = step(3210, 3200)
    result = classify(walking_row(), new)
    assert (result.tier, result.reason) == ("divergent", "leg boundaries differ")


def test_a_leg_start_that_differs_is_divergent():
    new = transport_row()
    new["steps"][0] = step(3200, 3201)
    result = classify(transport_row(), new)
    assert (result.tier, result.reason) == ("divergent", "leg start differs")


def test_the_last_leg_end_that_differs_is_divergent():
    old = walking_row()
    new = walking_row()
    new["steps"][3] = step(3203, 3201)
    result = classify(old, new)
    assert (result.tier, result.reason) == ("divergent", "the route's last leg end differs")


def test_a_leg_end_before_a_bank_state_change_that_differs_is_divergent():
    result = classify(bank_row(), bank_row(end=(3203, 3201)))
    assert (result.tier, result.reason) == ("divergent", "leg end before a bank-state change differs")


def test_a_changed_end_before_a_different_transport_is_divergent():
    old = transport_row(tail=False)
    new = transport_row(tail=False, end=(3203, 3201), destination=tile(3400, 3400))
    result = classify(old, new)
    assert (result.tier, result.reason) == ("divergent", "next action after a changed leg end differs")


def test_a_changed_end_before_a_transport_of_another_type_is_divergent():
    result = classify(transport_row(), transport_row(end=(3203, 3201), kind="SPIRIT_TREE"))
    assert (result.tier, result.reason) == ("divergent", "next action after a changed leg end differs")


def test_a_changed_end_before_a_step_without_a_transport_is_divergent():
    def jump(end):
        steps = [step(3200, 3200), step(3201, 3200), step(3202, 3200), step(end[0], end[1]),
                 step(3300, 3300), step(3301, 3300)]
        return row(steps=steps, clicks=[3, 5])

    result = classify(jump((3203, 3200)), jump((3203, 3201)))
    assert (result.tier, result.reason) == ("divergent", "next action after a changed leg end differs")


# ---------------------------------------------------------------- divergent: the new end serves the next action


def test_a_leg_end_where_the_local_transport_cannot_be_used_is_divergent():
    result = classify(transport_row(), transport_row(end=(3203, 3201), usable=False))
    assert result.tier == "divergent"
    assert result.reason == "new leg end cannot use the next transport (usableFromPrevious false or missing)"


def test_a_missing_usable_flag_is_divergent():
    result = classify(transport_row(), transport_row(end=(3203, 3201), usable=None))
    assert result.tier == "divergent"
    assert result.reason == "new leg end cannot use the next transport (usableFromPrevious false or missing)"


def test_a_leg_end_where_the_global_teleport_cannot_be_cast_is_divergent():
    result = classify(teleport_row(), teleport_row(end=(3203, 3201), castable=False))
    assert result.tier == "divergent"
    assert result.reason == "new leg end cannot cast the next global teleport (castableFromPrevious false or missing)"


def test_a_missing_castable_flag_is_divergent():
    result = classify(teleport_row(), teleport_row(end=(3203, 3201), castable=None))
    assert result.tier == "divergent"
    assert result.reason == "new leg end cannot cast the next global teleport (castableFromPrevious false or missing)"


def test_an_unchanged_leg_end_needs_no_proof():
    assert tier_of(teleport_row(castable=False), teleport_row(castable=False)) == "strict"


# ---------------------------------------------------------------- divergent: new structural checks


def test_new_follows_false_on_a_reached_row_is_divergent():
    result = classify(walking_row(), walking_row(follows=False))
    assert (result.tier, result.reason) == ("divergent", "new follows is not true on a reached row")


def test_new_follows_missing_on_a_reached_row_is_divergent():
    result = classify(walking_row(), walking_row(follows=None))
    assert (result.tier, result.reason) == ("divergent", "new follows is not true on a reached row")


def test_follows_is_not_required_on_an_unreached_row():
    unreached = row(steps=[], reached=False, termination="NO_PATH", follows=None)
    assert tier_of(unreached, copy.deepcopy(unreached)) == "strict"


def test_a_new_leg_end_that_is_neither_click_nor_kept_is_divergent():
    result = classify(walking_row(), walking_row(clicks=[2]))
    assert (result.tier, result.reason) == ("divergent", "a new leg end is neither a click nor a kept step")


# ---------------------------------------------------------------- command line


def run(tmp_path, old_rows, new_rows, *extra, old_head=None, new_head=None):
    old = write(tmp_path / "old.jsonl", old_rows, old_head)
    new = write(tmp_path / "new.jsonl", new_rows, new_head)
    return wd.main([old, new, *extra])


def test_exit_0_when_nothing_is_divergent(tmp_path, capsys):
    rows = [walking_row(name="a/early"), transport_row(name="b/early")]
    assert run(tmp_path, rows, copy.deepcopy(rows)) == 0
    out = capsys.readouterr().out
    assert "strict" in out
    assert "a" * 40 in out


def test_exit_0_with_equivalent_rows(tmp_path):
    assert run(tmp_path, [transport_row()], [transport_row(end=(3203, 3201))]) == 0


def test_exit_1_with_a_divergent_row(tmp_path, capsys):
    old = [walking_row(name="a/early"), walking_row(name="b/early")]
    new = [walking_row(name="a/early"), walking_row(name="b/early", cost=99)]
    assert run(tmp_path, old, new, "--list") == 1
    out = capsys.readouterr().out
    assert "b/early" in out
    assert "cost differs" in out


def test_exit_2_when_the_scenario_sets_differ(tmp_path):
    assert run(tmp_path, [walking_row(name="a/early")], [walking_row(name="b/early")]) == 2


def test_exit_2_when_a_dump_has_no_rows(tmp_path):
    assert run(tmp_path, [], []) == 2


def test_exit_2_when_the_header_tiers_differ(tmp_path):
    rows = [walking_row()]
    assert run(tmp_path, rows, copy.deepcopy(rows), old_head=header("smoke"), new_head=header("standard")) == 2


def test_exit_2_without_a_header(tmp_path):
    old = tmp_path / "old.jsonl"
    old.write_text(json.dumps(walking_row()) + "\n")
    new = write(tmp_path / "new.jsonl", [walking_row()])
    assert wd.main([str(old), new]) == 2


def test_exit_2_on_a_line_that_is_not_json(tmp_path):
    old = write(tmp_path / "old.jsonl", [walking_row()])
    new = tmp_path / "new.jsonl"
    new.write_text(json.dumps(header()) + "\n" + "{not json\n")
    assert wd.main([old, str(new)]) == 2


def test_exit_2_on_a_row_missing_its_steps(tmp_path):
    broken = walking_row()
    del broken["steps"]
    old = write(tmp_path / "old.jsonl", [walking_row()])
    new = write(tmp_path / "new.jsonl", [broken])
    assert wd.main([old, new]) == 2


def test_exit_2_on_a_duplicate_scenario(tmp_path):
    rows = [walking_row(), walking_row()]
    assert run(tmp_path, rows, rows) == 2


def test_exit_2_when_a_file_is_missing(tmp_path):
    assert wd.main([str(tmp_path / "nope.jsonl"), str(tmp_path / "nope2.jsonl")]) == 2


def test_summary_json_describes_the_comparison(tmp_path):
    old = [walking_row(name="a/early"), transport_row(name="b/early"), teleport_row(name="c/early"),
           walking_row(name="d/early"), bank_row()]
    old[-1]["scenario"] = "e/early"
    new = copy.deepcopy(old)
    new[1] = transport_row(name="b/early", end=(3203, 3201))
    new[2] = teleport_row(name="c/early", end=(3203, 3201), castable=True)
    new[3]["cost"] = 11
    summary = tmp_path / "summary.json"
    assert run(tmp_path, old, new, "--summary", str(summary)) == 1
    data = json.loads(summary.read_text())
    assert data["total"] == data["old_count"] == data["new_count"] == 5
    assert data["tiers"] == {"strict": 2, "equivalent": 2, "divergent": 1}
    assert data["divergent"] == [{"scenario": "d/early", "reason": "cost differs"}]
    assert data["old"]["pluginCommit"] == "a" * 40
    assert data["new"]["tier"] == "smoke"
    assert data["detail"]["leg end tile differs before a transport"] == 1
    assert data["detail"]["leg end tile differs before a global teleport"] == 1
    assert sorted(data["end_tile_cases"], key=lambda c: c["scenario"]) == [
        {"scenario": "b/early", "kind": "transport"}, {"scenario": "c/early", "kind": "teleport"}]
    assert data["end_tile_counts"] == {"transport": 1, "teleport": 1}
    assert data["clicks"] == {"old": 1 + 2 + 2 + 1 + 2, "new": 1 + 2 + 2 + 1 + 2}
    assert data["kept"] == {"old": 0, "new": 0}
    assert data["follows"] == {"old_reached": 5, "old_follows": 5, "new_reached": 5, "new_follows": 5}
    assert data["most_clicks"][0]["clicks"] == 2
    assert len(data["most_clicks"]) <= 5
    assert [case["scenario"] for case in data["bank_cases"]] == ["e/early"]
    assert data["kept_cases"] == []


def test_the_script_runs_as_a_program_and_exits_with_the_verdict(tmp_path):
    old = write(tmp_path / "old.jsonl", [walking_row()])
    new = write(tmp_path / "new.jsonl", [walking_row(cost=12)])
    script = str(SCRIPTS / "walk_diff.py")
    divergent = subprocess.run([sys.executable, script, old, new], capture_output=True, text=True)
    assert divergent.returncode == 1
    same = subprocess.run([sys.executable, script, old, old], capture_output=True, text=True)
    assert same.returncode == 0
    unusable = subprocess.run([sys.executable, script, old, str(tmp_path / "missing.jsonl")],
                              capture_output=True, text=True)
    assert unusable.returncode == 2


def dogleg_row(name="z/early"):
    """North-east then north-west into a click, then straight north: one zigzag at the click."""
    steps = [step(3200, 3200), step(3200, 3201), step(3201, 3202), step(3200, 3203), step(3200, 3204)]
    return row(name=name, steps=steps, clicks=[2, 4])


@pytest.mark.parametrize("second, zigzag", [((3200, 3202), True), ((3202, 3200), True), ((3200, 3200), False)],
                         ids=["NE-NW", "NE-SE", "NE-SW"])
def test_a_zigzag_is_two_diagonals_turning_90_degrees(second, zigzag):
    # NE then NW and NE then SE turn 90 degrees; NE then SW reverses (180 degrees) and is not one.
    steps = [step(3200, 3200), step(3201, 3201), step(*second)]
    assert wd.zigzags(row(steps=steps, clicks=[2])) == ([(1, "interior")] if zigzag else [])


def test_straight_and_cardinal_turns_are_no_zigzag():
    steps = [step(3200, 3200), step(3201, 3201), step(3202, 3202), step(3202, 3203), step(3203, 3203),
             step(3204, 3202)]
    assert wd.zigzags(row(steps=steps, clicks=[5])) == []


def test_a_zigzag_names_the_role_of_its_middle_tile():
    assert wd.zigzags(dogleg_row()) == [(2, "click")]
    kept = dogleg_row()
    kept["clicks"], kept["kept"] = [4], [2]
    assert wd.zigzags(kept) == [(2, "kept")]


def test_moves_across_a_transport_or_bank_change_are_no_zigzag():
    across = [step(3200, 3200), step(3201, 3201), step(3200, 3202, flag=1)]
    banked = [step(3200, 3200), step(3201, 3201), step(3200, 3202, bank=1)]
    assert wd.zigzags(row(steps=across)) == []
    assert wd.zigzags(row(steps=banked)) == []


def test_zigzags_are_counted_per_side_and_reported_without_changing_the_verdict(tmp_path, capsys):
    straight_north = [step(3200, 3200 + k) for k in range(5)]
    old = [row(name="z/early", steps=straight_north, clicks=[2, 4]), walking_row(name="a/early")]
    new = [dogleg_row(), walking_row(name="a/early")]
    summary = tmp_path / "summary.json"
    assert run(tmp_path, old, new, "--summary", str(summary)) == 0
    out = capsys.readouterr().out
    assert "zigzags (two diagonals turning 90 degrees): old 0 in 0 scenarios, new 1 in 1 scenarios  MORE THAN OLD" in out
    data = json.loads(summary.read_text())
    assert data["tiers"] == {"strict": 1, "equivalent": 1, "divergent": 0}
    assert data["zigzags"]["old"] == {"zigzags": 0, "scenarios": 0, "roles": {}, "cases": []}
    assert data["zigzags"]["new"] == {"zigzags": 1, "scenarios": 1, "roles": {"click": 1},
                                      "cases": [{"scenario": "z/early", "index": 2, "role": "click"}]}


def test_legs_are_maximal_walking_runs():
    steps = transport_row()["steps"]
    assert wd.legs(steps) == [(0, 3), (4, 6)]


def test_a_step_on_another_plane_is_not_a_walking_move():
    assert not wd.is_walk(step(3200, 3200), step(3201, 3200, plane=1))
    assert not wd.is_walk(step(3200, 3200), step(3202, 3200))
    assert wd.is_walk(step(3200, 3200), step(3201, 3201))
    assert not wd.is_walk(step(3200, 3200), step(3201, 3200, flag=1))
    assert not wd.is_walk(step(3200, 3200), step(3201, 3200, bank=1))


@pytest.mark.parametrize("packed", [0, tile(3200, 3200), tile(3200, 3200, 3), tile(32767, 32767, 3)])
def test_unpack_inverts_the_packing(packed):
    x, y, plane = wd.unpack(packed)
    assert tile(x, y, plane) == packed
