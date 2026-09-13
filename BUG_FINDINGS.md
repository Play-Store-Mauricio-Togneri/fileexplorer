# Bug Findings

## Medium


### [c/dead-or-unreachable-behavior/test-structure-guards/a-typed-context-import-exempts-the-context-only-check] The new "context-only tests declare why" guard is disabled by the very import its targets need

**Location:** `scripts/check_tests.py:537-547` (`REAL_DEVICE_API`), used at `:553-573` (`check_context_only_tests_say_why`)

**Severity:** Medium
**Confidence:** High

**Defect:** The guard's stated contract, in its own comments, is that a test "whose only platform touch is `InstrumentationRegistry`" reaches the device for one thing — a `Context` — and must therefore carry `// device-required: <why>`. The exemption regex `REAL_DEVICE_API` is documented as "everything that reaches a real device for **more than** a Context", but its Android alternation includes the bare package `android\.content`, which is the package `Context` itself lives in. A test that declares the type it borrows (`import android.content.Context`) is exempted; the identical test written with the type inferred is not. Which files the guard fires on is decided by an unrelated style choice.

**Trigger:** Write an instrumentation test whose only platform use is `InstrumentationRegistry.getInstrumentation().targetContext`, and give the field an explicit type.

**Incorrect result:** The rule the guard exists to enforce is not enforced for the shape it was written for. Four files currently in the suite are exempted solely by that import and carry no `device-required` marker, while four substantively identical files that omit the import did have to state a reason. Instrumentation tests that belong in the JVM source set keep accruing on the emulator unchallenged, which is the drift `check_instrumentation_tests_need_a_device` and this sibling were added to stop.

**Evidence / verification:** Re-implemented both regexes standalone (no import of the module, `python3 -B`, nothing written) and classified every `*Test.kt` under `app/src/androidTest/java`: 62 files match `CONTEXT_ONLY_API`, 0 are currently flagged, and 7 are exempted *solely* by the `android.content` alternative. Of those seven, three legitimately use `Intent`/`ContextWrapper`/`PackageManager` (`IntentUtilOpenFileWithTest`, `IntentUtilPlayStoreTest`, `IntentUtilShareTest`), and four import `android.content.Context` and nothing else from `android.*`: `AndroidStorageVolumeChangeSourceTest.kt`, `AppImageLoaderCacheKeyTest.kt`, `ThumbnailDiskCacheTest.kt`, `ThumbnailDiskCacheWiringTest.kt`.
Refutation attempt: checked whether those four genuinely need a device for more than a `Context` — they drive Coil, a `DiskCache` under `context.cacheDir`, and a receiver registration whose device-need is expressed only through `InstrumentationRegistry`, i.e. exactly the signal the check treats as insufficient. Whether or not they should move, the classification is not being made on device use. Also checked whether the overlap was deliberate: the author did reason about one exclusion — `androidx.test.platform.` is dropped from `REAL_DEVICE_API` precisely because it *is* `InstrumentationRegistry` — but not about `android.content`.
Baseline: `git show 9e87306d…:scripts/check_tests.py | grep -c REAL_DEVICE_API` → 0. The check and both regexes arrive in this branch.
Verified the guard suite itself passes (`scripts/check-tests.sh`, exit 0, 6/6) — the defect is that this check never fires, not that it errors.

**Suggested fix:** Drop `content` from `REAL_DEVICE_API`'s `android.*` alternation — keep it in `ANDROID_API`, where "touches any Android API" is the right question — or narrow it to the `android.content` members that really do imply more than a `Context`: `Intent`, `ContentResolver`, `ContextWrapper`, `pm.*`. The four newly exempt files then have to state a reason like the other four did.

