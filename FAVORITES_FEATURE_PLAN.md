# Favorites Feature — Implementation Plan

Add the ability to mark files **and folders** as favorites, surfaced as a new
home-screen section, a global bottom-sheet toggle, and a per-row indicator in the
folder view. The design mirrors the existing **Recents** feature
(`DataStoreRecentFilesSource` → `RecentFilesRepository` → `HomeViewModel`/UI) as
closely as possible, diverging only where favorites genuinely differ.

> UI work must go through the `frontend-design` skill, and all user-facing strings
> must be localized to every supported locale (see CLAUDE.md). Theming uses
> `MaterialTheme.colorScheme.*` only — no hardcoded colors. Icons come from the
> **Outlined** Material family only (never `Icons.Filled.*`).

---

## 1. Confirmed product decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | **Ordering** of the favorites list | Newest favorite first (`favoritedTimestamp` desc) — mirrors Recents |
| 2 | **Folder-view indicator** | Fixed, tinted trailing star just left of the ⋮ overflow; **visible in selection mode** too |
| 3 | **Recents card sheet** | Also gets the Add/Remove-favorites option (toggle is truly global) |
| 4 | **Favorites card ⋮ menu** | Dedicated, **type-aware** sheet (separate from the folder-view sheet) |
| 5 | **Settings** | "Clear favorites" action only — **no** enable/disable toggle |
| 6 | **Multi-select mode** | No batch favorite; per-item only |

### Locked assumptions (correct before implementing if wrong)
- **No size cap** on favorites (Recents caps at `MAX_RECENT_FILES = 20`).
- **Prune-on-display** via `File(path).exists()` filtering + a prune call on home
  `RESUMED`, identical to Recents.
- **Star tint** = `MaterialTheme.colorScheme.primary` (theme-safe accent).
- **Remove** uses an optimistic UI update then persists (mirrors `removeFromRecents`),
  to avoid flicker.
- Favorite cards reuse the Recents card visual (thumbnail/icon + name + ⋮ menu); the
  **section is hidden when empty**.
- Favorite **file** opening reuses the existing Recents type-routing
  (archive / apk / text / image / generic intent); favorite **folder** opening
  navigates into the folder.

### Star icon semantics
- Not a favorite → `Icons.Outlined.StarBorder` (hollow).
- Is a favorite → `Icons.Outlined.Star` (solid-looking, still in the Outlined family).
- **Do not** use `Icons.Filled.Star` — it violates the project icon rule.

---

## 2. Existing Recents architecture (the template)

Verified reference points:

- **Model** — `data/model/RecentFile.kt`: `RecentFile(path, name, mimeType, lastOpenedTimestamp) : FileTypeInfo`; `isDirectory` hardcoded `false`. `FileTypeInfo` (`data/model/FileTypeInfo.kt`) is the shared interface with `isDirectory` + computed type predicates (`isImage`, `isPdf`, …, `hasThumbnailSupport`).
- **Source interface** — `data/source/RecentFilesSource.kt`:
  ```kotlin
  interface RecentFilesSource {
      val recentFilesFlow: Flow<List<RecentFile>>
      suspend fun getRecentFiles(): List<RecentFile>
      suspend fun updateRecentFiles(transform: (List<RecentFile>) -> List<RecentFile>)
      suspend fun clearRecentFiles()
  }
  ```
- **Source impl** — `data/source/DataStoreRecentFilesSource.kt`: Preferences DataStore, single `stringPreferencesKey("files")`, JSON-array serialization (`path`/`name`/`mimeType`/`timestamp`), `.flowOn(Dispatchers.IO)`, `sortedByDescending { lastOpenedTimestamp }`, `ErrorReporter.error(e, "load_recent_files")` on parse failure.
- **Repository** — `data/repository/RecentFilesRepository.kt`:
  - `Context.recentFilesDataStore by preferencesDataStore(name = "recent_files", corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })`.
  - `recentFilesFlow` = `source.recentFilesFlow.map { it.filter { File(it.path).exists() } }`.
  - `addRecentFile(file: File)` ignores directories, dedups by path, `take(20)`.
  - `removeRecentFile(path)`, `pruneNonExistentFiles()` (guarded write), `clearRecentFiles()`.
