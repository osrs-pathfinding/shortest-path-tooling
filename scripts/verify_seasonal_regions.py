#!/usr/bin/env python3
"""
Verify seasonal teleport region assignments against wiki ground truth.

Reads:
- src/test/resources/leagues_regions.tsv (bbox source-of-truth in this repo)
- ../shortest-path/src/main/resources/transports/seasonal_transports.tsv
  (plugin data in the sibling shortest-path repo)

Re-implements LeagueRegionDumperTest's chunk classifier in Python, then for
each Map of Alacrity / Evil Eye row compares the actual region (current
chunk-based result, or the per-row 'Region override' column when set)
against the wiki's region categorization.

Output: a table of mismatches grouped by relic.
"""

import os
import re
import sys
from collections import defaultdict

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BBOX_TSV = os.path.join(REPO, "src/test/resources/leagues_regions.tsv")
TRANSPORTS = os.path.normpath(
    os.path.join(
        REPO, "../shortest-path/src/main/resources/transports/seasonal_transports.tsv"
    )
)


def load_bboxes(path):
    """Returns list of (region, xMin, xMax, yMin, yMax) in file order."""
    out = []
    with open(path) as f:
        for line in f:
            s = line.strip()
            if not s or s.startswith("#"):
                continue
            parts = s.split("\t")
            if len(parts) != 5:
                continue
            r, x1, x2, y1, y2 = parts
            out.append((r, int(x1), int(x2), int(y1), int(y2)))
    return out


def classify_chunk(cx, cy, bboxes):
    """Mimic LeagueRegionDumperTest: pick bbox with greatest overlap; ties -> earliest in file."""
    chunk_x_min = cx * 64
    chunk_x_max = chunk_x_min + 63
    chunk_y_min = cy * 64
    chunk_y_max = chunk_y_min + 63
    best = None
    best_area = 0
    for r, x1, x2, y1, y2 in bboxes:
        ox1 = max(chunk_x_min, x1)
        ox2 = min(chunk_x_max, x2)
        oy1 = max(chunk_y_min, y1)
        oy2 = min(chunk_y_max, y2)
        if ox1 > ox2 or oy1 > oy2:
            continue
        area = (ox2 - ox1 + 1) * (oy2 - oy1 + 1)
        if area > best_area:
            best_area = area
            best = r
    return best or "NEUTRAL"


def classify_tile(x, y, bboxes):
    cx = x >> 6
    cy = y >> 6
    return classify_chunk(cx, cy, bboxes)


# ------------------- WIKI GROUND TRUTH -------------------
# Map of Alacrity: substring match against the display info (after "Map of Alacrity: ").
ALACRITY = {
    "ASGARNIA": [
        "Burthorpe:",
        "Dwarf mine:",
        "Falador:",
        "God wars dungeon:",
        "Heroes' guild:",
        "Ice dungeon:",
        "Ice mountain:",
        "Motherlode mine:",
        "Taverley:",
        "Taverley dungeon:",
        "Troll stronghold:",
        "Trollheim:",
    ],
    "DESERT": [
        "Al Kharid:",
        "Kalphite lair:",
        "Necropolis:",
        "Pollnivneach:",
    ],
    "FREMENNIK": [
        "Slayer dungeon:",
        "Miscellania:",
        "Rellekka:",
        "Waterbirth island:",
    ],
    "KANDARIN": [
        "Ardougne:",
        "Barbarian outpost:",
        "Catherby:",
        "Corsair cove:",
        "Gnome Stronghold:",
        "Eagles peak:",
        "Coal trucks:",
        "Observatory:",
        "Sinclair mansion:",
        "Stronghold slayer cave:",
        "Yanille:",
        "Yanille dungeon:",
    ],
    "KARAMJA": [
        "Brimhaven:",
        "Brimhaven dungeon:",
        "Cairn isle:",
        "Crandor:",
        "Kharazi jungle:",
        "Musa point:",
        "Shaman caves:",
        "Shilo village:",
        "Waterfall:",
        "Viyeldi caves:",
    ],
    "KOUREND": [
        "Karuulm slayer dungeon:",
        "Karuulm:",
        "Dense essence mine:",
        "Catacombs:",
        "Chasm of fire:",
        "Hosidius:",
        "Forthos dungeon:",
        "Wintertodt:",
    ],
    "MORYTANIA": [
        "Barrows:",
        "Burgh de Rott:",
        "Darkmeyer:",
        "Mausoleum:",
        "Meyerditch lab:",
        "Nature grotto:",
        "Mort Myre:",
        "Mos Le'Harmless:",
        "Ectofuntus:",
        "Slayer tower:",
    ],
    "TIRANNWN": [
        "Arandar:",
        "Iorwerth dungeon:",
        "Zul-Andra:",
    ],
    "VARLAMORE": [
        "Aldarin:",
        "Nemus retreat:",
        "Auburn valley:",
        "Custodia cave:",
        "Darkfrost:",
        "Proudspire:",
        "Ralos rise:",
        "Mokhaiotl ruins:",
        "Stalker den:",
        "Tlati rainforest:",
        "Tonali cavern:",
        "Zanaris:",
    ],
    "WILDERNESS": [
        "Chaos temple:",
        "Deep wilderness dungeon:",
        "Lava dragon isle:",
        "Lava maze:",
        "Revenant cave:",
        "Wilderness god wars dungeon:",
        "Wilderness slayer cave:",
    ],
}

