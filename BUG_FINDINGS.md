### [a/state-and-lifecycle/extraction/leave-guard-missing-on-search-iteminfo-home] Leaving Search, Item info or Home just after confirming an extraction silently cancels and rolls it back

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/UncompressHandler.kt:113`
  - Related: `ui/screens/search/SearchScreen.kt:178` and `activities/SearchActivity.kt:25`
  - Related: `activities/ItemInfoActivity.kt:41`
  - Related: `ui/screens/home/HomeScreen.kt:104`
  - Related: `ui/screens/search/SearchViewModel.kt:79`, `ui/screens/iteminfo/ItemInfoViewModel.kt:106`, `ui/screens/home/HomeViewModel.kt:148`
  - Where the guard was added: `ui/screens/folder/FolderScreen.kt:267` and `ui/screens/analyzercategory/AnalyzerCategoryScreen.kt:147-152`
- **Severity:** Medium
- **Confidence:** High
- **Likelihood:** Medium. Extracting from Search or Item info and pressing back right away is a natural sequence, but the window is only 500 ms after tapping Extract.
- **Defect:** This is an incomplete fix.
  - The extraction progress dialog is now held back for 500 ms. That dialog is modal and swallows back.
  - The branch added a "show the pending dialog instead of leaving" guard to Folder and Analyzer category only.
  - Search, Item info and Home host the same extraction code in their ViewModel scope, and their back paths are unguarded:
    - Search's toolbar back and system back.
    - Item info's close button.
    - Home's system back on Android 7–11, where back finishes the root activity.
  - Leaving in the window destroys the ViewModel, which cancels the extraction partway. The extraction then rolls back everything it wrote.
  - No toast is shown: cancellation is deliberately silent, and the screen is gone anyway.
- **Trigger:**
  1. In Search, tap a `.zip` result.
  2. Tap Extract.
  3. Within 500 ms, press system back or the toolbar arrow.
  
  The same applies to Item info → Extract → close, and to Home on API 24–30.
- **Evidence / verification:**
  - In the baseline, `d7947cad:util/UncompressHandler.kt` published progress on the first emission, which arrives within tens of milliseconds. The back-swallowing dialog was therefore up almost at once.
  - In HEAD, the screen publishes only after `PROGRESS_DIALOG_DELAY_MS` (`DelayedProgress.kt`).
  - A grep shows `showPendingProgress` and `runningExtractions` used only in the Folder and Analyzer category screens and ViewModels.
  - The handler's own KDoc (`UncompressHandler.kt:215-225`) describes exactly this failure.
  - Refutation attempt (separate agent):
    - It found no BackHandler, no other scope, and no onDestroy or service that keeps the work alive.
    - It confirmed the rollback in `FileRepository.uncompressFile` runs on cancellation.
    - On API 31+ the root activity is moved to the back rather than finished, so Home is unaffected there.
- **Suggested fix:**
  - Mirror the Folder screen's guard in Search, Item info and Home:
    - Expose `runningExtractions` and a `showPendingProgress()` pass-through from each ViewModel.
    - Add `BackHandler(enabled = runningExtractions > 0) { if (!viewModel.showPendingProgress()) leave() }`.
    - Route Search's toolbar back and Item info's close button through the same check.
  - No other caller is affected.