# Bug Findings

Scope: `git diff d0b63a1c283c63959fcd3e1195f5e367171e529d...HEAD` (54 commits). Only defects the
reviewed changes **introduce** are reported. Pre-existing defects appear solely as rows in the
candidate disposition table.

## Medium

### [a/state-and-lifecycle/folder-navigation/breadcrumb-depth-exceeds-back-stack] Ancestor breadcrumb on a startup folder empties the nav back stack and freezes the screen

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/FolderActivity.kt:166`
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

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/FeedbackActivity.kt:257`
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

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/SettingsActivity.kt:143`
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
  `PickerRequest` lives in `FolderUiState.pickerRequest` (`FolderScreen.kt:584`), not in
  `remember` —
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
  **and** the enclosing `fun` line. `CONSUMING` (`:184`) matches
  `\bfun\b|\bval\b|\bvar\b|\bif\b|->|assert|&&|\|\|`,
  so a discarded `fetchSemanticsNodes()` result is skipped whenever it is the first statement of a
  test, or within two lines of any `val`/`var`. Those are the ordinary shapes of the defect, so the
  guard passes on the violations it exists to catch.
- **Trigger:**
  `@Test fun x() { composeTestRule.onAllNodesWithText("x").fetchSemanticsNodes().isNotEmpty() }`
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
- **Defect:** Every `read_text()` call omits `encoding=`, so Python decodes with the platform
  default.
  Under a non-UTF-8 locale the scripts raise `UnicodeDecodeError` on the first non-ASCII source
  byte.
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
    1. `:165` — the hardcoded-UI-string guard runs `matcher.findall(line)` per line, so a matcher
       whose
       literal is wrapped onto its own line is never flagged. The assertion stays locale-dependent,
       and
       `assertDoesNotExist()` on it is a guaranteed false pass off-locale — the precise failure the
       check exists to stop.
    2. `:61` — `^\s*@Composable\b` requires the annotation to be the first token, so
       `private @Composable fun Foo()` is missed.
    3. `:216` — `ANDROID_API` includes the bare substring `android\.`, so a pure-JVM test that
       merely
       mentions a package name in a string literal counts as "needs a device". This change added
       `<package android:name="com.android.vending" />` to the manifest, making such literals
       natural
       in store-intent tests.
- **Trigger:** (1)
  `composeTestRule\n  .onNodeWithText(\n      "Data"\n  )\n  .assertDoesNotExist()`;
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

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/SettingsActivity.kt:1306`
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
  or fail". Orchestrator gives a fresh **process** per test but does not clear the app data
  directory
  without `testInstrumentationRunnerArguments["clearPackageData"] = "true"`. DataStore files — named
  first in the comment — persist across tests exactly as they did at baseline, so the comment
  records
  a contract the build does not have and will mislead the next person debugging cross-test bleed.
- **Trigger:** Any instrumentation test that writes DataStore state and does not clean up after
  itself; the next test in the same class observes it.
- **Evidence / verification:** `grep -rn "clearPackageData\|testInstrumentationRunnerArguments"`
  over
  every `*.kts`/`*.gradle` returns no hits. Refutation attempt: actual isolation is not **worse**
  than baseline — Orchestrator strictly improves in-memory isolation — so this is an incorrect
  asserted contract rather than a behavioural regression. Landmine worth recording with the fix:
  adding `clearPackageData` would wipe the JaCoCo `.ec` files that
  `enableAndroidTestCoverage = true`
  (`app/build.gradle.kts:47`) depends on.
- **Suggested fix:** correct the comment to describe process-level isolation only, and note why
  `clearPackageData` is deliberately not set.

