---
name: audit-tests
description:
  Audits the File Explorer test suite for tests that pass regardless of whether the code is correct, for production code with no coverage, and for suite-level health problems (duplication, flakiness sources, misplaced tests, stale guards). Produces a ranked report and, on request, the fixes. Use when asked to review, audit, improve or harden the tests, or when a test's green result feels unearned.
---

# Auditing this test suite

The suite is large — ~700 instrumentation tests and ~620 unit tests — and its size is exactly what
makes it worth auditing. A big green suite reads as confidence, so a test that cannot fail is more
expensive here than a missing test: it occupies the slot where a real check belongs and reports
success while doing nothing.

The first audit found **8,142 lines** of instrumentation tests asserting against private
`@Composable` copies of production screens. Four of those copies had already drifted from what
shipped. Every one of those tests was green. Assume the same class of thing has crept back.

**Do not run tests.** Static analysis, `grep`, reading files and `scripts/check-tests.sh` are all
free. Launching an emulator, `./gradlew connectedDebugAndroidTest`, `testDebugUnitTest` or
`scripts/test.sh` requires asking first — the owner runs those.

---

## Step 0 — the mechanical pass

Two scripts, both static, both seconds. Run them first; they do the work that does not need
judgment.

```bash
./scripts/check-tests.sh            # the CI guard — fails on five unambiguous defects
python3 scripts/audit_tests.py      # the audit report — no finding fails it, every line is a candidate
```

`check_tests.py` fails on: a `@Composable` declared inside `androidTest`; a Compose animation the
test declares itself (`AnimatedVisibility`, `slideIn*`, `Crossfade` and friends) instead of driving
production's; a Compose matcher carrying a literal that `strings.xml` also defines; a
`fetchSemanticsNodes()` result computed and discarded; and an instrumentation test that touches no
Android API. **If it passes, that tells you only that those five shapes are absent.** If it fails,
fix those first — no judgment required.

`audit_tests.py` prints six sections, each runnable alone
(`python3 scripts/audit_tests.py coverage duplicates`):

| Section      | Surfaces                                                                            |
|--------------|-------------------------------------------------------------------------------------|
| `orphans`    | Test files that reference no production symbol at all                               |
| `coverage`   | Production files no test names, ranked by blast radius                              |
| `duplicates` | The same `@Test` name in two files                                                  |
| `skips`      | Every `@Ignore`, `@Retry` and `assumeTrue` — each one reports green without running |
| `fixtures`   | Temp directories created with no `@After` that removes them                         |
| `assertions` | `@Test` bodies with no assertion and no asserting helper                            |

**Its output is candidates, not findings.** It matches by name, so it cannot see coverage that flows
through a factory, and it cannot tell a legitimate "this does not throw" test from a missing check.
Read each line before acting on it. Everything in Steps 1–3 is what neither script can see.

Then take stock of shape, which is itself a finding — an outlier file usually grew a second
responsibility:

```bash
find app/src/androidTest -name '*Test.kt' -exec wc -l {} + | sort -rn | head -15
```

---

## Step 1 — tests that pass regardless of correctness

This is the highest-value pass. Work through the taxonomy; each entry is a shape that has actually
occurred in this repository.

### 1.1 Assertions against a copy of production

The dominant failure mode. A test declares its own version of the thing it claims to test, then
asserts on that. It can never fail for a production reason.

`check_tests.py` catches the `@Composable` form. The same idea appears without composables:

- A test re-implementing a state machine the ViewModel owns — `selectedPaths + path` written in the
  test rather than driving `FolderViewModel.toggleSelection`.
- A test re-declaring a derived property's expression. `SearchBehaviorTest` once computed
  `query.isNotEmpty() && searchComplete && results.isEmpty()` itself — the exact body of
  `SearchUiState.showNoResults`, the thing under test.
- A test re-implementing a formatter (`formatDuration`, `formatDate`) instead of asserting the
  screen's output.

Detection: `audit_tests.py orphans` catches the extreme case — a file naming no production symbol at
all. For the rest, ask of each test file: **which production symbol does this exercise, and would a
change to it fail this test?** A UI test whose only app references are `R` and `FileExplorerTheme`
is
testing nothing.

### 1.2 Tests that exercise the framework, not the app

`NavigationDrawerTest` once built `ModalNavigationDrawer` + `NavigationDrawerItem` inline in all
eight tests. There is no `NavigationDrawer` component in this codebase — the drawer lives inside
`HomeScreen`. Those tests verified that Compose Material3 renders a label and fires `onClick`, and
would have stayed green with the app's drawer deleted.

