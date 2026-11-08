#!/usr/bin/env python3
"""Collapse same-package imports in the plugin's src/main/java into wildcards.

The plugin-hub token budget counts the import block of every file, so a
package imported name by name costs far more than one `import pkg.*;`.  This
tool performs that collapse mechanically, reproducibly and without changing
what any simple name means.

Subcommands
  universe  Build the pinned type universe (a data file) from a classpath file
            and the JDK API.
  rewrite   Rewrite src/main/java of a working tree in place.
  check     Read-only: exit 0 only when the tree at a ref is already canonical.

Rules (R1-R12)
  R1  Parse.  Imports are the comment-stripped lines
      `import [static ]<qualifier>[.*];` before the first type declaration,
      one statement per line.  A second statement on a line, code after the
      semicolon, an import line carrying a comment, or a qualifier that is
      not a dotted Java identifier is unusable input (exit 2, file:line).
      Commented-out imports are ignored.
  R2  Members.  An external package is looked up in the universe, a package of
      the tree is read from the tree's own sources; any other imported package
      is unusable input (regenerate the universe).  A file mentions a name when
      the identifier occurs in its code outside comments and string or char
      literals (and not after a dot), or leads a Javadoc {@link}, {@linkplain},
      @see, @throws, @exception or {@value} reference.
  R3  Existing on-demand imports are expanded in memory to the names the file
      mentions (members of the package, minus names the file declares or
      imports single-type, minus its own package, minus java.lang) and then
      collapsed again by the same rules, so the result does not depend on how
      the tree was reached.  A name provided by two on-demand packages is
      unusable input.  An existing `import static C.*;` is kept as is.
  R4  Nested-type qualifiers (java.util.Map.Entry) are never collapsed and
      never counted.
  R5  Groups: non-static single-type imports by package, static single
      imports by class.  A single import of a name the file never mentions
      is left alone and not counted (unused imports are not this tool's job).
  R6  A package with at least --threshold (default 2) names is a candidate.
  R7  Clash safety, iterated to a fixpoint: a name stays an explicit import
      when it exists in the file's own package, in java.lang, or in another
      package collapsed in the same file; a package whose remaining names
      fall below the threshold stays fully explicit, and the iteration
      repeats.
  R8  A candidate is skipped (and reported) when a name it provides is
      mentioned by the file, also lives in java.lang and is neither imported
      nor declared there: `pkg.*` would make that java.lang name ambiguous.
  R9  A static group of at least --static-threshold (default 2; 0 disables)
      collapses to `import static C.*;` only when it is the only static
      on-demand import the file would have, and C is a class of the tree.
      With two or more eligible classes none collapses; every static group
      left explicit is reported (static_groups_left_explicit).
  R10 Order convention: a wildcard is written on the line of the first import
      it replaces (an expanded existing wildcard keeps its own line).  The
      group's other lines are deleted.  Imports kept explicit, other imports,
      blank lines and comments are not touched and not reordered.  A group
      that falls below the threshold after expansion is written as its
      explicit imports in name order on the old wildcard line.  A file whose
      text does not change is not rewritten (byte-identical).
  R11 `check --repo R --ref REF`: exit 0 when the rewrite would change no file
      at REF and every on-demand import resolves at least one name the file
      mentions; exit 1 listing the offending files; exit 2 on unusable input.
      Blobs are read through `git show`, no checkout.
  R12 Only src/main/java/**/*.java is read for rewriting; src/test is never
      touched.  Iteration is in sorted order and rewrite(rewrite(x)) ==
      rewrite(x).

The type universe (scripts/data/wildcard-type-universe.tsv) is a committed
file built from a fixed RuneLite release classpath and the JDK 11 API, so the
result for a given tree never depends on what RuneLite published since.
`load_universe` refuses a file without the pinned release in its header.

Exit codes: 0 ok, 1 not canonical (check), 2 unusable input.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC_ROOT = "src/main/java"
PINNED_RUNELITE = "1.13.6"
RELEASE_MODULES = ("client", "runelite-api", "jshell", "cache")
REQUIRED_MODULES = ("client", "runelite-api")
UNIVERSE_FIRST_LINE = "# wildcard-type-universe v1"
DEFAULT_UNIVERSE = Path(__file__).resolve().parent / "data" / "wildcard-type-universe.tsv"
OWN_ROOT_PACKAGE = "shortestpath"
JDK_RELEASE = "11"
JDK_RELEASE_LETTER = "B"  # ct.sym names release 11 "B"

_MF_PATH = Path(__file__).resolve().parent / "measure_footprint.py"
_spec = importlib.util.spec_from_file_location("measure_footprint", _MF_PATH)
mf = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mf)

JAVA_KEYWORDS = frozenset("""
abstract assert boolean break byte case catch char class const continue default do
double else enum extends final finally float for goto if implements import
instanceof int interface long native new package private protected public return
short static strictfp super switch synchronized this throw throws transient try
void volatile while true false null var
""".split())

IDENT = r"[A-Za-z_$][A-Za-z0-9_$]*"
_IMPORT_START = re.compile(r"^\s*import\b")
_IMPORT_RE = re.compile(
    r"^\s*import\s+(static\s+)?(" + IDENT + r"(?:\." + IDENT + r")*)(\.\*)?\s*;\s*$")
_PACKAGE_RE = re.compile(r"^\s*package\s+(" + IDENT + r"(?:\." + IDENT + r")*)\s*;")
_TYPE_KEYWORD = re.compile(r"(?<![.\w$])(?:class|interface|enum)\b")
_DECL_RE = re.compile(r"(?<![.\w$])(?:class|interface|enum)\s+(" + IDENT + r")")
_HEADER_STMT = re.compile(r"(?m)^[ \t]*(?:import|package)\b[^;\n]*;")
_IDENT_USE = re.compile(r"(?<![\w$.])(" + IDENT + r")")
_JAVADOC_REF = re.compile(
    r"(?:\{@(?:link|linkplain|value)|@(?:see|throws|exception))\s+(" + IDENT + r")")
_TOKEN = re.compile(
    r'//[^\n]*|/\*.*?\*/|"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'', re.S)
_GRADLE_JAR = re.compile(
    r"/files-2\.1/(?P<g>[^/]+)/(?P<m>[^/]+)/(?P<v>[^/]+)/[0-9a-f]+/(?P<f>[^/]+)$")
_MAVEN_JAR = re.compile(
    r"/repository/(?P<g>.+)/(?P<m>[^/]+)/(?P<v>[^/]+)/(?P<f>[^/]+)$")


class UnusableInput(Exception):
    """Input that cannot be processed safely; mapped to exit code 2."""


# ---------- seams ----------

def git(repo, *args):
    """Run git in `repo`; every git call of this module goes through here."""
    try:
        return mf.git(str(repo), *args)
    except RuntimeError as e:
        raise UnusableInput(str(e)) from e


def run(argv):
    """Run a command given as an argument list; returns the CompletedProcess."""
    return subprocess.run(argv, check=False, capture_output=True, text=True)


# ---------- source analysis ----------

def _blank(s):
    return re.sub(r"[^\n]", " ", s)


def mask_source(text, name="<text>"):
    """(code, javadocs): `code` is `text` with comments and string/char
    literals blanked out (newlines kept, so line numbers still match);
    `javadocs` lists the bodies of the /** */ comments."""
    docs = []

    def sub(m):
        s = m.group()
        if s == '"""':
            raise UnusableInput(f"{name}: text blocks are not supported")
        if s.startswith("/**") and s != "/**/":
            docs.append(s)
        return _blank(s)

    return _TOKEN.sub(sub, text), docs


