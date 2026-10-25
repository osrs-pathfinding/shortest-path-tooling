#!/usr/bin/env python3
"""Measure the real-tokenizer token count of the plugin's src/main/java.

Skeleton — implementation lands in the GREEN step.
"""
import importlib.util
from pathlib import Path

_MF_PATH = Path(__file__).resolve().parent / "measure_footprint.py"
_spec = importlib.util.spec_from_file_location("measure_footprint", _MF_PATH)
mf = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mf)

ENCODING = "cl100k_base"


def measure(repo, ref, enc):
    raise NotImplementedError


def main():
    raise NotImplementedError


if __name__ == "__main__":
    main()
