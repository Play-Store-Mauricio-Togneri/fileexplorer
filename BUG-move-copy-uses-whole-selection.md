# Bug: "Move to" / "Copy to" from a row's overflow menu operate on the whole selection

**Status:** open, present at `HEAD` (`a8ee51e`)
**Severity:** high — silently relocates files the user did not choose, with no confirmation and no undo
**Affected:** `versionCode 240` / `versionName 2.4.0`, and every earlier build containing `fc1aa75` (2026-05-19)
**Scope:** the folder screen only (`FolderActivity` → `FolderScreen`). Home, Search, Favorites and Recents have no selection mode and are unaffected.
**Not introduced by the configurable-swipe-actions change** — that change reuses the same dispatch block verbatim, but the swipe path cannot reach this bug (see [Why the swipe path is immune](#why-the-swipe-path-is-immune)).

---

## Summary

While the folder screen is in selection mode, the overflow (⋮) button remains visible on every row
that is **not itself selected**. Opening that menu on an unselected row and choosing **Move to** or
**Copy to** does not act on that row. It acts on **the entire current selection plus that row**.

The user is given no indication that this happened: the bottom sheet is presented as being about the
single file they tapped, and the destination picker that follows shows only the title "Move to" /
"Copy to" with no item count. The first evidence is that files are missing from the source folder.

For **Move**, this is data movement the user never requested, and across volumes it is implemented as
copy-then-delete-source — the originals are removed.

---

## Reproduction

1. Open any folder containing at least three items — call them `A`, `B`, `C`.
2. Long-press `A` to enter selection mode. Tap `B`. The action bar now reads "2 selected"; `A` and
   `B` are highlighted.
3. Row `C` is **not** selected, so its ⋮ button is still rendered. Tap it.
4. The file actions bottom sheet opens. It is titled and instrumented for `C` alone — the analytics
   event it fires on open carries `C`'s extension and MIME type.
5. Tap **Move to**.
6. The destination picker opens, titled simply "Move to".
7. Pick any destination and confirm.

**Expected:** `C` is moved. `A` and `B` stay where they are and stay selected, or the selection is
cleared — either way they are not touched.

**Actual:** `A`, `B` **and** `C` are all moved to the destination. Selection mode exits. No dialog,
toast or picker text ever mentioned more than one item.

**Copy to** behaves identically (files are duplicated into the destination rather than moved, so it
is not destructive, but it is still not what was asked for).

---

## Root cause

Three pieces combine. Each is individually reasonable; together they are the defect.

### 1. The ⋮ button is hidden per-row, not per-mode

`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/components/FileListItem.kt:157-167`

```kotlin
if (showMenu) {
    Box(modifier = Modifier.size(48.dp)) {
        if (!isSelected) {                     // <-- per-row, not per-mode
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    ...
```

The guard is `!isSelected` — a property of *this row*. It is not `!isSelectionMode`. The reserved
48dp `Box` keeps the row's layout stable whether or not the button is drawn, which is why a selected
row does not visibly reflow; but every *unselected* row keeps a live, tappable ⋮ for the whole
duration of selection mode.

`showMenu` defaults to `true` (`FileListItem.kt:76`) and the folder screen never overrides it.

### 2. `toggleSelection` adds to the selection; it does not replace it

`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderViewModel.kt:400-409`

```kotlin
fun toggleSelection(file: FileItem) {
    _state.update { state ->
        val newSelected = if (file.path in state.selectedPaths) {
            state.selectedPaths - file.path
        } else {
            state.selectedPaths + file.path       // <-- set union
        }
        state.copy(selectedPaths = newSelected)
    }
}
```

This is correct for its primary caller (tapping rows to build a selection). It is the wrong primitive
for "act on exactly this one row".

### 3. Move / Copy from the sheet route through the selection, unlike every other action there

`app/src/main/java/com/mauriciotogneri/fileexplorer/ui/screens/folder/FolderScreen.kt:476-483`

```kotlin
FileAction.MoveTo -> {
    viewModel.toggleSelection(file)                                    // add C to {A, B}
    viewModel.onAction(com.mauriciotogneri.fileexplorer.data.model.FileAction.MoveTo)
}
FileAction.CopyTo -> {
    viewModel.toggleSelection(file)
    viewModel.onAction(com.mauriciotogneri.fileexplorer.data.model.FileAction.CopyTo)
}
```

`onAction(MoveTo)` reaches `FolderViewModel.onMoveTo()` at `FolderViewModel.kt:460-472`:

```kotlin
private fun onMoveTo() {
    val selectedItems = getSelectedFiles()        // {A, B, C}
    if (selectedItems.isEmpty()) return

    _state.update {
        it.copy(
            pickerRequest = PickerRequest(items = selectedItems, mode = OperationMode.MOVE),
            selectedPaths = emptySet()
        )
    }
}
```

`getSelectedFiles()` (`FolderViewModel.kt:421-424`) resolves the *whole* `selectedPaths` set against
the current listing. The single `file` the user tapped is never passed anywhere — it only ever exists
as the third element the `toggleSelection` call bolted onto an existing selection.

The request then flows to `executeOperationInternal(request.items, targetPath, mode)`
(`FolderViewModel.kt:527`), which calls `fileRepository.copyFiles(sources = items, …,
deleteAfter = (mode == OperationMode.MOVE), …)` (`FolderViewModel.kt:542-547`) — every item in the
request is copied, and on a move every source is then deleted.

---

## Blast radius — which actions are affected

Only **Move to** and **Copy to**. Every other entry in the sheet passes the tapped file directly and
is correct. This is worth stating explicitly, because the two broken cases look like the rest:

| Sheet action | Dispatch (`FolderScreen.kt`) | Affected? |
| :--- | :--- | :--- |
| Move to | `toggleSelection(file)` → `onAction(MoveTo)` | **Yes** |
| Copy to | `toggleSelection(file)` → `onAction(CopyTo)` | **Yes** |
| Delete | `showDeleteConfirmDialog(listOf(file))` | No |
| Rename | `showRenameDialog(file)` | No |
| Compress | `showCompressDialog(listOf(file))` | No |
| Uncompress | `showUncompressDialog(file)` | No |
| Share | `IntentUtil.shareFiles(context, listOf(file))` | No |
| Open with | `IntentUtil.openFileWith(context, file, …)` | No |
| Info | `ItemInfoActivity.createIntent(context, file.path)` | No |
| Add / remove favorite | `addToFavorites(file)` / `removeFromFavorites(file)` | No |
| Select | `toggleSelection(file)` | No — that *is* its job |

Note that Delete escapes on two counts: it passes `listOf(file)` directly, **and** the confirmation
dialog would have exposed the discrepancy anyway — `DeleteConfirmDialog.kt:44-48` prints the file's
name when the count is 1 and a `R.plurals.item_amount` count otherwise, so a mismatched batch would
read "3 items" instead of a filename.

Move and Copy have no equivalent checkpoint.

---

## Why the user gets no warning

Every surface between the tap and the irreversible operation is silent about how many files are in
flight:

- **The bottom sheet** is constructed as `FileActionsBottomSheet(file = file, mode = "icon", …)`
  (`FolderScreen.kt:456-462`). It renders one file's actions and fires
  `trackBottomSheetOpened(extension, mimeType, source, mode)` with *that file's* extension and MIME
  type (`FileActionsBottomSheet.kt:70`). Nothing in it is plural.
- **The destination picker** shows only a mode title — `picker_title_move` / `picker_title_copy` —
  at `DestinationPicker.kt:83-86`. `request.items` is passed to `PickerViewModel` and used for
  validation (`PickerViewModel.kt:183, 196`, the target-is-a-source-parent check) but is **never
  rendered as a count**.
- **There is no confirmation dialog** on the move/copy path at all; confirming the destination starts
  the operation.

So the sequence is: a sheet about one file → a picker naming no files → files gone.

---

## Data-loss analysis

`Move` is not a rename. `executeOperationInternal` delegates to
`fileRepository.copyFiles(sources = …, deleteAfter = true, …)`, so across volumes the sources are
**copied and then deleted**. The unintended extra files are therefore:

- removed from their original location,
- reported to MediaStore as deleted (`MediaStoreUtil.notifyDeleted`, `FolderViewModel.kt:572`),
- not recoverable from within the app — there is no undo anywhere in this codebase.

`Copy` is non-destructive but still writes files the user did not ask for into the destination, which
on a full or near-full volume can fail the whole batch.

---

## Secondary symptom: a selection that cannot be cleared

A narrower variant, reachable when the tapped row is stale — it is still composed but no longer in
`state.files` (deleted by another app, or on a volume that was just unmounted):

1. Nothing else is selected. The user opens ⋮ on the stale row and taps **Move to**.
2. `toggleSelection` adds the phantom path to `selectedPaths`.
3. `getSelectedFiles()` filters against `state.files` and returns an empty list.
4. `onMoveTo()` hits `if (selectedItems.isEmpty()) return` — **before** the `selectedPaths = emptySet()`
   write, which lives inside the `_state.update` block it never reaches.
5. `selectedPaths` is left holding the phantom path. Because `isSelectionMode` is
   `selectedPaths.isNotEmpty()` (`FolderViewModel.kt:110`) and `selectedCount` is `selectedPaths.size`,
   the screen sits in selection mode showing "1 selected" with no row highlighted, until the user
   backs out or a reload clears it.

---

## Why the swipe path is immune

The row-swipe path added by the configurable-swipe-actions work reaches the *same two lines* at
`FolderScreen.kt:404-411`, but cannot trigger the bug, because the gesture is switched off entirely
whenever anything is selected:

- `SwipeableFileListItem.kt:133` — the reveal buttons are composed only inside `if (!isSelectionMode)`.
- `SwipeableFileListItem.kt:192-193` — `pointerInput` returns early when `isSelectionMode` is true, so
  the row cannot even be dragged open.
- `SwipeableFileListItem.kt:114-118` — entering selection mode animates an already-open row shut.

Since `isSelectionMode == selectedPaths.isNotEmpty()`, a swipe button can only be tapped while the
selection is empty. `toggleSelection(file)` therefore yields exactly `{file.path}` and
`getSelectedFiles()` returns exactly `[file]`.

This is a property of the gesture's gating, not of the dispatch code — the dispatch is equally wrong
on both paths and only one path can reach it. Fixing the root cause fixes both.

---

## Suggested fixes

### Preferred: give the ViewModel single-file entry points

Add explicit single-item operations alongside the selection-based ones, so no caller has to mutate
selection state to express "act on this row":

```kotlin
fun onMoveTo(file: FileItem) = startOperation(listOf(file), OperationMode.MOVE)
fun onCopyTo(file: FileItem) = startOperation(listOf(file), OperationMode.COPY)
```

…where `startOperation` sets `pickerRequest` without touching `selectedPaths`, and the existing
`onMoveTo()` / `onCopyTo()` become thin wrappers passing `getSelectedFiles()`. Then both dispatch
blocks (`FolderScreen.kt:404-411` and `:476-483`) call the single-file form, matching how `Rename`
and `Delete` already pass the file directly.

This is the smallest change that removes the class of bug rather than one instance, and it makes the
mapping unit-testable — currently nothing below the UI layer can observe it.

### Also worth doing (independent, defence in depth)

- **Hide the ⋮ for the whole of selection mode**, not just on selected rows: change
  `FileListItem.kt:159` from `if (!isSelected)` to a new `isSelectionMode` parameter. During
  selection mode the action bar is the intended way to act on the selection, so a per-row menu that
  silently merges with it has no clear meaning. Keep the reserved 48dp `Box` so layout does not shift.
- **Show the item count in the destination picker.** `request.items` is already available at
  `DestinationPicker.kt:46`; surfacing `R.plurals.item_amount` next to the title would have made this
  bug self-evident to the user and to any future reviewer.

---

## Test coverage gap

Nothing at any level covers the sheet's action → ViewModel mapping:

- `FileActionsBottomSheetTest` asserts only that the component *emits* the right `FileAction` enum
  value; it never runs the dispatch.
- No test under `app/src/androidTest/.../screens/folder/` or `.../integration/` drives the ⋮ menu on
  the folder screen while a selection exists.
- `FolderViewModelTest` has no test that pins `onMoveTo`/`onCopyTo` to a specific item set.

Regression tests worth adding with the fix:

1. **Unit** (`FolderViewModelTest`): with `selectedPaths = {A, B}`, invoking the single-file move for
   `C` produces `pickerRequest.items == [C]` and leaves `selectedPaths` unchanged.
2. **Unit** (`FolderViewModelTest`): the selection-based `onMoveTo()` still produces all selected
   items — so the fix does not regress the action bar.
3. **Instrumentation**: select two rows, open ⋮ on a third, choose "Move to", and assert the picker
   opens for exactly one item.

---

## Evidence index

| Claim | Location |
| :--- | :--- |
| ⋮ hidden per-row, not per-mode | `ui/components/FileListItem.kt:157-167` |
| `showMenu` defaults to true, never overridden | `ui/components/FileListItem.kt:76` |
| `toggleSelection` is a set union | `ui/screens/folder/FolderViewModel.kt:400-409` |
| Sheet's Move/Copy route through selection | `ui/screens/folder/FolderScreen.kt:476-483` |
| `onMoveTo` reads the whole selection | `ui/screens/folder/FolderViewModel.kt:460-472` |
| `getSelectedFiles` resolves all selected paths | `ui/screens/folder/FolderViewModel.kt:421-424` |
| Move deletes sources after copying | `ui/screens/folder/FolderViewModel.kt:542-547` |
| `isSelectionMode` == selection non-empty | `ui/screens/folder/FolderViewModel.kt:110` |
| Picker title carries no count | `ui/screens/picker/DestinationPicker.kt:83-86` |
| Delete dialog would have shown the count | `ui/components/DeleteConfirmDialog.kt:44-48` |
| Swipe gesture disabled in selection mode | `ui/components/SwipeableFileListItem.kt:133, 192-193` |
| Selection mode exists only on the folder screen | `grep -rl isSelectionMode app/src/main` |
