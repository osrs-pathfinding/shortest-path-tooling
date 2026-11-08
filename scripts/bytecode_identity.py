#!/usr/bin/env python3
"""Prove that two versions of the plugin's src/main/java compile to the same bytes.

A mechanical import rewrite must not change behaviour.  The strongest cheap
proof is that the class files are identical: both trees are compiled with
`javac --release 11 -g:none` (no debug tables, so line numbers and local
names cannot differ) with Lombok on the processor path, and the two output
directories are compared with `diff -rq`.

Usage:
  python3 bytecode_identity.py --repo R --base TREEISH --head TREEISH
                               --classpath-file FILE
                               [--javac PATH] [--work DIR] [--self-check]

  --repo            git repository holding both tree-ishes (the plugin)
  --base, --head    tree-ishes whose src/main/java is compared
  --classpath-file  one jar per line (the compile classpath); the jar whose
                    file name starts with `lombok-` becomes the processor path
  --javac           compiler to use (default: `javac` on PATH)
  --work            scratch directory (default build/w/bytecode/<base12>-<head12>);
                    its base/, head/ and base2/ sub-directories are replaced
  --self-check      compile the base twice first and require identical output,
                    so javac or Lombok nondeterminism cannot read as a pass

Each tree is exported with `git archive` and extracted with the tarfile
`data` filter; nothing is checked out and no repository file is modified.

Exit codes: 0 identical, 1 class files differ, 2 unusable input (javac
failure, missing Lombok, empty export, bad classpath file, git failure).
"""
from __future__ import annotations

import argparse
import io
import re
import shutil
import subprocess
import sys
import tarfile
from pathlib import Path

SRC_ROOT = "src/main/java"
RELEASE = "11"


class UnusableInput(Exception):
    """Input that cannot be processed; mapped to exit code 2."""


# ---------- seams ----------

def run(argv):
    """Run a command given as an argument list; stdout and stderr are bytes."""
    try:
        return subprocess.run(argv, check=False, capture_output=True)
    except OSError as e:
        # a missing git, javac or diff: unusable input (exit 2), not "class
        # files differ" (exit 1, what an uncaught exception would look like)
        raise UnusableInput(f"cannot run {argv[0]}: {e}") from e


def _text(data):
    if isinstance(data, bytes):
        return data.decode("utf-8", errors="replace")
    return data or ""


def git(repo, *args):
    """stdout (bytes) of `git -C repo args...`; a failing git is unusable input."""
    p = run(["git", "-C", str(repo), *args])
    if p.returncode != 0:
        raise UnusableInput(f"git -C {repo} {' '.join(args)} failed with exit code "
                            f"{p.returncode}:\n{_text(p.stderr).strip()}")
    return p.stdout


# ---------- steps ----------

def export_tree(repo, treeish, dest):
    """Extract src/main/java of `treeish` into `dest` (replaced if present);
    returns the sorted list of .java files."""
    dest = Path(dest)
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True)
    data = git(repo, "archive", "--format=tar", treeish, "--", SRC_ROOT)
    if not hasattr(tarfile, "data_filter"):
        raise UnusableInput("this Python's tarfile has no extraction filters; "
                            "use a release that does (3.12, or a security update of 3.8+)")
    try:
        with tarfile.open(fileobj=io.BytesIO(data)) as tar:
            tar.extractall(dest, filter="data")
    except (tarfile.TarError, OSError) as e:
        raise UnusableInput(f"cannot extract {SRC_ROOT} of {treeish}: {e}") from e
    sources = sorted(str(p) for p in (dest / SRC_ROOT).rglob("*.java"))
    if not sources:
        raise UnusableInput(f"no java files under {SRC_ROOT} at {treeish}")
    return sources


def read_classpath(path):
    try:
        jars = [ln.strip() for ln in Path(path).read_text().splitlines() if ln.strip()]
    except OSError as e:
        raise UnusableInput(f"cannot read classpath file {path}: {e}") from e
    if not jars:
        raise UnusableInput(f"{path}: empty classpath file")
    lombok = [j for j in jars if Path(j).name.startswith("lombok-")]
    if not lombok:
        raise UnusableInput(f"{path}: no lombok-*.jar on the classpath")
    return jars, lombok[0]