Ask: **if I deleted the production code this file is named after, would anything here fail?**

### 1.3 Discarded or absent assertions

`check_tests.py` catches the `fetchSemanticsNodes()` case. Also look for:

- A `@Test` whose body ends in a `performClick()` with nothing asserted afterwards.
- A boolean flag declared, never written by production, then asserted false — the assertion is true
  by construction. (`aboutRow_withoutOnClick` was written this way before being changed to assert on
  the absence of a click action.)
- `assertNotNull` on something the test itself just constructed.

### 1.4 Tautologies

A test that asserts a data class returns its own constructor arguments tests the Kotlin compiler.
`deleteProgress_showsPartialFailure` did exactly this — no `setContent`, no dialog — while its name
promised a UI assertion.

`audit_tests.py assertions` lists every `@Test` with no assertion; a tautology is the subset
where the assertions present only read back what the test constructed.

### 1.5 Name/assertion contradictions

`delete_nonExistentFile_returnsTrue` asserted `assertFalse`. When a name and an assertion disagree,
one of them was changed to match an implementation instead of a contract. Decide which is the
contract, then make them agree — and if the production behaviour is the questionable one, say so
rather than silently pinning it.

```bash
grep -rn 'fun .*returns\(True\|False\)' -A8 app/src/androidTest app/src/test --include=*.kt \
  | grep 'assertTrue\|assertFalse'
grep -rn 'fun .*_\(fails\|succeeds\)' -A8 app/src/androidTest app/src/test --include=*.kt \
  | grep 'assertTrue\|assertFalse'
```

### 1.6 Locale-dependent false passes

`onNodeWithText("Share").assertDoesNotExist()` passes on every non-English device because the
literal
never matches. `check_tests.py` catches literals that `strings.xml` defines, with `location_*` and
`storage_*` deliberately excluded — those double as legitimate fixture folder names.

What it cannot catch: a **plural** written out by hand in a form the resource does not produce, and
a
formatted string assembled in the test rather than through `getString(id, args)`. Both hide bugs in
the 19 translations.

### 1.7 Assertions weaker than the name

- `renameDialog_folder_selectsEntireName` asserted only that the name was displayed; selection was
  never checked.
- Twelve `*_rendersCorrectly` theme tests asserted only that text existed. Black-on-black in dark
  mode passed all of them.

For each test, ask: **name a change to production this test would catch that a bare "it composed"
check would not.** If there is no answer, the assertion is too weak.

### 1.8 Duplicates

`FileOperationExecutionTest` was a near-subset of `FileOperationsEndToEndTest` — same conflict
counter, empty folder, mixed selection and progress-filename cases. Duplication costs emulator
wall-clock on every merge and splits maintenance.

`audit_tests.py duplicates` finds these. Two caveats before acting: parallel screens legitimately
have parallel test names (`ImageViewerScreenTest` and `TextViewerScreenTest` share
`shareButton_firesChooserIntent` and both are real), and a shared `ui/components` dialog should be
tested in exactly one place — when two screen test files both cover it, the screen-specific file is
the one to trim. Diff case-by-case and carry over anything unique before deleting.

---

## Step 2 — coverage gaps

Coverage is now measurable — `enableAndroidTestCoverage` and `enableUnitTestCoverage` are on for
debug — but do not run the build to get it. Derive gaps statically instead:

`audit_tests.py coverage` produces this list already grouped. Weight the results by what a defect
costs, not by line count. In this app that ordering is:

1. **Anything that parses untrusted bytes.** `data/util/*MetadataExtractor` and `*ThumbnailFetcher`
   run platform decoders over files the user did not create. `SqliteMetadataExtractorTest` exists
   because one of them *deleted the user's file* — the framework's default SQLite error handler
   wipes
   a database it considers corrupt. Every extractor and fetcher must have: a valid fixture, a
   truncated one, wrong magic bytes, an empty file, a directory, and an assertion that **the probed
   file is byte-identical afterwards**. See `MetadataExtractorRobustnessTest` and
   `ThumbnailFetcherRobustnessTest`.
2. **Irreversible file operations.** Copy/move/delete/rename/compress/extract, and specifically
   their
   partial-failure and cancellation paths. There is no undo.
3. **ViewModels.** Business logic lives there by convention (`CLAUDE.md`); a ViewModel with no test
   is untested business logic. `AboutViewModel` and `TextViewerViewModel` were both in this state.
4. **Screens whose only coverage is a component test.** A row that renders correctly in isolation
   says nothing about whether the screen wires it up.