# Evil Eye: substring match against the display info (after "Evil Eye: ").
EVIL_EYE = {
    "ASGARNIA": [
        "Cerberus",
        "Commander Zilyana",
        "Giant Mole",
        "General Graardor",
        "Kree'arra",
        "K'ril Tsutsaroth",
        "Nex",
        "Royal Titans",
        "Whisperer",
    ],
    "DESERT": [
        "Kalphite Queen",
        "Leviathan",
        "Tempoross",
        "Tombs of Amascut",
    ],
    "FREMENNIK": [
        "Dagannoth Kings",
        "Duke Sucellus",
        "Phantom Muspah",
        "Vorkath",
    ],
    "KANDARIN": ["Kraken", "Thermonuclear smoke devil"],
    "KARAMJA": ["Fight Cave", "Inferno", "TzHaar-Ket-Rak"],
    "KOUREND": [
        "Alchemical hydra",
        "Chambers of Xeric",
        "Hespori",
        "Mimic",
        "Sarachnis",
        "Skotizo",
        "Wintertodt",
        "Yama",
    ],
    "MORYTANIA": [
        "Araxxor",
        "Barrows",
        "Grotesque Guardians",
        "The Nightmare",
        "Theatre of Blood",
    ],
    "TIRANNWN": ["The Gauntlet", "Zalcano", "Zulrah"],
    "VARLAMORE": [
        "Amoxliatl",
        "Doom of Mokhaiotl",
        "Fortis Colosseum",
        "The Hueycoatl",
        "Abyssal Sire",
        "Moons of Peril",
        "Vardorvis",
    ],
    "WILDERNESS": [
        "Artio",
        "Callisto",
        "Calvar'ion",
        "Chaos Elemental",
        "Chaos Fanatic",
        "Corporeal Beast",
        "Crazy Archaeologist",
        "King Black Dragon",
        "Scorpia",
        "Spindel",
        "Venenatis",
        "Vet'ion",
    ],
}

# Fairy Mushroom: matched by leading token of the display info after
# "Fairy Mushroom: ". For fairy ring teleports the leading token is the
# 3-letter code (AIQ, AIR, ...). For spirit trees & tool leprechauns the
# leading text is the location name (possibly with a " (planted)" suffix
# stripped). Source: https://oldschool.runescape.wiki/w/Fairy_mushroom?action=raw
# (Demonic Pacts League — Nature's Accord relic). Destinations whose wiki
# row carries the multi-region {{DPIcon|...}} tag instead of {{DPL|Region}}
# (Misthalin/Varlamore sub-realms like Zanaris, Abyssal Area, Cosmic plane,
# Gorak's Plane, Abyssal Nexus, Enchanted Valley, POH garden) are not in
# any single DPL region and are intentionally NEUTRAL — they are omitted
# from this table so the verifier flags them as "unmapped" rather than
# mismatched.
MUSHROOM = {
    "ASGARNIA": [
        "AIQ", "Entrana", "Falador Farm", "Falador Park",
        "Port Sarim", "Rimmington", "Taverley", "Troll Stronghold",
    ],
    "DESERT": [
        "AKP", "BIQ", "DLQ", "Al Kharid",
    ],
    "FREMENNIK": [
        "AJR", "AJS", "ALP", "CIP", "DKS",
        "Etceteria", "Weiss",
    ],
    "KANDARIN": [
        "AIR", "AKQ", "AKS", "ALS", "BIS", "BKP", "BLR",
        "CIQ", "CJR", "CLR", "CLS", "DJP",
        "Ardougne Monastery", "Battlefield of Khazard", "Catherby",
        "Feldip Hills", "Myths' Guild", "North of Ardougne",
        "North of McGrubor's Wood", "Poison Waste", "Tree Gnome Stronghold",
        "Tree Gnome Village", "White Wolf Mountain", "Yanille",
    ],
    "KARAMJA": [
        "BJR", "BLP", "CKR", "DKP",
        "Brimhaven", "Tai Bwo Wannai",
    ],
    "KOUREND": [
        "BLS", "CIR", "CIS", "DJR",
        "Farming Guild", "Hosidius",
    ],
    "MISTHALIN": [
        "BJP", "CLP", "DIS", "DKR",
        "Champions' Guild", "Draynor Manor", "Fossil Island", "Grand Exchange",
        "Lumbridge", "North of Seth Groats", "Underwater", "Varrock",
    ],
    "MORYTANIA": [
        "ALQ", "BIP", "BKR", "CKS", "DLS",
        "Canifis", "Harmony Island", "Port Phasmatys",
    ],
    "TIRANNWN": [
        "BJS", "DLR",
        "Lletya", "Prifddinas",
    ],
    "VARLAMORE": [
        "AIS", "AJP", "CKQ",
        "Aldarin", "Auburnvale", "Kastori", "Locus Oasis", "Nemus Retreat",
        "Ortus Farm",
    ],
}

