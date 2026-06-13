# Bug Findings

Apocalypse bug review of the FileExplorer Android app (Kotlin + Jetpack Compose, minSdk 23 /
targetSdk 37). Focus: defects that cause incorrect application, data, security, build, or release
behavior. Discovery used parallel read-only agents over a deterministic partition of all 141 main
source files; every reported finding was independently re-verified against the source by reading the
exact code paths and attempting refutation. Verification was **static** (no on-device execution);
the
worktree was treated as read-only and is byte-for-byte unchanged from the initial snapshot.

---

## Medium

### [a/error-handling/move-operation/failed-source-delete-reported-as-success] Move silently degrades to copy and falsely notifies MediaStore of deletion

- **Location:** `data/repository/FileRepository.kt:295` and `:327` (ignored `source.delete()`
  result), `:335-344` (unconditional `isComplete`); `ui/screens/folder/FolderViewModel.kt:392-404` (
  `notifyDeleted(sourcePaths)`).
- **Severity:** Medium
- **Confidence:** High
- **Defect:** In `copyRecursive`, the move branch runs `if (deleteAfter) source.delete()` for both
  directories (295) and files (327) but discards the returned `Boolean`. The flow then emits
  `isComplete = true` regardless of whether any source was actually removed. The ViewModel treats
  this as success: it tracks the move as succeeded and, for MOVE, calls
  `MediaStoreUtil.notifyDeleted(context, sourcePaths)` — telling MediaStore the originals are gone
  while they remain on disk. A "move" that failed to delete its source silently becomes a
  copy-with-leftover, reported as a successful move, and the still-present files vanish from
  Gallery/media apps until the next scan.
- **Trigger:** Move a file/folder from a location that is readable but not deletable (
  read-only-mounted SD card / OTG volume, or a permission-restricted path).
- **Evidence / verification:** The non-progress `deleteRecursive` correctly propagates failure (
  `file.delete() && allSucceeded`, line 217), proving the move path's discarded result is an
  omission, not a convention. No post-move existence check exists. Refutation: the comprehensive
  catch ladder in `executeOperationInternal` never sees this because no exception is thrown —
  `delete()` returning `false` is silent.
- **Suggested fix:** Capture `source.delete()`; if it fails, surface a failure (or treat the
  operation as a partial failure) and do not emit unconditional success or notify MediaStore of a
  deletion that did not happen.

### [a/error-handling/archive-extraction/partial-files-not-cleaned-on-failure] Extraction leaves partial files on cancel or any non-bomb/slip failure

- **Location:** `data/repository/FileRepository.kt:544-600` (try/catch covers only
  `ZipBombException`/`ZipSlipException`; `currentTargetFile`/`extractedPaths` cleaned only there);
  surfacing in `util/UncompressHandler.kt:142-148` (CancellationException swallowed, no cleanup, no
  folder refresh).
- **Severity:** Medium
- **Confidence:** High
- **Defect:** The extraction loop only has `catch (ZipBombException)` and
  `catch (ZipSlipException)`, which delete the in-progress `currentTargetFile` and already-
  `extractedPaths`. Every other failure — a corrupt/truncated entry (`ZipException` during
  `input.read`), disk full (`IOException`), `getUniqueTargetFile` exhaustion, a per-entry wrong
  password, or **user cancellation** (the `emit` at 574 throws `CancellationException`) — propagates
  past these catches with **no cleanup**. The already-extracted files plus the half-written
  `currentTargetFile` (a truncated file indistinguishable from a complete one) are left in the
  user's folder, while `UncompressHandler` reports failure (or, on cancel, silently) and does not
  call `loadFiles()`. This is inconsistent with `compressFiles` (which deletes its output on any
  exception, line 482-485) and the bomb/slip paths that do clean up.
- **Trigger:** Start extracting an archive and tap Cancel mid-way, or extract an archive with a
  corrupt later entry, or fill the disk during extraction.
- **Evidence / verification:** Catch list confirmed to contain only the two exception types;
  `currentTargetFile` is set at 561 and nulled only on per-entry success at 586, so on a failing
  write it holds a partial file. `CancellationException` is neither subclass, so it escapes
  uncleaned; `UncompressHandler` catches it at 144 without rethrow and performs no cleanup. No test
  pins post-failure residue.
- **Suggested fix:** Wrap the extraction loop in
  `try { … } catch (Throwable) { currentTargetFile?.delete(); extractedPaths.forEach { File(it).delete() }; throw }` (
  or a `finally` that cleans up unless completion succeeded), so cancellation and all error paths
  remove partial output.

### [a/concurrency/recursive-fs-operations/missing-cooperative-cancellation] Search and extraction ignore cancellation between items

- **Location:** `data/repository/FileRepository.kt:386-419` (`searchIn`, no `ensureActive()`);
  `:545-590` (uncompress loop, no `ensureActive()`). Contrast siblings that do check: `:226` (
  delete), `:281` (copy), `:440` (compress).
- **Severity:** Medium
- **Confidence:** High
- **Defect:** Both recursive operations lack a `currentCoroutineContext().ensureActive()` and their
  only suspension/cancellation point is `emit`. For **search** (`emit` at 407 only fires on a
  match), changing the query or a filter cancels the collector but the upstream `Dispatchers.IO`
  walk keeps running `listFiles()` to completion for any subtree that matches nothing — so each
  post-debounce keystroke stacks another full, uninterruptible recursive walk onto the limited IO
  pool, wasting CPU/IO/battery and making the newest search sluggish on large storage. For *
  *uncompress** (`emit` at 574 only fires while writing bytes), an archive dominated by directory
  entries or zero-byte file entries runs to completion after Cancel — creating thousands of
  files/dirs while the Cancel button appears dead.
