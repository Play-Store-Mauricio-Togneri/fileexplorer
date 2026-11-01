### [a/state-and-lifecycle/folder-operations/leaving-before-progress-dialog-cancels-operation] Leaving the folder screen in the first half second silently stops a confirmed delete, move or copy part-way

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/DelayedProgress.kt:42-43`
    - Related: `ui/screens/folder/FolderViewModel.kt:623` (move/copy), `:1091` (delete);
      `ui/screens/folder/FolderScreen.kt:261`; `activities/FolderActivity.kt:185`, `:192`;
      `data/repository/FileRepository.kt:838`
- **Severity:** Medium
- **Confidence:** High
- **Likelihood:** Low. It needs a back press, the toolbar back arrow or an ancestor breadcrumb tap
  within 0.5 s of confirming, on an operation still running at that point.
- **Defect:**
    - The progress dialog now appears only after 500 ms. Until then nothing blocks back: the
      screen's own back handler is active only in selection mode, and confirming clears the
      selection.
    - Leaving pops the folder's navigation entry, which clears its ViewModel and cancels every
      operation running in that ViewModel's scope.
    - The delete, rename and copy walks check for cancellation item by item, so the work stops
      part-way.
    - The cancel message is sent to a screen that is already gone, so the user sees nothing.
- **Trigger:** Confirm a delete of a large folder, a move, or a copy, then press back within half a
  second.
- **Evidence / verification:**
    - Outcomes by operation:
        - **Delete:** what was already removed is gone and the rest stays.
        - **Move:** items already renamed or copied stay at the destination and the rest stays at
          the source.
        - **Copy:** leaves a partly filled folder at the destination with no warning; a user may
          take it as complete and delete the originals.
        - **Compress and extract:** roll back cleanly to "nothing happened", the same as the
          baseline's short pre-count window, so they are not part of this finding.
    - Library behavior was confirmed in navigation 2.10.2: a popped entry's ViewModel store is
      cleared when the transition ends, and these transitions are instant.
    - At the baseline:
        - small deletes ran a blocking walk that could not be cancelled;
        - large deletes, moves and copies showed the modal dialog from their first progress update,
          before the first file changed.
    - Refutation attempt: the analyzer category screen documents the same behavior as intended (its
      ViewModel KDoc and the test "leaving the listing mid-walk still drops what the walk removed"),
      so it is excluded here. Nothing documents it for the folder screen; the plan's "the screen
      stays usable" rule exists so that a second operation can start, not so that the screen can be
      left.
- **Suggested fix:**
    - While a folder operation's dialog is still pending, intercept system back, the toolbar back
      arrow and ancestor breadcrumb taps. Show the dialog at once instead of popping the screen; the
      press then lands on the modal dialog, as it would 500 ms later.
    - Navigating into a child folder can stay allowed.
    - Leave the analyzer screen as documented.

### [a/logic-errors/media-index-sync/android10-gone-file-scan-drops-prefixed-rows] On Android 10, a gallery update after renaming or deleting a file can remove other photos from the gallery, including the renamed one

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/AndroidMediaIndexSync.kt:158-161`
    - Related: `:185-197` (only gone folder rows are kept out of the scan, at `:192`), the comments
      at `:74-77` and `:180-183`
- **Severity:** Medium
- **Confidence:** Medium. The scanner behavior comes from recall of Android 10's
  `ModernMediaScanner` and matches the code's own comment at `:74-77`; no Android 10 device or
  source was available.
- **Likelihood:** Low. It is limited to Android 10 and needs a file name that is the start of other
  names, in practice a file with no extension.
- **Defect:**
    - For each operation the sync scans the created paths first and the removed (now missing) paths
      last, in one scanner call.
    - On Android 10, scanning a missing path deletes every gallery row whose path merely starts with
      it, case-insensitively and with no `/` boundary. The code guards against this for folders
      (`Trip` must not drop `Trip 2`) but still scans missing files.
    - So renaming `IMG_2024` to `IMG_2024.jpg` indexes the new file, then deletes that brand-new
      row.
    - Deleting or moving an extensionless `IMG` drops the rows of every `IMG_0001.jpg` beside it.
    - The files themselves are untouched; they are missing from galleries until the next full media
      scan.
- **Trigger:** Android 10. Either:
    - rename a file so the new name extends the old one (adding a missing extension, `clip.mp` →
      `clip.mp4`);
    - delete or move an extensionless file whose name prefixes its siblings' names.
- **Evidence / verification:**
    - A throwaway unit test in a disposable copy (API 29, rename `IMG_2024` → `IMG_2024.jpg` with
      the old row indexed) shows the scanner receiving `[.../IMG_2024.jpg, .../IMG_2024]` in that
      order. The existing test at `AndroidMediaIndexSyncTest.kt:470` pins the same created-then-gone
      order.
    - At the baseline, the rename deleted exactly the old row and then scanned the new path, so
      nothing else was touched.
    - Refutation attempt: the comment at `:180-183` assumes every name has an extension ("extension
      included"). Nothing in GALLERY_SYNC_PLAN.md accepts this case.
- **Suggested fix:**
    - On Android 10, scan an entry's missing paths before its created ones.
    - After scanning a missing file, rescan the existing siblings whose names start with its name.
    - Do this together with the next finding's per-root error handling, or a failing lookup would
      then also skip the created scans.

### [a/error-handling/review-prompt/visible-failures-do-not-block-ask] Some failures the user sees do not stop the rating request

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/iteminfo/ItemInfoViewModel.kt:255`
    - Related: same file `:259`; `activities/MainActivity.kt:172`;
      `ui/screens/picker/PickerViewModel.kt:120-121`;
      `ui/screens/pdfviewer/PdfViewerViewModel.kt:240`
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** Low. It needs a user who already qualifies for the rating request and hits one of
  these failures in the same session. The Info and startup cases are the plausible ones.
- **Defect:** AGENTS.md requires every user-visible failure to block the rating request for the rest
  of the session, and similar paths do (a missing recent or favorite, a gone analyzer volume, a
  folder listing failure, a wrong ZIP password). These do not:
    - the Info screen's "could not load" state, for a file that is gone or whose details fail to
      read;
    - the "startup folder unavailable" toast;
    - the picker's "unable to load files" state;
    - a wrong PDF password.

  A later success can then trigger the rating request right after the failure.
- **Trigger:** Open Info on a stale search result or recent, or start the app with its startup
  folder on a removed SD card, then complete any counted action.
- **Evidence / verification:**
    - None of these paths calls the failure hook or sends a failure event that calls it, and the
      error reporter does not call it either.
    - The startup path's disarm only clears a pending request; it does not block future ones.
    - The picker case is practically unreachable, because its helpers catch their own errors.
    - The wrong PDF password is a policy call; the ZIP flow treats the same situation as a failure.
    - This was reported in an earlier audit on this branch; none of these files has changed since.
- **Suggested fix:** Call the failure hook in both Info error branches, in the picker's storage-load
  error branch, beside the startup-unavailable toast (inside its existing "started" check), and, if
  the owner agrees, on a wrong PDF password.