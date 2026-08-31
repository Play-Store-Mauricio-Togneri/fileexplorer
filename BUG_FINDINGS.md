# Bug Findings

## Medium

### [b/contract-mismatches/text-viewer-tests/missing-delete-retains-old-failure-contract] An instrumentation test still demands the pre-branch missing-delete contract, so the connected suite is red

**Location:**
`app/src/androidTest/java/com/mauriciotogneri/fileexplorer/ui/screens/textviewer/TextViewerViewModelTest.kt:191`
Related:
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/FileAccess.kt:151` (`removePath`,
which
maps `ENOENT` to `AlreadyAbsent`),
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/textviewer/TextViewerViewModel.kt:141`
(the `delete` call whose result the screen branches on),
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:406-427` (the
small delete path that classifies the root)

**Severity:** Medium

**Confidence:** High

**Defect:** `onDeleteConfirmed_whenDeleteFails_showsAToastInsteadOfFinishing` deletes a path that
was
never created and asserts `TextViewerUiEvent.ShowToast`. Under this branch that path is no longer a
failure: `removePath` answers `AlreadyAbsent` for `ENOENT`, `delete` sorts the root into
`alreadyAbsentPaths` rather than incrementing `failedCount`, `DeleteResult.success` is therefore
true, and `TextViewerViewModel` emits `Finish`. The test is not stale in the harmless sense — it is
a
guard that now contradicts the contract the branch deliberately introduced, and it fails on every
device run.

**Trigger:** Deterministic. Run the test; no device state or race is needed.

**Evidence / verification:** The test file is unchanged on this branch —
`git diff main...HEAD -- …/androidTest/…/textviewer/TextViewerViewModelTest.kt` is empty — while the
delete contract underneath it was rewritten. Traced the whole path: `TextFilePreview.read` throws
for
the missing file, the `catch` sets `isLoading = false`, so the test's
`state.first { !it.isLoading }`
does complete and the assertion is reached rather than timing out; `FileItem.from` on a missing path
reports `isDirectory = false`, so the screen's directory guard (`TextViewerViewModel.kt:137`) does
not
fire the toast either; `deleteRecursive` returns no `failureErrno` and `anyRemoved = false`, giving
`alreadyAbsentPaths = [path]`, `failedCount = 0`, `success = true`; `TextViewerViewModel.kt:149`
emits `Finish`. Baseline: `git show main:…/FileRepository.kt` has
`delete(files) = files.all { deleteRecursive(File(it.path)) }` over `File.delete()`, which returns
false for a missing path — so the test passed on `main` and the regression is introduced here.
Refutation attempts: looked for a later guard that could still emit a toast (there is none —
`Finish`
is the only emission on the success branch), and for a device-only condition that would make
`Os.remove` answer something other than `ENOENT` for a path in the test's own temp dir (none).
Not executed: no emulator attached, so this is static, but nothing in the chain is device-dependent
beyond `Os.remove`'s errno for a non-existent path.

**Suggested fix:** Change the test to assert `Finish` for an already-absent path, matching the
contract `FolderErrorStatesTest` now asserts. If the genuine delete-failure toast still needs
instrumentation coverage, inject a `FileRepository` that reports a failure — the ViewModel already
takes one as a constructor parameter — rather than staging a missing file.

### [a/logic-errors/transfer-reporting/skip-notice-masked-by-source-delete-failure] A move that both skipped files and failed to delete an original never tells the user files were left behind

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:638`
Related:
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:646` (the
`else if` the flag shadows),
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:727` (the
directory-level skip guard),
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:842` (the leaf
source delete, which has no such guard),
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:708` (the
symlink branch, which has no such guard either)

**Severity:** Medium

**Confidence:** Medium

**Defect:** The transfer-completion handler tests `sourceDeleteFailed` first and the new skip
counters only in its `else if`, so whenever both are set the user sees only "some originals could
not be deleted" and is never told that `skippedFiles + unreadableDirectories` files never reached
the destination. The comment at the `else if` justifies the ordering with "the two no longer
overlap, as a directory held open by a skipped file does not raise that flag", but that non-overlap
holds only for a *directory's own* delete: `copyRecursive`'s guard is
`deleteAfter && !deleted(source) && skippedFiles == skippedBefore` (`:727`), while the leaf branch
(`:842`) sets `sourceDeleteFailed = true` on `RemoveOutcome.Failed` unconditionally and the symlink
branch (`:708`) does the same. The two flags are therefore independent, and `sourceDeleteFailed` is
sticky for the whole transfer.

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

**Evidence / verification:** Traced statically end to end. `FileRepository.kt:842` —
`when (deleteAndDropThumbnail(source)) { … is RemoveOutcome.Failed -> sourceDeleteFailed = true }` —
carries no skip guard, unlike the directory branch at `:727`. `FolderViewModel.kt:638-676` is a
plain `if / else if / else` over `copyProgress.sourceDeleteFailed` then
`copyProgress.skippedFiles + copyProgress.unreadableDirectories > 0`, so the second branch is
unreachable while the first is true. No other channel carries the skip count: `OperationProgress`
(`app/src/main/java/com/mauriciotogneri/fileexplorer/data/model/OperationProgress.kt`, built at
`FolderViewModel.kt:606`) holds only `mode`, `currentFile`, `copiedBytes`, `totalBytes` and
`isCancelling`, and the dialog reads no skip field.

