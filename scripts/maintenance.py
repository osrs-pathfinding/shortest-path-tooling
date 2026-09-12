#!/usr/bin/env python3
"""Orchestrating maintenance CLI for the shortest-path tooling repo.

One discoverable entry point for the post-game-update maintenance
surfaces.  Every external invocation goes through the single ``run``
seam below: list-form argv, captured output, per-operation timeouts.
All scratch state lives under ``build/``; regenerated data lands only in
the submodule's ``src/main/resources/``.

Subcommands:
    cache          download the latest OSRS cache + keys.json from
                   archive.openrs2.org, then patch keys.json into the
                   shape the RuneLite XteaKeyManager expects
    collision-map  update the submodule's collision-map.zip — by default
                   fast-forward the submodule to origin/master and print
                   an edge diff of the new artifact for review;
                   ``--local`` regenerates the zip through the local
                   runelite pipeline instead
"""

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path
from typing import List, Optional

REPO = Path(__file__).resolve().parent.parent
SUBMODULE = REPO / "shortest-path"

GIT_TIMEOUT_SECONDS = 120
SCRIPT_TIMEOUT_SECONDS = 300
DOWNLOAD_TIMEOUT_SECONDS = 1800
GRADLE_TIMEOUT_SECONDS = 1800
RUNELITE_BUILD_TIMEOUT_SECONDS = 3600
JAVA_DUMPER_TIMEOUT_SECONDS = 1800


def run(cmd: List[str], *, cwd: Optional[Path] = None,
        timeout: int = SCRIPT_TIMEOUT_SECONDS,
        binary: bool = False) -> subprocess.CompletedProcess:
    """The only subprocess call site — injected/monkeypatched in tests.

    ``binary=True`` captures stdout/stderr as bytes (needed for ``git
    show`` of binary artifacts); otherwise output is decoded text.  The
    default cwd is the repo root so wrapped scripts write their scratch
    files where ``.gitignore`` already covers them.
    """
    try:
        return subprocess.run(
            cmd, cwd=cwd if cwd is not None else REPO,
            capture_output=True, text=not binary, timeout=timeout)
    except subprocess.TimeoutExpired:
        # A wedged subprocess (network stall, gradle daemon hang) must
        # not hang the whole maintenance run without a diagnostic.
        raise SystemExit(
            f"{cmd[0]} timed out after {timeout}s") from None


def _stderr_tail(proc: subprocess.CompletedProcess, lines: int = 8) -> str:
    tail = (proc.stderr or "").strip().splitlines()
    return "\n".join(tail[-lines:])


def check_tools(names: List[str]) -> None:
    """Fail fast when a wrapped binary is missing from PATH."""
    missing = [n for n in names if shutil.which(n) is None]
    if missing:
        raise SystemExit(
            f"missing required tools: {', '.join(missing)}")


def patch_keys_json(path: Path) -> int:
    """Rename openrs2 key fields to the shape the RuneLite
    XteaKeyManager expects: ``mapsquare`` -> ``region`` and
    ``key`` -> ``keys``.

    Operates on parsed JSON, so it is field-precise and idempotent —
    unlike the workflow's sed pair, which corrupts ``"keys"`` into
    ``"keyss"`` on re-run and differs between BSD and GNU ``sed -i``.
    Malformed input (not a JSON list of objects) aborts before anything
    is written.  Returns the number of fields renamed.
    """
    try:
        entries = json.loads(path.read_text())
    except (json.JSONDecodeError, OSError) as exc:
        raise SystemExit(
            f"{path}: cannot parse keys JSON ({exc})") from None
    if not isinstance(entries, list) or not all(
            isinstance(e, dict) for e in entries):
        raise SystemExit(
            f"{path}: expected a JSON list of objects — refusing to "
            f"patch")
    renamed = 0
    for entry in entries:
        if "mapsquare" in entry:
            entry["region"] = entry.pop("mapsquare")
            renamed += 1
        if "key" in entry:
            entry["keys"] = entry.pop("key")
            renamed += 1
    if renamed:
        path.write_text(json.dumps(entries, indent=2) + "\n")
    return renamed


def do_cache() -> int:
    """Download the latest OSRS cache and patch keys.json."""
    check_tools(["curl", "jq", "unzip"])
    script = REPO / "collision-map-update" / "download-latest-cache.sh"
    # The script writes ./cache, ./cache.zip and ./keys.json into its
    # working directory — pin cwd to the repo root so the artifacts land
    # where every consumer (dumpers, the keys patch, .gitignore)
    # expects them.
    proc = run([str(script)], cwd=REPO, timeout=DOWNLOAD_TIMEOUT_SECONDS)
    if proc.returncode != 0:
        print(f"download-latest-cache.sh failed "
              f"(rc={proc.returncode})", file=sys.stderr)
        tail = _stderr_tail(proc)
        if tail:
            print(tail, file=sys.stderr)
        return 1
    keys_path = REPO / "keys.json"
    if not keys_path.exists():
        print(f"download finished but {keys_path} is missing",
              file=sys.stderr)
        return 1
    renamed = patch_keys_json(keys_path)
    cache_dir = REPO / "cache"
    if not cache_dir.is_dir():
        print(f"download finished but {cache_dir} is missing",
              file=sys.stderr)
        return 1
    entries = sum(1 for _ in cache_dir.iterdir())
    print(f"cache/: {entries} entries, keys.json: {renamed} fields "
          f"renamed")
    return 0


def cmd_cache(args: argparse.Namespace) -> int:
    return do_cache()


def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser(
        "cache",
        help="Download the latest OSRS cache and patch keys.json")

    args = ap.parse_args(argv)

    if args.cmd == "cache":
        return cmd_cache(args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
