import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))

import site_lists  # noqa: E402

FORESTRY = '''
public enum CallLocation
{
	ARCEUUS("Arceuus", 1683, 3746, "Book of the Dead 5"),
	DRUMSTICK("Drum", 2146, 3548, 120, "Tele to boat docked at Drumstick", Quest.PANDEMONIUM),
	ZANARIS("Zanaris", 2415, 4463, "Fairy Ring Zanaris", Quest.LOST_CITY);
	private static final int DEFAULT_RADIUS_TILES = 40;
	CallLocation(String callout, int centreX, int centreY, String quickTele)
'''

STAR = '''
public enum StarLocationDetails
{
	RIMMINGTON_MINE("Rimmington mine", Region.ASGARNIA, new Point(2974, 3241), null,
		new StarLocationTransport[]{
			new StarLocationTransport("Rimmington POH", TransportType.HOUSE_PORTAL),
		}),
	MYTHS_GUILD("Myths' Guild", Region.FELDIP_HILLS, new Point(2468, 2842), Quest.DRAGON_SLAYER_II,
'''


def test_reads_each_forestry_call_with_or_without_a_radius():
    sites = site_lists.forestry_sites(FORESTRY, "src")
    assert [s["id"] for s in sites] == ["forestry-arceuus", "forestry-drumstick", "forestry-zanaris"]
    assert sites[1] == {"id": "forestry-drumstick", "name": "Drum", "category": "forestry",
                        "target": [2146, 3548, 0], "targetSource": "src DRUMSTICK"}


def test_reads_each_star_site_with_its_region():
    sites = site_lists.star_sites(STAR, "src")
    assert [(s["id"], s["category"], s["target"]) for s in sites] == [
        ("star-rimmington-mine", "star asgarnia", [2974, 3241, 0]),
        ("star-myths-guild", "star feldip_hills", [2468, 2842, 0])]


def test_writes_one_site_per_line():
    text = site_lists.render(site_lists.star_sites(STAR, "src"))
    lines = text.splitlines()
    assert lines[0] == "[" and lines[-1] == "]" and len(lines) == 4
    assert json.loads(text)[1]["name"] == "Myths' Guild"
