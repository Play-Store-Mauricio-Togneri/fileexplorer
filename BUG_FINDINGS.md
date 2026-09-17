### [a/boundary-and-encoding-cases/file-sorting/name-tiebreaker-ordering-mismatch] The size/date tiebreaker does not order names the way the name sort does

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:246-247` (
  `thenByName`) against `:257-274` (`sortByNameInPlace`); the claim is at `:208-215`
- **Severity:** Low
- **Confidence:** Low
- **Defect:** `thenByName()` keys on `String.CASE_INSENSITIVE_ORDER` while `sortByNameInPlace` keys
  on `name.lowercase(Locale.ROOT)`, and the KDoc asserts the two are the same ordering ("matches
  what `sortByNameInPlace` keys on without the per-comparison `lowercase()` allocation"). They are
  not: `CASE_INSENSITIVE_ORDER` folds per `char` via `Character.toUpperCase`/`toLowerCase`, whereas
  `String.lowercase(Locale.ROOT)` applies SpecialCasing and can change a string's length.
- **Trigger:** A folder containing a name with a SpecialCasing character — `"İa"` lowercases to
  `"i̇a"`, so the name sort files it after `"ib"` while the size or date tiebreaker files it before.
  Visible as different row order for the same two files depending on the sort mode.
- **Evidence / verification:** The two key expressions as quoted; the baseline had no name
  tiebreaker on the size/date modes at all (`compareBy({ !it.isDirectory }, { it.size })`), so the
  divergence is introduced. Refutation attempts: for ASCII and Latin-1 the two orderings agree
  exactly, and the test `sortFiles orders equal-size folders the same way the name sort does`
  exercises only ASCII, so it cannot see this. Each comparator is internally consistent, so there is
  no transitivity violation and no `IllegalArgumentException` — the effect is row order only. *
  *Remaining assumptions:** that any user has such a filename, and that they notice the ordering
  differs between modes.
- **Suggested fix:** Key both on the same thing. Either give `thenByName` a `lowercase(Locale.ROOT)`
  selector, or move the name sort to `CASE_INSENSITIVE_ORDER`, and correct the KDoc to name
  whichever is chosen.

### [a/state-and-lifecycle/analyzer-category/selection-not-pruned-when-entries-are-dropped] A row re-selected during a delete leaves a phantom selection with a dead Delete button

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/analyzercategory/AnalyzerCategoryViewModel.kt:281` (
  the pre-launch `clearSelection()`) and `:343-363` (`dropDeleted`)
- **Severity:** Low
- **Confidence:** Low
- **Defect:** `onDeleteConfirmed` clears the selection before launching, and `dropDeleted` prunes
  `entries` and `state.files` but never `selectedPaths`. A path re-selected while its delete is in
  flight therefore survives in `selectedPaths` after its row has been removed from `files`.
- **Trigger:** Confirm a delete, then long-press one of the rows still on screen that is part of
  that in-flight delete. When the delete lands, `isSelectionMode` stays true and the top bar reads "
  1 selected" for a row that is not drawn; `selectedFiles` resolves to empty, so the bottom bar's
  Delete calls `showDeleteConfirmDialog(emptyList())`, `itemsToDelete` is empty, and
  `AnalyzerCategoryScreen.kt:296` never shows the dialog. The user is left with a stuck selection
  and a button that does nothing until they clear it by hand.
- **Evidence / verification:** `dropDeleted` updates `totalBytes`, `files` and `hasMore` and does
  not touch `selectedPaths`. That this is a recognised invariant in this codebase rather than an
  invented one is shown by `FolderViewModel.kt:531-536`, which documents leaving selection mode
  precisely so a selected path that no longer resolves to a listed file cannot "keep the screen
  counting a row it cannot show". Refutation attempts: the row menu is not a second route, since
  `FileListItem.kt:177` hides it in selection mode; `selectAll()` self-heals it, but only if the
  user happens to tap it; and the folder screen is immune because `loadFiles()` resets
  `selectedPaths` on every reload, which the analyzer listing never does. **Remaining assumptions:**
  the re-selection must land inside the delete window, which is usually brief.
- **Suggested fix:** Subtract `paths` from `state.selectedPaths` inside `dropDeleted`, so the
  selection can never outlive the rows it names.

### [b/contract-mismatches/analyzer-category/entry-cap-against-an-uncapped-total] A category can report itself empty while its chart row still shows bytes

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/AnalyzerRepository.kt:206` (
  `DEFAULT_MAX_ENTRIES_PER_TYPE = 10_000`) and
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/AnalyzerResultsHolder.kt:66-79` (
  `remove`)
- **Severity:** Low
- **Confidence:** Low
- **Defect:** `CategoryFiles.totalBytes` is the category's full scanned total across the volume,
  while `CategoryFiles.entries` stops at the 10,000 largest files. `remove` subtracts only the bytes
  of the entries it drops, which is correct arithmetic but leaves a residual whenever the category
  held more files than the cap. Delete every listed entry and `AnalyzerCategoryUiState.isEmpty`
  becomes true while the chart row behind the listing still reports the bytes of the 10,001st file
  onward — a category presented as empty next to a slice claiming several GB, with no way to reach
  the remaining files.
- **Trigger:** A category holding more than 10,000 files (photos on a well-used device), then
  deleting all of the listed ones.
- **Evidence / verification:** The holder's KDoc reasons only about the subtraction ("subtracting
  its size is the same arithmetic either way") and does not address the residual. Refutation attempt
  that succeeded on the arithmetic but not on the state: both the ViewModel's `totalBytes` and the
  holder's are reduced by the same `removed.sumOf { it.size }`, computed independently over
  identical lists, so there is no double subtraction and no numeric error — this is an unreconciled
  presentation state, not bad maths. **Remaining assumptions:** reachability is low, since it needs
  both a category past the cap and the user deleting every listed row, which means paging through
  100 pages.
- **Suggested fix:** Tell the listing how many files the category actually holds so it can
  distinguish "nothing left" from "nothing left that was listed", and show the residual explicitly
  rather than presenting a non-empty category as empty.