# Banker's Briefcase: substring match against the display info after
# "Banker's Briefcase: ". Source: the Combined map pin block on
# https://oldschool.runescape.wiki/w/Bank_Heist_(Demonic_Pacts_League)?action=raw
# Each line in that block has "x,y,title:NAME,desc:REGION,icon:..." which gives
# the in-game region classification for that bank pin. Banks that don't appear
# on the wiki map (most Misthalin banks — Varrock, Edgeville, Lumbridge — plus
# Wars/Souls/Tutorial Island/sub-realm destinations) are intentionally NEUTRAL
# in the chunk classifier and are reported as 'unmapped' rather than
# mismatched.
BRIEFCASE = {
    "ASGARNIA": [
        "Ancient Prison", "Camdozaal", "Crafting Guild",
        "Falador", "Mining Guild", "Motherlode Mine", "Port Sarim",
        "Rogues' Den", "Void Knights' Outpost", "Warriors' Guild",
    ],
    "DESERT": [
        "Al Kharid", "Emir's Arena", "Mage Training Arena", "Nardah",
        "Shantay Pass", "Sophanem", "Tombs of Amascut", "Unkah",
    ],
    "FREMENNIK": [
        "Blast Furnace", "Etceteria", "Jatizso", "Keldagrim",
        "Lunar Isle", "Neitiznot", "Peer the Seer",
    ],
    "KANDARIN": [
        "Ape Atoll", "Ardougne", "Barbarian", "Castle Wars", "Catherby",
        "Corsair Cove", "Fishing Guild", "Gnome Stronghold", "Legends' Guild",
        "Myths' Guild", "Ourania", "Piscatoris", "Port Khazard",
        "Seers' Village", "Yanille",
    ],
    "KARAMJA": [
        "Mor Ul Rek", "Rionasta", "Shilo Village", "Tzhaar City",
    ],
    "KOUREND": [
        "Arceuus", "Blast mine", "Chambers of Xeric", "Charcoal camp",
        "Ent dungeon", "Farming Guild", "Hosidius", "Kourend Castle",
        "Land's End", "Lovakengj", "Mount Karuulm", "Port Piscarilius",
        "Saltpetre mine", "Shayzien", "Sulphur mine", "Vinery",
        "Wintertodt", "Woodcutting Guild",
    ],
    "MORYTANIA": [
        "Burgh de Rott", "Canifis", "Darkmeyer", "Hallowed Sepulchre",
        "Mos Le'Harmless", "Port Phasmatys", "Tarn's Lair", "Trouble Brewing",
        "Ver Sinhaza",
    ],
    "TIRANNWN": [
        "Gauntlet", "Lletya", "Prifddinas", "Trahaearn mine",
    ],
    "VARLAMORE": [
        "Aldarin", "Auburn",   # matches both "Auburn Valley" pins + "Auburnvale" TSV
        "Cam Torum", "Civitas illa Fortis", "Fortis Colosseum",
        "Hueycoatl", "Hunter Guild", "Mistrock", "Quetzacalli Gorge",
        "Tal Teklan",
    ],
    "WILDERNESS": [
        "Ferox Enclave", "Mage Arena",
    ],
}


