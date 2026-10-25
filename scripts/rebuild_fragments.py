#!/usr/bin/env python3
"""Emit fragment specs for the segment-attributed history rebuild.

Skeleton — implementation lands in the GREEN step.
"""
from __future__ import annotations

import subprocess

SEGMENT_ORDER = ["v1.22.2", "v1.22.3", "v1.22.4", "v1.22.5", "post-v1.22.5"]


def git(repo, *args):
    try:
        return subprocess.run(
            ["git", "-C", repo, *args], check=True, capture_output=True, text=True
        ).stdout
    except subprocess.CalledProcessError as e:
        raise RuntimeError(
            f"git -C {repo} {' '.join(args)} failed with exit code "
            f"{e.returncode}:\n{(e.stderr or '').strip()}"
        ) from e


def parse_ledger(text):
    raise NotImplementedError


def parse_show_u0_hunks(diff_text):
    raise NotImplementedError


def generate(repo, series_base, branch, ledger_paths):
    raise NotImplementedError


def main() -> int:
    raise NotImplementedError


if __name__ == "__main__":
    import sys
    sys.exit(main())
