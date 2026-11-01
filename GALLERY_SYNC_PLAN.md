# Gallery sync redesign — plan

## 1. Problem

Every file operation updates Android's media index (MediaStore, which gallery, music and file apps
read) itself, inline, before it reports done:

- Copy and move scan every created path and remove the row of every source path, one provider call
  per file, in batches of 500.
- Delete removes the rows of the deleted roots; extract scans what it wrote; rename scans the new
  path and removes the old one.
- The calls are spread over seven screens and the extract handler: `FolderViewModel`,
  `HomeViewModel`, `SearchViewModel`, `AnalyzerCategoryViewModel`, `ImageViewerViewModel`,
  `PdfViewerViewModel`, `TextViewerViewModel` and `UncompressHandler`, all through
  `MediaStoreUtil`.

Consequences:

- The user waits for bookkeeping, not for their files. Measured on the `Pixel_7_API_36` emulator: a
  2,000-file folder moved by rename in 14.5 s, almost all of it MediaStore updates. The files were
  in place after the first few milliseconds.
- On Android 11+ the work is redundant: the system keeps the index right by itself (section 3).
- Each screen carries its own copy of the gallery logic, and its own tests of it.

## 2. Decisions

| # | Decision |
|---|---|
| G1 | **Every operation does only its file work** — copy, move, rename, delete, compress, extract, and the viewers' delete. No operation calls MediaStore inline |
| G2 | **Progress dialog after a delay, for every operation.** Any operation still running after about half a second shows its progress dialog; one that finishes sooner never shows it. Replaces the per-operation rules (2.8.0 shows the move dialog only for more than one item) |
| G3 | **Android 11+ (API 30+): never sync.** The system's media service updates the index for every file operation the app makes through a file path (section 3). Accepted: the cases where it does not (section 7) |
| G4 | **Android 7–10 (API 24–29): sync in the background**, once the operation ends, whether it succeeded, failed or was cancelled |
| G5 | **App-wide queue, saved to disk.** The 7–10 sync runs from one queue for the whole app, processed in order, independent of any screen. It is saved so a sync interrupted by the app being killed resumes on the next launch |
| G6 | **Queue only the top-level paths** each operation touched (section 4), never one entry per file |
| G7 | **Rescan, never remove.** The sync never removes an index entry directly (section 4) |
| G8 | **One place.** The sync sits behind one data-source interface the repository layer calls after every operation; screens stop calling `MediaStoreUtil` |
| G9 | **Manual check on Android 10.** The 7–10 path cannot run on the project's emulator (API 36); it gets one manual run on an API 29 emulator before release |
| G10 | **Unchanged:** the thumbnail-cache eviction and the home screen's storage-size invalidation stay inline — they are the app's own caches and cost almost nothing |

## 3. Evidence for G3

Probe on the API 36 emulator (2026-11-01), run twice: once as the device shell, once inside the app
through `FileRepository` with every gallery update removed, then reading the index after each step.

| # | Operation | What the index held, with no update from the app |
|---|---|---|
| 1 | Create files and folders | Rows at once, photos included, without any scan |
| 2 | Rename a file | Row moved to the new path |
| 3 | Rename a folder | Every file and subfolder row moved with it |
| 4 | Copy a file | New row at the destination |
| 5 | Move by copy, then delete | Old row gone, new one there |
| 6 | Delete a file | Row gone |
| 7 | Delete a folder | File rows gone; the folder's own rows (and its subfolders') stay |
| 8 | Rename on the SD card (shell run only) | Row moved |

Two external reviews of the AOSP MediaProvider source (Android 11–17) agree with rows 2, 3, 6, 7 and
8: renames and deletes go through MediaProvider's FUSE handlers on every version since Android 11,
and `rmdir` leaves the folder's row. They **disagree** on rows 1, 4 and 5 — whether a file created
through a file path is immediately visible to *other* apps' galleries with its details (one says it
is scanned when closed; the other says it stays "pending" until a scan). The probe cannot tell:
both runs read the index as the app that created the files, which sees its own pending rows.

