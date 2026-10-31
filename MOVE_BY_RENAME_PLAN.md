# Move by rename — implementation plan

Status: **draft, awaiting approval** (2026-10-31). Nothing below is implemented.

## 1. Problem

User feedback: *"A move within one drive copies every byte, and fails if free space is below the
selection size, though a rename needs none. Add a rename path now."*

Confirmed in the code:

- `FolderViewModel.executeOperation` (`FolderViewModel.kt:610`) sums the size of the whole
  selection, reads `StatFs(targetPath).availableBytes`, and refuses the move with
  `error_not_enough_space` when free space is smaller (`FolderViewModel.kt:640`) — whatever the
  mode, whatever the drive.
- A move is `FileRepository.copyFiles(deleteAfter = true)` (`FileRepository.kt:957`): every file is
  byte-copied, then its source is deleted (`FileRepository.kt:1155`).

Consequences on a same-drive move:

- A user with 3 GB free cannot move a 10 GB folder from `Download` to `Movies`.
- A large move takes minutes, wears flash, and drains battery.
- A cancel or failure mid-move leaves the selection split across both folders.
- The pre-flight refusal reports no analytics event, so there is no data on how often users hit it.

## 2. What other file managers do

Read from their source code on 2026-10-31.

| # | App | Renames first? | Falls back to copy when | Space check on a move | Name clash |
|---|---|---|---|---|---|
| 1 | AOSP DocumentsUI (system Files app) | Yes, when same provider and the item supports move | Any failure of the provider move | Only items from another provider (internal → SD card is never checked) | Copies to "name (1)" |
| 2 | Material Files | Yes, per item, no up-front check | Any `IOException`; retries rename at every tree level | None found (text search only) | Asks: merge / replace / rename / skip |
| 3 | Amaze | Only if same storage type | First failed rename sends the whole rest to copy | Skipped for renames; after a failure counts the whole selection | Asks: rename / skip / overwrite |
| 4 | Fossify | Decided up front by path; internal storage only; first item not a folder | Never — a failed rename is reported as "partially moved" | None on the rename path | Asks: skip / overwrite / merge / keep both |

Android's own `java.nio.file.Files.move` (libcore `UnixCopyFile.move`) also tries `rename(2)`
first and copies only on `EXDEV`/`EISDIR`, but cannot move a non-empty directory across drives.

This plan follows the Material Files / DocumentsUI model (try rename per item, copy what fails,
space-check only what is copied) while keeping this app's automatic `(1)` naming and merge
behaviour instead of adding a conflict dialog.

## 3. Open decisions

| # | Decision | Recommendation |
|---|---|---|
| D1 | Where the rename lives: a **separate step before the untouched copy code** (A), or folded into `copyFiles` (B) | **A.** B touches the riskiest function in the app, forces the space check into the flow, and silently turns every existing JVM move test into a rename test (temporary folders share one filesystem, so every rename would succeed) |
| D2 | Links: a rename moves a link as a link; today's move drops links (`FileRepository.kt:1011`) | **Accept**, and update `EdgeCasesTest.move_symlinkedDirectory_leavesTheLinkTargetIntact` to assert the moved link is still a link and its target is untouched. That keeps the test's intent (nothing behind a link is copied or deleted) |
| D3 | Favorites and recents: today a moved item drops out of both; the in-folder rename remaps them (`FolderViewModel.kt:918`) | **Keep today's behaviour** so same-drive and cross-drive moves match. Remapping both can come later |
| D4 | Message for the rare partial move (case 18 below) | **Reload + the existing `error_not_enough_space` toast**, no new string |
| D5 | Release | **Not in 2.8.0.** Ship as its own change in a later release |

## 4. Design principles

1. **A rename either completes or leaves the item untouched.** Any failure sends the item to
   today's copy path, so the worst case is today's behaviour.
2. **`copyFiles` is not modified.** Every existing copy/move test keeps testing the copy path.
3. **Copy mode is not modified.**
4. **Same bookkeeping as today's move**: the same MediaStore lists, thumbnail drops, and
   storage-size cache invalidation, produced for renamed items.
5. **No UI change, no new strings, no new analytics events.**