Refutation attempts, all failed to disprove it: (a) checked whether the repository's
`copiedFiles == 0` storage probe (`FileRepository.kt:890`) catches the case — it fires only when
*nothing* copied, and here files did copy; (b) checked whether the emission-level MediaStore
handling compensates — it does not report to the user at all; (c) checked whether the two flags are
mutually exclusive by any invariant — they are not, per the leaf and symlink branches above; (d)
checked the baseline: on `main` there is no skip mechanism, an unopenable source threw out of
`copyRecursive` and the whole move failed with `error_move_failed`, so the user was told loudly. The
behaviour is introduced, not pre-existing.

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

### [a/logic-errors/file-deletion/global-structural-flag-misclassifies-later-root] A sticky operation-wide flag lets a later root that still exists be reported to MediaStore as fully deleted

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:623`
Related:
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:541` (the
flag's
declaration),
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:603` (the only
site that sets it, and never resets it),
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:923` (the
`notifyTreeDeleted` call),
`app/src/main/java/com/mauriciotogneri/fileexplorer/util/MediaStoreUtil.kt:65` (the prefix delete it
performs)

**Severity:** Medium

**Confidence:** Medium

**Defect:** The per-root classification at `:623` compares two before/after quantities, but only one
of them is a quantity. `failedFiles` is a counter, so `failedFiles == failedBefore` really does mean
"this root added no leaf failure". `structuralDeleteFailed` is a `Boolean` set to `true` at `:603`
and never reset, so `structuralDeleteFailed == structuralBefore` means "this root added no
structural
failure" **only for the first root that fails one**. Once any root has set it, `structuralBefore` is
already `true` for every subsequent root, and a later root that also fails structurally still
satisfies the equality. If that later walk removed at least one node, `:625` adds the still-existing
root to `removedRootPaths`, and `FolderViewModel` hands it to `MediaStoreUtil.notifyTreeDeleted`,
whose delete matches as a `GLOB` prefix — so the rows of everything under a directory that is still
on disk are purged, and a media provider that owns those rows may unlink the backing files with
them.

**Trigger:** Delete two or more roots through the progress path (`totalNodes >=
DELETE_PROGRESS_THRESHOLD`, `FolderViewModel.kt:852`). The first root must fail a directory or
symlink removal. A later root must remove at least one node and then fail a directory removal
without
adding a leaf-file failure — the plainest case being the later root directory itself: its children
unlink cleanly, then its own `rmdir` fails with `ENOTEMPTY` because another process created an entry
after the listing was captured, or with `EBUSY` on a mount point.

**Evidence / verification:** Read the loop at `FileRepository.kt:613-629` directly:
`structuralBefore` is captured per root but the underlying flag is monotonic, so the guard degrades
to a no-op after the first structural failure. Confirmed the flag has exactly one assignment (
`:603`,
`= true`) and no reset anywhere in the function. Confirmed the consumer:
`FolderViewModel.kt:922-927`
passes `progress.removedRootPaths` unconditionally to `notifyTreeDeleted`, which is
`removeRows(..., includeDescendants = true)` — the prefix delete whose contract, stated in the
comment at the call site, is that the root holds nothing. Refutation attempts: looked for a per-root
counter, an errno guard, a re-stat of the root before classifying it, or a caller-side existence
check — none exists; and checked whether the mirror case is equally harmful (a later root with a
structural failure and `removedNodes == removedBefore` lands in `absentRootPaths` and is only
scanned, which is benign). Baseline: on `main`, `deleteWithProgress` had no per-root paths at all
and
`FolderViewModel` gated the whole notification on
`progress.failedFiles == 0 && !progress.structuralDeleteFailed`, so a sticky flag could only
suppress, never mislabel — this path is introduced.

Confidence is Medium rather than High because the provider-side consequence is device-dependent: a
provider that rejects the Files-collection delete falls back to a scan (harmless), while one that
accepts it drops descendant rows, and only some providers unlink the file behind a removed row. The
misclassification itself is certain from the code.

**Suggested fix:** Give the structural failure the same shape as the leaf failure — a counter, or a
flag scoped to the current root and OR-ed into the operation-wide one after the comparison — so each
root is classified from its own before/after state. A re-stat of the root before adding it to
`removedRootPaths` would be a cheap belt-and-braces check given what the prefix delete does.

## Low

### [b/contract-mismatches/delete-analytics/removed-count-unit-split]

`delete_completed.removed_count` counts selected roots on one delete path and leaf files on the
other

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:985`
Related:
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:868` (the
other producer),
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/AnalyticsTracker.kt:877` (the contract
both are meant to satisfy),
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:585-598` (the
leaf-only counting the progress path reports from)