### [b/contract-mismatches/storage-volume-classification/emulated-read-as-not-removable] An SD card adopted as internal storage is reclassified as internal and loses its name

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/AndroidStorageSource.kt:101`
Related: `:55-56` (`type`), `:69-75` (`name`), `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/VolumeInfo.kt:32`, `app/src/main/java/com/mauriciotogneri/fileexplorer/data/model/StorageDevice.kt:16` and `:46-61`

**Severity:** Medium
**Confidence:** Medium

**Defect:** `Volume.isRemovable` is computed as `info?.isEmulated?.not() ?: !path.contains("emulated")`, i.e. it answers "can this volume be detached?" by asking the framework whether the volume is *emulated*. Those are different questions. Android reports adoptable storage — an SD card the user formatted as internal — as a `TYPE_EMULATED` volume stacked on a removable private volume: `StorageVolume.isEmulated()` is true while `StorageVolume.isRemovable()` is also true. The predicate that matches the field's name, its KDoc ("the device's own storage, or one that can be detached") and the baseline classification is `isRemovable()`, which is public API at the same level.

**Trigger:** Run the app on a device with an SD card formatted as internal storage (Settings → Storage → Format as internal, or `sm set-force-adoptable`).

**Incorrect result:** `type()` returns `StorageType.INTERNAL`, so `name()` takes the `R.string.storage_internal` branch and never consults the framework description — the card loses its own label and, alongside the built-in volume, is numbered by `numberDuplicates` into "Internal storage 1" / "Internal storage 2". `storageIcon()` draws a phone rather than a card on the home cards, the destination picker and the analyzer. `StorageDevice.analyticsType` flips from `sd_card` to `internal`, silently moving a dimension the `StorageType` KDoc calls "part of the analytics contract". The baseline classified the same volume as `SD_CARD`, because an adopted volume is surfaced at `/storage/<uuid>`, which does not contain `emulated`.

**Evidence / verification:** Read the current and baseline implementations (`git show 9e87306d…:…/StorageDevice.kt` → `fun isSdCard(path: String) = !path.contains("emulated")`, used for both label and icon). Confirmed against the platform metadata that the alternative is available: `StorageVolume` is API 24, and `isEmulated()`, `isRemovable()` and `getDescription(Context)` all carry `since 24` in `platforms/android-37.0/data/api-versions.xml`, against `minSdk = 24` (`app/build.gradle.kts:23`). Checked every other volume shape and found `isRemovable()` never worse: primary emulated on internal disk gives `emulated=true, removable=false` (INTERNAL, unchanged); a physical card or USB drive is `TYPE_PUBLIC` with `emulated=false, removable=true` (SD_CARD, unchanged). Only adopted storage separates the two.
Refutation attempt: looked for a guard or fallback that recovers the removable answer (none — the path fallback is only consulted when `info` is null), and for evidence the equivalence is intended rather than accidental. It is intended: `app/src/test/java/…/AndroidStorageSourceTest.kt:213-225` is a test named `getStorages takes removability from the framework rather than from the path`, commented "A volume the framework calls emulated is this device's own storage whatever its path looks like", which constructs exactly the adopted-storage shape (path `/storage/1234-5678`, `isEmulated = true`) and asserts INTERNAL. The premise is false for adopted storage, so the equivalence is deliberate but wrong, and a green test now holds it in place. The test's `described()` helper (`:250-256`) models `isEmulated = path.contains("emulated")`, which cannot represent an adopted volume at all.
Remaining uncertainty, which is why this is Medium rather than High confidence: the flag values for a `TYPE_EMULATED` volume on an adopted disk come from AOSP's hidden `VolumeInfo.buildStorageVolume`, which is not reproducible in this environment (no platform sources installed, and the attached emulator has no adopted volume); and adoptable storage is disabled by some OEMs, so the population affected is device-dependent.

**Suggested fix:** Key the type on `StorageVolume.isRemovable()` instead of `isEmulated()`, keeping `!path.contains("emulated")` as the fallback for a volume the framework does not recognise. Update `AndroidStorageSourceTest`'s case and its `described()` helper to carry the two flags separately, so an adopted volume — emulated *and* removable — becomes representable.

### [b/validation-and-coercion/file-search/a-literal-wildcard-in-the-query-switches-the-whole-match-to-an-anchored-glob] A search whose text contains `?` or `*` stops finding files whose names contain those characters

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:1050-1057` and `:1083`
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/GlobPattern.kt:4`, `:13`, `:24`, `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/search/SearchViewModel.kt:305-310`

**Severity:** Medium
**Confidence:** Medium

**Defect:** `globPatternOrNull(query)` treats the presence of `*` or `?` anywhere in the query as a request for pattern matching, and `GlobPattern.matches` is anchored to the whole filename. Both characters are legal in names on the ext4/f2fs volume behind `/storage/emulated/0`, so a query that merely *contains* one is reinterpreted, and the substring match that query has always had is not attempted at all. There is no escape syntax, no fallback when the pattern matches nothing, and nothing in the UI says the mode changed — `search_placeholder` is just "Search files…".

**Trigger:** Search for a fragment of a filename that contains `?` or `*`. For example, searching `Here?` for a track named `Where Do We Go From Here?.mp3`, or `FAQ?` for `FAQ?.pdf`.

**Incorrect result:** The screen reports no results for a file that exists and that the previous release found. `Here?` becomes a glob demanding a name of exactly five code points beginning `Here`, which `Where Do We Go From Here?.mp3` is not; the baseline's `name.contains(query, ignoreCase = true)` matched it.

**Evidence / verification:** Read both predicates side by side — HEAD builds `matchesName` from the glob when `queryHasWildcard` is true (`FileRepository.kt:1050-1057`) and applies it at `:1083`; `git show 9e87306d…:…/FileRepository.kt` shows the baseline's unconditional `if (name.contains(query, ignoreCase = true))` at the same point, with no `GlobPattern.kt` in the tree at all.
Refutation attempt: checked whether `matches` also performs a containment pass (it does not — it anchors both ends, and the KDoc at `GlobPattern.kt:19-23` states the anchoring is deliberate: "`*.txt` does not match `notes.txt.bak`"); checked whether the matcher itself is faulty (it is not — I verified the `nameAfterStar ≤ nameIndex < name.length` invariant that keeps the backtrack's `codePointAt` in bounds, the surrogate-safe advance by `Character.charCount`, the trailing-star handling and the empty-name and empty-pattern cases, and found the algorithm correct); and checked whether the app warns the user (`SearchViewModel.kt:283` confirms the app knows this query is "wildcard used", but reports it only to analytics).
Remaining uncertainty, which is why this is Medium rather than High confidence: how often the affected characters actually occur in users' filenames. They are forbidden on FAT/exFAT SD cards and rejected by most download paths, but legal and not unusual in media and document names on internal storage.

**Suggested fix:** When the glob produces no results for the whole search, re-run it as the plain substring match — the smallest change, and it preserves every match the previous release produced. Alternatives: keep glob matching unanchored (implicit leading and trailing `*` unless the user anchors), or surface the mode in the UI so an empty result set is explicable.

## Low

### [a/state-and-lifecycle/analyzer-volume-selection/selection-state-not-reconciled-when-a-volume-disappears] The analyzer keeps offering a volume that has gone away, with an enabled button that does nothing

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/analyzer/AnalyzerViewModel.kt:185-203` (the `StorageUnavailableException` path) and `:119-152` (`observeResults`)
Related: `:69` (`selectedStorage`), `:159` (`startScan`), `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/analyzer/AnalyzerScreen.kt:186`, `:214`, `:222`

