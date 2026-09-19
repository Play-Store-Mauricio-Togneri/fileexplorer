## High

### [b/security-defects/analyzer-category/stale-path-recursive-delete] Stale analyzer rows can delete a replacement directory

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/analyzercategory/AnalyzerCategoryViewModel.kt:277` (
related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:462`,
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepository.kt:501`)

**Severity:** High

**Confidence:** High

**Defect:** Analyzer results retain a path and old size, but deletion passes that stale path to
`FileRepository.delete`, which decides recursively from the object currently occupying the path. If
a scanned file is replaced by a directory, confirming deletion of the old file row recursively
removes the replacement directory and all of its contents. A missing path on an ejected volume is
also accepted as already absent, so the held result and chart claim that the file was deleted even
though it returns with the volume.

**Trigger:** Complete a scan, load a category row, replace that file path with a non-empty directory
before confirming deletion, or eject its removable volume before confirming.

**Evidence / verification:** `AnalyzerCategoryViewModel.onDeleteConfirmed` forwards the previously
loaded `FileItem` without a live type, identity, or mounted-root check.
`FileRepository.deleteRecursive` ignores the captured type and follows the live `File.isDirectory`
result before removing descendants. Rebuilding a `FileItem` during page loading does not prevent
drift after the page was loaded, and the confirmation dialog shows only the old name/count. The
analyzer category feature and this stale caller do not exist in baseline
`9e87306d73491fbfb5d72fa7f4644a1dd85b4ee5`; the shared recursive delete therefore becomes newly
reachable through this unsafe path.

**Suggested fix:** Immediately before deletion, verify that the volume is mounted and that each path
still identifies the same non-directory object scanned originally. Reject file-to-directory or
identity drift instead of passing it to recursive deletion.

### [a/logic-errors/storage-analyzer/unreadable-root-reported-complete] Unreadable storage is reported as a completed scan

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/AnalyzerRepository.kt:125` (
related: `app/src/main/java/com/mauriciotogneri/fileexplorer/data/util/StorageAvailability.kt:13`,
`app/src/main/java/com/mauriciotogneri/fileexplorer/activities/AnalyzerActivity.kt:26`)

**Severity:** High

**Confidence:** Medium

**Defect:** A null directory listing is skipped, and the scan is rejected only when `StatFs(root)`
throws. A successful root stat does not prove that the selected volume is still mounted or readable,
so an unreadable root or lost subtree can produce a partial or empty tally that is presented as
complete, with all unseen used space attributed to System.

**Trigger:** Revoke All Files Access while the analyzer activity remains open, or detach removable
media during a scan while its mount point still resolves to a stattable underlying filesystem.

**Evidence / verification:** Every `list() == null` increments `unreadableDirectories` and
continues; completion then proceeds whenever `storageAnswers(rootPath)` returns true.
`StorageAvailability.kt` explicitly documents that a successful stat can answer for the filesystem
below an abandoned mount point. `AnalyzerActivity` has no resumed-state permission check, unlike
`MainActivity`. Unit tests additionally assert that a nonexistent root completes when the injected
stat probe returns true. Refutation found no volume-identity, root-readability, or permission
validation before the completing emission. The remaining uncertainty is device-specific mount and
scoped-storage behavior; the analyzer did not exist in the baseline.

**Suggested fix:** Validate current storage permission and selected-volume identity before starting
and completing a scan. Treat an unreadable root or vanished selected volume as failure while
continuing to classify only expected inaccessible subdirectories as System space.

### [b/security-defects/media-store/id-only-chunk-delete-toctou] Chunked MediaStore cleanup drops its path guard

**Location:** `app/src/main/java/com/mauriciotogneri/fileexplorer/util/MediaStoreUtil.kt:156` (
related: `app/src/main/java/com/mauriciotogneri/fileexplorer/util/MediaStoreUtil.kt:129`,
`app/src/main/java/com/mauriciotogneri/fileexplorer/util/MediaStoreUtil.kt:185`)

**Severity:** High

**Confidence:** Medium

**Defect:** Tree cleanup first queries row IDs with the deleted-tree path predicate, then issues a
separate destructive request constrained only by `_id IN (...)`. A matching row moved outside the
tree between those calls retains its ID and is still deleted; MediaStore deletion can unlink the
file now represented by that row outside the requested tree.

**Trigger:** `notifyTreeDeleted` runs while another app or media-provider operation moves or renames
a matching row after `idsMatching` returns and before `ContentResolver.delete` evaluates its
selection.

**Evidence / verification:** The query and delete are separate provider calls with no transaction or
revalidation spanning them, and the destructive selection contains no `DATA` constraint. Callers
reach this path after recursive tree deletion from folder, search, home, analyzer, and archive
rollback flows. Refutation through caller ordering narrows the race but does not prevent row
relocation or reuse. Baseline `9e87306d73491fbfb5d72fa7f4644a1dd85b4ee5` passed
`DATA=? OR DATA GLOB ?` directly to one provider delete, so it did not introduce this app-side
query/delete gap.

**Suggested fix:** Keep the original exact/prefix path predicate in every chunk delete, for example
`(_id IN (...)) AND (DATA=? OR DATA GLOB ?)`, then requery for the next chunk.

## Medium

### [b/contract-mismatches/storage-analyzer/logical-bytes-mixed-with-allocated-usage] Analyzer mixes logical sizes with allocated usage

**Location:**
`app/src/main/java/com/mauriciotogneri/fileexplorer/data/repository/AnalyzerRepository.kt:146` (
related:
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/analyzer/AnalyzerViewModel.kt:137`,
`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/analyzer/StorageDonutChart.kt:93`)

**Severity:** Medium

**Confidence:** High

**Defect:** Category totals sum `File.length` apparent bytes, while the denominator is `StatFs`
allocated usage. Sparse files, hard links, allocation rounding, and files changing during the scan
break the assumed equality; category rows can exceed the used headline and chart arcs can overdraw.
After a category deletion, the view model refreshes storage stats but ignores the refreshed used
value and subtracts the stale scanned apparent length from the old allocated value, which can
materially misstate remaining usage.

**Trigger:** Scan a volume containing sparse or hard-linked files, mutate a listed file during or
after the scan, or delete files whose apparent length differs materially from allocated blocks.

**Evidence / verification:** `AnalyzerRepository` adds `file.length()` to category totals.
`AnalyzerViewModel` derives `usedBytes` from `totalBytes - availableBytes`, floors only the System
remainder, and clamps each category independently; `StorageDonutChart` then accumulates every
independent sweep without normalization. On deletion, refreshed `StorageDevice` values update the
cards, but chart `usedBytes` remains `state.usedBytes - freedBytes`. Refutation found no invariant
equating apparent and allocated bytes. The analyzer did not exist in the baseline.

**Suggested fix:** Keep category and volume totals in one stated unit, normalize chart slices when
totals diverge, and derive post-delete used space from refreshed stats for the selected volume
rather than subtracting stale apparent sizes.