# Test audit — fix plan

Written 2026-10-30 on `release/2.8.0`, from the `/audit-tests` run of the same day. It covers the 39
findings in that audit's table (R1–R39, same numbering), plus three small extras found while
planning (E1–E3). Every row was re-read against the current source while this plan was written, and
line numbers are current as of that reading. Nothing here has been implemented or run.

Path prefixes used below:

| # | Prefix | Means                                                         |
|---|--------|---------------------------------------------------------------|
| 1 | `T/`   | `app/src/androidTest/java/com/mauriciotogneri/fileexplorer/`  |
| 2 | `U/`   | `app/src/test/java/com/mauriciotogneri/fileexplorer/`         |
| 3 | `P/`   | `app/src/main/java/com/mauriciotogneri/fileexplorer/`         |

---

## 1. Ground rules for every row

These come from `.claude/skills/audit-tests/SKILL.md` (Step 4) and `AGENTS.md`, and every change
below follows them:

- **Never re-declare production UI or logic in a test.** If a target is unreachable, widen it to
  `internal` with a comment saying it is a test seam. Only one row (R25) needs a production seam at
  all.
- **All user-facing text goes through `getString` / `getQuantityString`.** Fixture names the test
  itself created stay literal.
- **Each new or changed test gets a short comment naming the defect it guards**, the way
  `SqliteMetadataExtractorTest` and `NavTransitions` do.
- **Prove each fix can fail.** For every row, temporarily apply the "ships green if…" change from the
  audit and confirm the new test goes red, then revert it. This is cheap for unit rows and costs
  emulator time for instrumentation rows (see §5).
- **Test names follow the file's existing style**: backticked sentences in `app/src/test`,
  `camelCase_withUnderscores` in `app/src/androidTest`.
- Instrumentation tests must run (never always-skip) on `Pixel_7_API_36`. Every skip condition
  below reads platform or fixture state, never a production answer.

---

## 2. Decisions needed before starting

Each decision has a recommended answer. The rows that depend on it are blocked until it is settled.

| # | Row(s)   | Decision | Options | Recommended |
|---|----------|----------|---------|-------------|
| 1 | R1       | How to measure the navigation transition's real length | (a) Make the duration constant `internal` and assert ≥ 10 ms on the JVM. Catches only a `0`. (b) Extract a shared `internal val` tween spec and assert its vectorized duration on the JVM. Also catches a spring swap, but not a dropped `animationSpec`. (c) Measure through Compose on the device. Catches every variant, but uses `AnimatedVisibility`, which the "no test-declared animations" guard rejects. It would need a narrow `// production-transition: <why>` opt-out marker, read like `// device-required:`. | **(c)** plus the opt-out marker: the enter/exit values are production's, only the host is the test's |
| 2 | R10, R12 | The "delete cancelled" / cancel toast is emitted from inside the coroutine being cancelled, into a flow with no buffer (`P/ui/screens/folder/FolderViewModel.kt:1035`, `:211`), so it very likely never shows. **Possible production bug, unverified.** | Fix production (emit from `cancelDelete()` itself, or under `NonCancellable`) and pin it with a test; or leave production alone and don't assert the toast | Verify on device first; if confirmed, fix in a separate change. The R10/R12 tests below deliberately do not assert the toast |
| 3 | R23      | How strictly to enforce the badge trail | (a) Weak invariant only: parent version ≥ child version. (b) Also pin the previous release's version table in the test and assert "child raised ⇒ parent raised"; must be updated each release. (c) Production stores per-release raise sets and derives the versions; exact, but a refactor. | **(b)**, with a comment telling the release process to update the table |
| 4 | R22      | Home's APK Settings button is also untested (`T/ui/screens/home/HomeDialogsTest.kt:634` renders the shared dialog with its own lambda, not `HomeScreen`'s) | Add a Home wiring test now (needs an APK in recents or favorites, tapped on the home screen), or log it | Add it now: same defect, same fix |
| 5 | R37 (also R6, R22) | A "wait for the recents entry, then remove it" cleanup helper exists in three test files; R37 would add a fourth copy | Move it to `T/testutil/` now, or copy it again | Move it |
| 6 | R25      | Holding a search mid-stream needs `FileRepository.searchFilesStreaming` (`P/data/repository/FileRepository.kt:1143`) to become `open` | Accept the seam (precedent: `listFiles`, `listNames`, `countChildren` are already `open` for tests), or drop the in-list spinner test | Accept the seam |
| 7 | R38      | Splitting `FolderDialogsTest` (948 lines) into five per-dialog files and dissolving `FolderErrorStatesTest` is a large file move | Do the full split; or only move the file to `ui/components/` as is | Full split, done first in the instrumentation work so later rows edit the final files |

Two related items are **out of scope for this plan**; record them separately:

- **Possible production bug (from the audit, unverified):** cancelling a large delete may never tell
  the gallery index about folders it already removed, because removed roots are only reported on
  the final progress emission (`P/data/repository/FileRepository.kt` ~`:750`).
- **Copy-pasted production code** is why several "duplicate" tests must stay: viewer delete ×3,
  favorites vs. recents pruning ×2, extraction wiring ×5 screens. The copies have already drifted
  (R16). Merging them is a production refactor, not a test fix.

---

## 3. Order of work

The rows are ordered so that files are moved before they are edited, and the cheap unit-test loop
comes before the emulator-bound work. Within each phase, rows go from most false confidence to
least.

**Phase 1 — tooling and structural moves**

1. R39 (scripts) together with R8. The new string check fails on R8's line until R8 is fixed, so
   they land as one change.
2. R38: split and move the dialog suites.
3. Shared test helpers moved to `T/testutil/`: the non-fatal report reader (R15), the APK
   extension skip condition (R22), and the recents cleanup helper (R37, if decision 5 says yes).

**Phase 2 — unit tests** (fast loop; run after every file)

R2 + R35 (the same file) → R3 (unit part) → R9 + R34 (the same file) → R10, R11, E1 (all in
`FolderViewModelTest`) → R12 → R13 → R14 → R16 → R20 (unit part) → R23.

**Phase 3 — instrumentation**, batched by file to avoid editing the same file twice

| # | Batch                 | Rows                                    | Files touched |
|---|-----------------------|-----------------------------------------|---------------|
| 1 | Navigation            | R1                                      | `NavGraphTest`, `scripts/check_tests.py` (decision 1) |
| 2 | Item info             | R3 (delete one test), R32               | `ItemInfoMetadataTest`, `ItemInfoScreenTest` |
| 3 | Recent files          | R4, R25 (recents sheet)                 | `RecentFilesSectionTest` |
| 4 | Settings              | R5, R17, R28, R29, R36 (settings)       | `SettingsScreenTest`, `SettingsDialogsTest` |
| 5 | Analyzer category     | R6, R22, R36 (analyzer), R19            | `AnalyzerCategoryScreenTest`, `AnalyzerCategoryLaunchTest`, new `AnalyzerCategoryActivityLaunchTest` |
| 6 | Picker                | R7, R36 (picker)                        | `PickerBottomBarTest`, `StorageSelectorContentTest`, `FolderPickerContentTest` |
| 7 | Extractors            | R15                                     | `MetadataExtractorRobustnessTest`, `ThumbnailFetcherRobustnessTest`, new `testutil/NonFatalReports.kt` |
| 8 | File open and routing | R22, R30, R37                           | `FileOpenRoutingTest`, `ItemInfoScreenEventsTest`, `SearchScreenTest`, `FolderScreenTest`, plus Home (decision 4) |
| 9 | Folder screen         | R25 (restricted state, compress), R33, R36 (folder), R38 (screen tests) | `FolderScreenTest`, `FolderLoadErrorTest`, the split dialog tests, `FolderSelectionModeTest` |
| 10 | Navigation targets   | R20                                     | `ActivityNavigationTest` |
| 11 | Components           | R18, R21, R25 (sheet, filters), R27     | `SwipeableFileListItemTest`, `ActionBarTest`, `FileActionsBottomSheetTest`, `SearchFiltersBarTest`, `ThemeRenderingTest` |
| 12 | Feedback             | R24                                     | `FeedbackScreenAdditionalTest` |
| 13 | Search               | R25 (spinners), R31                     | `SearchBehaviorTest`, `SearchScopingTest`, `FileRepository` seam (decision 6) |
| 14 | Edge and permissions | R26, R36 (permission), E2, E3           | `EdgeCasesTest`, `PermissionScreenTest`, `PermissionScreenActionsTest`, `AllFilesAccessPremiseTest`, `FolderSortingTest` |

