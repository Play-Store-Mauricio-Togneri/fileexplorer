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


def blank(text: str, strings: bool = False) -> str:
    """
    Source with comment and raw-string bodies — and, when `strings` is set, double-quoted string and
    character literals too — replaced by spaces.

    The guards below match across lines, because a matcher wrapped over three lines is the same
    matcher. Once they do, "skip lines that look like comments" no longer holds: the same text is an
    assertion in code, documentation in a KDoc block, and data inside a literal. Raw strings hold
    fixture payloads and are never a matcher's argument, so they are blanked for every caller; the
    one guard that reads literals — the hardcoded-string check — keeps only the double-quoted ones.
    Blanking is character-for-character and keeps newlines, so every offset and line number still
    addresses the original file.
    """
    out = list(text)
    end = len(text)

    def erase(start: int, stop: int) -> None:
        for k in range(start, stop):
            if out[k] != "\n":
                out[k] = " "

    i = 0
    while i < end:
        pair = text[i:i + 2]
        if pair == "//":
            stop = text.find("\n", i)
            stop = end if stop < 0 else stop
            erase(i, stop)
            i = stop
        elif pair == "/*":
            depth, j = 1, i + 2  # Kotlin block comments nest.
            while j < end and depth:
                if text[j:j + 2] == "/*":
                    depth, j = depth + 1, j + 2
                elif text[j:j + 2] == "*/":
                    depth, j = depth - 1, j + 2
                else:
                    j += 1
            erase(i, j)
            i = j
        elif text.startswith('"""', i):
            stop = text.find('"""', i + 3)
            stop = end if stop < 0 else stop + 3
            erase(i, stop)
            i = stop
        elif text[i] in "\"'":
            quote, j = text[i], i + 1
            while j < end and text[j] != quote:
                if text[j] == "\\":
                    j += 2
                elif quote == '"' and text[j:j + 2] == "${":
                    # A template expression can hold quotes of its own; skip to its closing
                    # brace so they do not end the literal early.
                    depth, j = 1, j + 2
                    while j < end and depth:
                        depth += {"{": 1, "}": -1}.get(text[j], 0)
                        j += 1
                else:
                    j += 1
            j = min(j + 1, end)
            if strings:
                erase(i, j)
            i = j
        else:
            i += 1
    return "".join(out)


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def snippet(text: str, match: re.Match) -> str:
    """The matched source collapsed onto one line, since the match itself may span several."""
    return " ".join(match.group(0).split())


# `@Composable` attached to a declaration, wherever it sits among the modifiers: `@Composable` on
# its own line and `private @Composable fun` declare the same thing. Requiring `fun` after the
# modifier run is what separates a declaration from a function *type* — a helper that takes
# `content: @Composable () -> Unit` receives the production composable rather than replacing it.
COMPOSABLE_DECLARATION = re.compile(
    r"@Composable\b"
    r"(?:\s*(?:@\w+(?:\([^)]*\))?|private|internal|public|protected|inline|suspend|actual|expect"
    r"|open|override|abstract|final|tailrec|operator|infix|external))*"
    r"\s*\bfun\b"
)


def check_no_test_composables() -> bool:
    """
    A test that declares its own composable asserts against a copy of the UI. The copy cannot
    fail when production changes, which is the opposite of what an instrumentation test is for.

    Matches the annotation only in code position, so prose in a KDoc block explaining this
    history does not trip the check.
    """
    hits = []
    for path in kotlin_files():
        text = path.read_text()
        for match in COMPOSABLE_DECLARATION.finditer(blank(text, strings=True)):
            hits.append(f"{rel(path)}:{line_of(text, match.start())}")
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


# `%d`, `%s`, and their positional forms `%1$d` / `%2$s`. A resource holding one of these is a
# format string: a test asserts the *substituted* form, so the literal itself never matches and only
# a pattern can catch it.
PLACEHOLDER = re.compile(r"%(?:\d+\$)?([ds])")


def _format_pattern(text: str) -> str | None:
    """
    A format string as a regex, so the substituted form a test writes is matched too.

    Returns None when the resource is nothing but placeholders and padding — "%s" alone compiles to
    `^.+$`, which matches every literal in the suite and turns this check into a wall of noise. Such
    a resource carries no words to key on, so there is nothing here to catch.
    """
    parts: list[str] = []
    literal = []
    last = 0
    for match in PLACEHOLDER.finditer(text):
        parts.append(re.escape(text[last:match.start()]))
        literal.append(text[last:match.start()])
        parts.append(r"\d+" if match.group(1) == "d" else ".+")
        last = match.end()
    parts.append(re.escape(text[last:]))
    literal.append(text[last:])
    if not "".join(literal).strip():
        return None
    return "".join(parts)