- **Trigger:** Type/edit a search query (or toggle a filter) on a large tree; or cancel extraction
  of an archive full of empty/dir entries.
- **Evidence / verification:** Confirmed by grep that `ensureActive` appears at 226/281/440 but
  nowhere in `searchIn` or the 545-590 loop. `flowOn(Dispatchers.IO)` moves only the producer; a
  blocking `listFiles()`/`mkdirs()` is not interrupted without a cooperative check. The cancelled
  collector cannot leak stale UI results (it stops), so this is a
  wasted-work/uninterruptible-operation defect, not a stale-overwrite.
- **Suggested fix:** Call `currentCoroutineContext().ensureActive()` at the top of `searchIn` and at
  the top of each iteration of the uncompress `for (header in headers)` loop, matching the sibling
  operations.

### [b/contract-mismatches/mediastore-sync/notifications-diverge-from-filesystem] MediaStore index drifts from the real filesystem after copy/move and partial delete

- **Location:** `ui/screens/folder/FolderViewModel.kt:392-399` (copy/move path reconstruction) and
  `:573-578` (unconditional `notifyDeleted(allPaths)`); `util/MediaStoreUtil.kt:11-40`;
  `data/repository/FileRepository.kt:347-370` (collision rename).
- **Severity:** Medium
- **Confidence:** High
- **Defect:** Three concrete inconsistencies: (1) On copy/move completion the VM scans
  `"$targetPath/${File(item.path).name}"` — top-level original names only.
  `MediaScannerConnection.scanFile` does not recurse, so a copied/moved **folder's** child media is
  never indexed at the new location, and for a move the children's old rows are never removed. (2)
  When `copyFiles` renames on collision to `name (1).ext` (`getUniqueTargetFile`), the VM still
  scans the original `name.ext`, so the file actually created is never indexed while a pre-existing
  unrelated file is rescanned. (3) In the large-delete (`deleteWithProgress`) branch,
  `MediaStoreUtil.notifyDeleted(context, allPaths)` runs unconditionally on completion, including
  paths that **failed** to delete — so still-present files vanish from MediaStore views; the
  small-delete branch correctly notifies only on full success (line 561), so the two paths disagree.
- **Trigger:** Copy/move a folder containing media; copy a file into a folder that already has a
  same-named file; partially-failed delete of a ≥10-node tree.
- **Evidence / verification:** Confirmed `scanFile`/`scanFiles` pass paths verbatim with no
  directory walk; `getUniqueTargetFile` unconditionally suffixes on collision while the VM
  reconstructs names from `item.path`; `notifyDeleted(allPaths)` at 576 sits outside any
  `failedFiles == 0` guard. Delete's own `collectAllPaths` is recursive, proving the recursive
  contract is understood elsewhere. Self-heals on the next full media scan, hence Medium not High.
- **Suggested fix:** Scan/notify the actual created paths (return them from `copyFiles`), recurse
  into directories for media scanning, and gate `notifyDeleted` on the set of paths that actually no
  longer exist.

### [a/concurrency/folder-size/unbounded-symlink-following-walk] Folder-size calculation follows symlinks and ignores cancellation

- **Location:** `ui/screens/iteminfo/ItemInfoViewModel.kt:261-269` (`calculateFolderSize`), invoked
  from `:250-259`/`:237-239`.
- **Severity:** Medium
- **Confidence:** High
- **Defect:** `folder.walkTopDown().forEach { if (it.isFile) size += it.length() }` has no symlink
  guard, no traversal bound, and no `ensureActive()`. `FileTreeWalk` decides descent via
  `File.isDirectory`, which resolves symlinks, so it walks **into** symlinked directories: the
  reported size double-counts content reachable through links and includes symlink-target content —
  inconsistent with every other size routine in the app (`FileRepository.totalSize` returns `0L` for
  symlinks; `LocationsRepository.calculateDirectorySize` bounds the same `walkTopDown()` with
  `.take(10000)`). A symlink cycle makes the walk run pathologically (until paths exceed `PATH_MAX`
  and `listFiles()` returns null), counting cycle content many times. With no suspension point it
  also ignores cancellation, so closing the Info screen leaves the walk running on an IO thread.
- **Trigger:** Tap "Info" on a directory that contains (or is reachable to, via the app's all-files
  access) a symlinked or cyclically-linked subtree.
- **Evidence / verification:** Confirmed this is the lone unguarded recursive traversal; every
  `FileRepository` recursion calls `isSymlink()` and most call `ensureActive()`. Runs on
  `Dispatchers.IO`, so not a main-thread ANR, but the wrong size and runaway/uninterruptible
  behavior stand.
- **Suggested fix:** Skip symlinks (`Files.isSymbolicLink`), add `ensureActive()` inside the loop,
  and bound the walk (`.take(MAX)`), mirroring `FileRepository.totalSize`/`LocationsRepository`.

### [a/error-handling/document-metadata/unbounded-entry-read-oom-crash] Crafted Office/EPUB document crashes the app on Info via unbounded entry read

