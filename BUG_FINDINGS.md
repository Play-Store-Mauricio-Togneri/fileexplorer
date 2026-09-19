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