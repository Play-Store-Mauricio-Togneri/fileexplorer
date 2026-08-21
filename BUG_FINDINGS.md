# Bug Findings

Scope: `git diff d0b63a1c283c63959fcd3e1195f5e367171e529d...HEAD` (54 commits). Only defects the
reviewed changes **introduce** are reported. Pre-existing defects appear solely as rows in the
candidate disposition table.

## Medium

### [a/state-and-lifecycle/folder-navigation/breadcrumb-depth-exceeds-back-stack] Ancestor breadcrumb on a startup folder empties the nav back stack and freezes the screen

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/FolderActivity.kt:166`
  (related: `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:95`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/util/StartupDestinationResolver.kt:60`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/components/BreadcrumbPathParser.kt:30`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderScreen.kt:114`)
- **Severity:** Medium
- **Confidence:** Medium
- **Defect:** `onNavigateToFolder` assumes the breadcrumb trail and the `NavController` back stack
  have the same depth — it computes `levelsBack = currentSegments.size - targetSegments.size` and
  pops that many times. `MainActivity.openStartupFolder()` is the first caller to pass a `rootPath`
  that is an **ancestor** of `path`, so the trail is trimmed to the storage root and renders
  clickable ancestors that were never pushed. Tapping one pops more entries than exist. The tap is a
  visual no-op, but the back stack is silently emptied, which drops the surviving entry's
  `maxLifecycle` to `CREATED`; `FolderScreen` collects its state with `collectAsStateWithLifecycle`,
  so collection stops and the folder freezes at its last-rendered content — later deletes, renames,
  sort changes and refreshes never appear.
- **Trigger:** Settings → Startup screen → "Specific folder" → pick any folder **below** a storage
  root (e.g. `/storage/emulated/0/Download`). Cold start. Tap the "Internal storage" breadcrumb.
- **Evidence / verification:** `BreadcrumbPathParser.parsePath("/storage/emulated/0/Download", …,
  rootPath="/storage/emulated/0")` yields 2 items; `Breadcrumbs.kt:119` sets
  `clickable(enabled = !isLast)`, so index 0 is tappable. `FolderNavHost`
  (`FolderActivity.kt:113-185`) declares one `composable`, so the stack holds one folder entry;
  `levelsBack = 4 - 3 = 1`. A separate refutation pass read the resolved
  `androidx.navigation 2.9.8` sources: `NavControllerImpl.popBackStack()` returns
  `popped && dispatchOnDestinationChanged()`, and `dispatchOnDestinationChanged()` returns
  `lastBackStackEntry != null` — **false only after the pop has already happened** — so the guard
  `if (!navController.popBackStack()) return@…` fires too late.
  That refutation **disproved a stronger initial claim** (a permanently blank screen):
  `NavigatorState.kt:130` registers the entry in `_transitionsInProgress` before `pop()`, so
  `populateVisibleEntries()` keeps it and `NavHost` still renders. The surviving symptom is the
  freeze, not a blank screen. Attribution: all seven baseline `createIntent` call sites
  (`HomeScreen.kt:326,334,371,455,555`, `SearchScreen.kt:259,339`) pass `rootPath == path`, and the
  pop logic is byte-identical at baseline — the invariant only breaks in the new configuration.
  `StartupScreenTest.kt:120-137` pins that configuration as intended but never taps the breadcrumb;
  `NavigationIntegrationTest` and `BreadcrumbsIntegrationTest` use a fake `onNavigateToFolder` and
  never exercise the real `NavHost`. **Remaining assumption:** the library trace and the freeze are
  established by reading sources, not by running on a device — no emulator was available.
- **Suggested fix:** stop deriving the pop count from path depth. Either resolve the target against
  the actual back-stack entries and navigate forward when no entry matches, or clamp `levelsBack` to
  the current entry count minus one so the launch destination can never be popped.

### [a/concurrency/feedback-screen/http-client-constructed-during-composition] A test seam moves TLS trust-manager initialization onto the main thread

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/FeedbackActivity.kt:257`
  (related: `:269`, `:278`)
- **Severity:** Medium
- **Confidence:** High
- **Defect:** `Factory` gained a test seam, `private val httpClient: OkHttpClient = client`. Because
  it is a **constructor default**, it is evaluated wherever the `Factory` is constructed — and
  `FeedbackScreen`'s own default argument constructs one during composition. That forces the
  `by lazy { OkHttpClient() }`, and `OkHttpClient`'s constructor eagerly resolves the platform trust
  manager, which reads the system CA store from disk. All of it runs on the main thread, violating
  the project rule that I/O never blocks it.
- **Trigger:** Open the drawer → Feedback. First composition, once per process.
- **Evidence / verification:** Decompiled the resolved `okhttp 5.5.0` artifact:
  `javap -p -c okhttp3/OkHttpClient.class` shows `Platform.get().platformTrustManager()` and
  `Platform.get().newSslSocketFactory(…)` invoked inside
  `public okhttp3.OkHttpClient(okhttp3.OkHttpClient$Builder)` — the constructor, not a lazy path.
  At baseline the `Factory` was `class Factory(private val application: Application)` with no
  `client` reference, and the first touch was `client.newCall(request)` at `:149`, inside
  `viewModelScope.launch(Dispatchers.IO)` (`:140`). Refutation attempt: `by lazy` caps the cost at
  once per process rather than per recomposition — that bounds the severity but does not move the
  work off the main thread; nothing else touches this private `client` earlier.
- **Suggested fix:** make the seam lazy at the call site — take a `() -> OkHttpClient`, or an
  `OkHttpClient? = null` resolved inside `create()` — so production never forces the client while
  composing.

### [a/state-and-lifecycle/settings-startup-folder-picker/picker-state-not-retained-across-recreation] Rotating during startup-folder selection discards the whole flow

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/SettingsActivity.kt:143`
  (related: `app/src/main/AndroidManifest.xml:72`)
- **Severity:** Medium
- **Confidence:** High
- **Defect:** The new startup-folder `DestinationPicker` is held only in
  `var startupFolderPicker by remember { mutableStateOf<PickerRequest?>(null) }` — plain `remember`,
  not `rememberSaveable`, and not hoisted into a ViewModel. `SettingsActivity` is the one Activity
  that declares no `android:configChanges`, so any configuration change recreates it and destroys an
  in-progress, multi-level folder selection with nothing saved.
- **Trigger:** Settings → Startup screen → "Specific folder" → navigate several levels into the
  picker → rotate the device, or toggle system dark mode. The picker disappears, the user is back on
  the settings list, and the startup screen is unchanged.
