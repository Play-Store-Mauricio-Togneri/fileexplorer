# Instrumentation Test Plan — Coverage Gap Closure

This plan adds instrumentation tests (`app/src/androidTest/`) for the coverage gaps
identified in the test-gap analysis. It is organized into **stages, one per gap point**.
Point 10 (Accessibility) is intentionally **excluded** per request.

> Status: **PLAN ONLY** — no test code has been written yet. Implement stages after approval.
> Per `CLAUDE.md`, `/delta-review` will be run after each stage's code changes, and `CLAUDE.md`
> updated if anything material changes.

---

## Working protocol — keep this file current as stages land

This file is a **living checklist**. As soon as a stage is fully implemented and its tests pass:

1. **Run the stage's tests** using the `▶ Run:` command printed inside that stage (it is also echoed
   back in chat when the stage is implemented).
2. **Mark its row ✅ in the status tables** (*Prerequisites status* / *Stage → point mapping*) — keep
   the row *and* its detailed section as an implementation record, so the tables always show at a
   glance what is done and where to resume in the next session.
3. If the stage introduced a dependency or convention that later stages rely on, note it in the
   relevant prerequisite's row (or `CLAUDE.md`).
4. When every row is ✅, **delete this file** — its purpose is complete.

**Always print the run command:** Whenever you add or update a test, end your response with the
ready-to-paste Gradle command that runs that specific test class (or classes) — the stage's `▶ Run:`
line, or a targeted `-Pandroid.testInstrumentationRunnerArguments.class=…` command.

**Gradle quick reference** (no product flavors; module `:app`; package `com.mauriciotogneri.fileexplorer`;
a connected device/emulator is required):

```bash
# Run ALL instrumentation tests
./gradlew :app:connectedDebugAndroidTest

# Run a single test class
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FULLY_QUALIFIED_CLASS>

# Run several classes at once (comma-separated, no spaces)
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<CLASS_A>,<CLASS_B>

# Run an entire package
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.mauriciotogneri.fileexplorer.integration
```

Each stage below carries its own ready-to-paste `▶ Run:` command targeting only that stage's classes.

---

## Decisions baked into this plan

| Decision | Choice |
| --- | --- |
| Test fidelity | **Hybrid** — render real stateless components directly; render real screens + real ViewModels (over fakes / temp dirs) where behavior wiring matters; stubs only where real wiring is impractical. |
| Android intents | **Add Espresso-Intents** — stub (`intending`) and assert (`intended`) real outgoing intents (open / share / install / `Activity` launches). |
| Config-change / restoration | **Hybrid** — `StateRestorationTester` for Compose `rememberSaveable` state; `ActivityScenario.recreate()` for ViewModel / `SavedStateHandle` survival. |

### Prerequisites status

| Done | Prereq | Item |
| :---: | --- | --- |
| ✅ | P1 | Espresso-Intents dependency |
| ⏸️ | P2 | MockK-android (deferred — add only if Stage 11 mocks instead of using the P3 seam) |
| ✅ | P3 | `FolderScreen` injectable `viewModel` (default keeps `key = path`; no behavior change) |
| ☐ | P4 | Shared test conventions |
| ✅ | P5 | Fixture helpers (`FileFixtures.kt`) — added (text/folder/apk/zip/password-zip) |

### Stage → point mapping

> Mark a stage ✅ when implemented and its tests pass; keep the row and its detailed section as a record.

| Done | Stage | Point | Area |
| :---: | --- | --- | --- |
| ✅ | 2 | 2 | File-open tap routing in `FolderScreen` (`OpenFileResult` branches) — 7 tests green |
| ✅ | 3 | 3 | `ItemInfo` metadata renderers (PDF, Office, EPUB, SQLite, VCard, iCalendar, CSV) — 15 tests green |
| ☐ | 4 | 4 | Configuration change / state restoration |
| ✅ | 5 | 5 | Sort behavior (actual reordering, not just the menu) — 7 tests green |
| ✅ | 6 | 6 | Picker "New Folder" flow + storage switching — 9 tests green |
| ✅ | 7 | 7 | Uncompress UI failure / retry loop — 4 tests green (inject VM w/ FakeStorageSource for allowed-roots) |
| ✅ | 8 | 8 | Home → Search launch & search scoping — 7 tests green |
| ✅ | 9 | 9 | Badge dots — BadgeDot component, 3 tests green (drawer-badge logic covered by HomeViewModelBadgeTest unit) |
| ☐ | 10 | 11 | Permission screen variations |
| ☐ | 11 | 12 | Folder-screen load errors |
| ✅ | 12 | 13 | Drawer → Activity round-trips — 4 tests green |

*(Point 10 — Accessibility — is omitted on purpose.)*