@dataclass
class Import:
    line: int
    static: bool
    qualifier: str
    wildcard: bool

    @property
    def package(self):
        """Package (non-static) or class (static) the import draws from."""
        return self.qualifier if self.wildcard else self.qualifier.rpartition(".")[0]

    @property
    def name(self):
        return "*" if self.wildcard else self.qualifier.rpartition(".")[2]

    @property
    def nested(self):
        """Non-static import whose qualifier ends in a type (R4)."""
        last = self.package.rpartition(".")[2]
        return (not self.static) and last[:1].isupper()


@dataclass
class Source:
    path: str
    lines: list
    pkg: str
    imports: list
    refs: set
    declared: set
    top_types: set
    code: str
    shadowed: set = field(default_factory=set)


def parse_imports(lines, code_lines, name="<text>"):
    """R1: the import statements of a file as a list of Import."""
    end = len(lines)
    for idx, cl in enumerate(code_lines):
        if _TYPE_KEYWORD.search(cl):
            end = idx
            break
    imports = []
    for idx in range(end):
        cl = code_lines[idx]
        if not _IMPORT_START.match(cl):
            continue
        where = f"{name}:{idx + 1}"
        m = _IMPORT_RE.match(cl)
        if not m:
            raise UnusableInput(f"{where}: unsupported import statement: {lines[idx].strip()}")
        if lines[idx].strip() != cl.strip():
            raise UnusableInput(f"{where}: import line carries a comment: {lines[idx].strip()}")
        imports.append(Import(idx, bool(m.group(1)), m.group(2), bool(m.group(3))))
    return imports