- **Location:** `data/util/OfficeMetadataExtractor.kt:18` (`readText()` on `docProps/core.xml`);
  `data/util/EpubMetadataExtractor.kt:19` and `:35` (`readText()` on the OPF and
  `META-INF/container.xml`); both caught only by `catch (Exception)` at `ItemInfoViewModel.kt:243`.
- **Severity:** Medium
- **Confidence:** Medium
- **Defect:** These extractors call `.readText()` on a single ZIP entry's inflater stream with no
  size cap, materializing the entire decompressed entry in memory. ZIP entry sizes are
  attacker-controlled, so a small `.docx`/`.xlsx`/`.pptx`/`.epub` whose target entry inflates to
  gigabytes (a zip-bomb entry) throws `OutOfMemoryError`. `OutOfMemoryError` is an `Error`, not an
  `Exception`, so neither the extractor's `catch (Exception)` (Office:21 / Epub:22) nor
  `ItemInfoViewModel`'s `catch (Exception)` (243) catches it — it propagates out of the
  `viewModelScope.launch(Dispatchers.IO)`, which has no `CoroutineExceptionHandler`, crashing the
  app.
- **Trigger:** User taps "Info" on a crafted Office/EPUB file (a file explorer routinely points at
  arbitrary downloaded files).
- **Evidence / verification:** No `entry.size` guard precedes either read (verified). `ZipFile`
  /streams are closed via `.use` (no leak); ZIP slip does not apply (entries are read into memory,
  never written to a derived path); XXE does not apply (`FEATURE_PROCESS_DOCDECL` never enabled).
  Unlike the Coil-wrapped thumbnail fetchers, this metadata path's catch is `Exception`-only, so the
  OOM definitively escapes. Remaining uncertainty (Medium): whether a given entry size OOMs depends
  on device heap, but a multi-GB declared/inflating entry reliably exhausts mobile heaps.
- **Suggested fix:** Cap the read (e.g. read at most N KB via a bounded reader, or check
  `entry.size` against a limit before reading); catch `Throwable` (or `OutOfMemoryError`) around
  metadata extraction.

### [a/null-and-numeric-hazards/pdf-thumbnail/unbounded-bitmap-dimensions] PDF thumbnail computes unbounded bitmap dimensions

- **Location:** `data/util/PdfThumbnailFetcher.kt:45-50` (scale/width/height), `:27` (
  `catch (e: Exception)`).
- **Severity:** Medium
- **Confidence:** Medium
- **Defect:** `scale` normalizes only the page **width** to the target;
  `height = (page.height * scale)` has no upper bound (only `coerceAtLeast(1)`). A first page with
  an extreme aspect ratio (e.g. width 1pt, height 14400pt — a valid PDF MediaBox) yields, even at
  list size 120 px, a `120 × 1,728,000` ARGB_8888 bitmap (~829 MB); at Info size 400 px, ~9 GB. A
  zero-width page makes `scale = 120/0 = +Infinity`, so
  `height = (page.height*Inf).toInt() = Int.MAX_VALUE`. `createBitmap` then throws
  `OutOfMemoryError` (an `Error`, not caught by the `Exception` handler at line 27). Coil generally
  converts this to a failed request (so a hard Java crash is not guaranteed), but the multi-GB
  allocation attempt causes severe memory pressure and risks the system low-memory killer
  terminating the process; the thumbnail also fails outright for legitimately tall PDFs.
- **Trigger:** Browse a folder containing a crafted/extreme-aspect-ratio PDF (any PDF auto-issues a
  thumbnail request — `FileItem.hasThumbnailSupport` is true for PDFs).
- **Evidence / verification:** Only `coerceAtLeast(1)` bounds the dimensions (no upper clamp, no
  total-pixel cap); the blow-up is driven by page geometry, not request size. Refutation: the
  `catch` is on `Exception`, so OOM escapes the fetcher; whether Coil's outer chain swallows the
  `Throwable` is the one uncertainty (Medium) — but the unbounded allocation and the wrong/failed
  result for tall pages are certain regardless.
- **Suggested fix:** Clamp both dimensions and the total pixel count to a sane maximum (scale to fit
  the target box, not just width), and treat zero/garbage page dimensions defensively.

### [a/null-and-numeric-hazards/archive-extraction/uncompressed-size-sum-overflow] Crafted ZIP64 sizes overflow the pre-extraction storage/bomb checks

- **Location:** `data/repository/FileRepository.kt:528` (
  `headers.sumOf { it.uncompressedSize.coerceAtLeast(0) }`); checks at `:530` and `:534-536`.
- **Severity:** Medium
- **Confidence:** Medium
- **Defect:** `coerceAtLeast(0)` clamps individual negative declared sizes but does not prevent the
  `Long` **sum** from overflowing to a negative value. A crafted ZIP64 archive declaring a few
  entries near `Long.MAX_VALUE` produces a negative `totalBytes`, making both guards false:
  `totalBytes > MAX_UNCOMPRESSED_SIZE` (bomb) and `totalBytes > availableSpace` (insufficient
  storage) are skipped. The runtime per-byte cap at line 570 still bounds the bomb to ~10 GB, but
  the insufficient-storage pre-check has no runtime backstop, so an archive with real content larger
  than free space proceeds past the point it should have been cleanly rejected, fills the disk,
  throws ENOSPC `IOException`, and (per
  `[a/error-handling/archive-extraction/partial-files-not-cleaned-on-failure]`) leaves the partial
  output on disk.