Legend: ✅ done · ☐ not started · ⏸️ deferred.

---

````## Shared prerequisites (do these first — before the stages below)

### P1. Add the Espresso-Intents dependency

`gradle/libs.versions.toml` (reuse existing `espressoCore = "3.7.0"`):

```toml
androidx-espresso-intents = { group = "androidx.test.espresso", name = "espresso-intents", version.ref = "espressoCore" }
```

`app/build.gradle.kts`:

```kotlin
androidTestImplementation(libs.androidx.espresso.intents)
```
````
Used by Stages 2, 8, 10, 12.

### P2. Add a MockK android artifact (only if Stage 11 uses mocking instead of the screen seam)

`gradle/libs.versions.toml`:

```toml
mockk-android = { group = "io.mockk", name = "mockk-android", version.ref = "mockk" }
```

`app/build.gradle.kts`:

```kotlin
androidTestImplementation(libs.mockk.android)
```

> Decision deferred to Stage 11 — prefer the production seam (P3) over mocking. Add this only if
> we choose the mock route there.

### P3. Production seam: make `FolderScreen` accept an injectable `viewModel` (REQUIRES APPROVAL)

`FolderScreen` currently builds its own `FolderViewModel` internally:

```kotlin
// FolderScreen.kt (current)
val viewModel: FolderViewModel = viewModel(
    factory = FolderViewModel.Factory(context.applicationContext as Application, path, title)
)
```

`HomeScreen` and `SearchScreen` already expose `viewModel` as a default-valued parameter. Proposed
**backward-compatible** change (mirrors the existing pattern, no behavior change):

```kotlin
fun FolderScreen(
    path: String,
    title: String? = null,
    rootPath: String? = null,
    rootDisplayName: String? = null,
    onNavigateToFolder: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: FolderViewModel = viewModel(
        factory = FolderViewModel.Factory(LocalContext.current.applicationContext as Application, path, title)
    )
) { ... }
```

This unblocks: **Stage 11** (forcing a load-error state) and the **folder context-menu badge** in
Stage 9. Stages 2 and 5 do **not** need it (they use real files on a temp dir).

> This is the only production code change in the plan. It is flagged for explicit approval per the
> "Strict Scope" rule in `CLAUDE.md`. If rejected, Stage 11 falls back to MockK-android (P2) plus a
> narrower seam, and the folder context-menu badge case in Stage 9 is dropped.

### P4. Shared test conventions

- **Naming:** mirror existing layout, e.g. `ui/components/FileActionsBottomSheetTest.kt`,
  `integration/FileOpenRoutingTest.kt`. Method names follow the existing
  `feature_condition_expectedOutcome()` convention.
- **Rules:** `createComposeRule()` for pure-composable tests; `createAndroidComposeRule<ComponentActivity>()`
  where an `Activity`/`Context`, Espresso-Intents, or `recreate()` is needed (both already used in the suite).
- **Temp dirs / real repos:** reuse the established pattern —
  `testDir = File(context.cacheDir, "test_<name>_${System.currentTimeMillis()}")`, real `FileRepository()`,
  cleanup in `@After` with `testDir.deleteRecursively()`.
- **Storage fake:** reuse `testutil/FakeStorageSource(testDir)`; extend with a multi-storage variant for Stage 6.
- **Espresso-Intents lifecycle:** `Intents.init()` in `@Before`, `Intents.release()` in `@After`;
  always `intending(...)` to stub before performing the action so no real external app launches.
- **String assertions:** use `context.getString(R.string.…)` (never hardcoded UI text), consistent with the suite.
- **New fakes to add under `androidTest/.../testutil/`:**
  - `MultiStorageFakeStorageSource` (Stage 6, Stage 8 scoping).
  - `FakePreferencesSource` for androidTest (Stage 9) — mirror the existing
    `app/src/test/.../data/source/FakePreferencesSource.kt`.
  - `ThrowingFileRepository` or a MockK mock (Stage 11).

### P5. Fixture helpers

Add `testutil/FileFixtures.kt` with helpers reused across stages:
- `createTextFile(dir, name, content)`, `createFolder(dir, name)`.
- `createZip(dir, name, entries)` and `createPasswordZip(dir, name, password, entries)` — extract the
  zip-creation logic already used by `FileOperationsEndToEndTest` (compress / password-protected cases)
  into a shared helper rather than duplicating it.
- `createFakeApk(dir, name)` — a file with `.apk` extension (routing is MIME/extension based via
  `MimeTypeUtil.isApk`; contents are irrelevant for the permission-dialog branch).
- `FileItem` factory for component tests (see existing `FileListItemTest` for the shape).

---

## Stage 2 (Point 2) — File-open tap routing in `FolderScreen`

