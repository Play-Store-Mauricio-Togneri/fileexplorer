# File Explorer — UI/UX review

- **Date:** 2026-09-28
- **Commit:** `29109a6` (branch `release/2.7.0`)
- **Device:** Android emulator, API 36, gesture navigation. Package
  `com.mauriciotogneri.fileexplorer`, launched via `.activities.MainActivity`.
- **Captured:** 19 screens, covering all 15 `ui/screens/` packages and all 15 Activities, plus the
  drawer, the progress UI, Feedback, Legal and Other apps. 903 screenshots: 153 `default` and 150
  each for `dark`, `ar`, `de`, `land` and `tablet`. Only the 109 shots linked from this report were
  kept. [`manifest.md`](manifest.md) lists them with the steps to reproduce each one.
- **Not reachable, or reached another way:**
    - **`ACTION_GET_CONTENT` / `OPEN_DOCUMENT`:** the app declares no intent filter for either, so
      both open the system DocumentsUI. Those 2 shots are `default` only and are not reviewed. The
      app's own picker is the copy/move/startup-folder destination picker, and that is fully
      covered.
    - **Name-conflict dialog:** there is none. Paste silently auto-renames to `name (1).ext`, and
      the manifest captures that result instead.
    - **List/grid view mode:** it doesn't exist. All 6 sort options were captured.
    - **First-launch drawer badges:** `default` only, because they need `pm clear`.
    - **Text viewer:** on this emulator `.txt` and `.log` open a system chooser, because Chrome
      handles `text/plain`. The text-viewer shots use `.json` and `.md` files instead.
    - **Landscape "⋮" sheet actions** (Info, Favorites, Compress, Delete): not reachable in
      landscape (see UX-02). Those flows were opened in portrait and rotated just before the shot.
    - **German single-selection Delete:** not tappable (see UX-05). The `de` delete-confirm shots
      were reached through the row sheet.
    - **Transient states** (scanning, search in progress, 250 MB copy): captured in all 6 variants
      after slowing them with 100k empty files. Frame timing differs between variants.
- **Refute pass:** 69 findings from 6 areas.
    - **Dropped by the refuters (3):** L-6, the storage-card wording, which is shared with the
      Analyzer and so is consistent. A-8, analyzer Select all selects one page, which is documented
      as intended and is not silent. S-6, moving Theme to the top of Settings, which rests on an
      unsupported premise and goes against a deliberate group order.
    - **Dropped at merge (1):** S-1 ("Clear recent files" is disabled while tracking is off). The
      code confirms it, but no screenshot shows it; the cited `ar` shots have tracking on.
    - **Sub-points cut from kept findings:** the Back half of A-7, part 2 of F-1, V-5's clear
      button (a new action), the Sort-sheet half of B-2, and V-4's full-screen overlay point.
    - **Tier changes:** all three browse Blocking findings (B-1, B-2, B-3), L-2 and V-1 were lowered
      to Friction. B-5, B-7, B-9, L-5, A-7 and V-8 were lowered to Polish.
    - **Result:** the 65 surviving findings merge into 52 items: **Blocking 1 · Friction 26 · Polish
      25**.
- **Ranking:** by tier, then by reach × impact ÷ effort within each tier (H=3, M=2, L=1; effort S=1,
  M=2, L=3). Effort counts translations: a new or reworded string costs 20 locales.
- **Paths:** code paths are relative to `app/src/main/java/com/mauriciotogneri/fileexplorer/` unless
  they start with `app/`. Screenshot links are relative to this file.
- **Colours:** the grey palette comes from the app's own theme (`ui/theme/Color.kt`) and is not
  reported as an issue.

## Summary

