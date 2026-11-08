"""Tests for ``scripts/bytecode_identity.py``.

The module's single ``run`` seam is replaced by a fake that answers the git,
javac and diff calls; the fake javac writes class files whose bytes depend on
the sources it was given, and ``diff`` is the real one, so identity and
difference are decided by the same comparison the gate uses.  The script is
loaded via importlib because ``scripts/`` has no ``__init__.py``.
"""

import importlib.util
import io
import subprocess
import sys
import tarfile
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = ROOT / "scripts" / "bytecode_identity.py"

spec = importlib.util.spec_from_file_location("bytecode_identity", SCRIPT_PATH)
bi = importlib.util.module_from_spec(spec)
sys.modules["bytecode_identity"] = bi
spec.loader.exec_module(bi)

REAL_RUN = subprocess.run


def cp(cmd, stdout=b"", stderr=b"", rc=0):
    return subprocess.CompletedProcess(cmd, rc, stdout, stderr)


def tar_of(files):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w") as tar:
        for name, text in files.items():
            data = text.encode()
            info = tarfile.TarInfo(name)
            info.size = len(data)
            tar.addfile(info, io.BytesIO(data))
    return buf.getvalue()


class Fakes:
    """Records every argv and answers git archive, javac and (optionally) diff."""

    def __init__(self, trees, javac_version=b"javac 17.0.18\n", javac_rc=0, diff=None):
        self.trees = trees
        self.javac_version = javac_version
        self.javac_rc = javac_rc
        self.diff = diff
        self.calls = []

    def __call__(self, argv):
        self.calls.append(list(argv))
        if argv[0] == "git":
            if "archive" in argv:
                return cp(argv, stdout=tar_of(self.trees[argv[argv.index("--") - 1]]))
            if "rev-parse" in argv:
                return cp(argv, stdout=(argv[-1] + "0" * 40).encode() + b"\n")
            raise AssertionError(argv)
        if argv[0].endswith("javac"):
            if "-version" in argv:
                return cp(argv, stdout=self.javac_version)
            if self.javac_rc:
                return cp(argv, stderr=b"A.java:3: error: boom\n", rc=self.javac_rc)
            listing = Path(argv[-1][1:])
            out = Path(argv[argv.index("-d") + 1])
            srcs = [ln.strip().strip('"') for ln in listing.read_text().splitlines()]
            digest = "".join(Path(s).read_text() for s in srcs)
            (out / "A.class").write_text(digest)
            return cp(argv)
        if argv[0] == "diff":
            if self.diff is not None:
                return self.diff
            return REAL_RUN(argv, check=False, capture_output=True)
        raise AssertionError(argv)

    def javac_calls(self):
        return [c for c in self.calls if c[0].endswith("javac") and "-version" not in c]


@pytest.fixture
def classpath(tmp_path):
    f = tmp_path / "cp.txt"
    f.write_text("/m2/a/client-1.13.6.jar\n/m2/b/lombok-1.18.30.jar\n")
    return f


def invoke(monkeypatch, tmp_path, classpath, fakes, *extra):
    monkeypatch.setattr(bi, "run", fakes)
    return bi.main(["--repo", "r", "--base", "BASE", "--head", "HEAD",
                    "--classpath-file", str(classpath), "--work", str(tmp_path / "w"),
                    *extra])


SAME = {"src/main/java/x/A.java": "class A {}\n"}
OTHER = {"src/main/java/x/A.java": "class A { int v; }\n"}


# ---------- exit codes ----------

def test_identical_class_files_exit_0(monkeypatch, tmp_path, classpath, capsys):
    fakes = Fakes({"BASE": SAME, "HEAD": dict(SAME)})
    assert invoke(monkeypatch, tmp_path, classpath, fakes) == 0
    assert "identical: 1 class files" in capsys.readouterr().out


def test_differing_class_files_exit_1_naming_the_file(monkeypatch, tmp_path, classpath, capsys):
    fakes = Fakes({"BASE": SAME, "HEAD": OTHER})
    assert invoke(monkeypatch, tmp_path, classpath, fakes) == 1
    out = capsys.readouterr().out
    assert "class files differ" in out and "A.class" in out


def test_diff_failure_other_than_a_difference_exits_2(monkeypatch, tmp_path, classpath, capsys):
    fakes = Fakes({"BASE": SAME, "HEAD": SAME}, diff=cp(["diff"], stderr=b"trouble", rc=2))
    assert invoke(monkeypatch, tmp_path, classpath, fakes) == 2
    assert "diff -rq failed" in capsys.readouterr().err


def test_javac_failure_exits_2_with_the_stderr_tail(monkeypatch, tmp_path, classpath, capsys):
    fakes = Fakes({"BASE": SAME, "HEAD": SAME}, javac_rc=1)
    assert invoke(monkeypatch, tmp_path, classpath, fakes) == 2
    assert "error: boom" in capsys.readouterr().err


# ---------- javac argv ----------

def test_javac_argv_carries_release_no_debug_and_the_lombok_processor(
        monkeypatch, tmp_path, classpath):
    fakes = Fakes({"BASE": SAME, "HEAD": SAME})
    assert invoke(monkeypatch, tmp_path, classpath, fakes) == 0
    first = fakes.javac_calls()[0]
    assert first[first.index("--release") + 1] == "11"
    assert "-g:none" in first
    assert first[first.index("-processorpath") + 1] == "/m2/b/lombok-1.18.30.jar"
    assert first[first.index("-cp") + 1] == "/m2/a/client-1.13.6.jar:/m2/b/lombok-1.18.30.jar"
    assert "-proc:full" not in first
    assert first[first.index("-encoding") + 1] == "UTF-8" and "-nowarn" in first


def test_javac_21_gets_proc_full(monkeypatch, tmp_path, classpath):
    fakes = Fakes({"BASE": SAME, "HEAD": SAME}, javac_version=b"javac 21.0.2\n")
    assert invoke(monkeypatch, tmp_path, classpath, fakes) == 0
    assert "-proc:full" in fakes.javac_calls()[0]


def test_the_javac_version_is_printed(monkeypatch, tmp_path, classpath, capsys):
    fakes = Fakes({"BASE": SAME, "HEAD": SAME})
    invoke(monkeypatch, tmp_path, classpath, fakes)
    assert "javac 17.0.18" in capsys.readouterr().out


def test_javac_override_is_used(monkeypatch, tmp_path, classpath):
    fakes = Fakes({"BASE": SAME, "HEAD": SAME})
    invoke(monkeypatch, tmp_path, classpath, fakes, "--javac", "/jdk/bin/javac")
    assert all(c[0] == "/jdk/bin/javac" for c in fakes.calls if c[0].endswith("javac"))


def test_git_archive_exports_only_main_sources_after_a_double_dash(
        monkeypatch, tmp_path, classpath):
    fakes = Fakes({"BASE": SAME, "HEAD": SAME})
    invoke(monkeypatch, tmp_path, classpath, fakes)
    archives = [c for c in fakes.calls if "archive" in c]
    assert [c[-3:] for c in archives] == [["BASE", "--", "src/main/java"],
                                          ["HEAD", "--", "src/main/java"]]
