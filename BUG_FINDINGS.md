# Bug Findings

## Medium

### [a/logic-errors/transfer-reporting/skip-notice-masked-by-source-delete-failure] A move that both skipped files and failed to delete an original never tells the user files were left behind

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:638`
Related:
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:646` (the
`else if` the flag shadows),
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:727` (the
directory-level skip guard),
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:839` (the leaf
source delete, which has no such guard)

**Severity:** Medium

**Confidence:** Medium

**Defect:** The transfer-completion handler tests `sourceDeleteFailed` first and the new skip
counters only in its `else if`, so whenever both are set the user sees only "some originals could
not be deleted" and is never told that `skippedFiles + unreadableDirectories` files never reached
the destination. The comment at the `else if` justifies the ordering with "the two no longer
overlap, as a directory held open by a skipped file does not raise that flag", but that non-overlap
holds only for a *directory's own* delete: `copyRecursive`'s guard is
`deleteAfter && !deleted(source) && skippedFiles == skippedBefore`, and the leaf branch below it
sets `sourceDeleteFailed = true` on `RemoveOutcome.Failed` unconditionally. The two flags are
therefore independent, and `sourceDeleteFailed` is sticky for the whole transfer.

The analytics event is lost the same way: the branch reports
`trackOperationFailed(action, "source_delete_failed")` with no `errno`, so the `skippedErrno` this
change set added specifically to validate `isStorageUnavailable`'s errno set never reaches the
dashboard for these transfers.

**Trigger:** A move whose selection contains at least one file the walk cannot open *and* at least
one copied file whose source unlink fails. Both are the cases this change set was written for: the
skip is the documented `Android/data` / `Android/obb` denial on Android 11+, and the source-delete
failure is the documented read-only or permission-denied original. A single source directory holding
one denied file and one movable file whose unlink returns `EPERM`/`EROFS` is enough — the two do not
need separate roots.

**Evidence / verification:** Traced statically end to end. `FileRepository.kt:839` —
`when (deleteAndDropThumbnail(source)) { … is RemoveOutcome.Failed -> sourceDeleteFailed = true }` —
carries no skip guard, unlike the directory branch at `:727`. `FolderViewModel.kt:638-676` is a
plain `if / else if / else` over `copyProgress.sourceDeleteFailed` then
`copyProgress.skippedFiles + copyProgress.unreadableDirectories > 0`, so the second branch is
unreachable while the first is true. No other channel carries the skip count: `OperationProgress` (
`FolderViewModel.kt:604-613`) holds only `currentFile`, `copiedBytes`, `totalBytes` and
`isCancelling`, and the dialog reads no skip field.

Refutation attempts, all failed to disprove it: (a) checked whether the repository's
`copiedFiles == 0` storage probe (`FileRepository.kt:900`) catches the case — it fires only when
*nothing* copied, and here files did copy; (b) checked whether the emission-level MediaStore
handling compensates — it does not report to the user at all; (c) checked whether the two flags are
mutually exclusive by any invariant — they are not, per the leaf branch above; (d) checked the
baseline: on `main` there is no skip mechanism, an unopenable source threw out of `copyRecursive`
and the whole move failed with `error_move_failed`, so the user was told loudly. The behaviour is
introduced, not pre-existing.

Remaining assumption (the reason this is Medium rather than High confidence): I could not execute
the co-occurrence — no emulator was attached, and staging a source whose unlink fails while its
parent stays writable needs a device. Each flag's reachability is individually established from the
code and its own documentation; only their joint frequency on real devices is unmeasured.

Worth stating because it changes the impact: the skipped sources are intact (the delete is reached
only by a file that was copied), so nothing is lost by the move itself. The loss is downstream — a
user told only that the originals could not be removed may delete the source folder by hand, and
`Os.remove` needs write permission on the parent, not read permission on the file, so the denied
files this walk skipped do unlink and are gone.

**Suggested fix:** Report both conditions rather than letting one shadow the other. Either test the
skip counters before `sourceDeleteFailed` and fold the delete failure into that message, or emit a
single event carrying both quantities so the toast can say how much arrived, how much was skipped,
and that some originals remain. Whichever order is chosen, pass `copyProgress.skippedErrno` to
`trackOperationFailed` on the `source_delete_failed` branch too, and correct the `else if` comment,
which asserts a non-overlap the code does not enforce.

## Low