- **HomeViewModel** — `ui/screens/home/HomeViewModel.kt`: `HomeUiState` carries `recentFiles`, `recentFileMode ("icon"|"press")`, `recentFileToDelete`, etc.; `observeRecentFiles()` combines the flow with `preferencesRepository.recentFilesEnabled` and `.flowOn(ioDispatcher)`; `loadData()` calls `pruneNonExistentFiles()`; `openRecentFile()` does existence-check + type-routing; injected `ioDispatcher` (defaults `Dispatchers.IO`).
- **DI** — manual `ViewModelProvider.Factory` (no Hilt) in `HomeViewModel`, `FolderViewModel`, `SettingsViewModel`. `SettingsViewModel.clearRecentFiles()` already exists (≈ lines 114–117).
- **Home UI** — `ui/screens/home/HomeScreen.kt` renders `RecentFilesSection` in a `verticalScroll` Column (Search → Recents → Locations → Storages → dialogs); prunes on `RESUMED`.
- **Recents section** — `ui/components/RecentFilesSection.kt`: `if (recentFiles.isEmpty()) return`; `LazyRow` (`spacedBy(12.dp)`, `contentPadding = horizontal 16.dp`, `key = { it.path }`); `RecentFileCard(file, onClick, onIconClick, onLongPress)` 120×160dp.
- **Recents sheet** — `ui/components/RecentFileActionsBottomSheet.kt`: `RecentFileAction { OpenWith, OpenFolder, Share, RemoveFromRecents, Delete, Info }`; reusable `RecentFileActionItem(icon, text, onClick)` → `DropdownMenuItem`; `FullWidthDragHandle()`; analytics per action.
- **Folder-view sheet** — `ui/components/FileActionsBottomSheet.kt`: `FileActionsBottomSheet(file: FileItem, mode, onAction, onDismiss)`; `FileAction { Select, Share, OpenWith, Compress, Uncompress, MoveTo, CopyTo, Rename, Delete, Info }`; reusable `FileActionItem(icon, text, onClick)`. **Delete** is the second-to-last item (≈ lines 157–164), then **Info**.
- **Folder row** — `ui/components/FileListItem.kt`: `Row[ SelectableFileIcon(40dp) | Spacer(16dp) | Column(weight 1f){ name(maxLines1, ellipsis) ; metadata } | Box(48dp){ if(!isSelected) IconButton(MoreVert) } ]`. Existing **lock badge** overlay on the icon for restricted folders (`align(BottomEnd).size(16dp)`, surface circle, `Icons.Outlined.Lock`). Wrapped by `SwipeableFileListItem` (swipe → Delete/Rename).
- **Folder screen** — `ui/screens/folder/FolderScreen.kt`: `LazyColumn { items(state.files, key = { it.path }) { … } }`; `onMenuClick = { fileForActions = file }`; `fileForActions?.let { … FileActionsBottomSheet … }` with a `when (action)` dispatcher.

---

## 3. File-by-file changes

### A. Data model — NEW `data/model/Favorite.kt`
```kotlin
@Immutable
data class Favorite(
    val path: String,
    val name: String,
    override val isDirectory: Boolean,   // REAL value (unlike RecentFile, which hardcodes false)
    val mimeType: String,
    val favoritedTimestamp: Long
) : FileTypeInfo
```
> Match `RecentFile`'s relationship to `FileTypeInfo` exactly — verify which members
> need `override` (RecentFile provides `mimeType` and overrides `isDirectory`). The
> only behavioral difference is `isDirectory` is a real constructor value here.

### B. Source — NEW `data/source/FavoriteFilesSource.kt` (interface)
```kotlin
interface FavoriteFilesSource {
    val favoritesFlow: Flow<List<Favorite>>
    suspend fun getFavorites(): List<Favorite>
    suspend fun updateFavorites(transform: (List<Favorite>) -> List<Favorite>)
    suspend fun clearFavorites()
}
```

### C. Source impl — NEW `data/source/DataStoreFavoriteFilesSource.kt`
Clone `DataStoreRecentFilesSource` with these changes:
- Add a JSON key `isDirectory` (boolean) alongside `path`/`name`/`mimeType`/`timestamp`.
- Parse/serialize `Favorite` instead of `RecentFile`.
- `sortedByDescending { it.favoritedTimestamp }`.
- `ErrorReporter.error(e, "load_favorite_files")`.
- Key stays `stringPreferencesKey("files")` (scoped to its own DataStore file, so no collision).