def expected_region(display_info, table):
    """Longest-key-wins substring match. Returns region or None."""
    if ":" in display_info:
        rest = display_info.split(":", 1)[1].strip()
    else:
        rest = display_info
    rest_lc = rest.lower()
    best_region = None
    best_len = 0
    for region, keys in table.items():
        for k in keys:
            if k.lower() in rest_lc and len(k) > best_len:
                best_len = len(k)
                best_region = region
    return best_region


def main():
    bboxes = load_bboxes(BBOX_TSV)
    if not bboxes:
        sys.exit(f"no bboxes loaded from {BBOX_TSV}")

    mismatches_alacrity = []
    mismatches_evileye = []
    mismatches_mushroom = []
    briefcase_neutral = []
    unmapped = []

    with open(TRANSPORTS) as f:
        for lineno, raw in enumerate(f, 1):
            line = raw.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 7:
                continue
            dest = parts[0].strip()
            display = parts[6].strip()
            if not dest or not display:
                continue
            m = re.match(r"^(\d+)\s+(\d+)\s+(\d+)$", dest)
            if not m:
                continue
            x, y, _ = int(m.group(1)), int(m.group(2)), int(m.group(3))
            actual = classify_tile(x, y, bboxes)
            # Column index 11 is the per-row `Region override`. When set,
            # PathfinderConfig.isTransportRegionAllowed uses it as the
            # effective destination region — treat the row as matching
            # the override for verification purposes.
            override = parts[11].strip() if len(parts) > 11 else ""
            if override:
                actual = override.upper()

            if display.startswith("Map of Alacrity:"):
                exp = expected_region(display, ALACRITY)
                if exp is None:
                    unmapped.append((lineno, display, x, y, actual))
                elif exp != actual:
                    mismatches_alacrity.append((lineno, display, x, y, actual, exp))
            elif display.startswith("Evil Eye:"):
                exp = expected_region(display, EVIL_EYE)
                if exp is None:
                    unmapped.append((lineno, display, x, y, actual))
                elif exp != actual:
                    mismatches_evileye.append((lineno, display, x, y, actual, exp))
            elif display.startswith("Fairy Mushroom:"):
                exp = expected_region(display, MUSHROOM)
                if exp is None:
                    unmapped.append((lineno, display, x, y, actual))
                elif exp != actual:
                    mismatches_mushroom.append((lineno, display, x, y, actual, exp))
            elif display.startswith("Banker's Briefcase:"):
                exp = expected_region(display, BRIEFCASE)
                if exp is None:
                    unmapped.append((lineno, display, x, y, actual))
                elif exp != actual:
                    briefcase_neutral.append((lineno, display, x, y, actual, exp))

    def print_section(title, rows):
        print(
            f"\n=== {title} ({len(rows)} mismatch{'es' if len(rows) != 1 else ''}) ==="
        )
        if not rows:
            return
        for r in rows:
            ln, disp, x, y, actual, exp = r
            print(
                f"  L{ln:<4} ({x:>5},{y:>5})  actual={actual:<12} expected={exp:<12}  {disp}"
            )

    print_section(
        "Map of Alacrity mismatches (wiki vs chunk-classifier)", mismatches_alacrity
    )
    print_section("Evil Eye mismatches (wiki vs chunk-classifier)", mismatches_evileye)
    print_section(
        "Fairy Mushroom mismatches (wiki vs chunk-classifier)", mismatches_mushroom
    )
    print_section(
        "Banker's Briefcase mismatches (wiki vs chunk-classifier)", briefcase_neutral
    )

    if unmapped:
        print(
            f"\n=== Unmapped display names ({len(unmapped)}) — not found in wiki tables ==="
        )
        for r in unmapped:
            ln, disp, x, y, actual = r
            print(f"  L{ln:<4} ({x:>5},{y:>5})  actual={actual:<12} {disp}")

    # Counts summary
    print(
        f"\nSummary: {len(mismatches_alacrity)} Alacrity, "
        f"{len(mismatches_evileye)} Evil Eye, "
        f"{len(mismatches_mushroom)} Fairy Mushroom, "
        f"{len(briefcase_neutral)} Banker's Briefcase, "
        f"{len(unmapped)} unmapped."
    )


if __name__ == "__main__":
    main()