### [b/contract-mismatches/delete-analytics/removed-count-unit-split]
`delete_completed.removed_count` counts selected roots on one delete path and leaf files on the other

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:987`
Related:
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:869` (the
other producer),
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/AnalyticsTracker.kt:870` (the contract
both are meant to satisfy)

**Severity:** Low

**Confidence:** High

**Defect:** The two delete paths populate the same event's `removed_count` and
`already_absent_count` with different units. The small path passes `result.removedPaths.size` /
`result.alreadyAbsentPaths.size`, which are *selected roots*. The progress path passes
`progress.deletedFiles - progress.alreadyAbsentFiles` / `progress.alreadyAbsentFiles`, which are
*leaf files across the whole tree*. `trackDeleteCompleted`'s own KDoc, written in this change set,
states the contract as "@param removedCount how many of [itemCount] this app actually took off
disk" — a bound the progress path breaks, since `removed_count` there routinely exceeds
`item_count`.

The result is that `delete_completed` with `source="folder"` is a bimodal series no query can
separate: nothing on the event says which path produced a given row.

**Trigger:** Deterministic, decided by `totalNodes < DELETE_PROGRESS_THRESHOLD` (= 10) at
`FolderViewModel.kt:852`. Deleting three loose files emits `item_count=3, removed_count=3`. Deleting
one folder containing 30 files emits `item_count=1, removed_count=30`.

**Evidence / verification:** Both call sites read directly; `DeleteResult.removedPaths` is built in
`FileRepository.delete` (`:414-425`) by appending `item.path` per selected root, while
`DeleteProgress.deletedFiles`/`alreadyAbsentFiles` are incremented in
`deleteRecursiveWithProgress` (`:589-596`) only under `if (!isDirectory && !isSymlink)` — leaf
files, matching `totalFileCount`. Refutation attempt: looked for a reading under which the two
agree, and for a doc or test pinning the progress path to file units — `FolderViewModelTest` asserts
the values passed but never the unit contract, and no comment reconciles the two. Baseline:
`git show main:…/AnalyticsTracker.kt` has `trackDeleteCompleted(itemCount, source)` with neither
parameter, so the mismatch is introduced. Verified as compiling and passing: `testDebugUnitTest`
exit 0, `lintDebug` exit 0.

**Suggested fix:** Pick one unit and make both paths speak it. Selected roots is the unit
`item_count` already uses and the one the KDoc asserts, so the progress path should carry root
counts — `DeleteProgress` already exposes exactly those as `removedRootPaths` and `absentRootPaths`.
If leaf-file counts are wanted instead, they belong in separately named parameters so the two
quantities are never mixed in one field.

### [b/contract-mismatches/storage-availability/inverted-jvm-stub-contract]
`storageAnswersAt`'s KDoc states the opposite of what the function returns under the unit-test
`android.jar`

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/StorageAvailability.kt:25`

**Severity:** Low

**Confidence:** High

**Defect:** The KDoc justifies extracting `storageAnswersAt` into its own function with: "Under the
unit-test `android.jar` this constructor neither stats anything nor fails, so a test that went
through it would silently always see an available volume — `FileRepositoryTest` stubs this function
rather than trusting that." The stated JVM behaviour is inverted.
`testOptions.unitTests.isReturnDefaultValues` is set nowhere in the build, so a stubbed `android.*`
constructor throws `RuntimeException("Method … not mocked")`; `storageAnswersAt` catches
`Exception`, which that is, and returns **false**. Unstubbed on the JVM the function reports the
volume as *gone*, not as available.

This matters because it is the contract a future test author will act on: a test of the
`copiedFiles == 0 && (skippedFiles > 0 || unreadableDirectories > 0)` branch that forgets the stub
takes the `FileTransferIOException` path — the failure branch, which on `compressFiles` also deletes
the archive — while the doc promises the success branch. The doc points the diagnosis at the wrong
half of the condition.

**Trigger:** Any JVM unit test that reaches `FileRepository.storageStillAnswers` without
`mockkStatic`-ing `storageAnswersAt`.

**Evidence / verification:** `grep -rn returnDefaultValues` over `*.kts`/`*.gradle` returns nothing;
`app/build.gradle.kts:75-89` configures `unitTests.all` for `useJUnitPlatform()` and task inputs
only. The same change set contradicts itself: `FileRepositoryTest.givenPlentyOfFreeSpace()` (
`:2417-2419`) comments that `StatFs` is "an android.* class that throws 'not mocked' on the JVM" and
mocks its constructor for exactly that reason, while `StorageAvailability.kt` says the same
constructor "neither stats anything nor fails". Refutation attempt: checked for a Robolectric or
unmock plugin that would give `StatFs` a real JVM implementation, and for a project-level
`testOptions` override — neither exists. The file is new in this diff, so the incorrect claim is
introduced.

**Suggested fix:** Correct the sentence to say that the stubbed constructor throws and the function
therefore answers `false` off device, and keep the conclusion — stubbing rather than trusting the
default is still right, and is in fact more necessary than the current wording implies, because the
untrue default is the failing one.

## Summary

**By severity:** Critical 0 · High 0 · Medium 1 · Low 2

**By confidence:** High 2 · Medium 1 · Low 0

| Severity | High | Medium | Low |
|----------|------|--------|-----|
| Critical | 0    | 0      | 0   |
| High     | 0    | 0      | 0   |
| Medium   | 0    | 1      | 0   |
| Low      | 2    | 0      | 0   |