**Severity:** Low
**Confidence:** High

**Defect:** Two halves of the same omission. (a) `startScan`'s `.catch` returns the user to `AnalyzerStep.SELECTION` with an error message but never re-reads `storages`, so the volume list still offers the volume whose disappearance caused the failure. (b) `observeResults` *does* refresh `storages`, but never reconciles `selectedPath` against the refreshed list, and the Analyze button is gated on `selectedPath != null` rather than on the resolved `selectedStorage`, while `startScan` begins with `val storage = _uiState.value.selectedStorage ?: return`.

**Trigger:** (a) Eject an SD card during a scan of it. The walk loses a directory, `storageAnswers` answers false, `StorageUnavailableException` is raised, and the user lands back on a volume list that still shows the card with its old capacity bar. (b) Scan a volume, open a category, delete a file — `observeResults` re-reads `storages` — and have the selected volume be unmounted inside that window; then go back to the volume list.

**Incorrect result:** (a) Selecting the ejected card and tapping Analyze walks a dead path, fails the same way and toasts again, with nothing on the list to say the volume is gone — an unbounded retry loop. (b) No card renders as selected, because `storage.path == selectedPath` is false for every row, yet the primary button is enabled; tapping it hits the `?: return` and produces no scan, no toast and no state change at all.

**Evidence / verification:** Traced the catch block (`AnalyzerViewModel.kt:185-203`) and confirmed it copies only scan fields — `storages` is untouched — and that the only two readers of the storage list are `init` (`:92`) and `observeResults`, which returns early unless `step == RESULTS` (`:123`). Traced `selectedPath`'s only writers (`:100` init, `:155` `selectStorage`); neither is reconciled against a refreshed list. Read `AnalyzerScreen.kt:222` (`enabled = selectedPath != null`) against `AnalyzerViewModel.kt:159` (`?: return`).
Refutation attempt: checked whether `VolumeSelection` bails out before drawing the button — it only early-returns on `storages.isEmpty()` (`AnalyzerScreen.kt:186`), a different state; a non-empty list missing the selected volume falls straight through to the enabled button. Checked whether the new `StorageVolumeChangeSource` feeds the analyzer and would refresh it — it does not; only `HomeViewModel` observes it.
Both files are new on this branch (`git diff --name-status 9e87306d…..HEAD` marks them `A`), so neither half can be pre-existing.

**Suggested fix:** Re-read the storage list on the `StorageUnavailableException` path alongside the error message, the way the delete-correction path already does, and clear `selectedPath` wherever the refreshed list no longer contains it. Gate the button on the resolved device (`selectedStorage != null`) rather than on the raw path, so a dangling selection cannot present an actionable control.