### D. Repository — NEW `data/repository/FavoritesRepository.kt`
```kotlin
val Context.favoriteFilesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "favorite_files",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class FavoritesRepository(private val source: FavoriteFilesSource) {

    val favoritesFlow: Flow<List<Favorite>> = source.favoritesFlow.map { favorites ->
        favorites.filter { File(it.path).exists() }
    }

    suspend fun getFavorites(): List<Favorite> = withContext(Dispatchers.IO) {
        source.getFavorites().filter { File(it.path).exists() }
    }

    // Granular signature so both FileItem callers (folder view) and RecentFile callers
    // (recents sheet) can add without constructing a FileItem or touching disk.
    suspend fun addFavorite(
        path: String, name: String, isDirectory: Boolean, mimeType: String
    ) = withContext(Dispatchers.IO) {
        val entry = Favorite(path, name, isDirectory, mimeType, System.currentTimeMillis())
        source.updateFavorites { current ->
            listOf(entry) + current.filterNot { it.path == entry.path }   // dedup + move to top; NO cap
        }
    }

    suspend fun removeFavorite(path: String) = withContext(Dispatchers.IO) {
        source.updateFavorites { current -> current.filterNot { it.path == path } }
    }

    suspend fun pruneNonExistentFiles() = withContext(Dispatchers.IO) {
        val current = source.getFavorites()
        if (current.any { !File(it.path).exists() }) {
            source.updateFavorites { it.filter { fav -> File(fav.path).exists() } }
        }
    }

    suspend fun clearFavorites() = source.clearFavorites()
}
```

### E. DI wiring — EDIT three Factories
In `HomeViewModel.Factory`, `FolderViewModel.Factory`, and `SettingsViewModel.Factory`,
construct and inject:
```kotlin
favoritesRepository = FavoritesRepository(
    DataStoreFavoriteFilesSource(application.favoriteFilesDataStore)
)
```
Add a `favoritesRepository: FavoritesRepository` constructor param to each ViewModel.

### F. HomeViewModel — EDIT `ui/screens/home/HomeViewModel.kt`
- `HomeUiState`: add
  ```kotlin
  val favorites: List<Favorite> = emptyList(),
  val favoritePaths: Set<String> = emptySet(),   // for the Recents-sheet toggle label
  val selectedFavorite: Favorite? = null,
  val favoriteFileMode: String = "icon",
  val favoriteToDelete: Favorite? = null,
  ```
- `observeFavorites()` mirroring `observeRecentFiles()` but with **no** preference combine:
  ```kotlin
  viewModelScope.launch {
      favoritesRepository.favoritesFlow
          .flowOn(ioDispatcher)
          .collect { favs -> _uiState.update {
              it.copy(favorites = favs, favoritePaths = favs.map { f -> f.path }.toSet())
          } }
  }
  ```
- `loadData()`: add `viewModelScope.launch { favoritesRepository.pruneNonExistentFiles() }`
  next to the recents prune.
- Add:
  - `showFavoriteActions(favorite, mode)` (existence check, mirror `showRecentFileActions`).
  - `removeFromFavorites(favorite)` (optimistic update + `favoritesRepository.removeFavorite`).
  - `favoriteToDelete` set/confirm/dismiss (mirror `recentFileToDelete` + `confirmDeleteRecentFile`).
  - `openFavorite(favorite)` — folder → emit a navigate event/callback; file → reuse the
    file-routing currently inside `openRecentFile()`.
  - For the **Recents sheet** toggle: `addRecentToFavorites(recentFile)` and
    `removeRecentFromFavorites(recentFile)` calling the repo (isDirectory = false).
- **Refactor (small, to avoid duplication):** extract the type-routing body of
  `openRecentFile()` into a private helper, e.g.
  `openFileByType(path, name, mimeType)`, called by both `openRecentFile()` and the
  file branch of `openFavorite()`. If this proves messy, duplicate instead — flag it.

### G. FolderViewModel — EDIT `ui/screens/folder/FolderViewModel.kt`
- `FolderUiState`: add `val favoritePaths: Set<String> = emptySet()`.
- Collect `favoritesRepository.favoritesFlow` (on the injected IO dispatcher) and update
  `favoritePaths` (just the path set — the row only needs membership).