5. **Navigation.** `NavGraph` decides what the user sees first and whether the permission wall stays
   on the back stack. `NavTransitions` documents a real process-killing crash from a zero-duration
   transition.

Also look for gaps *inside* covered areas — these do not show up as unreferenced symbols:

- A component tested only in its default state, never in its loading, empty, error or badge state.
  Production growing an `isLoading` parameter that no test passes `true` to is the shape.
- Only the happy path. For every "does X" test, is there a "does not do X when it shouldn't"?
- `ThemeMode.SYSTEM` — the default, and the one most easily forgotten.
- RTL assertions that check text exists rather than geometry (`getBoundsInRoot`); LTR satisfies them
  equally.
- No test running under a non-English locale, which leaves all 19 translations unverified.

---

## Step 3 — suite health

Things that are not wrong per test but degrade the suite.

**Misplaced tests.** A test needing no device belongs in `app/src/test`, where it runs in seconds.
`check_tests.py` enforces this. The reverse also matters: an `AndroidViewModel` test forced onto the
JVM ends up mocking the `Context` it should be using.

**Flakiness sources, and `@Retry` as a symptom.** Every `@Retry` is a claim that a failure is an
emulator-load artifact. Re-examine each one — a retry on a genuinely intermittent bug hides it.

Then look for the causes: fixed `Thread.sleep`; node selection by bare index rather than by a
distinguishing property; assumptions about `File.listFiles()` order (it has none); and
process-global singletons — `SortManager`, `ThemeManager`, the Coil caches, DataStore files — that
one test mutates and the next inherits. Test Orchestrator now gives each test its own process, which
removes most cross-test bleed; a test still depending on order after that is a real defect.

**`assumeTrue` that has become a permanent skip.** A guarded test reports as passing while never
running. Check each condition still varies on the target devices.

**Uncleaned fixtures.** Every test creating a temp directory needs an `@After` that removes it.

**Missing assertion messages inside loops.** A table-driven test that fails without naming the case
costs an hour. Every assertion inside a `forEach` needs the case in its message.

**A guard that has gone stale.** `check_tests.py` has exclusions — the `location_*`/`storage_*`
prefixes, the "consuming context" regex. Confirm they still exclude only what they were meant to,
and add a check whenever this audit finds a new shape worth preventing mechanically. **Encoding a
finding in the script is worth more than fixing the instance.**

---

## Step 4 — report, then fix

Rank by *how much false confidence the test provides*, not by how easy it is to fix. A test that
cannot fail outranks a missing test, which outranks a weak assertion.

For each finding give: `file:line`, what the test claims, what it actually verifies, and the
concrete
production change that would slip past it. That last part is the test of the finding itself — if you
cannot name such a change, it is not a finding.

When fixing, follow the patterns already in the repo rather than inventing new ones:

| Need                                         | Use                                                   | Exemplar                                 |
|----------------------------------------------|-------------------------------------------------------|------------------------------------------|
| Render a real screen over real data          | Temp dir + real ViewModel + `FakeStorageSource`       | `SearchScopingTest`, `FolderSortingTest` |
| Drive the real folder screen                 | `testutil/FolderScreenRobot`                          | `FolderSelectionModeTest`                |
| Assert a screen actually launched something  | Espresso-Intents + `intending(anyIntent())`           | `ActivityNavigationTest`                 |
| Reach a `private` composable                 | Make it `internal` — `androidTest` is a friend module | `ItemInfoContent`, `SettingsScreen`      |
| Build a metadata bag with one field set      | `testutil/MetadataFixtures`                           | `ItemInfoScreenTest`                     |
| Match a badge, a button vs. its dialog title | `testutil/TestMatchers`                               | `SettingsScreenTest`                     |
| Observe genuinely in-flight UI               | Inject a client/dispatcher the test can hold open     | `FeedbackScreenAdditionalTest`           |

Two rules when writing the replacement:

- **Never re-declare production UI or logic in the test.** If the target is unreachable, widen its
  visibility to `internal` and say why in a comment. That is a deliberate test seam, not a leak.
- **Every assertion on user-facing text goes through `getString` / `getQuantityString`.** Fixture
  names — files and folders the test itself created — stay as literals; they are not translated.

Leave a short comment on any test whose point is non-obvious, naming the defect it guards. The
existing suite does this well (`SqliteMetadataExtractorTest`, `NavTransitions`) and it is why those
guards survive refactors.

Finish by re-running `./scripts/check-tests.sh` and reporting compile status. State plainly that the
suites were not executed — that is the owner's call.