### [b/contract-mismatches/delete-reporting/one-message-counts-roots-on-one-path-and-leaf-files-on-the-other] The partial-delete message counts different things depending on which delete path ran

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:928-936` and `:1048-1056`
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:1939-1951` (`DeleteResult.failedCount`/`clearedCount`, defined over selected roots), `:1884` and `:627-636` (`DeleteProgress.deletedFiles`/`failedFiles`, incremented per leaf), `app/src/main/res/values/strings.xml:71-74`, rendered at `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderScreen.kt:174-182`

**Severity:** Low
**Confidence:** High

**Defect:** The new small-delete branch fills `FolderUiEvent.ShowDeletePartialSuccess` with `result.clearedCount` / `result.failedCount`, which count **selected roots**, while the progress branch fills the same event with `progress.deletedFiles` / `progress.failedFiles`, which count **leaf files**. Both feed one plural, `delete_partial_success` ("Deleted %1$d items, %2$d failed"), with nothing on screen to say which unit is in play.

**Trigger:** Delete a mixed selection in which some roots fail. Which branch runs is decided by `DELETE_PROGRESS_THRESHOLD`, measured in nodes, which the user cannot see.

**Incorrect result:** A selection of four folders holding 900 files reports "Deleted 3 items, 1 failed" below the threshold and "Deleted 412 items, 488 failed" above it, for the same action and the same outcome.

**Evidence / verification:** Read both producers and confirmed they feed one event type; read the plural in `values/strings.xml` and confirmed both use `%1$d`/`%2$d` with the word "items"; traced the definitions of both count pairs to establish that they are defined over different populations. Baseline (`git show 9e87306d…:…/FolderViewModel.kt`) emitted `ShowToastRes(R.string.delete_error)` from the small-delete branch and produced no counts at all, so the disagreement arrives with this branch.
Refutation attempt: checked whether "items" is vague enough to make both truthful — it is, which is why this is Low rather than higher; the defect is that two producers of one message disagree on the unit, not that either number is false.

**Suggested fix:** Report the same unit from both producers — selected roots is the one both can compute — or give the two paths distinct strings.

### [a/null-and-numeric-hazards/transfer-progress/skipped-files-counted-in-the-byte-total] The copy/move progress bar can never reach 100% when files are skipped

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:717` with `:823-841` and `:786-796`
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:621-630`

**Severity:** Low
**Confidence:** High

**Defect:** `totalBytes` is tallied once up front from `sources.sumOf { File(it.path).totalSize() }`, over the whole selection *including* files that are later skipped because they cannot be opened. `copiedBytes` only accumulates bytes actually written. Nothing subtracts a skipped file's length from the denominator.

**Trigger:** Copy or move a selection containing files the OS names but refuses to open — `Android/data` on a removable volume is the case that reaches users.

**Incorrect result:** The progress bar stalls short of full and the dialog closes on `isComplete` at a visibly partial fraction, implying the transfer was cut off. The partial-success toast that follows states the real counts, so this is cosmetic.

**Evidence / verification:** Read the tally (`:717`), the skip path (`:786-796`, which increments `skippedFiles` and returns without writing) and the emission (`:823-838`). Confirmed the file *counter* has the same asymmetry deliberately and is reconciled for it — `CopyProgress.skippedFiles` is documented at `:1808-1815` as "counted towards `totalFiles` … so the two together say how much of the selection made it across" — while no equivalent reconciliation exists for bytes. Baseline had no skip path at all (`git show 9e87306d…:…/FileRepository.kt` opens the source directly after reserving the target), so every counted byte was either copied or the transfer failed.

**Suggested fix:** Track `skippedBytes` alongside `skippedFiles` and render the fraction against `totalBytes - skippedBytes`, or subtract the skipped file's length from a running total as each skip is recorded.

### [b/contract-mismatches/transfer-telemetry/outcome-dimension-omitted-on-two-of-the-branches-that-set-it] Two transfer-failure events are missing the `outcome` dimension their siblings set

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:686-696` and `:714-718`
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/AnalyticsTracker.kt:983-999`, and the branches that do set it at `:687-692`, `:1004-1012`, `:1023-1029`

**Severity:** Low
**Confidence:** High

**Defect:** `trackOperationFailed`'s `outcome` parameter defaults to null and is then dropped from the bundle. Within one `if/else` written in this change, the `sourceDeleteFailed` arm passes `outcome = "partial"` and the sibling arm does not; the delete-failure-only branch likewise omits it. Every delete path on the same screen sets it.

**Trigger:** Any partial transfer without `sourceDeleteFailed`, and any `sourceDeleteFailed` with nothing skipped.

