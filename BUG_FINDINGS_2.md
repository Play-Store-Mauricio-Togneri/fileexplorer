## Medium

### `[b/contract-mismatches/startup-routing/permission-grant-skips-configured-folder]`

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:119`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/navigation/NavGraph.kt:67`
- **Confidence:** High
- **Trigger:** Configure a startup folder, revoke storage permission, cold-start, then grant
  permission on the permission screen.
- **Impact:** The app opens Home instead of the configured startup folder.
- **Cause:** `startupFolderPath()` maps missing permission to `null`, the same value used for “start
  at Home.” The grant callback only navigates to Home; it never retries startup resolution.
- **Baseline:** Startup-folder routing was added in the audited range.
- **Suggested fix:** Retain a pending startup request while permission is absent and resolve it
  after the grant, with the existing recreation/one-shot guards.

### `[c/dead-or-unreachable-behavior/settings-home-order/no-accessible-reorder-path]`

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/SettingsActivity.kt:1475`
- **Confidence:** High
- **Trigger:** Use TalkBack, Switch Access, a keyboard, or another semantics-driven input method to
  reorder Home sections.
- **Impact:** Section labels are reachable, but their ordering cannot be changed.
- **Cause:** The sole mutation path is raw `pointerInput` / `detectDragGestures`; the handle has no
  description, semantic action, or keyboard operation. `HomeSectionsOrderDialogTest.kt:31`
  explicitly records that no alternate actions exist.
- **Baseline:** The dialog was introduced in the audited range.
- **Suggested fix:** Add semantic Move up/Move down actions and equivalent keyboard controls that
  share the drag reorder operation.

### `[a/boundary-and-encoding-cases/settings-dialogs/new-option-lists-not-scrollable]`

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/SettingsActivity.kt:1145`,
  `SettingsActivity.kt:1328`
- **Confidence:** High
- **Trigger:** Open a new swipe-action or Home-section-order dialog in landscape, split-screen, or
  at a large font scale.
- **Impact:** Lower swipe choices or reorder rows can be squeezed/clipped and cannot be selected or
  dragged.
- **Cause:** The dialogs place fixed `Column`s in Material3 `AlertDialog` text slots without
  scrolling. Material3 1.4 constrains that slot with `weight(1f, fill = false)` but does not add
  scrolling; the code itself notes squeezed rows at line 1314.
- **Baseline:** The affected dialogs were added in the audited range.
- **Suggested fix:** Use bounded scrollable list content, retaining gesture ownership for the
  reorder handle.

###
`[c/dead-or-unreachable-behavior/test-structure-guards/multiline-semantics-result-is-not-detected]`

- **Location:** `scripts/check_tests.py:424`
- **Confidence:** High
- **Trigger:** Write a discarded Compose assertion across lines, for example `fetchSemanticsNodes()`
  followed by `.isNotEmpty()` on the next line.
- **Impact:** The structural guard passes although the expression's Boolean result is discarded, so
  the test cannot fail.
- **Cause:** The detector requires `fetchSemanticsNodes().isNotEmpty()` to appear on one physical
  line.
- **Baseline:** The guard was introduced after the selected base.
- **Suggested fix:** Match the fluent chain across whitespace/newlines before statement-consumption
  analysis.

### `[c/dead-or-unreachable-behavior/test-structure-guards/import-aliases-bypass-guards]`

- **Location:** `scripts/check_tests.py:153`, `scripts/check_tests.py:271`
- **Confidence:** High
- **Trigger:** Alias `Composable` or a UI matcher in a Kotlin import, then use the alias in an
  instrumentation test.
- **Impact:** A test-local composable replica or locale-dependent literal matcher can bypass the
  guards and pass CI.
- **Cause:** The detectors match only the literal spellings `@Composable` and matcher method names;
  Kotlin import aliases are not resolved.
- **Baseline:** The guard was introduced after the selected base.
- **Suggested fix:** Reject aliases of guarded imports, or parse Kotlin imports and include their
  aliases in the detector.

###
`[c/dead-or-unreachable-behavior/test-structure-guards/test-infrastructure-counts-as-device-use]`

- **Location:** `scripts/check_tests.py:474`
- **Confidence:** High
- **Trigger:** Put a pure logic test in `androidTest` using the usual `AndroidJUnit4` runner import.
- **Impact:** The “Instrumentation tests need a device” guard accepts the test, so slow
  emulator-only tests remain misplaced.
- **Cause:** Any `androidx.test.*` import satisfies the device-use predicate, including test-runner
  infrastructure. Seventy-one of the seventy-four current instrumentation tests import that
  namespace.
- **Baseline:** The guard was introduced after the selected base.
- **Suggested fix:** Exclude test-runner imports and require production Android API use,
  instrumentation access, or Compose test APIs.