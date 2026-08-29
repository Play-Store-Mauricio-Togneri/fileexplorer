# Bug Findings

Audit range: `d0b63a1c283c63959fcd3e1195f5e367171e529d...38df6e4092747b3a220f3792c221b6c1595119bb`.

## Medium

### `[b/contract-mismatches/startup-routing/permission-grant-skips-configured-folder]`

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:119`, `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/navigation/NavGraph.kt:67`
- **Confidence:** High
- **Trigger:** Configure a startup folder, revoke storage permission, cold-start, then grant permission on the permission screen.
- **Impact:** The app opens Home instead of the configured startup folder.
- **Cause:** `startupFolderPath()` maps missing permission to `null`, the same value used for “start at Home.” The grant callback only navigates to Home; it never retries startup resolution.
- **Baseline:** Startup-folder routing was added in the audited range.
- **Suggested fix:** Retain a pending startup request while permission is absent and resolve it after the grant, with the existing recreation/one-shot guards.

### `[c/dead-or-unreachable-behavior/settings-home-order/no-accessible-reorder-path]`

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/SettingsActivity.kt:1475`
- **Confidence:** High
- **Trigger:** Use TalkBack, Switch Access, a keyboard, or another semantics-driven input method to reorder Home sections.
- **Impact:** Section labels are reachable, but their ordering cannot be changed.
- **Cause:** The sole mutation path is raw `pointerInput` / `detectDragGestures`; the handle has no description, semantic action, or keyboard operation. `HomeSectionsOrderDialogTest.kt:31` explicitly records that no alternate actions exist.
- **Baseline:** The dialog was introduced in the audited range.
- **Suggested fix:** Add semantic Move up/Move down actions and equivalent keyboard controls that share the drag reorder operation.

