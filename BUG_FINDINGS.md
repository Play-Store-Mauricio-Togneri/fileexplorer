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