- Add `addToFavorites(file: FileItem)` →
  `favoritesRepository.addFavorite(file.path, file.name, file.isDirectory, file.mimeType)`.
- Add `removeFromFavorites(file: FileItem)` → `favoritesRepository.removeFavorite(file.path)`.

### H. SettingsViewModel — EDIT `ui/screens/settings/SettingsViewModel.kt`
- Add `clearFavorites()` mirroring the existing `clearRecentFiles()` (≈ lines 114–117),
  including the "cleared" snackbar/effect it triggers.

### I. FileActionsBottomSheet — EDIT `ui/components/FileActionsBottomSheet.kt`
- Add to the sealed class:
  ```kotlin
  data object AddToFavorites : FileAction()
  data object RemoveFromFavorites : FileAction()
  ```
- Add an `isFavorite: Boolean` parameter to the composable.
- Insert a `FileActionItem` **immediately above the Delete item** (≈ before line 157):
  ```kotlin
  FileActionItem(
      icon = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
      text = stringResource(
          if (isFavorite) R.string.action_remove_from_favorites
          else R.string.action_add_to_favorites
      ),
      onClick = {
          AnalyticsTracker.trackBottomSheet... (extension, mimeType, source)   // see §K
          onAction(if (isFavorite) FileAction.RemoveFromFavorites else FileAction.AddToFavorites)
      }
  )
  ```

### J. RecentFileActionsBottomSheet — EDIT `ui/components/RecentFileActionsBottomSheet.kt`
- Add to `RecentFileAction`: `AddToFavorites`, `RemoveFromFavorites`.
- Add an `isFavorite: Boolean` parameter.
- Insert the same star `RecentFileActionItem` above the existing **Delete** item
  (the Delete item is ≈ lines 109–116). Recents are files-only, so the label logic is
  identical (file semantics).

### K. AnalyticsTracker — EDIT `data/util/AnalyticsTracker.kt`
- Add tracking methods consistent with the existing
  `trackBottomSheet<Action>(extension, mimeType, source)` convention, e.g.
  `trackBottomSheetAddToFavorites(...)` / `trackBottomSheetRemoveFromFavorites(...)`.

### L. FileListItem — EDIT `ui/components/FileListItem.kt`
- Add `isFavorite: Boolean = false` parameter (thread it through `SwipeableFileListItem`
  too).
- Insert a fixed trailing star **between the name `Column` and the trailing `Box(48dp)`**,
  rendered only when `isFavorite` and **independent of `isSelected`** (so it stays visible
  in selection mode):
  ```kotlin
  if (isFavorite) {
      Icon(
          imageVector = Icons.Outlined.Star,
          contentDescription = stringResource(R.string.content_description_favorite),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)   // add small horizontal padding to taste
      )
  }
  ```
- Verify the new element doesn't break the swipe layout in `SwipeableFileListItem`.

### M. FolderScreen — EDIT `ui/screens/folder/FolderScreen.kt`
- Pass `isFavorite = state.favoritePaths.contains(file.path)` to each row.
- Pass `isFavorite = state.favoritePaths.contains(it.path)` into `FileActionsBottomSheet`.
- Extend the `when (action)` dispatcher:
  ```kotlin
  FileAction.AddToFavorites -> viewModel.addToFavorites(file)
  FileAction.RemoveFromFavorites -> viewModel.removeFromFavorites(file)
  ```

### N. FavoritesSection — NEW `ui/components/FavoritesSection.kt`
Clone `RecentFilesSection.kt`:
- `if (favorites.isEmpty()) return`.
- Section header uses `R.string.section_favorites`, placed **below** Recents in `HomeScreen`.
- Same `LazyRow` config (`spacedBy(12.dp)`, `contentPadding = horizontal 16.dp`,
  `key = { it.path }`).
- Renders `FavoriteFileCard` (below).

### O. FavoriteFileCard — NEW `ui/components/FavoriteFileCard.kt`
Clone `RecentFileCard` (120×160dp, corner 12dp; name + ⋮ menu row), **type-aware**:
- If `favorite.isDirectory` → render the folder icon (reuse `FileIcon` / `getFileIcon`;
  confirm `FileIcon` renders a folder glyph for a directory), **no thumbnail**.
