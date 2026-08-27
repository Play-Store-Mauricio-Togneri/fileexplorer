#!/usr/bin/env python3
"""
Reporting pass for the `audit-tests` skill.

`check_tests.py` is the CI guard: it fails the build on four unambiguous defects. This script is the
opposite — no finding it prints ever fails the run, and every line it prints is a *candidate* that
needs a human or an agent to judge. Keep the two separate: a heuristic that becomes reliable enough
to block on should move into check_tests.py, and everything else belongs here. It exits non-zero
only on an unknown section name and on a source root holding no Kotlin files — an audit that
inspected nothing otherwise reads exactly like an audit that found nothing.

Usage:  python3 scripts/audit_tests.py [section ...]
Sections: orphans coverage duplicates skips fixtures assertions   (default: all)
"""

from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

# This script reads UTF-8 Kotlin sources and prints UTF-8 guidance, so it must not depend on the
# process locale: under LC_ALL=C the reads would raise UnicodeDecodeError and the prints
# UnicodeEncodeError. Reads pass encoding= explicitly; the output streams are pinned here.
for _stream in (sys.stdout, sys.stderr):
    _stream.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main/java"
ANDROID_TEST = ROOT / "app/src/androidTest/java"
UNIT_TEST = ROOT / "app/src/test/java"

BOLD, DIM, RESET = "\033[1m", "\033[2m", "\033[0m"

# Risk ordering for the coverage gap report. A defect in the first group destroys user data; a
# defect in the last is cosmetic. Coverage is not worth chasing uniformly.
RISK = [
    ("parses untrusted bytes", ("data/util/",), (
        "MetadataExtractor", "ThumbnailFetcher", "Extractor", "Fetcher")),
    ("irreversible file operations", ("data/repository/", "util/"), ()),
    ("business logic", ("ui/screens/",), ("ViewModel", "UiState")),
    ("navigation", ("ui/navigation/",), ()),
    ("reusable UI", ("ui/components/",), ()),
]


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def kotlin(root: Path) -> list[Path]:
    return sorted(root.rglob("*.kt")) if root.exists() else []


def package_of(text: str) -> str:
    match = re.search(r"^package\s+([\w.]+)", text, re.M)
    return match.group(1) if match else ""


def declared_symbols(text: str) -> set[str]:
    """
    Top-level class/object/interface/fun/val names a Kotlin file declares.

    The receiver of an extension is skipped so that `fun Throwable.isNoSpaceLeft()` counts as
    `isNoSpaceLeft`, not `Throwable`. Capturing the receiver instead made `DiskSpaceTest` and
    `LanguageUtilTest` — which exercise nothing but extensions — look like they touched no
    production symbol at all.
    """
    return set(
        re.findall(
            r"^(?:internal |private |public )?(?:@\w+\s+)*"
            r"(?:data |sealed |abstract |open |enum |value )*"
            r"(?:class|object|interface|fun|val)\s+"
            r"(?:<[^>]+>\s*)?(?:[A-Za-z_]\w*(?:<[^>]*>)?\.)?([A-Za-z_]\w*)",
            text,
            re.M,
        )
    )


def load_production() -> dict[str, dict]:
    """Every production file with its package and the symbols it declares."""
    production = {}
    for path in kotlin(MAIN):
        text = path.read_text(encoding="utf-8")
        production[rel(path)] = {
            "path": path,
            "package": package_of(text),
            "symbols": declared_symbols(text),
        }
    return production


def load_tests() -> list[dict]:
    tests = []
    for root in (ANDROID_TEST, UNIT_TEST):
        for path in root.rglob("*Test.kt"):
            text = path.read_text(encoding="utf-8")
            tests.append({
                "path": path,
                "rel": rel(path),
                "text": text,
                "package": package_of(text),
                "instrumentation": root is ANDROID_TEST,
            })
    return sorted(tests, key=lambda t: t["rel"])


def heading(title: str, note: str) -> None:
    print(f"\n{BOLD}## {title}{RESET}")
    print(f"{DIM}{note}{RESET}\n")


# ---------------------------------------------------------------------------