| ID    | Tier     | Area                            | Title                                                                                                       | Effort |
|-------|----------|---------------------------------|-------------------------------------------------------------------------------------------------------------|--------|
| UX-01 | Blocking | Launch                          | "Grant Permission" does nothing after "Don't ask again" (Android 7–10)                                      | S–M    |
| UX-02 | Friction | Browse, Launch, Find            | Action and filter sheets don't scroll; landscape cuts off their last actions                                | S      |
| UX-03 | Friction | Launch                          | Recent/Favorites rows give no sign that they scroll on 411 dp phones                                        | S      |
| UX-04 | Friction | Analyzer                        | "System" and 0 B rows look tappable but lead to a 2 s toast or an empty screen                              | S      |
| UX-05 | Friction | Browse                          | Selection bar overflows in long locales; German single-select loses Delete                                  | S      |
| UX-06 | Friction | Viewers, Browse, Analyzer       | LTR content (text files, names, paths) is reordered under RTL UI                                            | S      |
| UX-07 | Friction | Browse, Launch, Find            | File action sheets don't name their file, order actions differently, and leave Delete untinted              | S–M    |
| UX-08 | Friction | Find                            | Search results come in file-system walk order                                                               | S      |
| UX-09 | Friction | Browse                          | Compress starts with an empty name; the three name dialogs label their field three ways                     | S      |
| UX-10 | Friction | Browse                          | Copy and Move finish silently, including the silent auto-rename                                             | M      |
| UX-11 | Friction | Launch                          | Permission screen doesn't say what to switch on, and looks the same after a failed attempt                  | M      |
| UX-12 | Friction | Find                            | "No results found" never mentions the active filters                                                        | M      |
| UX-13 | Friction | Find, Analyzer                  | Search and analyzer category rows don't show where each file lives                                          | M      |
| UX-14 | Friction | Find                            | Destination picker shows a blank body in folders without subfolders                                         | S–M    |
| UX-15 | Friction | Find                            | Picker "New folder" fails silently, and its duplicate check misses most names                               | S      |
| UX-16 | Friction | Viewers                         | PDF search runs only on the IME key, and its match count goes stale                                         | S      |
| UX-17 | Friction | Viewers                         | "Go to page" hides behind a page counter that looks like a label                                            | S      |
| UX-18 | Friction | Viewers                         | In the text viewer, a wrapped line looks the same as a new line                                             | S      |
| UX-19 | Friction | Browse                          | Item Info has no top bar, and its only exit scrolls away                                                    | S–M    |
| UX-20 | Friction | Settings                        | Feedback sends device details without saying so                                                             | M      |
| UX-21 | Friction | Find                            | Search doesn't show it's still running once results fill the screen, or that it stopped at 100              | M      |
| UX-22 | Friction | Analyzer                        | Analyzer results in landscape show only the donut; the list is below the fold                               | M      |
| UX-23 | Friction | Settings                        | "Clear recent files" doesn't confirm; "Clear favorites" confirms with an ambiguous bare title               | M      |
| UX-24 | Friction | Viewers                         | Image/PDF/text viewers keep both bars in landscape, leaving about half the height                           | M      |
| UX-25 | Friction | Viewers                         | "Full screen" on a portrait phone leaves a landscape video the same size                                    | M      |
| UX-26 | Friction | Viewers                         | Media controls take 35–40 % of the height in landscape; audio artwork shrinks to ~40 dp                     | M      |
| UX-27 | Friction | Browse                          | A folder holding only hidden files reads "Empty folder", and its delete confirmation gives no hint          | M      |
| UX-28 | Polish   | Browse, Launch                  | Folder title shows the entry point, not the current folder, and the current breadcrumb is the faintest text | S      |
| UX-29 | Polish   | Browse                          | Sort sheet has no title and marks the active choice with an inverted shade                                  | S      |
| UX-30 | Polish   | Launch                          | Drawer lists Settings first and has no header or grouping                                                   | S      |
| UX-31 | Polish   | Launch                          | English permission strings are Title Case; the rest of the app is sentence case                             | S      |
| UX-32 | Polish   | Analyzer, Browse                | Raw `/storage/emulated/0` mount path in the scan progress and Item Info                                     | S      |
| UX-33 | Polish   | Analyzer                        | Scan "Cancel" is a filled primary button and is named differently from the "Stop" it confirms               | S      |
| UX-34 | Polish   | Analyzer                        | Scanned-file count has no digit grouping                                                                    | S      |
| UX-35 | Polish   | Find                            | Picker title doesn't say what is being copied or moved                                                      | S      |
| UX-36 | Polish   | Browse                          | Transfer progress dialog shows a bare bar with no amount or percentage                                      | S      |
| UX-37 | Polish   | Analyzer                        | Category bars measure a different whole than the donut (owner's call)                                       | S      |
| UX-38 | Polish   | Analyzer                        | Analyzer results don't name the analyzed volume                                                             | S      |
| UX-39 | Polish   | Browse                          | Item Info shows a bogus "Jan 1, 1904" date and no sign of the folder size                                   | S–M    |
| UX-40 | Polish   | Settings                        | Rows that open the Play Store use the in-app navigation chevron                                             | S      |
| UX-41 | Polish   | Settings                        | Landscape option dialogs hide choices with no scroll cue                                                    | S      |
| UX-42 | Polish   | Launch, Settings (app-wide)     | No screen limits its content width on tablets or in landscape                                               | S–M    |
| UX-43 | Polish   | Browse                          | Hidden items look exactly like normal ones when shown                                                       | S      |
| UX-44 | Polish   | Browse, Find, Settings, Viewers | Landscape display-cutout inset leaves blank strips or misaligned rows                                       | S      |
| UX-45 | Polish   | Find                            | Picker's "can't copy here" reason is small print below the disabled button                                  | S      |
| UX-46 | Polish   | Viewers                         | Rotation closes viewer dialogs and resets zoom                                                              | S      |
| UX-47 | Polish   | Find                            | Filter sheets have no titles, and "Any" sits beside "Any type" with the same icon                           | S–M    |
| UX-48 | Polish   | Browse                          | Swiped rows stay open: several at once, and behind dialogs                                                  | M      |
| UX-49 | Polish   | Settings                        | Arabic/Urdu privacy policy: two Firebase bullets flip to LTR                                                | S      |
| UX-50 | Polish   | Settings                        | Feedback field is a fixed 150 dp box above an empty screen                                                  | S      |
| UX-51 | Polish   | Viewers                         | The four viewers show errors four different ways; the image error is top-aligned                            | S–M    |
| UX-52 | Polish   | Viewers                         | A finished video goes black after rotation                                                                  | S      |

---

## Friction

### UX-07 — File action sheets don't name their file, order actions differently, and leave Delete untinted

- **Tier:** Friction · **Areas:** Browse, Launch, Find · **Variants:** all
- **Screens:** browse "⋮" sheet, Home recent and favorite sheets, search result sheet, analyzer file
  sheet
- **Evidence:**
    - Screenshots:
        - [browse__folder__file_actions_sheet__default.png](shots/browse__folder__file_actions_sheet__default.png)
        - [launch__home__recent_actions_sheet__default.png](shots/launch__home__recent_actions_sheet__default.png)
        - [launch__home__favorite_actions_sheet__default.png](shots/launch__home__favorite_actions_sheet__default.png)
        - [find__search__result_actions_sheet__default.png](shots/find__search__result_actions_sheet__default.png)
    - Code:
        - `ui/components/FileActionsBottomSheet.kt:81-196,201-216` (no header; every item
          `onSurfaceVariant`; Delete between Favorites and Info)
        - `ui/components/FavoriteFileActionsBottomSheet.kt:93,102` (Share before Open folder)
        - `ui/components/SearchFileActionsBottomSheet.kt:76-138`
- **Problem:** The sheet rises over the row that opened it, and nothing in it names the file, so the
  user acts on trust. The same actions sit in different positions per screen: Favorites swaps Share
  and Open folder, and Browse puts Share before Open with. Delete, the only destructive action, sits
  mid-list styled like the rest, even though the app tints it `error` in the swipe button and the
  delete dialog.
- **Proposal:**
    - Add a non-interactive header row: the list-row icon or thumbnail, the name in `bodyLarge` (2
      lines, ellipsis), and size or item count in `bodySmall` / `onSurfaceVariant`, followed by a
      `HorizontalDivider(color = outlineVariant)`.
    - Use one order everywhere: Open with → Open folder → Share → screen-specific actions →
      Favorite → Info → divider → Delete.
    - Tint Delete's icon and text `colorScheme.error`.
    - Extract `FileListItem`'s icon logic into a shared composable for the header.
- **Reach:** High · **Impact:** Med · **Effort:** S–M (no strings)
- **Source:** B-6, L-7, F-12

### UX-08 — Search results come in file-system walk order

- **Tier:** Friction · **Area:** Find · **Variants:** all
- **Screens:** Search
- **Evidence:
  ** [find__search__hits_many_keyboard_hidden__default.png](shots/find__search__hits_many_keyboard_hidden__default.png)
  shows file_180, 173, 176, 190…
    - `ui/screens/search/SearchViewModel.kt:305-311`: each hit is appended.
    - `data/repository/FileRepository.kt:1145`: the walk uses unsorted `dir.list()`.
- **Problem:** Users have to read every row to find the one they want. The folder screen always
  sorts, so search feels inconsistent with it.
- **Proposal:** Sort the results by the folder sort preference, using `preferencesRepository` and
  `FileRepository.sortInPlace`. Sort once `searchComplete` is set, so rows don't shift under a
  pending tap. Pair it with UX-21's cap notice, so a sorted list of 100 doesn't look complete.
- **Reach:** Med · **Impact:** Med · **Effort:** S (no strings)
- **Source:** F-3

### UX-09 — Compress starts with an empty name; the three name dialogs label their field three ways

- **Tier:** Friction · **Area:** Browse · **Variants:** all
- **Screens:** Compress, Rename and New folder dialogs, including the picker's New folder
- **Evidence:**
    - Screenshots:
        - [browse__folder__compress_dialog__default.png](shots/browse__folder__compress_dialog__default.png)
        - [browse__folder__rename_dialog__default.png](shots/browse__folder__rename_dialog__default.png)
        - [browse__folder__create_folder_dialog__default.png](shots/browse__folder__create_folder_dialog__default.png)
        - [find__picker__new_folder_dialog__default.png](shots/find__picker__new_folder_dialog__default.png)
    - Code:
        - `ui/components/CompressDialog.kt:42` (`mutableStateOf("")`)
        - `ui/components/CompressDialog.kt:76-80` ("Enter file name:" body line)
        - `ui/components/CompressDialog.kt:86-98` (".zip" chip always shown)
        - `ui/components/RenameDialog.kt:87-102` and `ui/components/CreateFolderDialog.kt:68-83` (no
          `label`)
- **Problem:**
    - Compress always makes the user type a name from scratch, although the obvious default is
      known.
    - Typing `x.zip` shows `x.zip .zip`.
    - Three dialogs for the same "enter a name" task use three different styles.
- **Proposal:**
    - Pass `suggestedName` into `CompressDialog`: the name stem for a single item,
      `File(currentPath).name` for several. Hold it as a fully selected `TextFieldValue`, as
      `RenameDialog` does.
    - Hide the ".zip" chip when the name already ends in `.zip`.
    - In all three dialogs, drop the body line and set
      `label = { Text(stringResource(R.string.info_name)) }`.
    - Retire `compress_enter_name` from the 20 `strings.xml` files.
- **Reach:** Med · **Impact:** Med · **Effort:** S (no new strings)
- **Source:** B-8

### UX-10 — Copy and Move finish silently, including the silent auto-rename

- **Tier:** Friction · **Area:** Browse · **Variants:** all
- **Screens:** folder list after copy/move
- **Evidence:**
    - Screenshots:
        - [browse__folder__copy_conflict_result__default.png](shots/browse__folder__copy_conflict_result__default.png)
        - [browse__folder__copy_conflict_auto_renamed__default.png](shots/browse__folder__copy_conflict_auto_renamed__default.png)
        - [browse__folder__move_done__default.png](shots/browse__folder__move_done__default.png)
    - Code: `ui/screens/folder/FolderViewModel.kt:744-746`. The success branch only sends analytics;
      partial and failed outcomes do toast (`:710-743`, `:760-813`).
- **Problem:** Copy doesn't change the current view, so there is no sign it worked. Move makes files
  vanish with no hint where they went. When a name collided, the copy was silently saved as
  `name (1).ext`.
- **Proposal:**
    - On success, emit `FolderUiEvent.ShowTransferSuccess(mode, count)` and show it as a toast, like
      the existing partial-success toasts. This needs new plurals `copy_success` ("Copied %d item(
      s)") and `move_success`.
    - When any item was auto-renamed, say so in the same message.
    - Optionally use a Snackbar with the existing `action_open_folder` to jump to the destination.
- **Reach:** High · **Impact:** Med · **Effort:** M (2 plurals × 20 locales, with extra quantities
  for ar, ru and ro)
- **Source:** B-4

### UX-11 — Permission screen doesn't say what to switch on, and looks the same after a failed attempt

- **Tier:** Friction · **Area:** Launch · **Variants:** all
- **Screens:** permission screen
- **Evidence:**
    - Screenshots:
        - [launch__permission__first_launch_denied__default.png](shots/launch__permission__first_launch_denied__default.png)
          and [launch__permission__returned_still_denied__default.png](shots/launch__permission__returned_still_denied__default.png)
          are identical apart from the clock.
        - [launch__permission__system_settings_page__default.png](shots/launch__permission__system_settings_page__default.png)
        - [launch__permission__first_launch_denied__land.png](shots/launch__permission__first_launch_denied__land.png)
    - Code:
        - `ui/screens/permission/PermissionScreen.kt:94-99`: the app detects a return without a
          grant, but only sends analytics.
        - `ui/screens/permission/PermissionScreen.kt:146-166`
- **Problem:** Every new Android 11+ user is sent to a system page titled "All files access", with a
  stark warning, that the app never prepared them for. A user who backs out lands on the same
  unchanged screen and taps again, or gives up. This is the app's first-run conversion step.
- **Proposal:**
    - Add a line under the message: "On the next screen, turn on the switch for File Explorer."
    - Turn the existing "returned without grant" branch into `rememberSaveable` state that shows one
      more `bodyMedium` line in `colorScheme.error`: "Access is still off. Turn on the switch, then
      come back."
    - Make the column `verticalScroll`, because in landscape the button already sits at the bottom
      edge.
- **Reach:** High · **Impact:** Med · **Effort:** M (2 strings × 20)
- **Source:** L-3

### UX-12 — "No results found" never mentions the active filters

- **Tier:** Friction · **Area:** Find · **Variants:** all
- **Screens:** Search
- **Evidence:**
  -
  Screenshots: [find__search__no_results__default.png](shots/find__search__no_results__default.png), [find__search__empty_query__default.png](shots/find__search__empty_query__default.png) (
  the default Kind is Files)
    - Code:
        - `data/model/SearchFilters.kt:51` (`itemKind = FILES`)
        - `ui/screens/search/SearchScreen.kt:235-244` (no-results branch is a bare `Text`)
- **Problem:** A user searching for a folder they know exists gets "No results found", because
  folders are excluded by default and the message says nothing about it. The same happens after
  narrowing Type earlier in the session. The chip does show the value, which softens this.
- **Proposal:**
    - In the no-results branch, when `itemKind != ANY || selectedTypes.isNotEmpty()`, add a second
      centred line in `bodyMedium` / `onSurfaceVariant`: `search_no_results_filters_hint` = "Filters
      above may be hiding matches".
    - Separately, fix the stale KDoc at `SearchFiltersBar.kt:56-61`: the chips are deliberately
      never filled (`:81-82`).
- **Reach:** High · **Impact:** Med · **Effort:** M (1 string × 20)
- **Source:** F-1 (narrowed: the chip-styling part and the `includeHidden` trigger were dropped)

### UX-13 — Search and analyzer category rows don't show where each file lives

- **Tier:** Friction · **Areas:** Find, Analyzer · **Variants:** all
- **Screens:** Search results, Analyzer category list
- **Evidence:**
    - Screenshots:
        - [find__search__hits_many_keyboard_hidden__default.png](shots/find__search__hits_many_keyboard_hidden__default.png)
        - [analyzer__analyzercategory__images__default.png](shots/analyzer__analyzercategory__images__default.png)
        - [find__search__result_actions_sheet__default.png](shots/find__search__result_actions_sheet__default.png)
    - Code:
        - `ui/screens/search/SearchScreen.kt:255-300`
        - `ui/screens/analyzercategory/AnalyzerCategoryScreen.kt:363-373`
        - `ui/components/FileListItem.kt:102,280-283` (the second line can only be size, date or
          none)
        - `ui/components/SearchFileActionsBottomSheet.kt:76` (Open folder is gated by
          `!file.isDirectory`)
- **Problem:** Both lists span a whole volume, and rows show only name and size. Same-named files (
  `IMG_0001.jpg`, `README.md`) look identical, and "do I still need this?" depends on where a file
  lives. Finding out takes a sheet and then Info, per row. Folder results can't reveal their parent
  at all.
- **Proposal:**
    - Add an optional `locationLine: String?` to `FileListItem`, rendered in `bodySmall` /
      `onSurfaceVariant` on one line with `TextOverflow.StartEllipsis`.
    - Compute "Storage name/relative/parent" once per hit or page in the view models (search stream,
      analyzer page), not during composition. Apply UX-06's LTR direction to it.
    - Show "Open folder" for folder results too; `SearchScreen.kt:345-348` already opens the parent.
- **Reach:** High · **Impact:** Med · **Effort:** M (no strings)
- **Source:** F-2, A-9

### UX-14 — Destination picker shows a blank body in folders without subfolders

- **Tier:** Friction · **Area:** Find · **Variants:** all
- **Screens:** copy/move/startup destination picker
- **Evidence:**
  -
  Screenshots: [find__picker__copy_to_destination__default.png](shots/find__picker__copy_to_destination__default.png), [find__picker__move_to_destination__tablet.png](shots/find__picker__move_to_destination__tablet.png)
    - Code: `ui/screens/picker/FolderPickerContent.kt:62-81` has no empty branch. Compare
      `ui/components/EmptyState.kt:29-44`.
- **Problem:** Most copy/move flows end in a leaf folder, so the screen seen right before confirming
  is blank. It looks the same as loading or failed.
- **Proposal:** Add `folders.isEmpty() -> EmptyState(messageResId = R.string.picker_no_subfolders)`
  with the new string "No subfolders". Don't reuse "Empty folder": the folder may hold files.
- **Reach:** High · **Impact:** Low–Med · **Effort:** S–M (1 string × 20)
- **Source:** F-7

### UX-15 — Picker "New folder" fails silently, and its duplicate check misses most names

- **Tier:** Friction · **Area:** Find · **Variants:** all
- **Screens:** destination picker, New folder
- **Evidence:**
  -
  Screenshots: [find__picker__new_folder_dialog__default.png](shots/find__picker__new_folder_dialog__default.png)
    - Code:
        - `ui/screens/picker/PickerViewModel.kt:221-234` (no failure branch)
        - `ui/screens/picker/PickerViewModel.kt:236,117-120` (existing names = listed writable
          folders only)
        - `ui/screens/folder/FolderViewModel.kt:886-898` (the folder screen toasts `create_error`)
- **Problem:** Typing the name of an existing file, or of a hidden or read-only folder, passes
  validation. `mkdir()` then fails, the dialog closes and nothing is said. The same action on the
  folder screen shows an error.
- **Proposal:**
    - Return `File(path).list()` from `getExistingNames()`, computed on `ioDispatcher`, so the
      dialog shows its existing inline `error_name_exists`.
    - Emit a one-shot `R.string.create_error` toast on failure.
- **Reach:** Low–Med · **Impact:** Med · **Effort:** S (existing strings)
- **Source:** F-6

### UX-16 — PDF search runs only on the IME key, and its match count goes stale

- **Tier:** Friction · **Area:** Viewers · **Variants:** all
- **Screens:** PDF viewer search
- **Evidence:**
  -
  Screenshots: [viewers__pdfviewer__search_open__default.png](shots/viewers__pdfviewer__search_open__default.png), [viewers__pdfviewer__search_hits__default.png](shots/viewers__pdfviewer__search_hits__default.png)
    - Code:
        - `ui/screens/pdfviewer/PdfViewerScreen.kt:644-650` (search only from
          `KeyboardActions(onSearch)`)
        - `ui/screens/pdfviewer/PdfViewerViewModel.kt:434-436` (`onSearchQueryChange` leaves the old
          results)
        - `ui/screens/pdfviewer/PdfViewerScreen.kt:680`
- **Problem:** Users who know the app's file search, which runs as you type, type and wait. After
  searching "fox", editing to "zebr" still shows "1 of 6 matches" and the fox highlights.
- **Proposal:**
    - In `onSearchQueryChange`, when the trimmed query differs from `searchedQuery`, cancel
      `searchJob` and reset `searchedQuery`, `matches`, `currentIndex` and `inProgress`.
    - Optionally debounce by about 300 ms and call `submitSearch()`. The search is already
      cancellable.
- **Reach:** Low–Med · **Impact:** Med · **Effort:** S (M with the debounce)
- **Source:** V-5 (narrowed: the clear button was dropped as a new action)

### UX-17 — "Go to page" hides behind a page counter that looks like a label

- **Tier:** Friction · **Area:** Viewers · **Variants:** all
- **Screens:** PDF viewer
- **Evidence:**
  -
  Screenshots: [viewers__pdfviewer__page1__default.png](shots/viewers__pdfviewer__page1__default.png), [viewers__pdfviewer__go_to_page_dialog__default.png](shots/viewers__pdfviewer__go_to_page_dialog__default.png)
    - Code: `ui/screens/pdfviewer/PdfViewerScreen.kt:585-599` (a `Surface` + `Text` chip, the only
      entry point)
- **Problem:** Jumping to a page is a core reading task. Its only entry point reads as status, so
  most users will scroll instead.
- **Proposal:** Add the app's existing chip caret after the text:
  `Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp))`, as in `ChipCaret` at
  `ui/components/SearchFiltersBar.kt:310-316`. Seeding the dialog field with the current page, fully
  selected, is optional.
- **Reach:** Med · **Impact:** Low–Med · **Effort:** S (no strings)
- **Source:** V-6

### UX-18 — In the text viewer, a wrapped line looks the same as a new line

- **Tier:** Friction · **Area:** Viewers · **Variants:** default, dark, ar, de (phone portrait)
- **Screens:** text viewer
- **Evidence:**
  -
  Screenshots: [viewers__textviewer__large_file_truncated__default.png](shots/viewers__textviewer__large_file_truncated__default.png) (
  compare [the tablet shot](shots/viewers__textviewer__large_file_truncated__tablet.png))
    - Code: `ui/screens/textviewer/TextViewerScreen.kt:186-195` (no `textIndent`)
- **Problem:** On a phone, log records wrap onto several rows at the same left edge, so continuation
  rows read as new records.
- **Proposal:** Add a hanging indent, `textIndent = TextIndent(firstLine = 0.sp, restLine = 2.em)`,
  in the same `remember`ed `TextStyle` as UX-06's `textDirection`.
- **Reach:** Med · **Impact:** Low–Med · **Effort:** S
- **Source:** V-7

### UX-19 — Item Info has no top bar, and its only exit scrolls away

- **Tier:** Friction · **Area:** Browse · **Variants:** all; worst in land
- **Screens:** Item Info
- **Evidence:**
  -
  Screenshots: [browse__iteminfo__video_mp4_scrolled__default.png](shots/browse__iteminfo__video_mp4_scrolled__default.png), [browse__iteminfo__image__land.png](shots/browse__iteminfo__image__land.png)
    - Code:
        - `ui/screens/iteminfo/ItemInfoScreen.kt:291-312` (✕ inside the `verticalScroll`)
        - `ui/screens/iteminfo/ItemInfoScreen.kt:101` (`PreviewHeight = 200.dp`)
- **Problem:** Item Info is the only full screen without an app bar. Its ✕ sits top-end instead of
  top-start, there is no title, and both scroll away. In landscape the preview fills the first
  screen.
- **Proposal:**
    - Use a `Scaffold` with a `TopAppBar`: an `ArrowBack` navigation icon with `navigate_back`, the
      file name as title (1 line, ellipsis), and the same colours as `FolderScreen`.
    - Remove the in-content ✕.
    - Cap the preview at 120 dp when the height is compact.
- **Reach:** Med · **Impact:** Med · **Effort:** S–M (existing strings)
- **Source:** B-10

### UX-20 — Feedback sends device details without saying so

- **Tier:** Friction · **Area:** Settings · **Variants:** all
- **Screens:** Feedback, Legal (Privacy Policy)
- **Evidence:**
  -
  Screenshots: [settings__feedback__typed__default.png](shots/settings__feedback__typed__default.png), [settings__legal__privacy_top__default.png](shots/settings__legal__privacy_top__default.png)
    - Code:
        - `activities/FeedbackActivity.kt:209-242` (the payload adds model, board, resolution,
          timezone, RAM, storage and more)
        - `activities/FeedbackActivity.kt:269-270` (posts to a `script.google.com` endpoint)
        - `activities/FeedbackActivity.kt:346-407` (no disclosure in the UI)
        - `app/src/main/res/raw/privacy.md:7`
- **Problem:** Users think they are sending the sentence they typed, but a device profile goes with
  it. The Privacy Policy in the same menu says the app communicates with no external servers, and
  nothing says a reply is impossible.
- **Proposal:** Add one line under Submit, in `bodySmall` / `onSurfaceVariant`:
  `feedback_disclosure` = "Your message is sent together with your device model, Android and app
  version, and language to help diagnose problems. We can't reply to feedback." Aligning
  `raw*/privacy.md` is a separate legal decision for the owner and is outside this item.
- **Reach:** Low–Med · **Impact:** High (trust) · **Effort:** M (1 string × 20)
- **Source:** S-4 (narrowed to the disclosure line)

### UX-21 — Search doesn't show it's still running once results fill the screen, or that it stopped at 100

- **Tier:** Friction · **Area:** Find · **Variants:** all
- **Screens:** Search
- **Evidence:**
  -
  Screenshots: [find__search__hits_many_keyboard_hidden__default.png](shots/find__search__hits_many_keyboard_hidden__default.png), [find__search__in_progress__default.png](shots/find__search__in_progress__default.png)
    - Code:
        - `ui/screens/search/SearchScreen.kt:307-318` (the spinner is the last list item)
        - `ui/screens/search/SearchViewModel.kt:413` (`MAX_RESULTS = 100`)
        - `ui/screens/search/SearchViewModel.kt:306` (hits past the cap are dropped)
- **Problem:** Once hits fill the screen, the spinner is below the fold, so users act on an
  incomplete list. A broad query simply ends at 100 without saying so.
- **Proposal:**
    - While `isSearching`, replace the divider under the filter bar with an indeterminate
      `LinearProgressIndicator`, and drop the in-list spinner.
    - When the search is complete at the cap, add a footer item: new plural
      `search_results_capped` = "Showing the first %d results. Type more to narrow them."
- **Reach:** Med · **Impact:** Med · **Effort:** M (1 plural × 20)
- **Source:** F-4

### UX-22 — Analyzer results in landscape show only the donut; the list is below the fold

- **Tier:** Friction · **Area:** Analyzer · **Variants:** land (main), tablet
- **Screens:** Analyzer results, and volume selection in land
- **Evidence:**
    - Screenshots:
        - [analyzer__analyzer__done__land.png](shots/analyzer__analyzer__done__land.png)
        - [analyzer__analyzer__system_explanation__land.png](shots/analyzer__analyzer__system_explanation__land.png)
        - [analyzer__analyzer__done__tablet.png](shots/analyzer__analyzer__done__tablet.png)
    - Code: `ui/screens/analyzer/AnalyzerScreen.kt:401-430` (one `LazyColumn`),
      `ui/screens/analyzer/StorageDonutChart.kt:54` (220 dp)
- **Problem:** After a multi-minute scan, a landscape user sees a ring with no labels and no list,
  which looks like the whole result. The chart and the rows are never on screen together.
- **Proposal:** In `ScanResults`, use `BoxWithConstraints`. When
  `maxWidth > maxHeight || maxWidth >= 600.dp`, show a `Row` with the chart (weight 0.4, centred)
  and the category `LazyColumn` (weight 0.6). Otherwise keep the column.
- **Reach:** Med · **Impact:** Med · **Effort:** M (no strings)
- **Source:** A-1

### UX-23 — "Clear recent files" doesn't confirm; "Clear favorites" confirms with an ambiguous bare title

- **Tier:** Friction · **Area:** Settings · **Variants:** all; worst in de/es/fr
- **Screens:** Settings, Data group
- **Evidence:**
    - Screenshots:
        - [settings__settings__recent_cleared_toast__default.png](shots/settings__settings__recent_cleared_toast__default.png)
        - [settings__settings__favorites_clear_confirm__default.png](shots/settings__settings__favorites_clear_confirm__default.png)
        - [settings__settings__favorites_clear_confirm__de.png](shots/settings__settings__favorites_clear_confirm__de.png) ("
          Favoriten löschen" — "Löschen")
    - Code:
        - `activities/SettingsActivity.kt:174-177` (recents cleared directly)
        - `activities/SettingsActivity.kt:463-471`
        - `activities/SettingsActivity.kt:1195-1222` (`ClearFavoritesConfirmDialog` has no `text`)
        - `app/src/main/res/values-de/strings.xml:10`
- **Problem:** Two adjacent, irreversible "Clear…" rows behave differently: one tap, or a stray tap
  while scrolling, wipes the history. In German, Spanish and French the favorites dialog reads "
  Delete favorites? — Delete", which sounds like deleting the files.
- **Proposal:**
    - Generalise the dialog to `ClearDataConfirmDialog(title, message, …)` and route Clear recent
      files through it.
    - Add body lines: "Removes all folders and files from Favorites. The files themselves are not
      deleted." and the matching recents line.
- **Reach:** Med · **Impact:** Med · **Effort:** M (2 strings × 20; S for the recents confirmation
  alone)
- **Source:** S-2, S-3

### UX-24 — Image/PDF/text viewers keep both bars in landscape, leaving about half the height

- **Tier:** Friction · **Area:** Viewers · **Variants:** land
- **Screens:** image, PDF and text viewers
- **Evidence:**
    - Screenshots:
        - [viewers__imageviewer__landscape_image__land.png](shots/viewers__imageviewer__landscape_image__land.png):
          52 % of the height left for the image.
        - [viewers__pdfviewer__search_hits__land.png](shots/viewers__pdfviewer__search_hits__land.png):
          39 % with search open.
        - [viewers__textviewer__markdown__land.png](shots/viewers__textviewer__markdown__land.png)
    - Code:
        - `ui/screens/imageviewer/ImageViewerScreen.kt:119-130`
        - `ui/screens/pdfviewer/PdfViewerScreen.kt:211-222`
        - `ui/screens/textviewer/TextViewerScreen.kt:111-122`
        - Each is an 80 dp `BottomAppBar` under a 64 dp `TopAppBar`.
- **Problem:** People rotate to landscape to see more, and here two permanent bars take almost half
  of that height. Reading a PDF means scrolling every few lines.
- **Proposal:** When the height is compact (under 480 dp), drop `bottomBar` and move Share and
  Delete into `TopAppBar` actions as Outlined `IconButton`s, reusing `action_share` and
  `action_delete`. Consolidate the three copy-pasted action bars into one shared
  `ViewerActions(compact)`.
- **Reach:** Med (among viewer users; the viewers only open when no other app handles the file) · *
  *Impact:** Med · **Effort:** M (no strings)
- **Source:** V-2

### UX-25 — "Full screen" on a portrait phone leaves a landscape video the same size

- **Tier:** Friction · **Area:** Viewers · **Variants:** default, dark, ar, de
- **Screens:** media viewer (video)
- **Evidence:**
  -
  Screenshots: [viewers__mediaviewer__video_playing__default.png](shots/viewers__mediaviewer__video_playing__default.png)
  and [viewers__mediaviewer__video_fullscreen__default.png](shots/viewers__mediaviewer__video_fullscreen__default.png)
  have the same 506 px picture band.
    - Code:
        - `ui/screens/mediaviewer/MediaViewerViewModel.kt:235-237`
        - `ui/screens/mediaviewer/MediaViewerScreen.kt:686-701` (`ImmersiveMode` only hides the
          system bars)
- **Problem:** The user taps Full screen expecting a bigger picture, and nothing visible changes.
- **Proposal:**
    - Expose `videoIsLandscape` from `player.videoSize`.
    - While `fullscreen && videoIsLandscape`, set
      `requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, and restore `UNSPECIFIED` in
      `onDispose`.
    - The player already survives the rotation.
- **Reach:** Med · **Impact:** Med · **Effort:** M (no strings)
- **Source:** V-3

### UX-26 — Media controls take 35–40 % of the height in landscape; audio artwork shrinks to ~40 dp

- **Tier:** Friction · **Area:** Viewers · **Variants:** land; tablet for audio
- **Screens:** media viewer (video and audio)
- **Evidence:**
    - Screenshots:
        - [viewers__mediaviewer__video_playing__land.png](shots/viewers__mediaviewer__video_playing__land.png)
        - [viewers__mediaviewer__audio_mp3_playing__land.png](shots/viewers__mediaviewer__audio_mp3_playing__land.png)
        - [viewers__mediaviewer__audio_mp3_playing__tablet.png](shots/viewers__mediaviewer__audio_mp3_playing__tablet.png)
    - Code:
        - `ui/screens/mediaviewer/MediaViewerScreen.kt:456-578` (three stacked tiers)
        - `ui/screens/mediaviewer/MediaViewerScreen.kt:367-375,407-422`
- **Problem:** In landscape the controls take as much room as the video. Audio loses its artwork
  almost entirely, and on tablet the controls stretch the full width.
- **Proposal:**
    - When the height is compact, lay out one `Row` about 64 dp tall: play (48 dp), position,
      `Slider` with `weight(1f)`, duration, mute, fullscreen.
    - For audio in landscape, put the artwork on the start side and the track info plus controls on
      the end side.
    - On tablet, cap the controls at `widthIn(max = 600.dp)`.
- **Reach:** Med · **Impact:** Med · **Effort:** M (no strings)
- **Source:** V-4 (the full-screen overlay point was dropped, because the controls auto-hide)

### UX-27 — A folder holding only hidden files reads "Empty folder", and its delete confirmation gives no hint

- **Tier:** Friction · **Area:** Browse · **Variants:** all
- **Screens:** folder list, empty state, delete confirmation, including swipe-delete
- **Evidence:**
    - Screenshots:
        - [browse__folder__uxreview_list__default.png](shots/browse__folder__uxreview_list__default.png):
          `hidden` reads "1 item".
        - [browse__folder__hidden_items_shown__default.png](shots/browse__folder__hidden_items_shown__default.png):
          it actually holds 4 entries.
        - [browse__folder__empty_state__default.png](shots/browse__folder__empty_state__default.png)
        - [browse__folder__delete_confirm_dialog__default.png](shots/browse__folder__delete_confirm_dialog__default.png)
    - Code:
        - `data/repository/FileRepository.kt:189-200` (`countChildren` drops dotfiles)
        - `ui/screens/folder/FolderScreen.kt:384-395`
        - `ui/components/DeleteConfirmDialog.kt:43-50`
- **Problem:** With hidden items off (the default), a dotfile-only folder shows "0 items", and
  inside it says "Empty folder". The delete dialog shows only the name, so a user tidying up gets no
  reason to hesitate. Excluding dotfiles from the count is a documented choice, and the delete is
  explicit and confirmed. What is missing is the hint.
- **Proposal:**
    - For a single-directory delete, compute `countChildren(path, showHidden = true)` off the main
      thread and show `item_amount` under the name.
    - When the list is empty only because hidden items are off, show `EmptyState` plus a
      `TextButton(show_hidden_items)` that calls `toggleHiddenFiles()`.
    - Both steps use existing strings.
- **Reach:** Low–Med · **Impact:** Med · **Effort:** M (VM plumbing, no new strings)
- **Source:** B-3 (Blocking → Friction)

---

## Polish

### UX-28 — Folder title shows the entry point, not the current folder, and the current breadcrumb is the faintest text

- **Tier:** Polish · **Areas:** Browse, Launch · **Variants:** all
- **Screens:** folder screen from every entry point (Home locations, Recent/Favorites "Open folder",
  Search, Analyzer); picker breadcrumbs
- **Evidence:**
    - Screenshots:
        - [browse__folder__deep_nesting_breadcrumbs__default.png](shots/browse__folder__deep_nesting_breadcrumbs__default.png):
          12 levels deep and still titled "Internal Storage".
        - [browse__folder__ops_list__dark.png](shots/browse__folder__ops_list__dark.png): "ops" is
          dimmer than its parent.
        - [launch__home__recent_actions_sheet__default.png](shots/launch__home__recent_actions_sheet__default.png): "
          Open folder" titles the folder screen "Recent".
    - Code:
        - `activities/FolderActivity.kt:167-172` (every pushed entry reuses the launch title)
        - `ui/screens/home/HomeScreen.kt:352,377,425,462`
        - `ui/components/Breadcrumbs.kt:111-118` (last segment `primary`, ancestors
          `onSurfaceVariant`)
- **Problem:**
    - The largest text on screen repeats where the user came from: "Internal Storage", or "Recent"
      from Home.
    - The current folder, the most useful "where am I" cue, is drawn in the dimmest shade.
    - Search and Analyzer title the same action with the folder name, so the title depends on the
      entry point.
- **Proposal:**
    - Use `displayTitle` only at the launch path, and `File(currentPath).name` below it.
    - In the Home `OpenFolder` branches, pass `File(parentPath).name`.
    - Draw the last breadcrumb in `onSurface` with `FontWeight.Medium`.
- **Reach:** High · **Impact:** Low–Med · **Effort:** S
- **Source:** B-5, L-5 (both Friction → Polish: the breadcrumb auto-scrolls, so the user doesn't
  lose their place)

### UX-29 — Sort sheet has no title and marks the active choice with an inverted shade

- **Tier:** Polish · **Area:** Browse · **Variants:** all; clearest in dark
- **Screens:** Sort sheet
- **Evidence:**
  -
  Screenshots: [browse__folder__sort_sheet__default.png](shots/browse__folder__sort_sheet__default.png), [browse__folder__sort_sheet__dark.png](shots/browse__folder__sort_sheet__dark.png)
    - Code:
        - `ui/screens/folder/FolderScreen.kt:846-857` (selected `onSurface`, unselected `primary`)
        - `ui/screens/folder/FolderScreen.kt:796-837` (no title)
- **Problem:** The active order is hard to spot, and the unselected rows read as disabled. The
  search filters and the analyzer use radio buttons for the same pattern.
- **Proposal:** Add a `titleMedium` header with `menu_sort_by`. Use
  `leadingIcon = { RadioButton(selected, onClick = null) }` and colour every label `onSurface`.
- **Reach:** Med · **Impact:** Med · **Effort:** S (existing string)
- **Source:** B-7 (Friction → Polish)

### UX-30 — Drawer lists Settings first and has no header or grouping

- **Tier:** Polish · **Area:** Launch · **Variants:** all
- **Screens:** navigation drawer
- **Evidence:**
  -
  Screenshots: [launch__drawer__open__default.png](shots/launch__drawer__open__default.png), [launch__drawer__open__tablet.png](shots/launch__drawer__open__tablet.png)
    - Code: `ui/screens/home/HomeScreen.kt:170-229`
- **Problem:** Analyzer, the drawer's only feature destination, sits between two housekeeping rows,
  and the bare sheet looks unfinished.
- **Proposal:**
    - Reorder to Analyzer, then a `HorizontalDivider`, then Settings and About.
    - Replace the top spacer with an `app_name` header in `titleSmall` / `onSurfaceVariant`.
    - Badges are untouched.
- **Reach:** High · **Impact:** Low · **Effort:** S
- **Source:** L-8

### UX-31 — English permission strings are Title Case; the rest of the app is sentence case

- **Tier:** Polish · **Area:** Launch · **Variants:** EN variants
- **Screens:** permission screen, APK permission dialog
- **Evidence:**
  -
  Screenshots: [launch__permission__first_launch_denied__default.png](shots/launch__permission__first_launch_denied__default.png)
    - Code: `app/src/main/res/values/strings.xml:52,54,77,79`
- **Problem:** The first screen a user sees uses a different capitalisation style from every other
  dialog and button, and Material 3 specifies sentence case.
- **Proposal:** English only: "Storage permission required", "Grant permission", "Permission
  required", "Open settings". The translations are already sentence case.
- **Reach:** High · **Impact:** Low · **Effort:** S (English only)
- **Source:** L-10

### UX-32 — Raw `/storage/emulated/0` mount path in the scan progress and Item Info

- **Tier:** Polish · **Areas:** Analyzer, Browse · **Variants:** all
- **Screens:** Analyzer scanning, Item Info "Location"
- **Evidence:**
  -
  Screenshots: [analyzer__analyzer__scanning__default.png](shots/analyzer__analyzer__scanning__default.png), [browse__iteminfo__image__default.png](shots/browse__iteminfo__image__default.png)
    - Code:
        - `ui/screens/analyzer/AnalyzerViewModel.kt:199,240`
        - `ui/screens/iteminfo/ItemInfoScreen.kt:383-386`
        - `ui/components/BreadcrumbPathParser.kt:5,46-60` (the friendly-name mapping already exists)
- **Problem:** Two screens show a Linux mount point where the picker and the breadcrumbs say "
  Internal Storage".
- **Proposal:**
    - **Analyzer:** publish `currentFolder` with `storage.path` replaced by `storage.displayName`.
    - **Item Info:** show Location as `BreadcrumbPathParser` segments joined with " › ", and keep
      the raw path as the copied value.
    - Apply UX-06's direction handling to both.
- **Reach:** High · **Impact:** Low · **Effort:** S
- **Source:** A-4 (raw-path half), B-11 (Location part)

### UX-33 — Scan "Cancel" is a filled primary button and is named differently from the "Stop" it confirms

- **Tier:** Polish · **Area:** Analyzer · **Variants:** all
- **Screens:** Analyzer scanning, stop dialog
- **Evidence:**
  -
  Screenshots: [analyzer__analyzer__scanning__default.png](shots/analyzer__analyzer__scanning__default.png), [analyzer__analyzer__stop_scan_dialog__default.png](shots/analyzer__analyzer__stop_scan_dialog__default.png)
    - Code:
        - `ui/screens/analyzer/AnalyzerScreen.kt:370-372`
        - `ui/components/OperationProgressDialog.kt:74` (the app's progress dialogs cancel with a
          `TextButton`)
- **Problem:** The only emphasised action during a long wait throws the wait away, and "Cancel"
  turns into "Stop" in its own confirmation.
- **Proposal:** Use an `OutlinedButton` or `TextButton` labelled `analyzer_stop_scanning_confirm` ("
  Stop"), which already exists in 20 locales.
- **Reach:** High · **Impact:** Low · **Effort:** S
- **Source:** A-5

### UX-34 — Scanned-file count has no digit grouping

- **Tier:** Polish · **Area:** Analyzer · **Variants:** all
- **Screens:** Analyzer scanning
- **Evidence:**
  -
  Screenshots: [analyzer__analyzer__scanning__default.png](shots/analyzer__analyzer__scanning__default.png) ("
  1775 files")
    - Code: `ui/screens/analyzer/AnalyzerScreen.kt:357-363`,
      `app/src/main/res/values/strings.xml:467-470` (`%2$d`)
- **Problem:** Real phones have 5–6 digit counts, and they print ungrouped next to locale-formatted
  sizes.
- **Proposal:** Pass `NumberFormat.getIntegerInstance(locale).format(fileCount)` and change `%2$d`
  to `%2$s` in all 20 `strings.xml` files. This is a mechanical edit with no retranslation.
- **Reach:** High · **Impact:** Low · **Effort:** S
- **Source:** A-6

### UX-35 — Picker title doesn't say what is being copied or moved

- **Tier:** Polish · **Area:** Find · **Variants:** all
- **Screens:** destination picker (copy/move)
- **Evidence:**
  -
  Screenshots: [find__picker__copy_to_folder_list__default.png](shots/find__picker__copy_to_folder_list__default.png), [find__picker__move_to_destination__default.png](shots/find__picker__move_to_destination__default.png)
    - Code: `ui/screens/picker/DestinationPicker.kt:83-87`,
      `ui/screens/picker/PickerTopBar.kt:24-31`
- **Problem:** The full-screen picker hides the selection. A few levels down, the user can't confirm
  what they are about to move.
- **Proposal:** Add an optional `subtitle` to `PickerTopBar`, in `bodySmall` / `onSurfaceVariant`.
  It shows the item's name for one item, otherwise the existing `plurals/item_amount`, and nothing
  for startup-folder selection.
- **Reach:** High · **Impact:** Low · **Effort:** S (no new strings)
- **Source:** F-9

### UX-36 — Transfer progress dialog shows a bare bar with no amount or percentage

- **Tier:** Polish · **Area:** Browse · **Variants:** all
- **Screens:** copy/move progress; delete, compress and uncompress progress
- **Evidence:**
  -
  Screenshots: [browse__progress__copy_250mb__default.png](shots/browse__progress__copy_250mb__default.png)
    - Code:
        - `ui/components/OperationProgressDialog.kt:50-69`
        - `data/model/OperationProgress.kt:9-10` (`copiedBytes` and `totalBytes` already exist)
        - `ui/components/DeleteProgressDialog.kt:56-62`,
          `ui/components/CompressProgressDialog.kt:68-74` (`maxLines = 1` without ellipsis)
- **Problem:** On a 250 MB copy the modal dialog gives no sense of how much is done or how long is
  left.
- **Proposal:**
    - Add one row under the bar: the file name on the start side (weighted, ellipsized), and
      `"<copied> / <total> · <n>%"` on the end side, using `FileSizeFormatter` and the existing
      `analyzer_percent_format`.
    - Add `TextOverflow.Ellipsis` to the delete and compress file lines.
- **Reach:** Med · **Impact:** Low–Med · **Effort:** S
- **Source:** B-9 (Friction → Polish; the unsupported "destination" claim was dropped)

### UX-37 — Category bars measure a different whole than the donut (owner's call)

- **Tier:** Polish · **Area:** Analyzer · **Variants:** all; stark on near-empty volumes
- **Screens:** Analyzer results
- **Evidence:**
    - Screenshots:
        - [analyzer__analyzer__done_sd_card_empty__default.png](shots/analyzer__analyzer__done_sd_card_empty__default.png):
          the ring says "0% used" while the System bar is full.
        - [analyzer__analyzer__done__default.png](shots/analyzer__analyzer__done__default.png)
    - Code:
        - `ui/screens/analyzer/AnalyzerViewModel.kt:385` (`bytes / usedBytes`)
        - `ui/screens/analyzer/StorageDonutChart.kt:96` (the arc is bytes / total)
        - `ui/screens/analyzer/StorageDonutChart.kt:30-33` (the stated intent)
- **Problem:** The same `UsageBar` means share of capacity on the volume cards and share of used
  space on the results, right under a ring drawn to capacity. The six category greys are too close
  to link arcs to rows, so bar length is the only link, and it doesn't match.
- **Proposal:**
    - Compute `fraction = bytes / totalBytes` and draw arcs at `fraction * 360f`.
    - Update the KDoc and `AnalyzerViewModelTest`.
    - **Trade-off:** on a typical phone every non-System bar becomes a dot. This goes against the
      documented intent, so it is the owner's call.
- **Reach:** Low–Med · **Impact:** Low–Med · **Effort:** S
- **Source:** A-3

### UX-38 — Analyzer results don't name the analyzed volume

- **Tier:** Polish · **Area:** Analyzer · **Variants:** all
- **Screens:** Analyzer results
- **Evidence:**
  -
  Screenshots: [analyzer__analyzer__done__default.png](shots/analyzer__analyzer__done__default.png), [analyzer__analyzer__done_sd_card_empty__default.png](shots/analyzer__analyzer__done_sd_card_empty__default.png)
    - Code: `ui/screens/analyzer/AnalyzerScreen.kt:100` (fixed title)
- **Problem:** With two volumes, the two results screens look alike apart from the numbers.
- **Proposal:** While in `RESULTS`, reuse the two-line title from
  `AnalyzerCategoryScreen.kt:168-184`: `drawer_analyzer` over `selectedStorage.displayName` in
  `bodySmall` / `onSurfaceVariant`.
- **Reach:** Med · **Impact:** Low · **Effort:** S
- **Source:** A-7 (narrowed to the title; the Back-behaviour half was dropped)

### UX-39 — Item Info shows a bogus "Jan 1, 1904" date and no sign of the folder size

- **Tier:** Polish · **Area:** Browse · **Variants:** all
- **Screens:** Item Info
- **Evidence:**
  -
  Screenshots: [browse__iteminfo__video_mp4_scrolled__default.png](shots/browse__iteminfo__video_mp4_scrolled__default.png), [browse__iteminfo__folder__default.png](shots/browse__iteminfo__folder__default.png)
    - Code:
        - `ui/screens/iteminfo/ItemInfoScreen.kt:1015-1020`
        - `ui/screens/iteminfo/ItemInfoScreen.kt:416-421` (Size only when `folderSize != null`)
        - `ui/screens/iteminfo/ItemInfoViewModel.kt:259-266`
- **Problem:** The MP4 zero epoch is shown as a real recording date. For folders there is no Size
  row and no sign that one is coming.
- **Proposal:**
    - Skip `info_date_recorded` when the year is 1904 or earlier.
    - Always render the Size row for directories, with a 16 dp `CircularProgressIndicator` in the
      value slot until the size arrives.
- **Reach:** Med · **Impact:** Low–Med · **Effort:** S–M
- **Source:** B-11 (the refuter noted the Size row is missing in every folder variant; see the note
  at the end)

### UX-40 — Rows that open the Play Store use the in-app navigation chevron

- **Tier:** Polish · **Area:** Settings · **Variants:** all
- **Screens:** About (Version row), Other apps
- **Evidence:**
  -
  Screenshots: [settings__about__main__default.png](shots/settings__about__main__default.png), [settings__otherapps__list__default.png](shots/settings__otherapps__list__default.png)
    - Code: `activities/AboutActivity.kt:147-154,201-206`,
      `activities/OtherAppsActivity.kt:229-234,245-249`
- **Problem:** "Version 2.7.0 >" reads as a details screen, so the jump to the Play Store is a
  surprise.
- **Proposal:** Add a `trailingIcon` parameter to `AboutRow`, and pass
  `Icons.AutoMirrored.Outlined.OpenInNew` for the Version row and in `AppRow`, tinted
  `onSurfaceVariant`.
- **Reach:** Med · **Impact:** Low · **Effort:** S
- **Source:** S-5

### UX-41 — Landscape option dialogs hide choices with no scroll cue

- **Tier:** Polish · **Area:** Settings · **Variants:** land
- **Screens:** swipe-action dialogs, Locations dialog
- **Evidence:**
  -
  Screenshots: [settings__settings__swipe_left_dialog__land.png](shots/settings__settings__swipe_left_dialog__land.png), [settings__settings__locations_dialog__land.png](shots/settings__settings__locations_dialog__land.png)
    - Code: `activities/SettingsActivity.kt:1151-1159,1248,1346`
- **Problem:** The dialogs do scroll, but the last visible row ends cleanly, so users think "Copy
  to" or "Info" don't exist.
- **Proposal:** Hoist the scroll state, and draw `HorizontalDivider(color = outlineVariant)` above
  and below the list when `canScrollBackward` / `canScrollForward`. Put this in a shared
  `ScrollableDialogContent`.
- **Reach:** Low · **Impact:** Med · **Effort:** S
- **Source:** S-7

### UX-42 — No screen limits its content width on tablets or in landscape

- **Tier:** Polish · **Areas:** Launch, Settings (app-wide) · **Variants:** tablet, land
- **Screens:** Home, Settings, About, Feedback, Legal, Other apps, and the folder lists
- **Evidence:**
    - Screenshots:
        - [launch__home__with_recents_and_favorites__tablet.png](shots/launch__home__with_recents_and_favorites__tablet.png)
        - [launch__home__first_launch_after_grant__land.png](shots/launch__home__first_launch_after_grant__land.png)
        - [settings__legal__privacy_top__tablet.png](shots/settings__legal__privacy_top__tablet.png)
        - [settings__settings__top__tablet.png](shots/settings__settings__top__tablet.png)
    - Code:
        - `ui/components/LocationsSection.kt:58` (`chunked(2)`)
        - `ui/screens/home/HomeScreen.kt:245-250`
        - `activities/SettingsActivity.kt:284-289`
        - `activities/LegalActivity.kt:156-167`
        - No `widthIn` anywhere in the app.
- **Problem:** On wide windows, cards and bars stretch to 600–1200 dp, the legal text runs about 140
  characters per line, and labels sit far from their controls.
- **Proposal:**
    - Add `content_max_width` = 640 dp (840 dp for Home) to `dimens.xml`.
    - Centre each content column with `.widthIn(max = …)`.
    - Give `LocationsSection` `(maxWidth / 180.dp).coerceIn(2, 3)` columns.
- **Reach:** Low–Med · **Impact:** Low–Med · **Effort:** S–M (no strings)
- **Source:** L-9, S-9 (narrowed to line length and scannability)

### UX-43 — Hidden items look exactly like normal ones when shown

- **Tier:** Polish · **Area:** Browse · **Variants:** all
- **Screens:** folder list, and search results with the Hidden filter on
- **Evidence:**
  -
  Screenshots: [browse__folder__hidden_items_shown__default.png](shots/browse__folder__hidden_items_shown__default.png)
    - Code: `ui/components/FileListItem.kt:134-145`
- **Problem:** With "Show hidden items" on (a persisted setting), users can't tell system and config
  files from their own.
- **Proposal:** For names starting with ".", draw the icon and name at reduced emphasis: a private
  alpha constant next to `EMPTY_TEXT_ALPHA`, or `onSurfaceVariant` for the name.
- **Reach:** Low–Med · **Impact:** Low · **Effort:** S
- **Source:** B-14

### UX-44 — Landscape display-cutout inset leaves blank strips or misaligned rows

- **Tier:** Polish · **Areas:** Browse, Find, Settings, Viewers · **Variants:** land
- **Screens:** folder list, destination picker (copy/move and startup folder), PDF search status row
- **Evidence:**
    - Screenshots:
        - [browse__folder__ops_list__land.png](shots/browse__folder__ops_list__land.png)
        - [settings__settings__startup_folder_picker_internal__land.png](shots/settings__settings__startup_folder_picker_internal__land.png)
        - [find__picker__copy_to_folder_list__land.png](shots/find__picker__copy_to_folder_list__land.png)
        - [viewers__pdfviewer__search_hits__land.png](shots/viewers__pdfviewer__search_hits__land.png)
    - Code:
        - `ui/screens/folder/FolderScreen.kt:349-353`
        - `ui/screens/picker/DestinationPicker.kt:116-121`
        - `ui/screens/picker/PickerBottomBar.kt:58` (fixed `bottom = 32.dp`, no insets)
        - `ui/screens/pdfviewer/PdfViewerScreen.kt:694-698`
- **Problem:** The Scaffold's horizontal cutout inset pads the backgrounds too, so the breadcrumb
  band and the lists stop short of the edge and leave a blank strip. The picker's bottom bar ignores
  insets and takes about 23 % of the height, and the PDF match count sits in the cutout band.
- **Proposal:**
    - Apply only vertical Scaffold padding, and move `WindowInsets.displayCutout.only(Horizontal)`
      inside the backgrounds: `windowInsetsPadding` on the breadcrumb row, and `asPaddingValues()`
      on the list `contentPadding`.
    - Picker bar: `bottom = 16.dp` plus
      `windowInsetsPadding(navigationBars ∪ displayCutout, Horizontal + Bottom)`.
    - PDF status row: `TopAppBarDefaults.windowInsets.only(Horizontal)`.
- **Reach:** Low–Med · **Impact:** Low · **Effort:** S
- **Source:** B-15, S-10, F-10, V-11 (the 3-button-nav overlap in F-10 is inferred from code only)

### UX-45 — Picker's "can't copy here" reason is small print below the disabled button

- **Tier:** Polish · **Area:** Find · **Variants:** all; worst on tablet
- **Screens:** destination picker
- **Evidence:**
  -
  Screenshots: [find__picker__copy_to_same_folder_invalid__default.png](shots/find__picker__copy_to_same_folder_invalid__default.png), [find__picker__copy_to_same_folder_invalid__tablet.png](shots/find__picker__copy_to_same_folder_invalid__tablet.png)
    - Code: `ui/screens/picker/PickerBottomBar.kt:250-260`
- **Problem:** Users tap the greyed button first and hunt for the reason afterwards.
- **Proposal:** Render the error above the buttons, start-aligned: `Icons.Outlined.ErrorOutline` (18
  dp, `error`) plus `bodyMedium` in `error`.
- **Reach:** Low–Med · **Impact:** Low · **Effort:** S
- **Source:** F-8

### UX-46 — Rotation closes viewer dialogs and resets zoom

- **Tier:** Polish · **Area:** Viewers · **Variants:** land (any rotation)
- **Screens:** image, PDF and text viewers
- **Evidence:**
  -
  Screenshots: [viewers__imageviewer__delete_confirm__land.png](shots/viewers__imageviewer__delete_confirm__land.png)
  and [viewers__pdfviewer__go_to_page_dialog__land.png](shots/viewers__pdfviewer__go_to_page_dialog__land.png).
  The capture run had to open these in landscape, because rotating closed them.
    - Code:
        - `ui/screens/imageviewer/ImageViewerScreen.kt:77,168-169`
        - `ui/screens/pdfviewer/PdfViewerScreen.kt:128-130,748`
        - `ui/screens/textviewer/TextViewerScreen.kt:69`
        - `app/src/main/AndroidManifest.xml:116-134` (no `configChanges`)
- **Problem:** Turning the phone dismisses an open Delete or Go-to-page dialog, drops the typed page
  number, and loses the zoom.
- **Proposal:**
    - Switch the dialog flags and the page `input` to `rememberSaveable`.
    - Save the image `scale`, and reset `offset`.
    - Optionally give `PdfZoomState` a `Saver`.
- **Reach:** Low · **Impact:** Low–Med · **Effort:** S (M with zoom)
- **Source:** V-8 (Friction → Polish)

### UX-47 — Filter sheets have no titles, and "Any" sits beside "Any type" with the same icon

- **Tier:** Polish · **Area:** Find · **Variants:** all
- **Screens:** Search filter sheets
- **Evidence:**
  -
  Screenshots: [find__search__filter_kind_menu__default.png](shots/find__search__filter_kind_menu__default.png), [find__search__filter_type_menu__default.png](shots/find__search__filter_type_menu__default.png)
    - Code:
        - `ui/components/SearchFiltersBar.kt:318-322,352-355` (both use `SelectAll`)
        - `ui/components/SearchFiltersBar.kt:211-229` (no header)
        - `app/src/main/res/values/strings.xml:121,123`
- **Problem:** With Kind set to Any, the chip row reads "Any ▾ · Hidden ▾ · Any type ▾", with two
  identical icons.
- **Proposal:** Rename `search_filter_kind_any` to "Files and folders" and give it
  `Icons.Outlined.FolderCopy`. Sheet titles are optional and cost 3 more strings.
- **Reach:** Med · **Impact:** Low · **Effort:** S–M (1 string × 20; M with titles)
- **Source:** F-11

### UX-48 — Swiped rows stay open: several at once, and behind dialogs

- **Tier:** Polish · **Area:** Browse · **Variants:** all
- **Screens:** folder list
- **Evidence:**
  -
  Screenshots: [browse__folder__swipe_right_in_progress__default.png](shots/browse__folder__swipe_right_in_progress__default.png), [browse__folder__swipe_right_delete_confirm__default.png](shots/browse__folder__swipe_right_delete_confirm__default.png)
    - Code: `ui/components/SwipeableFileListItem.kt:90,114-126`
- **Problem:** Rows left open look glitched, clip their names, and leave Rename or Delete targets
  exposed.
- **Proposal:** Hoist `revealedPath` into `FolderScreen`. Opening a row closes the others, and any
  dialog, sheet or scroll clears it.
- **Reach:** Med · **Impact:** Low · **Effort:** M
- **Source:** B-13

### UX-49 — Arabic/Urdu privacy policy: two Firebase bullets flip to LTR

- **Tier:** Polish · **Area:** Settings · **Variants:** ar (ur from the same source; not captured)
- **Screens:** Legal, Privacy Policy
- **Evidence:**
  -
  Screenshots: [settings__legal__privacy_scrolled__ar.png](shots/settings__legal__privacy_scrolled__ar.png)
    - Code: `app/src/main/res/raw-ar/privacy.md:33-34`, `app/src/main/res/raw-ur/privacy.md:33-34`
- **Problem:** Two bullets start with Latin `**Firebase…**`, so they render left-to-right with their
  dots on the wrong side.
- **Proposal:** Prefix those 4 lines with U+200F (RLM), or reorder each item so it opens with Arabic
  or Urdu text. No code changes.
- **Reach:** Low · **Impact:** Low · **Effort:** S
- **Source:** S-8

### UX-50 — Feedback field is a fixed 150 dp box above an empty screen

- **Tier:** Polish · **Area:** Settings · **Variants:** all; most visible on tablet
- **Screens:** Feedback
- **Evidence:**
  -
  Screenshots: [settings__feedback__typed__default.png](shots/settings__feedback__typed__default.png), [settings__feedback__typed__tablet.png](shots/settings__feedback__typed__tablet.png)
    - Code: `activities/FeedbackActivity.kt:340-352`
- **Problem:** The limit is 1,000 characters, but only about 5 lines show while 60 % of the screen
  stays blank.
- **Proposal:**
    - Replace the fixed height with `heightIn(min = 150.dp)` and `minLines = 6`.
    - Make the column `verticalScroll().imePadding()`.
- **Reach:** Low · **Impact:** Low · **Effort:** S
- **Source:** S-11

### UX-51 — The four viewers show errors four different ways; the image error is top-aligned

- **Tier:** Polish · **Area:** Viewers · **Variants:** all
- **Screens:** image, media, PDF and text viewers (error and empty states)
- **Evidence:**
    - Screenshots:
        - [viewers__imageviewer__corrupt_image__default.png](shots/viewers__imageviewer__corrupt_image__default.png)
        - [viewers__mediaviewer__corrupt_video__default.png](shots/viewers__mediaviewer__corrupt_video__default.png)
        - [viewers__pdfviewer__corrupt_pdf__default.png](shots/viewers__pdfviewer__corrupt_pdf__default.png)
        - [viewers__textviewer__empty_file__default.png](shots/viewers__textviewer__empty_file__default.png)
    - Code:
        - `ui/screens/imageviewer/ImageViewerScreen.kt:241-270` (the Coil error slot gets full-size
          constraints)
        - `ui/screens/mediaviewer/MediaViewerScreen.kt:664-684`
        - `ui/screens/pdfviewer/PdfViewerScreen.kt:238-251,816-836`
        - `ui/screens/textviewer/TextViewerScreen.kt:134-153`
- **Problem:** Each viewer handles "can't show this file" differently:
    - The PDF error uses the PDF-document icon and reads as a placeholder.
    - The text error is plain red text.
    - The image error sits at the top of the screen instead of the centre.
    - None of them says why it failed.
- **Proposal:**
    - Add one shared `ViewerMessage(icon, text, supportingText?)`: centred with `wrapContentSize()`,
      a 48 dp icon, `bodyLarge` / `onSurfaceVariant`.
    - Icons: `BrokenImage` for the image viewer, `ErrorOutline` for the others, and keep `Lock` for
      password-protected PDFs.
    - Drop `error` from the text viewer's message.
    - Optional hint string: "The file may be damaged or in a format this device can't open".
- **Reach:** Low · **Impact:** Low–Med · **Effort:** S–M (M with the hint string × 20)
- **Source:** V-9, V-10

### UX-52 — A finished video goes black after rotation

- **Tier:** Polish · **Area:** Viewers · **Variants:** land (rotated after the end)
- **Screens:** media viewer
- **Evidence:**
  -
  Screenshots: [viewers__mediaviewer__video_ended__land.png](shots/viewers__mediaviewer__video_ended__land.png) (
  compare [the default shot](shots/viewers__mediaviewer__video_ended__default.png))
    - Code: `ui/screens/mediaviewer/MediaViewerScreen.kt:338-358`
- **Problem:** After rotating, the finished video area is solid black and looks broken.
- **Proposal:** When the surface re-attaches and the player isn't playing, force a frame render with
  `player.seekTo(player.currentPosition)`. Check this on a device: a seek in the ENDED state
  re-enters BUFFERING and READY, which may disturb how the ViewModel tracks the ended state.
- **Reach:** Low · **Impact:** Low · **Effort:** S
- **Source:** V-12

---

## Notes outside the UX scope

- **Possible defect, not verified:** the folder Size row is missing from every
  `browse__iteminfo__folder__*` variant, not only the early frames.
  `ItemInfoViewModel.loadFolderSize` may never finish for `/sdcard/UXReview`, which holds the
  100k-file `zz_scanload` folder, or it may fail silently. Worth checking on a device before
  treating UX-39 as polish only.
- **Privacy policy vs. feedback:** `raw*/privacy.md:7` says the app transmits nothing, but it lists
  Firebase at lines 33-34 and Feedback posts device data (UX-20). Aligning the policy is a legal and
  content decision for the owner.
