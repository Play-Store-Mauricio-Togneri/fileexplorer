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

---

## Polish

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


###

REMOVE ux-review FOLDER!!!!

###

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
