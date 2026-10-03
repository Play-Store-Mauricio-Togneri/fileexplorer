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
    - **Sub-points cut from kept findings:** the Back half of A-7, part 2 of F-1, V-5's clear button
      (a new action), the Sort-sheet half of B-2, and V-4's full-screen overlay point.
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

| ID    | Tier   | Area                            | Title                                                                                         | Effort |
|-------|--------|---------------------------------|-----------------------------------------------------------------------------------------------|--------|
| UX-31 | Polish | Launch                          | English permission strings are Title Case; the rest of the app is sentence case               | S      |
| UX-32 | Polish | Analyzer, Browse                | Raw `/storage/emulated/0` mount path in the scan progress and Item Info                       | S      |
| UX-33 | Polish | Analyzer                        | Scan "Cancel" is a filled primary button and is named differently from the "Stop" it confirms | S      |
| UX-34 | Polish | Analyzer                        | Scanned-file count has no digit grouping                                                      | S      |
| UX-35 | Polish | Find                            | Picker title doesn't say what is being copied or moved                                        | S      |
| UX-36 | Polish | Browse                          | Transfer progress dialog shows a bare bar with no amount or percentage                        | S      |
| UX-37 | Polish | Analyzer                        | Category bars measure a different whole than the donut (owner's call)                         | S      |
| UX-38 | Polish | Analyzer                        | Analyzer results don't name the analyzed volume                                               | S      |
| UX-39 | Polish | Browse                          | Item Info shows a bogus "Jan 1, 1904" date and no sign of the folder size                     | S–M    |
| UX-40 | Polish | Settings                        | Rows that open the Play Store use the in-app navigation chevron                               | S      |
| UX-41 | Polish | Settings                        | Landscape option dialogs hide choices with no scroll cue                                      | S      |
| UX-42 | Polish | Launch, Settings (app-wide)     | No screen limits its content width on tablets or in landscape                                 | S–M    |
| UX-43 | Polish | Browse                          | Hidden items look exactly like normal ones when shown                                         | S      |
| UX-44 | Polish | Browse, Find, Settings, Viewers | Landscape display-cutout inset leaves blank strips or misaligned rows                         | S      |
| UX-45 | Polish | Find                            | Picker's "can't copy here" reason is small print below the disabled button                    | S      |
| UX-46 | Polish | Viewers                         | Rotation closes viewer dialogs and resets zoom                                                | S      |
| UX-47 | Polish | Find                            | Filter sheets have no titles, and "Any" sits beside "Any type" with the same icon             | S–M    |
| UX-48 | Polish | Browse                          | Swiped rows stay open: several at once, and behind dialogs                                    | M      |
| UX-49 | Polish | Settings                        | Arabic/Urdu privacy policy: two Firebase bullets flip to LTR                                  | S      |
| UX-50 | Polish | Settings                        | Feedback field is a fixed 150 dp box above an empty screen                                    | S      |
| UX-51 | Polish | Viewers                         | The four viewers show errors four different ways; the image error is top-aligned              | S–M    |
| UX-52 | Polish | Viewers                         | A finished video goes black after rotation                                                    | S      |

---

## Polish


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
  Screenshots: [analyzer__analyzer__scanning__default.png](shots/analyzer__analyzer__scanning__default.png)
  ("
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
  Screenshots: [viewers__mediaviewer__video_ended__land.png](shots/viewers__mediaviewer__video_ended__land.png)
  (compare [the default shot](shots/viewers__mediaviewer__video_ended__default.png))
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
