### [b/contract-mismatches/file-open-telemetry/no-handler-conflates-a-query-failure] A PackageManager query failure is filed as "the device has no app for this file"

- **Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/IntentUtil.kt:222-224`,
  with `hasLaunchableHandler`'s catch at `:327-330`
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `hasLaunchableHandler` returns `false` for two different things: the device genuinely
  having no launchable handler, and `queryIntentActivities` throwing. The new call site reads that
  single `false` as the former and files `file_open_failed` with `reason = "no_handler"`, whose own
  KDoc defines the value as "the device having no app for this file — nothing the app can start
  resolved it". The two causes are then indistinguishable in the dataset, which defeats the
  measurement the event was added to make, and a device where the query fails also files a
  Crashlytics non-fatal on every open-with.
- **Trigger:** "Open with" on a device where `packageManager.queryIntentActivities` throws —
  `TransactionTooLargeException`/`DeadObjectException` under PackageManagerService pressure, or an
  oversized result for the `*/*` wildcard query.
- **Evidence / verification:**
  `if (!hasLaunchableHandler(context, intent)) { trackFileOpenFailed(file, mimeType, source, "no_handler") }`,
  against a helper whose `catch (e: Exception)` reports `query_handlers` and returns `false`.
  Refutation attempt that failed on impact but succeeded on scope: user-visible behaviour is
  unaffected — the comment at `:217-221` is explicit that the probe must never gate the launch, and
  `IntentUtilOpenFileWithTest.noLaunchableHandler_stillLaunchesChooser` pins that the chooser
  launches regardless — so this is a telemetry-correctness defect only. The helper and its catch are
  pre-existing; the call site and the event that reads its result are new. **Remaining assumption:**
  how often the query throws in the field is unknown.
- **Suggested fix:** Have `hasLaunchableHandler` return a tri-state (or null on failure) so the call
  site can file a distinct `reason` such as `query_failed`, keeping `no_handler` to the case it
  documents.

### [a/logic-errors/analyzer-scan/used-total-not-refreshed-for-a-second-scan] A second scan measures against a storage snapshot that only in-listing deletes refresh

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/analyzer/AnalyzerViewModel.kt:160-161`,
  against the one-shot read at `:92`
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `startScan` derives `usedBytes` as `storage.totalBytes - storage.availableBytes` from
  the `storages` list read once in `init`. That list is refreshed in only two places: the
  `StorageUnavailableException` recovery, and `observeResults` after a delete made *inside* the
  category listing. The code states the invariant it is protecting at `:135-139` — leaving the
  snapshot stale "would make a second scan of the volume start from a total too high by exactly the
  space the user deleted, and hand every one of those bytes to SYSTEM". That invariant is left open
  for a route the feature itself offers.
- **Trigger:** From the category listing, use `AnalyzerFileAction.OpenFolder` to jump into
  `FolderActivity`, delete files there, come back, and re-scan. `usedBytes` is computed from the
  pre-delete `availableBytes`, and `breakdown` hands the difference to `AnalyzerCategory.SYSTEM` —
  the exact wrong answer the comment names. Any other change in free space during the analyzer's
  lifetime does the same; since a scan takes minutes, a second scan is always well after the
  snapshot.
- **Evidence / verification:** `val usedBytes = storage.totalBytes - storage.availableBytes` in
  `startScan`, reading `_uiState.value.selectedStorage`, whose `storages` came from `init`.
  Refutation attempt that partly succeeded: every snapshot is stale to some degree, which is why
  this is Low — but the author documented the invariant and closed only one of the two in-app routes
  to breaking it. **Remaining assumption:** how often users take the OpenFolder route and then
  re-scan.
- **Suggested fix:** Re-read `storageRepository.getStorages()` at the head of `startScan` (it is
  already an async read elsewhere in this ViewModel) and derive `usedBytes` from that, rather than
  from the `init` snapshot.

### [b/resource-and-configuration-parity/analyzer-plurals/quantity-set-drift] The new
`analyzer_found` plural declares quantities four languages cannot select and omits one three languages require

- **Location:** `app/src/main/res/values-es/strings.xml:478`, `values-fr/strings.xml:478`,
  `values-pt/strings.xml:478` (missing `many`); `values-in/strings.xml:444`,
  `values-ja/strings.xml:444`, `values-vi/strings.xml:444`, `values-zh/strings.xml:444` (surplus
  `one`)
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** `analyzer_found` is the only plural this release adds whose quantity sets do not
  follow the convention already established in each locale's file. In Spanish, French and Portuguese
  every one of the 17 sibling plurals carries `many`, which CLDR selects for exact millions;
  `analyzer_found` carries only `one`/`other`, so a count of exactly 1,000,000 falls back to
  `other`. In Indonesian, Japanese, Vietnamese and Chinese — single-form languages whose other 17
  plurals each carry `other` alone — it declares a `one` item that `PluralRules.select()` can never
  return.
- **Trigger:** `AnalyzerScreen.kt:359` calls
  `pluralStringResource(R.plurals.analyzer_found, fileCount, …)`. The es/fr/pt half needs a scan
  reporting exactly 1,000,000 (or 2,000,000, …) files in a category; the in/ja/vi/zh half is a
  resource that is dead on every count.
- **Evidence / verification:** Baseline quantity histograms confirm the drift is introduced —
  `git show 9e87306d:…/values-es/strings.xml` has 13 plurals with `13 many / 13 one / 13 other`, and
  HEAD has `17 many / 18 one / 18 other`, the odd one out being `analyzer_found`;
  `values-{in,ja,vi,zh}` go from `13 other` and nothing else to `1 one / 18 other`. Lint
  corroborates independently: `MissingQuantity` at exactly those three lines and `UnusedQuantity` at
  exactly those four. `LocalizationParityTest` cannot see either — its `requiredQuantities` map
  covers only `ar`/`ru`/`ro` and only checks for *missing* quantities, never surplus ones.
  Refutation attempt that partly succeeded and caps this at Low: I parsed all three files and
  confirmed `many` and `other` are byte-identical in all 17 sibling plurals in each of es, fr and
  pt, so following the convention would produce the same text and nothing user-visible differs
  today. **Remaining assumption:** that no future correction to those translations makes the two
  forms diverge. Neither half can throw; there is no crash here.
- **Suggested fix:** Add `many` to `analyzer_found` in es/fr/pt and drop the `one` item in
  in/ja/vi/zh. Widening `LocalizationParityTest.requiredQuantities` to `es`/`fr`/`pt` → `many` and
  adding a symmetric assertion that no plural declares a quantity its language cannot select would
  close both halves and stop the next one.

### [a/concurrency/uncompress-rollback/absent-path-reported-as-rolled-back] The extraction rollback can report a path it did not remove

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:1496-1503`,
  with `deleteTree` at `:524`
- **Severity:** Low
- **Confidence:** Low
- **Defect:** The rollback is `if (created.exists() && deleteTree(created)) rolledBack.add(it)`, and
  `deleteTree(f)` is `deleteRecursive(f).failureErrno == null`. Since `AlreadyAbsent` carries no
  errno, a path that disappears between the `exists()` check and `removePath` makes `deleteTree`
  answer true, and the path joins `rolledBack` — which the caller feeds to a prefix MediaStore
  delete. That is exactly what the `exists()` guard was added to prevent, per the comment at
  `:1490-1495`.
- **Trigger:** Something else removes a just-extracted path in the microseconds between `exists()`
  and the `remove(2)` inside `deleteRecursive`, during a failed extraction's rollback.
- **Evidence / verification:** The guard and the `failureErrno == null` test as quoted. Baseline was
  `if (deleteRecursive(File(it))) rolledBack.add(it)`, and `File.delete()` answered `false` for an
  absent path, so the baseline did not have this hole — it had a different one, failing to report a
  directory that did come away when a child had been removed concurrently. Refutation attempt that
  nearly succeeded: the `exists()` guard genuinely narrows this to a microsecond window, and for any
  harm to follow, something must *also* take the path over before the MediaStore delete runs — it is
  a double race. **Remaining assumptions:** both races, neither of which could be provoked here.
- **Suggested fix:** Have `deleteTree` distinguish `Removed` from `AlreadyAbsent` and add to
  `rolledBack` only when something was actually unlinked, matching the `removedRootPaths`/
  `absentRootPaths` split the delete walk already makes.

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