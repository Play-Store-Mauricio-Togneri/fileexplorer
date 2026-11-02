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