---

## 4. Row by row

Each row gives what is wrong, the change, any production seam needed, the existing test to copy,
where it runs, and its effort (S = under an hour, M = a few hours).

### Tests that cannot fail

#### R1 — Instant navigation transitions: measure the duration, not the identity

- **Wrong:** `T/integration/NavGraphTest.kt:145` asserts only `InstantEnter != EnterTransition.None`.
  Any of these passes, and each brings back the documented NaN process crash:
  setting `INSTANT_TRANSITION_MS = 0` (`P/ui/navigation/NavTransitions.kt:21`), swapping in a
  spring, or dropping `animationSpec` (the default spring lasts 0 ms for 1f → 1f).
- **Change (option c, per decision 1):** replace the test with
  `navTransitions_lastLongerThanTheRoundingThreshold`:
  - Set `mainClock.autoAdvance = false`.
  - In `setContent`, use `transition = rememberTransition(SeekableTransitionState(false))`. This is
    the state type `NavHost` divides by. Host it with
    `transition.AnimatedVisibility(visible = { it }, enter = InstantEnter, exit = InstantExit) { Box(Modifier.size(1.dp)) }`.
  - Call `seekTo(0f, targetState = true)`, advance one frame, and assert
    `transition.totalDurationNanos >= 10_000_000` ("enter").
  - Call `snapTo(true)`, then `seekTo(0f, targetState = false)`, and assert the same ("exit").
  - Comment: name the NaN crash and the ~9 ms threshold from the `NavTransitions` KDoc.
  - Add the `// production-transition:` opt-out to `check_no_test_declared_animations` in
    `scripts/check_tests.py`. Read it from the raw source, exactly like `DEVICE_REQUIRED`, and
    document it in that check's docstring.
- **Change (if decision 1 picks b):**
  - Add `internal val InstantTransitionSpec = tween<Float>(INSTANT_TRANSITION_MS)`, used by both
    transitions.
  - New `U/ui/navigation/NavTransitionsTest.kt` asserts
    `InstantTransitionSpec.vectorize(Float.VectorConverter).getDurationNanos(...) >= 10 ms`.
  - Delete the device test.
- **Seam:** none for (c); the shared spec value for (b).
- **Copy:** clock control in `T/ui/components/ReviewPromptEffectTest.kt:58`, `:178`.
- **Runs in:** instrumentation for (c), unit for (b). **Effort:** M.

#### R2 — Preference defaults read the test fake, not the real store

- **Wrong:** `U/data/repository/PreferencesRepositoryTest.kt:45, 142, 182, 222, 241, 268` read
  `FakePreferencesSource`'s hand-copied defaults. The real empty-store fallbacks in
  `P/data/source/DataStorePreferencesSource.kt:24, 34, 45, 58, 68, 135, 140` are tested only for a
  *failing* store, never for an *unset* one.
- **Change:** in `U/data/source/DataStorePreferencesSourceTest.kt`, add one test per setting over
  `DataStorePreferencesSource(FakeInMemoryDataStore())`:
  - `` `an unset store shows no hidden files` `` → `assertFalse`
  - `` `an unset theme follows the system` `` → `SYSTEM`
  - `` `an unset sort mode sorts by name ascending` `` → `NAME_ASC`
  - `` `unset enabled locations show every location` `` → `LocationType.entries.toSet()`
  - `` `recent files are on in an unset store` `` → `assertTrue`
  - `` `an unset startup screen opens home with no folder` `` → `HOME` and a null startup folder
  - Move `` `switching one swipe direction off leaves the other alone` `` (repository test `:133`)
    here: `setSwipeLeftAction(NONE)`, then assert right is still `DELETE`. The source's read-back
    test gives both sides the same value, so two directions sharing one key would pass today.
  - Then delete repository tests `:45, :63, :70, :96, :106, :133, :142, :182, :222, :241, :268`.
    Keep every `set… updates … flow` test, every `getInitial*` test, and all badge tests from
    `:259` on: those exercise logic the repository owns.
- **Seam:** none. **Copy:** `DataStorePreferencesSourceTest.kt:89`. **Runs in:** unit. **Effort:** S.

#### R3 — Item Info metadata routing tested where it lives

- **Wrong:**
  - `T/ui/screens/iteminfo/ItemInfoMetadataTest.kt:406` passes every other section as null, so
    their absence is true by construction; the composable only checks for non-null.
  - The real type checks are in `P/ui/screens/iteminfo/ItemInfoViewModel.kt:170-229`.
  - `U/ui/screens/iteminfo/ItemInfoViewModelTest.kt:206` checks 6 of the 12 fields. Simply adding
    the missing `assertNull`s would only catch the CSV gate: the other extractors return null on a
    `.txt` anyway.
- **Change:**
  1. Delete `metadataSection_onlyMatchingTypeRendered`.
  2. Add `` `each file type reaches its own extractor and no other` `` to `ItemInfoViewModelTest`:
     - `mockkObject` all 12 extractor objects and stub each `extract` (APK takes
       `extract(any(), any())`) to return a `mockk<XMetadata>()` sentinel.
     - `mockkObject(MimeTypeUtil)` and stub only `getMimeType(any())` per row, because `MimeTypeMap`
       is a stub on the JVM.
     - Rows: jpg → image, mp3 → audio, mp4 → video, pdf, apk, zip, docx (an `OFFICE_MIME_TYPES`
       value), epub. Then, with mime `"*/*"` to drive the extension path: `.db` → sqlite,
       `.vcf` → vcard, `.ics` → icalendar, `.csv` → csv.
     - For each row: a new ViewModel, advance, `assertSame` on the expected field and `assertNull`
       on the other 11, with a message naming the row. Unmock in `tearDown`.
  3. Rewrite `` `file metadata is null for plain text file` `` under the same stubs (`text/plain`)
     and assert all 12 fields are null.
- **Seam:** none. **Copy:** `ItemInfoViewModelTest.kt:68-77`. **Runs in:** unit (and one
  instrumentation deletion). **Effort:** M.

#### R4 — Every recent-file card must open its own file's menu

- **Wrong:** `T/ui/screens/home/RecentFilesSectionTest.kt:175` long-presses `photo.jpg`, which is
  the first card. `:202` renders a one-card list. A section always passing `recentFiles.first()`
  (`P/ui/components/RecentFilesSection.kt:87-88`) passes both.
- **Change:**
  1. `recentFilesSection_cardLongPress_triggersMenuCallback`: long-press `document.pdf`, the second
     card. Assert `menuFile == testRecentFiles[1]` and mode `"press"`.
  2. New `recentFilesSection_eachCardsMenu_reportsItsOwnFile`:
     - Render all three cards.
     - For each file, click
       `onNode(hasContentDescription(string(content_description_more_options)) and hasAnyAncestor(hasText(file.name))).performScrollTo()`,
       then `waitForIdle`.
     - Collect what the callback receives; assert `assertEquals(testRecentFiles, received)`.
  3. Keep `:202`, adding the comment from `FavoritesSectionTest.kt:131` explaining why it uses
     `onNode` rather than `onAllNodes[0]`.
- **Seam:** none. **Copy:** `T/ui/components/FavoritesSectionTest.kt:131`, `:153`.
  **Runs in:** instrumentation. **Effort:** S.

#### R5 — Render the "tracking on AND has files" rule

- **Wrong:** `T/ui/screens/settings/SettingsDialogsTest.kt:64` passes `enabled = true` straight to
  the row. The rule itself (`P/activities/SettingsActivity.kt:348`) is never rendered: the screen
  helper hardcodes `hasRecentFiles = false` (`SettingsScreenTest.kt:1284-1285`).
