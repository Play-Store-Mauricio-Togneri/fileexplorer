# Bug Findings

Apocalypse bug review of the FileExplorer Android app (Kotlin + Jetpack Compose, minSdk 23 /
targetSdk 37). Focus: defects that cause incorrect application, data, security, build, or release
behavior. Discovery used parallel read-only agents over a deterministic partition of all 141 main
source files; every reported finding was independently re-verified against the source by reading the
exact code paths and attempting refutation. Verification was **static** (no on-device execution);
the
worktree was treated as read-only and is byte-for-byte unchanged from the initial snapshot.

## Low

### [a/state-and-lifecycle/swipe-row/offset-not-reset-on-selection] Swiped-open row stays revealed and active after "Select All"

- **Location:** `ui/components/SwipeableFileListItem.kt:64,77-95,119-121,170,207`; triggered via
  `ui/screens/folder/FolderViewModel.kt:258-262` (`selectAll`).
- **Severity:** Low
- **Confidence:** Medium
- **Defect:** Nothing resets the `offsetX` `Animatable` when `isSelected` becomes true. After
  swiping a row open (offset ≈ ±80 px) and then invoking "Select All", the row renders selected *
  *and** translated; `pointerInput(isSelected)` now returns early so it can't be swiped back (only a
  tap on the row body resets it), and the revealed Delete/Rename action button — gated only on the
  sign of `offsetX`, not `isSelected` — stays composed and clickable, so tapping the exposed strip
  fires a single-item delete/rename in the middle of a multi-selection.
- **Trigger:** Swipe a row open (not in selection mode), then choose "Select All".
- **Evidence / verification:** No `LaunchedEffect(isSelected)`/reset path exists; `LazyColumn` keys
  by path so the `Animatable` survives the recompose; the destructive delete is still gated by the
  confirmation dialog, hence Low.
- **Suggested fix:** Add `LaunchedEffect(isSelected) { if (isSelected) offsetX.animateTo(0f) }` and
  gate the action buttons on `!isSelected`.
