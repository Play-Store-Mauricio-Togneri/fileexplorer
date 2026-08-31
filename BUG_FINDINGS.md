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
