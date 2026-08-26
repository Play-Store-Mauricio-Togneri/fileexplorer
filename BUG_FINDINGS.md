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
  (related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/FileErrors.kt:40`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/ImageErrors.kt:27`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/AppImageLoader.kt:120`, `:127`)
- **Severity:** High
- **Confidence:** Medium
- **Status (2026-08-24):** the leak this entry describes is **closed**; the report it describes
  is not. `ed3ab14` changed the site to `ErrorReporter.warning(it.scrubbed(), "image_viewer_load")`,
  and `scrubbed()` (`ErrorScrubbing.kt:52-57`) replaces the throwable with
  `IOException(javaClass.name)`, dropping the message and the whole cause chain — so no SVG markup
  can reach Crashlytics. The suggested fix is still worth doing, now purely to stop filing an
  unactionable bad-file condition. References below were repointed after `23cf6c5` moved the
  predicate: `isUnreadableImage` is now `isUnreadableFile` in `FileErrors.kt:40`, and
  `ImageErrors.kt:27` is now `isUndecodableImage`.
- **Defect:** `.svg`/`.svgz` are viewable (`MimeTypeUtil.kt:89`) and `SvgDecoder.Factory()` is
  registered on both image loaders, so a malformed SVG raises AndroidSVG's `SVGParseException`. It
  extends `Exception` directly, so it matches neither `isUnreadableFile` (`e is IOException`) nor
  `isUndecodableImage` (an `IllegalStateException` message match against two fixed Coil strings) and
  falls through to `ErrorReporter.warning`. `SVGParseException` messages quote the offending markup,
  so what is uploaded is file **content**, not a path. `ImageErrors.kt:23` names "a malformed SVG"
  as a case that "remain[s] worth reporting" — so the report is intended; its payload was not.
- **Trigger:** open any `.svg` whose XML the parser rejects.
- **Evidence / verification:** Read `ImageViewerViewModel.kt:95-108` (both filters applied, then
  `ErrorReporter.warning`), `FileErrors.kt:40` and `ImageErrors.kt:27-30` (the two filter
  definitions, in separate files since `23cf6c5`, neither matching a direct `Exception` subclass),
  `AppImageLoader.kt:120`/`:127` (decoder registered on
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
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/textviewer/TextViewerViewModel.kt:97`)
- **Severity:** Medium
- **Confidence:** High
- **Status (2026-08-24):** premise no longer holds at HEAD. All five sites now pass `e.scrubbed()`
  (`ed3ab14`), so none hands the platform exception unchanged and no path is transmitted.
  `SqliteMetadataExtractor` (`e5228b3`) and `TextViewerViewModel` (`23cf6c5`) additionally gained
  the `isUnreadable*` filter this entry asks for. What is left of it is the three remaining readers,
  carried forward as
  [a/error-handling/metadata-extractors/unfiltered-catch-reports-expected-read-failures] below, where
  the residual cost is non-fatal volume rather than exposure.
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
  `SqliteMetadataExtractor.kt:27` + `:42-43`, `TextViewerViewModel.kt:92-98` — in every case the
  read is inside the guarded region and no `isUnreadable*`-style predicate stands between the catch
  and the report. Contrast `FileErrors.kt:40`, which is the guard the package already uses.
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

## Medium