**Accepted as working** (owner's decision, 2026-11-01). The check that would settle it: read
`is_pending`, `width` and `height` of a copied image as another app sees them, on API 30 and 36.

Not relevant here: the app never rewrites a file in place, and does not declare
`requestRawExternalStorageAccess` (the one flag that bypasses the index on 12+).

## 4. Design

### 4.1 What an operation reports

After it ends, each operation hands the sync one change: the top-level paths it created and the
top-level paths it took away. Nothing per file.

| Operation | Created roots | Removed roots |
|---|---|---|
| Copy | Each item's destination path | — |
| Move (rename or copy) | Each item's destination path | Each item's source path |
| Rename in place | New path | Old path |
| Delete | — | Each deleted root |
| Compress | The archive | — |
| Extract | Each extracted top-level entry | Rolled-back roots, if it failed |

A partial or cancelled operation reports the same roots: the sync looks at the disk, so whatever
did or did not happen is what it finds (4.3).

### 4.2 The interface

```kotlin
interface MediaIndexSync {
    /** Called once per operation, after it ends however it ends. Never throws, never blocks. */
    fun afterOperation(created: List<String>, removed: List<String>)
}
```

- Lives with the app's other data sources (`data/source/`), with one Android implementation, so
  repositories and tests can substitute it, as the project's other sources are.
- The Android implementation decides by SDK level, passed in (the `PdfViewerSupport.mode(sdkInt, …)`
  pattern) so both branches run as JVM unit tests: 30+ returns at once; 24–29 queues.
- Called by the repository layer — `FileRepository` and the extract path — not by screens.

### 4.3 The 7–10 sync

One worker in an app-wide scope (the kind `FileExplorerApplication` already creates), taking queue
entries one at a time, in order.

For each entry, reading the disk **when it runs**, not when it was queued:

- **Created roots:** walk each one (links skipped, as every walk in the app does) and scan the files
  that exist, in batches of 500 paths per scanner call.
- **Removed roots:** query the index for rows at each root and under it (the prefix query
  `MediaStoreUtil` already uses for tree deletes, with its escaping of wildcard characters), keep
  only the rows whose file **no longer exists**, and scan those. A scan of a missing path drops its
  row.

Why rescans instead of removals (G7):

- Removing an index row can make the media provider delete the file behind it. By the time a
  queued sync runs, a new file may sit at an old path; a rescan sees it and indexes it instead.
- Android 10's provider silently refuses some removals; a scan is the recovery the app already uses.
- Order between operations stops mattering, and so do partial results: each sync reflects the disk
  as it is.

### 4.4 The saved queue

- Stored as a small string preference through `DataStoreSafeAccess`, like the app's other stored
  lists, hand-serialised and parsed so that a bad value degrades to an empty queue.
- An entry is removed only after its sync finished; the worker resumes the queue at app start.
- Bounded by construction: a few paths per operation.

### 4.5 The delayed dialog (G2)

- One shared rule for the folder screen's four progress states (`operationProgress`,
  `deleteProgress`, `compressProgress`, `uncompressProgress`), the viewers' deletes, and in-place
  rename: the operation starts with its dialog hidden; a timer started with it reveals the dialog
  if the operation is still running when it fires; finishing cancels the timer.
- During that half second the screen stays usable, so a second operation can start. Each
  operation must clear only its own dialog and job handle — the guard move/copy got in 2.8.0 (I6 in
  `MOVE_BY_RENAME_PLAN.md`) and delete and compress already had.

### 4.6 What gets removed

- Every `MediaStoreUtil.scanFile(s)`, `notifyDeleted` and `notifyTreeDeleted` call in screens.
- The path batching that exists only to feed those calls: `CopyProgress.createdPaths`,
  `deletedSourcePaths`, `absentSourcePaths`, `copyFiles`' `onPartialTransfer`, `moveByRename`'s
  `onRenamedPaths`, the extracted-path batches, and the matching ViewModel handling and tests.

## 5. Scope

- In: every operation listed in 4.1, every screen listed in section 1, the delayed dialog.
- Out: favorites/recents remapping on move (a separate follow-up in `MOVE_BY_RENAME_PLAN.md`).

## 6. Tests

Unit (JVM):

1. `MediaIndexSync` does nothing on SDK 30+ and queues on 24–29.
2. Created roots: existing files under them are scanned, links skipped, batches of 500.
3. Removed roots: only rows whose file is gone are scanned; a row whose path holds a file again is
   left alone; nothing is ever removed directly.
4. Paths with `%`, `_` and `\` in them query only their own rows.
5. The queue survives a restart (saved, parsed back, resumed) and a corrupt saved value reads as
   empty.
6. Entries run in order, one at a time; a failing entry does not block the next.
7. Each operation reports the roots in 4.1 — including failed, partial and cancelled ones — and
   makes no MediaStore call.
8. Each dialog stays hidden for an operation that ends within the delay and appears for one that
   does not; a finishing operation never clears a newer one's dialog.

Device (`Pixel_7_API_36`):

9. Premise test, kept permanently: the section 3 probe as a test — create, copy, rename a folder,
   move by copy, delete through `FileRepository` with no sync, and assert the index followed. A
   future Android change that breaks G3 then fails a test instead of leaving galleries silently
   wrong.
10. Existing operation flow tests stay green with the gallery calls gone.

Manual (G9): copy, move, rename, delete, extract on an API 29 emulator; check the gallery after the
background sync.

## 7. Accepted risks

| # | Risk | Why accepted |
|---|---|---|
| A1 | Rows 1, 4, 5 of section 3: new files may not reach other apps' galleries with full details until a later scan | Owner's decision; see the settling check in section 3 |
| A2 | A deleted folder's own row stays in the index on 11+ | Galleries list photos, videos and music, not folders |
| A3 | Devices without the updatable media module (Android Go, some ROMs without Play) may behave differently | Owner's decision |
| A4 | USB drives may not be indexed at all on some devices | Nothing to sync there on any version |
| A5 | On 7–10 the gallery lags until the background sync runs | The files are in place; the sync catches up |
| A6 | Verified on API 36 only | The premise test (test 9) runs on every instrumentation pass |

## 8. Open decisions

| # | Decision | Recommendation |
|---|---|---|
| O1 | Exact dialog delay | **500 ms** |
| O2 | A 7–10 sync whose volume is unmounted when it runs | **Keep the entry and retry at the next launch**; drop it after a few attempts |