- **Change:**
  - In `SettingsScreenTest`, give `renderSettingsScreen` three parameters: `recentFilesEnabled`,
    `hasRecentFiles` and `onClearRecentFiles`.
  - Add three tests:
    - `settingsScreen_clearRecentFiles_enabledWhenTrackingOnAndHasFiles`: render with
      (true, true); the row is enabled, and clicking it invokes the callback.
    - `settingsScreen_clearRecentFiles_disabledWhenTrackingOff`: render with (false, true); assert
      `assertIsNotEnabled()`.
    - `settingsScreen_clearRecentFiles_disabledWhenNoRecentFiles`: render with (true, false);
      assert `assertIsNotEnabled()`.
  - Comment: each disabled case pins one half of the `&&`.
  - Rename `SettingsDialogsTest:64` to `clearRecentFiles_enabled_isClickable`. It tests that the row
    respects `enabled`, not the rule.
- **Seam:** none (`SettingsScreen` is already `internal`). **Copy:** `SettingsScreenTest.kt:937`,
  `clickableWithText` in `T/testutil/TestMatchers.kt`. **Runs in:** instrumentation. **Effort:** S.

#### R6 — Analyzer category taps observed by their effect

- **Wrong:** `T/ui/screens/analyzercategory/AnalyzerCategoryScreenTest.kt:123` and `:130` assert
  only that a click handler exists. One is always attached because of long-press. So emptying the
  open branch (`P/ui/screens/analyzercategory/AnalyzerCategoryScreen.kt:263-271`) or setting
  `IconButton(onClick = {})` at `:189` passes. System back in selection mode (`:139`) is never
  driven.
- **Change:**
  - Give `render` an `onCloseClick` parameter.
  - Replace `rows_respondToATap` with `rows_tapOpensTheFile`:
    - Click the row, then `intended(allOf(hasAction(ACTION_VIEW), hasData(<FileProvider uri of file0>)))`.
    - The file open writes a recents entry, so register the opened path with the recents cleanup
      helper.
  - Replace `backArrow_isTheWayOut` with `backArrow_closesTheListing`: click the back arrow and
    assert the callback fired.
  - New `selection_systemBack_clearsTheSelectionRatherThanLeaving`:
    - Render three files and long-press `file0`.
    - Call `onBackPressedDispatcher.onBackPressed()`.
    - Assert the selection count is gone and the activity is not finishing.
- **Seam:** none. **Copy:** `T/integration/FileOpenRoutingTest.kt:95-104`,
  `T/ui/screens/folder/FolderSelectionModeTest.kt:189`. **Runs in:** instrumentation. **Effort:** S.

#### R7 — Picker absence checks over nothing

- **Wrong:**
  - `T/ui/screens/picker/PickerBottomBarTest.kt:136` asserts that a resource the component never
    loads is absent. The component renders whatever string it is handed
    (`P/ui/screens/picker/PickerBottomBar.kt:107`).
  - `StorageSelectorContentTest.kt:108` asserts names it never passed in are absent.
  - `FolderPickerContentTest.kt:174` passes an empty folder list, so the name it checks can never
    appear.
- **Change:**
  - Replace `noValidationError_errorNotDisplayed` with
    `validationError_clearsOnceTheDestinationIsValid`:
    - Hold the error in `mutableStateOf`, starting at the "same folder" message.
    - Assert the message is shown, then set the error to null.
    - Assert the message is gone and the Move button is enabled.
    - Comment: a stale error makes a valid folder look refused.
  - Delete `emptyStorageList_showsNothing`: there is no empty-state behaviour to pin.
  - `error_showsErrorMessage`: pass real folders. It then pins that the error replaces stale rows
    (the error branch comes before the list, `FolderPickerContent.kt:50`).
- **Seam:** none. **Copy:** `FolderDialogsTest.kt:765`. **Runs in:** instrumentation. **Effort:** S.

#### R8 — The unknown-count check only works in English

- **Wrong:** `T/ui/components/FileListItemTest.kt:154` asserts that no text containing "item"
  exists. On any non-English locale this passes for anything, including a `childCount ?: 0`
  regression (`P/ui/components/FileListItem.kt:294-296`).