def declarations(code):
    """(declared, top_level): names of the classes, interfaces, enums and
    annotation types declared anywhere in the file / at brace depth 0."""
    declared, top = set(), set()
    depth, last = 0, 0
    for m in _DECL_RE.finditer(code):
        depth += code.count("{", last, m.start()) - code.count("}", last, m.start())
        last = m.start()
        declared.add(m.group(1))
        if depth == 0:
            top.add(m.group(1))
    return declared, top


def nested_shadowed(code):
    """Names of nested types whose every use in `code` lies inside the body of
    a class that declares a nested type of that name.  A member class shadows
    an on-demand import only there, so a name used anywhere else in the file
    (another top-level class, say) still resolves through the import."""
    close_of, stack = {}, []
    for idx, ch in enumerate(code):
        if ch == "{":
            stack.append(idx)
        elif ch == "}" and stack:
            close_of[stack.pop()] = idx
    scopes = defaultdict(list)
    stack, last = [], 0
    for m in _DECL_RE.finditer(code):
        for idx in range(last, m.start()):
            if code[idx] == "{":
                stack.append(idx)
            elif code[idx] == "}" and stack:
                stack.pop()
        last = m.start()
        if stack:
            scopes[m.group(1)].append((stack[-1], close_of.get(stack[-1], len(code))))
    if not scopes:
        return set()
    body = _HEADER_STMT.sub(lambda m: _blank(m.group()), code)
    body = re.sub(r"\.[ \t\r\n]+", lambda m: "." * len(m.group()), body)
    shadowed = set(scopes)
    for m in _IDENT_USE.finditer(body):
        name = m.group(1)
        if name in shadowed and not any(a <= m.start() < b for a, b in scopes[name]):
            shadowed.discard(name)
    return shadowed


def references(code, javadocs):
    """R2: simple names a file mentions (see the module docstring)."""
    body = _HEADER_STMT.sub(lambda m: _blank(m.group()), code)
    body = re.sub(r"\.[ \t\r\n]+", ".", body)
    refs = set(_IDENT_USE.findall(body))
    for doc in javadocs:
        refs.update(_JAVADOC_REF.findall(doc))
    return refs


def analyse(path, text):
    code, docs = mask_source(text, path)
    lines = text.split("\n")
    code_lines = code.split("\n")
    pkg = ""
    for cl in code_lines:
        m = _PACKAGE_RE.match(cl)
        if m:
            pkg = m.group(1)
            break
        if _TYPE_KEYWORD.search(cl):
            break
    declared, top = declarations(code)
    return Source(path, lines, pkg, parse_imports(lines, code_lines, path),
                  references(code, docs), declared, top, code, nested_shadowed(code))


def own_packages(sources):
    """{package: set of top-level type names} over analysed sources."""
    pkgs = defaultdict(set)
    for src in sources:
        pkgs[src.pkg].update(src.top_types)
    return dict(pkgs)


_GENERICS = re.compile(r"<[^<>]*>")


def member_names(code):
    """Names declared in the body of the outermost type of `code`, nested
    declarations and parameter lists left out.  A superset on purpose: used
    to tell whether a static wildcard resolves anything."""
    depth, out = 0, []
    for ch in code:
        if ch == "{":
            depth += 1
            if depth == 2:
                out.append("{")
            continue
        if ch == "}":
            depth -= 1
            continue
        if depth == 1:
            out.append(ch)
        elif depth == 0:
            out.append(" ")
    body = "".join(out)
    prev = None
    while prev != body:
        prev, body = body, _GENERICS.sub(" ", body)
    prev = None
    while prev != body:
        prev, body = body, re.sub(r"\([^()]*\)", "()", body)
    found = re.findall(r"(?<![\w$.])(" + IDENT + r")\s*(?=[(={;,])", body)
    return set(found) - JAVA_KEYWORDS


def nested_body(code, name):
    """The text from the opening brace to the matching closing brace of the
    type `name` declared directly inside the body that `code` holds (`code`
    is a whole file, or a body starting at its own opening brace), or None."""
    depth, last = 0, 0
    for m in _DECL_RE.finditer(code):
        depth += code.count("{", last, m.start()) - code.count("}", last, m.start())
        last = m.start()
        if depth != 1 or m.group(1) != name:
            continue
        start = code.find("{", m.end())
        if start < 0:
            return None
        level = 0
        for idx in range(start, len(code)):
            if code[idx] == "{":
                level += 1
            elif code[idx] == "}":
                level -= 1
                if level == 0:
                    return code[start:idx + 1]
        return None
    return None


# ---------- universe ----------

