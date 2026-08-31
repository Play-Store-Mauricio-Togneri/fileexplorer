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