- **Trigger:** Extract a crafted ZIP64 archive whose central-directory declares overflowing
  uncompressed sizes, on a device with less free space than the real extracted size.
- **Evidence / verification:** Two's-complement: sum of large positives wraps negative, and
  `negative > positiveLimit` is false. `FileHeader.uncompressedSize` comes from attacker-controlled
  ZIP64 metadata read before extraction. Harm is conditional (crafted ZIP64 + free space < real
  size), hence Medium.
- **Suggested fix:** Detect overflow when summing (e.g. `Math.addExact` in a try, or saturate to
  `Long.MAX_VALUE`), and treat a non-positive/overflowed total as "exceeds limits."

### [a/error-handling/release-signing/unsigned-release-silent-success] Release build is unsigned (not debug-signed) when keystore is absent, and the build script reports success

- **Location:** `app/build.gradle.kts:30-52`; `.gitignore` (`keystore.properties` ignored);
  `scripts/build.sh:10-21`.
- **Severity:** Medium
- **Confidence:** High
- **Defect:** `signingConfigs { if (keystorePropertiesFile.exists()) create("release"){…} }` and
  `release { if (keystorePropertiesFile.exists()) signingConfig = … }` mean that when
  `keystore.properties` is missing, the `release` build type gets **no** signing config. AGP does
  not fall back to debug signing for `release`; it emits an **unsigned** AAB without erroring.
  `scripts/build.sh` then only checks `[[ -f "$AAB_PATH" ]]`, finds the unsigned file, prints "
  Release AAB created", and exits 0 (`set -e` never trips because Gradle succeeds). A clean
  checkout (fresh machine / CI — `keystore.properties` is gitignored and uncommitted) thus produces
  an un-uploadable artifact while reporting success.
- **Trigger:** `./scripts/build.sh` (or `bundleRelease`) on any tree without `keystore.properties`.
- **Evidence / verification:** Confirmed `keystore.properties` is gitignored and untracked, the
  `release` type has no other `signingConfig` assignment, and no CI config injects it.
  Failure-reported-as-success is in `build.sh`.
- **Suggested fix:** Fail the build explicitly when `keystore.properties` is missing for a release
  build (or intentionally fall back and warn), and have `build.sh` verify the artifact is signed (
  e.g. `apksigner verify` / bundletool) rather than merely present.

### [b/validation-and-coercion/filename-validator/accepts-control-chars-and-trailing-dots] FileNameValidator accepts control characters (NUL → crash) and trailing dots

- **Location:** `util/FileNameValidator.kt:7,20-24`; consumed by `FileRepository.rename` →
  `Files.move`/`toPath` (`FileRepository.kt:168`) and `FolderViewModel.onRename` (`:492-512`, no
  try/catch) and `onCreateFolder` (`:523-538`).
- **Severity:** Medium
- **Confidence:** Medium
- **Defect:** `INVALID_FILENAME_CHARS` lists only `/ \ * ? " < > | :`; `isValidFileName` never
  rejects control characters, and callers `trim()` only whitespace. (1) A name containing a NUL (
  ` `) passes validation; in `renameRegular`, `targetFile.toPath()` throws
  `InvalidPathException("Nul character not allowed")` — an unchecked exception not covered by the
  `AtomicMoveNotSupportedException`/`FileAlreadyExistsException`/`IOException` catches — which
  propagates out of the unguarded `viewModelScope.launch` in `onRename`/`onCreateFolder` (no
  `CoroutineExceptionHandler`), crashing the app. (2) A trailing `.` survives trimming and is
  accepted, but FAT/exFAT strip trailing dots, so the on-disk name differs from the requested name,
  breaking subsequent collision/case-rename comparisons on removable storage.
- **Trigger:** Paste a name containing a NUL/control byte into the rename or create-folder field (
  NUL case → crash); create/rename to a trailing-dot name on a FAT/exFAT SD card (mismatch case).
- **Evidence / verification:** Validator confirmed to omit any control-character/trailing-dot check;
  `UnixPath` rejects NUL via `InvalidPathException` (documented); `onRename`/`onCreateFolder` have
  no try/catch and `viewModelScope` has no exception handler. Confidence Medium because UI
  reachability of an embedded NUL requires pasting null-bearing clipboard content.
- **Suggested fix:** Reject control characters (`name.any { it.isISOControl() }`) and trim/reject
  trailing dots and spaces in `isValidFileName`; additionally guard `onRename`/`onCreateFolder`
  against unexpected exceptions.

### [a/error-handling/file-operation/uncaught-statfs-exception-on-vanished-target] Copy/move pre-flight space check can crash the app

- **Location:** `ui/screens/folder/FolderViewModel.kt:345-362` (esp. `:349` `StatFs(targetPath)`
  inside `try { … } finally { … }` with no `catch`).
- **Severity:** Medium
- **Confidence:** Medium
- **Defect:** `executeOperation` runs `StatFs(targetPath).availableBytes` (and
  `fileRepository.totalSize`) inside a `try`/`finally` block with **no `catch`**. `StatFs(path)`
  throws `IllegalArgumentException` when the native `statvfs` fails on a vanished/unmounted path.
  That exception escapes the `viewModelScope.launch` (SupervisorJob, no `CoroutineExceptionHandler`)
  and reaches the default uncaught handler → process crash. Every other failure mode in the
  operation is carefully caught and converted to a toast in `executeOperationInternal`, which is
  never reached because the throw happens in the pre-check.