**Incorrect result:** `operation_failed` rows land with `error_type=partial` / `error_type=source_delete_failed` and no `outcome`, so a dashboard query filtered on that dimension under-counts transfers — the split the in-code comment at `:681-685` introduces is only half applied. No user-visible effect.

**Evidence / verification:** Read `trackOperationFailed`'s signature and body (`AnalyticsTracker.kt:983-999`) and confirmed `outcome?.let { put(…) }` drops a null; compared the five call sites on this screen. The parameter and these branches are both new on this branch.
Refutation attempt: re-read the comment at `:681-685` claiming the `error_type`/`outcome` split is deliberate — it documents the split but gives no reason to leave `outcome` unset on the two branches whose shape is just as knowable.

**Suggested fix:** Pass an `outcome` on both branches, so every `operation_failed` emitted for a transfer carries the dimension the delete paths already guarantee.

### [c/dead-or-unreachable-behavior/test-string-vocabulary/an-escaped-percent-is-never-decoded] The hardcoded-string guard has no coverage for the new percentage resource

**Location:** `scripts/check_tests.py:209-231` (`_format_pattern`) and `:242-248` (escape decoding in `translatable_strings`)
Related: `app/src/main/res/values/strings.xml:157`, `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/analyzer/AnalyzerScreen.kt:486-489`

**Severity:** Low
**Confidence:** High

**Defect:** `PLACEHOLDER` consumes `%1$s` and leaves `%%` behind as a literal segment, and `translatable_strings` decodes `\'`, `\"` and `\\` but never `%%` → `%`. The pattern generated for a resource ending in `%%` therefore ends in two literal percent signs, while the string a test would actually see ends in one.

**Trigger:** This branch adds the first resource containing `%%`: `<string name="analyzer_percent_format">%1$s%%</string>`.

**Incorrect result:** The generated pattern is `^.+%%$`, which the rendered value (`60%`) can never match, so `check_no_hardcoded_ui_strings` has no coverage for that resource. A future `onNodeWithText("60%")` aimed at the analyzer donut would pass the guard while being locale-dependent in exactly the way the guard exists to prevent — `AnalyzerScreen.kt:487` formats the number through `LocalLocale.current.platformLocale`, which yields Arabic-Indic digits under `ar`.

**Evidence / verification:** Ran `translatable_strings()` standalone against the real resources and dumped the generated patterns; `^.+%%$` is present and is the only pattern containing `%%`. Confirmed `analyzer_percent_format` is new on this branch (`git show 9e87306d…:app/src/main/res/values/strings.xml | grep '%%'` returns nothing), so the flaw in `_format_pattern` is older but was unreachable until now.
Refutation attempt: searched the instrumentation suite for a violation the blind spot is already hiding. One candidate, `ItemInfoScreenTest.kt:591` `onNodeWithText("75.0%")`, is not one: `ItemInfoScreen.kt:1143` builds that with `String.format(Locale.US, "%.1f%%", ratio)` — a hardcoded-locale format with no resource behind it, which the guard's own docstring permits — and it predates the branch. So this is a latent blind spot, not a currently-missed defect, which is what keeps it at Low.

**Suggested fix:** Decode `%%` → `%` alongside the existing escapes, after the placeholder scan has run, so the generated pattern ends in a single literal percent.

### [b/resource-and-configuration-parity/storage-volume-naming/framework-description-follows-the-system-locale] A removable volume is named in the system's language, which is not always the app's

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/AndroidStorageSource.kt:69-75`
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/VolumeInfo.kt:32`, rendered at `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/components/StoragesSection.kt:105`, `ui/screens/picker/StorageSelectorContent.kt:56`, `ui/screens/analyzer/AnalyzerScreen.kt:278`, and carried into breadcrumbs via `util/StartupDestinationResolver.kt:61,74`

**Severity:** Low
**Confidence:** High

**Defect:** `name()` now returns `StorageVolume.getDescription(context)` verbatim for every non-internal volume. For an unlabelled volume the framework computes that string in the *system* locale, from system_server's own resources, while the app's own strings resolve through its 20 shipped `values-*` sets.

**Trigger:** A device whose system language is one the app does not ship — Korean, Polish, Swedish, Thai and most others — with a removable volume mounted and no vendor label on it.

**Incorrect result:** The storage list mixes two languages: the built-in volume reads "Internal storage" (the English fallback the app resolves to) while the card beside it reads the system's own translation.

