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