- **Evidence / verification:** `AndroidManifest.xml:55` (MainActivity) and `:67` (FolderActivity)
  both declare `configChanges="orientation|screenSize|screenLayout|keyboardHidden"`; `:72-74`
  (SettingsActivity) does not, and there is no `screenOrientation` or `setRequestedOrientation`
  anywhere in `app/src/main`. Refutation attempt: `FolderScreen`'s picker survives because its
  `PickerRequest` lives in `FolderUiState.pickerRequest` (`FolderScreen.kt:584`), not in `remember` —
  so this is a new asymmetry introduced with the feature, not a shared limitation. The settings
  screen's pre-existing dialogs were already lost on rotation; what is new is a multi-step
  full-screen flow being subject to it.
- **Suggested fix:** hold the picker request in `SettingsViewModel` alongside the other settings
  state, matching how `FolderScreen` already does it; `rememberSaveable` with a `PickerRequest`
  saver would also work but leaves the picker's own navigation depth unsaved.

### [c/dead-or-unreachable-behavior/test-structure-guards/context-window-consumes-its-own-anchor] Discarded-assertion guard cannot fire on the most common form of the bug it targets

- **Location:** `scripts/check_tests.py:205`
- **Severity:** Medium
- **Confidence:** High
- **Defect:** The look-back window is `lines[max(0, i - 2): i + 1]` — it includes the matched line
  **and** the enclosing `fun` line. `CONSUMING` (`:184`) matches `\bfun\b|\bval\b|\bvar\b|\bif\b|->|assert|&&|\|\|`,
  so a discarded `fetchSemanticsNodes()` result is skipped whenever it is the first statement of a
  test, or within two lines of any `val`/`var`. Those are the ordinary shapes of the defect, so the
  guard passes on the violations it exists to catch.
- **Trigger:** `@Test fun x() { composeTestRule.onAllNodesWithText("x").fetchSemanticsNodes().isNotEmpty() }`
- **Evidence / verification:** Three violations were planted in a **disposable copy** of the tree
  outside the repository. Only the third — preceded by `performClick()`/`waitForIdle()` — was
  reported: `ProbeCTest.kt:22`. The first-statement case and the case two lines below a `val` were
  silently accepted. Refutation attempt: re-running with the `val` removed **did** flag the second
  case, which isolates the window bounds rather than the target regex as the cause. New file in this
  range, so INTRODUCED.
- **Suggested fix:** exclude the matched line from the window (`lines[max(0, i - 2): i]`) and drop
  `\bfun\b` from `CONSUMING`, or match the statement rather than a line neighbourhood.

### [c/dead-or-unreachable-behavior/test-structure-guards/absent-source-set-reports-success] Guards report green when the directory they scan does not exist

- **Location:** `scripts/check_tests.py:35` (related: `:227`)
- **Severity:** Medium
- **Confidence:** High
- **Defect:** `kotlin_files()` is `sorted(ANDROID_TEST.rglob("*.kt"))`. `Path.rglob` on a missing
  directory yields nothing rather than raising, and `ANDROID_TEST` is never `.exists()`-checked. A
  source-set move, a rename, or a partial checkout therefore turns all four checks into no-ops that
  print `OK` and exit 0 — the guard reports "All test structure checks passed" while having
  inspected nothing.
- **Trigger:** rename or move `app/src/androidTest`, then run `./scripts/check-tests.sh`.
- **Evidence / verification:** Renamed the directory away in the disposable copy — all four checks
  printed `OK` and the script exited 0. The sibling script guards this correctly
  (`scripts/audit_tests.py:45` does check existence), which shows the omission is not the intended
  contract. New file in this range.
- **Suggested fix:** fail loudly when `ANDROID_TEST` is absent, as `audit_tests.py` already does.

### [a/error-handling/test-structure-guards/platform-default-encoding-decode] Guard scripts abort the whole test run under a non-UTF-8 locale

- **Location:** `scripts/check_tests.py:201` (related: `:64`, `:165`, `:228`,
  `scripts/audit_tests.py:78`, `:91`)
- **Severity:** Medium
- **Confidence:** High
- **Defect:** Every `read_text()` call omits `encoding=`, so Python decodes with the platform default.
  Under a non-UTF-8 locale the scripts raise `UnicodeDecodeError` on the first non-ASCII source byte.
  `scripts/test.sh:10` runs `check-tests.sh` first under `set -e`, so the entire run aborts before
  anything compiles.
- **Trigger:** `LC_ALL=C ./scripts/test.sh` — the default locale in minimal CI containers.
- **Evidence / verification:** Reproduced in the disposable copy:
  `UnicodeDecodeError: 'ascii' codec can't decode byte 0xe2 in position 1308`, exit 1, with
  `locale.getpreferredencoding(False)` reporting `ANSI_X3.4-1968`. Refutation attempt: it passes
  under the repository's normal UTF-8 locale, so the failure is environment-dependent rather than
  universal — and it fails loudly rather than silently, which is why this is Medium and not higher.
- **Suggested fix:** pass `encoding="utf-8"` to every `read_text()` in both scripts.

### [c/dead-or-unreachable-behavior/test-structure-guards/line-scoped-and-anchored-pattern-matching] Three guards under-detect valid Kotlin formulations

- **Location:** `scripts/check_tests.py:165` (related: `:61`, `:216`)
- **Severity:** Medium
- **Confidence:** High
- **Defect:** Three checks match line-oriented or over-anchored patterns against Kotlin source and
  miss legal formulations of exactly what they forbid:
  1. `:165` — the hardcoded-UI-string guard runs `matcher.findall(line)` per line, so a matcher whose
     literal is wrapped onto its own line is never flagged. The assertion stays locale-dependent, and
     `assertDoesNotExist()` on it is a guaranteed false pass off-locale — the precise failure the
     check exists to stop.
  2. `:61` — `^\s*@Composable\b` requires the annotation to be the first token, so
     `private @Composable fun Foo()` is missed.
  3. `:216` — `ANDROID_API` includes the bare substring `android\.`, so a pure-JVM test that merely
     mentions a package name in a string literal counts as "needs a device". This change added
     `<package android:name="com.android.vending" />` to the manifest, making such literals natural
     in store-intent tests.
- **Trigger:** (1) `composeTestRule\n  .onNodeWithText(\n      "Data"\n  )\n  .assertDoesNotExist()`;
  (2) `private @Composable fun Foo()`; (3) a JUnit-only test containing `"com.android.chrome"`.
- **Evidence / verification:** All three reproduced against planted probes in the disposable copy.
  For (1), four violations were planted and only the single-line `onNodeWithText("Theme")` was
  reported; collapsing the wrapped call onto one line made it fire, isolating line-scoping as the
  cause. For (2), both orderings were planted and only the annotation-first form was reported. For
  (3), the identical test without the `"com.android…"` literal **was** flagged while the one with it
  was not. Also outside the matcher set: `onNodeWithContentDescription` and `assertTextEquals`.