- Else → thumbnail-or-type-icon exactly like `RecentFileCard`.
- Callbacks: `onClick`, `onIconClick`, `onLongPress` (same shape as `RecentFileCard`).

### P. FavoriteFileActionsBottomSheet — NEW `ui/components/FavoriteFileActionsBottomSheet.kt`
Clone the structure of `RecentFileActionsBottomSheet`. **Type-aware** action set:
- Sealed class:
  ```kotlin
  sealed class FavoriteFileAction {
      data object OpenWith : FavoriteFileAction()        // files only
      data object Share : FavoriteFileAction()           // files only
      data object OpenFolder : FavoriteFileAction()      // both (containing folder)
      data object RemoveFromFavorites : FavoriteFileAction()
      data object Delete : FavoriteFileAction()
      data object Info : FavoriteFileAction()
  }
  ```
- **File** favorite shows: Open with, Share, Open folder, Remove from favorites, Delete, Info.
- **Folder** favorite shows: Open folder, Remove from favorites, Delete, Info
  (tapping the card opens the folder; omit Open With/Share).
- Reuse `FullWidthDragHandle()`; icons: `Icons.Outlined.Star` for the remove item,
  `Icons.AutoMirrored.Outlined.OpenInNew`, `Icons.Outlined.Folder`, `Icons.Outlined.Share`,
  `Icons.Outlined.Delete`, `Icons.Outlined.Info`. Analytics `source = "favorite"`.

### Q. HomeScreen — EDIT `ui/screens/home/HomeScreen.kt`
- Render `FavoritesSection(...)` directly **below** the Recents section in the scroll Column.
- Wire favorite card callbacks to the VM: `onClick → openFavorite`,
  `onIconClick → showFavoriteActions(.., "icon")`, `onLongPress → showFavoriteActions(.., "press")`.
- Show `FavoriteFileActionsBottomSheet` when `uiState.selectedFavorite != null`; map its
  actions to VM calls (Open folder → navigate to `File(path).parent`, mirroring the recents
  `OpenFolder` handling; Remove → `removeFromFavorites`; Delete → set `favoriteToDelete`;
  Share/OpenWith/Info → reuse existing intent helpers).
- Add a delete-confirmation dialog driven by `favoriteToDelete` (mirror the recents one).
- For the **Recents** sheet, pass `isFavorite = uiState.favoritePaths.contains(recentFile.path)`
  and route its new actions to `addRecentToFavorites` / `removeRecentFromFavorites`.

### R. SettingsScreen — EDIT `ui/screens/settings/SettingsScreen.kt`
- Add a "Clear favorites" row next to "Clear recent files", using
  `R.string.settings_favorite_files_clear`, calling `viewModel.clearFavorites()`, and
  surfacing `R.string.settings_favorite_files_cleared`. **No** enable/disable toggle.

---

## 4. String resources

Add to `app/src/main/res/values/strings.xml` **and all 19 locale folders**
(`values-ar, -bn, -ca, -de, -el, -es, -fr, -hi, -in, -it, -ja, -nl, -pt, -ro, -ru, -tr, -ur, -vi, -zh`):

| Key | English value |
|-----|---------------|
| `section_favorites` | Favorites |
| `action_add_to_favorites` | Add to favorites |
| `action_remove_from_favorites` | Remove from favorites |
| `settings_favorite_files_clear` | Clear favorites |
| `settings_favorite_files_cleared` | Favorites cleared |
| `content_description_favorite` | Favorite |

**Reuse existing keys** (no new strings needed) for the favorites sheet:
`action_open_with`, `action_share`, `action_open_folder`, `action_info`, `action_delete`.

> Place new keys near their siblings (the `action_*` block ≈ lines 81–95, `section_*`
> ≈ lines 115–117, `settings_recent_files_*` ≈ lines 147–151, `content_description_*`
> ≈ line 380) in every locale. RTL (`values-ar`, `values-ur`) is handled by the existing
> `supportsRtl` flag — no extra work, but verify the trailing star mirrors correctly.

---

## 5. Test plan

Follow existing patterns; honor the known testing constraints (no Robolectric; unmocked
`android.*` throws; use injected `ioDispatcher` to avoid `StandardTestDispatcher` IO
flakiness; instrumentation tests inject `FakeStorageSource(testDir)` for real file ops).

