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

---

# Bug Findings — file-identity leaks into telemetry

Scope: whole-tree sweep, triggered by a Crashlytics non-fatal in `FileRepository.compressFiles`
(2026-08-23). Unlike the section above, this section reports **pre-existing** defects rather than
only those a reviewed change introduced. All eleven are pre-existing; the one whose *framing*
depends on that session's work carries an explicit **Provenance** line. Defects the session
introduced or uncovered and then fixed are listed under *Found and fixed* at the end.

One rule accounts for most of it. `CLAUDE.md`: *"Event params describe a file, never identify it —
extension, MIME type, source, counts. Never log file names, paths, or contents."* Every site below
keeps that rule in its own explicit arguments and breaks it through an exception **message**:
`ErrorReporter.report` calls `FirebaseCrashlytics.recordException(e)` (`ErrorReporter.kt:126`),
which transmits the message and the entire cause chain, and additionally `Log.e(TAG,
"[$severity][$operation] ${e.message}", e)` on debug builds. A path is the payload the rule exists
to protect: it names what the user has.

Verification note for this whole section: every call site, catch shape and filter below was read in
the repository. Claims about **platform or library message formats** (`org.json`,
`Uri.toSafeString`,
`FileProvider`, AndroidSVG) are documented behaviour that was *not* read here — they are what sets
Confidence below High where it is.

## High

### [b/security-defects/recent-files-and-favorites/json-parse-failure-records-entire-store] A corrupt recents or favorites blob uploads every stored path

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/DataStoreRecentFilesSource.kt:75`
  (related:
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/DataStoreFavoriteFilesSource.kt:77`,
  `:60`, `:63`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ErrorReporter.kt:126`)
- **Severity:** High
- **Confidence:** High
- **Defect:** Both sources parse the hand-rolled JSON string preference inside
  `catch (e: Exception) { ErrorReporter.error(e, …) }`, with the whole `JSONArray(json)`
  construction
  and the per-element reads inside the guarded region. Android's `org.json` raises every syntax
  error through `JSONTokener.syntaxError()`, which appends `" at character N of "` followed by **the
  entire input string** — here the complete serialised list of the user's recent or favourited
  files, every absolute path and file name. One malformed byte therefore uploads the whole store.
  Because the parse runs on each read rather than once, it repeats for the life of the corruption.
- **Trigger:** any truncation or corruption of the `recent_files` / `favorites` preference, or a
  forward-incompatible schema change shipped to a user holding old data.
- **Evidence / verification:** Read both parsers: `DataStoreRecentFilesSource.kt:57-78` and
  `DataStoreFavoriteFilesSource.kt:58-80` are the same shape, both ending in
  `ErrorReporter.error(e, "load_recent_files")` / `"load_favorite_files"`. `ErrorReporter.kt:126`
  confirms `recordException(e)` and `:119` the debug `Log.e`. The message-content claim rests on
  `org.json`'s documented
  `JSONTokener.toString()` (`" at character " + pos + " of " + in`), which was not read in-repo —
  that is the one link in the chain taken on documentation rather than source.
- **Suggested fix:** catch `JSONException` ahead of the generic clause and report a path-free
  stand-in (the operation name and the blob's length, not its content), leaving the generic catch
  for everything else.