- **Trigger:** Pick a destination on removable storage, then have it unmounted/removed (or deleted
  by another app) before/at the moment Confirm runs.
- **Evidence / verification:** Confirmed only `finally`, no `catch`, wraps the `withContext` block;
  the catch ladder lives in `executeOperationInternal` (370-461), reached only after the pre-check.
  `StatFs` throwing `IllegalArgumentException` on an invalid path is documented. Narrow timing
  window (removable storage), hence Medium.
- **Suggested fix:** Wrap the pre-check in try/catch and treat a `StatFs`/IO failure as an "
  operation failed / target unavailable" toast like the other error paths.

### [a/concurrency/home-load/stale-snapshot-overwrites-newer-state] Concurrent Home reloads can resurrect a just-removed recent file

- **Location:** `ui/screens/home/HomeViewModel.kt:177-201` (`loadData`, no job guard; full overwrite
  at 193-198); re-triggered every resume by `ui/screens/home/HomeScreen.kt:106-108` (
  `repeatOnLifecycle(RESUMED){ loadData() }`); interacts with `removeFromRecents` (`:224-235`).
- **Severity:** Medium
- **Confidence:** Medium
- **Defect:** `loadData` keeps no `Job` and cancels nothing, yet runs on every `RESUMED`. Two
  invocations can overlap; whichever finishes its IO last wins (not the most recent). Inside the IO
  block, the `Triple` snapshots `getRecentFiles()` early, then does the slow `getLocations()` (which
  walks up to 10000 files per location after `refreshSizeCache()` clears the cache), widening the
  window. If an in-flight load snapshotted the recents list before the user swipes-to-remove a
  recent (`removeFromRecents` persists the removal and optimistically updates `_uiState`), the older
  load completes and overwrites `_uiState` with the stale list — the removed entry reappears.