**Evidence / verification:** Read `name()` and `volumeInfoAt`; confirmed the description is returned unmodified whenever it is non-blank. Confirmed the app does not override its own locale (`grep -rn "setApplicationLocales|LocaleListCompat|localeConfig|AppCompatDelegate" app/src/main` returns nothing), so app and system locales agree for the 20 shipped languages and diverge only outside them. Baseline named every removable volume from `R.string.storage_sd_card` (`git show 9e87306d…:…/AndroidStorageSource.kt`), which always followed the app's own resource resolution.
Refutation attempt: the KDoc at `:58-67` shows the framework description is consulted deliberately, because it is the only place a volume's kind and label are recorded — that rationale holds for a labelled volume, whose label is locale-independent, but not for the unlabelled case where the framework returns its own translated "SD card"/"USB drive".

**Suggested fix:** Use the framework description only when it is a volume *label* — i.e. when it differs from the framework's own generic names — and fall back to the translated resource otherwise. Or accept it explicitly and record the trade-off in the KDoc.

### [a/state-and-lifecycle/folder-sort-scroll-reset/a-failed-listing-advances-the-anchor-it-was-meant-to-reset] A sort change whose listing failed disarms the scroll reset for the retry that succeeds

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderScreen.kt:141-147`
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:1226-1240`

**Severity:** Low
**Confidence:** Medium

**Defect:** `loadFiles`'s catch-all publishes the new `sortMode` while leaving `files` as the previously sorted list — deliberately, so the sort sheet does not show a selection the app no longer sorts by. The screen's `LaunchedEffect(state.sortMode)` cannot tell that apart from a real re-sort: it advances `previousSortModeName` to the new mode and scrolls against a list that was never re-ordered. When the retry later succeeds and the order really does change, `state.sortMode` is unchanged, so the effect does not fire.

**Trigger:** Change the sort mode in a folder whose re-listing throws — volume unmounted, permission revoked mid-listing — then return to the folder so `onScreenResumed` reloads successfully.

**Incorrect result:** After the retry, the keyed `LazyColumn` anchors on the row that was on top under the old order and follows it to its new position — the jump the reset exists to prevent.

**Evidence / verification:** Read the effect and the failure publish. Confirmed `previousSortModeName` has no other writer. Confirmed the effect's *immediate* side effect is harmless, because the error state renders instead of the list — the damage is the `previousSortModeName` write, which persists. The whole `listState`/`previousSortModeName` mechanism is new on this branch (`git show 9e87306d…:…/FolderScreen.kt` has no `rememberLazyListState`); the underlying anchoring behaviour is pre-existing, so only the partially-effective mitigation is introduced.

**Suggested fix:** Advance `previousSortModeName` only when the state carrying the new mode also carried a fresh listing — either do not publish `sortMode` from the failure path, or gate the comparison on the load having produced rows.

### [a/resource-management/analyzer-results-holder/not-released-when-the-activity-is-destroyed-without-finishing] A completed scan's file lists survive the analyzer being destroyed under memory pressure

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/AnalyzerActivity.kt:50-56`
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/AnalyzerResultsHolder.kt:41-56`, `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/AnalyzerRepository.kt` (`DEFAULT_MAX_ENTRIES_PER_TYPE = 10_000`)

**Severity:** Low
**Confidence:** Medium

**Defect:** `onDestroy` clears the process-wide holder only `if (isFinishing)`. The guard is required — a rotation destroys this activity while the category listing on top of it still reads the holder in `onCreate` — but it also skips the clear when the system destroys the activity to reclaim memory. The other three clear sites (`startScan`, `confirmCancelScan`, `backToSelection`) are all user-driven and unreachable once the ViewModel store is gone.

**Trigger:** Complete a scan, open a category listing, and have `AnalyzerActivity` destroyed by the system rather than by the user — low-memory reclaim of a stopped activity, or the "Don't keep activities" developer option.

**Incorrect result:** Up to 10,000 `AnalyzerFileEntry` per type stay reachable from an `object` after the activity holding them is gone — the file's own estimate of ~220 bytes per entry puts that in the low tens of megabytes. The `AnalyzerViewModel` created when the user navigates back starts at `AnalyzerStep.SELECTION`, so nothing displays those results and nothing releases them until the analyzer is finished outright. The retention is worst exactly when memory is scarce, which is what caused the destroy.

**Evidence / verification:** Read the guard and confirmed the rotation case genuinely requires it (`AnalyzerCategoryActivity.onCreate` reads `filesFor(category)`, so an unconditional clear would break the listing across rotation). Confirmed no `onTrimMemory`/`ComponentCallbacks2` path exists — `FileExplorerApplication` (unchanged) registers none. Confirmed the retention is bounded: finishing the analyzer does clear it, so this is a window, not an unbounded leak, which is what keeps it at Low. Both files are new on this branch.

**Suggested fix:** Release the holder when the analyzer's own ViewModel is cleared as well as on `isFinishing`, or on `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` — the results are only ever read while the analyzer task is foregrounded.