## 5. Repository changes (`FileRepository.kt`)

### 5.1 Rename seam

Add a constructor parameter, declared before `onFilesMutated` for the reason `removeFile` gives
(`FileRepository.kt:95-107`):

```kotlin
private val renamePath: (from: File, to: File) -> Unit = { from, to -> Os.rename(from.path, to.path) },
```

- `Os.rename` is a plain `rename(2)`: it never copies, and it throws `ErrnoException` on failure.
- JVM tests pass a java.io stand-in (`if (!from.renameTo(to)) throw IOException()`) and can force
  any failure. `android.system.Os` is a throwing stub on the JVM, as with `removeFile`.
- **Do not reuse `renameRegular`** (`FileRepository.kt:429`): its fallback is `Files.move` without
  `ATOMIC_MOVE`, which silently byte-copies a file across drives with none of the move's progress,
  space, or MediaStore handling.
- **Do not use `Files.move(ATOMIC_MOVE)`** either: it silently replaces an existing target and
  needs API 26 (minSdk is 24).

### 5.2 Pre-check (no side effects)

```kotlin
suspend fun findMoveCollisions(sources: List<FileItem>, targetDir: String): List<FileItem>
```

Returns the items whose name is already taken in `targetDir`, where "taken" is
`target.exists() || target.isSymlink()` — a broken link answers `false` to `exists()` but would
be replaced by `rename(2)`. On a case-insensitive volume `exists()` already answers for a name that
differs only in case.

### 5.3 Rename step

```kotlin
suspend fun moveByRename(
    sources: List<FileItem>,
    targetDir: String,
    allowedRoots: List<String>,
    onRenamedPaths: suspend (created: List<String>, deleted: List<String>) -> Unit
): RenameMoveResult   // renamed: List<FileItem>, remaining: List<FileItem>, movedFiles: Int
```

Runs on `Dispatchers.IO`. Before the loop:

- `isWithinAllowedRoots(targetFolder, allowedRoots)` or throw `SecurityException` — the same guard
  and exception `copyFiles` uses (`FileRepository.kt:970`), so the ViewModel's existing catch
  applies.

Per item, in selection order:

1. `currentCoroutineContext().ensureActive()`.
2. `target = File(targetFolder, source.name)`.
3. To `remaining`, untouched, if any of these holds:
   - the source's parent is the target folder (canonical paths);
   - the target folder is the source or inside it (canonical paths);
   - the name is taken (same test as 5.2, repeated here because the state may have changed).
4. `renamePath(source, target)`:
   - `CancellationException` → rethrow (never treated as a rename failure).
   - Any other exception → to `remaining`, untouched.
5. On success → to `renamed`, then run the bookkeeping walk (5.4) for this item.

`finally`: `notifyFilesMutated()` (clears the home screen's storage-size cache, as `copyFiles`
does at `FileRepository.kt:1274`).

### 5.4 Bookkeeping walk for a renamed item

Wrapped in `withContext(NonCancellable)` so a cancel cannot leave MediaStore or the thumbnail cache
stale for an item that has already moved.

Walk the item at its **new** path with `forEachChild`, not following links (`isSymlink()` → skip,
as `totalFileCount` does). For each regular file:

- `created += newPath`
- `deleted += oldPath` (the new path with the target prefix swapped back for the source prefix)
- evict the thumbnail cached under the **old** path: the key is `type:path:mtime`
  (`ThumbnailDiskCache.kt:182`) and a rename does not change mtime. Needs a small overload:
  `thumbnailDiskCacheKeyFor(file: File, path: String = file.absolutePath)`.
- `movedFiles++`

Parity with today's move, verified against `FileRepositoryTest.kt:2230`: both lists name **files
only**, never directories, and links are not reported.

Batching: hand `created`/`deleted` to `onRenamedPaths` every `MEDIA_PATH_BATCH_SIZE` (500) files
and start new lists, as `copyFiles` does — one path per file is unbounded and has run small-heap
devices out of memory. Flush the remainder at the end of each item.

A selected item that is itself a file is "walked" as a single file.

## 6. ViewModel changes (`FolderViewModel.kt`)

COPY: unchanged, byte for byte.