- **Suggested fix:** match against the file text with `re.DOTALL`-style multi-line patterns (or a
  lightweight Kotlin parse) rather than per line; drop the `^\s*` anchor on `@Composable`; and
  require a word boundary and a known package prefix (`android.app.`, `android.content.`, …) instead
  of the bare `android.` substring.

## Low

### [b/contract-mismatches/settings-home-sections/unkeyed-draft-can-persist-placeholder-order] Home-sections dialog can save the default order over the user's stored one

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/SettingsActivity.kt:1306`
  (related: `:132`, `:1405`)
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `var draft by remember { mutableStateOf(order) }` has no key. `order` arrives from
  `homeSectionOrder.collectAsState(initial = HomeSection.DEFAULT_ORDER)`, so if the dialog opens
  before the DataStore flow first emits, `draft` captures the placeholder and never updates when the
  real value arrives. Tapping Save then writes the default arrangement over the user's stored one.
- **Trigger:** Tap the "Home sections" row within the frame or two before the preference flow emits.
- **Evidence / verification:** `:132` seeds the placeholder, `:1405` saves `draft` unconditionally.
  Refutation attempt: the DataStore singleton is already warm by the time Settings opens —
  `HomeViewModel.kt:258` reads it — so the window is roughly one to two frames. That narrows it to
  Low but does not close it. The structurally identical hazard at `:1221`
  (`LocationsSelectionDialog` vs `enabledLocations`) is byte-identical at baseline and therefore
  pre-existing.
- **Suggested fix:** key the state — `remember(order) { mutableStateOf(order) }` — which costs
  nothing and cannot regress the drag flow, since `order` is stable once emitted.

### [b/contract-mismatches/instrumentation-test-isolation/orchestrator-comment-overstates-isolation] Build comment asserts on-disk isolation the configuration does not provide

- **Location:** `app/build.gradle.kts:79`
- **Severity:** Low
- **Confidence:** High
- **Defect:** The comment introduced with `execution = "ANDROIDX_TEST_ORCHESTRATOR"` states that
  "state one test leaks (DataStore files, ThemeManager, static caches) cannot make the next one pass
  or fail". Orchestrator gives a fresh **process** per test but does not clear the app data directory
  without `testInstrumentationRunnerArguments["clearPackageData"] = "true"`. DataStore files — named
  first in the comment — persist across tests exactly as they did at baseline, so the comment records
  a contract the build does not have and will mislead the next person debugging cross-test bleed.
- **Trigger:** Any instrumentation test that writes DataStore state and does not clean up after
  itself; the next test in the same class observes it.
- **Evidence / verification:** `grep -rn "clearPackageData\|testInstrumentationRunnerArguments"` over
  every `*.kts`/`*.gradle` returns no hits. Refutation attempt: actual isolation is not **worse**
  than baseline — Orchestrator strictly improves in-memory isolation — so this is an incorrect
  asserted contract rather than a behavioural regression. Landmine worth recording with the fix:
  adding `clearPackageData` would wipe the JaCoCo `.ec` files that `enableAndroidTestCoverage = true`
  (`app/build.gradle.kts:47`) depends on.
- **Suggested fix:** correct the comment to describe process-level isolation only, and note why
  `clearPackageData` is deliberately not set.

### [a/error-handling/locations-size-cache/stale-mark-consumed-before-clear-that-can-fail] A failed cache clear loses the staleness mark with no retry

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/LocationsRepository.kt:61`
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `if (sizeCacheStale.getAndSet(false)) { cacheSource.clearCache() }` consumes the mark
  before the action it gates can fail. `clearCache()` routes through `editSafely`
  (`DataStoreSafeAccess.kt:19-28`), which swallows `IOException` and returns normally. The mark is
  already gone, so the external change that set it never invalidates anything and the home cards keep
  pre-change totals for the full 5-minute TTL.
- **Trigger:** Another app writes to shared storage (media notification → `markSizeCacheStale`), then
  the next home load runs while the DataStore write fails — ENOSPC being the realistic case for a
  file explorer's users, and one that `reportUnlessDiskFull` (`DataStoreSafeAccess.kt:58-62`)
  deliberately silences.
- **Evidence / verification:** At baseline `HomeViewModel.loadData()` called
  `locationsRepository.refreshSizeCache()` → `cacheSource.clearCache()` **unconditionally on every
  load** (baseline `HomeViewModel.kt:236`, `LocationsRepository.kt:46-48`), so a swallowed clear was
  simply retried on the next load. The new one-shot mark has no retry. Refutation attempt: the mark
  is re-set by any later media notification and the TTL still expires the entries, so the window is
  bounded — which is why this is Low.
- **Suggested fix:** clear the mark only after a successful clear — have `clearCache()` report
  success, or re-set `sizeCacheStale` when it fails.