- **Trigger:** Return to Home (resume) while a prior `loadData` is mid-IO, then remove a recent
  file (whose underlying file still exists, so it isn't filtered by the `exists()` check).
- **Evidence / verification:** `loadData` has no job tracking (`hasLoadedOnce` only gates the
  spinner); both invocations write `_uiState.value` on Main with no completion ordering guarantee;
  the early recents snapshot plus slow locations load makes the overlap realistic.
- **Suggested fix:** Track the load job and cancel the previous one before starting a new load (or
  use a single `stateIn`/`collectLatest`-driven flow), so the latest load owns the state.

### [a/concurrency/recent-files/non-atomic-read-modify-write] Recent-files updates are lost under concurrency

- **Location:** `data/repository/RecentFilesRepository.kt:28-44` (`addRecentFile`) and `:46-50` (
  `removeRecentFile`); `data/source/DataStoreRecentFilesSource.kt:26-29` (read) and `:31-45` (
  write); concurrent callers `util/IntentUtil.kt:201-217` (per-open `launch` on a shared IO scope)
  and `HomeViewModel.removeFromRecents`/`confirmDeleteRecentFile`.
- **Severity:** Medium
- **Confidence:** Medium
- **Defect:** `addRecentFile`/`removeRecentFile` read the list via `source.getRecentFiles()` (
  `dataStore.data.first()`) in one transaction, mutate in memory, then write the whole list via
  `source.saveRecentFiles()` (`dataStore.edit { … = array.toString() }`) in a *separate* transaction
  that overwrites wholesale. `dataStore.edit{}` serializes writes among themselves, but because the
  read is outside the edit block, two interleaved calls both read the same base and the second write
  clobbers the first. Opening two files in quick succession (each `trackRecentFile` launches on
  `CoroutineScope(SupervisorJob()+Dispatchers.IO)`), or opening one while removing another, loses an
  update: e.g. base `[B]`, open C and D concurrently → both read `[B]`, write `[C,B]` then `[D,B]` →
  C is permanently lost; add+remove interleavings can resurrect a removed file.
- **Trigger:** Two recent-files mutations overlapping in time (multi-threaded `Dispatchers.IO`).
- **Evidence / verification:** Read and write are distinct suspend calls/transactions with no
  surrounding lock or single-`edit` block; the two reachable entry points are independent scopes.
  Dedup/`MAX_RECENT_FILES` stay intact per writer, but cross-writer updates are lost.
- **Suggested fix:** Perform the read-modify-write inside a single `dataStore.edit { }` block (read
  `preferences[KEY]`, mutate, write back) so the operation is atomic, or serialize mutations through
  a `Mutex`.

---

## Low

### [c/api-or-library-misuse/media-thumbnails/ignores-requested-size-unbounded-decode] Video/EPUB thumbnail fetchers ignore the requested size and read/decode at full scale

- **Location:** `data/util/VideoThumbnailFetcher.kt:38` (`getFrameAtTime` returns a full-resolution
  frame); `data/util/EpubThumbnailFetcher.kt:36` (`readBytes()` on the cover entry).
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** The video fetcher decodes a source-resolution frame (a 4K frame is ~33 MB ARGB_8888;
  8K/crafted far more) and never applies `options.size` or `getScaledFrameAtTime`; the EPUB fetcher
  reads the entire cover entry into memory with no size cap (zip-bomb cover). Both
  `catch (Exception)` only, so the resulting `OutOfMemoryError` is not caught by the fetcher —
  though Coil's outer chain generally converts it to a failed request, the large allocations cause
  memory pressure and risk a low-memory process kill.
- **Trigger:** Browse a folder containing a large/crafted video or EPUB (thumbnails auto-load).
- **Evidence / verification:** Confirmed no size honoring/`inSampleSize` in either fetcher and no
  `getScaledFrameAtTime` usage anywhere. Coil containment makes a hard crash non-guaranteed, hence
  Low.
- **Suggested fix:** Honor `options.size` (`getScaledFrameAtTime` on API 27+ with a 23-26 fallback;
  bound EPUB cover reads by `entry.size`/a cap), and catch `Throwable` in the fetchers.

### [a/error-handling/copy-operation/partial-destination-not-cleaned] Mid-copy I/O failure leaves a truncated destination file

- **Location:** `data/repository/FileRepository.kt:296-328` (catch at 318 rethrows without deleting
  `targetFile`).
- **Severity:** Low
- **Confidence:** High
- **Defect:** `getUniqueTargetFile` creates the destination, then bytes stream in. On `IOException`
  the catch wraps and rethrows `FileTransferIOException` but never deletes the half-written
  `targetFile`, leaving a truncated file indistinguishable from a complete copy. For a MOVE this
  compounds with earlier-deleted children (line 327), leaving the operation half-done with no
  rollback. Inconsistent with `compressFiles`, which deletes its output on failure (482-485).
- **Trigger:** Removable storage unmounted mid-copy, source disappears, or destination fills during
  the byte transfer.
- **Evidence / verification:** `copyFiles` has no cleanup path; the test `FileRepositoryTest:594`
  checks the exception wrapping but not partial-file cleanup. Environmental trigger, hence Low.
- **Suggested fix:** In the catch, delete the partially-written `targetFile` before rethrowing.

### [b/contract-mismatches/copy-move/symlink-silently-dropped-or-deleted] Copy omits symlinks (silent success); move deletes them (data loss)

- **Location:** `data/repository/FileRepository.kt:283-286`.
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `copyRecursive` returns immediately for a symlink: on COPY it creates nothing yet the
  operation completes as success (a symlink "copy" silently produces nothing), and on MOVE it does
  `source.delete()`, destroying the link with no recreation (data loss of the link).
  `totalFileCount`/`totalSize` also exclude symlinks, so the omission is invisible in progress
  totals.
- **Trigger:** Copy or move a symlink (or a folder containing one).
- **Evidence / verification:** Confirmed the early-return branch; symlink skipping avoids following
  links (reasonable) but reporting success while omitting/destroying data is a gap. Niche on Android
  user storage (FAT/exFAT/FUSE rarely expose user symlinks), hence Low.
- **Suggested fix:** Either recreate the symlink at the destination, or surface that links were
  skipped rather than reporting unqualified success; do not `delete()` the link on move without
  recreating it.

### [a/logic-errors/delete-progress/directory-nodes-counted-in-numerator-only] Delete progress counts directories in deleted/failed but not in the total

- **Location:** `data/repository/FileRepository.kt:221` (`totalFiles` via leaf-only
  `totalFileCount`) vs `:243-247` (increment per node, incl. directories); display
  `ui/components/DeleteProgressDialog.kt:45`; partial-success toast
  `ui/screens/folder/FolderViewModel.kt:609-613`.
- **Severity:** Low
- **Confidence:** High
- **Defect:** `totalFiles` counts only leaf files (directories/symlinks contribute 0), but
  `deletedFiles`/`failedFiles` increment for every `delete()` including directories. So
  `deletedFiles` can exceed `totalFiles` (progress fraction > 1, clamped by Material3), and the
  partial-success toast reports inflated counts (e.g. a 2-file folder with one undeletable file
  reports "1 deleted, 2 failed" because the non-emptied directory is also counted as a failed file).
- **Trigger:** Delete (via the ≥10-node progress path) any tree containing directories, especially
  with a partial failure.
- **Evidence / verification:** `totalFileCount` returns 0 for directories (649-652) while the
  increment runs per node; the dialog's `> 0` guard prevents NaN, so the bar is cosmetically wrong
  but the toast counts are genuinely incorrect.
- **Suggested fix:** Either count directories in `totalFiles` too, or increment `deletedFiles`/
  `failedFiles` only for non-directory nodes, so numerator and denominator agree.

### [a/concurrency/destination-picker/main-thread-canwrite-io] Destination picker does disk I/O on the main thread on every navigation

- **Location:** `ui/screens/picker/PickerViewModel.kt:106-110`/`:153-191` (`validateDestination` →
  `File(...).canWrite()` at 159, not wrapped in `withContext`).
- **Severity:** Low
- **Confidence:** High
- **Defect:** `navigateToPath` calls `validateDestination` synchronously on the Main thread,
  performing `canWrite()` (an `access()` syscall) and `File(...).parent` on every folder/storage tap
  and breadcrumb navigation. `loadFolders` correctly does its `canWrite()` checks on
  `Dispatchers.IO`, so this is an inconsistency that can jank/ANR on slow or sleeping removable
  volumes and violates the project rule "never block the main thread."
- **Trigger:** Any navigation within the destination picker.
- **Evidence / verification:** `validateDestination` is invoked inline from Main-thread click
  callbacks with no dispatcher hop.
- **Suggested fix:** Move the `canWrite()`/path checks into `withContext(Dispatchers.IO)` as
  `loadFolders` does.

### [a/error-handling/destination-picker/silent-create-folder-failure] Picker "create folder" failure closes the dialog with no feedback

- **Location:** `ui/screens/picker/PickerViewModel.kt:203-216` (no else on `success`); collision
  source `:218` (`getExistingNames` returns folders only);
  `data/repository/FileRepository.kt:84-105`.
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `createFolder` dismisses the dialog and only navigates/tracks on success; on failure
  it does nothing — no toast. Because the dialog's `existingNames` comes from `getExistingNames()` (
  directories only), a name colliding with an existing **file**, an over-long path, or an OS-denied
  write makes `FileRepository.createFolder` return `false`, so the dialog closes as if it worked
  while no folder is created.