MOVE, inside `executeOperation` / `executeOperationInternal`:

1. Destination check first, as today: `StatFs(targetPath)` throwing → `error_invalid_target_path`,
   `ReviewPrompt.onActionFailed()`.
2. `collisions = findMoveCollisions(items, targetPath)`. If
   `available < totalSize(collisions)` → today's refusal (`error_not_enough_space`,
   `ReviewPrompt.onActionFailed()`). Nothing has moved.
3. Run `moveByRename` **inside `executeOperationInternal`'s existing try/catch**, so every existing
   catch branch (cancel, `SecurityException`, generic) applies unchanged. Its callback calls
   `MediaStoreUtil.scanFiles(created)` and `MediaStoreUtil.notifyDeleted(deleted)` — the calls
   copied items get today (`FolderViewModel.kt:705-713`).
4. `remaining` empty → success exactly as the copy path reports it:
   `trackDestinationPickerOperationFinished("move", true)`, `ReviewPrompt.onActionSucceeded()`,
   `loadFiles()`.
5. `remaining` holds anything beyond `collisions` (a rename failed unexpectedly) → read
   `StatFs` again and compare with `totalSize(remaining)`. Short → `error_not_enough_space`,
   `ReviewPrompt.onActionFailed()`, `loadFiles()`; renamed items stay moved (D4).
6. Otherwise → `copyFiles(remaining, deleteAfter = true)` through the existing code. The
   partial-success messages use `copiedFiles + renamed.movedFiles` as the transferred count.
7. Exactly one `trackDestinationPickerOperationFinished` per move.

Cancel: the progress dialog appears only once the copy starts, as today. A cancel that arrives
during the rename step (the screen being left) is a `CancellationException`, handled by the
existing branch: dialog cleared, `loadFiles()`, no success reported.

## 7. Cases

| # | Case | Result |
|---|---|---|
| 1 | Same drive, name free | Instant rename, no space needed |
| 2 | Internal storage ↔ SD card or USB | `EXDEV` → copied as today |
| 3 | File name taken | Copied as today → `name (1)` |
| 4 | Folder name taken | Copied as today → merged |
| 5 | Name taken in a different letter case, case-insensitive volume | Counts as taken → copied as today |
| 6 | Name taken by a broken link | Counts as taken → copied as today |
| 7 | Another app creates the same name between check and rename | A file there is replaced; folder cases fail safely (`ENOTEMPTY`/`EISDIR`/`ENOTDIR`). Same microsecond window as the in-folder rename today |
| 8 | Destination is the item's own folder or inside it | Not renamed; the picker already blocks it (`PickerViewModel.kt:221-251`) |
| 9 | Source deleted by another app meanwhile | Rename fails → copy path reports it absent, as today |
| 10 | Protected folders (top-level `DCIM`, `Android/` on 11+) | Android refuses → copied as today |
| 11 | Folder holding files the app cannot read | Whole folder moves (today those stay behind as "skipped") |
| 12 | Link inside a folder, or a selected link | Moves as a link (D2) |
| 13 | Read-only drive, or SD card on Android 7–9 | Rename refused → copy path, same errors as today |
| 14 | Mixed selection | Decided per item; renames first, then the copy |
| 15 | Cancel during the rename step | Finished renames stay, with their bookkeeping; the rest untouched |
| 16 | Cancel or failure during the copy | As today; renamed items stay moved |
| 17 | Name-clash items do not fit | Refused before anything moves |
| 18 | Unexpected rename failure and short space | Partial: renamed items stay, the rest refused (D4) |
| 19 | Gallery on Android 7–10 | Explicit scan of new paths and removal of old ones |
| 20 | Gallery on Android 11+ | Same explicit calls; harmless if the OS already updated its rows |
| 21 | Favorites and recents | Drop out as today (D3) |
| 22 | Thumbnail cache | Old entries dropped, as today |
| 23 | Home storage-size cache | Cleared, as today |
| 24 | App killed mid-step | Each rename is atomic; the gallery may be stale until the next scan, as with today's copy |
| 25 | Path too long, target is a file, mount point inside the tree | Rename fails → copy path, same errors as today |
| 26 | Copy mode | Unchanged |