**Goal:** Verify the real tap-routing in `FolderScreen` (`IntentUtil.openFile` → `OpenFileResult`
branches): zip → uncompress dialog, apk → install-permission dialog, regular file → `ACTION_VIEW`.

**Under test:** `FolderScreen.kt` (tap handler at the file-list item) +
`util/IntentUtil.openFile` + `FolderViewModel.showUncompressDialog` / `setPendingApkInstall`.

**New file:** `integration/FileOpenRoutingTest.kt`

**▶ Run:**
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.integration.FileOpenRoutingTest
```

**Approach:** Real-wiring. Render the real `FolderScreen(path = tempDir, onNavigateToFolder, onNavigateBack)`
pointed at a temp dir populated with real fixtures (P5). The real `FolderViewModel`/`FileRepository`
list them. Wrap with `Intents.init()/release()` and `intending(anyIntent())` so taps don't actually
launch external apps. Use `createAndroidComposeRule<ComponentActivity>()`.

**Test cases:**
1. `tapRegularFile_firesViewIntent` — `intended(allOf(hasAction(ACTION_VIEW), hasFlag(GRANT_READ_URI_PERMISSION)))`.
2. `tapImageFile_firesViewIntent_withImageMime` (sanity that the chooser/view path is taken).
3. `tapZipFile_showsUncompressDialog` — non-password zip → `UncompressDialog` (assert title + entry count text).
4. `tapPasswordZip_showsPasswordDialog` — password-protected zip → `PasswordUncompressDialog` (assert password field present).
5. `tapApkFile_noInstallPermission_showsApkPermissionDialog` — assert `ApkPermissionDialog` title/message.
6. `apkPermissionDialog_settingsButton_firesManageUnknownSourcesIntent` — `intended(hasAction(ACTION_MANAGE_UNKNOWN_APP_SOURCES))`.
7. `tapFolder_navigates_doesNotFireViewIntent` — `onNavigateToFolder` invoked; `Intents` records no `ACTION_VIEW`.
8. `tapRegularFile_recordsRecentFile_whenTrackingEnabled` *(optional)* — assert via `RecentFilesRepository` over a test DataStore; skip if DataStore isolation proves flaky.

**Risks/notes:**
- The apk branch depends on `IntentUtil.canInstallApks(context)` → `canRequestPackageInstalls()`, which is
  normally **false** on a fresh emulator (so the permission dialog shows). Document this environment
  assumption; if an emulator has the permission pre-granted, cases 5–6 will instead route to the install
  intent — guard with an `assumeFalse(IntentUtil.canInstallApks(context))` so the test self-skips rather than flakes.
- `ACTION_VIEW` resolution: `intending(anyIntent()).respondWith(Instrumentation.ActivityResult(RESULT_OK, null))`
  prevents real chooser/app launch.

**Est. tests:** ~8

---

## Stage 3 (Point 3) — `ItemInfo` metadata renderers (7 types)

**Goal:** Render the info screen for the 7 metadata types the existing `ItemInfoScreenTest` never
exercises: **PDF, Office, EPUB, SQLite, VCard, iCalendar, CSV**.

**Under test:** `ItemInfoScreen.kt` sections gated at lines ~393–425
(`pdfMetadata`, `officeMetadata`, `epubMetadata`, `sqliteMetadata`, `vcardMetadata`,
`icalendarMetadata`, `csvMetadata`) and their `*MetadataSection` composables.

**New file:** `ui/screens/iteminfo/ItemInfoMetadataTest.kt` (keep `ItemInfoScreenTest.kt` as-is; this
extends coverage following its exact pattern of constructing a populated `ItemInfoUiState` and
rendering the real content).

**▶ Run:**
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.ui.screens.iteminfo.ItemInfoMetadataTest
```

**Approach:** Component/render with hand-constructed `*Metadata` objects (no binary fixtures —
consistent with how image/audio/video/apk/zip are already tested). Assert the section's labels
(`R.string.info_*`) and formatted values appear.

**Metadata field reference (drives assertions):**
- `PdfMetadata(pageCount)`
- `OfficeMetadata(title, creator, subject, keywords, createdDate, modifiedDate)`
- `EpubMetadata(title, creator, publisher, language, date, description)`
- `SqliteMetadata(tableCount, tableNames, totalRowCount)`
- `VCardMetadata(contactCount, hasPhoneNumbers, hasEmails, hasPhotos)`
- `ICalendarMetadata(eventCount, todoCount, earliestDate, latestDate)`
- `CsvMetadata(rowCount, columnCount)`