### [b/contract-mismatches/analyzer-category-results/a-recursive-delete-updates-only-the-listed-category] Deleting a listing row whose path has become a directory leaves the other categories overstated

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/analyzercategory/AnalyzerCategoryViewModel.kt:266-317` and `:327-362`
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/AnalyzerResultsHolder.kt:64-80`, `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:480-506`

**Severity:** Low
**Confidence:** Medium

**Defect:** `FileRepository.delete` removes a path recursively, which the ViewModel's own comment acknowledges as the answer for a row whose path has become a directory since the scan. `dropDeleted` then reconciles only the listed category: it filters this category's `entries` and calls `AnalyzerResultsHolder.remove(category, paths)` with the deleted root alone. Files unlinked underneath it that belong to other categories are never taken off their totals or their entry lists.

**Trigger:** A path the scan recorded as a file has since been replaced by a directory containing files of other types, and the user deletes that row from the category listing.

**Incorrect result:** The other categories keep entries for files that no longer exist — tapping one opens nothing — and their slices of the chart stay overstated until a re-scan. Because `AnalyzerViewModel.observeResults` derives `usedBytes` from the freed bytes it can see, the unaccounted difference is handed to the SYSTEM slice.

**Evidence / verification:** Traced `onDeleteConfirmed` → `FileRepository.delete` → `deleteRecursive` (recursive by contract) → `dropDeleted` → `AnalyzerResultsHolder.remove(category, …)`, which is single-category by construction. Confirmed the scan itself can never record a directory as an entry (`AnalyzerRepository.kt:139-142` pushes directories onto the stack and never calls `largest.add` for them), so the trigger genuinely requires a file→directory change between scan and delete — which is why this is Medium confidence and Low severity. The comment at `:256-265` acknowledges the staleness but covers only the deleted row itself.

**Suggested fix:** Either refuse to delete from this listing a path that no longer stats as a file, or apply the removal prefix-wise across every held category instead of only the one being listed.

### [a/state-and-lifecycle/analyzer-chart/the-reveal-animation-is-owned-by-a-recyclable-list-item] The donut chart replays its reveal animation every time it scrolls back into view

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/analyzer/StorageDonutChart.kt:60-63`
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/analyzer/AnalyzerScreen.kt:385-399`

**Severity:** Low
**Confidence:** Medium

**Defect:** The reveal progress is held in a plain `remember { Animatable(0f) }` inside `item(key = "chart")` of a `LazyColumn`, driven by `LaunchedEffect(Unit)`. A lazy item's composition is disposed once it leaves the viewport, and an item key governs saveable state and reuse identity, not plain `remember` — so the `Animatable` is recreated at 0f on re-entry and the effect replays. The file's own KDoc states the intent: "The ring is drawn to scale exactly once, on first appearance."

**Trigger:** On the results screen, scroll until the 220dp chart leaves the viewport, then scroll back up.

**Incorrect result:** The ring re-draws from empty over 700ms every time, instead of staying at scale. Cosmetic.

**Evidence / verification:** Read the state declaration, the `LaunchedEffect(Unit)` key, and the list structure that makes the chart a lazy item. Estimated the content height that makes the chart scrollable off-screen (220dp chart + 32dp spacer + six rows + 24dp padding ≈ 700dp), which exceeds the viewport on smaller phones and at larger font scales but not on every device — that, plus the fact that I reasoned from the Compose API contract rather than observing it on a device, is why this is Medium confidence.

**Suggested fix:** Hoist the reveal progress above the `LazyColumn`, or make it saveable, so the animation is owned by the results screen rather than by a recyclable list item.

### [c/api-or-library-misuse/mediastore-provider-test/a-hard-assert-behind-a-real-clock-latch] A MediaStore test turned a capability skip into a timing-dependent hard failure

**Location:** `app/src/androidTest/java/com/mauriciotogneri/fileexplorer/util/MediaStoreUtilProviderTest.kt:170-197`

**Severity:** Low
**Confidence:** Medium

**Defect:** `createAndScan` changed `assumeTrue("Provider did not report a path for the row", …)` into `assertTrue(…)`, evaluated after `scanned.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)` — a wall-clock wait on `MediaScannerConnection` completing on the device, sampled once. The tightening itself is deliberate and correct in intent: three classes' worth of silent skips were reporting green. What is new is that this particular class's assertion now depends on a real-clock deadline rather than on a device capability. The sibling conversions in `IntentUtilOpenFileTest.kt:126-136`, `IntentUtilPlayStoreTest.kt:112-122`, `IntentUtilOpenFileWithTest.kt:164-170` and `PermissionScreenActionsTest.kt:58-73` key on capability (no viewer, no browser, API < R), not on timing.

**Trigger:** Run the instrumentation suite on a loaded emulator where the media scan does not complete inside the timeout.

