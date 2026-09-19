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