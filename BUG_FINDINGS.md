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
