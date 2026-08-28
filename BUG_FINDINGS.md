## Medium

### [c/dead-or-unreachable/test-structure-guards/backtick-identifier-blinds-scanner] A backticked test name containing an apostrophe silently disables two structural guards

- **Location:** `scripts/check_tests.py:104` (inside `blank()`); consumed by
  `check_no_test_composables` (`:159`), `check_no_discarded_assertions` (`:419` via
  `_opaque_literals:370`)
- **Severity:** Medium
- **Confidence:** High
- **Defect:** `blank()` erases comments and string literals before the guards scan a file. Its
  literal branch is `elif text[i] in "\"'":` — it handles `"` and `'` but has **no branch for a
  backtick**. Kotlin's backtick-quoted declaration names are therefore scanned as ordinary code, so
  an apostrophe inside one (`` fun `it doesn't crash`() ``) is read as opening a char literal. The
  scanner runs to the next `'` in the file, or to EOF, and for the `strings=True` callers erases
  everything in between. Every violation after that point becomes invisible and `check-tests.sh`
  still exits 0 — the guard reports OK rather than reporting that it could not scan.
- **Trigger:** any file under `app/src/androidTest/java` that declares a backtick-quoted name
  containing an apostrophe, with a violation later in the file.
- **Evidence / verification:** Reproduced with a control/treatment pair in an isolated workspace
  outside the repository (a copy of the script with `ROOT` repointed, run with `python3 -B`). Two
  files identical except for one added backticked test name:
    - control (no backticked name): `No test-local @Composable declarations` **FAIL**,
      `No discarded assertion results` **FAIL**
    - treatment (` fun `it doesn't crash`() ` added above the violations): both **OK**, violations
      still present.

  The guards are otherwise not vacuous — against a synthetic violating file they correctly fail, and
  against the real tree all four print OK. Refutation attempt: I checked whether the 8
  apostrophe-bearing backticked names already present in `app/src/test` trigger this today. They do
  not — `kotlin_files()` (`:42-43`) globs `ANDROID_TEST` only (`:28`), so the unit-test tree is
  never scanned. I also confirmed the ordering constraint: violations *before* the backticked name
  still fail, so only content after it is blinded.
- **Reachability:** latent, not currently active. `app/src/androidTest` has **0** backticked
  declaration names today, so nothing is being concealed right now, and `check-tests.sh` passes
  legitimately. But `app/src/test` has **813** such names (8 with apostrophes) and `CLAUDE.md`'s
  testing section endorses that style, so the first instrumentation test written that way turns two
  guards off for its file with no signal. The guard that catches discarded assertions is exactly the
  one protecting against tests that assert nothing.
- **Baseline attribution:** INTRODUCED. `git cat-file -e d0b63a1c:scripts/check_tests.py` fails —
  the file does not exist at baseline (`A` in the diff).
- **Suggested fix:** add a backtick case to `blank()` that skips from one `` ` `` to its closing
  `` ` `` without interpreting anything inside, placed alongside the existing `"` / `'` branch. A
  backticked name cannot span a newline, so bounding the skip at the next newline also guards
  against an unterminated backtick. Consider making an unterminated literal a hard error rather than
  an erase-to-EOF, so a scan that cannot be completed fails loudly instead of passing.

### [a/state-and-lifecycle/app-startup/main-thread-blocking-io] Cold start blocks the main thread on filesystem I/O before the first frame

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:110-115` (also
  `:45-47`, `:79-99`)
- **Severity:** Medium
- **Confidence:** Medium
- **Defect:** `onCreate` calls `openStartupFolder()` before `setContent`, which calls
  `startupDestination(path)`:

  ```kotlin
  private fun startupDestination(path: String) = try {
      runBlocking(Dispatchers.IO) {
          val storages = StorageRepository(AndroidStorageSource(this@MainActivity)).getStorages()
          StartupDestinationResolver.resolve(path, storages)
      }
  ```

  `runBlocking` moves *where* the syscalls execute, not *whether the main thread waits*.
  `AndroidStorageSource.getStorages()` calls `context.getExternalFilesDirs(null)` — which stats
  every mounted volume and creates the app directory on any volume missing it — plus one `StatFs`
  per deduplicated volume; `StartupDestinationResolver.resolve` then adds `isDirectory` + `canRead`.
  The launcher Activity's first frame is delayed by exactly that wall-clock time, unbounded when a
  volume is slow, spinning up, or stalled in FUSE.

  The KDoc at `:102-104` states *"nothing reads the filesystem on the main thread"*. That is
  misleading: no filesystem call executes **on** the main thread, but the main thread blocks for its
  full duration. Routing through `Dispatchers.IO` additionally hides the work from StrictMode's
  disk-read detection, so the normal safety net will not flag it.
- **Trigger:** cold start with the startup screen set to a folder and storage permission granted.
- **Evidence / verification:** `git show d0b63a1c:.../MainActivity.kt` — baseline `onCreate` is
  `super.onCreate` + `enableEdgeToEdge` + `setContent`, with **zero** `runBlocking`; HEAD has two. A
  separate refutation agent confirmed the mechanism and established three corrections I have
  adopted: (a) the blocking *preference* read is not new cost — `FileExplorerApplication.onCreate`
  already performed two `runBlocking` DataStore reads at baseline, so the store is warm and
  `getInitialStartupFolderPath()` hits `inMemoryCache`; only the **filesystem** portion is new; (b)
  two gates bound the audience — `hasStoragePermission()` at `:84` and `?: return` at `:87`, so
  default-configuration users pay only the warm preference read; (c) `savedInstanceState == null`
  plus MainActivity's `configChanges` (`AndroidManifest.xml:55`) confine this to cold start. Nothing
  memoizes `getStorages()`, so there is no guard making the added cost free.
- **Remaining uncertainty (why Medium confidence):** on a healthy device with internal storage only
  this is a handful of stats, plausibly sub-millisecond, and invisible. The impact is
  device-dependent and I could not measure it — no emulator or device was available. That refutation
  pass argued for **Low** severity on this basis. I have kept **Medium** because the worst credible
  impact under a realistic trigger is a launch-time stall of unbounded duration on removable
  storage, and the project's own standard in `CLAUDE.md` is explicit: *"Run file I/O, sorting, and
  filtering on background dispatchers — never block the main thread."* Reasonable reviewers could
  rank this Low; the disagreement is recorded rather than resolved.
- **Baseline attribution:** INTRODUCED (filesystem work only; the blocking-preference-read pattern
  is pre-existing house style).
- **Suggested fix:** launch the startup folder asynchronously rather than blocking. Compose the home
  screen immediately with a flag suppressing its first frame, resolve the destination in a
  coroutine, then start `FolderActivity` — or hoist resolution into `MainViewModel` and drive it
  from state. If the synchronous behaviour is genuinely required to stop the home screen flashing
  past, bound it with `withTimeout` so a stalled volume degrades to the home screen instead of
  holding the launch, and correct the KDoc to say the main thread blocks.

## Low

### [a/resource-management/settings-viewmodel/activity-context-retained]
`SettingsViewModel` retains a destroyed Activity across rotation

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/settings/SettingsViewModel.kt:277`;
  reached from
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/SettingsActivity.kt:121`
- **Severity:** Low
- **Confidence:** High
- **Defect:** The Factory gained
  `storageRepository = StorageRepository(AndroidStorageSource(context))`. `AndroidStorageSource`
  holds its Context in a field (`class AndroidStorageSource(private val context: Context)`), and
  `SettingsActivity` passes `LocalContext.current` from inside `setContent` — the Activity itself.
  `SettingsActivity` declares no `configChanges` (`AndroidManifest.xml:72-74`), so rotation destroys
  and recreates it while the `ViewModelStore` survives; the retained ViewModel then pins a destroyed
  Activity and its view hierarchy.

  Every other ViewModel Factory in the codebase passes `application` (`FolderViewModel.kt:1116`,
  `HomeViewModel.kt:728`, `SearchViewModel.kt:384`, `ItemInfoViewModel.kt:317`). This is the lone
  outlier.
- **Trigger:** open Settings, rotate the device.
- **Evidence / verification:** `git show d0b63a1c:.../SettingsViewModel.kt` — the baseline Factory
  built only the four DataStore repositories, and androidx's `preferencesDataStore` delegate
  resolves through `applicationContext` (`PreferenceDataStoreDelegate.android.kt:98`:
  `val applicationContext = thisRef.applicationContext`), so nothing Context-holding was retained
  before. Confirmed `SettingsActivity` has no `configChanges` in the manifest. A separate refutation
  pass corrected the mechanism in two ways I have adopted: the Factory runs only when the ViewModel
  does not yet exist, so **exactly one** Activity is pinned regardless of how many rotations
  follow (not one per rotation); and retention is released at `onCleared()` when Settings finishes,
  so the blast radius is one Activity held for the lifetime of the settings screen. The
  stale-resources concern was refuted outright — `_storages` is loaded once in `init` (`:75-83`) and
  never refreshed, so the labels are computed at creation time regardless of which Context is held,
  and there is no in-app language switcher.
- **Baseline attribution:** INTRODUCED (the `Factory(context)` shape itself is pre-existing; the
  Context-retaining dependency is new).
- **Suggested fix:** pass `context.applicationContext` to `AndroidStorageSource` at
  `SettingsViewModel.kt:277`, matching the other four Factories.