def load_universe(path):
    """{package: frozenset of top-level type names}; refuses a file that is
    not pinned to the expected RuneLite release."""
    try:
        text = Path(path).read_text(encoding="utf-8")
    except OSError as e:
        raise UnusableInput(f"cannot read universe {path}: {e}") from e
    lines = text.splitlines()
    if not lines or lines[0] != UNIVERSE_FIRST_LINE:
        raise UnusableInput(f"{path}: not a wildcard type universe (first line)")
    if f"# runelite: {PINNED_RUNELITE}" not in lines:
        raise UnusableInput(f"{path}: universe is not pinned to RuneLite {PINNED_RUNELITE}")
    pkgs = defaultdict(set)
    for line in lines:
        if not line or line.startswith("#"):
            continue
        pkg, _, name = line.partition("\t")
        if not name:
            raise UnusableInput(f"{path}: bad universe row: {line!r}")
        pkgs[pkg].add(name)
    return {p: frozenset(n) for p, n in pkgs.items()}


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def jar_coordinates(path):
    """(group, module, version) of a jar from its place in Gradle's module
    cache or in a Maven repository layout."""
    m = _GRADLE_JAR.search(path)
    if m:
        return m.group("g"), m.group("m"), m.group("v")
    m = _MAVEN_JAR.search(path)
    if m:
        return m.group("g").replace("/", "."), m.group("m"), m.group("v")
    raise UnusableInput(f"{path}: not in a Gradle module cache or Maven repository layout; "
                        "cannot tell the group of the jar")


def _import_packages_of_text(text, name):
    src = analyse(name, text)
    pkgs = set()
    for imp in src.imports:
        if not imp.static and not imp.nested:
            pkgs.add(imp.package)
    return pkgs


_ADDED_IMPORT = re.compile(
    r"^\+\s*import\s+(?!static\b)(" + IDENT + r"(?:\." + IDENT + r")*)(\.\*)?\s*;")


def collect_packages(repo, base_ref, head_refs):
    """U1: packages imported at base_ref, plus those of every import line the
    commits between base_ref and the head refs add."""
    pkgs = set()
    names = git(repo, "ls-tree", "-r", "--name-only", base_ref, "--", SRC_ROOT).splitlines()
    files = [n for n in names if n.endswith(".java")]
    if not files:
        raise UnusableInput(f"no {SRC_ROOT}/*.java files at {base_ref} in {repo}")
    for n in files:
        pkgs |= _import_packages_of_text(git(repo, "show", f"{base_ref}:{n}"), n)
    log = git(repo, "log", "-p", "--format=", "--no-color", "--no-ext-diff",
              f"^{base_ref}", *head_refs, "--", SRC_ROOT)
    for line in log.splitlines():
        m = _ADDED_IMPORT.match(line)
        if not m:
            continue
        q = m.group(1)
        pkg = q if m.group(2) else q.rpartition(".")[0]
        if pkg and not pkg.rpartition(".")[2][:1].isupper():
            pkgs.add(pkg)
    pkgs = {p for p in pkgs
            if p != OWN_ROOT_PACKAGE and not p.startswith(OWN_ROOT_PACKAGE + ".")}
    pkgs.add("java.lang")
    return pkgs


def _skip_class_name(name):
    return "$" in name or name in ("package-info", "module-info")


def jar_names(jar, packages, into):
    try:
        with zipfile.ZipFile(jar) as z:
            entries = z.namelist()
    except (OSError, zipfile.BadZipFile) as e:
        raise UnusableInput(f"{jar}: cannot list jar: {e}") from e
    for entry in entries:
        if not entry.endswith(".class"):
            continue
        pkg_path, _, file = entry.rpartition("/")
        name = file[:-len(".class")]
        pkg = pkg_path.replace("/", ".")
        if pkg in packages and not _skip_class_name(name):
            into[pkg].add(name)


def ctsym_names(ctsym, packages, into):
    try:
        with zipfile.ZipFile(ctsym) as z:
            entries = z.namelist()
    except (OSError, zipfile.BadZipFile) as e:
        raise UnusableInput(f"{ctsym}: cannot list ct.sym: {e}") from e
    for entry in entries:
        if not entry.endswith(".sig"):
            continue
        parts = entry.split("/")
        if len(parts) < 4 or JDK_RELEASE_LETTER not in parts[0]:
            continue
        pkg = ".".join(parts[2:-1])
        name = parts[-1][:-len(".sig")]
        if pkg in packages and not _skip_class_name(name):
            into[pkg].add(name)


def default_jdk_home():
    javac = shutil.which("javac")
    if not javac:
        raise UnusableInput("javac not found on PATH; pass --jdk-home")
    return Path(os.path.realpath(javac)).parent.parent