## 8. Tests

### 8.1 Repository unit tests (`app/src/test/.../data/repository/FileRepositoryTest.kt`)

JVM, real temporary folders, `renamePath` stand-in that can be forced to fail.

Renamed:

1. Single file moves; same bytes, same modified date.
2. Empty folder moves.
3. Nested tree (3 levels, files and empty subfolders) arrives with an identical structure.
4. Hidden files and `.nomedia` inside a folder move with it.
5. Several items all move, in selection order.
6. Names with spaces, accents, emoji, and several dots move.

Left untouched, sent to the copy list:

7. File name taken by a file.
8. Folder name taken by a folder.
9. File name taken by a folder, and the reverse.
10. Broken link at the name.
11. Hidden file at the name.
12. Destination is the item's own folder.
13. Destination is inside the item.
14. Rename refused as "other drive".
15. Rename refused for permission.
16. Source deleted before the step.

Mixed and refused:

17. A selection mixing renamed, taken, and failing items splits correctly, keeps order, and leaves
    the copy list untouched.
18. Destination outside the storage roots, or reached through `../`, is refused with
    `SecurityException` before anything moves.
19. Destination that is a file is refused / nothing renamed.

Reporting:

20. **Parity test**: for an identical twin tree, the created and deleted paths reported by the
    rename step equal those `copyFiles(deleteAfter = true)` reports (as sets).
21. Both lists name files only; no folders, no links.
22. `movedFiles` excludes folders and links.
23. 1,201 files arrive in batches of 500 / 500 / 201, nothing lost or duplicated.
24. The walk does not follow links; nothing behind a link is reported.

Side effects:

25. Cached thumbnails of moved images and videos are dropped; unrelated entries stay (mirror of
    `FileRepositoryTest.kt:366`).
26. Storage-size cache notified after success, failure, and cancel.
27. A failed rename drops no thumbnail and reports no paths.

Cancel:

28. Cancel between items: items already moved are reported; the rest are untouched.
29. Cancel during the walk: the walk finishes and reports.
30. Cancel is never treated as a rename failure (item not sent to the copy list; exception
    propagates).

Regression:

31. Every existing `copyFiles` test passes unchanged.
32. `findMoveCollisions` returns exactly the taken names and touches nothing.

### 8.2 ViewModel unit tests (`app/src/test/.../ui/screens/folder/FolderViewModelTest.kt`)

`fileRepository` is a strict mock (`FolderViewModelTest.kt:139`): every existing move test must
stub `findMoveCollisions` → empty and `moveByRename` → "renamed nothing, all remaining", so it keeps
testing the copy path.

Everything renamed:

1. No `copyFiles` call; success analytics once; `ReviewPrompt.onActionSucceeded()` once;
   `loadFiles()`.
2. Succeeds even when free space is below the selection size (the reported bug).
3. Every batch reaches MediaStore: `scanFiles(created)`, `notifyDeleted(deleted)`.

Part renamed:

4. `copyFiles` receives only the remaining items; progress reflects only them.
5. Copy with skipped files: the partial message counts the renamed files too.
6. Copy whose originals could not be deleted: today's message.
7. Copy throwing `InsufficientStorageException`, `DestinationNotWritableException`,
   `FileTransferIOException`, or another exception: same toast and analytics as today.

Refusals:

8. Name-clash items that do not fit: refused; `moveByRename` never called.
9. Unexpected rename failure with short space: toast, `ReviewPrompt.onActionFailed()`, reload;
   renamed items' MediaStore updates still sent.
10. Cross-drive (nothing renamed) with short space: refused exactly as today.
11. Destination gone: `moveByRename` never called.
12. `SecurityException` from the rename step: same toast, analytics, and error report as today.

Unchanged paths:

13. COPY never calls `findMoveCollisions` or `moveByRename`.
14. Cancel during the rename step: dialog cleared, reload, no success reported.
15. All existing move tests pass with the stubs above.
16. Exactly one `trackDestinationPickerOperationFinished` per move.

### 8.3 Instrumentation tests (`Pixel_7_API_36`)