### `[a/boundary-and-encoding-cases/settings-dialogs/new-option-lists-not-scrollable]`

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/SettingsActivity.kt:1145`, `SettingsActivity.kt:1328`
- **Confidence:** High
- **Trigger:** Open a new swipe-action or Home-section-order dialog in landscape, split-screen, or at a large font scale.
- **Impact:** Lower swipe choices or reorder rows can be squeezed/clipped and cannot be selected or dragged.
- **Cause:** The dialogs place fixed `Column`s in Material3 `AlertDialog` text slots without scrolling. Material3 1.4 constrains that slot with `weight(1f, fill = false)` but does not add scrolling; the code itself notes squeezed rows at line 1314.
- **Baseline:** The affected dialogs were added in the audited range.
- **Suggested fix:** Use bounded scrollable list content, retaining gesture ownership for the reorder handle.

### `[c/dead-or-unreachable-behavior/test-structure-guards/multiline-semantics-result-is-not-detected]`

- **Location:** `scripts/check_tests.py:424`
- **Confidence:** High
- **Trigger:** Write a discarded Compose assertion across lines, for example `fetchSemanticsNodes()` followed by `.isNotEmpty()` on the next line.
- **Impact:** The structural guard passes although the expression's Boolean result is discarded, so the test cannot fail.
- **Cause:** The detector requires `fetchSemanticsNodes().isNotEmpty()` to appear on one physical line.
- **Baseline:** The guard was introduced after the selected base.
- **Suggested fix:** Match the fluent chain across whitespace/newlines before statement-consumption analysis.

### `[c/dead-or-unreachable-behavior/test-structure-guards/import-aliases-bypass-guards]`

- **Location:** `scripts/check_tests.py:153`, `scripts/check_tests.py:271`
- **Confidence:** High
- **Trigger:** Alias `Composable` or a UI matcher in a Kotlin import, then use the alias in an instrumentation test.
- **Impact:** A test-local composable replica or locale-dependent literal matcher can bypass the guards and pass CI.
- **Cause:** The detectors match only the literal spellings `@Composable` and matcher method names; Kotlin import aliases are not resolved.
- **Baseline:** The guard was introduced after the selected base.
- **Suggested fix:** Reject aliases of guarded imports, or parse Kotlin imports and include their aliases in the detector.

### `[c/dead-or-unreachable-behavior/test-structure-guards/test-infrastructure-counts-as-device-use]`

- **Location:** `scripts/check_tests.py:474`
- **Confidence:** High
- **Trigger:** Put a pure logic test in `androidTest` using the usual `AndroidJUnit4` runner import.
- **Impact:** The “Instrumentation tests need a device” guard accepts the test, so slow emulator-only tests remain misplaced.
- **Cause:** Any `androidx.test.*` import satisfies the device-use predicate, including test-runner infrastructure. Seventy-one of the seventy-four current instrumentation tests import that namespace.
- **Baseline:** The guard was introduced after the selected base.
- **Suggested fix:** Exclude test-runner imports and require production Android API use, instrumentation access, or Compose test APIs.

## Audit Details

### Audit Status

**Partial.** This report intentionally ends at `38df6e4`. While it was being completed, the working target advanced to `45b6869` and `ItemInfoScreen.kt` became modified; neither is covered. The data partition also did not complete before its delegated reviewer exhausted its runtime budget.

### Initial Worktree Snapshot

The cutoff snapshot was clean on `main` at `38df6e4092747b3a220f3792c221b6c1595119bb`. Its tracked-state digest was `b8e0b3d1bf3cd2ee3b2fe06c520da691e79c59b7cb966454d36a7bf5e79865a3`; staged and unstaged patch hashes were empty. Ignored build outputs, IDE files, local properties, and credentials configuration were excluded. The selected base was supplied by the user after automatic branch-base discovery found no usable non-empty base.

### Audit Coverage

The deterministic inventory contained 424 paths: 217 first-pass changed/related seeds plus 207 direct consumers, with 420 current and 4 deleted paths. Inventory digest: `c8c078a99de7981e627493efaa36988e26a1b5297fceec44b8d735b80c10f996`; partition digest: `cd6d2373dbd0105ebdf6fdb320fb5c52ec6d602ef2bf4c5da3ba20d62768a90c`.

| Partition | Included | Inspected | Skipped |
|---|---:|---:|---:|
| UI | 147 | 147 | 0 |
| Build/test tooling | 104 | 104 | 0 |
| Data/filesystem | 173 | 0 | 173 |
| **Total** | **424** | **251** | **173** |

UI coverage traced 34 meaningful flows, including startup/permission, home, navigation, file operations, pickers, settings, viewers, and feedback. Build coverage traced five flows: structural test guards, test launch, build/dependency configuration, release shrinking/signing, and resource packaging. Data/filesystem flows were not counted as traced because that review did not finish.

### Candidate Dispositions

| Candidate class | Count | Disposition |
|---|---:|---|
| Reported findings | 6 | The six findings above |
| Refuted | 5 | Backtick scanner regression fixed; four UI candidates had alternate behavior or were not defects |
| Pre-existing | 2 | Picker and image-viewer behaviors match the base |
| Unverified | 0 | No unverified candidate is presented as a finding |
| **Total** | **13** | `13 = 6 + 5 + 2 + 0` |

All required taxonomy areas were applied to the inspected partitions. The six surviving candidates were challenged against the baseline and by focused static reproductions; the scanner findings were additionally reproduced against the current guard with synthetic Kotlin snippets.

### Verification Performed

- `git diff --check d0b63a1...38df6e4` passed.
- `PYTHONDONTWRITEBYTECODE=1 ./scripts/check-tests.sh` passed.
- `PYTHONDONTWRITEBYTECODE=1 python3 -B scripts/audit_tests.py` exited 0.
- Translation-key parity across all 20 localized `strings.xml` files passed.
- Navigation Compose 2.10 predictive-transition APIs were verified from the cached official source artifact.
- Focused synthetic checks confirmed the three reported structural-guard false negatives and confirmed the previous backtick/apostrophe scanner issue is fixed.

### Exclusions and Limitations

Gradle unit tests, lint, and instrumentation tests were not run. Two isolated Gradle attempts failed before project configuration because this environment could not create its lock/wildcard-IP infrastructure; no third workaround was attempted. The later `45b6869` commit and current `ItemInfoScreen.kt` worktree edit are excluded by the agreed cutoff, and the 173-file data/filesystem partition remains unaudited.

### Summary

Six Medium, High-confidence defects survived in the audited UI and test-tooling partitions. The most user-visible are startup-folder routing after a permission grant and inaccessible/unscrollable Home-settings dialogs. No application files were changed by this audit; this report is its only artifact.