**Test cases (per type: a "populated" render + a "nulls handled" render):**
1. `pdfInfo_displaysPageCount`
2. `pdfInfo_nullPageCount_sectionHandlesGracefully`
3. `officeInfo_displaysTitleCreatorSubjectKeywordsDates`
4. `officeInfo_partialNulls_showsOnlyPresentFields`
5. `epubInfo_displaysTitleCreatorPublisherLanguageDateDescription`
6. `epubInfo_partialNulls_showsOnlyPresentFields`
7. `sqliteInfo_displaysTableCountAndRowCount`
8. `sqliteInfo_displaysTableNames`
9. `vcardInfo_displaysContactCount`
10. `vcardInfo_displaysBooleanCapabilities` (phones/emails/photos true)
11. `icalendarInfo_displaysEventAndTodoCounts`
12. `icalendarInfo_displaysDateRange`
13. `csvInfo_displaysRowAndColumnCounts`
14. `csvInfo_nulls_handledGracefully`
15. `metadataSection_onlyMatchingTypeRendered` — e.g. with only `csvMetadata` set, no PDF/EPUB labels appear (guards the `if (… != null)` gating).

**Risks/notes:** Confirm the exact value formatting each section uses (e.g. counts via `<plurals>`),
and assert with the same `stringResource`/`pluralStringResource` the screen uses. Verify the label
string IDs against `ItemInfoScreen.kt` before writing assertions (some types may render a subset).

**Est. tests:** ~15

---

## Stage 4 (Point 4) — Configuration change / state restoration

**Goal:** Verify UI/state survives recreation. None exist today.

**Under test:** `rememberSaveable`/`SavedStateHandle`-backed state across Home, Folder, Search,
Feedback, Picker.

**New file:** `integration/StateRestorationTest.kt`

**▶ Run:**
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.integration.StateRestorationTest
```

**Approach (Hybrid):**
- **Composable-level** via `StateRestorationTester(composeTestRule)`: set content through the tester,
  drive state, call `tester.emulateSavedInstanceStateRestore()`, assert state preserved.
- **Activity/VM-level** via `createAndroidComposeRule<ComponentActivity>()` +
  `composeTestRule.activityRule.scenario.recreate()` for real `Activity` recreation (config change).

**Test cases:**
1. `folder_selectionMode_survivesRecreation` — enter selection mode, select 2 items, recreate, assert
   action bar + selected count restored.
2. `folder_openCreateFolderDialog_survivesRecreation` — open dialog, type a partial name, recreate,
   assert dialog still open with text. *(If it does NOT survive — likely if not `rememberSaveable` —
   record as a finding; see note.)*
3. `folder_scrollPosition_survivesRecreation` — scroll a long list, recreate, assert position retained.
4. `search_queryAndResults_surviveRecreation` — type query, get results, recreate, assert query + results.
5. `feedback_typedText_survivesRecreation` — type feedback text, recreate, assert text + char counter.
6. `picker_navigationState_survivesRecreation` — navigate into a subfolder, recreate, assert still in it.
7. `home_drawerOpen_state` *(optional)* — drawer open state behavior across recreation.

**Risks/notes:**
- This stage **may surface real bugs** (state that is *not* currently saved). Where a test reveals
  non-restoration, the honest outcome is a documented finding + a follow-up decision (fix prod with
  `rememberSaveable`/`SavedStateHandle`, or assert the current behavior). Do **not** silently weaken the
  assertion — flag it. Per "Plan & Pause", any production fix is raised separately.
- `StateRestorationTester` only restores `rememberSaveable`; ViewModel survival needs the `recreate()` path.

**Est. tests:** ~6–7

---

## Stage 5 (Point 5) — Sort behavior (actual reordering)

**Goal:** Verify that selecting each sort mode actually **reorders** the list (existing tests only
confirm the sheet shows options and a callback fires).

**Under test:** real `FolderScreen` sort sheet (6 modes wired to `onSortModeSelected`) →
`FolderViewModel.setSortMode` → `SortManager` → `FileRepository.sortFiles` → reload.

**New file:** `integration/FolderSortingTest.kt`

**▶ Run:**
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.integration.FolderSortingTest
```