- **Change:** in `fileListItem_directoryWithUnknownCount_showsNoItemCount`, replace it with:
  1. `onNodeWithText(getQuantityString(R.plurals.item_amount, 0, 0)).assertDoesNotExist()`, which
     catches the `?: 0` regression in every locale.
  2. The row's merged text is exactly the name plus an empty reserved second line:
     `SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString("LoadingFolder"), AnnotatedString("")))`.
     This catches any placeholder text, and also the second line being dropped (the "jumping
     name" defect described at `FileListItem.kt:174-178`).
- **Open:** check 2 assumes an empty `Text` adds `""` to the merged list. If the first run shows
  otherwise, keep check 1 alone.
- **Seam:** none. **Copy:** `FileListItemTest.kt:432`. **Runs in:** instrumentation. **Effort:** S.
  Lands together with R39(a).

### Untested paths: deletes, extraction and the rating rule

#### R9 — A top-level file the progress delete cannot remove

- **Wrong:** the leaf-failure branch of `deleteWithProgress` (`P/data/repository/FileRepository.kt:711`,
  errno at `:696`) never runs. The failure tests (`U/data/repository/FileRepositoryTest.kt:1463`,
  `:1501`) fail only directories. Dropping it reports a partial failure as success and asks for a
  rating.
- **Change:**
  - In `FileRepositoryTest`, add
    `` `deleteWithProgress counts a top-level file it could not remove as failed` ``:
    - Fixtures: `locked.txt`, plus a folder holding `a.txt`.
    - `FileRepository(removeFile = { f -> if (f == locked) RemoveOutcome.Failed(EACCES) else deleteOnJvm(f) })`.
    - Pass `locked` first, so the test also proves the walk carries on past a failure.
    - Assert on the last emission: complete; `failedFiles == 1`; `deletedFiles == 1`;
      `totalFiles == 2`; `failureErrno == EACCES`; no structural failure;
      `removedRootPaths == [folder]`; `absentRootPaths` empty; `locked` still exists.
  - In `U/ui/screens/folder/FolderViewModelTest.kt:1573`, add
    `verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }` and `trackDeleteCompleted` exactly 0.
    A successful delete shows no toast; success is only those two calls (`FolderViewModel.kt:1086-1099`).
- **Seam:** none (`removeFile` is already a constructor parameter, `FileRepository.kt:99`).
  **Copy:** `FileRepositoryTest.kt:1073`. **Runs in:** unit. **Effort:** S.

#### R10 — Cancelling a large delete or a compression

- **Wrong:** `cancelDelete` (`P/ui/screens/folder/FolderViewModel.kt:1141`), `cancelCompression`
  (`:1233`) and the cancel branch (`:1034-1035`) are never called. Dropping `deleteJob?.cancel()`
  means Cancel stops nothing.
- **Change:** two tests in `FolderViewModelTest`, each commented "a cancel that only clears the
  dialog leaves the work running and later counts it as a success":
  - `` `cancelling a large delete stops it and counts nothing` ``:
    - `deleteWithProgress` returns
      `flow { emit(inFlight); gate.await(); emit(completeWithRoots) }`, where `gate` is a
      `CompletableDeferred`.
    - Confirm, advance, and assert the in-flight file is shown.
    - Call `cancelDelete()` and assert progress is null.
    - Complete the gate and advance. Assert progress is still null.
    - Assert 0 calls to `onActionSucceeded`, `trackDeleteCompleted`, `notifyTreeDeleted`, the
      error reporter and `trackOperationFailed`, and that `listFiles` ran exactly twice.
  - `` `cancelling a compression stops it and counts nothing` ``: the same shape with
    `compressFiles`. Assert no `trackCompressCompleted`, no success, no media scan and no failure
    report.
  - Do **not** assert the cancel toast (decision 2).
- **Seam:** none. **Copy:** `FolderViewModelTest.kt:2153`, gate as at `:737`. **Runs in:** unit.
  **Effort:** M.

#### R11 — Rename failure, and the case-only rename guard

- **Wrong:**
  - `U/ui/screens/folder/FolderViewModelTest.kt:1321` checks only favorites and recents. The
    failure branch (`FolderViewModel.kt:895-898`) and the success count (`:893`) are unpinned.
  - The case-only guard (`:881`) is never exercised.
- **Change:**
  - Extend `:1321`:
    - Collect `viewModel.events` and expect a toast with `R.string.rename_error`.
    - `verify(exactly = 1) { AnalyticsTracker.trackOperationFailed("rename", "unknown") }`. This
      failure event is what blocks the rating ask; `AnalyticsTracker` is mocked here, so verify the
      event rather than `ReviewPrompt.onActionFailed`.
    - Assert no `onActionSucceeded` and no `notifyDeleted`.
  - Add `` `a case-only rename does not report the old path deleted` ``:
    - The rename returns a result with `isCaseOnlyRename = true`.
    - Assert 0 calls to `notifyDeleted` and a scan of the new path.
    - Comment, marked as an assumption since the code states no reason: on a case-insensitive
      volume both paths name the same file.
  - To `:1300`, add `notifyDeleted` exactly once with the old path, so an inverted guard also
    fails.
- **Seam:** none. **Copy:** `FolderViewModelTest.kt:2972` (toast), `:1363` (rating).
  **Runs in:** unit. **Effort:** S.

#### R12 — A cancelled extraction must stay quiet

- **Wrong:** `U/util/UncompressHandlerTest.kt:510` checks only that the job stopped and progress
  cleared. Dropping `if (e !is CancellationException)` (`P/util/UncompressHandler.kt:176`) makes
  every cancel file a non-fatal and block the rating ask.
- **Change:** extend that test. After cancel and advance, assert:
  - `ErrorReporter.error` was never called.
  - `AnalyticsTracker.trackOperationFailed` was never called (this is the one that fails without
    the guard).
  - Neither `ReviewPrompt.onActionFailed` nor `onActionSucceeded` was called.
  - Wrap it in `handler.events.test { …; expectNoEvents() }`.
  - Comment: a cancel is the user's choice, not a failure.
  - The toast check is weak on its own (decision 2); the error and analytics checks carry the test.
- **Seam:** none. **Copy:** `UncompressHandlerTest.kt:479`. **Runs in:** unit. **Effort:** S.

#### R13 — Where an archive extracts to, outside the folder screen

- **Wrong:** only `FolderViewModelTest.kt:1060` pins the target folder. Home (`:886`) and Item Info
  (`:246`) stub `uncompressFile(any(), any(), …)`; Search and Analyzer have no extraction test.
  Dropping the target line (`P/ui/screens/search/SearchViewModel.kt:373`, `HomeViewModel.kt:945`,
  `AnalyzerCategoryViewModel.kt:411`) leaves it `""`, so every extraction from that screen fails.
- **Change:**
  - Add `` `an archive extracts into the folder that holds it` `` to each of the four ViewModel test
    files:
    - Stub `getZipInfo(zip.path)` to return `ZipInfo(1, false)` and `uncompressFile(...)` to return
      `emptyFlow()`.
    - Call `showUncompressDialog(zip)`, advance, `confirmUncompress()`, advance.
    - `coVerify(exactly = 1) { uncompressFile(zip.path, <parent>, null, any(), any()) }`.
  - Per-screen setup:

    | # | Screen       | Fixture | Notes |
    |---|--------------|---------|-------|
    | 1 | Home         | a zip in `tempDir` | parent is `tempDir` |
    | 2 | Item Info    | a zip in a `tempDir` subfolder | ViewModel created on it; parent is that subfolder |
    | 3 | Search       | `/storage/emulated/0/Download/Archives/archive.zip` | also stub `storageRepository.getStorages()` (strict mock) |
    | 4 | Analyzer     | `File(root, "sub/archive.zip")` | via `createViewModel(1)` |

  - Comment: these screens have no folder of their own, and an unset target is `""`.
- **Seam:** none. **Copy:** `FolderViewModelTest.kt:1060`. **Runs in:** unit. **Effort:** S.

#### R14 — A favorite delete that fails on Home

- **Wrong:** the failure branch (`P/ui/screens/home/HomeViewModel.kt:889-901`) has no test. Its
  recents twin does (`U/ui/screens/home/HomeViewModelTest.kt:790`).
- **Change:** add `` `confirmDeleteFavorite carries the cause it failed for` ``:
  - Use a favorite at a JVM-absent path. `delete` returns
    `DeleteResult(failedCount = 1, failureErrno = EACCES)`.
  - Assert:
    - The delete error is shown and `favoriteToDelete` is cleared.
    - `trackOperationFailed("delete", any(), EACCES, "home_favorite", "all_failed")`.
    - `removeFavorite` is never called: a favorite whose file survived must stay.
    - No `notifyTreeDeleted`, `onActionSucceeded` or `trackDeleteCompleted`.
    - `getStorages()` is called twice (as `:701` does).
- **Seam:** none. **Copy:** `HomeViewModelTest.kt:790`, `:701`. **Runs in:** unit. **Effort:** S.

#### R15 — Extractors: corrupt input must not file a non-fatal

- **Wrong:**
  - `T/data/util/MetadataExtractorRobustnessTest.kt:102-130` checks no exception, a null result and
    an unchanged file, but not "no non-fatal reported".
  - The thumbnail fetchers already check this (`ThumbnailFetcherRobustnessTest.kt:254`).
  - 11 of the 12 extractors have the guard; APK (`ApkMetadataExtractor.kt:63`) reports every
    exception.
- **Change:**
  1. Move the logcat reader out of `ThumbnailFetcherRobustnessTest` into a new
     `T/testutil/NonFatalReports.kt`:
     - Moves: `errorReportsWhile` (`:509`), `logcat()` (`:536`) and their constants.
     - New signature: `fun nonFatalsFiledWhile(case: String, block: () -> Unit): List<String>`.
     - The thumbnail test then calls it.
  2. In the wrong-magic, truncated and empty-file tests:
     - Wrap the `extractors.forEach` in `nonFatalsFiledWhile("<input kind>") { … }`.
     - Assert the result is empty.
     - Rename each to `…_returnsWithoutThrowingOrReporting`.
     - One capture window per test is enough: each report line names `extract_<type>_metadata`.
  3. Cover all 12 extractors, APK included. Leave out the directory and missing-file inputs: a
     missing file returns before any guard, and Item Info only probes files, gated by type.
  4. Keep the single positive control (`ThumbnailFetcherRobustnessTest.kt:286`), and update both
     KDocs to say it now proves the shared channel.
- **Open:** if the APK extractor floods on garbage input, that is a production finding: give it the
  same guard in a separate change.
- **Seam:** none. **Copy:** `ThumbnailFetcherRobustnessTest.kt:254`. **Runs in:** instrumentation.
  **Effort:** M.

#### R16 — Image and text viewer delete never checks the gallery index

- **Wrong:** `ImageViewerViewModel.kt:141` and `TextViewerViewModel.kt:152` call `notifyDeleted`,
  but their tests only stub it. The PDF viewer verifies it
  (`U/ui/screens/pdfviewer/PdfViewerViewModelDeleteTest.kt:119`, `:141`).
- **Change:**
  - In `` `deleting still removes the single file the viewer was opened on` `` (image `:221`, text
    `:120`), add `coVerify(exactly = 1) { MediaStoreUtil.notifyDeleted(any(), listOf(file.absolutePath)) }`.
  - In `` `a delete the repository could not carry out blocks the rating ask` `` (image `:240`,
    text `:141`), add `coVerify(exactly = 0) { MediaStoreUtil.notifyDeleted(any(), any()) }`.
- **Seam:** none. **Copy:** `PdfViewerViewModelDeleteTest.kt:119`. **Runs in:** unit. **Effort:** S.

### Other untested wiring

#### R17 — Swipe, second-line and clear-favorites wiring through the settings screen

- **Wrong:** the helper (`T/ui/screens/settings/SettingsScreenTest.kt:1245`) declares these
  callbacks but no caller passes them, and it hardcodes `hasFavorites = false`. Unguarded:
  - `P/activities/SettingsActivity.kt:445`, `:456` (swipe)
  - `:415`, `:434` (second line)
  - `:353` (the confirm dialog)
- **Change:** add `hasFavorites` and `onClearFavorites` to the helper, then add:
  - `settingsScreen_swipeLeftDialog_storesTheLeftActionOnly`: open the left row and pick "Move to".
    Assert left is MOVE_TO, the right callback was never called, and the dialog closed.
  - `settingsScreen_swipeRightDialog_storesTheRightActionOnly`: the mirror, choosing "Copy to".
  - `settingsScreen_folderSecondLineDialog_storesTheFolderChoiceOnly`, and the same for files.
  - `settingsScreen_clearFavorites_asksBeforeClearing`:
    - Render with `hasFavorites = true`.
    - Tapping the row clears nothing (count is 0).
    - Tapping `buttonWithText(dialog_clear)` clears once (count is 1) and closes the dialog.
  - Comment each with the swap or missing-confirm defect it guards.
- **Seam:** none. **Copy:** `SettingsScreenTest.kt:937`; use `buttonWithText` because the dialog
  title repeats the row label. **Runs in:** instrumentation. **Effort:** M.

#### R18 — Tapping and drawing the left swipe button

- **Wrong:** the only tap test (`T/ui/components/SwipeableFileListItemTest.kt:274`) uses
  `leftAction = NONE`. The left-swipe tests at `:90` and `:580` only find the node. Passing
  `onSwipeAction(rightAction)` at `P/ui/components/SwipeableFileListItem.kt:178` (tapping Rename
  deletes) or swapping the alignment at `:172` both pass.
- **Change:**
  - New `tappingTheLeftRevealedButton_reportsTheLeftAction`:
    - Use the defaults (left RENAME, right DELETE) and swipe left.
    - Click at `Offset(right - 40.dp, centerY)` on the Rename node.
    - Assert RENAME was reported.
  - New `swipeLeft_drawsItsButtonInTheUncoveredStrip`, plus a mirror for the right swipe:
    - Find the uncovered strip from the row and button bounds, without restating the 80 dp width.
    - Sample the strip's corner in `captureToImage` as the button's fill.
    - Assert that some pixel in the strip differs from that fill by more than about 0.25, meaning
      the icon or label is drawn there.
- **Seam:** none. **Copy:** `SwipeableFileListItemTest.kt:274`, `:336`; the strongest-ink helper in
  `FileListItemTest.kt:910`. **Runs in:** instrumentation. **Effort:** M.

#### R19 — Analyzer category launch: the intent must carry the category, and the screen must read it

- **Wrong:** `T/integration/AnalyzerCategoryLaunchTest.kt:78` checks only the component. Dropping
  the extra (`P/activities/AnalyzerCategoryActivity.kt:84`) or its read (`:42-44`) makes every
  category open a screen that closes itself.
- **Change:**
  1. Rename the test to `everyFileCategoryRowTap_launchesTheListingForThatCategory`:
     - Loop over `AnalyzerCategory.entries - SYSTEM`.
     - Scroll with `performScrollToNode(hasText(label))`.
     - Assert `intended(allOf(hasComponent(...), hasExtra(any(String::class.java), equalTo(category.name))))`.
     - Fix the class KDoc ("Asserts the component alone").
  2. New `T/activities/AnalyzerCategoryActivityLaunchTest.kt` (`createEmptyComposeRule()`, no
     Espresso-Intents), with `AnalyzerResultsHolder.clear()` in `@After`:
     - `categoryIntent_opensTheListingForThatCategory`:
       - Store empty results for every category.
       - Launch each of the five with `createIntent(context, category)`.
       - Wait for its label as the title.
     - `withoutCategoryExtra_finishes`.
- **Seam:** none. **Copy:** `ActivityNavigationTest.kt:269-273`,
  `T/activities/ViewerActivityLaunchTest.kt:75-94`. **Runs in:** instrumentation. **Effort:** M.

#### R20 — The Downloads card must open Downloads

- **Wrong:** `T/integration/ActivityNavigationTest.kt:140`, `:151` check only that the folder
  screen launched. The unit tests stub every location to one folder, so the location → folder
  mapping (`P/data/repository/LocationsRepository.kt:246`) is tested nowhere.
- **Change:**
  - Instrumentation: in `locationCard_launchesFolderActivity`, hoist the public Downloads directory.
    Keep the existing skip condition (`downloads.isDirectory`, a platform fact) and assert
    `intended(launchesFolder(downloads.absolutePath))`.
  - Unit: add `` `getLocations resolves every location to its own public directory` ``:
    - Give the stub jar's `Environment.DIRECTORY_*` fields distinct values ("Download",
      "Pictures", …). Use reflection if Kotlin refuses the assignment.
    - `mockkStatic(Environment)`, with `getExternalStoragePublicDirectory(any())` answering
      `File(tempDir, firstArg())`.
    - Create each folder.
    - Assert each location's path is `File(tempDir, DIRECTORY_X)`, and that Podcasts is absent (the
      SDK level is 0 on the JVM).
    - In `finally`, reset the fields and unmock.
    - The expected location-to-constant table is the spec itself, not a copy of production logic.
