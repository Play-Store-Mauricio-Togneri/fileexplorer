#!/usr/bin/env python3
"""
Structural guards for the instrumentation suite.

These exist because the suite once accumulated ~8k lines of tests that asserted against
private @Composable replicas of production screens rather than the screens themselves.
Those tests stayed green while production drifted away from them. The checks below make
that failure mode, and the three others found alongside it, impossible to reintroduce
silently.

Run via scripts/check-tests.sh (which scripts/test.sh calls before the emulator run).
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ANDROID_TEST = ROOT / "app/src/androidTest/java"
STRINGS_XML = ROOT / "app/src/main/res/values/strings.xml"

RED, GREEN, DIM, RESET = "\033[31m", "\033[32m", "\033[2m", "\033[0m"


class Failure(Exception):
    pass


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def kotlin_files() -> list[Path]:
    return sorted(ANDROID_TEST.rglob("*.kt"))


def report(title: str, hits: list[str], why: list[str]) -> bool:
    print(f"==> {title}")
    if not hits:
        print(f"{GREEN}OK{RESET}")
        return True
    print(f"{RED}FAIL{RESET}")
    for line in why:
        print(f"{DIM}     {line}{RESET}")
    for hit in hits:
        print(f"       {hit}")
    return False


def check_no_test_composables() -> bool:
    """
    A test that declares its own composable asserts against a copy of the UI. The copy cannot
    fail when production changes, which is the opposite of what an instrumentation test is for.

    Matches the annotation only in code position, so prose in a KDoc block explaining this
    history does not trip the check.
    """
    pattern = re.compile(r"^\s*@Composable\b")
    hits = []
    for path in kotlin_files():
        for n, line in enumerate(path.read_text().splitlines(), 1):
            if pattern.match(line):
                hits.append(f"{rel(path)}:{n}")
    return report(
        "No test-local @Composable declarations",
        hits,
        [
            "Tests must render the production composable, not a local copy of it.",
            "If the target is private, make it 'internal' as a test seam.",
        ],
    )


# `location_*` and `storage_*` resources are display names for well-known folders ("Documents",
# "Downloads", "SD Card"). Components that render a path segment — Breadcrumbs, the picker list —
# show the on-disk name, not the resource, and a fixture folder is legitimately called "Documents".
# Excluding these prefixes keeps the check on UI chrome, where a literal is genuinely wrong.
FILESYSTEM_NAME_PREFIXES = ("location_", "storage_")

# A comment mentioning a literal is documentation, not an assertion.
COMMENT = re.compile(r"^\s*(\*|//|/\*)")


def translatable_strings() -> set[str]:
    """Every user-facing literal that has a resource, so assertions can be checked against it."""
    root = ET.parse(STRINGS_XML).getroot()
    values: set[str] = set()
    for node in root.iter():
        if node.tag not in ("string", "plurals"):
            continue
        if node.get("translatable") == "false":
            continue
        name = node.get("name") or ""
        if name.startswith(FILESYSTEM_NAME_PREFIXES):
            continue
        targets = [node] if node.tag == "string" else list(node)
        for target in targets:
            text = "".join(target.itertext()).strip()
            if not text:
                continue
            # Resource escaping: \' \" and the literal backslash-escapes used in strings.xml.
            text = text.replace("\\'", "'").replace('\\"', '"').replace("\\\\", "\\")
            values.add(text)
            # Plurals are stored with a %d placeholder; tests write the substituted form.
            if "%d" in text:
                values.add(re.sub(r"%d", r"\\d+", re.escape(text)).replace(r"\ ", " "))
    return values


def check_no_hardcoded_ui_strings() -> bool:
    """
    onNodeWithText("Share").assertDoesNotExist() passes on every non-English device whether or
    not Share is shown, because the literal simply never matches.

    Flags a literal only when strings.xml actually defines that exact text — test-owned data
    (file names, typed input, formatted values like "1920 x 1080 px") is not translated and is
    correctly written inline.
    """
    resources = translatable_strings()
    plural_patterns = [re.compile(f"^{p}$") for p in resources if r"\d+" in p]
    literal_values = {v for v in resources if r"\d+" not in v}
    matcher = re.compile(r'(?:onNodeWithText|onAllNodesWithText|hasText)\(\s*"((?:[^"\\]|\\.)*)"')
    hits = []
    for path in kotlin_files():
        for n, line in enumerate(path.read_text().splitlines(), 1):
            if COMMENT.match(line):
                continue
            for literal in matcher.findall(line):
                decoded = literal.replace('\\"', '"').replace("\\\\", "\\")
                if decoded in literal_values or any(p.match(decoded) for p in plural_patterns):
                    hits.append(f"{rel(path)}:{n}: {line.strip()}")
    return report(
        "No hardcoded user-facing strings in matchers",
        hits,
        [
            "Use getString(R.string.…) / getQuantityString(R.plurals.…) instead.",
            "A literal makes the assertion locale-dependent, and assertDoesNotExist()",
            "on a literal is a guaranteed false pass off-locale.",
        ],
    )


# Contexts in which the value of a `fetchSemanticsNodes()` expression is genuinely consumed.
CONSUMING = re.compile(
    r"waitUntil|runCatching|assert|\bval\b|\bvar\b|\breturn\b|->|=\s*$|\bfun\b|\bif\b|\bwhile\b|&&|\|\|"
)


def check_no_discarded_assertions() -> bool:
    """
    `onAllNodesWithText(x).fetchSemanticsNodes().isNotEmpty()` written as a bare statement
    computes a boolean and throws it away, so the test cannot fail.

    A `waitUntil { … }` lambda body or an assignment does consume the value; only an
    unconsumed statement is a defect, so the two preceding lines are inspected for a
    consuming construct before flagging.
    """
    target = re.compile(r"fetchSemanticsNodes\(\)\.(isNotEmpty|isEmpty|size)")
    hits = []
    for path in kotlin_files():
        lines = path.read_text().splitlines()
        for i, line in enumerate(lines):
            if not target.search(line):
                continue
            window = " ".join(lines[max(0, i - 2): i + 1])
            if CONSUMING.search(window):
                continue
            hits.append(f"{rel(path)}:{i + 1}: {line.strip()}")
    return report(
        "No discarded assertion results",
        hits,
        ["Wrap it in assertTrue(...) / assertFalse(...) or it can never fail."],
    )


ANDROID_API = re.compile(
    r"composeTestRule|InstrumentationRegistry|androidx\.test\.|Instrumentation\b|android\."
)


def check_instrumentation_tests_need_a_device() -> bool:
    """
    A test that touches no Android API and renders no UI belongs in app/src/test, where it runs
    in seconds instead of waiting on an emulator.
    """
    hits = []
    for path in ANDROID_TEST.rglob("*Test.kt"):
        if not ANDROID_API.search(path.read_text()):
            hits.append(rel(path))
    return report(
        "Instrumentation tests need a device",
        sorted(hits),
        ["This test uses no Android API — move it to app/src/test."],
    )


def main() -> int:
    checks = [
        check_no_test_composables,
        check_no_hardcoded_ui_strings,
        check_no_discarded_assertions,
        check_instrumentation_tests_need_a_device,
    ]
    ok = all([check() for check in checks])
    print()
    if not ok:
        print(f"{RED}Test structure checks failed.{RESET}")
        return 1
    print(f"{GREEN}All test structure checks passed.{RESET}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