def report_orphans(production: dict, tests: list[dict]) -> None:
    """
    Test files that never touch a production symbol.

    Same-package tests need no import, so an import count alone is misleading — the check is whether
    any production symbol name appears anywhere in the file body. A test that references none is
    asserting against something it built itself.
    """
    heading(
        "Test files referencing no production symbol",
        "These assert against something the test itself declares. Verify each one actually\n"
        "exercises the app; a UI test that touches nothing from main/ cannot fail for a\n"
        "production reason.",
    )

    all_symbols = set()
    for info in production.values():
        all_symbols |= info["symbols"]
    # Names too generic to be evidence of anything.
    all_symbols -= {"Factory", "Companion", "create", "of", "from", "value", "state", "context"}

    found_any = False
    for test in tests:
        body = re.sub(r"^import .*$", "", test["text"], flags=re.M)
        body = re.sub(r"^package .*$", "", body, flags=re.M)
        referenced = {s for s in all_symbols if re.search(rf"\b{re.escape(s)}\b", body)}
        # R and the theme wrapper are present in every UI test and prove nothing.
        referenced -= {"R", "FileExplorerTheme", "ThemeMode"}
        if not referenced:
            found_any = True
            print(f"  {test['rel']}")
    if not found_any:
        print("  none")


def report_coverage(production: dict, tests: list[dict]) -> None:
    """Production files no test names, grouped by how much a defect there would cost."""
    heading(
        "Production code no test references",
        "Ranked by blast radius, not by line count. Work top-down; the first group parses\n"
        "files the user did not create and has already caused data loss once.\n"
        "Caveat: this matches by name, so anything reached only through a factory or a\n"
        "registry — the thumbnail fetchers via AppImageLoader, for instance — appears here\n"
        "even when it is covered end to end. Confirm before writing a new test.",
    )

    corpus = "\n".join(t["text"] for t in tests)
    uncovered = defaultdict(list)

    for rel_path, info in production.items():
        stem = info["path"].stem
        if stem in ("Color", "Type", "Theme"):
            continue  # palette/typography constants, asserted through ThemeRenderingTest
        if re.search(rf"\b{re.escape(stem)}\b", corpus):
            continue
        short = rel_path.replace("app/src/main/java/com/mauriciotogneri/fileexplorer/", "")
        for label, prefixes, hints in RISK:
            if short.startswith(prefixes) and (not hints or any(h in stem for h in hints)):
                uncovered[label].append(short)
                break
        else:
            uncovered["other"].append(short)

    if not uncovered:
        print("  none")
        return
    for label, _, _ in RISK + [("other", (), ())]:
        entries = uncovered.get(label)
        if entries:
            print(f"  {BOLD}{label}{RESET}")
            for entry in sorted(entries):
                print(f"    {entry}")


# Kotlin allows backticked test names, which the unit suite uses heavily.
TEST_FUN = re.compile(r"@Test[^\n]*\n(?:\s*@\w+[^\n]*\n)*\s*fun\s+(`[^`]+`|[A-Za-z_]\w*)")


def report_duplicates(tests: list[dict]) -> None:
    """
    The same @Test name in two files usually means the same scenario is paid for twice on every
    emulator run. Diff the two before deleting either — one often has a case the other lacks.
    """
    heading(
        "Duplicated @Test names across files",
        "Same name, two files: likely the same scenario covered twice. Confirm by reading\n"
        "both, carry over anything unique, then delete one.",
    )
    owners = defaultdict(list)
    for test in tests:
        for name in TEST_FUN.findall(test["text"]):
            owners[name].append(test["rel"])

    duplicates = {n: f for n, f in owners.items() if len(f) > 1}
    if not duplicates:
        print("  none")
        return
    for name in sorted(duplicates):
        print(f"  {name}")
        for owner in duplicates[name]:
            print(f"    {owner}")


def report_skips(tests: list[dict]) -> None:
    """
    Every skip is a claim. `assumeTrue` says "this device cannot run it"; `@Retry` says "this failure
    is emulator load, not a bug". Both report green while verifying nothing, so both need re-reading
    whenever the fleet or the code changes.
    """
    heading(
        "Skips and retries",
        "Each of these reports as passing without necessarily running. Re-check that every\n"
        "condition still varies, and that every @Retry is a load artifact rather than a real\n"
        "intermittent bug being papered over.",
    )
    pattern = re.compile(r"^\s*(@Ignore|@Retry\b|.*\bassume(?:True|False)\()", re.M)
    found = False
    for test in tests:
        for n, line in enumerate(test["text"].splitlines(), 1):
            if pattern.match(line) and "import" not in line:
                found = True
                print(f"  {test['rel']}:{n}: {line.strip()}")
    if not found:
        print("  none")


def report_fixtures(tests: list[dict]) -> None:
    """A temp directory created without an @After that removes it fills the device over a full run."""
    heading(
        "Fixtures without cleanup",
        "A test creating a temp directory needs an @After that deletes it.",
    )
    found = False
    for test in tests:
        creates = re.search(r"(mkdirs\(\)|createTempDir|cacheDir)", test["text"])
        cleans = "deleteRecursively" in test["text"] or "@After" in test["text"]
        if creates and not cleans:
            found = True
            print(f"  {test['rel']}")
    if not found:
        print("  none")