- **Seam:** none. **Copy:** `U/data/repository/LocationsRepositoryTest.kt:166-188`.
  **Runs in:** both. **Effort:** M.

#### R21 — The Uncompress button in the action bar

- **Wrong:** `T/ui/components/ActionBarTest.kt` never selects a zip, so removing or swapping the
  zip branch (`P/ui/components/ActionBar.kt:103-121`) passes.
- **Change:** add an `archive.zip` fixture (`application/zip`), then:
  - `actionBar_singleZipSelected_showsUncompressInsteadOfCompress`
  - `actionBar_uncompressButton_triggersAction`, expecting `FileAction.Uncompress`
  - `actionBar_zipAmongSeveral_showsCompress`, which pins the single-selection condition
- **Seam:** none. **Copy:** `ActionBarTest.kt:265`. **Runs in:** instrumentation. **Effort:** S.

#### R22 — The APK Settings button on the search and analyzer screens (and Home, per decision 4)

- **Wrong:** each screen wires the dialog's Settings button by hand. Folder
  (`FileOpenRoutingTest.kt:164`) and Item Info (`ItemInfoScreenEventsTest.kt:195`) are pinned.
  Search (`P/ui/screens/search/SearchScreen.kt:408`), Analyzer (`AnalyzerCategoryScreen.kt:352`)
  and Home (`HomeScreen.kt:522`) are not.
- **Change:**
  - Move `assumePlatformMapsApkExtension` (now copied at `FileOpenRoutingTest.kt:202` and
    `ItemInfoScreenEventsTest.kt:331`) into `T/testutil/`.
  - `SearchScreenTest`:
    - Add Espresso-Intents init with `intending(anyIntent())` in setUp, and release in tearDown.
    - New `apkPermissionDialog_settingsButton_firesManageUnknownSourcesIntent`:
      - Same platform skip conditions as `FileOpenRoutingTest.kt:166-170`.
      - Create a fake `app.apk`, search "app", tap it, then tap Settings.
      - Assert the intent with the R30 matcher.
  - `AnalyzerCategoryScreenTest`: the same test. Add `render(files, totalBytes)`, with the existing
    `render(fileCount)` delegating to it.
  - Home (decision 4): the same test, driven from an APK in the home screen's recents.
  - Fix the `FileOpenRoutingTest.kt:150-162` KDoc:
    - Drop the five stale line numbers.
    - Name the five screens instead, and the test pinning each one.
- **Seam:** none. **Copy:** `FileOpenRoutingTest.kt:164-179`. **Runs in:** instrumentation.
  **Effort:** S (M with Home).

#### R23 — The badge trail rule

- **Wrong:** `PreferencesRepositoryTest.kt:386` checks each badge version alone. Raising a drawer
  row's badge without raising the hamburger's leaves the new dot unreachable for users who already
  dismissed the hamburger (`P/data/repository/PreferencesRepository.kt:193-194`).
- **Invariant:** a badge shows when its stored dismissal is below its current version (`:145`). For
  a user who dismissed everything, a child's dot is reachable only if its parent was raised in the
  same release. So every child raise needs a parent raise. That implies parent ≥ child, but parent
  ≥ child alone does not guarantee it.
- **Change:** in `PreferencesRepositoryTest`:
  - Add a `TRAIL` map from child to parent: the drawer's Settings, Analyzer and About badges → the
    hamburger; the About screen's other-apps badge → the drawer's About.
  - Add `ROOTS`: the hamburger and the folder context-menu badge.
  - `` `every badge sits on a trail` ``: `assertEquals(ALL_BADGES, ROOTS + TRAIL.keys)`, so a new
    badge must be placed.
  - `` `no badge is raised past the badge that leads to it` ``:
    - Read each version through production: `dismissBadge(id)`, then read the stored version on a
      fresh fake.
    - Assert parent ≥ child for each edge.
  - Decision 3(b), additionally:
    - Pin `LAST_RELEASE_VERSIONS` in the test.
    - Assert that any child whose version rose past the pinned value has a parent that rose too.
    - Comment: update the pinned table every release.
