# Bug Findings

## Medium

### [b/contract-mismatches/text-viewer-tests/missing-delete-retains-old-failure-contract] Instrumentation test retains the old missing-delete contract

**Location:**
`app/src/androidTest/java/com/mauriciotogneri/fileexplorer/ui/screens/textviewer/TextViewerViewModelTest.kt:191`;
related:
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/textviewer/TextViewerViewModel.kt:141`,
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/FileAccess.kt:151`

**Severity:** Medium

**Confidence:** High

**Defect:** The instrumentation test still requires deleting an already-missing text-viewer file to
emit an error toast, but the changed repository deliberately maps `ENOENT` to `AlreadyAbsent` and
returns a successful `DeleteResult`. The ViewModel now emits `Finish`, so the connected
instrumentation suite is reliably red even though the shipped behavior follows the new contract.

**Trigger:** Run
`TextViewerViewModelTest.onDeleteConfirmed_whenDeleteFails_showsAToastInsteadOfFinishing`; it
constructs a path that was never created and confirms deletion.

**Evidence / verification:** `FileItem.from(missing)` passes the non-directory guard, `removePath`
maps `ENOENT` to `AlreadyAbsent`, `failedCount` stays zero, and `TextViewerViewModel` scans the
absent path and emits `Finish`. The collector subscribes before the action, the AndroidJUnit4 test
is discoverable, and no downstream guard can emit the expected toast. A separate refutation
confirmed the path and found that the changed `FolderErrorStatesTest` already asserts the opposite
new contract. In baseline `9e87306d73491fbfb5d72fa7f4644a1dd85b4ee5`, `File.delete()` returned false
for a missing path and the ViewModel emitted `ShowToast`, so the stale assertion is introduced by
the branch's uncoordinated contract change.

**Suggested fix:** Update this test to expect `Finish` for an already-absent path, and use an
injectable failing repository if a genuine delete-failure toast still needs coverage.

### [a/logic-errors/file-deletion/global-structural-flag-misclassifies-later-root] A later failed root is reported as deleted

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:613`; related:
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:922`

**Severity:** Medium

**Confidence:** Medium

**Defect:** `deleteWithProgress` uses the sticky operation-wide `structuralDeleteFailed` Boolean as
though it were a per-root failure counter. Once an earlier selected root sets the flag, a later root
can suffer another structural deletion failure without changing it; if that later walk removed any
child, the still-existing root is added to `removedRootPaths` and sent to MediaStore's prefix
deletion.

**Trigger:** Delete at least two roots through the progress path. The first root must have a
directory or symlink removal failure; the later root must remove at least one child and then fail to
remove a directory without adding a failed leaf, such as when another process creates an entry after
the directory listing was captured.

**Evidence / verification:** At the later root, `structuralBefore` is already `true`; assigning
`true` for its structural failure leaves the equality test at line 623 satisfied.
`removedNodes > removedBefore` then adds the live root at line 625, and `FolderViewModel` passes
every such root to `notifyTreeDeleted`, whose prefix contract requires a fully deleted tree. A
separate refutation found no per-root counter, errno guard, existence check, or caller-side success
gate. In baseline `9e87306d73491fbfb5d72fa7f4644a1dd85b4ee5`, MediaStore notification was suppressed
for the entire operation whenever the global structural flag was set, so this live-root notification
path did not exist. The exact provider-side effect is device-dependent, which limits confidence, but
it can range from stale or missing MediaStore entries to removal of a surviving backing file.

**Suggested fix:** Track structural failures with a counter or root-local flag and classify each
root from that root's own before/after state before adding it to `removedRootPaths`.

## Low

### [a/error-handling/file-transfer/source-close-masks-destination-failure] Source cleanup masks the destination failure

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:766`; related:
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:691`

**Severity:** Low

**Confidence:** Medium

**Defect:** After opening the source, a destination-reservation failure enters a catch that calls
`input.close()` directly. If that close also throws, its exception replaces the original
`InsufficientStorageException` or `DestinationNotWritableException`; the ViewModel misses its
actionable catch, shows a generic failure, and reports an environmental close error as an unexpected
defect.

**Trigger:** Destination creation must fail after the source opens, and closing the source must also
fail, for example while removable or failing storage disappears during reservation.

**Evidence / verification:** The close at line 769 is not guarded by `use`, `runCatching`, or
suppression, so Kotlin exception replacement is direct. The repository's outer catch rethrows it
unchanged and the ViewModel's generic catch receives it. A separate refutation confirmed that stream
close may throw but found the simultaneous trigger uncommon; it did not establish that Android's
close error contains the source path, so no privacy leak is asserted here. The baseline reserved the
destination before opening the source and therefore had no open stream whose cleanup could mask a
reservation failure.

**Suggested fix:** Preserve and rethrow the original classified destination exception when closing
the source fails, without attaching an unsanitized close throwable to an exception that may be
reported.

### [b/contract-mismatches/analytics/delete-completion-count-unit-mismatch] Large-delete completion mixes root and leaf counts

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:985`;
related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/AnalyticsTracker.kt:870`,
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:585`

**Severity:** Low

**Confidence:** High

**Defect:** Large deletes emit `item_count` in selected-root units but populate `removed_count` and
`already_absent_count` from leaf-file progress counters. The new subcounts therefore are not subsets
of `item_count`, unlike the tracker contract and the small-delete path, corrupting delete-completion
telemetry.

**Trigger:** Delete a directory selection whose node count reaches the progress threshold. One
selected directory containing 100 files can emit `item_count=1` with `removed_count=100`; removing
an empty directory can emit `removed_count=0` even though the selected root was removed.

**Evidence / verification:** `DeleteProgress` explicitly counts only leaf files in `deletedFiles`
and `alreadyAbsentFiles`, while `itemCount` is captured from `files.size`.
`AnalyticsTracker.trackDeleteCompleted` defines both new counts as portions of `itemCount`, and the
small-delete branch correctly uses the root-level result lists. Existing `FolderViewModelTest`
coverage pins the mismatched values rather than normalizing them. Two independent refutation passes
found no threshold invariant, downstream normalization, or alternate contract that reconciles the
units. The baseline event had no `removed_count` or `already_absent_count`, so the inconsistency is
introduced by this branch.

**Suggested fix:** Populate the completion event from `removedRootPaths.size` and
`absentRootPaths.size`, matching `itemCount` and the small-delete branch.

## Summary

- Findings by severity: Medium 2, Low 2.
- Findings by confidence: High 2, Medium 2, Low 0.

| Severity | High | Medium | Low |
|----------|-----:|-------:|----:|
| Medium   |    1 |      1 |   0 |
| Low      |    1 |      1 |   0 |