- **Trigger:** In the picker, create a folder whose name passes the dialog but fails `mkdir()` (file
  of that name exists, path too long, scoped/removable write denied).
- **Evidence / verification:** The dialog validates only invalid chars and folder-name collisions;
  `createFolder` returns `false` (not throwing) for the above; the success-only branch leaves no
  feedback path.
- **Suggested fix:** Show an error toast on the `false` branch, mirroring the folder screen's
  `onCreateFolder`.

### [a/null-and-numeric-hazards/exif-metadata/focal-length-zero-denominator] EXIF focal length with zero denominator renders as "Infinity"/"NaN"

- **Location:** `data/util/ImageMetadataExtractor.kt:93-105` (`parseFocalLength`); display in
  `ui/screens/iteminfo/ItemInfoScreen.kt`.
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `parts[0].toDouble() / parts[1].toDouble()` does floating-point division, so a
  rational like `"5/0"` yields `+Infinity` and `"0/0"` yields `NaN` (no exception, `runCatching`
  doesn't trip). The value is stored and formatted as `"%.1f mm"` → "Infinity mm"/"NaN mm". The
  manual split also bypasses `ExifInterface.getAttributeDouble`, which would have returned the
  default for a zero denominator. (Related: `formatExposureTime` at 114 can render absurd `1/<huge>`
  strings for tiny exposures.)
- **Trigger:** Open Info on an image whose EXIF `FocalLength` rational has denominator 0.
- **Evidence / verification:** Division is unguarded against a zero denominator and the result is
  not `isFinite`-checked before formatting. Depends on ExifInterface surfacing the raw `"n/0"`
  string, hence Medium.
- **Suggested fix:** Guard the denominator (return null when 0) and/or validate `isFinite()` before
  storing; prefer `getAttributeDouble`.

### [a/boundary-and-encoding-cases/csv-metadata/quoted-newline-miscount] CSV row/column counts are wrong for RFC-4180 quoted embedded newlines

- **Location:** `data/util/CsvMetadataExtractor.kt:18-25` (per-physical-line counting), `:50-62` (
  per-line quote state).
- **Severity:** Low
- **Confidence:** High
- **Defect:** `forEachLine` iterates **physical** lines, but a quoted field may span multiple
  physical lines as one logical record. Each non-blank continuation line increments `rowCount` (
  over-counting records), and if the first record begins with a quoted multi-line field, the first
  physical line has an unterminated quote so `detectSeparator` finds zero separators, falls back to
  `,`, and `countColumns` returns 1 (under-counting columns). The reported CSV metadata is simply
  wrong; the quote-state machine is per-line and never carried across iterations.
- **Trigger:** Open Info on a valid CSV containing a quoted field with an embedded newline.
- **Evidence / verification:** Quote state (`inQuotes`) is local to `countOccurrences` per line;
  confirmed it streams (no OOM) and doubled-quote escaping is correct within a line — only
  cross-line record boundaries are mishandled.
- **Suggested fix:** Track quote state across lines (a streaming record parser) so logical records
  and the header column count are computed correctly.

### [b/resource-and-configuration-parity/mime-type/svgz-misclassified-as-svg]

`.svgz` is treated as a directly-decodable SVG

- **Location:** `data/util/MimeTypeUtil.kt:135-138` (`isSvgByExtension` includes `svgz`), `:89` (
  `VIEWABLE_IMAGE_EXTENSIONS`); `data/model/FileItem.kt:37,45`.
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `.svgz` (gzip-compressed SVG) is classified as SVG, so it is routed to Coil's
  `SvgDecoder` (thumbnail and image viewer), which consumes raw SVG/XML and does not gunzip —
  decoding fails and the file errors out instead of falling through to a generic handler.
- **Trigger:** A `.svgz` file in a listing or opened in the viewer.
- **Evidence / verification:** `svgz` is claimed by the SVG predicates and
  `VIEWABLE_IMAGE_EXTENSIONS`, but no gunzip step exists; depends on `SvgDecoder` not handling gzip,
  hence Medium.
- **Suggested fix:** Either gunzip `.svgz` before handing it to the SVG decoder, or stop classifying
  `.svgz` as a directly-viewable SVG.

### [a/state-and-lifecycle/swipe-row/offset-not-reset-on-selection] Swiped-open row stays revealed and active after "Select All"

- **Location:** `ui/components/SwipeableFileListItem.kt:64,77-95,119-121,170,207`; triggered via
  `ui/screens/folder/FolderViewModel.kt:258-262` (`selectAll`).
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** Nothing resets the `offsetX` `Animatable` when `isSelected` becomes true. After
  swiping a row open (offset ≈ ±80 px) and then invoking "Select All", the row renders selected *
  *and** translated; `pointerInput(isSelected)` now returns early so it can't be swiped back (only a
  tap on the row body resets it), and the revealed Delete/Rename action button — gated only on the
  sign of `offsetX`, not `isSelected` — stays composed and clickable, so tapping the exposed strip
  fires a single-item delete/rename in the middle of a multi-selection.