Shared storage with All Files Access granted through the app op, as
`MediaStoreUtilProviderTest.grantAllFilesAccess` does. The running emulator was checked
(read-only) to have a mounted public SD volume (`A92F-12FB`), so the cross-drive tests run there.
Their skip condition must read platform state (a mounted removable volume from `StorageManager`),
never a production function — `scripts/check-tests.sh` bans production symbols inside `assume*`.

Same drive:

1. File move: the rename step reports it renamed; content and date intact.
2. Folder tree move: structure intact; inode unchanged (if the emulator preserves it — see R3).
3. 2,000-file tree moves; every file reported.
4. Move within the SD card renames.

Across drives:

5. Internal → SD card: rename refused, falls back to copy, result identical to today.
6. SD card → internal: the same.

Name taken:

7. File → `name (1)`, folder → merged, exactly as today, on the same drive and across drives.

Gallery:

8. A moved image has a MediaStore row at the new path and none at the old one — for a rename and
   for a copy fallback.
9. A moved folder of images: every child at the new path, none at the old.

UI flow:

10. Select an item → Move → pick folder → confirm: the item appears at the destination and no
    dialog remains (same drive). Uses `R.string` lookups, never literal text.
11. The same flow across drives.

Links:

12. `EdgeCasesTest.move_symlinkedDirectory_leavesTheLinkTargetIntact` updated per D2.

Existing tests that must stay green: `FileOperationsEndToEndTest`, `FileOperationIntegrationTest`,
`EdgeCasesTest`, `MediaStoreUtilProviderTest`, `PickerBottomBarTest`, `OperationProgressDialogTest`.

## 9. Files to change

- `app/src/main/java/.../data/repository/FileRepository.kt` — seam, `findMoveCollisions`,
  `moveByRename`, `RenameMoveResult`.
- `app/src/main/java/.../data/util/ThumbnailDiskCache.kt` — key overload with explicit path.
- `app/src/main/java/.../ui/screens/folder/FolderViewModel.kt` — MOVE flow.
- `app/src/test/.../data/repository/FileRepositoryTest.kt` — 8.1.
- `app/src/test/.../ui/screens/folder/FolderViewModelTest.kt` — 8.2 and stubs in existing move
  tests.
- `app/src/androidTest/...` — 8.3 (a new class for the rename tests, plus `EdgeCasesTest`).

## 10. Verification

```bash
./gradlew -w --console=plain -I gradle/agent-quiet.init.gradle testDebugUnitTest
./gradlew -w --console=plain lintDebug
./scripts/check-tests.sh
```

Then the new and changed instrumentation classes through the `instrumentation-runner` subagent,
only with the emulator up, one Gradle build at a time. Then `delta-review` on the changed files.

## 11. Risks and unverified assumptions

| # | Item | Mitigation |
|---|---|---|
| R1 | Microsecond window where another app creates the same name between check and rename (case 7) | Accepted; same window as the in-folder rename. `renameat2(RENAME_NOREPLACE)` is not reachable from Java |
| R2 | Unverified that Android 11+ updates MediaStore itself on a FUSE rename | The plan does not rely on it: explicit scan/remove on every version |
| R3 | Unverified that the emulator keeps a file's inode across a rename on shared storage | Tests prove the rename from the step's own result; the inode is only an extra assertion |
| R4 | `isSymlink()` on the JVM uses a canonical-path fallback (SDK_INT is 0); unverified that it detects a broken link | Check while writing test 8.1-10; adjust the "taken" test if not |
| R5 | Cross-drive device tests depend on the emulator's SD card staying mounted | Skip condition reads the mounted volume; it holds today |
| R6 | A huge renamed tree is walked for bookkeeping with no progress dialog | Same as today's silent pre-walk for the size check; far cheaper than copying |
| R7 | On Android 11+, a FUSE rename of a huge directory may take time while MediaStore updates its rows (magnitude unverified) | Still far faster than copying; measured on the 2,000-file device test |

## 12. Out of scope (possible follow-ups)

- Remap favorites and recents for moves (both rename and copy paths).
- Analytics event for the pre-flight space refusal (no data on it today).
- Analytics parameter for rename vs copy, to measure the fast path in the field.
- Per-level rename inside a merge (Material Files style).