- **Cannot catch** under (a): a child raised while still at or below its parent. Today the
  hamburger leads by enough that the next release would be almost entirely uncovered, which is why
  (b) is recommended.
- **Seam:** none. **Copy:** the existing `ALL_BADGES` check. **Runs in:** unit. **Effort:** S.

#### R24 — Feedback: network failure and the in-flight spinner

- **Wrong:** only an HTTP 500 is driven (`T/ui/screens/feedback/FeedbackScreenAdditionalTest.kt:257`).
  Either of these passes:
  - calling `onSuccess()` in the catch (`P/activities/FeedbackActivity.kt:163-175`): the screen
    closes and the text is lost;
  - dropping the submit-flag reset (`:390`): back then discards the draft without asking.

  The KDoc promises a spinner (`:399`) that nothing asserts.
- **Change:**
  - Give `renderFeedback` an `onBackClick` parameter.
  - Add `failingClient()`: an interceptor that waits on a gate, then throws `UnknownHostException`.
  - New tests:
    - `feedbackScreen_networkFailure_keepsScreenAndText`:
      - Type, submit, and assert submit is disabled.
      - Release the gate and wait until submit is enabled again.
      - Assert no success, and that the typed text is still shown.
    - `feedbackScreen_failedSubmit_backAsksBeforeDiscarding`: the same failure, then system back.
      Assert the discard dialog is shown and the screen did not close.
    - `feedbackScreen_submitInProgress_showsSpinner`, with the existing gated client:
      - An indeterminate progress node (unmerged tree) is absent before submit and present after.
      - Release the gate; the spinner goes away.
- **Seam:** none. **Copy:** `FeedbackScreenAdditionalTest.kt:233`,
  `FeedbackScreenTest.kt:70-73`. **Runs in:** instrumentation. **Effort:** S.

#### R25 — Small states no test renders

- **Restricted folder:** `P/ui/screens/folder/FolderScreen.kt:418-428`.
  - In `T/ui/screens/folder/FolderLoadErrorTest`, add `unreadableEmptyFolder_showsRestrictedState`:
    - Use a `FileRepository` subclass whose `listFiles` returns an empty list and `countChildren`
      returns null.
    - Assert `folder_restricted_empty` is shown and `list_empty` is absent.
  - Also assert `folder_restricted_empty` is absent in `emptyDirectory_showsEmptyState_notError`.
- **Compress progress with skipped files:** `P/ui/components/CompressProgressDialog.kt:51-58`.
  These go in the split dialog file from R38:
  - `compressProgressDialog_skippedFiles_measureAgainstCompressibleBytes`: total 4096, skipped 2048,
    compressed 1024 → `0.5f`. It reads 0.25 if skips are ignored.
  - `compressProgressDialog_compressedBeyondCompressible_isFloored`: total 2048, skipped 2048,
    compressed 1024 → `1.0f`.
- **Search spinners:** `SearchScreen.kt:307`, `:322`, in `SearchBehaviorTest`. `renderSearch`
  takes a `StorageSource` and a `FileRepository`.
  - `search_inFlightBeforeFirstMatch_showsSpinner`:
    - A storage source whose `getStorages()` waits on a gate.
    - Type, then wait for an indeterminate progress node (the query is debounced).
    - Assert there is no "no results" text; release the gate; the result appears and the spinner
      goes.
  - `search_inFlightAfterFirstMatch_keepsSpinnerBelowRows`:
    - A repository subclass whose `searchFilesStreaming` emits one result, then waits.
    - Assert the row and the spinner are both shown.
  - Release every gate in `@After`.
- **Favorite row in sheets:**
  - `T/ui/components/FileActionsBottomSheetTest.kt`: give `setSheet` an `isFavorite` parameter.
    Add `favorite_showsRemoveFromFavorites_notAdd`, `addToFavoritesAction_invokesCallback` and
    `removeFromFavoritesAction_invokesCallback`.
  - `RecentFilesSectionTest`: add the row to `bottomSheet_displaysAllActions`, plus
    `bottomSheet_favorite_offersRemoveFromFavorites` and both callback tests.
- **Search filter chips:** `P/ui/components/SearchFiltersBar.kt:75`, `:174`, `:180`, `:361-371`.
  - `typeChipIsDisabledWhileKindIsFolders`: the chip is not enabled, and a click opens nothing.
  - `showHiddenOptionReportsTrue` and `hideHiddenOptionReportsFalse`: both start from
    `includeHidden = false`, so a version that only toggles fails the second. Both assert the sheet
    closed.
  - `typeChipSummarisesFirstTypePlusCount`: with videos, images and audio selected, assert
    `getString(search_filter_type_summary, getString(location_images), 2)` is shown.
- **Seam:** `FileRepository.searchFilesStreaming` becomes `open fun` (decision 6), with the comment
  "open so a test can hold a search mid-stream".
- **Copy:** `FolderLoadErrorTest.kt:65`, `FolderDialogsTest.kt:634`,
  `ItemInfoScreenEventsTest.kt:127-131`, `FileActionsBottomSheetTest.kt:257`.
- **Runs in:** instrumentation. **Effort:** M for the screen half, M for the component half.

### Weak assertions

#### R26 — Renaming a file with a malformed name must succeed

- **Wrong:** `T/edge/EdgeCasesTest.kt:258` accepts success or failure. Replacing the plain file-API
  fallback (`P/data/repository/FileRepository.kt:452`) with `return null` passes.
- **Why success is the only right outcome on the device** (read from the code, not run):
  - The fixture only exists if the plain file API created it and the listing returned it intact,
    so that API's name encoding round-trips.
  - `renameTo` encodes both paths the same way, and ext4/f2fs accept any byte except `/` and NUL.
- **Change:** rename it to `malformedFileName_rename_succeedsThroughTheJavaIoFallback`. Assert:
  - The result is not null.
  - The new path is `sourceDir/renamed.txt`.
  - The renamed file's content matches.
  - The malformed entry is gone.
  - Keep the skip condition, which reads fixture state.
  - Comment: such names can't become a `java.nio` path, so the fallback is their only route.
- **Open:** if the first device run fails, the code reading above is wrong. Report it before
  loosening the test again.
- **Seam:** none. **Copy:** `EdgeCasesTest.kt:211-221`. **Runs in:** instrumentation. **Effort:** S.

#### R27 — Text colour of file names and of the delete button

- **Wrong:** `T/theme/ThemeRenderingTest.kt:520, 649, 663, 691` only find nodes. The file-row pixel
  test (`:446`) samples fills, not text. File names drawn black on dark (`FileListItem.kt:160`) or a
  wrong delete-button colour (`DeleteConfirmDialog.kt:70`) pass.
- **Change:**
  - Let the file's fill and ink helpers accept a `SemanticsMatcher` and `useUnmergedTree`. Keep the
    name-based versions, delegating to the new ones.
  - Add two light/dark tests with `@SdkSuppress(O)`, like the file's other pixel tests:
    - `fileListItem_drawsItsNameInOnSurface`:
      - Read the ink of `hasText(name)` in the unmerged tree, against the mode's surface.
      - Assert it is the mode's `onSurface`.
      - `Color.Black` is 32/255 off the light value, well over the 0.02 tolerance.
    - `deleteConfirmDialog_drawsItsConfirmInError`: on the dialog's delete button, the fill 4 dp in
      is `surfaceContainerHigh` and the ink is `error`.
  - Keep the four `*_rendersInEveryMode` tests: they are the class's structural check, including
    SYSTEM mode.
- **Open:** assumes capturing pixels works for a node inside a dialog window. Verify on the first
  run.
- **Seam:** none. **Copy:** `ThemeRenderingTest.kt:625`, `FileListItemTest.kt:928`.
  **Runs in:** instrumentation. **Effort:** M.

#### R28 — Only the current option may be selected

- **Wrong:** `SettingsScreenTest.kt:661, 826, 1039, 1104` assert only that the current option is
  selected. Hardcoding `selected = true` passes in all three dialogs:
  - theme: `SettingsActivity.kt:949`
  - startup: `:1008` (not in the original finding)
  - the shared option dialog: `:1165`