**Severity:** Low

**Confidence:** High

**Defect:** The two delete paths populate the same event's `removed_count` and
`already_absent_count` with different units. The small path passes `result.removedPaths.size` /
`result.alreadyAbsentPaths.size`, which are *selected roots*. The progress path passes
`progress.deletedFiles - progress.alreadyAbsentFiles` / `progress.alreadyAbsentFiles`, which are
*leaf files across the whole tree*. `trackDeleteCompleted`'s own KDoc, written in this change set,
states the contract as "@param removedCount how many of [itemCount] this app actually took off
disk" — a bound the progress path breaks in both directions, since `removed_count` there routinely
exceeds `item_count` and, for a selection of empty directories, reports 0 for roots that were
removed.

The result is that `delete_completed` with `source="folder"` is a bimodal series no query can
separate: nothing on the event says which path produced a given row.

**Trigger:** Deterministic, decided by `totalNodes < DELETE_PROGRESS_THRESHOLD` (= 10) at
`FolderViewModel.kt:852`. Deleting three loose files emits `item_count=3, removed_count=3`. Deleting
one folder containing 30 files emits `item_count=1, removed_count=30`. Deleting ten empty
directories
emits `item_count=10, removed_count=0`, because an empty directory contributes no leaf.

**Evidence / verification:** Both call sites read directly; `DeleteResult.removedPaths` is built in
`FileRepository.delete` (`:411-427`) by appending `item.path` per selected root, while
`DeleteProgress.deletedFiles`/`alreadyAbsentFiles` are incremented in
`deleteRecursiveWithProgress` (`:585-598`) only under `if (!isDirectory && !isSymlink)` — leaf
files, matching `totalFileCount`. Refutation attempt: looked for a reading under which the two
agree, and for a doc or test pinning the progress path to file units — `FolderViewModelTest` asserts
the values passed but never the unit contract, and no comment reconciles the two. Baseline:
`git show main:…/AnalyticsTracker.kt` has `trackDeleteCompleted(itemCount, source)` with neither
parameter, so the mismatch is introduced. Verified as compiling and passing: `testDebugUnitTest`
exit 0, `lintDebug` exit 0. Found independently by a second review, which contributed the
empty-directory case above.

**Suggested fix:** Pick one unit and make both paths speak it. Selected roots is the unit
`item_count` already uses and the one the KDoc asserts, so the progress path should carry root
counts — `DeleteProgress` already exposes exactly those as `removedRootPaths` and `absentRootPaths`.
If leaf-file counts are wanted instead, they belong in separately named parameters so the two
quantities are never mixed in one field.

### [a/error-handling/file-transfer/source-close-masks-destination-failure] Closing the source can replace the classified destination failure with a generic one

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:766-771`
Related:
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:977-985`
(`createDestinationFile`, which raises the classified exceptions),
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:691` and
`:701` (the two catches that would be missed),
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:731` (the
generic catch that would receive it instead)

**Severity:** Low

**Confidence:** Medium

**Defect:** The source stream is opened before the destination is reserved, and the reservation's
`catch (e: Throwable)` closes it with a bare `input.close()` before rethrowing. A `close(2)` that
fails — the case the comment two lines above explicitly anticipates, "a volume going away under an
open descriptor fails at `close(2)`" — throws out of the `catch` block itself, and Kotlin propagates
that exception in place of `e`. The `InsufficientStorageException` or
`DestinationNotWritableException`
is lost, so `FolderViewModel` misses both of its actionable catches: the user gets the generic
"copy/move failed" toast instead of "not enough space", and an environmental close failure is
reported to Crashlytics through the generic `ErrorReporter.error` at `:735` as if it were an app
defect.

**Trigger:** Destination creation must fail after the source opens, and closing the source must also
fail — a removable or failing volume disappearing during the reservation satisfies both at once,
which is exactly the scenario the classified exceptions exist for.

**Evidence / verification:** Read the block: the close at `:769` is inside the `catch` and guarded
by
neither `use`, `runCatching`, nor `addSuppressed`, so replacement is direct Kotlin semantics. Traced
the outer handling: the repository's own wrapping `try` starts *below* this line (per the comment at
`:761-765`), so the substituted `IOException` reaches the ViewModel unclassified and lands in the
generic catch at `FolderViewModel.kt:731`. Checked the one structurally similar site —
`compressFiles`
at `:1116-1130` opens the source the same way but goes straight into `input.use {}`, so it has no
unguarded close and is not affected. Baseline: on `main` the destination was reserved before the
source was opened, so there was no open stream whose cleanup could mask a reservation failure — the
window is introduced by this branch's reordering. Confidence is Medium because both halves must fail
in the same instant, which is uncommon; the mechanism itself is certain. Not asserted: a privacy
leak — Android's close error is errno-derived and was not shown to carry the source path.

**Suggested fix:** Close the source without letting its failure escape —
`runCatching { input.close() }`,
or attach it with `e.addSuppressed(...)` — and rethrow the original classified exception either way,
so the ViewModel's actionable catches still fire.