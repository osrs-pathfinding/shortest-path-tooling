#!/usr/bin/env python3
"""Rebuild driver: content-anchored fragment apply + upstream replay.

Skeleton — implementation lands in the GREEN commit.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys


def git(repo, *args):
    raise NotImplementedError


def gradle(worktree, *tasks):
    raise NotImplementedError


def parse_range(s):
    raise NotImplementedError


def cluster_runs(minus_runs):
    raise NotImplementedError


def locate_positions(content, needle):
    raise NotImplementedError


def resolve_match(content, old, before, after):
    raise NotImplementedError


def split_plus_per_hunk(fragment, groups, diff_hunks=None):
    raise NotImplementedError


def hunk_status(content, old, plus, era_content):
    raise NotImplementedError


def build_subject(fragment):
    raise NotImplementedError


def build_body(fragment):
    raise NotImplementedError


def provisional_amend_needed(head_subject, fragment):
    raise NotImplementedError


def expected_modify_delete(path):
    raise NotImplementedError


def replay_conflict_verdict(paths):
    raise NotImplementedError


def make_conflict_record(fragment, file, reason, match_count):
    raise NotImplementedError


def make_bump_record(fragment, from_seg, to_seg, missing_symbol,
                     compile_line):
    raise NotImplementedError


def make_ledger_row(fragment, commit, file, orig_segment,
                    applied_segment, position, match_counts,
                    disposition):
    raise NotImplementedError


def load_state(path):
    raise NotImplementedError


def save_state(path, state):
    raise NotImplementedError


def gate_failed(rc, out):
    raise NotImplementedError


def main() -> int:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("apply-fragment")
    sub.add_parser("run-segment")
    sub.add_parser("replay")
    sub.add_parser("status")
    args = ap.parse_args()
    raise NotImplementedError(args.cmd)


if __name__ == "__main__":
    sys.exit(main())