**Approach:** Real-wiring on a temp dir with fixtures of **known, distinct** name / size / mtime so
each ordering is unambiguous. Read row order via `onAllNodesWithTag("file_row")` (add a stable
`testTag` to the list item if one isn't already present — verify first) or via
`onNodeWithText(...).assertExists()` plus positional `onChildAt`.

**Fixtures:** e.g. `a.txt` (1 KB, oldest), `b.txt` (3 KB, middle), `c.txt` (2 KB, newest), plus a
folder `dir/` — set sizes by content length and mtimes via `File.setLastModified(...)`.

**Test cases:**
1. `sortNameAsc_ordersAlphabetically`
2. `sortNameDesc_ordersReverseAlphabetically`
3. `sortSizeAsc_ordersBySizeAscending`
4. `sortSizeDesc_ordersBySizeDescending`
5. `sortDateAsc_ordersByModifiedAscending`
6. `sortDateDesc_ordersByModifiedDescending`
7. `foldersOrderedRelativeToFiles_perSortContract` — assert the documented folders-vs-files rule
   (confirm the intended behavior from `SortManager`/`FileRepository.sortFiles` first).
8. `selectedSortMode_isIndicatedInSheet` — reopen sheet, assert the active mode is marked selected.

**Risks/notes:**
- **`SortManager` is a process-global singleton** and `FolderViewModel` also **persists** sort mode to
  DataStore. Reset to `SortMode.NAME_ASC` in `@Before` and `@After` (e.g. `SortManager.setSortMode(NAME_ASC)`)
  to prevent order-dependent flakiness across tests/classes.
- Confirm a stable list-item `testTag` exists; if not, adding one is a trivial, justifiable test seam
  (flag in review).

**Est. tests:** ~8

---

## Stage 6 (Point 6) — Picker "New Folder" flow + storage switching

**Goal:** Cover the end-to-end create-folder-in-picker flow and multi-storage switching (existing
tests only check the New-Folder button fires a callback and basic navigation).

**Under test:** `DestinationPicker.kt` + `PickerViewModel` (`createFolder`, `navigateToStorage`,
`navigateUp`, `showStorageSelector`).

**New file:** `integration/PickerCreateFolderTest.kt` (and extend storage-switch cases here).

**▶ Run:**
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.integration.PickerCreateFolderTest
```

**Approach:** Real-wiring. Build the real `PickerViewModel` via `PickerViewModel.Factory(app,
FileRepository(), StorageRepository(fakeStorage), sourceItems, mode, sort, showHidden)` exactly as
`PickerNavigationIntegrationTest` already does, and render `DestinationPicker`.

**Test cases — create folder:**
1. `newFolder_dialogOpens_onButtonTap`.
2. `newFolder_validName_createsOnDisk` — assert `File(currentPath, name).exists()` and `isDirectory`.
3. `newFolder_appearsInPickerList_afterCreate`.
4. `newFolder_navigatesIntoNewFolder_afterCreate` — `currentPath` becomes the new folder (per `createFolder` → `navigateToPath`).
5. `newFolder_duplicateName_showsError` — uses `getExistingNames()`.
6. `newFolder_cancel_doesNotCreate`.
7. `newFolder_thenConfirm_returnsNewFolderPath` — full round trip: create → Move/Copy Here → `onConfirm(newPath)`.

**Test cases — storage switching (needs `MultiStorageFakeStorageSource` returning 2 dirs):**
8. `multipleStorages_showsStorageSelector` — `showStorageSelector` true at root.
9. `selectStorage_navigatesIntoIt_showsItsFolders`.
10. `navigateUpFromStorageRoot_returnsToStorageSelector` (multi-storage `navigateUp` contract).
11. `singleStorage_skipsSelector_goesStraightToFolders` (existing fake behavior — assert no selector).
12. `confirmInSecondStorage_returnsPathUnderThatStorage`.

**Risks/notes:** `PickerViewModel.loadFolders` filters to `item.isDirectory && File(item.path).canWrite()`.
Temp dirs under `cacheDir` are writable, so created folders appear; document this so fixtures live under
the fake storage root.

**Est. tests:** ~12

---

## Stage 7 (Point 7) — Uncompress UI failure / retry loop

**Goal:** Cover the *UI* of the uncompress flow including the wrong-password retry loop (existing
tests cover the dialogs in isolation and the repository throwing, but not the screen-level loop).

**Under test:** `FolderViewModel` uncompress path via `UncompressHandler` (`showUncompressDialog`,
`confirmUncompress(password)`, state `isPasswordProtected`/`uncompressProgress`) + `PasswordUncompressDialog`
/ `UncompressDialog` / `UncompressProgressDialog` as wired in `FolderScreen`.

**New file:** `integration/UncompressFlowTest.kt`

**▶ Run:**
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.integration.UncompressFlowTest
```

**Approach:** Real-wiring on a temp dir. Create real zips (plain + password-protected) via the P5
helper. Render real `FolderScreen` (or, if the global `UncompressHandler`/`Toast` proves awkward,
drive a real `FolderViewModel` directly and assert `state`/`events`). Prefer the screen-level test;
fall back to VM-level for the retry assertion if needed.

**Test cases:**
1. `tapPasswordZip_showsPasswordDialog` (also covered in Stage 2; here it's the entry to the loop).
2. `passwordZip_correctPassword_extractsAndShowsFiles` — extracted entries appear in the folder list after reload.
3. `passwordZip_wrongPassword_showsErrorToast_andReprompts` — assert the error event/toast
   (`FolderUiEvent.ShowToastRes`) and that the password dialog can be re-submitted.
4. `passwordZip_wrongThenCorrect_succeeds` — the actual retry loop end-to-end.
5. `plainZip_extracts_withoutPasswordPrompt`.
6. `uncompress_inProgress_showsProgressDialog` — `UncompressProgressDialog` visible with current file.
7. `uncompress_cancel_stopsExtraction` — `cancelUncompression()`; progress dialog dismissed.
8. `uncompress_conflictingNames_createUniqueEntries` *(optional, if not already in E2E)*.

**Risks/notes:**
- `events` are a `SharedFlow` (one-time) — collect within the test (`composeTestRule.awaitIdle()` +
  a collector) or assert the resulting UI (toast text via Espresso `onView(withText(...))` in the toast
  window, which is brittle) — prefer asserting VM `events` for the error case.
- Reuse the password-zip creation already proven in `FileOperationsEndToEndTest`.

**Est. tests:** ~7–8

---

## Stage 8 (Point 8) — Home → Search launch & search scoping

**Goal:** Verify (a) the Home search bar actually launches `SearchActivity`, and (b) search results
are scoped to the provided storage root(s).

**Under test:** `HomeScreen` search-bar wiring (`onSearchContainerClick`/`onSearchIconClick` →
`startActivity(Intent(context, SearchActivity::class.java))`) and `SearchViewModel.performSearch` →
`FileRepository.searchFilesStreaming(rootPath = …)` over `storageRepository.getStorages()`.

**New files:** `integration/HomeSearchLaunchTest.kt`, `integration/SearchScopingTest.kt`

**▶ Run:**
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.integration.HomeSearchLaunchTest,com.mauriciotogneri.fileexplorer.integration.SearchScopingTest
```

**Approach:**
- **Launch:** render real `HomeScreen` (inject a `HomeViewModel` if needed, but default is fine);
  `Intents.init()`, tap the search bar / icon, `intended(hasComponent(SearchActivity::class.java.name))`.
- **Scoping:** build a real `SearchViewModel(app, FileRepository(), StorageRepository(fakeStorage))`
  where the fake exposes a temp-dir root containing some matching + non-matching files, render
  `SearchScreen(onBackClick = {}, viewModel = thatVm)`, type a query, assert only in-root matches show.

**Test cases — launch:**
1. `searchContainerTap_launchesSearchActivity`.
2. `searchIconTap_launchesSearchActivity`.

**Test cases — scoping/behavior over real streaming results:**
3. `search_returnsMatchesWithinRoot`.
4. `search_excludesFilesOutsideRoot` — files outside the fake storage root never appear.
5. `search_deduplicatesAcrossOverlappingRoots` — two roots, one nested; matching file shown once
   (covers the `seenPaths` dedup in `SearchViewModel`).
6. `search_streamingResults_appendIncrementally` — partial results render before completion (pairs
   with the existing `SearchBehaviorTest`, but over the real repo).
7. `search_clearQuery_resetsResults`.
8. `search_backButton_invokesOnBackClick`.

**Risks/notes:** `SearchActivity` ignores the `root` arg (its NavGraph route is a placeholder) — do
**not** assert root-scoped *launch* args; scoping is asserted at the `SearchViewModel` level via
injected storages. Note this divergence in the test file header.

**Est. tests:** ~8

---

## Stage 9 (Point 9) — Badge dots

**Goal:** Verify the new-feature badge dots render when not dismissed and disappear after the
associated item is opened. Currently only `HomeViewModelBadgeTest` (unit) covers the logic.

**Under test:** `ui/components/BadgeDot.kt`; Home drawer badges (Settings/Feedback/About) +
menu badge; folder context-menu badge.

**New files:** `ui/components/BadgeDotTest.kt`, `integration/BadgeIntegrationTest.kt`

**▶ Run:**
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.ui.components.BadgeDotTest,com.mauriciotogneri.fileexplorer.integration.BadgeIntegrationTest
```

**Approach:**
- **Component:** `BadgeDot(showBadge, content)` — trivial, deterministic.
- **Integration (Home):** `HomeScreen` already accepts an injectable `viewModel`. Build a
  `HomeViewModel` over a fresh **`FakePreferencesSource`** (added in P4) so badge state is
  deterministic (no shared DataStore). Assert dot visible, open the drawer item, assert dismiss
  callback persisted (badge gone on recomposition).
- **Integration (Folder context menu):** needs the P3 seam to inject a `FolderViewModel` over a fake
  prefs source. If P3 is rejected, drop this one case.

**Test cases:**
1. `badgeDot_showBadgeTrue_dotDisplayed`.
2. `badgeDot_showBadgeFalse_dotHidden`.
3. `badgeDot_wrapsContent_contentStillDisplayedAndClickable`.
4. `home_settingsItem_showsBadge_whenNotDismissed`.
5. `home_settingsItem_badgeDismissed_afterOpen`.
6. `home_feedbackItem_showsBadge_whenNotDismissed` / `…_dismissedAfterOpen`.
7. `home_aboutItem_showsBadge_whenNotDismissed` / `…_dismissedAfterOpen`.
8. `home_menuBadge_dismissed_afterDrawerOpened`.
9. `folder_contextMenuBadge_showsThenDismisses_afterMenuOpened` *(requires P3)*.

**Risks/notes:** Use a fake `PreferencesSource`, never the real DataStore, to keep tests isolated and
order-independent. Badge dot has no text — assert via a `testTag` on `BadgeDot` (add one if absent —
trivial seam) or by structural/semantics matching.

**Est. tests:** ~10

---

## Stage 10 (Point 11) — Permission screen variations

**Goal:** Extend beyond the existing render/click/theme tests to the grant action and resume-based
re-check, within what instrumentation can reliably do.

**Under test:** `PermissionScreen.kt` — grant button branches (Android 11+ → `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`
intent; below → runtime permission launcher) and the `repeatOnLifecycle(RESUMED)` re-check that calls
`onPermissionGranted()` when permission is present.

**New file:** `ui/screens/permission/PermissionScreenActionsTest.kt`

**▶ Run:**
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.ui.screens.permission.PermissionScreenActionsTest
```

**Approach:** `createAndroidComposeRule<ComponentActivity>()` + Espresso-Intents.

**Test cases:**
1. `grantButton_onR_plus_firesManageAllFilesAccessIntent` —
   `intended(allOf(hasAction(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION), hasData("package:<pkg>")))`.
   Guard with `assumeTrue(Build.VERSION.SDK_INT >= R)`.
2. `grantButton_belowR_launchesRuntimePermissionRequest` — guard `assumeTrue(SDK_INT < R)`; assert the
   permission dialog intent is recorded (or self-skip on modern emulators).
3. `permissionAlreadyGranted_onResume_invokesOnPermissionGranted` — use `GrantPermissionRule` (legacy
   path) or `assumeTrue` MANAGE access; assert `onPermissionGranted` fires. Document that on R+ this
   requires `MANAGE_EXTERNAL_STORAGE` which can't be granted by `GrantPermissionRule`, so this case may
   be **API-conditional** / skipped on R+.
4. `permissionScreen_grantButton_isClickable_andLabeled` (lightweight, always-runs sanity).

**Risks/notes:** Real permission state can't be freely toggled in instrumentation on R+. Keep this
stage **honest and conditional** — verify the *intent we fire* (fully controllable) and use
`assume*` to self-skip cases that depend on un-grantable state, rather than forcing brittle passes.
The analytics-only branches (permanently-denied / returned-without-granting) are not UI-observable and
are explicitly **out of scope** here (covered by intent + analytics unit tests if desired).

**Est. tests:** ~4 (2–3 effective on a given API level)

---

## Stage 11 (Point 12) — Folder-screen load errors

**Goal:** Cover the `FolderScreen` error state (`state.error` set when listing throws) and
empty/permission-style outcomes that the UI must render.

**Under test:** `FolderViewModel.loadFiles()` catch branch → `FolderUiState.error` →
`FolderScreen` error rendering.

**New file:** `ui/screens/folder/FolderLoadErrorTest.kt`

**▶ Run:**
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderLoadErrorTest
```

**Approach (depends on P3 seam):** `FileRepository.listFiles` returns an empty list (not a throw) for
missing paths, so the error branch can't be triggered with real files. Inject a `FolderViewModel`
built over a **`ThrowingFileRepository`** (or a MockK mock) whose `listFiles` throws, then render
`FolderScreen(path, …, viewModel = thatVm)` using the P3 parameter.
- **If P3 approved:** subclass/fake or `mockk` the repo and inject the VM (cleanest).
- **If P3 rejected:** add MockK-android (P2) and test `FolderViewModel` + a minimal real
  error-content composable, OR descope to asserting the empty-state path only.

**Test cases:**
1. `listFilesThrows_showsErrorState` — assert `R.string.error_load_files` text rendered, list hidden.
2. `errorState_then_refresh_recovers` — repo throws once then succeeds; `refresh()` clears the error
   and shows files.
3. `emptyDirectory_showsEmptyState_notError` — distinguishes empty (real) from error.
4. `nonExistentPath_showsEmptyState` — confirms current behavior (returns empty → empty state, not error).
5. `loading_showsProgressIndicator_beforeListReturns` *(optional)* — assert the `isLoading` spinner if a controllable delay is feasible.

**Out of scope (documented):** true "SD card removed mid-session" and "permission revoked
mid-session" require device-state manipulation not reliably available in instrumentation — note as a
known limitation rather than faking it unconvincingly.

**Risks/notes:** This stage is the primary justification for P3. Decide P3 vs MockK before starting.

**Est. tests:** ~4–5

---

## Stage 12 (Point 13) — Drawer → Activity round-trips

**Goal:** Verify the navigation drawer actually launches the right `Activity` (existing tests only
check the click callbacks fire), and the Info action from the folder launches `ItemInfoActivity`.

**Under test:** Home drawer item clicks → `startActivity` for `SettingsActivity` / `FeedbackActivity` /
`AboutActivity`; folder file Info → `ItemInfoActivity.createIntent`; About-screen rows →
`OtherAppsActivity` / `LegalActivity`.

**New file:** `integration/ActivityNavigationTest.kt`

**▶ Run:**
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.integration.ActivityNavigationTest
```

**Approach:** `createAndroidComposeRule<ComponentActivity>()` + Espresso-Intents; render the real
`HomeScreen` (and real `FolderScreen` for the Info case), open the drawer, tap items, assert
`intended(hasComponent(...))`. `intending(...)` stubs each so the target Activity doesn't actually start.

**Test cases:**
1. `drawerSettings_launchesSettingsActivity`.
2. `drawerFeedback_launchesFeedbackActivity`.
3. `drawerAbout_launchesAboutActivity`.
4. `folderFileInfo_launchesItemInfoActivity_withFilePathExtra` — `intended(allOf(hasComponent(ItemInfoActivity),
   hasExtra(EXTRA_FILE_PATH, path)))` (the extra key is private — assert via `hasComponent` + that an extra exists,
   or expose a const; prefer component-only assertion to avoid a production change).
5. `aboutOtherApps_launchesOtherAppsActivity` *(optional — extends `AboutScreenTest` from callback to real intent)*.
6. `aboutPrivacy_launchesLegalActivity_privacy` / `aboutTerms_launchesLegalActivity_terms` *(optional)*.

**Risks/notes:** Keep these as intent-launch assertions only (don't drive the launched Activities —
each target screen is already unit/instrumentation-tested in isolation). The Info case can reuse the
Stage 2 folder harness.

**Est. tests:** ~4–6

---

## Suggested execution order

1. **P1** (espresso-intents) — unblocks Stages 2, 8, 10, 12.
2. **Stage 3** (renderers, self-contained).
3. **P5 fixtures helper**, then **Stage 2** (proves the real-`FolderScreen` + intents harness reused later).
4. **Stage 5**, **Stage 7** (reuse the temp-dir/zip harness from Stage 2 / P5).
5. **Stage 6**, **Stage 8** (picker + search real-wiring; add multi-storage fake).
6. **Stage 12** (intents; reuses Stage 2 folder harness).
7. **Stage 9** (badges; add androidTest `FakePreferencesSource`).
8. **Decide P3** → **Stage 11** (load errors) + folder-badge case of Stage 9.
9. **Stage 4** (restoration — last, since it may surface prod findings to triage).
10. **Stage 10** (permission — API-conditional; isolate so CI on one API level is deterministic).

## Definition of done (per stage)

- New test file(s) compile and **pass** via the stage's `▶ Run:` command on a connected device/emulator.
- No hardcoded UI strings — all assertions via `R.string` resources.
- Tests are isolated: temp dirs cleaned in `@After`; global `SortManager` and any DataStore reset;
  `Intents.release()` always called.
- `/delta-review` run on the stage's diff; findings addressed.
- `CLAUDE.md` testing section updated if conventions/deps changed (e.g. espresso-intents added).
- **Per the Working protocol: remove this stage's section (and its mapping-table row) from this file**
  once it is green, so the document always reflects only remaining work.

## Estimated total

~12 new test files, ~110–120 new instrumentation tests, 1 small production seam (P3, optional),
1–2 new test dependencies (espresso-intents required; mockk-android conditional).

## Open risks / honesty notes

- **Stage 4** and **Stage 11** can reveal that some state isn't restored / some errors aren't surfaced.
  Those are findings to triage, not assertions to soften.
- **Stage 2 (apk)** and **Stage 10** depend on emulator permission state — use `assume*` to self-skip
  rather than produce flaky passes; document the API/level assumptions in each file header.
- **Stage 8** scoping is asserted at the ViewModel level because `SearchActivity` does not currently
  consume a `root` argument.