def javac_major(javac):
    p = run([javac, "-version"])
    out = (_text(p.stdout) + _text(p.stderr)).strip()
    if p.returncode != 0:
        raise UnusableInput(f"{javac} -version failed: {out}")
    m = re.search(r"(\d+)(?:\.(\d+))?", out)
    if not m:
        raise UnusableInput(f"cannot read the javac version from {out!r}")
    major = int(m.group(1))
    if major == 1 and m.group(2):
        major = int(m.group(2))
    return out, major


def _argfile_line(path):
    return '"' + path.replace("\\", "\\\\").replace('"', '\\"') + '"'


def compile_tree(javac, major, sources, out_dir, jars, lombok, listing):
    """Compile `sources` into `out_dir`; returns the javac argv used."""
    out_dir = Path(out_dir)
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True)
    Path(listing).write_text("\n".join(_argfile_line(s) for s in sorted(sources)) + "\n")
    argv = [javac, "--release", RELEASE, "-g:none", "-encoding", "UTF-8", "-nowarn"]
    if major >= 21:
        argv.append("-proc:full")
    argv += ["-processorpath", lombok, "-cp", ":".join(jars), "-d", str(out_dir),
             f"@{listing}"]
    p = run(argv)
    if p.returncode != 0:
        tail = "\n".join(_text(p.stderr).strip().splitlines()[-20:])
        raise UnusableInput(f"javac failed with exit code {p.returncode}:\n{tail}")
    return argv


def compare_dirs(a, b):
    """(returncode, output lines) of `diff -rq a b`."""
    p = run(["diff", "-rq", str(a), str(b)])
    return p.returncode, _text(p.stdout).strip().splitlines()


def count_classes(directory):
    return sum(1 for _ in Path(directory).rglob("*.class"))


# ---------- entry point ----------

def build_parser():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--repo", required=True)
    p.add_argument("--base", required=True)
    p.add_argument("--head", required=True)
    p.add_argument("--classpath-file", required=True)
    p.add_argument("--javac", default="javac")
    p.add_argument("--work")
    p.add_argument("--self-check", action="store_true")
    return p


def _short(repo, treeish):
    return _text(git(repo, "rev-parse", "--verify", treeish)).strip()[:12]


def execute(args):
    jars, lombok = read_classpath(args.classpath_file)
    work = Path(args.work) if args.work else (
        Path("build/w/bytecode") / f"{_short(args.repo, args.base)}-"
        f"{_short(args.repo, args.head)}")
    work = work.resolve()
    version, major = javac_major(args.javac)
    print(f"javac: {version}")

    def build(treeish, name):
        sources = export_tree(args.repo, treeish, work / name)
        compile_tree(args.javac, major, sources, work / f"{name}-classes", jars, lombok,
                     work / f"{name}-sources.txt")
        return work / f"{name}-classes"

    base_out = build(args.base, "base")
    if args.self_check:
        again = build(args.base, "base2")
        rc, lines = compare_dirs(base_out, again)
        if rc != 0:
            raise UnusableInput("two compiles of the same tree differ (javac or Lombok "
                                "is not deterministic here):\n" + "\n".join(lines))
        print(f"self-check: two compiles of {args.base} are identical")
    head_out = build(args.head, "head")
    rc, lines = compare_dirs(base_out, head_out)
    if rc == 0:
        print(f"identical: {count_classes(base_out)} class files")
        return 0
    if rc == 1:
        print("class files differ:")
        for line in lines:
            print(f"  {line}")
        return 1
    raise UnusableInput(f"diff -rq failed with exit code {rc}")


def main(argv=None):
    args = build_parser().parse_args(argv)
    try:
        return execute(args)
    except UnusableInput as e:
        print(f"error: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
