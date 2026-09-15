#!/usr/bin/env python3
"""
Structural guards for the instrumentation suite.

These exist because the suite once accumulated ~8k lines of tests that asserted against
private @Composable replicas of production screens rather than the screens themselves.
Those tests stayed green while production drifted away from them. The checks below make
that failure mode, and the four others found alongside it, impossible to reintroduce
silently.

Run via scripts/check-tests.sh (which scripts/test.sh calls before the emulator run).
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# This script reads UTF-8 Kotlin sources and prints UTF-8 guidance, so it must not depend on the
# process locale: under LC_ALL=C the reads would raise UnicodeDecodeError and the prints
# UnicodeEncodeError. Reads pass encoding= explicitly; the output streams are pinned here.
for _stream in (sys.stdout, sys.stderr):
    _stream.reconfigure(encoding="utf-8", errors="replace")

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
    Source with comment, raw-string and backtick-quoted-name bodies — and, when `strings` is set,
    double-quoted string and character literals too — replaced by spaces.

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
        elif text[i] == "`":
            # A backtick-quoted declaration name holds prose, not code, so an apostrophe in one
            # ("fun `it doesn't crash`()") must not be read as opening a character literal: left
            # interpreted, the scan would run to the next apostrophe in the file — or to EOF —
            # and erase every violation in between, and the guard would then print OK for a file
            # it had not read. Such a name cannot span a newline, so an unterminated backtick is
            # bounded by its own line rather than swallowing the rest of the file.
            stop = text.find("`", i + 1)
            line_end = text.find("\n", i + 1)
            line_end = end if line_end < 0 else line_end
            stop = line_end if stop < 0 or stop > line_end else stop + 1
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
        text = path.read_text(encoding="utf-8")
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


# Empty, and meant to stay that way. Both prefixes that once stood here were removed for the same
# reason, a release apart.
#
# `storage_` went first. Nothing on disk is called "Internal Storage": `Breadcrumbs` resolves the
# root segment itself with `stringResource(R.string.storage_internal)`, and `AndroidStorageSource`
# falls back to `storage_internal` / `storage_sd_card` for a volume with no description — so both
# are translated chrome.
#
# `location_` looked like the safe half of that pair and was not. The argument for it was that a
# component rendering a path segment shows the on-disk name, so a fixture folder the test created is
# legitimately called "Documents" — true as far as it goes, but the exclusion is scoped to the
# *value*, not to the file or the call. `location_documents`, `location_images`, `location_audio`
# and the rest are live UI chrome: `Location` renders them in the home Locations section,
# `AnalyzerCategory` in the analyzer, and `SearchFiltersBar` on the search type chips. Excluding the
# value deleted those words from this check's vocabulary everywhere, so an `onNodeWithText("Images")`
# aimed at a filter chip would have passed on all twenty locales with nothing to catch it. Three
# `assertDoesNotExist()` calls were already sitting inside that blind spot.
#
# The fixtures that needed the exclusion were renamed instead — "Ledgers", "Parcels" — to names no
# `<string>` value defines. A test that needs a folder name picks one production does not translate;
# a test that needs a *resource* asks for the resource, exactly as production does.
FILESYSTEM_NAME_PREFIXES = ()


# `%d`, `%s`, and their positional forms `%1$d` / `%2$s`. A resource holding one of these is a
# format string: a test asserts the *substituted* form, so the literal itself never matches and only
# a pattern can catch it.
PLACEHOLDER = re.compile(r"%(?:\d+\$)?([ds])")


def _format_pattern(text: str) -> str | None:
    r"""
    A format string as a regex, so the substituted form a test writes is matched too.

    Returns None when what surrounds the placeholders pins nothing down, because such a pattern
    matches half the literals in the suite and turns this check into a wall of noise:

    * "%s" alone compiles to `^.+$`, which matches every literal there is.
    * "%1$s%%" compiles to `^.+%$`, which matches every percentage there is — including "75.0%",
      which production formats under an explicit `Locale.US` with no resource behind it, and which
      this check's own docstring calls correctly written inline.

    A `%d` is an anchor of its own — it pins a digit in a fixed position — so a resource holding one
    is kept as soon as anything at all separates its placeholders: `^\d+ / \d+$` and `^\d+°$` are
    selective enough to be worth having, while "%d" alone matches every number in the suite.

    The rule is broader than the `%%` case that prompted it: a `%s`-only resource surrounded by
    punctuation — "%1$s: %2$s", "%1$s (%2$s)" — drops out too. That is the intended reading. Such a
    pattern says nothing but "two things with a colon between them", and a guard that fires on every
    literal of that shape is one nobody can act on.
    """
    parts: list[str] = []
    literal = []
    digits = False
    last = 0
    for match in PLACEHOLDER.finditer(text):
        literal.append(_decoded_percents(text[last:match.start()]))
        parts.append(re.escape(literal[-1]))
        digits = digits or match.group(1) == "d"
        parts.append(r"\d+" if match.group(1) == "d" else ".+")
        last = match.end()
    literal.append(_decoded_percents(text[last:]))
    parts.append(re.escape(literal[-1]))
    around = "".join(literal)
    pinned = any(char.isalnum() for char in around) or (digits and around != "")
    if not pinned:
        return None
    return "".join(parts)


def _decoded_percents(text: str) -> str:
    """
    `%%` decoded to the single percent sign it stands for.

    Only ever called on what is left between the placeholders: decoded any earlier, "%%s" would be
    read as a placeholder rather than as a percent sign followed by an "s".
    """
    return text.replace("%%", "%")


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
        text = path.read_text(encoding="utf-8")
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
# `if`, `while` and `assertTrue` wrap the expression in parentheses and `waitUntil` in a lambda, so
# these are looked for anywhere ahead of it, at any nesting depth.
CONSUMING_KEYWORD = re.compile(
    r"waitUntil|runCatching|assert|\bval\b|\bvar\b|\breturn\b|\bif\b|\bwhile\b"
)

# An assignment — including an expression body, `fun ready() = …` — or a `when` branch. Read
# outside brackets only: `onAllNodesWithText(text, useUnmergedTree = true)` carries a named
# argument rather than an assignment, and `filter { it -> … }` a lambda header rather than a branch.
CONSUMING_OPERATOR = re.compile(r"(?<![=!<>])=(?!=)|->")

# A line the statement continues past. `{` is deliberately absent, and so is a trailing `->`:
# `fun someTest() {` and `use { scenario ->` open a body rather than continue an expression, and
# reading them as one is what let a discarded result borrow the enclosing declaration's keywords.
CONTINUES = re.compile(r"(?:[=(,.+]|&&|\|\|)\s*$")

# Lines that continue the one above them whatever it ended with: a chained call, and the closing
# bracket of an argument list that was wrapped.
CONTINUATION_START = (".", "?.", ")", "}")

# Calls taking a lambda whose last expression is its value.
CONSUMING_BLOCK = re.compile(r"\b(?:waitUntil|runCatching)\b")

# Where the search outward for such a call stops: past a declaration there is no enclosing
# expression left that could consume anything.
DECLARATION = re.compile(r"\bfun\b|\bclass\b|\bobject\b|\binit\b")

# Comments are erased rather than filled (see `_opaque_literals`), so a line holding nothing but one
# is blank for the purpose of finding the brace that closes a block.
FILLER = " \t_"


def _statement_bounds(lines: list[str], index: int) -> tuple[int, int]:
    """
    First and last line of the statement `lines[index]` belongs to.

    Backwards, a line opening with `)` or `}` is a continuation whatever precedes it — that is the
    tail of a wrapped argument list. Forwards it is not: the `}` below a statement usually closes
    the block around it, and swallowing it would hide the block's end from the caller.
    """
    start = index
    while start > 0 and (
        lines[start].lstrip().startswith(CONTINUATION_START) or CONTINUES.search(lines[start - 1])
    ):
        start -= 1
    end = index
    while end + 1 < len(lines) and (
        lines[end + 1].lstrip().startswith((".", "?.")) or CONTINUES.search(lines[end])
    ):
        end += 1
    return start, end


def _outside_brackets(text: str) -> str:
    """`text` with the contents of every bracketed group — arguments, indexes, lambdas — removed."""
    depth = 0
    kept = []
    for char in text:
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth = max(0, depth - 1)
        elif depth == 0:
            kept.append(char)
    return "".join(kept)


def _opaque_literals(text: str) -> str:
    """
    Source with the body of every literal — quoted, character and raw — reduced to `_`, so it can
    hold neither a brace nor an operator, yet still ends a line where it ends one.

    `blank()` erases a literal outright, which leaves `val label = "x"` looking like a line ending
    in `=` — a continuation — and the `val` would then be read as consuming the statement below it.
    Comments stay erased, because a trailing `// note` does leave the operator before it line-final.
    What opens each erased run in the original source is what tells the two apart.
    """
    blanked = list(blank(text, strings=True))
    inside = opaque = False
    for i, char in enumerate(blanked):
        if char != " ":
            inside = False
        elif text[i] != " ":
            if not inside:
                inside, opaque = True, not text.startswith(("//", "/*"), i)
            if opaque:
                blanked[i] = "_"
    return "".join(blanked)


def _enclosing_blocks(lines: list[str], index: int) -> list[str]:
    """
    The lines opening the blocks around `lines[index]`, innermost first, each rejoined with the
    lines it wraps over so that a `waitUntil(` split from its `) {` is still one opener.
    """
    openers = []
    depth = 0
    for candidate in range(index - 1, -1, -1):
        depth += lines[candidate].count("}") - lines[candidate].count("{")
        if depth < 0:
            opened = _statement_bounds(lines, candidate)[0]
            openers.append(" ".join(part.strip() for part in lines[opened:candidate + 1]))
            depth = 0
    return openers


def check_no_discarded_assertions() -> bool:
    """
    `onAllNodesWithText(x).fetchSemanticsNodes().isNotEmpty()` written as a bare statement
    computes a boolean and throws it away, so the test cannot fail.

    The value is used when the statement holding it assigns, asserts or branches on it, or when it
    is the last expression of a `waitUntil { … }` or `runCatching { … }` lambda — the block that
    returns it may sit several `if`s out. So the statement is rebuilt from its continuation lines
    and read as a whole, then the blocks around it are walked outward. Sampling the lines above the
    match instead, as this once did, cannot tell `waitUntil {` from `fun someTest() {`, and the
    `fun` line then excused the defect's most common shape — the first statement of a test.

    The chain is matched against that rebuilt statement, not against one physical line, because a
    wrapped `.isNotEmpty()` is what a long chain turns into — the same defect, one line break wider.
    """
    # Tolerant of the space the rebuild leaves where the line break was.
    target = re.compile(r"fetchSemanticsNodes\(\)\s*\.\s*(?:isNotEmpty|isEmpty|size)")
    hits = []
    for path in kotlin_files():
        text = path.read_text(encoding="utf-8")
        # Read against the neutralised source, so a brace or an `=` inside a comment or a literal
        # cannot be read as code. It is character-for-character, and split on "\n" alone rather
        # than by `splitlines()`, whose extra separators would misalign the two views — so line
        # numbers still address the original file, which is what the report quotes.
        lines = _opaque_literals(text).split("\n")
        source = text.split("\n")
        for i, line in enumerate(lines):
            if "fetchSemanticsNodes()" not in line:
                continue
            start, end = _statement_bounds(lines, i)
            statement = " ".join(part.strip() for part in lines[start:end + 1])
            # The match opening on line `i`, not merely the first in the statement: two of them can
            # share one statement, each judged by what precedes it. Bounding the match to the span
            # line `i` occupies in the rebuild also keeps a benign `fetchSemanticsNodes().map { }`
            # from claiming — and reporting at its own line — a discarded chain further down.
            offset = sum(len(lines[part].strip()) + 1 for part in range(start, i))
            match = target.search(statement, offset)
            if match is None or match.start() >= offset + len(lines[i].strip()):
                continue
            prefix = statement[:match.start()]
            if CONSUMING_KEYWORD.search(prefix):
                continue
            if CONSUMING_OPERATOR.search(_outside_brackets(prefix)):
                continue
            closes_block = next(
                (part.strip() for part in lines[end + 1:] if part.strip(FILLER)), ""
            ).startswith("}")
            if closes_block and _returns_from_a_consuming_block(lines, start):
                continue
            hits.append(f"{rel(path)}:{i + 1}: {source[i].strip()}")
    return report(
        "No discarded assertion results",
        hits,
        ["Wrap it in assertTrue(...) / assertFalse(...) or it can never fail."],
    )


def _returns_from_a_consuming_block(lines: list[str], index: int) -> bool:
    """Whether a block around `lines[index]` is one whose last expression is its value."""
    for opener in _enclosing_blocks(lines, index):
        if CONSUMING_BLOCK.search(opener):
            return True
        if DECLARATION.search(opener):
            return False
    return False


# Package roots a test only reaches by calling into the platform. The bare substring `android.`
# stood here and let a *package name* qualify a test as needing a device: a pure-JVM test that says
# `"com.android.vending"` to build a store intent touches no Android API at all. Literals are
# blanked before this runs for the same reason, so both halves of that mistake are closed.
#
# The androidx.test namespace is listed subpackage by subpackage for the same reason. The bare
# `androidx.test.` stood here and let the *runner* qualify a test: `@RunWith(AndroidJUnit4::class)`
# imports `androidx.test.ext.junit.runners`, which is how the new-test template starts every file,
# so a test asserting nothing but arithmetic satisfied the predicate. Runner, filter and annotation
# packages are infrastructure a misplaced test carries by habit and drops on the way out, so only
# the packages that reach a real device — a context, an activity, Espresso, UI Automator — count.
ANDROID_API = re.compile(
    r"composeTestRule|InstrumentationRegistry|Instrumentation\b|"
    r"androidx\.test\.(?:core|espresso|platform|rule|uiautomator|ext\.junit\.rules)\.|"
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
        if not ANDROID_API.search(blank(path.read_text(encoding="utf-8"), strings=True)):
            hits.append(rel(path))
    return report(
        "Instrumentation tests need a device",
        sorted(hits),
        ["This test uses no Android API — drop @RunWith(AndroidJUnit4) and move it to "
         "app/src/test."],
    )


# A test whose only platform touch is `InstrumentationRegistry` reaches the device for one thing: a
# `Context`. That is usually to borrow `cacheDir` as a scratch directory, which the JVM suite gets
# from `java.io.tmpdir` for free and in seconds. So the marker below is required to say what else the
# device is for.
CONTEXT_ONLY_API = re.compile(r"InstrumentationRegistry|Instrumentation\b")

# Everything that reaches a real device for more than a Context: a rendered composable, a launched
# activity, Espresso, UI Automator, or any framework class. `androidx.test.platform.` is deliberately
# absent — that package *is* InstrumentationRegistry, so listing it would match every candidate and
# the check would never fire.
#
# `android.content` is matched member by member rather than as a package for the same reason:
# `Context` itself lives there, so the bare package exempted every test that declared the type it
# borrows — `import android.content.Context` — while the identical test with the type inferred was
# flagged. Which files the check fired on was decided by an import style, not by device use. Every
# other member of the package (Intent, ContextWrapper, pm.*, res.*) does imply more than a Context,
# so only Context is excluded.
REAL_DEVICE_API = re.compile(
    r"composeTestRule|ActivityScenario|"
    r"androidx\.test\.(?:core|espresso|rule|uiautomator|ext\.junit\.rules)\.|"
    r"\bandroid\.(?:app|database|graphics|hardware|media|net|os|provider|system|text"
    r"|util|view|webkit|widget|Manifest)\b|"
    r"\bandroid\.content\.(?!Context\b)"
)

# The opt-out. Read from the raw source rather than the blanked copy, because it is a comment.
DEVICE_REQUIRED = re.compile(r"//\s*device-required:\s*\S")


def check_context_only_tests_say_why() -> bool:
    """
    The sibling of check_instrumentation_tests_need_a_device, for the loophole that check leaves.

    `InstrumentationRegistry` counts as an Android API there, so a pure-JVM test qualifies for the
    emulator by doing nothing but `getInstrumentation().targetContext.cacheDir` to name a temp
    directory — which is how `EdgeCasesTest` came to run ~40 `FileRepository` cases on a device while
    `FileRepositoryTest` runs the same kind of case on the JVM in seconds.

    Some of these genuinely belong here: reading `context.assets` needs a packaged APK, and
    `FileRepository.copy` calls `StatFs`, which is not mocked in the unit source set. So this does not
    move anything — it requires the file to state the reason once, as `// device-required: <why>`, so
    the next file to land here has to make the same case instead of inheriting the exemption.
    """
    hits = []
    for path in ANDROID_TEST.rglob("*Test.kt"):
        raw = path.read_text(encoding="utf-8")
        code = blank(raw, strings=True)
        if not CONTEXT_ONLY_API.search(code):
            continue
        if REAL_DEVICE_API.search(code):
            continue
        if DEVICE_REQUIRED.search(raw):
            continue
        hits.append(rel(path))
    return report(
        "Context-only instrumentation tests declare why",
        sorted(hits),
        [
            "This test reaches the device only for a Context — usually just to borrow cacheDir.",
            "Move it to app/src/test, or add '// device-required: <reason>' saying what needs a device.",
        ],
    )


# Compose's animation entry points. A test naming one of these is building the transition itself
# rather than observing production's, so `enter = slideInVertically { it }` written in the test is
# what the assertion ends up verifying.
TEST_DECLARED_ANIMATION = re.compile(
    r"\b(AnimatedVisibility|AnimatedContent|Crossfade|animateContentSize"
    r"|slideIn(?:Vertically|Horizontally)|slideOut(?:Vertically|Horizontally)"
    r"|fadeIn|fadeOut|scaleIn|scaleOut|expandVertically|shrinkVertically)\s*[({]"
)


def check_no_test_declared_animations() -> bool:
    """
    The sibling of check_no_test_composables, for the case it cannot see.

    `FileOperationIntegrationTest` once wrapped the real DestinationPicker in an
    `AnimatedVisibility(enter = slideInVertically { it })` that the test itself declared, then
    asserted the title appeared when `visible` flipped to true. Two tests named
    `pickerOverlay_slidesInFromBottom` therefore verified androidx.compose.animation, and would
    have stayed green with production's slide deleted or reversed. No @Composable is declared, so
    the annotation check does not fire.

    A test that needs to assert an animation must drive the production composable that owns it.
    """
    hits = []
    for path in kotlin_files():
        text = path.read_text(encoding="utf-8")
        for match in TEST_DECLARED_ANIMATION.finditer(blank(text, strings=True)):
            hits.append(f"{rel(path)}:{line_of(text, match.start())}")
    return report(
        "No test-declared animations",
        hits,
        [
            "This builds the transition in the test, so the assertion checks Compose, not the app.",
            "Render the production composable that declares the animation instead.",
        ],
    )


def main() -> int:
    # rglob on a missing directory yields nothing rather than raising, so a moved or renamed
    # source set would turn every check into a no-op that prints OK and exits 0.
    if not kotlin_files():
        print(f"{RED}No Kotlin sources under {rel(ANDROID_TEST)} — "
              f"the guards would inspect nothing.{RESET}")
        return 1
    checks = [
        check_no_test_composables,
        check_no_test_declared_animations,
        check_no_hardcoded_ui_strings,
        check_no_discarded_assertions,
        check_instrumentation_tests_need_a_device,
        check_context_only_tests_say_why,
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