- **Change:** in those four tests, add `onAllNodes(isSelectable() and isSelected()).assertCountEquals(1)`.
  Comment: "a dialog marking every option passes the positive check alone".
- **Cannot catch:** the radio-button lines (`:957`, `:1021`, `:1173`). A radio button with no click
  handler has no semantics, so those lines only change pixels. Leave them.
- **Seam:** none. **Copy:** `SettingsScreenTest.kt:1240`. **Runs in:** instrumentation. **Effort:** S.

#### R29 — The show-hidden toggle, on → off

- **Wrong:** `SettingsScreenTest.kt:198` only goes off → on, so `onEnabledChange(true)` at
  `SettingsActivity.kt:838` passes.
- **Change:** rename it to `showHiddenToggle_clickToEnable_triggersCallback`. Add
  `showHiddenToggle_clickToDisable_triggersCallback`: render enabled, click, and assert the new
  value is `false`.
- **Seam:** none. **Copy:** `SettingsScreenTest.kt:127`, `:143`. **Runs in:** instrumentation.
  **Effort:** S.

#### R30 — The unknown-sources Settings intent must target this app

- **Wrong:** `FileOpenRoutingTest.kt:178` and `ItemInfoScreenEventsTest.kt:210` check the action
  only. Dropping the `package:` data (`P/util/IntentUtil.kt:573-576`) sends users to the list of all
  apps.
- **Change:** in both tests, assert
  `intended(allOf(hasAction(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES), hasData("package:${activity.packageName}")))`.
  R22's new tests use the same matcher.
- **Seam:** none. **Copy:** `T/ui/screens/permission/PermissionScreenActionsTest.kt:89-94`.
  **Runs in:** instrumentation. **Effort:** S.

#### R31 — Search scoping: wait for the search to finish before checking absence

- **Wrong:** `T/integration/SearchScopingTest.kt:96-111` waits for the first match, then asserts
  that other files are absent while results are still arriving (`SearchViewModel.kt:298-312`).
- **Change:**
  - Keep the ViewModel that `renderSearch` builds in a field.
  - Add `awaitSearchComplete(query)`:
    `waitUntil(8_000) { uiState.value.query == query && uiState.value.searchComplete }`, then
    `waitForIdle()`.
  - In both tests, in order: type the query, wait for completion, wait for the expected match,
    then assert absence.
  - Use `searchComplete`, not "spinner gone": the spinner is also absent during the 300 ms
    debounce, so it could pass before the search starts.
- **Seam:** none. **Copy:** `SearchScopingTest.kt:115-125`. **Runs in:** instrumentation.
  **Effort:** S.

#### R32 — Item Info label/value pairs, and vCard "no"

- **Wrong:**
  - `ItemInfoScreenTest.kt:526` and `ItemInfoMetadataTest.kt:169`, `:235` match labels and values
    separately, though both files have a row helper.
  - The vCard rows (`ItemInfoScreen.kt:1303-1317`) are tested only with `true`.
- **Change:**
  - Use `assertInfoRow(label, value)` for artist and album, the office fields (title, creator,
    subject, keywords) and the epub fields (title, creator, publisher, language, description).
  - `vcardInfo_displaysBooleanCapabilities`: assert `assertInfoRow(label, string(R.string.yes))`
    for each of the three.
  - New `vcardInfo_falseCapabilities_areNotListed`: two contacts, all three flags false. The
    contact count is shown and the three capability labels are absent.
- **Seam:** none. **Copy:** `ItemInfoMetadataTest.kt:180-181`. **Runs in:** instrumentation.
  **Effort:** S.

#### R33 — A name collision must disable the confirm button

- **Wrong:** `FolderDialogsTest.kt:338` and `:532` assert only the message. Setting
  `isValid = isBasicValid` (`P/ui/components/RenameDialog.kt:75`, `CompressDialog.kt:64`) keeps the
  message and enables the button.
- **Change:**
  - Rename them to `renameDialog_existingName_showsErrorAndDisablesRename` and
    `compressDialog_existingZipName_showsErrorAndDisablesCompress`. Both land in the R38 split files.
  - Append `onNode(buttonWithText(...)).assertIsNotEnabled()` to each. `buttonWithText` is required
    for Compress because the dialog title has the same text.
- **Seam:** none. **Copy:** `FolderDialogsTest.kt:196-216`. **Runs in:** instrumentation.
  **Effort:** S.

#### R34 — "The directory is still attempted" proves nothing

- **Wrong:** `U/data/repository/FileRepositoryTest.kt:1153` asserts `folder.exists()`, which is
  true either way, since the surviving child keeps the folder non-empty.
- **Change:**
  - In that test, record every path the `removeFile` lambda is called with.
  - Replace the assertion with
    `assertEquals("The directory is still attempted after a child fails", listOf(child, folder), attempted)`.
    This also pins the depth-first order that makes the child's errno the one reported.
- **Seam:** none. **Copy:** `FileRepositoryTest.kt:1073`. **Runs in:** unit. **Effort:** S.

#### R35 — Five setters missing from the failing-store write test

- **Wrong:** `U/data/source/DataStorePreferencesSourceTest.kt:347-357` calls 7 of the 12 setters.
  These are missing: `setHomeSectionOrder`, `setFolderSecondLine`, `setFileSecondLine`,
  `setSwipeLeftAction`, `setSwipeRightAction`.
- **Change:** call all five, and add the comment "every PreferencesSource write; add new setters
  here".
- **Seam:** none. **Runs in:** unit. **Effort:** S.

### Suite health

#### R36 — Duplicates to delete

Each entry was confirmed by reading both copies. Anything unique is carried over first.

| # | Delete | Kept copy | Carry over |
|---|--------|-----------|------------|
| 1 | `SettingsDialogsTest.kt:50`, `:81`, `:202`, `:231` | `SettingsScreenTest.kt:216`, `:231`, `:425`, `:453` | nothing |
| 2 | `FolderSelectionModeTest.kt:134` | `:72` | nothing |
| 3 | `PermissionScreenTest.kt:32`, `:44`, `:56`, `:122` | `:98` | the grant button's `assertIsEnabled()` goes into `:98`'s loop; update `:98`'s KDoc |
| 4 | `PermissionScreenActionsTest.kt:87` | `:69` | nothing |
| 5 | `FolderPickerContentTest.kt:158` | `:66` | nothing |
| 6 | `AnalyzerCategoryScreenTest.kt:139`, `:289` | `:148`, `:298` | nothing |
| 7 | `FolderScreenTest.kt:239` | `FolderSelectionModeTest.kt:252` | move its KDoc rationale onto `:252` |
| 8 | `FolderScreenTest.kt:254` | `FolderSelectionModeTest.kt:162` | nothing; drop the then-empty "Selection bar" header |

The audit script's own duplicate list (viewer deletes, favorites vs. recents, extraction cancel,
second-line settings, MIME predicates, cache keys) is **kept in full**: each copy guards its own
copy of the production code. **Effort:** S.

#### R37 — A folder test that launches a real app

- **Wrong:** `T/ui/screens/folder/FolderScreenTest.kt:165` taps a real file with no intent stub. It
  launches a real app and writes a recents entry that nothing removes. `FileOpenRoutingTest.kt:86`
  and `:95` leak the same entry, behind stubbed launches.
- **Change:**
  - Delete `FolderScreenTest.kt:165`.
  - Carry its "does not navigate" check over: in `FileOpenRoutingTest.tapRegularFile_firesViewIntent`,
    capture `onNavigateToFolder` and assert it was never called.
  - Clean up recents in `FileOpenRoutingTest`:
    - Record the paths of the fixtures it taps.
    - In `tearDown`, wait for their recents entries to be written, then remove them.
    - Use the shared `T/testutil/` helper (decision 5).
- **Seam:** none. **Copy:** `ActivityNavigationTest.kt:95-96`, `AnalyzerCategoryScreenTest.kt:440-455`.
  **Runs in:** instrumentation. **Effort:** S.

#### R38 — Dialog suites in the wrong place, and the folder screen's name wiring

- **Wrong:**
  - `T/ui/screens/folder/FolderDialogsTest.kt` renders only five shared `ui/components` dialogs.
  - `FolderErrorStatesTest` is a repository device test with one Compose test in it (`:116`).
  - Nothing checks that the folder screen hands its existing names to the dialogs
    (`P/ui/screens/folder/FolderScreen.kt:538`, `:593`, `:612`).