# Anything that can fail a test: JUnit, Espresso-Intents, MockK's verify in both its call and brace
# forms, Turbine's await*/expect* (which throw when the flow does not produce what the test claims),
# and waitUntil (which throws on timeout).
#
# Recognising only `verify(`/`coVerify(` made every entry in the assertions section a false positive
# — this codebase writes `coVerify { … }` throughout — and a section that is all noise is one nobody
# reads.
ASSERTION = re.compile(
    r"\bassert|AssertionError|\bintended\(|\b(?:co)?[Vv]erify\s*[({]|assertThrows|\bfail\("
    r"|\bexpectNoEvents\(|\bexpectMostRecentItem\(|\bawait(?:Item|Error|Complete)\(|\bwaitUntil\("
)


def _block_at(lines: list[str], start: int) -> str:
    """The brace-balanced block beginning at or after [start]."""
    depth, body, started = 0, [], False
    for line in lines[start:]:
        depth += line.count("{") - line.count("}")
        body.append(line)
        if "{" in line:
            started = True
        if started and depth <= 0:
            break
    return "\n".join(body)


def _asserting_helpers(text: str) -> set[str]:
    """Private helpers in this file that assert, so a test delegating to one still counts."""
    lines = text.splitlines()
    helpers = set()
    for i, line in enumerate(lines):
        match = re.match(r"\s*private fun\s+([A-Za-z_]\w*)", line)
        if match and ASSERTION.search(_block_at(lines, i)):
            helpers.add(match.group(1))
    return helpers


def report_assertions(tests: list[dict]) -> None:
    """
    A @Test body with no assertion of any kind. Compose's perform* calls throw when a node is
    missing, so such a test is not always empty — but it is asserting reachability only, and the name
    usually promises more.

    A test that delegates to an asserting helper in the same file is not flagged; that is a shared
    scenario, not a missing check.
    """
    heading(
        "@Test bodies with no assertion",
        "At best these assert 'the node existed'. Read each one against its name: if the name\n"
        "promises a behaviour, the assertion is missing. Tests delegating to an asserting\n"
        "helper in the same file are already excluded.\n"
        "A test whose whole point is 'this does not throw' legitimately has no assertion —\n"
        "keep it, but make the name say so, so the next reader does not file it as a defect.",
    )
    found = False
    for test in tests:
        lines = test["text"].splitlines()
        helpers = _asserting_helpers(test["text"])
        for i, line in enumerate(lines):
            if not line.strip().startswith("@Test"):
                continue
            body = _block_at(lines, i)
            if ASSERTION.search(body):
                continue
            if any(re.search(rf"\b{re.escape(h)}\s*\(", body) for h in helpers):
                continue
            name = TEST_FUN.search(body)
            found = True
            print(f"  {test['rel']}: {name.group(1) if name else '<unnamed>'}")
    if not found:
        print("  none")


SECTIONS = {
    "orphans": lambda p, t: report_orphans(p, t),
    "coverage": lambda p, t: report_coverage(p, t),
    "duplicates": lambda p, t: report_duplicates(t),
    "skips": lambda p, t: report_skips(t),
    "fixtures": lambda p, t: report_fixtures(t),
    "assertions": lambda p, t: report_assertions(t),
}


def main(argv: list[str]) -> int:
    requested = argv[1:] or list(SECTIONS)
    unknown = [s for s in requested if s not in SECTIONS]
    if unknown:
        print(f"Unknown section(s): {', '.join(unknown)}")
        print(f"Available: {', '.join(SECTIONS)}")
        return 2

    # rglob on a missing directory yields nothing rather than raising, so a moved or renamed
    # source set would drop out of every section silently, and a report over half the tree reads
    # exactly like a report over all of it.
    empty = [rel(root) for root in (MAIN, ANDROID_TEST, UNIT_TEST) if not kotlin(root)]
    if empty:
        print(f"{BOLD}No Kotlin sources under {', '.join(empty)} — "
              f"the audit would report on an incomplete tree.{RESET}")
        return 1

    production = load_production()
    tests = load_tests()
    instrumentation = sum(1 for t in tests if t["instrumentation"])
    print(f"{BOLD}Test suite audit{RESET}")
    print(
        f"{DIM}{len(tests)} test files "
        f"({instrumentation} instrumentation, {len(tests) - instrumentation} unit) "
        f"over {len(production)} production files{RESET}"
    )

    for section in requested:
        SECTIONS[section](production, tests)

    print(f"\n{DIM}Every line above is a candidate, not a verdict. Judge each one.{RESET}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
