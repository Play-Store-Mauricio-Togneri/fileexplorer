# Plan 1 — Delete failures ("folder not empty" / "in use")

Source: `todo.md` row 1. Branch: `release/2.8.0`. Paths below are relative to
`app/src/main/java/com/mauriciotogneri/fileexplorer/` unless they start with `app/`.

---

## 1. Problem

People delete a folder and the app says it failed, mostly with "Some items inside could not be
deleted", sometimes "This item is in use". In the usual case the app never even tried the items
that stopped the delete. Android 11+ blocks every app from `Android/data` and `Android/obb`
(All Files Access included). The delete walk can't list those folders, takes them for empty, then
asks the system to remove a folder that is not empty, and maybe is a mount point. The message
blames items the user can't see and can't do anything about. Retrying never helps.

Goal: the walk notices what Android hides or protects. It leaves those folders alone and tells
the user why. The analytics then say enough to confirm the cause on Android 15/16.

---

## 2. Verified cause

Every claim in the todo row, checked against `release/2.8.0` (HEAD `6f1b8f06`).

| # | Claim | Verdict | Evidence |
|---|-------|---------|----------|
| 1 | Both delete walks call the child walker without the "listing failed" callback | **Confirmed** | `data/repository/FileRepository.kt:530` (`deleteRecursive`), `:672` (`deleteWithProgress`) |
| 2 | The callback's default does nothing | **Confirmed** | `FileRepository.kt:1739-1747` — `onListingFailed: () -> Unit = {}`, `list() ?: return onListingFailed()` |
| 3 | The folder's own removal is still attempted, so it fails "not empty" and that is the first error | **Confirmed** | `FileRepository.kt:545-561` and `:686-697`: removal is unconditional; the walk is depth-first and the first errno wins (`:533-535`, `:695-697`). An unlisted folder has no children to fail first, so its own errno is the first one |
| 4 | The code's own comment admits it | **Confirmed** | `data/util/DeleteFailure.kt:27-33` (KDoc on `NOT_EMPTY`) |
| 5 | The copy walk already counts these folders | **Confirmed** | `FileRepository.kt:843` (copy) and `:1259` (compress) pass `onListingFailed = { unreadableDirectories++ }`; the count rides on `CopyProgress.unreadableDirectories` (`:1961-1968`); `FolderViewModel.kt:693-746` adds it to "skipped" and reports `operation_failed` with outcome `partial`. Copy also checks whether the volume still answers after losing folders (`FileRepository.kt:1036-1050`, `data/util/StorageAvailability.kt`) |
| 6 | No retry exists | **Confirmed** | No retry anywhere in either walk or in any of the 8 delete callers |
| 7 | User is told "Some items inside could not be deleted" | **Confirmed, with nuance** | `app/src/main/res/values/strings.xml:68`. Shown only when *no* selected item was fully removed (`FolderViewModel.kt:991-998`, `:1144-1153`). When some were, the user gets the count plural instead, "Deleted N items, M failed" (`strings.xml:72-75`, `FolderScreen.kt:204-212`) |
| 8 | Nothing in either walk checks for mount points | **Confirmed** | No `Os.stat`/`st_dev`, no path check, in `deleteRecursive`, `deleteWithProgress`, `removePath` (`data/util/FileAccess.kt:191-206`) or `forEachChild` |
| 9 | Unlistable folders are `Android/data` and `Android/obb` (Android 11+) | **Likely (unchanged)** | Several comments in the code say so: `DeleteFailure.kt:30`, `data/model/AnalyzerCategory.kt:37`, `data/repository/AnalyzerRepository.kt:62`. `FileRepository.kt:1264-1270` adds that on a **removable** volume `list()` *does* name `Android/data` entries and only opening them is denied. Still to prove on a device (§6.2) |
| 10 | "Busy" is the kernel refusing to remove those folders' mount points on 11–14 | **Likely (unchanged)** | Not provable from code. Settled by the Proof run (§6.2) |
| 11 | Nothing stops a selection from including them | **Confirmed, scope wider than stated** | `FolderViewModel.kt:931-951` doesn't filter. Folders can also be deleted from Search (`ui/screens/search/SearchViewModel.kt:333-369`) and from Home favorites (`ui/screens/home/HomeViewModel.kt:866-901`). The analyzer and the three viewers use the same `FileRepository.delete` — 8 callers in all, every one reaching the same walk |
| 12 | User is told "This item is in use" | **Confirmed, same nuance as #7** | `strings.xml:69` |
| 13 | Delete code identical in 2.6, 2.7, 2.8 | **Confirmed for everything that decides a failure** | `FileRepository.kt:478-766` is byte-identical to `release/2.7.0` and to `release/2.6.0:455-743`. `FileAccess.kt` and `DeleteFailure.kt` are unchanged. `FolderViewModel` delete handling differs only in 2.8's added review-prompt success call and cancel handling, so classification is unchanged. `targetSdk`/`minSdk` are the same (37/24) in all three |
| 14 | "2.8 has no fix for this" | **Confirmed** | — |

Things the todo row doesn't mention but this plan depends on:

- **Found (affects the "Unknown").** 2.6 still collected analytics on Firebase Test Lab devices
  (`release/2.6.0:data/util/AnalyticsTracker.kt` gates on `DEBUG || isEmulator()` only). 2.7
  added `isTestLab` (`release/2.7.0:data/util/TelemetryCollection.kt:19`). The device and Android
  version mix therefore differs between 2.6 and 2.7 data. "Android 12 and 15 rose on 2.7" may be
  a change in who is counted rather than in the code. §6.4 checks this with per-version rates.