- **Trigger:** Swipe a row open (not in selection mode), then choose "Select All".
- **Evidence / verification:** No `LaunchedEffect(isSelected)`/reset path exists; `LazyColumn` keys
  by path so the `Animatable` survives the recompose; the destructive delete is still gated by the
  confirmation dialog, hence Low.
- **Suggested fix:** Add `LaunchedEffect(isSelected) { if (isSelected) offsetX.animateTo(0f) }` and
  gate the action buttons on `!isSelected`.

### [a/state-and-lifecycle/input-dialogs/non-saveable-text-state] In-progress dialog text is lost on uiMode/locale/font-scale config changes

- **Location:** `ui/components/CreateFolderDialog.kt:40` and `ui/components/RenameDialog.kt:51-58` (
  `remember { mutableStateOf(...) }`).
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** Both dialogs hold editable text in `remember` rather than `rememberSaveable`. The
  dialog's visibility survives recreation (driven by ViewModel state), so on an Activity recreation
  the dialog reopens but the field re-seeds to its initial value, discarding in-progress input.
- **Trigger:** Begin typing a name, then trigger a config change the host doesn't handle (system
  dark/light toggle `uiMode`, locale, or font-scale). Rotation is *not* affected (the activities
  declare `configChanges` for it).
- **Evidence / verification:** No `rememberSaveable` in `ui/components`; rotation is guarded but
  `uiMode`/`locale`/`fontScale` are not in `configChanges`. Recoverable by retyping, hence Low.
- **Suggested fix:** Use `rememberSaveable` for the dialog text state.

### [b/security-defects/file-provider/over-broad-shared-paths] FileProvider exposes the entire filesystem via configured paths

- **Location:** `app/src/main/res/xml/provider_paths.xml:3-8` (`external-path "."` and
  `root-path "/"`); `AndroidManifest.xml:109-117`; `util/IntentUtil.kt:219-229`.
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `root-path "/"` (plus `external-path "."`) lets `FileProvider.getUriForFile` mint a
  content URI for any path the app process can read. This converts any future/elsewhere
  path-injection bug into a full-filesystem read primitive for a grantee app.
- **Trigger:** Any path forwarded into `getUriForFile` (share / open-with / APK install).
- **Evidence / verification:** Mitigated today: the provider is `exported="false"` and access
  requires explicit per-URI `FLAG_GRANT_READ_URI_PERMISSION` grants the app issues, so there is no
  direct external query path — this is over-exposure/hardening, not an active exploit.
  `root-path "/"` also does real work (sharing from SD/OTG mounts that `external-path` doesn't
  cover), so a fix must preserve that.
- **Suggested fix:** Narrow the provider paths to the storage roots the app actually shares from (
  enumerated mount points) instead of `/`, accepting the maintenance of keeping them in sync.

### [c/api-or-library-misuse/distribution-script/debug-build-and-unguarded-jq] Tester distribution ships a debug build and uses an unguarded app-id lookup

- **Location:** `scripts/distribute.sh:10,13-17`.
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** The script builds `assembleDebug` and uploads `app-debug.apk` to the Firebase "
  Testers" group, so testers exercise a debuggable, non-minified artifact whose behavior diverges
  from the shipped R8/`isShrinkResources` release — and nothing else in the repo validates the
  minified release path before production. Separately,
  `APP_ID=$(jq -r '...' app/google-services.json)` is unguarded: a missing field makes `jq` print
  `null` and exit 0, so `firebase … --app null` runs (the script uses `set -e` but not `set -u`/
  `pipefail`).
- **Trigger:** Running tester distribution; or `google-services.json` missing the expected field.
- **Evidence / verification:** Distributing a debug build may be intentional for internal testing (
  hence Low), but the minified release path is never validated; `google-services.json` is
  present/valid today, so the `jq` issue is latent.
- **Suggested fix:** Distribute a (test-signed) release-configured build, and guard the `jq`
  result (`[[ -n "$APP_ID" && "$APP_ID" != null ]]`); add `set -u -o pipefail`.

### [b/resource-and-configuration-parity/gradle-properties/missing-committed-template] No committed Gradle properties template; clean clones build without intended JVM args/encoding

- **Location:** `gradle.properties` (untracked) and `.gitignore` (ignores `gradle.properties`).
- **Severity:** Low
- **Confidence:** Low
- **Defect:** The repo ships no project Gradle properties and no `.example`/template. A clean
  clone (CI/new machine) builds with default Gradle daemon JVM args instead of the intended `-Xmx`/
  `-Dfile.encoding=UTF-8`, raising R8/`bundleRelease` OOM risk and dropping the explicit UTF-8
  encoding (locale-dependent resource/string handling on non-UTF-8 CI locales).
- **Trigger:** Building from a fresh checkout with no local `gradle.properties`.
- **Evidence / verification:** File confirmed untracked; contents are only `jvmargs` +
  `kotlin.code.style` (no `android.useAndroidX`, so no hard AndroidX failure). Impact is
  degraded-but-likely-builds, hence Low confidence.
- **Suggested fix:** Commit a `gradle.properties` with the required `jvmargs`/encoding (secrets stay
  in the separately-ignored `keystore.properties`), or a `gradle.properties.example` documented in
  the build steps.