def cmd_universe(args):
    cp_lines = [ln.strip() for ln in Path(args.classpath_file).read_text().splitlines()
                if ln.strip()]
    if not cp_lines:
        raise UnusableInput(f"{args.classpath_file}: empty classpath")
    coords = {jar: jar_coordinates(jar) for jar in cp_lines}
    seen = set()
    for jar, (group, module, version) in coords.items():
        if group == "net.runelite" and module in RELEASE_MODULES:
            seen.add(module)
            if version != PINNED_RUNELITE:
                raise UnusableInput(f"{jar}: {group}:{module} is {version}, "
                                    f"expected {PINNED_RUNELITE}")
    for module in REQUIRED_MODULES:
        if module not in seen:
            raise UnusableInput(f"net.runelite:{module} is missing from {args.classpath_file}")

    head_refs = [r for r in (args.head_refs or "").split(",") if r]
    packages = collect_packages(args.repo, args.base_ref, head_refs)
    names = defaultdict(set)
    for jar in cp_lines:
        jar_names(jar, packages, names)
    jdk_home = Path(args.jdk_home) if args.jdk_home else default_jdk_home()
    ctsym = jdk_home / "lib" / "ct.sym"
    if not ctsym.is_file():
        raise UnusableInput(f"{ctsym}: no ct.sym in the JDK")
    ctsym_names(ctsym, packages, names)
    missing = sorted(p for p in packages if not names.get(p))
    if missing:
        raise UnusableInput("no type names found for imported package(s): " + ", ".join(missing))

    javac_path = jdk_home / "bin" / "javac"  # the JDK whose ct.sym was read, not PATH's
    try:
        javac = run([str(javac_path), "-version"])
    except OSError as e:
        raise UnusableInput(f"cannot run {javac_path}: {e}") from e
    javac_version = ((javac.stdout or "") + (javac.stderr or "")).strip() or "javac"
    refs = [args.base_ref] + head_refs
    shas = [(r, git(args.repo, "rev-parse", "--verify", f"{r}^{{commit}}").strip()) for r in refs]

    header = [UNIVERSE_FIRST_LINE, f"# runelite: {PINNED_RUNELITE}"]
    for module in sorted(seen):
        header.append(f"# runelite-pinned: net.runelite:{module}:{PINNED_RUNELITE}")
    unpinned = sorted(
        (g, m, v, jar) for jar, (g, m, v) in coords.items()
        if (g == "net.runelite" or g.startswith("net.runelite."))
        and not (g == "net.runelite" and m in RELEASE_MODULES))
    for g, m, v, jar in unpinned:
        header.append(f"# runelite-unpinned: {g}:{m}:{v} sha256 {sha256_file(jar)}")
    header.append(f"# jdk-api: release {JDK_RELEASE} from ct.sym of {javac_version}, "
                  f"sha256 {sha256_file(ctsym)}")
    for jar in sorted(cp_lines, key=lambda j: (os.path.basename(j), j)):
        header.append(f"# jar: {os.path.basename(jar)} sha256 {sha256_file(jar)}")
    header.append("# packages-from: " + " ".join(f"{r}={s}" for r, s in shas))
    header.append("# generated-by: scripts/wildcard_imports.py universe")
    rows = [f"{p}\t{n}" for p in sorted(names) for n in sorted(names[p])]
    Path(args.out).write_text("\n".join(header + rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} rows for {len(names)} packages to {args.out}")
    return 0


# ---------- trees ----------

class DiskTree:
    """src/main/java of a working directory."""

    def __init__(self, root):
        self.root = Path(root)

    def paths(self):
        base = self.root / SRC_ROOT
        if not base.is_dir():
            raise UnusableInput(f"{base}: no {SRC_ROOT}")
        return sorted(p.relative_to(self.root).as_posix() for p in base.rglob("*.java"))

    def read(self, path):
        try:
            return (self.root / path).read_bytes().decode("utf-8")
        except (OSError, UnicodeDecodeError) as e:
            raise UnusableInput(f"{path}: cannot read as UTF-8: {e}") from e


class GitTree:
    """src/main/java of a ref, read through git (no checkout)."""

    def __init__(self, repo, ref):
        self.repo, self.ref = repo, ref

    def paths(self):
        out = git(self.repo, "ls-tree", "-r", "--name-only", self.ref, "--", SRC_ROOT)
        paths = sorted(n for n in out.splitlines() if n.endswith(".java"))
        if not paths:
            raise UnusableInput(f"no {SRC_ROOT}/*.java files at {self.ref} in {self.repo}")
        return paths

    def read(self, path):
        return git(self.repo, "show", f"{self.ref}:{path}")


@dataclass
class Env:
    universe: dict
    tree_types: dict
    sources: dict = field(default_factory=dict)

    def members(self, pkg):
        if pkg in self.universe:
            return self.universe[pkg]
        if pkg in self.tree_types:
            return self.tree_types[pkg]
        raise UnusableInput(f"package {pkg} is neither in the universe nor in the tree; "
                            "regenerate the universe")

    def class_source(self, cls):
        """Source of a class of the tree (outer class if `cls` is nested)."""
        found = self._class_file(cls)
        return found[0] if found else None

    def _class_file(self, cls):
        parts = cls.split(".")
        while parts:
            path = f"{SRC_ROOT}/{'/'.join(parts)}.java"
            if path in self.sources:
                return self.sources[path], cls.split(".")[len(parts):]
            parts.pop()
        return None

    def class_body(self, cls):
        """(body, found): the code holding the members of `cls`, which is
        the class of a tree file or a type nested in one; `found` is False when
        the class is not in the tree or a nested type cannot be located."""
        located = self._class_file(cls)
        if located is None:
            return None, False
        csrc, nested = located
        body = csrc.code
        for name in nested:
            body = nested_body(body, name)
            if body is None:
                return None, False
        return body, True


def make_env(universe, texts):
    """Env over {path: text} (every src/main/java file of the tree)."""
    sources = {p: analyse(p, texts[p]) for p in sorted(texts)}
    return Env(universe, own_packages(sources.values()), sources)


# ---------- rewrite ----------

def _new_report():
    return {"collapsed": [], "kept_explicit": [], "skipped": [], "expanded": [],
            "removed_unresolved": [], "static_collapsed": [], "static_left_explicit": []}


@dataclass
class Result:
    path: str
    old: str
    new: str
    report: dict
    unresolved: list
    unverifiable: list = field(default_factory=list)

    @property
    def changed(self):
        return self.new != self.old


def _java_lang(env):
    if "java.lang" not in env.universe:
        raise UnusableInput("the universe has no java.lang package")
    return env.universe["java.lang"]


def rewrite_text(text, path, env, threshold=2, static_threshold=2):
    """The canonical rewrite of one file; returns a Result."""
    src = analyse(path, text)
    env.sources.setdefault(path, src)
    refs, declared = src.refs, src.declared
    own = env.tree_types.get(src.pkg, set()) | src.top_types
    jl = _java_lang(env)
    report = _new_report()
    unresolved = []

    plain = [i for i in src.imports if not i.static and not i.nested]
    single_all = {i.name for i in src.imports if not i.static and not i.wildcard}
    singles, wild_lines = defaultdict(list), defaultdict(list)
    for imp in plain:
        (wild_lines if imp.wildcard else singles)[imp.package].append(imp)
    if "" in singles or "" in wild_lines:
        raise UnusableInput(f"{path}: import from the default package")

    expand = {}
    for pkg in sorted(wild_lines):
        members = env.members(pkg)
        # A top-level declaration shadows an on-demand import file-wide; a nested
        # one only inside its enclosing class, so `shadowed` holds the nested
        # names whose every use lies in such a scope (see nested_shadowed).
        expand[pkg] = {n for n in members & refs
                       if n not in src.top_types and n not in src.shadowed
                       and n not in single_all and n not in own and n not in jl}
        if not expand[pkg]:
            unresolved.append(pkg + ".*")
    owner = {}
    for pkg in sorted(expand):
        for n in expand[pkg]:
            if n in owner:
                raise UnusableInput(f"{path}: {n} is provided by both {owner[n]}.* and {pkg}.*")
            owner[n] = pkg

    names = {}
    for pkg in sorted(set(singles) | set(wild_lines)):
        members = env.members(pkg)
        used = set()
        for imp in singles.get(pkg, ()):
            if imp.name not in members:
                raise UnusableInput(f"{path}:{imp.line + 1}: {imp.name} is not a type of "
                                    f"{pkg}; regenerate the universe")
            if imp.name in refs:
                used.add(imp.name)
        names[pkg] = used | expand.get(pkg, set())

    active = {p for p, n in names.items() if len(n) >= threshold}
    initial = set(active)
    skipped = {}
    for pkg in sorted(active):
        shadow = sorted(n for n in env.members(pkg) & refs
                        if n in jl and n not in single_all and n not in declared)
        if shadow:
            skipped[pkg] = f"java.lang:{shadow[0]}"
    active -= set(skipped)
    while True:
        dropped = False
        for pkg in sorted(active):
            others = set()
            for q in active:
                if q != pkg:
                    others |= env.members(q)
            keep = {n for n in names[pkg] if n in own or n in jl or n in others}
            if len(names[pkg] - keep) < threshold:
                active.discard(pkg)
                dropped = True
                break
        if dropped:
            continue
        break

    def keep_reason(pkg, n):
        if n in own:
            return "own-package"
        if n in jl:
            return "java.lang"
        for q in sorted(active):
            if q != pkg and n in env.members(q):
                return f"clash:{q}"
        return None

    edits = {}
    for pkg in sorted(set(singles) | set(wild_lines)):
        sl, wl = singles.get(pkg, []), wild_lines.get(pkg, [])
        if pkg in active:
            kept = {n for n in names[pkg] if keep_reason(pkg, n)}
            rem = names[pkg] - kept
            del_lines = [i.line for i in sl if i.name in rem]
            wline = wl[0].line if wl else min(del_lines)
            have = {i.name for i in sl}
            repl = [f"import {pkg}.*;"] + [f"import {pkg}.{n};"
                                           for n in sorted(kept - have)]
            edits[wline] = repl
            for line in del_lines + [w.line for w in wl[1:]]:
                if line != wline:
                    edits[line] = []
            report["collapsed"].append({"package": pkg, "members": len(rem)})
            if wl:
                report["expanded"].append({"package": pkg, "members": len(expand[pkg])})
        elif wl:
            repl = [f"import {pkg}.{n};" for n in sorted(expand[pkg])]
            edits[wl[0].line] = repl
            for w in wl[1:]:
                edits[w.line] = []
            if repl:
                report["expanded"].append({"package": pkg, "members": len(repl)})
            else:
                report["removed_unresolved"].append(pkg)
        if pkg in initial and pkg not in skipped:
            for n in sorted(names[pkg]):
                reason = keep_reason(pkg, n)
                if reason:
                    report["kept_explicit"].append(
                        {"name": n, "package": pkg, "reason": reason})
        if pkg in skipped:
            report["skipped"].append({"package": pkg, "reason": skipped[pkg]})

    unverifiable = []
    _static_pass(src, env, refs, declared, single_all, own, static_threshold,
                 edits, report, unresolved, unverifiable)

    out = []
    for idx, line in enumerate(src.lines):
        if idx not in edits:
            out.append(line)
            continue
        eol = "\r" if line.endswith("\r") else ""
        out.extend(r + eol for r in edits[idx])
    new = "\n".join(out)
    return Result(path, text, new, report, unresolved, unverifiable)


def _static_pass(src, env, refs, declared, single_all, own, static_threshold,
                 edits, report, unresolved, unverifiable):
    """R9 plus the static half of the resolve check."""
    wildcards = [i for i in src.imports if i.static and i.wildcard]
    for imp in wildcards:
        body, found = env.class_body(imp.package)
        if not found:
            unverifiable.append(f"{src.path}:{imp.line + 1}: static wildcard of "
                                f"{imp.package}, which is not a class of the tree")
            continue
        members = member_names(body)
        if not (members & refs) - declared - single_all:
            unresolved.append(f"static {imp.package}.*")
    if static_threshold <= 0:
        return
    groups = defaultdict(list)
    for imp in src.imports:
        if imp.static and not imp.wildcard and imp.name in refs:
            groups[imp.package].append(imp)
    eligible = {c: sorted({i.name for i in lst}) for c, lst in groups.items()
                if len({i.name for i in lst}) >= static_threshold}
    if not eligible:
        return
    if len(eligible) == 1 and not wildcards:
        cls = next(iter(eligible))
        if env.class_source(cls) is None:
            report["static_left_explicit"].append(
                {"class": cls, "members": len(eligible[cls]),
                 "reason": "class-not-in-tree"})
            return
        lines = sorted(i.line for i in groups[cls])
        edits[lines[0]] = [f"import static {cls}.*;"]
        for line in lines[1:]:
            edits[line] = []
        report["static_collapsed"].append({"class": cls, "members": len(eligible[cls])})
        return
    reason = "two-or-more-static-classes" if len(eligible) > 1 else "existing-static-wildcard"
    for cls in sorted(eligible):
        report["static_left_explicit"].append(
            {"class": cls, "members": len(eligible[cls]), "reason": reason})


def summarize(results):
    """Report JSON over per-file Results."""
    files, totals = {}, {
        "files": len(results), "files_changed": 0, "collapsed_groups": 0,
        "collapsed_imports": 0, "kept_explicit": 0, "kept_by_reason": {},
        "kept_by_name": {}, "kept_by_import": {}, "skipped_packages": 0,
        "expanded_wildcards": 0, "removed_unresolved": 0, "static_collapsed": 0,
        "static_groups_left_explicit": 0, "unresolved_wildcards": 0}
    for res in sorted(results, key=lambda r: r.path):
        rep = res.report
        if res.changed:
            totals["files_changed"] += 1
        totals["collapsed_groups"] += len(rep["collapsed"])
        totals["collapsed_imports"] += sum(c["members"] for c in rep["collapsed"])
        totals["skipped_packages"] += len(rep["skipped"])
        totals["expanded_wildcards"] += len(rep["expanded"])
        totals["removed_unresolved"] += len(rep["removed_unresolved"])
        totals["static_collapsed"] += len(rep["static_collapsed"])
        totals["static_groups_left_explicit"] += len(rep["static_left_explicit"])
        totals["unresolved_wildcards"] += len(res.unresolved)
        for k in rep["kept_explicit"]:
            totals["kept_explicit"] += 1
            kind = k["reason"].split(":")[0]
            for key, val in (("kept_by_reason", kind), ("kept_by_name", k["name"]),
                             ("kept_by_import", f"{k['package']}.{k['name']}")):
                totals[key][val] = totals[key].get(val, 0) + 1
        entry = {k: v for k, v in rep.items() if v}
        if res.unresolved:
            entry["unresolved"] = res.unresolved
        if entry:
            files[res.path] = entry
    for key in ("kept_by_reason", "kept_by_name", "kept_by_import"):
        totals[key] = dict(sorted(totals[key].items()))
    return {"files": files, "totals": totals}


def process_tree(tree, universe, threshold=2, static_threshold=2, only=None):
    """Rewrite results for the files of `tree` (all of them, or `only`)."""
    paths = tree.paths()
    texts = {p: tree.read(p) for p in paths}
    env = make_env(universe, texts)
    wanted = paths if only is None else sorted(only)
    for p in wanted:
        if p not in texts:
            raise UnusableInput(f"{p}: not a {SRC_ROOT} file of the tree")
    return [rewrite_text(texts[p], p, env, threshold, static_threshold) for p in wanted]


def _write_report(path, report):
    if path:
        Path(path).write_text(json.dumps(report, indent=2, sort_keys=True) + "\n",
                              encoding="utf-8")


def cmd_rewrite(args):
    universe = load_universe(args.universe)
    tree = DiskTree(args.repo)
    only = None
    if args.files:
        only = [Path(f).as_posix() for f in args.files]
    results = process_tree(tree, universe, args.threshold, args.static_threshold, only)
    for res in results:
        if res.changed:
            (Path(args.repo) / res.path).write_bytes(res.new.encode("utf-8"))
    report = summarize(results)
    _write_report(args.report, report)
    t = report["totals"]
    print(f"rewrote {t['files_changed']} of {t['files']} files: "
          f"{t['collapsed_groups']} wildcards, {t['kept_explicit']} imports kept explicit, "
          f"{t['skipped_packages']} packages skipped, {t['static_collapsed']} static collapses")
    return 0


def cmd_check(args):
    universe = load_universe(args.universe)
    tree = GitTree(args.repo, args.ref)
    results = process_tree(tree, universe, args.threshold, args.static_threshold)
    report = summarize(results)
    _write_report(args.report, report)
    unverifiable = [msg for r in results for msg in r.unverifiable]
    if unverifiable:
        raise UnusableInput("; ".join(unverifiable))
    bad = [r for r in results if r.changed or r.unresolved]
    for r in bad:
        why = []
        if r.changed:
            why.append("not canonical")
        if r.unresolved:
            why.append("resolves no name: " + ", ".join(r.unresolved))
        print(f"{r.path}: {'; '.join(why)}")
    if bad:
        print(f"{len(bad)} of {len(results)} files offend at {args.ref}")
        return 1
    print(f"canonical: {len(results)} files at {args.ref}")
    return 0


def build_parser():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = p.add_subparsers(dest="cmd", required=True)

    u = sub.add_parser("universe", help="build the pinned type universe")
    u.add_argument("--repo", required=True)
    u.add_argument("--classpath-file", required=True)
    u.add_argument("--jdk-home")
    u.add_argument("--base-ref", required=True)
    u.add_argument("--head-refs", default="")
    u.add_argument("--out", required=True)

    def common(sp):
        sp.add_argument("--universe", default=str(DEFAULT_UNIVERSE))
        sp.add_argument("--threshold", type=int, default=2)
        sp.add_argument("--static-threshold", type=int, default=2)
        sp.add_argument("--report")

    r = sub.add_parser("rewrite", help="rewrite a working tree in place")
    r.add_argument("--repo", required=True)
    r.add_argument("--files", nargs="+")
    common(r)

    c = sub.add_parser("check", help="read-only canonical-form check at a ref")
    c.add_argument("--repo", required=True)
    c.add_argument("--ref", required=True)
    common(c)
    return p


def main(argv=None):
    args = build_parser().parse_args(argv)
    try:
        return {"universe": cmd_universe, "rewrite": cmd_rewrite,
                "check": cmd_check}[args.cmd](args)
    except UnusableInput as e:
        print(f"error: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