- **Found (affects the design).** The app declares `REQUEST_INSTALL_PACKAGES` (`AndroidManifest.xml:19`).
  Android's storage docs say apps holding it may reach other apps' `Android/obb` on 11+
  (**assumption — verify in §6.2**). If so, deleting inside `Android/obb` works for users who
  allowed "Install unknown apps", and a rule that skips `Android/obb` entirely would take that away.
- **Found (reading trap).** On the walked delete (10+ nodes), `error_type` holds the *shape*
  (`partial` / `all_failed` / `structural`, `FolderViewModel.kt:1088-1156`). On the one-call delete it
  holds the *cause* label. "Not empty" counts must be read from `errno` 39, as
  `.claude/skills/analyze-analytics/SKILL.md:168-176` already says.
- **Found (separate bug, not fixed here).** The move walk has the same blind spot.
  `FileRepository.kt:854` flags "source delete failed" for an unlistable source folder, because
  the guard compares only `skippedFiles`, not `unreadableDirectories`. Moving a folder that holds
  `Android/data` therefore says "some originals could not be deleted" as well as "skipped". See §8 Q6.

---

## 3. Approach

### 3.1 How to recognise a protected system folder

| # | Option | Pros | Cons |
|---|--------|------|------|
| 1 | **Path match**: `<volume root>/Android/data` or `<volume root>/Android/obb`, on API 30+ only | No extra system calls (string check on directories, cheap name test first). Runs and tests on the JVM with the Android version passed in (the `PdfViewerSupport.mode(sdkInt, …)` pattern). Also catches folders Android hides **without** a mount point: removable volumes, and possibly Android 15/16 (the "not empty" cluster). Same path-shape approach the app already uses (`ui/components/BreadcrumbPathParser.kt:5`) | Knows only those two folders. Misses alias paths (`/sdcard/...`) and any other mount point |
| 2 | **Device-ID compare**: `Os.lstat(dir).st_dev != Os.lstat(parent).st_dev` before removing | Catches any mount point, including unknown ones | One more system call per directory, each a round trip to the storage daemon. `android.system.Os` throws in JVM tests, so it needs a new constructor seam and the core logic is testable only on a device. **Misses hidden folders that are not mount points**, which is the likely 15/16 case. `/storage/emulated/0` and `/storage/emulated` share a device ID under the storage daemon, so it can't find volume roots either |
| 3 | Both | Widest net | Cost and test burden of 2 for a gain nobody has measured |

**Recommendation: option 1, backed by the "listing failed" callback as the catch-all.** A
hidden folder the path rule misses (an alias path, an unknown OEM folder) still gets the honest
"can't see inside" message instead of "in use". The new `failed_listing` / `failed_location`
analytics show whether any such folders exist. Volume-root shapes covered:
`/storage/emulated/<n>` (any user/work profile), `/storage/self/primary`, `/storage/<id>`
(SD/USB). No storage-access-framework walk exists (`DocumentFile`/`DocumentsContract` aren't
used anywhere), so file paths are the only input. Gated to API 30+ so Android 10 and below,
where `requestLegacyExternalStorage` makes these folders deletable, keep working exactly as today
(see §8 Q2).

### 3.2 What the walk does with a protected folder

| # | Option | Effect |
|---|--------|--------|
| 1 | Skip it entirely: no listing, no removal | Simplest. Takes away deleting inside `Android/obb` if the app can reach it (§2 finding) |
| 2 | **Walk its contents as usual if Android lets the app list them; never try to remove the folder itself** | Keeps every delete Android allows. When the listing fails (the expected case) it does the same as option 1. Failures inside count as "protected by Android", not as visible failures |

**Recommendation: option 2.** It removes only the step that can never succeed. Progress totals
need no change, because the walk still visits what the pre-count counted.

### 3.3 Where to block: walk, selection, or both

| # | Option | Covers | Cost |
|---|--------|--------|------|
| 1 | **In the repository walk** | All 8 delete entry points; protected folders *inside* a selection (deleting `Android` itself, which is probably the common case) | Users see the reason after confirming |
| 2 | At selection time (folder screen) | Only a direct selection of `Android/data` / `Android/obb` on one screen | Misses descendants, so it can't be the fix |
| 3 | Both | Saves one tap for direct selections | Extra code in 3 ViewModels; touches the delete flow's UI |

**Recommendation: option 1 now.** A direct selection already gets the right message after the
confirm dialog. The selection-time block is §8 Q3.

### 3.4 Result type and message choice

Each walk works out, for every node it couldn't remove, whether Android explains it:

- **protected**: the node is a protected folder (never removed), or sits inside one;
- **hidden**: the node's own listing failed and its removal then failed;
- **explained ancestor**: a folder whose subtree holds protected or hidden content, so its
  removal could never succeed;
- everything else is a **visible failure**, as today.

Exception: when the failing errno points at the whole volume ("storage unavailable" or
"read-only"), the failure is never explained away. A volume that drops out mid-walk then keeps
its true message.

The message is decided once, in `DeleteFailure.kt`, by precedence:

1. a visible failure exists: today's errno-based message (`deleteFailureFor(errno)`);
2. otherwise, any protected folder: new **"system folder"** message;
3. otherwise, any hidden folder: new **"hidden items"** message.

Visible failures rank first because only they are about items the user can see. They are also
what today's messages describe correctly.

**Mixed selections and plurals** — options:

| # | Option | Strings to add |
|---|--------|----------------|
| 1 | **When everything left over is by Android's design, show the reason string (no counts), whether part of the selection went or none of it. When a visible failure exists, keep today's behaviour (the count plural for partial, the errno message otherwise)** | 2 strings × 20 languages, 0 plurals |
| 2 | New plurals carrying counts *and* the reason, e.g. "Deleted %1$d items. %2$d kept: Android protects system folders" | 2 strings + 2 plurals × 20 languages (each plural with every quantity: ar 6, ru 4, ro/fr/es/pt 3, …), new event fields, and changes in 2 screens |

**Recommendation: option 1.** The count plural says "failed", which is exactly the misleading
wording being removed. The list reloads straight after, so the user sees what went. It also
needs no composable changes.

### 3.5 Review prompt

- A clean delete: unchanged (`ReviewPrompt.onActionSucceeded()`).
- Anything left behind by design is not a clean success. AGENTS.md says partial success never
  asks for a rating. The new delete-failure event calls `ReviewPrompt.onActionFailed()`, the way
  every partial result already does through `trackOperationFailed` (copy partial,
  `FolderViewModel.kt:715-735`). One edge cost: someone who emptied `Android/obb` through
  installer access still sees "kept" and no rating ask that session. That's acceptable, because
  the folder itself did stay.
- The three viewers send no delete analytics and call `onActionFailed()` directly
  (`ImageViewerViewModel.kt:146-152`). No change there.

### 3.6 Analytics

Today's `operation_failed` for deletes sends `operation`, `error_type`, `errno`, `source`,
`outcome` (`data/util/AnalyticsTracker.kt:1078-1094`). Add five params, sent only on deletes that
reached the walk's result:

| # | Param | Type / values | Meaning |
|---|-------|---------------|---------|
| 1 | `cause` | `permission_denied`, `read_only`, `not_empty`, `busy`, `storage_unavailable`, `errno_other`, `unknown`, **`system_folder`**, **`hidden_contents`** | The cause behind the message the user saw, from the same `DeleteFailure` label. Sent on **both** delete paths, so one query covers both (the walked path keeps the shape in `error_type` so existing dashboards keep working) |
| 2 | `system_folders` | `0`, `1`, `2`, `3+` | Protected folders the walk left in place |
| 3 | `unlisted_folders` | `0`, `1`, `2`, `3+` | Folders it couldn't list that then stayed |
| 4 | `failed_listing` | `file`, `unlisted`, `empty`, `listed` | For the node behind `errno`: a file; a folder whose listing failed; a folder that listed **empty** yet would not go (hidden entries, the case the callback can't see); a folder with entries. Omitted when no node failed with an errno |
| 5 | `failed_location` | `android_data`, `android_obb`, `android` (the volume's `Android` folder or anything else under it), `volume_root`, `other` | Rough place of the same node. Worked out from the path on the device; only the label is sent |

- Counts are bucketed because the export is aggregated by param combination
  (`SKILL.md:9-10`); exact counts would multiply rows for no added answer.
- No names, paths or contents: every value comes from a fixed list, and the types carrying them
  are enums or integers, so a path can't reach the event (AGENTS.md "Analytics & Crash
  Reporting").
- `errno` keeps its meaning (first failure, depth-first, `SKILL.md:173`). An explained failure
  still reports its errno, so pre- and post-fix data stay comparable. `cause` says whether it
  was explained.
- Firebase limits: 25 params per event, names ≤ 40 chars, values ≤ 100 chars. This event goes
  to 10 params; the longest name is 16 chars, the longest value 19. `trackEvent` already cuts to
  the limits (`AnalyticsTracker.kt:55-78`). BigQuery receives every param, so no console custom
  dimension is needed for the analysis skill. Registering them is only for the Firebase console
  UI (limit 50 event-scoped dimensions).
- Optional diagnostics left out on purpose: a volume label (primary / secondary) and a
  mount-point flag for the failing node (one `Os.lstat` per failure). See §8 Q5.

---

## 4. Step-by-step changes

Order matters only for compiling: 1 → 2 → 3 → 4 → 5, then 6–8 in any order.

### Step 0 — run the Proof before coding (no code change)

§6.2 on the **current** debug build. It decides two open points: whether `Android/obb` is
reachable with "Install unknown apps" allowed (§3.2), and the errno each Android version gives
(§2 #10).

### Step 1 — new `data/util/SystemFolders.kt`

- `internal fun isProtectedSystemFolder(path: String, sdkInt: Int): Boolean`. True only for
  `sdkInt >= Build.VERSION_CODES.R` and a path, after `File(path).path` normalisation, equal
  to `<volume>/Android/data` or `<volume>/Android/obb`. Cheap test first (`name` is `data` or
  `obb` and the parent's name is `Android`), regex only after. Exact match only: descendants
  are not matched (inside a protected folder the walk decides by itself, §3.4). Case-sensitive.
- `enum class DeleteLocation(val analyticsLabel: String)` and
  `internal fun deleteLocationOf(path: String): DeleteLocation` for §3.6 #5.
- One shared volume-root pattern, with the same idea as `BreadcrumbPathParser`:

```kotlin
private val VOLUME_ROOT = Regex("/storage/(?:emulated/\\d+|self/primary|[^/]+)")
```

- KDoc: why API 30, why path and not device ID (§3.1), and that a miss falls back to the
  hidden-items handling.

### Step 2 — `data/util/DeleteFailure.kt`

- Add `SYSTEM_FOLDER("system_folder", R.string.delete_error_system_folder)` and
  `HIDDEN_CONTENTS("hidden_contents", R.string.delete_error_hidden_items)`. Add
  `val keptByAndroid: Boolean` (true for those two only).
- Rewrite the class KDoc ("as far as the errno says" is no longer the whole story: two causes are
  seen by the walk, not by an errno). Rewrite the `NOT_EMPTY` KDoc: it now means a folder the walk
  *could* see into that still had entries (e.g. a file written during the delete); the
  `Android/data` explanation moves to the new entries.
- Add `enum class FailedListing(val analyticsLabel: String)` with `FILE`, `UNLISTED`, `EMPTY`,
  `LISTED`.
- Add the precedence function, pure and JVM-testable:

```kotlin
fun deleteFailureFor(unexplainedErrno: Int?, systemFolders: Int, unlistedFolders: Int, firstErrno: Int?): DeleteFailure =
    when {
        unexplainedErrno != null -> deleteFailureFor(unexplainedErrno)
        systemFolders > 0 -> DeleteFailure.SYSTEM_FOLDER
        unlistedFolders > 0 -> DeleteFailure.HIDDEN_CONTENTS
        else -> deleteFailureFor(firstErrno)   // results built without the new fields behave as today
    }
```

- Add `@Immutable data class DeleteDiagnostics(cause: DeleteFailure, errno: Int?, systemFolders: Int,
  unlistedFolders: Int, failedListing: FailedListing?, failedLocation: DeleteLocation?)`. It lives
  here, not in the repository, so `AnalyticsTracker` doesn't depend on `data/repository`.

### Step 3 — `data/repository/FileRepository.kt`

1. **Constructor seam**, declared *before* `onFilesMutated` (trailing-lambda rule, `:96-97`):
   `private val isSystemFolder: (File) -> Boolean = { isProtectedSystemFolder(it.path, Build.VERSION.SDK_INT) }`.
   JVM tests read `SDK_INT` as 0 (as `isSymlink()` already does), so every existing JVM test
   keeps today's behaviour. New tests inject the rule.
2. **`deleteRecursive`** (`:524-562`): add `insideSystemFolder: Boolean = false`. Per node:
   - `protected = isSystemFolder(file)`. Path only, no system call, so it works even if
     `isDirectory` can't stat the folder.
   - For a directory: `forEachChild(onListingFailed = { listing = UNLISTED })`, with `listing`
     starting at `EMPTY` and set to `LISTED` on the first child; children get
     `insideSystemFolder || protected`.
   - If `protected`: return without removing; `systemFolders += 1`.
   - Otherwise remove as today. On `Failed(errno)`: work out `explained` (§3.4: inside a system
     folder, or own listing failed, or subtree kept something, and the errno doesn't classify
     as `STORAGE_UNAVAILABLE`/`READ_ONLY`). If unlisted and explained and not inside a system
     folder: `unlistedFolders += 1`. Record `firstErrno` / `failedListing` / `failedLocation` for
     the first failure; record `unexplainedErrno` for the first unexplained failure.
   - `TreeOutcome` (`:575-579`) gains `unexplainedErrno`, `systemFolders`, `unlistedFolders`,
     `failedListing`, `failedLocation`; `failureErrno` keeps its meaning (first errno of any failure).
3. **`delete`** (`:478-513`): a root is failed when `failureErrno != null || systemFolders > 0`.
   This matters: a protected root makes no system call, so it has no errno and would otherwise
   be filed as already absent. Sum the new fields into `DeleteResult`.
4. **`removedTree`** (`:591-595`, extraction rollback): add `&& outcome.systemFolders == 0`. A tree
   holding a folder the walk didn't remove was not emptied.
5. **`deleteWithProgress`** (`:642-766`): the same rules, in the function's own counter style.
   Capture `systemFolders + unlistedFolders` before walking a directory's children and compare
   after. A protected folder walks its children, skips its own removal and increments
   `structuralFailures`, so the existing per-root bookkeeping (`:738-747`) keeps it out of both
   root lists. Its doc ("a directory … that could not be removed") already fits. The final
   emission carries the new fields.
6. **`DeleteResult`** (`:2081-2109`) and **`DeleteProgress`** (`:2034-2072`): new fields with
   defaults (`unexplainedErrno = null`, `systemFolders = 0`, `unlistedFolders = 0`,
   `failedListing = null`, `failedLocation = null`) so the many test constructors still compile.
   Add computed `val failure: DeleteFailure` (Step 2 precedence) and
   `val diagnostics: DeleteDiagnostics`. Update both KDocs.

The protected check runs per directory as a string compare, never on files. Progress totals
(`totalFileCount`) stay as they are, because §3.2 option 2 walks what they count.

### Step 4 — `data/util/AnalyticsTracker.kt`

- New `fun trackDeleteFailed(errorType: String, outcome: String, source: String, diagnostics: DeleteDiagnostics)`.
  It calls `ReviewPrompt.onActionFailed()` and logs `operation_failed` with `operation=delete`,
  the existing four params (`errno` through `reportableErrno`) and §3.6's five.
- Build the map in an `internal fun deleteFailureParams(...)` so a JVM test can check buckets and
  omissions without Firebase; it shares the `operation_failed` map with `trackOperationFailed`
  instead of copying it.
- Leave `trackOperationFailed`'s signature alone: copy/move/rename/compress/uncompress don't
  change, and delete failures without a walk result (`exception`, `path_type_changed`) stay on it.
- Add the new function to the "every failure event calls `ReviewPrompt.onActionFailed()`" comment (`:1058-1061`).

Why a new function and not a sixth parameter: a clearer API, and no edits to the 8
copy/rename/uncompress stubs. Either way the existing "no failure reported" checks in tests need
updating (see Tests, final bullet).

### Step 5 — ViewModels (message and analytics)

Same rule everywhere. Message: `result.failure` / `progress.failure` in place of
`deleteFailureFor(…failureErrno)`. If `failure.keptByAndroid`, show `ShowToastRes(failure.messageResId)`
whether or not some roots were cleared. Analytics: `trackDeleteFailed(...)` in place of
`trackOperationFailed("delete", ...)`. `outcome` / `error_type` values stay as today.

| # | File | Sites | Change |
|---|------|-------|--------|
| 1 | `ui/screens/folder/FolderViewModel.kt` | `:966-999` (one call), `:1072-1080` (`reportDeleteFailure`), `:1088-1156` (`handleDeleteResult`) | Add the `keptByAndroid` branch ahead of the partial-plural branch; work out the shape (`partial`/`all_failed`/`structural`) first, then choose the message; `reportDeleteFailure` and `report()` call `trackDeleteFailed` |
| 2 | `ui/screens/analyzercategory/AnalyzerCategoryViewModel.kt` | `:339-370`, `:446-503`, `:580-588` | Same as #1. Rows are files (`:290-296`), so the new causes are rare here; changed for consistency and analytics |
| 3 | `ui/screens/search/SearchViewModel.kt` | `:357-368` | `result.failure` + `trackDeleteFailed(…, "all_failed", "search", result.diagnostics)`. Search results can be folders, and on a removable volume `Android/data` itself can appear |
| 4 | `ui/screens/home/HomeViewModel.kt` | `:734-745` (recent), `:890-901` (favorite) | Same as #3. Favorites can be folders |
| 5 | `ui/screens/imageviewer/ImageViewerViewModel.kt:152`, `ui/screens/pdfviewer/PdfViewerViewModel.kt:551`, `ui/screens/textviewer/TextViewerViewModel.kt:163` | message only | `result.failure.messageResId`. No behaviour change (single files); keeps one source of truth |

Leaving any of 1–4 unchanged would be a **regression**: a protected root has no errno, so the
old `deleteFailureFor(null)` would show the generic "Error deleting".

No composable changes. Screens already render `ShowToastRes`, and Home renders `deleteErrorResId`.

### Step 6 — strings, all 20 languages

Add after `delete_error_storage_unavailable` (`strings.xml:70`) in `values/` and in each
`values-*/strings.xml`. Escape apostrophes as `\'`. Keep `Android/data` untranslated.
`LocalizationParityTest` enforces that every language has them.

| # | Lang | `delete_error_system_folder` | `delete_error_hidden_items` |
|---|------|------------------------------|-----------------------------|
| 1 | en | Android protects system folders such as Android/data, so they were not deleted | Android doesn\'t let this app see some items inside, so they were not deleted |
| 2 | ar | يحمي نظام Android مجلدات النظام مثل Android/data، لذلك لم يتم حذفها | لا يسمح نظام Android لهذا التطبيق برؤية بعض العناصر بالداخل، لذلك لم يتم حذفها |
| 3 | bn | Android, Android/data-এর মতো সিস্টেম ফোল্ডার সুরক্ষিত রাখে, তাই সেগুলি মুছে ফেলা হয়নি | Android এই অ্যাপটিকে ভিতরের কিছু আইটেম দেখতে দেয় না, তাই সেগুলি মুছে ফেলা হয়নি |
| 4 | ca | Android protegeix les carpetes del sistema com ara Android/data, així que no s\'han eliminat | Android no permet que aquesta aplicació vegi alguns elements de dins, així que no s\'han eliminat |
| 5 | de | Android schützt Systemordner wie Android/data, daher wurden sie nicht gelöscht | Android lässt diese App einige Elemente darin nicht sehen, daher wurden sie nicht gelöscht |
| 6 | el | Το Android προστατεύει φακέλους συστήματος όπως το Android/data, οπότε δεν διαγράφηκαν | Το Android δεν επιτρέπει σε αυτήν την εφαρμογή να δει ορισμένα στοιχεία μέσα, οπότε δεν διαγράφηκαν |
| 7 | es | Android protege las carpetas del sistema como Android/data, así que no se eliminaron | Android no permite que esta aplicación vea algunos elementos de dentro, así que no se eliminaron |
| 8 | fr | Android protège les dossiers système comme Android/data, ils n\'ont donc pas été supprimés | Android ne laisse pas cette application voir certains éléments à l\'intérieur, ils n\'ont donc pas été supprimés |
| 9 | hi | Android, Android/data जैसे सिस्टम फ़ोल्डरों को सुरक्षित रखता है, इसलिए वे हटाए नहीं गए | Android इस ऐप को अंदर के कुछ आइटम देखने नहीं देता, इसलिए वे हटाए नहीं गए |
| 10 | in | Android melindungi folder sistem seperti Android/data, jadi folder tersebut tidak dihapus | Android tidak mengizinkan aplikasi ini melihat beberapa item di dalamnya, jadi item tersebut tidak dihapus |
| 11 | it | Android protegge le cartelle di sistema come Android/data, quindi non sono state eliminate | Android non consente a questa app di vedere alcuni elementi all\'interno, quindi non sono stati eliminati |
| 12 | ja | Android は Android/data などのシステムフォルダを保護しているため、削除されませんでした | Android により、このアプリは内部の一部の項目を参照できないため、削除されませんでした |
| 13 | nl | Android beschermt systeemmappen zoals Android/data, dus ze zijn niet verwijderd | Android laat deze app sommige items erin niet zien, dus ze zijn niet verwijderd |
| 14 | pt | O Android protege pastas do sistema como Android/data, por isso elas não foram excluídas | O Android não permite que este aplicativo veja alguns itens de dentro, por isso eles não foram excluídos |
| 15 | ro | Android protejează folderele de sistem precum Android/data, așa că nu au fost șterse | Android nu permite acestei aplicații să vadă unele elemente din interior, așa că nu au fost șterse |
| 16 | ru | Android защищает системные папки, такие как Android/data, поэтому они не были удалены | Android не позволяет этому приложению видеть некоторые объекты внутри, поэтому они не были удалены |
| 17 | tr | Android, Android/data gibi sistem klasörlerini korur, bu yüzden silinmediler | Android bu uygulamanın içindeki bazı öğeleri görmesine izin vermiyor, bu yüzden silinmediler |
| 18 | ur | Android سسٹم فولڈرز جیسے Android/data کی حفاظت کرتا ہے، اس لیے انہیں حذف نہیں کیا گیا | Android اس ایپ کو اندر موجود کچھ آئٹمز دیکھنے نہیں دیتا، اس لیے انہیں حذف نہیں کیا گیا |
| 19 | vi | Android bảo vệ các thư mục hệ thống như Android/data, nên chúng không bị xóa | Android không cho ứng dụng này xem một số mục bên trong, nên chúng không bị xóa |
| 20 | zh | Android 会保护 Android/data 等系统文件夹，因此未删除它们 | Android 不允许此应用查看其中的某些项目，因此未删除它们 |

No plurals are added (§3.4 option 1). Existing strings are unchanged; `delete_error_not_empty`
stays for visible not-empty failures.

### Step 7 — docs

- `AGENTS.md` "In-App Review": add `trackDeleteFailed` to the list of failure events that already
  call `onActionFailed()`.
- `.claude/skills/analyze-analytics/SKILL.md` "Reading traps" (`:168-176`): the new `error_type`
  labels `system_folder` / `hidden_contents` on the one-call path; the five new params and their
  values; "compare deletes on `cause` where present, else on `errno` + `outcome`"; in new
  versions `errno` 39 together with a `system_folder` / `hidden_contents` cause means "explained".

### Step 8 — nothing else

No badge (nothing new to point at), no Remote Config, no website change unless §8 Q7 says so.

---

## 5. Tests

JUnit 4 + mockk, as the repo does. Denials staged with `setReadable(false, false)` +
`assumeTrue(dir.list() == null)`, the existing fixture-state check
(`FileRepositoryTest.kt:2320-2329`).

### Unit tests (`app/src/test/java/com/mauriciotogneri/fileexplorer/`)

- **New `data/util/SystemFoldersTest.kt`**:
  - `isProtectedSystemFolder` is true at sdk 30 and false at 29 for
    `/storage/emulated/0/Android/data` and `/obb`;
  - it covers `/storage/emulated/10/...` (work profile), `/storage/1234-ABCD/...` (SD),
    `/storage/self/primary/...`, and a trailing slash;
  - false for `/storage/emulated/0/Android`, `.../Android/media`,
    `.../Download/Android/data` (user folder of the same name), `.../Android/data/com.foo`
    (descendant), `.../android/data` (case);
  - `deleteLocationOf` returns each label, including a descendant of `Android/data`, `Android`
    itself, a volume root, and a path off any volume.
- **New `data/util/DeleteFailureTest.kt`**: precedence of the 4-argument `deleteFailureFor`:
  - visible failure beats protected beats hidden;
  - no new fields means today's result (`deleteFailureFor(0)` → `UNKNOWN` on the JVM);
  - `keptByAndroid` is true only for the two new entries.
  - Only `null`/`ERRNO_UNKNOWN` errnos are asserted to *classify*, because the platform errno
    constants read 0 on the JVM (`DeleteFailure.kt:79-83`).
- **`data/repository/FileRepositoryTest.kt`** (inject `isSystemFolder = { it == fakeData }` on
  temp dirs):
  - `delete leaves a system folder in place and reports it`: the folder is never handed to the
    remove call; `failedCount` 1, `systemFolders` 1, `failure` `SYSTEM_FOLDER`, `failureErrno` null.
  - `delete of a parent removes everything but the system folder`: `Android/{media/x, data/y}`.
    `media` is gone; `data` and `Android` remain; `failure` is `SYSTEM_FOLDER`, not `NOT_EMPTY`.
  - `delete removes what it can list inside a system folder but not the folder`: the installer
    access case.
  - `delete counts a folder it cannot list and reports hidden contents`: `unlistedFolders` 1,
    `failedListing` `UNLISTED`, `failure` `HIDDEN_CONTENTS`.
  - `an unlisted folder that is in fact empty is removed and not counted`.
  - `a visible failure outranks hidden contents`: the remove seam fails one leaf with EROFS
    (value 30); assert `unexplainedErrno == 30`.
  - `a folder that lists empty but will not go is reported as empty-listed`: remove seam fails
    it; `failedListing` `EMPTY`.
  - `deleteWithProgress` versions of the first three: a protected root gives
    `structuralDeleteFailed`, appears in neither root list, and has `systemFolders` 1.
  - Extraction rollback: a tree holding a protected folder is not reported as removed (Step 3.4).
  - Existing tests stay untouched and must still pass. That proves the default rule is off on
    the JVM.
- **`data/util/AnalyticsTrackerReviewHookTest.kt`**: `a failed delete blocks the review prompt`
  (`trackDeleteFailed`).
- **New test for `deleteFailureParams`** (same package): buckets `0/1/2/3+`, both listing/location
  omissions when there is no errno, `operation=delete`, and every value from the fixed lists.
- **`ui/screens/folder/FolderViewModelTest.kt`**:
  - one-call delete with only a system folder left gives `ShowToastRes(delete_error_system_folder)`,
    `trackDeleteFailed("system_folder", "all_failed", "folder", …)` and no
    `onActionSucceeded`;
  - one-call partial (one root cleared plus a system folder) gives the reason toast, **not**
    `ShowDeletePartialSuccess`, with outcome `partial`;
  - a visible failure plus a system folder still gives `ShowDeletePartialSuccess`;
  - walked delete with a protected root alone gives the reason toast and error type / outcome
    `structural`;
  - walked partial with hidden contents gives the reason toast and outcome `partial`.
- **`AnalyzerCategoryViewModelTest.kt`, `SearchViewModelTest.kt`, `HomeViewModelTest.kt`**:
  - one test each that a result with `systemFolders = 1` shows `delete_error_system_folder`
    and reports through `trackDeleteFailed`;
  - switch the existing delete checks (Search `:576`, Home `:806`, `:1218`, Analyzer
    `:644/681/720`) to `trackDeleteFailed`;
  - add `every { AnalyticsTracker.trackDeleteFailed(any(), any(), any(), any()) } just Runs` to
    each class's setup.
- **Update the "no failure reported" checks**, or they pass without testing anything once
  deletes stop calling `trackOperationFailed`: `FolderViewModelTest.kt:2323` and `:2473` (the two
  that match any operation) and `HomeViewModelTest.kt:828` (delete only). Each needs a matching
  `verify(exactly = 0) { AnalyticsTracker.trackDeleteFailed(any(), any(), any(), any()) }`.
- `LocalizationParityTest` covers the 40 new string entries without changes.

### Instrumentation tests (run on `Pixel_7_API_36`, API 36; no skip there)

In `app/src/androidTest/.../data/repository/FileRepositoryDeviceTest.kt`, which already carries
the `device-required:` note:

- `delete_primaryAndroidData_isKeptAsSystemFolder`:
  - `FileRepository(removeFile = recorder)`, where the recorder **records and never deletes**,
    and the **default** system-folder rule (real `SDK_INT` 36);
  - delete a `FileItem` for `Environment.getExternalStorageDirectory()/Android/data`;
  - assert `failure == SYSTEM_FOLDER`, `failedCount == 1`, and that the recorder never received
    that folder;
  - safe even if the rule breaks, because nothing is removed.
- `delete_unlistedFolderOnAVolumeThatWentAway_keepsStorageUnavailable`:
  - in `cacheDir`, a folder with a child, `setReadable(false)` and
    `assumeTrue(dir.list() == null)` (holds on the emulator as the app's own user);
  - a remove seam that answers `Failed(OsConstants.ENOTCONN)` for that folder;
  - assert `failure == STORAGE_UNAVAILABLE`, not `HIDDEN_CONTENTS`;
  - this can only run on a device, because the errno constants are 0 on the JVM.

Not added: a test that really removes anything under `Android/` (destructive), and a test that
pins what the platform returns for `Android/data`'s listing. That is checked by hand in §6.2.

---

## 6. Verification

### 6.1 Per-change commands (AGENTS.md)

```bash
./gradlew -w --console=plain -I gradle/agent-quiet.init.gradle testDebugUnitTest  # unit tests
./gradlew -w --console=plain lintDebug                                            # Android lint
./scripts/check-tests.sh                                                          # structural guards
```

Then run the `delta-review` skill on the changed files (per CLAUDE.md). With the emulator already
up, hand the device class to the `instrumentation-runner` subagent; run one class (a
comma-separated method list runs only the first one):

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mauriciotogneri.fileexplorer.data.repository.FileRepositoryDeviceTest
```

### 6.2 Proof on Android 12, 15 and 16 (before *and* after the change)

**Emulators only. Never on a personal phone:** deleting `Android` wipes `Android/media`, where
WhatsApp and others keep their media on 11+. "A folder holding Android/" is read as the
`Android` folder itself, never the storage root.

Setup, one emulator at a time, cold-booted (memory note):

```bash
sdkmanager "system-images;android-31;google_apis;x86_64" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n Pixel_7_API_31 -k "system-images;android-31;google_apis;x86_64" -d pixel_7
avdmanager create avd -n Pixel_7_API_35 -k "system-images;android-35;google_apis;x86_64" -d pixel_7
free -h && emulator -avd Pixel_7_API_31 -no-snapshot-load     # then _35, then the existing Pixel_7_API_36
./gradlew -w --console=plain installDebug
adb shell appops set --uid com.mauriciotogneri.fileexplorer MANAGE_EXTERNAL_STORAGE allow
adb logcat -c && adb logcat -s Analytics:D                     # debug builds log every event with its params
```

On each emulator:

1. Open Internal storage → `Android`. Note how the `data` and `obb` rows look. "Restricted"
   means the listing failed (null); a count means it returned an array. Mark the cell
   null / empty / entries.
2. Delete `Android/obb` alone, then `Android/data` alone, then `Android`. Record the toast and
   the logged `errno`, `error_type`, and after the change `cause`, `system_folders`,
   `unlisted_folders`, `failed_listing`, `failed_location`.
3. Allow installs (`adb shell appops set com.mauriciotogneri.fileexplorer REQUEST_INSTALL_PACKAGES allow`),
   restart the app, repeat step 1 for `obb`. This settles §2's installer-access assumption and
   §8 Q1.
4. Optional, removable volume: `adb shell sm set-virtual-disk true`, `adb shell sm list-disks`,
   `adb shell sm partition <disk id> public`. Repeat 1–2 on the new volume's `Android`.

Expected:

- **Before the change**:
  - errno 16 (busy) on 12 and 39 (not empty) on 15/16 would confirm todo claim #10;
  - anything else rewrites it.
- **After the change**:
  - `Android/data` and `Android/obb` are never removed; the toast is the system-folder message;
  - `Android/media` and the rest of `Android` are emptied;
  - logcat shows `cause=system_folder system_folders=1|2`;
  - deleting an ordinary folder is unchanged (no toast, `delete_completed`);
  - switch the app to Arabic and Urdu once and check the toast reads correctly with the
    left-to-right `Android/data` inside it.

### 6.3 Regression spot-checks (after the change, on API 36)

- A read-only or unmounted volume still says "read-only" / "storage is no longer available" (if a
  virtual disk is at hand: start a large delete on it, then `adb shell sm unmount <volume>`
  mid-walk).
- A mixed selection with one real failure still shows "Deleted N items, M failed".

### 6.4 Reading the analytics after release (answers the "Unknown")

Use the `analyze-analytics` skill once the release has a few weeks of data. Restrict to
`operation_failed` / `operation` = `delete` rows that carry `cause` (new versions):

1. **Was it `Android/data` and `obb`?** Per `os_version`, compare the share of delete failures
   with `cause` `system_folder` in the new version against the share with `errno` 39 + 16 in
   2.7.x. Matching shapes (especially on 15/16) confirm it. The new version's overall delete
   failure rate, failures ÷ (failures + `delete_completed`), should fall by about that amount.
2. **Anything the path list misses?**
   - `cause` `hidden_contents`, broken down by `failed_location`: `android_*` points to an alias
     path or a folder to add; `other` points to something else (permissions on removable
     storage, other apps' folders).
   - `cause` `not_empty` with `failed_listing` `empty`: a listing that hid entries, a candidate
     for a new protected path.
   - `cause` `not_empty` with `listed`: files written during the delete.
3. **Why 12 and 15 rose on 2.7.** Compare per-`os_version` delete failure **rates** (not shares)
   between 2.6.x and 2.7.x. Rates that stay flat while shares moved mean the rise was the Test
   Lab devices dropping out of 2.7's data (§2 finding), not users.
4. **Why "not empty" clusters on 15/16.** Settled mostly by §6.2. In the field, the pre-fix
   `errno` split by `os_version` (16 vs 39) is the evidence. After the fix those folders are no
   longer removed, so the split stops being produced (§7 R9).

---

## 7. Risks and edge cases

| # | Risk | Handling |
|---|------|----------|
| 1 | `Android/obb` reachable through installer access | §3.2 option 2 keeps those deletes; checked in §6.2 step 3 |
| 2 | Alias paths (`/sdcard/Android/data`, `/mnt/...`) slip past the path rule | Fallback: a failed listing gives the "hidden items" message; a listing that came back empty gives today's message and `failed_listing=empty` in analytics. App paths come from `/storage/...` listings, so aliases are unlikely |
| 3 | Android 10 and below: rule off, deleting `Android/data` still wipes other apps' data, as today | Intentional (§8 Q2) |
| 4 | Volume removed mid-walk explained away as hidden | "Storage unavailable" / "read-only" are never explained away (§3.4); device test covers it |
| 5 | Removable-volume `Android/data` lists entries and every delete inside fails | All explained as "system folder"; the walk still tries each (a small cost, same as today) |
| 6 | Folder rows of a partly deleted `Android` keep stale MediaStore rows for the `Android/media` files that went | Existing behaviour for any root not fully cleared (only cleared roots are bulk-reported, `FolderViewModel.kt:954-964`); out of scope |
| 7 | Short toast for a ~80-character message | Same duration as every other delete error; §8 Q8 |
| 8 | Arabic/Urdu showing the LTR `Android/data` inside RTL text | Checked by hand in §6.2; if it renders wrong, wrap the path in Unicode isolates (U+2068 … U+2069) in those two files only |
| 9 | The fix stops producing the errno that showed busy vs not-empty per Android version | Accepted; §6.2 records it once per version, and pre-fix data already holds it. The mount-point flag in §8 Q5 would keep it in the field if wanted |
| 10 | Dashboard continuity: the one-call path's `error_type` changes from `not_empty`/`busy` to the new labels for these cases | Split by `app_version`; analysis skill updated in Step 7 |
| 11 | "No failure reported" checks in tests pass without testing anything after the switch | Listed in §5; each gets a `trackDeleteFailed` twin |
| 12 | Someone who emptied `Android/obb` sees "kept" and no rating ask that session | Accepted (§3.5) |
| 13 | Folder named `Android/data` inside a user folder | Not matched: the volume-root prefix is required |
| 14 | Cost per directory | One `name` compare, regex only on a `data`/`obb` folder under `Android`; no system call |

---

## 8. Open questions for the owner

1. **`Android/obb` handling** — contents-but-not-the-folder (recommended), or skip entirely?
   Decide after §6.2 step 3. If `obb` is never reachable, both behave the same.
2. **Android 10 and below** — keep deleting `Android/data` and `obb` there, as today
   (recommended), or protect them on every version?
3. **Selection-time block** for a direct selection of a protected folder (skip the confirm and
   show the reason) — defer (recommended) or add now? Adding it touches the delete UI on 3
   screens, so the `frontend-design` skill applies.
4. **Partial-success wording** — reason string without counts (recommended), or new count plurals
   (+2 plurals × 20 languages, screen changes)?
5. **Extra analytics** — also send the failing node's volume (primary / secondary) and a mount-point
   flag (one system call per failure)? Recommended: no for now; add only if §6.4 leaves the
   15/16 question open.
6. **Move-walk blind spot** (§2, `FileRepository.kt:854`) — fix as a separate change (recommended),
   or fold it in? It reuses Step 1's rule.
7. **Changelog** — add an entry to `website/changelog.html` and its translations for this fix?
8. **Toast length** — keep the short toast like every other delete error (recommended), or a long
   one for the two new messages?
