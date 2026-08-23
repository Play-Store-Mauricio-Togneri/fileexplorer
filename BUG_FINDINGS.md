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
the repository. Claims about **platform or library message formats** (`org.json`, `Uri.toSafeString`,
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
  `catch (e: Exception) { ErrorReporter.error(e, …) }`, with the whole `JSONArray(json)` construction
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
  confirms `recordException(e)` and `:119` the debug `Log.e`. The message-content claim rests on `org.json`'s documented
  `JSONTokener.toString()` (`" at character " + pos + " of " + in`), which was not read in-repo —
  that is the one link in the chain taken on documentation rather than source.
- **Suggested fix:** catch `JSONException` ahead of the generic clause and report a path-free
  stand-in (the operation name and the blob's length, not its content), leaving the generic catch
  for everything else.

### [b/security-defects/item-info/geo-intent-failure-records-photo-coordinates] A device with no maps app uploads the photo's GPS coordinates

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/iteminfo/ItemInfoScreen.kt:685`
  (related: `:681`)
- **Severity:** High
- **Confidence:** Medium
- **Defect:** `openGeoUri` builds `"geo:$latitude,$longitude?z=18"` and starts it inside
  `try { … } catch (e: Exception) { ErrorReporter.error(e, "open_geo_uri") }`. With no handler
  installed, `startActivity` throws `ActivityNotFoundException`, whose message is
  `"No Activity found to handle " + intent`; `Intent.toString()` renders the data URI through
  `Uri.toSafeString()`, which redacts the scheme-specific part only for `tel`, `sms`, `smsto` and
  `mailto`. A `geo:` URI passes through intact, so coordinates read out of the user's photo EXIF
  reach the crash report — the most sensitive value in this section, and the only one that locates
  a person rather than a file.
- **Trigger:** Item info on a geotagged photo → tap the coordinates row, on a device or emulator
  with no maps application.
- **Evidence / verification:** Read `openGeoUri` (`ItemInfoScreen.kt:678-688`) — the URI
  construction, the broad catch and `ErrorReporter.error` are all as described. The
  `Uri.toSafeString()` redaction list is platform behaviour not read here, which is what holds
  Confidence at Medium; the leak does not depend on the exact wording, only on `geo:` being absent
  from that list.
- **Suggested fix:** catch `ActivityNotFoundException` separately and report a synthetic exception
  carrying the operation only. The toast already covers the user-facing side.

### [b/security-defects/image-viewer/svg-parse-failure-records-document-text] A malformed SVG uploads its own markup

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/imageviewer/ImageViewerViewModel.kt:104`
  (related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ImageErrors.kt:27`, `:53`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/AppImageLoader.kt:120`, `:127`)
- **Severity:** High
- **Confidence:** Medium
- **Defect:** `.svg`/`.svgz` are viewable (`MimeTypeUtil.kt:89`) and `SvgDecoder.Factory()` is
  registered on both image loaders, so a malformed SVG raises AndroidSVG's `SVGParseException`. It
  extends `Exception` directly, so it matches neither `isUnreadableImage` (`e is IOException`) nor
  `isUndecodableImage` (an `IllegalStateException` message match against two fixed Coil strings) and
  falls through to `ErrorReporter.warning`. `SVGParseException` messages quote the offending markup,
  so what is uploaded is file **content**, not a path. `ImageErrors.kt:49` names "a malformed SVG"
  as a case that "remain[s] worth reporting" — so the report is intended; its payload was not.
- **Trigger:** open any `.svg` whose XML the parser rejects.
- **Evidence / verification:** Read `ImageViewerViewModel.kt:95-108` (both filters applied, then
  `ErrorReporter.warning`), `ImageErrors.kt:27` and `:53-56` (the two filter definitions, neither
  matching a direct `Exception` subclass), `AppImageLoader.kt:120`/`:127` (decoder registered on
  both loaders) and `MimeTypeUtil.kt:89`/`:133` (svg is viewable). AndroidSVG's message format was
  not read in-repo — Confidence Medium for that reason alone; the routing to `ErrorReporter` is
  certain.
- **Suggested fix:** add `SVGParseException` to `isUndecodableImage` — it is exactly the
  "content no decoder can turn into a bitmap" case that predicate already describes.

## Medium

### [b/security-defects/metadata-extractors/unfiltered-catch-records-absolute-path] Five read paths hand the platform's path-bearing exception straight to Crashlytics

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/CsvMetadataExtractor.kt:38`
  (related:
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/VCardMetadataExtractor.kt:40`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ICalendarMetadataExtractor.kt:61`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/SqliteMetadataExtractor.kt:43`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/textviewer/TextViewerViewModel.kt:91`)
- **Severity:** Medium
- **Confidence:** High
- **Defect:** Each wraps its read in a bare `catch (e: Exception)` and passes the exception unchanged
  to `ErrorReporter.warning`. `file.bufferedReader()` / `file.inputStream()` on a file deleted,
  unmounted or made unreadable between the caller's check and the read throws
  `FileNotFoundException`, whose message Android builds as `<absolute path>: open failed: EACCES …`.
  `SqliteMetadataExtractor` is the same shape one layer down —
  `SQLiteCantOpenDatabaseException("Cannot open database '<absolute path>'")` from
  `SQLiteDatabase.openDatabase`. The sibling extractors in the same package (audio, video, PDF, APK)
  all install an `isUnreadable*` filter first; these five never got one.
- **Trigger:** browse a folder holding a `.csv`, `.vcf`, `.ics` or `.db` and delete it — or unmount
  its volume — while metadata extraction is in flight; or open a text file and delete it underneath
  the viewer.
- **Evidence / verification:** Read all five. `CsvMetadataExtractor.kt:17` + `:37-38`,
  `VCardMetadataExtractor.kt:17` + `:39-40`, `ICalendarMetadataExtractor.kt:26` + `:60-61`,
  `SqliteMetadataExtractor.kt:27` + `:42-43`, `TextViewerViewModel.kt:90-91` — in every case the
  read is inside the guarded region and no `isUnreadable*`-style predicate stands between the catch
  and the report. Contrast `ImageErrors.kt:27`, which is the guard the package already uses.
- **Suggested fix:** add the existing `e is IOException` guard to the four file readers, and suppress
  `SQLiteException` in the sqlite one.

### [b/security-defects/zip-family-extractors/narrow-filter-lets-io-failure-report-the-path] `isUnreadableZip` matches only `ZipException`, so a vanished archive reports its path

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ZipErrors.kt:18`
  (related:
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ZipMetadataExtractor.kt:40`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/OfficeMetadataExtractor.kt:25`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/EpubMetadataExtractor.kt:26`, `:56`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/EpubThumbnailFetcher.kt:36`, `:127`)
- **Severity:** Medium
- **Confidence:** High
- **Defect:** `isUnreadableZip(e) = e is ZipException`, and its own KDoc states the consequence as
  intended: *"A genuine I/O failure (e.g. the file removed mid-read) surfaces as a plain
  [java.io.IOException] rather than a [ZipException], so it remains reportable."* That reportable
  `IOException` is a `FileNotFoundException` thrown by `ZipFile(file)`, and its message is the
  absolute path. The TOCTOU window between the caller's existence check and the open is what makes
  it reachable. Six report sites across four files inherit the gap.
- **Trigger:** delete or unmount a `.zip`, `.docx`, `.xlsx`, `.pptx` or `.epub` while its metadata or
  cover thumbnail is being extracted — routine while scrolling a folder on removable storage.
- **Evidence / verification:** Read `ZipErrors.kt` in full — the quoted KDoc is verbatim, and the
  predicate is a single `e is ZipException`. Confirmed all six call sites gate on
  `if (!isUnreadableZip(e))` before `ErrorReporter.warning`, and that each opens with `ZipFile(file)`
  inside the guarded region.
- **Suggested fix:** widen to `e is ZipException || e is IOException`. The reportability the KDoc
  argues for does not require the path, and a mid-read disappearance is no more actionable than a
  malformed container.

### [b/security-defects/share-and-open/fileprovider-failure-records-absolute-path] Four FileProvider call sites report the raw exception, which names the file

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/IntentUtil.kt:101`
  (related: `:150`, `:319`, `:57`, `:291`)
- **Severity:** Medium
- **Confidence:** Medium
- **Defect:** `getFileUri` delegates to `FileProvider.getUriForFile`, which throws
  `IllegalArgumentException("Failed to resolve canonical path for <file>")` when `getCanonicalPath()`
  fails — ELOOP on a symlink cycle, ENAMETOOLONG on a pathological name. All four callers pass `e`
  unchanged to `ErrorReporter.warning`. `:57` is the widest: a broad `catch (e: Exception)` around a
  whole multi-file share, so a selection can produce one report per file.
- **Trigger:** share, open, "open with", or install an APK whose path cannot be canonicalised.
- **Evidence / verification:** Read all four sites and `getFileUri` (`IntentUtil.kt:290-294`); the
  three single-file sites catch `IllegalArgumentException` explicitly and report `e`, and `:56-58`
  catches `Exception` around both share helpers. The *other* FileProvider failure branch —
  `"Failed to find configured root that contains <path>"` — is unreachable here:
  `app/src/main/res/xml/provider_paths.xml` declares `<root-path path="/"/>`, so every path resolves
  to a root. FileProvider's exact message wording is library behaviour not read in-repo, which is
  what holds Confidence at Medium.
- **Suggested fix:** report a synthetic exception naming the operation; the caught type already tells
  the triager which branch fired.

### [b/security-defects/analytics/extension-param-carries-whole-file-names] `getExtension` returns the entire name for a dotfile, and free text for any dotted name

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/FileExtensionUtil.kt:7`
- **Severity:** Medium
- **Confidence:** High
- **Defect:** `File(path).extension` is `name.substringAfterLast('.', "")`, which is not an extension
  for two common shapes. A dotfile has no other dot, so `.private-journal` yields
  `private-journal` — the whole name. A name with a dot in its body yields its tail, so
  `Q3.Acme Confidential` yields `acme confidential`. That value is sent as the `extension` analytics
  parameter from roughly ten call sites, which is exactly the identification `CLAUDE.md` allows
  `extension` as an alternative to. This is the one entry in this section that reaches Firebase
  Analytics rather than Crashlytics, so it lands in a dataset kept for product measurement.
- **Trigger:** open, share, compress or view any dotfile, or any file with a dot inside its name.
- **Evidence / verification:** Read `FileExtensionUtil.kt` in full — it is three lines, and the
  `.lowercase().ifEmpty { "unknown" }` wrapper does not constrain the value. Kotlin's
  `File.extension` contract gives the `substringAfterLast` behaviour.
- **Suggested fix:** allowlist known extensions, or reject any value containing whitespace or longer
  than about eight characters, falling back to the existing `"unknown"`. Note
  `app/src/test/java/com/mauriciotogneri/fileexplorer/data/util/FileExtensionUtilTest.kt:26`
  currently pins the leaking behaviour, so the fix changes that expectation.

## Low

### [b/contract-mismatches/file-repository/scrubbed-message-keeps-path-bearing-cause] Scrubbing the wrapper message leaves the absolute path in the cause

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:577`
  (related: `:680`)
- **Severity:** Low
- **Confidence:** High
- **Defect:** `FileTransferIOException("Failed to copy file", e)` and
  `DestinationNotWritableException("Cannot create file", e)` were scrubbed of file names, but both
  keep the platform exception as `cause`, and `recordException` records the whole chain. The scrub
  therefore holds for the message and not for the object. Latent rather than live: every consumer
  catches both by type without reporting (`FolderViewModel.kt:635`, `:650`, `:906`, `:913`), and
  `UncompressHandler.kt:159` catches generically but calls no `ErrorReporter`. One new call site
  that falls through to a reporting catch re-opens it.
- **Provenance:** the path-bearing cause is **pre-existing** — both wraps always attached it. What
  is new is the expectation: the 2026-08-23 scrub of these two messages makes the object look
  name-free when the chain still is not.
- **Trigger:** none today; a future consumer that reports what it catches.
- **Evidence / verification:** Enumerated every catch site for both types across `app/src/main`;
  none reports. The cause is attached at the throw sites above. The guard tests added alongside the
  scrub (`FileRepositoryTest.kt:1376`, `:1534`) assert over `thrown.message` only, so they would not
  notice the cause.
- **Suggested fix:** drop a `FileNotFoundException` cause at the wrap, or assert over
  `generateSequence(thrown) { it.cause }` in both guard tests so the whole chain is pinned.

### [a/error-handling/uncompress/failures-are-never-reported] Extraction is the one write path whose unknown failures reach no crash report

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/UncompressHandler.kt:159`
- **Severity:** Low
- **Confidence:** High
- **Defect:** Its generic `catch (e: Exception)` tracks an analytics event and shows a toast, but
  never calls `ErrorReporter` — unlike the copy/move equivalent (`FolderViewModel.kt:665`) and the
  compress one (`:920`), both of which report. A genuine bug in extraction is therefore invisible in
  production, and the asymmetry is undocumented.
- **Trigger:** any unexpected failure during extraction.
- **Evidence / verification:** Read the whole catch ladder (`UncompressHandler.kt:126-165`): it
  handles `ZipException`, `ZipSlipException`, `ZipBombException`, `InsufficientStorageException` and
  `SecurityException` by type, and the generic clause carries no reporter. This is also why the
  path-bearing exceptions reaching it are latent rather than live.
- **Suggested fix:** report the non-environmental cases as the two sibling paths do, carving out the
  environmental types first — and scrub the reported exceptions per this section before doing so.

### [a/resource-management/compress/pre-flight-work-outside-the-cleanup-guard] A failure between archive creation and the first write leaves an empty archive behind

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:758`
  (related: `:759`, `:760`, `:813`)
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `getUniqueTargetFile` creates the archive on disk at `:758`, and only then do
  `totalSize()`/`totalFileCount()` recurse over the whole selection — both outside the `try` whose
  catch performs `zipFile.delete()`. A `StackOverflowError` from that recursion on a deep tree, or an
  OOM on a large one, leaves a zero-byte `.zip` in the user's folder and skips the `finally`'s
  `notifyFilesMutated()`, so the stale cached sizes stand too. Widening the catch to `Throwable`
  closed the equivalent hole *inside* the try; this one is above it.
- **Trigger:** compress a directory tree deep enough to exhaust the stack in `totalFileCount`.
- **Evidence / verification:** Read `compressFiles` (`FileRepository.kt:747-764`): the create is at
  `:758`, the two recursive walks at `:759-760`, and `try {` at `:764`. Confidence Medium because
  the depth needed to trigger it was not measured.
- **Suggested fix:** compute the totals before creating the archive, or extend the guarded region to
  cover them.

### [c/dead-or-unreachable-behavior/folder-screen/refresh-has-no-caller] `FolderViewModel.refresh()` is unreachable

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:364`
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** No production caller; the only route that reloads the listing is `onScreenResumed()`
  (`:377`), reached from `FolderScreen.kt:175`.
- **Trigger:** n/a — dead code.
- **Evidence / verification:** Surfaced incidentally while tracing the compress failure paths; a
  repository-wide search for the symbol found no production call site. Confidence Medium because the
  search was incidental rather than the task at hand.
- **Suggested fix:** delete it, or wire it to whatever refresh affordance it was written for.

## Found and fixed

Recorded for completeness — defects the same session uncovered and closed, so they need no
follow-up:

- `FileRepository.kt:660` — the unique-name-exhaustion `IOException` embedded the user's file name
  and was the one message on this path that genuinely reached Crashlytics. Scrubbed.
- `FileRepository.kt:577`, `:680` — the same shape in two exceptions that are not reported today.
  Scrubbed as defence in depth; the residual cause-chain gap is the Low entry above.
- `FileRepository.kt:1168` — `forEachChild` visited a name the listing returned twice, twice. Both
  names resolve to the same file, so every caller was wrong on such a volume: compress produced a
  duplicate zip entry, which `ZipOutputStream` rejects, failing the whole archive and filing a
  non-fatal; copy wrote one source into a second, collision-renamed file; delete counted a
  successful removal as a failure; and all three totals overcounted. The dedupe first landed in
  `addToZip` alone, which fixed compress but left its denominator counting an entry the archive
  wrote once — moving it into the one walker every caller shares removed that divergence and the
  three sibling defects together.
- `FileRepository.kt:813` — `compressFiles` caught `Exception` where `copyFiles` and `uncompressFile`
  catch `Throwable`, so an `Error` skipped `zipFile.delete()` and left a partial archive. Widened.
- `FileRepository.kt:827` — a mid-archive `IOException` (removable storage unmounted, EIO, a source
  that vanished) propagated raw into the generic ViewModel catch and filed a non-fatal for an
  environmental condition. Now wrapped in `FileTransferIOException`, which
  `FolderViewModel.kt:913` shows as a toast without reporting — the fix for the original
  Crashlytics report that started this sweep.
- `addToZip`'s directory branch had **no** unit coverage at all: every `compressFiles` unit test
  passed a plain or missing file, so the recursion was exercised only by the instrumentation suite.
  Covered by `compressFiles archives a directory tree under its own entry names`.