### [a/state-and-lifecycle/thumbnail-cache/empty-buffer-committed-as-valid-entry] Zero-byte thumbnails are cached and served as permanent hits

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/EpubThumbnailFetcher.kt:51`
  (related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/AudioThumbnailFetcher.kt:52`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ThumbnailDiskCache.kt:108`, `:247`)
- **Severity:** Low
- **Confidence:** High
- **Defect:** Both fetchers call `thumbnailCache.write(buffer.copy())` unconditionally. The PDF,
  video and APK fetchers all guard with `if (compressed && buffer.size > 0)`
  (`PdfThumbnailFetcher.kt:308`, `VideoThumbnailFetcher.kt:408`, `ApkThumbnailFetcher.kt:232`)
  precisely to avoid, in their own words, "caching that commits a broken thumbnail to disk which is
  then served on every later request until the file's modification time changes". An empty buffer
  sails past the missing guard: `write()` only rejects `bytes.size > MAX_ENTRY_BYTES`, so a 0-byte
  entry is committed with metadata `"*"`, and `covers("*","*")` is unconditionally true — a permanent
  hit on zero bytes that occupies a cache slot and adds eviction pressure on good entries.
- **Trigger:** An EPUB whose selected cover entry is a 0-byte placeholder — `findCoverEntry` matches
  on name and never on size — or an audio file whose APIC frame yields a zero-length
  `embeddedPicture` array.
- **Evidence / verification:** Traced `write()`'s only rejection path and `covers()`'s unconditional
  `"*"` match. Refutation attempt (partly successful): the **visible** result is unchanged — an empty
  buffer failed to decode at baseline too, so the user sees the same error icon. What is new is that
  the useless entry is now persisted and permanently served, which is why this is Low rather than
  refuted outright.
- **Suggested fix:** apply the same `buffer.size > 0` guard the three sibling fetchers already use.

### [a/error-handling/telemetry/reporter-can-throw-into-a-successful-operation] The one unguarded reporter path became reachable from a success path

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ErrorReporter.kt:120`
  (related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ThumbnailDiskCache.kt:120`, `:137`)
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `report()` calls `FirebaseCrashlytics.getInstance().apply { … }` directly — the single
  telemetry path not wrapped in `withCrashlytics`. The new `ThumbnailDiskCache` calls `warning()`
  from its **write** path, so a throw from `getInstance()` propagates into `renderPdfThumbnail()` /
  `extractVideoThumbnail()` / `extractApkIcon()` and converts an already-successful extraction into a
  failed thumbnail. That is exactly what the project rule forbids: "an unavailable or failing
  reporter must never surface as a failure of the operation being diagnosed".
- **Trigger:** `FirebaseCrashlytics.getInstance()` throwing while a thumbnail write reports a
  non-fatal — an uninitialised or misconfigured Firebase app.
- **Evidence / verification:** The class doc added at `ErrorReporter.kt:86-93` asserts "nothing it
  raises is allowed out", which holds for `withCrashlytics` but not for `report()` five lines below.
  At baseline `warning()` was reached only from `fetch()`'s catch — paths that had already failed —
  so the outcome was unchanged either way. Refutation attempt: in a normally initialised production
  app `getInstance()` does not throw and Crashlytics swallows its own internal errors, so real-world
  reachability is low; the unguarded call itself is pre-existing. Reported because this change makes
  it reachable from a path that would otherwise have succeeded.
- **Suggested fix:** route `report()` through `withCrashlytics` like every other call in the file.

### [a/concurrency/uncompress/media-scan-races-the-rollback] Cancelled extractions leave MediaStore rows for deleted files

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/UncompressHandler.kt:102`
  (related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:984`)
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `scanFiles` now runs on every emission carrying paths, i.e. **during** extraction.
  `uncompressFile`'s catch deletes everything extracted on any failure, so a failed or cancelled
  extraction leaves MediaStore rows pointing at files that no longer exist — phantom gallery entries
  until the next full scan. `MediaScannerConnection.scanFile` binds a service asynchronously, so it
  races the rollback.
- **Trigger:** Extract an archive of at least `MEDIA_PATH_BATCH_SIZE` (500) entries, then cancel — or
  hit `ZipBombException` / `InsufficientStorageException` past the first batch.
- **Evidence / verification:** `FileRepository.kt:969-980` emits and resets `extractedPaths` per
  batch; `UncompressHandler.kt:102` scans each. Refutation attempt: not refutable by the rollback
  being synchronous, since the scan is a service bind. Mitigating and worth stating: the change fixes
  a worse baseline bug — at baseline only `progress.isComplete` was scanned, and by then
  `extractedPaths` held just the final leftovers, so most extracted files were never registered at
  all. This is the side effect of a genuine fix, not a plain regression.
- **Suggested fix:** accumulate the scanned paths and issue a compensating
  `MediaStoreUtil.notifyDeleted` for them on the rollback path, so the two stay consistent.

### [a/concurrency/startup-destination/filesystem-stat-on-the-main-thread] Startup resolution stats the volume on the UI thread

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/StartupDestinationResolver.kt:55`
  (related: `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:84`)
- **Severity:** Low
- **Confidence:** High
- **Defect:** `resolve()` calls `folder.isDirectory` and `folder.canRead()` directly from
  `MainActivity.onCreate`. Only `mountedStorages()` is wrapped in `runBlocking(Dispatchers.IO)`
  (`MainActivity.kt:106-113`); the stat itself is not, so it runs on the main thread before the first
  frame. The project rule is explicit that file I/O never blocks the main thread.
- **Trigger:** Any cold start with a configured startup folder, worst on a slow or freshly mounted
  FUSE/SD volume.
- **Evidence / verification:** The KDoc at `MainActivity.kt:69-77` deliberately justifies the
  blocking **preference** reads and the storage lookup, but does not mention the filesystem stat —
  so this part is unaccounted for rather than an accepted trade-off. StrictMode would flag it.
  Refutation attempt: the documented blocking reads were disposed as an accepted design decision and
  are **not** reported; only the undocumented stat is.
- **Suggested fix:** move the `isDirectory`/`canRead` check inside the existing
  `runBlocking(Dispatchers.IO)` block that already wraps `mountedStorages()`.

### [b/contract-mismatches/folder-screen-robot/row-actions-target-the-bottom-most-row] Test robot opens the wrong row's action sheet

- **Location:** `app/src/androidTest/java/com/mauriciotogneri/fileexplorer/testutil/FolderScreenRobot.kt:107`
  (related: `app/src/androidTest/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderScreenTest.kt:353`)
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `openRowActions(fileName)` uses `fileName` only for `waitForText` on `:103`. The tap on
  `:107` targets `nodes[tops.indices.maxByOrNull { tops[it] }]` — the bottom-most overflow button on
  screen. Every row and the toolbar share one content description (`FileListItem.kt:179`,
  `FolderScreen.kt:230`), so the named row is never located and any future assertion about *that*
  row's sheet will be made against a different row.
- **Trigger:** Call `openRowActions(name)` for any row that is not the last one in the rendered list.
- **Evidence / verification:** At `FolderScreenTest.kt:353`, `createStandardFixtures()` yields
  `Documents/`, `notes.txt`, `photo.jpg`; NAME_ASC puts directories first then lowercased name, so
  `openRowActions("notes.txt")` opens **photo.jpg**'s sheet. Refutation attempt (partly successful):
  the other three call sites (`:321`, `:331`, `:342`) each render exactly one row, so the bottom-most
  match coincidentally is the named one, and `:353` asserts only `selection_count == 1` and that
  `action_move_to` is displayed — both row-agnostic. **No shipped defect is currently concealed**;
  this is a mis-target and a landmine, which is why it is Low. Remaining assumption: instrumentation
  was not run, so the sort order is derived from production source rather than observed.
- **Suggested fix:** select the overflow button whose row bounds contain the named text node, rather
  than the bottom-most match.

## Audit Details

### Audit Status

**Partial.** Unmet completion conditions:

1. **The final discovery pass produced new candidates.** The ViewModel and Compose-UI agents returned
   late and added four (the Feedback HTTP client, the settings picker, the unkeyed draft, the
   main-thread stat), and five test sub-agents returned later still, adding one more (R-33) plus
   independent corroboration of C-19. Each new candidate was dispositioned, but no clean pass — one
   producing nothing new — was ever completed, so convergence is not demonstrated.
2. **No instrumentation verification.** No emulator was available (`adb devices` empty), so the
   50 changed `androidTest` files and every device-dependent flow were established statically. The
   robot mis-target and its sort order in particular were not observed running.
3. **The top finding's library semantics were read, not executed.** The `androidx.navigation 2.9.8`
   pop/lifecycle behaviour behind the breadcrumb finding was traced through the resolved sources and
   cross-checked against the normal pop case, but never run on a device.

Every included file was inspected, every inventoried flow traced, every taxonomy category covered, a
separate refutation attempt was made for every finding, and the final worktree comparison confirmed
no pre-existing state changed.

### Initial Worktree Snapshot

| Field | Value |
|---|---|
| Repository | `/home/max/Repositories/personal/fileexplorer` |
| Branch | `main` |
| Commit | `b4888881af81c6e0d3b257ce9db5ccdb3c1e5b30` |
| Initial worktree state | Clean — no tracked modifications, no untracked files, no staged changes |
| Staged / unstaged diff hashes | both `e3b0c442…` (SHA-256 of empty) |
| Snapshot manifest digest (`git ls-files -s`) | `ee3d16dc69ecdffe3835f900e90df44c13112d1f6d186eba00dc9d9484192054` |
| Inventory manifest digest | `da16ba3d20ce854a79cc9690403e17662f9e94bd65fa52c68cf7465f5c612257` |
| Ignored paths captured | none |
| Ignored paths excluded from the guarantee | `local.properties`, `keystore.properties`, `app/build/`, `build/`, `.gradle/` — untracked or ignored, never written to by this audit |
| Pre-existing `BUG_FINDINGS.md` | **absent at snapshot time**; this file is newly created, so no replacement approval was required |
| Final comparison | **PASS** — `git status --porcelain --untracked-files=all` empty and `git ls-files -s` digest identical to the snapshot, verified after all verification work |

All commands expected to write ran in a disposable workspace built from `git archive HEAD`, outside
the repository. Differences from the audited worktree: a synthetic `local.properties` (SDK path) was
added so Gradle could run, `.git` internals were excluded, and probe files were planted under
`app/src/androidTest/.../probe/` to exercise the guard scripts. The workspace was removed at the end.

### Audit Coverage

**Scope and resolution.** Change-based scope, **branch diff against a user-supplied base ref**. Base
ref detection ran first and found **none**: candidates `develop`, `master`, `origin/develop`,
`origin/master` do not exist; `main` (current branch, dropped) and `origin/main` both have their
merge-base at `HEAD` itself, so each would have produced an empty diff. `refs/remotes/origin/HEAD`
points at the non-existent `refs/remotes/origin/master`. The user then supplied
`d0b63a1c283c63959fcd3e1195f5e367171e529d`, whose merge-base with `HEAD` is itself (a direct
ancestor), 54 commits behind.

**Baseline state for attribution:** `d0b63a1c283c63959fcd3e1195f5e367171e529d`.

| Step | Command | Result |
|---|---|---|
| Seed set | `git diff d0b63a1…HEAD --name-only` | 173 files (48 A, 122 M, 3 D) |
| Excluded from seed | `scripts/__pycache__/*.pyc` ×2 | generated, not shipped or consumed |
| One-hop dependents (main source) | grep of each changed symbol across `app/src/main/java` | 43 files |
| One-hop dependents (build) | consumers of `libs.versions.toml` / Gradle config | `build.gradle.kts`, `settings.gradle.kts` |
| **Included set** | | **216 files** |

`included first-party files = inspected files + skipped files` → **216 = 216 + 0**. ✅

| Module | Included |
|---|---:|
| `app/src/androidTest` | 50 |
| `app/src/test` | 26 |
| `data/util` | 25 |
| `app/src/main/res` | 22 |
| `ui/screens` | 19 |
| `ui/components` | 17 |
| `activities` | 11 |
| `data/source` | 10 |
| `data/model` | 7 |
| build (`gradle/`, `*.gradle.kts`) | 6 |
| `util` | 4 |
| `scripts` | 4 |
| `.claude/` agent + skill config | 4 |
| `data/repository` | 3 |
| `ui/theme` | 2 |
| docs (`README.md`, `CLAUDE.md`) | 2 |
| `ui/util`, `ui/navigation`, root `FileExplorerApplication.kt`, `AndroidManifest.xml` | 4 |
| **Total** | **216** |

**Coverage reconciliation.** The test agent delegated ~40 instrumentation files to five sub-agents and
reported that none replied, flagging ~27 UI-screen tests as only red-flag-swept. Those sub-agents did
return — to this session rather than to their parent, whose `SendMessage` back-channel failed. Their
reports are accounted for here: all 27 UI-screen instrumentation files were read at HEAD, diffed
against the baseline and cross-read against their production Composable or ViewModel. The parent's
stated gap does not exist; coverage is complete.

**Skipped included paths:** none. Two files were read through their diff plus targeted sections
rather than end to end and are recorded here for honesty: `data/util/AnalyticsTracker.kt` (969 lines
— read as header, `trackEvent`/`setUserProperty`, and every diff hunk) and the two `.claude/skills/`
markdown files (skimmed for risky instructions, not read line by line). The 11 metadata extractors
outside the seed set are byte-identical to baseline and were read through `git diff --stat` plus
their contract boundaries.

**Meaningful flows.** 48 inventoried, all traced, none skipped:
`total meaningful flows = traced flows + skipped flows` → **48 = 48 + 0**. ✅

| ID | Entry point | Material result | Status |
|---|---|---|---|
| F-01 | `MainActivity.onCreate` | startup destination resolved; FolderActivity launched or home shown | traced |
| F-02 | `MainActivity` / `PermissionScreen` | NavGraph switches on `hasPermission` | traced |
| F-03 | `HomeViewModel.init` | ordered home sections rendered | traced |
| F-04 | `FolderViewModel.loadFiles` | directory listing → UI state | traced |
| F-05 | FolderScreen sort menu | sort mode persisted, list resorted | traced |
| F-06 | `SettingsViewModel.setShowHidden` | preference persisted, folder relisted | traced |
| F-07 | `FolderViewModel` selection | multi-select state | traced |
| F-08 | `onCopyTo` → picker → `copyFiles` | files copied, progress, rollback | traced |
| F-09 | `onMoveTo` | files moved, sources removed | traced |
| F-10 | `deleteWithProgress` | files deleted, thumbnails dropped | traced |
| F-11 | `FileRepository.rename` | renamed, case-only path handled | traced |
| F-12 | `FileRepository.createFolder` | folder created | traced |
| F-13 | `FileRepository.compressFiles` | zip written, progress reported | traced |
| F-14 | `FileRepository.uncompressFile` | files extracted, rollback on failure | traced |
| F-15 | `searchFilesStreaming` | results emitted, cancellable | traced |
| F-16 | `SearchViewModel` filters | filtered results | traced |
| F-17 | `IntentUtil.openFile` | external app launched via FileProvider | traced |
| F-18 | `ImageViewerViewModel` | image displayed | traced |
| F-19 | `TextViewerViewModel` | text displayed | traced |
| F-20 | `ItemInfoViewModel` + extractors | metadata shown | traced |
| F-21 | `AppImageLoader` + `ThumbnailDiskCache` | thumbnail rendered and cached | traced |
| F-22 | `FileRepository.dropThumbnail` | cache entry evicted | traced |
| F-23 | `FavoritesRepository` | favorite persisted | traced |
| F-24 | `RecentFilesRepository` | recent entry persisted | traced |
| F-25 | `LocationsRepository` + cache source | cached location sizes | traced |
| F-26 | `AndroidMediaChangeSource` | refresh on external change | traced |
| F-27 | `AndroidStorageSource.getStorages` | internal/SD device list | traced |
| F-28 | `SettingsViewModel.setThemeMode` | theme applied and persisted | traced |
| F-29 | `LanguageUtil` | locale applied | traced |
| F-30 | `setStartupScreen` | screen + folder persisted together | traced |
| F-31 | `setSwipeLeft/RightAction` | persisted, applied to rows | traced |
| F-32 | `setFile/FolderSecondLine` | list row subtitle | traced |
| F-33 | `setHomeSectionOrder` | reconciled order persisted | traced |
| F-34 | `setEnabledLocations` | home locations section | traced |
| F-35 | `setRecentFilesEnabled` | recent section on/off | traced |
| F-36 | `PreferencesRepository` badge APIs | `BadgeDot` visibility | traced |
| F-37 | `SwipeableFileListItem` | swipe action dispatched | traced |
| F-38 | `BreadcrumbPathParser` + `Breadcrumbs` | navigate to ancestor | traced |
| F-39 | `PickerViewModel` / `DestinationPicker` | target path returned | traced |
| F-40 | `PickerBottomBar` | folder created in picker | traced |
| F-41 | `IntentUtil` + `ApkPermissionDialog` | installer launched | traced |
| F-42 | `FeedbackActivity` | feedback submitted over HTTP | traced |
| F-43 | `AboutActivity` / `OtherAppsActivity` | Play Store intent | traced |
| F-44 | `LegalActivity` | localized `raw/` document rendered | traced |
| F-45 | `AnalyticsTracker.trackScreen*` | event sent, no PII | traced |
| F-46 | `ErrorReporter` | non-fatal recorded, never throws | traced |
| F-47 | build types + manifest placeholders | Firebase collection off on debug | traced |
| F-48 | `gradlew` / `check-tests.sh` | pass/fail signal for the change loop | traced |

**Taxonomy coverage.** All three tiers and every category were inspected. Tier A produced findings in
logic/state-and-lifecycle, error handling, concurrency and resource management; boundary/encoding and
null/numeric hazards were inspected and produced only refutations. Tier B produced findings in
contract mismatches and resource/configuration parity; validation-and-coercion and security defects
were inspected across the manifest, FileProvider, MediaStore queries, zip extraction and telemetry,
and produced only refutations. Tier C produced the guard-script findings; debt markers were checked
(one `TODO`-like marker in range, a doc comment, not a latent defect).

### Candidate Dispositions

`total candidates = findings + merged + refuted + pre-existing + unverified`
→ **69 = 15 + 2 + 34 + 18 + 0**. ✅
`reported findings = findings` → **15 = 15**. ✅

**Findings (15)**

| ID | Primary location | Category | Finding |
|---|---|---|---|
| C-01 | `FolderActivity.kt:166` | a/state-and-lifecycle | breadcrumb-depth-exceeds-back-stack |
| C-14 | `FeedbackActivity.kt:257` | a/concurrency | http-client-constructed-during-composition |
| C-15 | `SettingsActivity.kt:143` | a/state-and-lifecycle | picker-state-not-retained-across-recreation |
| C-02 | `scripts/check_tests.py:205` | c/dead-or-unreachable-behavior | context-window-consumes-its-own-anchor |
| C-04 | `scripts/check_tests.py:35` | c/dead-or-unreachable-behavior | absent-source-set-reports-success |
| C-05 | `scripts/check_tests.py:201` | a/error-handling | platform-default-encoding-decode |
| C-03 | `scripts/check_tests.py:165` | c/dead-or-unreachable-behavior | line-scoped-and-anchored-pattern-matching |
| C-16 | `SettingsActivity.kt:1306` | b/contract-mismatches | unkeyed-draft-can-persist-placeholder-order |
| C-08 | `app/build.gradle.kts:79` | b/contract-mismatches | orchestrator-comment-overstates-isolation |
| C-09 | `LocationsRepository.kt:61` | a/error-handling | stale-mark-consumed-before-clear-that-can-fail |
| C-10 | `EpubThumbnailFetcher.kt:51` | a/state-and-lifecycle | empty-buffer-committed-as-valid-entry |
| C-11 | `ErrorReporter.kt:120` | a/error-handling | reporter-can-throw-into-a-successful-operation |
| C-12 | `UncompressHandler.kt:102` | a/concurrency | media-scan-races-the-rollback |
| C-17 | `StartupDestinationResolver.kt:55` | a/concurrency | filesystem-stat-on-the-main-thread |
| C-19 | `FolderScreenRobot.kt:107` | b/contract-mismatches | row-actions-target-the-bottom-most-row |

**Merged (2)**

| ID | Primary location | Category | Merged into |
|---|---|---|---|
| C-06 | `scripts/check_tests.py:61` | c/dead-or-unreachable-behavior | line-scoped-and-anchored-pattern-matching |
| C-07 | `scripts/check_tests.py:216` | c/dead-or-unreachable-behavior | line-scoped-and-anchored-pattern-matching |

**Pre-existing (18)** — real defect paths whose incorrect behaviour reproduces unchanged at the
baseline. Recorded, not reported.

| ID | Primary location | Category | Reason |
|---|---|---|---|
| P-01 | `FileRepository.kt:357` | a/logic-errors | `files.all` short-circuits; multi-select delete abandons the rest after the first failure — identical at baseline |
| P-02 | `FileRepository.kt:861` | a/resource-management | rollback ledger is O(files) for flat archives into existing dirs; baseline peak was no better |
| P-03 | `DataStoreLocationsCacheSource.kt:17` | a/boundary-and-encoding-cases | absent timestamp reads as fresh when the wall clock is near the epoch; same window at baseline |
| P-04 | `FileRepository.kt:829` | a/error-handling | password pre-check reads one byte; a 0-byte first encrypted entry may not validate — byte-identical at baseline |
| P-05 | `SwipeableFileListItem.kt:216` | a/concurrency | `scope.launch { snapTo }` per drag event; same pattern at baseline |
| P-06 | `res/values-ca`, `res/values-it` | b/resource-and-configuration-parity | lint `MissingQuantity` ×26; plural count 13 = 13 vs baseline and no plural line changed in range |
| P-07 | `res/drawable/*.png` ×2 | b/resource-and-configuration-parity | lint `IconLocation`; files not in the seed set |
| P-08 | `res/xml/provider_paths.xml:6` | b/security-defects | `<root-path path="/">` lets the authority serve any absolute path; unchanged since baseline |
| P-09 | `ErrorReporter.kt:113` | a/error-handling | unguarded `getInstance()` itself (its *reachability* from a success path is reported as C-11) |
| P-10 | `FolderActivity.kt:176` | a/state-and-lifecycle | child destinations inherit the parent app-bar title |
| P-11 | `SearchViewModel.kt:338` | a/concurrency | `_uiState.value` read-modify-write; safe on `Main.immediate`, unchanged at baseline |
| P-12 | `FolderViewModel.kt:815` | a/error-handling | cancelled delete: `_events.emit` throws in a cancelled coroutine so `loadFiles()` is skipped — verified byte-identical at baseline |
| P-13 | `FileRepository.kt:841` | a/error-handling | `StatFs(targetDir)` unguarded in `uncompressFile` (the copy path gained a guard; this one did not) |
| P-14 | `SearchViewModel.kt:284` | a/error-handling | `getStorages()` unguarded → crash and `isSearching` stuck true |
| P-15 | `SettingsViewModel.kt:61` | a/error-handling | `getAvailableLocationTypes()` unguarded → spinner pinned |
| P-16 | `FolderViewModel.kt:717` | b/contract-mismatches | folder rename orphans descendants in MediaStore |
| P-17 | `PickerViewModel.kt:177` | a/concurrency | `validateDestination` runs `canWrite()` on the main thread |
| P-18 | `SettingsActivity.kt:191` | c/dead-or-unreachable-behavior | `AnimatedVisibility` exit never plays (content nulled the same frame); identical construct at `FolderScreen.kt:583` at baseline |

**Refuted (32)** — evidence disproved the suspected defect path.

| ID | Location / subject | Reason refuted |
|---|---|---|
| R-01 | `DataStorePreferencesSource.kt:191` | no badge id is a `:`-prefix of another; migration of bare-`id` entries is correct |
| R-02 | `res/drawable-v24/` deletion | `minSdk = 24`, so the qualifier was redundant and the surviving file is byte-identical |
| R-03 | `SwipeableFileListItem.kt` RTL | the change **fixed** an RTL bug: baseline used `Modifier.offset`/`Alignment.CenterStart`, HEAD uses `absoluteOffset`/`AbsoluteAlignment` |
| R-04 | deleted `FileOperationExecutionTest.kt` | all 14 cases absorbed into `FileOperationsEndToEndTest.kt`; the 5 weakened assertions have equivalents in `FileRepositoryTest.kt:944-1210` |
| R-05 | `scripts/check-tests.sh:8` | `exec python3` propagates status; measured exit 1 on a seeded violation |
| R-06 | `MainActivity.kt:107` `runBlocking` | deliberate and documented at `:69-77`, matching the existing application-level pattern; no incorrect result established |
| R-07 | `MainActivity.kt:79` permission early-return | the guard is correct behaviour; reachable only after a permission revoke and self-corrects next launch |
| R-08 | `FileRepository.kt:871` uncompress rollback | traced root-file, existing-dir, new-dir, non-normalized and dedup-rename entry shapes; the rollback can never delete a user's file |
| R-09 | `HomeSection.move` | remove-then-insert traced for both directions; guards cover out-of-range |
| R-10 | `searchFilesStreaming` | `ensureActive` per entry, symlink skip prevents cycles, `maxResults` honoured at both checks |
| R-11 | telemetry call sites (whole tree) | every parameter is an enum name or fixed label; no file name, path or content reaches telemetry |
| R-12 | Firebase collection on debug | two layers agree (build-type placeholders + `init()` guards) and `FirebaseCollectionTest` asserts the merged boolean |
| R-13 | missing/renamed string resources | compile-time references; the debug variant compiled and 746 unit tests executed |
| R-14 | format-specifier mismatch across 20 locales | zero findings from `lintDebug` (`StringFormatCount`/`StringFormatMatches`/`MissingTranslation` default-enabled, `abortOnError` default, exit 0) and from two independently written parsers |
| R-15 | `MediaStoreUtil` SQL injection | bound `?` args; `SQLiteQueryBuilder.computeWhere` parenthesizes the caller selection; `escapeForGlob` quotes `*`, `?`, `[` |
| R-16 | Zip Slip / symlink / zip bomb | canonical-path check with separator, header-sum and running-total ceilings, symlink entries written as regular files |
| R-17 | enum/sealed dispatch for 5 new enums | every `when` exhaustive; all persist by `name` with `?: DEFAULT` fallback, so an unknown stored value degrades |
| R-18 | `ThumbnailDiskCache` coverage over-claim | every request in the app is square; unreachable today |
| R-19 | PDF preview fit-inside change | deliberate and documented; square home cards are unchanged or better |
| R-20 | `gradle/agent-quiet.init.gradle` | verbosity only — does not touch `ignoreFailures`, `filter`, `failFast` or task outcomes |
| R-21 | minSdk 23→24 | lowest `VERSION_CODES` constant referenced is `O` (26); no dead or wrong guard |
| R-22 | version catalog + wrapper 9.6.1→9.7.1 | no unused/undeclared aliases; JUnit 5→6 kept Vintage; distribution verified via the wrapper's `.ok` marker |
| R-23 | `AndroidMediaChangeSource.kt:47` | registration failure is near-unreachable on current Android and the degradation is the documented intent ("costs freshness, not correctness") |
| R-24 | wasted home load on startup-folder cold start | performance only; no incorrect result and no project rule violated |
| R-25 | `loadChildCounts` rewrite | flusher outlives the last count; `Mutex` gives happens-before; `ensureActive` covers the non-suspending fast path; no batch aliased |
| R-26 | per-batch copy/uncompress path contract | batch lists reassigned not cleared; both consumers scan every emission; `sourceDeleteFailed` sticky |
| R-27 | `HomeViewModel.loadData` deferral | no window between `while (reloadPending)` and job completion; `supervisorScope` isolates branch failures |
| R-28 | `SearchViewModel` non-atomic state writes | every writer runs on `Dispatchers.Main.immediate`; no lost update |
| R-29 | lazy-list keys and reorder drag | `key = { it.path }` in both lists, `key(section)` on home; the drag cannot oscillate or loop |
| R-30 | preference defaults agreement | every `collectAsState(initial = …)` matches `DataStorePreferencesSource`'s default; fakes reproduce all of them |
| R-31 | thumbnail cache concurrency, partial writes, key collisions | `DiskLruCache` methods are `@Synchronized`; `completeEdit` writes then atomically moves; keys are SHA-256 hashed before touching the filesystem |
| R-32 | `ThumbnailDiskCache` resource management | no snapshot leak (Coil closes the source in a `finally`); `editor.abort()` in the catch is necessary, not error-masking |
| R-33 | `FolderScreen.kt:628` unreachable "Unselect all" branch | harmless dead code, which the taxonomy excludes on its own: no feature path is missing because `SelectionTopAppBar`'s Close icon (`:829`) already clears the selection. Pre-existing regardless — baseline gates the overflow menu identically (`FolderScreen.kt:183` at `d0b63a1`) |
| R-34 | `ShortDateFormatterTest.kt:163` cannot fail | weak but not vacuous — it pins idempotence, and `format()` does mutate per call (`format.timeZone = calendar.timeZone`), so a formatter that corrupted itself on the first call would be caught. Conceals no defect: the production path is correct. A weak test alone is outside the mandate |

**Unverified: 0.** No candidate was left blocked or inconclusive. Verification *limitations* that do
not correspond to a specific open candidate are recorded below.

### Verification Performed

| Check | Where | Result |
|---|---|---|
| `./gradlew -w --console=plain -I gradle/agent-quiet.init.gradle testDebugUnitTest` | disposable workspace | **746 tests, 0 failures, 0 errors**, exit 0 |
| `./gradlew -w --console=plain lintDebug` | disposable workspace | exit 0; 28 SARIF results — 26 `MissingQuantity` (ca/it) + 2 `IconLocation`, all attributable to unchanged files |
| `./scripts/check-tests.sh` | disposable workspace | exit 1 on a seeded violation — the guard can fail |
| Guard-script probes | disposable workspace | planted violations for the discarded-assertion, hardcoded-string, `@Composable` and Android-API checks; recorded exactly which were and were not detected |
| Missing-directory probe | disposable workspace | `app/src/androidTest` renamed away → all four checks `OK`, exit 0 |
| Locale probe | disposable workspace | `LC_ALL=C` → `UnicodeDecodeError`, exit 1 |
| Localization parity | two independently written parsers over all 20 `strings.xml` | 0 missing keys, 0 format-specifier mismatches, 0 unescaped literals |
| `javap -p -c okhttp3/OkHttpClient.class` | resolved okhttp 5.5.0 artifact | `platformTrustManager()` / `newSslSocketFactory()` confirmed inside the constructor |
| `androidx.navigation 2.9.8` source trace | resolved sources jar | `popBackStack()` pops before returning false; `populateVisibleEntries()` retains transitioning entries |
| Baseline attribution | `git show d0b63a1…:<path>` / `git diff d0b63a1…HEAD -- <path>` | every candidate's defect path read at baseline before disposition |
| Refutation | 7 discovery agents + 1 dedicated independent refutation agent + 5 sub-agents + orchestrator self-refutation | a separate refutation attempt was made for every finding; the top candidate's stated symptom was disproved and its severity corrected downward |
| Independent corroboration | 2 of the 5 test sub-agents derived C-19 separately | both traced `openRowActions("notes.txt")` to `photo.jpg`'s sheet from `FileRepository.kt:194-197`'s sort order; both rated it Medium — it is reported Low here because no shipped defect is concealed today |
| Worktree integrity | `git status --porcelain -uall`, `git ls-files -s \| sha256sum` | checked before, during and after; digest identical throughout |

### Exclusions and Limitations

- **No emulator.** `adb devices` was empty, so `connectedDebugAndroidTest` and `scripts/test.sh`
  never ran. All 50 changed instrumentation files, and every finding whose symptom is device-visible
  (C-01's freeze, C-19's sort order), rest on static reasoning.
- **Library internals read, not executed.** The `androidx.navigation` behaviour behind C-01 was
  traced through the 2.9.8 sources and cross-checked against the normal pop case; okhttp's
  constructor was confirmed from bytecode. Neither was observed at runtime.
- **Two files not read end to end.** `data/util/AnalyticsTracker.kt` (969 lines) was read as header,
  `trackEvent`/`setUserProperty` and every diff hunk; the two `.claude/skills/*.md` files were
  skimmed for risky instructions.
- **Excluded from the included set:** `scripts/__pycache__/*.pyc` ×2 — checked into git but generated,
  not shipped and not consumed to validate behaviour. Worth noting separately: they are tracked with
  no `.gitignore` rule. CPython revalidates them against source mtime and size, so there is no
  runtime effect and this is not reported as a defect.
- **Latent traps recorded but not reported as findings** (no reachable incorrect result today):
  the `home_section_order` user-property value is 34 of 36 characters, so a fifth `HomeSection` would
  silently truncate it; `ThumbnailDiskCache`'s coverage record would over-claim for a non-square
  request box, and every request in the app is currently square.
- **Coverage gap noted, not a finding:** `StartupDestinationResolver.kt:47` rejects `.`/`..` segments
  on a path later opened with `MANAGE_EXTERNAL_STORAGE`, and no test at any level exercises that
  branch. Flagged once because it is a security guard, not as a coverage report.
- **Probe files** were written into the disposable workspace only. The repository was never modified;
  the final digest comparison confirms it.

### Summary

**By severity:** Medium 7, Low 8. (No Critical or High survived verification — the one High candidate
was rescored to Medium by the refutation pass.)

**By confidence:** High 9, Medium 6, Low 0.

| Severity | High | Medium | Low | Total |
|---|---:|---:|---:|---:|
| Critical | 0 | 0 | 0 | **0** |
| High | 0 | 0 | 0 | **0** |
| Medium | 6 | 1 | 0 | **7** |
| Low | 3 | 5 | 0 | **8** |
| **Total** | **9** | **6** | **0** | **15** |

**Affected files (12):**

- `scripts/check_tests.py` (4 findings)
- `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/SettingsActivity.kt` (2)
- `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/FolderActivity.kt`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/FeedbackActivity.kt`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/util/StartupDestinationResolver.kt`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/util/UncompressHandler.kt`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/LocationsRepository.kt`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/EpubThumbnailFetcher.kt`
- `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ErrorReporter.kt`
- `app/build.gradle.kts`
- `app/src/androidTest/java/com/mauriciotogneri/fileexplorer/testutil/FolderScreenRobot.kt`
- `scripts/audit_tests.py`