### [a/error-handling/locations-size-cache/stale-mark-consumed-before-clear-that-can-fail] A failed cache clear loses the staleness mark with no retry

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/LocationsRepository.kt:61`
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `if (sizeCacheStale.getAndSet(false)) { cacheSource.clearCache() }` consumes the mark
  before the action it gates can fail. `clearCache()` routes through `editSafely`
  (`DataStoreSafeAccess.kt:19-28`), which swallows `IOException` and returns normally. The mark is
  already gone, so the external change that set it never invalidates anything and the home cards
  keep
  pre-change totals for the full 5-minute TTL.
- **Trigger:** Another app writes to shared storage (media notification → `markSizeCacheStale`),
  then
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

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/EpubThumbnailFetcher.kt:51`
  (related:
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/AudioThumbnailFetcher.kt:52`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ThumbnailDiskCache.kt:108`, `:247`)
- **Severity:** Low
- **Confidence:** High
- **Defect:** Both fetchers call `thumbnailCache.write(buffer.copy())` unconditionally. The PDF,
  video and APK fetchers all guard with `if (compressed && buffer.size > 0)`
  (`PdfThumbnailFetcher.kt:308`, `VideoThumbnailFetcher.kt:408`, `ApkThumbnailFetcher.kt:232`)
  precisely to avoid, in their own words, "caching that commits a broken thumbnail to disk which is
  then served on every later request until the file's modification time changes". An empty buffer
  sails past the missing guard: `write()` only rejects `bytes.size > MAX_ENTRY_BYTES`, so a 0-byte
  entry is committed with metadata `"*"`, and `covers("*","*")` is unconditionally true — a
  permanent
  hit on zero bytes that occupies a cache slot and adds eviction pressure on good entries.
- **Trigger:** An EPUB whose selected cover entry is a 0-byte placeholder — `findCoverEntry` matches
  on name and never on size — or an audio file whose APIC frame yields a zero-length
  `embeddedPicture` array.
- **Evidence / verification:** Traced `write()`'s only rejection path and `covers()`'s unconditional
  `"*"` match. Refutation attempt (partly successful): the **visible** result is unchanged — an
  empty
  buffer failed to decode at baseline too, so the user sees the same error icon. What is new is that
  the useless entry is now persisted and permanently served, which is why this is Low rather than
  refuted outright.
- **Suggested fix:** apply the same `buffer.size > 0` guard the three sibling fetchers already use.

### [a/error-handling/telemetry/reporter-can-throw-into-a-successful-operation] The one unguarded reporter path became reachable from a success path

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ErrorReporter.kt:120`
  (related:
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ThumbnailDiskCache.kt:120`, `:137`)
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `report()` calls `FirebaseCrashlytics.getInstance().apply { … }` directly — the single
  telemetry path not wrapped in `withCrashlytics`. The new `ThumbnailDiskCache` calls `warning()`
  from its **write** path, so a throw from `getInstance()` propagates into `renderPdfThumbnail()` /
  `extractVideoThumbnail()` / `extractApkIcon()` and converts an already-successful extraction into
  a
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
  (related:
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:984`)
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `scanFiles` now runs on every emission carrying paths, i.e. **during** extraction.
  `uncompressFile`'s catch deletes everything extracted on any failure, so a failed or cancelled
  extraction leaves MediaStore rows pointing at files that no longer exist — phantom gallery entries
  until the next full scan. `MediaScannerConnection.scanFile` binds a service asynchronously, so it
  races the rollback.
- **Trigger:** Extract an archive of at least `MEDIA_PATH_BATCH_SIZE` (500) entries, then cancel —
  or
  hit `ZipBombException` / `InsufficientStorageException` past the first batch.
- **Evidence / verification:** `FileRepository.kt:969-980` emits and resets `extractedPaths` per
  batch; `UncompressHandler.kt:102` scans each. Refutation attempt: not refutable by the rollback
  being synchronous, since the scan is a service bind. Mitigating and worth stating: the change
  fixes
  a worse baseline bug — at baseline only `progress.isComplete` was scanned, and by then
  `extractedPaths` held just the final leftovers, so most extracted files were never registered at
  all. This is the side effect of a genuine fix, not a plain regression.
- **Suggested fix:** accumulate the scanned paths and issue a compensating
  `MediaStoreUtil.notifyDeleted` for them on the rollback path, so the two stay consistent.

### [a/concurrency/startup-destination/filesystem-stat-on-the-main-thread] Startup resolution stats the volume on the UI thread

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/util/StartupDestinationResolver.kt:55`
  (related: `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:84`)
- **Severity:** Low
- **Confidence:** High
- **Defect:** `resolve()` calls `folder.isDirectory` and `folder.canRead()` directly from
  `MainActivity.onCreate`. Only `mountedStorages()` is wrapped in `runBlocking(Dispatchers.IO)`
  (`MainActivity.kt:106-113`); the stat itself is not, so it runs on the main thread before the
  first
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