- **NEW `data/repository/FavoritesRepositoryTest.kt`** (unit) — mirror
  `RecentFilesRepositoryTest`: a `FakeFavoriteFilesSource` (in-memory `MutableStateFlow`),
  temp files under `java.io.tmpdir` for existence/prune, `runTest`. Cover: add (incl.
  **folder** entries — must NOT be dropped), dedup + move-to-top, **no cap**, remove,
  prune drops missing only when stale, existence filtering on the flow, clear.
- **EDIT `ui/screens/home/HomeViewModelTest.kt`** — add a mocked `favoritesRepository`;
  cover: `observeFavorites` populates `favorites`/`favoritePaths`; prune on `loadData`;
  `openFavorite` routes folder vs file differently; remove (optimistic) + delete; the
  Recents-sheet add/remove path; toggle label reflects `favoritePaths`.
- **EDIT `FolderViewModelTest`** (if present; else add) — `favoritePaths` exposure;
  `addToFavorites`/`removeFromFavorites` call the repo with correct args (folder included).
- **Optional Compose UI test** — favorites section visibility (hidden when empty), the
  trailing star renders for favorited rows and persists in selection mode, and the
  Add/Remove toggle label flips. Mirror existing instrumentation patterns.

---

## 6. Verification

1. `./gradlew assembleDebug` (or the project's standard build task) — compiles.
2. `./gradlew testDebugUnitTest` — unit tests green.
3. Run the `delta-review` skill on the diff (per CLAUDE.md) and address findings.
4. Manual smoke in **light and dark**:
   - Favorite a file and a folder from the folder-view sheet → star appears on the row
     (and stays in selection mode); both appear in the home Favorites section, newest first.
   - Favorites section hidden when empty; reappears when populated.
   - Tap a favorite file → opens by type; tap a favorite folder → navigates in.
   - Favorite from the Recents sheet → label flips Add ⇄ Remove.
   - Favorites card ⋮ → type-aware actions; Remove and Delete behave correctly.
   - Delete a favorited item elsewhere, return home → entry pruned automatically.
   - Settings → "Clear favorites" empties the section.
   - RTL (Arabic/Urdu): star and rows mirror correctly.

---

## 7. Risks & open items

- **`FileTypeInfo` surface**: confirm exactly which members `Favorite` must `override`
  (mirror `RecentFile`); the only intended difference is a real `isDirectory`.
- **`FileIcon` directory rendering**: confirm it shows a folder glyph for a directory so
  `FavoriteFileCard` folders look right without a thumbnail.
- **`openRecentFile` refactor**: extracting `openFileByType(...)` is the clean path; if it
  entangles with existing dialog/permission state, duplicate the routing and flag it rather
  than over-refactoring.
- **Trailing star + swipe layout**: verify the new trailing element coexists with
  `SwipeableFileListItem`'s drag actions and doesn't shift hit targets.
- **Scope**: this touches ~6 new files, ~9 edited files, and 20 string files — sizeable but
  all mechanical mirrors of Recents. No architectural changes.

---

## 8. Implementation checklist (suggested order)

- [ ] A. `Favorite` model
- [ ] B. `FavoriteFilesSource` interface
- [ ] C. `DataStoreFavoriteFilesSource`
- [ ] D. `FavoritesRepository` (+ `favoriteFilesDataStore`)
- [ ] E. DI wiring in the three Factories
- [ ] F. `HomeViewModel` (+ optional `openFileByType` extraction)
- [ ] G. `FolderViewModel`
- [ ] H. `SettingsViewModel.clearFavorites()`
- [ ] K. `AnalyticsTracker` methods
- [ ] I. `FileActionsBottomSheet` toggle
- [ ] J. `RecentFileActionsBottomSheet` toggle
- [ ] L. `FileListItem` trailing star (+ `SwipeableFileListItem` passthrough)
- [ ] M. `FolderScreen` wiring
- [ ] N/O. `FavoritesSection` + `FavoriteFileCard`
- [ ] P. `FavoriteFileActionsBottomSheet`
- [ ] Q. `HomeScreen` wiring (section + sheet + delete dialog + Recents-sheet toggle)
- [ ] R. `SettingsScreen` "Clear favorites" row
- [ ] §4. Strings in all 20 locales
- [ ] §5. Tests
- [ ] §6. Build, unit tests, `delta-review`, manual light/dark + RTL check