def translatable_strings() -> tuple[set[str], list[re.Pattern]]:
    """
    Every user-facing literal that has a resource, so assertions can be checked against it, plus a
    pattern per format string for the substituted forms tests actually write.
    """
    root = ET.parse(STRINGS_XML).getroot()
    values: set[str] = set()
    patterns: set[str] = set()
    for node in root.iter():
        if node.tag not in ("string", "plurals"):
            continue
        if node.get("translatable") == "false":
            continue
        name = node.get("name") or ""
        filesystem_name = name.startswith(FILESYSTEM_NAME_PREFIXES)
        targets = [node] if node.tag == "string" else list(node)
        for target in targets:
            text = "".join(target.itertext()).strip()
            if not text:
                continue
            # Resource escaping: \' \" and the literal backslash-escapes used in strings.xml.
            text = text.replace("\\'", "'").replace('\\"', '"').replace("\\\\", "\\")
            if PLACEHOLDER.search(text):
                # A format string is never a folder name, so the filesystem-name exclusion — which
                # exists for plain display names like "SD Card" — must not cover it. Skipping these
                # is how `onNodeWithText("29.8 GB available")` survived: it is `storage_available`
                # ("%s verfügbar" in German) rendered through a locale-dependent DecimalFormat.
                pattern = _format_pattern(text)
                if pattern:
                    patterns.add(pattern)
            elif not filesystem_name:
                values.add(text)
    return values, [re.compile(f"^{p}$") for p in patterns]


# Everything that turns a literal into an assertion. Content descriptions come from strings.xml
# exactly as visible text does, so a literal is as locale-dependent in one as in the other.
STRING_MATCHERS = (
    "onNodeWithText",
    "onAllNodesWithText",
    "hasText",
    "onNodeWithContentDescription",
    "onAllNodesWithContentDescription",
    "hasContentDescription",
    "assertTextEquals",
    "assertTextContains",
    "assertContentDescriptionEquals",
)

# Matched against whole files rather than single lines: the argument of a matcher wrapped onto its
# own line is still the matcher's argument, and a per-line search never sees it.
STRING_MATCHER = re.compile(
    r"(?:" + "|".join(STRING_MATCHERS) + r')\(\s*"((?:[^"\\]|\\.)*)"'
)


def check_no_hardcoded_ui_strings() -> bool:
    """
    onNodeWithText("Share").assertDoesNotExist() passes on every non-English device whether or
    not Share is shown, because the literal simply never matches.

    Flags a literal when strings.xml defines that exact text, or when it is the substituted form of
    a resource holding a `%d` / `%s` placeholder. Test-owned data (file names, typed input, values
    formatted by production under an explicit `Locale.US` like "1920 x 1080 px") is not translated
    and is correctly written inline.
    """
    literal_values, format_patterns = translatable_strings()
    hits = []
    for path in kotlin_files():
        text = path.read_text()
        for match in STRING_MATCHER.finditer(blank(text)):
            decoded = match.group(1).replace('\\"', '"').replace("\\\\", "\\")
            if decoded in literal_values or any(p.match(decoded) for p in format_patterns):
                hits.append(f"{rel(path)}:{line_of(text, match.start())}: {snippet(text, match)}")
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


# Package roots a test only reaches by calling into the platform. The bare substring `android.`
# stood here and let a *package name* qualify a test as needing a device: a pure-JVM test that says
# `"com.android.vending"` to build a store intent touches no Android API at all. Literals are
# blanked before this runs for the same reason, so both halves of that mistake are closed.
ANDROID_API = re.compile(
    r"composeTestRule|InstrumentationRegistry|androidx\.test\.|Instrumentation\b|"
    r"\bandroid\.(?:app|content|database|graphics|hardware|media|net|os|provider|system|text"
    r"|util|view|webkit|widget|Manifest)\b"
)


def check_instrumentation_tests_need_a_device() -> bool:
    """
    A test that touches no Android API and renders no UI belongs in app/src/test, where it runs
    in seconds instead of waiting on an emulator.
    """
    hits = []
    for path in ANDROID_TEST.rglob("*Test.kt"):
        if not ANDROID_API.search(blank(path.read_text(), strings=True)):
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