**Incorrect result:** The class fails rather than skipping — a timing-dependent red in the suite that gates releases.

**Evidence / verification:** Read both versions (`git show 9e87306d…:…/MediaStoreUtilProviderTest.kt` returns `file.absolutePath.takeIf { rowExists(it) }`, nullable, with `assumeTrue` at each call site). Confirmed it cannot redden the per-change loop: CLAUDE.md excludes instrumentation from it, so `testDebugUnitTest` and `lintDebug` are unaffected. That bounds the blast radius to the instrumentation suite, which is what keeps this at Low; the exposure is the tail, so a single green run does not disprove it, which is why confidence is Medium.

**Suggested fix:** Keep the hard assert, but poll `rowExists` for a bounded window after the latch instead of sampling once, so a slow scan costs seconds rather than a failure.

### [c/api-or-library-misuse/file-repository-test-fixture/a-write-denial-assertion-that-cannot-hold-as-root] A new unit test fails for an environment reason when the suite runs as root

**Location:** `app/src/test/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepositoryTest.kt:62-95`

**Severity:** Low
**Confidence:** Medium

**Defect:** The new test `the fixture filesystem enforces a write denial` asserts `!directory.canWrite()` after `setWritable(false, false)`. Root ignores the permission bit, so the assertion cannot hold for a process running as uid 0. It is the only test on this branch that can turn the mandated per-change unit loop red for a reason that is not the code.

**Trigger:** Run `./gradlew testDebugUnitTest` as root — a Docker CI image, or any container without a mapped user.

**Incorrect result:** The unit loop goes red with a message about the environment. This is deliberate: the test stands in for thirteen `assumeTrue` guards elsewhere (`FileRepositoryTest.kt:2069-2076`, `:2252-2255`, `PickerViewModelTest.kt:292-300`, `FileAccessTest.kt:122-126`) that would otherwise skip invisibly, and it has no `assumeTrue` escape by design. The cost is that the failure mode is environmental rather than diagnostic.

**Evidence / verification:** Read the test and the guards it substitutes for. Verified it passes in this environment (`id -u` is 1000) by running the full unit suite in a disposable copy of the branch: `testDebugUnitTest` exits 0. The residual exposure is the root case, which I could not exercise here — hence Medium confidence. The file does not exist at the baseline (`git show 9e87306d…:…/FileRepositoryTest.kt | grep "write denial"` returns nothing).

**Suggested fix:** None required if the suite is never run as root; the assertion message already names the remedy. If a root CI runner is ever used, guard the class on `!canWrite()` being honoured and fail loudly once per run rather than per test.

### [a/boundary-and-encoding-cases/storage-volume-naming/duplicate-numbering-can-reproduce-a-name-it-was-meant-to-separate] Numbering duplicate volume names can still produce two identical labels

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/data/model/StorageDevice.kt:29-42`
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/source/AndroidStorageSource.kt:40-42`, rendered at `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/picker/StorageSelectorContent.kt:56`

**Severity:** Low
**Confidence:** Low

**Defect:** `numberDuplicates` appends a 1-based number only to names that appear more than once, and never checks that the names it produces are themselves distinct. A name that already reads as `"<X> N"` is left untouched and can collide with the number assigned to a duplicate `X`.

**Trigger:** Three volumes whose framework descriptions are `["SD card", "SD card", "SD card 1"]` — two the framework describes identically, and a third carrying a vendor or user label of the numbered form.

**Incorrect result:** `["SD card 1", "SD card 2", "SD card 1"]` — two volumes under one label, so the destination picker offers no way to tell them apart.

**Evidence / verification:** Read the function and hand-evaluated it on that input. Confirmed the collision is cosmetic and cannot crash Compose: the lazy list keys on `it.path` (`StorageSelectorContent.kt:30`), and both `AndroidStorageSource.kt:35` and `StorageRepository` enforce path uniqueness, so only the label collides.
Refutation attempt: tried to construct a realistic device that produces the triple. Volume names became free-form only on this branch — the baseline drew from a closed set of two app-owned resources, so the shape was unreachable there — but a device would need three volumes with exactly that naming pattern. I could not make the trigger plausible, only concrete, which is why this is Low confidence.

**Suggested fix:** After numbering, check that the produced names are distinct, and fall back to a disambiguator that cannot collide — the volume's last path segment — where they are not.

## Summary

Findings by severity: Critical 0, High 0, Medium 4, Low 13 (17 total).
Findings by confidence: High 8, Medium 8, Low 1.

| Severity | High | Medium | Low |
| --- | --- | --- | --- |
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 2 | 2 | 0 |
| Low | 6 | 6 | 1 |