### [coverage/home-screen-tap-routing/routing-decision-unreachable-from-tests] The tap routing decision is business logic no test can reach

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/home/HomeScreen.kt:537`
  (related: `:574`, `:518`, `:555`, `:285`, `:306`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/util/StartupDestinationResolver.kt:36`,
  `app/src/test/java/com/mauriciotogneri/fileexplorer/util/StartupDestinationResolverTest.kt`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:110`)
- **Severity:** Medium
- **Confidence:** High
- **Defect:** `openRecentFile` and `openFavorite` decide, from a live `File.isDirectory` stat, whether
  a tap navigates into a folder or is routed to `IntentUtil.openFile` — and, for favorites, refill an
  empty stored `mimeType`. That is business logic, which `CLAUDE.md` requires to carry a unit test and
  forbids letting coverage fall for. Both functions are `private` top-level declarations in a
  Composable file whose only callers are the tap lambdas at `:285` and `:306`, so no unit test can
  reach them, and the three home instrumentation tests only assert that `onFileClick` fires. The
  identical drift decision on the delete path is pinned five times over in `HomeViewModelTest`; this
  one is pinned zero times, so any future edit — tightening the predicate, dropping the mimeType
  refill, reordering the `exists()`/`isDirectory` checks — is silent.
- **Trigger:** not a runtime failure. It surfaces the next time either function is edited.
- **Evidence / verification:** `grep -rn 'openRecentFile\|openFavorite' app/src` returns four hits —
  two declarations and two call sites, all inside `HomeScreen.kt`; nothing in `app/src/test` or
  `app/src/androidTest` names either. `HomeScreenTest` renders only `LocationsSection`/
  `StoragesSection`; `RecentFilesSectionTest` and `FavoritesSectionTest` pass their own
  `onFileClick` and assert only that the callback receives the tapped model; `HomeDialogsTest`
  renders dialogs standalone. `scripts/check_tests.py:22` scans `app/src/androidTest/java` only and
  contains no coverage guard, so nothing mechanical reports this.
- **Suggested fix:** extract the decision into a pure resolver under `util/`, following
  `StartupDestinationResolver` — the codebase's existing precedent for exactly this shape: a stored,
  untrusted path resolved against the filesystem, returning a sealed destination or null, called from
  the UI layer (`MainActivity.kt:110`) and covered by 168 lines of JVM tests.

  1. Add `util/StoredEntryDestination.kt`: a sealed interface with `Missing`,
     `Folder(path: String, title: String)` and `Open(file: FileItem)`, plus an object
     `StoredEntryDestinationResolver` exposing
     `resolve(path: String, name: String, mimeType: String): StoredEntryDestination`. Move the three
     decisions verbatim into it — `!exists()` → `Missing`; `isDirectory` → `Folder`; otherwise
     `Open(FileItem(..., isDirectory = false, mimeType = mimeType.ifEmpty {
     MimeTypeUtil.getMimeType(File(path)) }, ...))`. Keep the existing explanatory comments with the
     code they explain.
  2. Reduce `openRecentFile`/`openFavorite` to a `when` over the result: `Missing` → the existing
     `recent_file_not_found` toast; `Folder` → the existing `FolderActivity.createIntent`;
     `Open` → the existing `openFileItem`. `openFavorite` keeps nothing else; the favorites-only
     mimeType refill moves into the resolver and becomes harmless for recents, whose stored mimeType
     is never empty (`addRecentFile` refuses directories, `RecentFilesRepository.kt:52`).
  3. Add `app/src/test/java/.../util/StoredEntryDestinationResolverTest.kt` on a `tempDir` built the
     way `HomeViewModelTest.kt:113` builds one. Cover, at minimum: a missing path → `Missing`; a real
     file → `Open` with `isDirectory = false`; a directory at a path whose stored name ends `.apk` →
     `Folder`, **not** `Open` (this is the case the whole fix exists for); a directory at a path whose
     stored name ends `.md` → `Folder`; an empty stored mimeType on a real `.zip` file → `Open`
     carrying `application/zip`, so `FileItem.isZip` is true (`isApk`/`isZip` are the only
     `FileTypeInfo` predicates with no by-extension fallback, `FileItem.kt:31-32`, and
     `IntentUtil.openFile:91,95` tests them before its own `ifEmpty` refill at `:106`); and a
     non-empty stored mimeType being preserved rather than re-derived.

  This closes the gap without an emulator. Moving the decision into `HomeViewModel` instead would
  also work and would put the stat on `ioDispatcher` — both callback parameters are one-line
  ViewModel calls (`showUncompressDialog`, `setPendingApkInstall`) and would disappear — but it needs
  new `HomeUiEvent` variants and event plumbing for the navigation, and `IntentUtil.openFile` already
  performs main-thread binder IPC (`resolveActivity` at `IntentUtil.kt:112`) in this same handler, so
  the dispatcher win is marginal.

### [a/state-and-lifecycle/home-action-sheets/sheets-gate-on-stored-type] Long-press offers file actions for an entry the tap now opens as a folder

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/components/FavoriteFileActionsBottomSheet.kt:77`
  (related: `:53`, `:56`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/components/RecentFileActionsBottomSheet.kt:48`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/home/HomeViewModel.kt:428`, `:540`,
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/home/HomeScreen.kt:350`, `:422`)
- **Severity:** Medium
- **Confidence:** High
- **Defect:** Both action sheets classify the entry from the **stored** type.
  `FavoriteFileActionsBottomSheet:77` gates Open with / Share behind `!favorite.isDirectory`, and
  `:53`/`:56` derive the `extension`/`mimeType` analytics params the same way;
  `RecentFileActionsBottomSheet` has no directory gate at all, because `RecentFile.isDirectory` is a
  constant `false`. `HomeViewModel.showRecentFileActions:428` and `showFavoriteActions:540` open the
  sheet after checking `exists()` only, so the stored flag reaches the sheet unvalidated. Now that the
  tap handlers stat the path, tapping such an entry opens a folder while long-pressing the same row
  still offers Open with and Share on it — one gesture corrected, its neighbour not.
- **Trigger:** delete `/Download/notes.md` from the folder screen (which does not prune recents), create
  a folder named `notes.md` in its place, then long-press the recents or favorites card on home.
- **Evidence / verification:** Both sheet files were read in full and are byte-identical to their
  state before the tap fix — the divergence is an incompletely applied fix, not a regression.
  Inert rather than harmful: `HomeScreen.kt:355` (OpenWith) and `:373`/`:441` (Share) build
  `FileItem(isDirectory = false, ...)` and reach `IntentUtil.openFileWith` / `shareFiles`, neither of
  which has an `isApk` branch, so the install path is not reachable from the sheet.
  `provider_paths.xml` declares `<root-path path="/"/>` and `FileProvider.getUriForFile` performs no
  stat, so a URI is minted for the directory; the recipient's `openFile` then fails with `EISDIR`.
  FileProvider exposes no directory enumeration and its `query()` returns only DISPLAY_NAME and SIZE,
  so nothing is leaked — the actions simply fail in the other app. The `EISDIR` behaviour is
  documented platform behaviour, not read in-repo.
- **Suggested fix:** stat once where the sheet is opened, and pass the answer down.

  1. In `HomeViewModel.showRecentFileActions:428` and `showFavoriteActions:540`, the `exists()` call
     already runs inside `withContext(ioDispatcher)` — widen it to read `isDirectory` in the same
     block and store it on `HomeUiState` beside `selectedRecentFile`/`selectedFavorite` (e.g.
     `selectedRecentFileIsDirectory`). This is the layer that already owns the stat, so no new
     main-thread I/O is introduced.
  2. Pass it into both composables at `HomeScreen.kt:350` and `:422` as an `isDirectory: Boolean`
     parameter. `RecentFileActionsBottomSheet` already takes a comparable `isFavorite: Boolean`
     (`RecentFileActionsBottomSheet.kt:51`), so the shape is established.
  3. Gate on the parameter instead of the model: replace `!favorite.isDirectory` at
     `FavoriteFileActionsBottomSheet.kt:77` with `!isDirectory`, use it for the `extension`/`mimeType`
     values at `:53`/`:56`, and add the same `!isDirectory` gate around Open with / Share in
     `RecentFileActionsBottomSheet`.
  4. Cover it in `HomeViewModelTest` with the `tempDir` idiom the delete guards already use
     (`HomeViewModelTest.kt:511`): a recents entry and a favorite whose path is a real directory must
     put `isDirectory = true` into the state; ordinary file entries must leave it false.

  Delete is already handled and needs no change here — `confirmDeleteRecentFile` and
  `confirmDeleteFavorite` re-stat and refuse (`HomeViewModel.kt:494`, `:606`).