- **Change:**
  1. Split `FolderDialogsTest` into `T/ui/components/{CreateFolderDialog, RenameDialog, CompressDialog, CompressProgressDialog, DeleteProgressDialog}Test.kt`:
     - Spread the "Additional Validation" tests (`:813+`) and the rename-selection tests (`:902+`)
       to their dialog's file.
     - Use `T/testutil/TestMatchers` in place of the file-local matchers; keep `titleWithText` in
       the compress test.
  2. Dissolve `FolderErrorStatesTest`:
     - Move `:116` into `DeleteProgressDialogTest`.
     - Move the rest to `T/data/repository/FileRepositoryDeviceTest.kt`, keeping its
       `// device-required:` comment and dropping the Compose rule.
     - Update the references in `U/data/repository/FileRepositoryOperationsTest.kt:35` and
       `T/ui/screens/textviewer/TextViewerViewModelTest.kt:192`.
  3. New screen tests in `FolderScreenTest`, using `FolderScreenRobot.openRowActions`:
     - `createFolder_hiddenNameTaken_refusesIt`: a hidden `.secret` fixture. Typing ".secret" shows
       the "name exists" error with Create disabled. This also pins that hidden names count.
     - `rename_takenName_refusesIt`: rename `notes.txt` to `photo.jpg`. The same error, with Rename
       disabled.
     - `compress_takenZipName_refusesIt`: with `archive.zip` present, typing "archive" shows the
       "file exists" error with Compress disabled.
- **Seam:** none. **Copy:** `FolderScreenTest.kt:270`. **Runs in:** instrumentation. **Effort:** M.

#### R39 — Blind spots in the audit scripts

- **(a) Check for a word taken from translated text,** in `scripts/check_tests.py`:
  - Add `check_no_substring_fragments`, which flags any Compose text matcher carrying
    `substring = true` whose literal appears as a whole word inside any translatable `<string>` or
    `<plurals>` item. Match case-insensitively when `ignoreCase = true`.
  - Reuse `STRING_MATCHER`'s matcher list, `blank()` and `translatable_strings()`'s parsing.
  - A prototype run finds exactly one hit today: `FileListItemTest.kt:154` (R8). The check adds no
    standing noise, and it lands together with R8.
  - Update the guard count in `.claude/skills/audit-tests/SKILL.md` ("seven" → "eight") and its
    list of what the guard fails on.
- **(b) Skip list completeness,** in `scripts/audit_tests.py`:
  - `report_skips` also lists `@SdkSuppress`, `@FlakyTest`, `assumeNotNull` and `assumeThat`.
    `AGENTS.md` names `@SdkSuppress` as a skip; its four current uses all hold on API 36.
  - Read only code lines: blank comments first (as `check_tests.blank` does), so the two KDoc
    lines it lists today (`AllFilesAccessPremiseTest.kt:17`, `PermissionScreenActionsTest.kt:59`)
    drop out.
- **(c) Orphan list opt-out,** in `scripts/audit_tests.py`:
  - `report_orphans` skips a file carrying `// audit-orphan-ok: <reason>`, read from the raw source
    like `// device-required:`.
  - Add the marker to `T/theme/StatusBarAppearanceTest.kt` (its subject is the theme wrapper
    itself) and `T/integration/AllFilesAccessPremiseTest.kt` (an environment premise by design).
  - Document the marker in the script and in `SKILL.md`.
- **(d) If decision 1 picks (c):** the `// production-transition:` opt-out described in R1.
- **Verify:** run `./scripts/check-tests.sh` before R8 is fixed and confirm the new check reports
  exactly `FileListItemTest.kt:154`. Run it again after R8 is fixed and confirm it passes. Run
  `python3 -B scripts/audit_tests.py skips orphans` and confirm the two KDoc lines and the two
  orphans are gone. Use `-B` because `scripts/__pycache__` is tracked in git.
- **Effort:** S–M.

### Extras (not in the 39)

#### E1 — Two folder tests and one cache-key test that read back their own setup

- `U/ui/screens/folder/FolderViewModelTest.kt:806` reads the `NAME_ASC` its own setUp wrote.
  Replace it with `` `a folder opens in the sort mode last chosen` ``:
  - Before creating the ViewModel, call `SortManager.setSortMode(SIZE_DESC)`.
  - Assert the state's sort mode and that `listFiles(..., SIZE_DESC)` was called.
  - No test opens a folder with a non-default sort today.
- `FolderViewModelTest.kt:816`: delete it; `:631` and `:469` cover the same thing for real.
- `U/data/util/ThumbnailDiskCacheKeyTest.kt:35` asserts `key() == key()` on a pure function. Delete
  it; size independence is pinned by `ThumbnailDiskCacheTest.kt:108`, `:142`.
- **Runs in:** unit. **Effort:** S.

#### E2 — The All Files Access premise test names three dependents; there are four

- In `T/integration/AllFilesAccessPremiseTest.kt:13-54`, the KDoc and failure message name
  `StartupScreenTest`, `MediaStoreUtilProviderTest` and `AndroidMediaChangeSourceTest`.
- `T/util/AndroidPermissionCheckerTest.kt:55-63` also skips on the same grant.
- Change "three" to "four (three entirely, one in part)" and name the fourth in the message.
- **Effort:** S.

#### E3 — FolderSortingTest leaves its sort choice in the real preferences store

- `T/integration/FolderSortingTest.kt:52-62` resets only the in-memory sort.
- Each test's choice is saved to the real store, and `FileExplorerApplication.kt:41` loads it into
  every later test process.
- Change:
  - In `@After`, restore it through production:
    `runBlocking { PreferencesRepository(DataStorePreferencesSource(context.preferencesDataStore)).setSortMode(NAME_ASC) }`.
  - Add a comment saying why.
  - Fix the KDoc's "order-independent" claim (`:36-37`).
- No test is known to be affected today.
- **Copy:** `StartupScreenTest.kt:81-82`, `:114-117`. **Effort:** S.

---

## 5. Verification

After every change, run the three per-change commands from `AGENTS.md`:

```bash
./gradlew -w --console=plain -I gradle/agent-quiet.init.gradle testDebugUnitTest  # unit tests
./gradlew -w --console=plain lintDebug                                            # Android lint
./scripts/check-tests.sh                                                          # structural guards
```

- **Unit rows (Phase 2):** narrow each run to the edited class with `--tests <Class>` while
  iterating, then run the three commands above. For each row, apply the "ships green if…" change,
  confirm the new test fails, and revert it.
- **Instrumentation rows (Phase 3):** the code must compile and pass lint and the structural guards
  locally. Running the tests needs a booted `Pixel_7_API_36` and is the owner's call. Hand every run
  to the `instrumentation-runner` subagent, **one class per run**: a comma-separated method list
  runs only the first test. Recommended:
  - Prove-it-fails runs for the can't-fail rows (R1, R3–R8) and for R15, R19, R26.
  - Plain green runs for the rest.
- **Open first-run checks:** R8 (empty text in the merged list), R26 (the rename really succeeds
  on the device) and R27 (pixel capture inside a dialog). Each has a stated fallback; none should
  be silently weakened.
- **Code review:** run the `delta-review` skill once per turn over the files that turn changed.
- **Report:** the full suites (`./scripts/unit-tests.sh`, `./scripts/instrumentation-tests.sh`)
  stay reserved for the owner.

---

## 6. Effort summary

| # | Area | Rows | Unit | Instrumentation | Size |
|---|------|------|------|-----------------|------|
| 1 | Tooling and moves | R39, R38, the shared helpers | — | 3 files moved or split | M |
| 2 | Can't fail | R1–R8 | R2, R3 | R1, R3, R4–R8 | ~1.5 days |
| 3 | Deletes, extraction, rating | R9–R16 | R9–R14, R16 | R15 | ~1.5 days |
| 4 | Other wiring | R17–R25 | R20, R23 | R17–R22, R24, R25 | ~2 days |
| 5 | Weak assertions | R26–R35 | R34, R35 | R26–R33 | ~1 day |
| 6 | Suite health and extras | R36, R37, E1–E3 | E1 | R36, R37, E2, E3 | ~0.5 day |

These are rough estimates for writing and compiling the changes, excluding emulator time.
