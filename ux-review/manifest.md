# UX review screenshot manifest

Package `com.mauriciotogneri.fileexplorer` (debug build 2.7.0), launcher `.activities.MainActivity`. Emulator API 36.
Fixtures live in `/sdcard/UXReview/` (`many` = 60 folders + 460 files, `unicode`, `hidden`, `deep` 12 levels, `media`, `docs`, `corrupt`, `big/large.bin` 250 MB, `conflict_src`/`conflict_dst`, `ops`, `zz_scanload` = 100k empty files to slow scans/search).

Variants: `default` = EN, light, portrait phone 1080x2400@420; `dark` = same as default + app theme System + `cmd uimode night yes`; `ar` = same as default + `cmd locale set-app-locales <pkg> --locales ar`; `de` = same as default + `cmd locale set-app-locales <pkg> --locales de`; `land` = same as default + `settings put system accelerometer_rotation 0; settings put system user_rotation 1`; `tablet` = same as default + `wm size 1600x2560; wm density 320`

| file | screen | state | variant | steps to reach it |
|---|---|---|---|---|
| `shots/analyzer__analyzer__done__default.png` | AnalyzerActivity | Results (donut + categories) | default | Stop dialog > Continue, wait for scan end |
| `shots/analyzer__analyzer__done__land.png` | AnalyzerActivity | Results (donut + categories) | land | same as default + land setting |
| `shots/analyzer__analyzer__done__tablet.png` | AnalyzerActivity | Results (donut + categories) | tablet | same as default + tablet setting |
| `shots/analyzer__analyzer__done_sd_card_empty__default.png` | AnalyzerActivity | Results for (empty) SD card | default | Analyzer > SD Card > Analyze |
| `shots/analyzer__analyzer__scanning__default.png` | AnalyzerActivity | Scan in progress | default | Analyzer > Internal Storage > Analyze (100k small files under UXReview/zz_scanload slow the scan) |
| `shots/analyzer__analyzer__scanning__ar.png` | AnalyzerActivity | Scan in progress | ar | same as default + ar setting |
| `shots/analyzer__analyzer__stop_scan_dialog__default.png` | AnalyzerActivity | Stop scanning? dialog | default | During scan > Cancel |
| `shots/analyzer__analyzer__system_explanation__default.png` | AnalyzerActivity | Toast explaining System category | default | Results > tap System |
| `shots/analyzer__analyzer__system_explanation__land.png` | AnalyzerActivity | Toast explaining System category | land | same as default + land setting |
| `shots/analyzer__analyzercategory__empty__default.png` | AnalyzerCategoryActivity | Empty category | default | SD Card results > Documents |
| `shots/analyzer__analyzercategory__images__default.png` | AnalyzerCategoryActivity | Images list | default | Internal Storage results > Images |
| `shots/browse__folder__compress_dialog__default.png` | FolderActivity | Compress dialog | default | docs > data.json '⋮' > Compress |
| `shots/browse__folder__copy_conflict_auto_renamed__default.png` | FolderActivity | Destination showing 'report (1).txt' | default | Home > Internal Storage > UXReview > conflict_dst |
| `shots/browse__folder__copy_conflict_result__default.png` | FolderActivity | After copy into folder with same name (no conflict dialog exists; auto-renames) | default | multiselect > Copy to > Internal Storage > UXReview > conflict_dst > Copy here |
| `shots/browse__folder__create_folder_dialog__default.png` | FolderActivity | New folder dialog | default | ops > toolbar '⋮' > New folder |
| `shots/browse__folder__deep_nesting_breadcrumbs__default.png` | FolderActivity | 12 levels deep, breadcrumbs end | default | Home > Internal Storage > UXReview > deep > level_1 … level_12 |
| `shots/browse__folder__delete_confirm_dialog__default.png` | FolderActivity | Delete confirmation | default | selection bar > Delete |
| `shots/browse__folder__emoji_folder__ar.png` | FolderActivity | Inside '🎉 party photos 🎂' | ar | unicode > 🎉 party photos 🎂 — then + ar setting |
| `shots/browse__folder__empty_state__default.png` | FolderActivity | Empty folder state | default | Home > Internal Storage > UXReview > empty |
| `shots/browse__folder__file_actions_sheet__default.png` | FolderActivity | File actions bottom sheet | default | many (scrolled to files) > row '⋮' |
| `shots/browse__folder__file_actions_sheet__land.png` | FolderActivity | File actions bottom sheet | land | same as default in landscape; sheet is cut off after 'Rename' and does not scroll |
| `shots/browse__folder__folder_actions_sheet__land.png` | FolderActivity | Folder actions bottom sheet | land | many (top) > folder row '⋮' — then + land setting |
| `shots/browse__folder__hidden_items_shown__default.png` | FolderActivity | Hidden items shown | default | hidden > '⋮' > Show hidden items |
| `shots/browse__folder__hidden_items_shown__ar.png` | FolderActivity | Hidden items shown | ar | same as default + ar setting |
| `shots/browse__folder__move_done__default.png` | FolderActivity | Right after move | default | ops > select beta.txt + alpha_renamed.txt > Move to > … > move_dst > Move here |
| `shots/browse__folder__multiselect__de.png` | FolderActivity | 2 items selected, selection bar | de | Home > Internal Storage > UXReview > conflict_src > long-press report.txt > tap unique.txt — then + de setting |
| `shots/browse__folder__ops_list__dark.png` | FolderActivity | Scratch folder for operations | dark | Home > Internal Storage > UXReview > ops — then + dark setting |
| `shots/browse__folder__ops_list__land.png` | FolderActivity | Scratch folder for operations | land | Home > Internal Storage > UXReview > ops — then + land setting |
| `shots/browse__folder__rename_dialog__default.png` | FolderActivity | Rename dialog prefilled | default | ops > alpha.txt '⋮' > Rename |
| `shots/browse__folder__single_selected__de.png` | FolderActivity | 1 item selected, selection bar | de | ops > long-press delete_me.txt — then + de setting |
| `shots/browse__folder__sort_sheet__default.png` | FolderActivity | Sort by sheet (6 options) | default | toolbar '⋮' > Sort by |
| `shots/browse__folder__sort_sheet__dark.png` | FolderActivity | Sort by sheet (6 options) | dark | same as default + dark setting |
| `shots/browse__folder__swipe_right_delete_confirm__default.png` | FolderActivity | Dialog after full swipe (Delete by default) | default | docs > full swipe on notes.txt (right; in ar: right-to-left) |
| `shots/browse__folder__swipe_right_in_progress__default.png` | FolderActivity | Row mid swipe-right (action reveal) | default | docs > drag notes.txt row toward right, screenshot while holding |
| `shots/browse__folder__uxreview_list__default.png` | FolderActivity | Test fixtures folder | default | Home > Internal Storage > UXReview |
| `shots/browse__iteminfo__arabic_name_pdf__default.png` | ItemInfoActivity | Info for Arabic-named PDF | default | unicode > تقرير_المشروع_النهائي.pdf '⋮' > Info |
| `shots/browse__iteminfo__folder__default.png` | ItemInfoActivity | Folder info | default | Internal Storage > UXReview '⋮' > Info |
| `shots/browse__iteminfo__image__default.png` | ItemInfoActivity | JPEG info | default | media > big_photo.jpg '⋮' > Info |
| `shots/browse__iteminfo__image__ar.png` | ItemInfoActivity | JPEG info | ar | same as default + ar setting |
| `shots/browse__iteminfo__image__land.png` | ItemInfoActivity | JPEG info | land | navigated in portrait, rotated to landscape (user_rotation 1) just before capture: in landscape the row '⋮' sheet is clipped after 'Rename', so Info/Favorites/Compress are unreachable |
| `shots/browse__iteminfo__video_mp4_scrolled__default.png` | ItemInfoActivity | MP4 info scrolled | default | MP4 info > scroll down |
| `shots/browse__progress__copy_250mb__default.png` | FolderActivity (OperationProgressDialog) | Copying 250 MB, mid-progress | default | Home > Internal Storage > UXReview > big > long-press large.bin > Copy to > UXReview > big_dest > Copy here; screencap ~0.3 s later |
| `shots/find__picker__copy_to_destination__default.png` | DestinationPicker (copy) | Valid destination | default | Copy to > … > conflict_dst |
| `shots/find__picker__copy_to_folder_list__default.png` | DestinationPicker (copy) | Folder list | default | Copy to > Internal Storage > UXReview |
| `shots/find__picker__copy_to_folder_list__land.png` | DestinationPicker (copy) | Folder list | land | same as default + land setting |
| `shots/find__picker__copy_to_same_folder_invalid__default.png` | DestinationPicker (copy) | Validation: same folder | default | Copy to > … > conflict_src (the source) |
| `shots/find__picker__copy_to_same_folder_invalid__tablet.png` | DestinationPicker (copy) | Validation: same folder | tablet | same as default + tablet setting |
| `shots/find__picker__move_to_destination__default.png` | DestinationPicker (move) | Valid destination | default | ops selection > Move to > … > move_dst |
| `shots/find__picker__move_to_destination__tablet.png` | DestinationPicker (move) | Valid destination | tablet | same as default + tablet setting |
| `shots/find__picker__new_folder_dialog__default.png` | DestinationPicker (copy) | New folder dialog in picker | default | picker > New folder |
| `shots/find__search__empty_query__default.png` | SearchActivity | Empty query with filter chips | default | Home > Search icon |
| `shots/find__search__filter_kind_menu__default.png` | SearchActivity | Kind filter menu | default | Search > 'Files' chip |
| `shots/find__search__filter_type_menu__default.png` | SearchActivity | Type filter menu | default | Search > 'Any type' chip |
| `shots/find__search__filter_type_menu__land.png` | SearchActivity | Type filter menu | land | same as default + land setting |
| `shots/find__search__hits_many_keyboard_hidden__default.png` | SearchActivity | Results, IME hidden | default | results > Back (hides IME) |
| `shots/find__search__in_progress__default.png` | SearchActivity | Search running (spinner below partial hits) | default | Search > type 'f_99', screencap ~0.45 s later |
| `shots/find__search__no_results__default.png` | SearchActivity | No results | default | Search > type 'zzqqxxnomatch' > Back |
| `shots/find__search__result_actions_sheet__default.png` | SearchActivity | Result actions sheet | default | results > row '⋮' |
| `shots/launch__drawer__open__default.png` | Navigation drawer | Open | default | Home > Open menu (hamburger) |
| `shots/launch__drawer__open__tablet.png` | Navigation drawer | Open | tablet | same as default + tablet setting |
| `shots/launch__home__favorite_actions_sheet__default.png` | HomeScreen | Favorite actions bottom sheet | default | Home > Favorites card '⋮' |
| `shots/launch__home__first_launch_after_grant__land.png` | HomeScreen | Right after granting access | land | Grant Permission > toggle on > Back — then + land setting |
| `shots/launch__home__recent_actions_sheet__default.png` | HomeScreen | Recent file actions bottom sheet | default | Home > first Recent card '⋮' |
| `shots/launch__home__recent_actions_sheet__land.png` | HomeScreen | Recent file actions bottom sheet | land | navigated in portrait, rotated to landscape (user_rotation 1) just before capture: in landscape the row '⋮' sheet is clipped after 'Rename', so Info/Favorites/Compress are unreachable |
| `shots/launch__home__with_recents_and_favorites__default.png` | HomeScreen | Recent + Favorites sections populated | default | After opening files and adding multipage.pdf to favorites: relaunch app |
| `shots/launch__home__with_recents_and_favorites__ar.png` | HomeScreen | Recent + Favorites sections populated | ar | same as default + ar setting |
| `shots/launch__home__with_recents_and_favorites__tablet.png` | HomeScreen | Recent + Favorites sections populated | tablet | same as default + tablet setting |
| `shots/launch__permission__first_launch_denied__default.png` | PermissionScreen (MainActivity) | Storage access not granted (default: first launch after `pm clear`) | default | default: `pm clear <pkg>`, `appops set --uid <pkg> MANAGE_EXTERNAL_STORAGE default`, launch MainActivity. Variants: appops reset only (no pm clear) |
| `shots/launch__permission__first_launch_denied__land.png` | PermissionScreen (MainActivity) | Storage access not granted (default: first launch after `pm clear`) | land | same as default + land setting |
| `shots/launch__permission__returned_still_denied__default.png` | PermissionScreen | Returned from settings without granting | default | Grant Permission > system Back |
| `shots/launch__permission__system_settings_page__default.png` | System 'All files access' page | Opened from app, toggle off | default | Permission screen > Grant Permission |
| `shots/settings__about__main__default.png` | AboutActivity | Main list | default | Home > Open menu > About |
| `shots/settings__feedback__typed__default.png` | FeedbackActivity | Text entered | default | Feedback > type a sentence |
| `shots/settings__feedback__typed__tablet.png` | FeedbackActivity | Text entered | tablet | same as default + tablet setting |
| `shots/settings__legal__privacy_scrolled__ar.png` | LegalActivity | Privacy policy, scrolled | ar | About > Privacy Policy > scroll down x2 — then + ar setting |
| `shots/settings__legal__privacy_top__default.png` | LegalActivity | Privacy policy, top | default | About > Privacy Policy |
| `shots/settings__legal__privacy_top__tablet.png` | LegalActivity | Privacy policy, top | tablet | same as default + tablet setting |
| `shots/settings__otherapps__list__default.png` | OtherAppsActivity | App list | default | About > Other apps |
| `shots/settings__settings__favorites_clear_confirm__default.png` | SettingsActivity | Clear favorites confirm dialog | default | Settings > Clear favorites (with favorites present) |
| `shots/settings__settings__favorites_clear_confirm__de.png` | SettingsActivity | Clear favorites confirm dialog | de | same as default + de setting |
| `shots/settings__settings__locations_dialog__land.png` | SettingsActivity | Locations multi-select dialog | land | Settings > Locations — then + land setting |
| `shots/settings__settings__recent_cleared_toast__default.png` | SettingsActivity | Toast after clearing recents | default | Settings > Clear recent files (with recents present) |
| `shots/settings__settings__startup_folder_picker_internal__land.png` | DestinationPicker (select mode) | Internal storage folder list | land | ... > Specific folder > Internal Storage — then + land setting |
| `shots/settings__settings__swipe_left_dialog__land.png` | SettingsActivity | Swipe left action dialog | land | Settings > Swipe left action — then + land setting |
| `shots/settings__settings__top__tablet.png` | SettingsActivity | Top of list | tablet | Home > Open menu > Settings — then + tablet setting |
| `shots/viewers__imageviewer__corrupt_image__default.png` | ImageViewerActivity | Corrupt JPEG error | default | corrupt > broken_image.jpg |
| `shots/viewers__imageviewer__delete_confirm__land.png` | ImageViewerActivity | Delete confirmation | land | image viewer > Delete — then + land setting |
| `shots/viewers__imageviewer__landscape_image__land.png` | ImageViewerActivity | Landscape JPEG | land | navigated in portrait, rotated to landscape (user_rotation 1) just before capture: in landscape the row '⋮' sheet is clipped after 'Rename', so Info/Favorites/Compress are unreachable |
| `shots/viewers__mediaviewer__audio_mp3_playing__land.png` | MediaViewerActivity | MP3 playing | land | navigated in portrait, rotated to landscape (user_rotation 1) just before capture: in landscape the row '⋮' sheet is clipped after 'Rename', so Info/Favorites/Compress are unreachable |
| `shots/viewers__mediaviewer__audio_mp3_playing__tablet.png` | MediaViewerActivity | MP3 playing | tablet | media > test_audio.mp3 — then + tablet setting |
| `shots/viewers__mediaviewer__corrupt_video__default.png` | MediaViewerActivity | Corrupt MP4 error | default | corrupt > broken_video.mp4 |
| `shots/viewers__mediaviewer__video_ended__default.png` | MediaViewerActivity | Playback ended | default | video > let it play to the end (10 s) |
| `shots/viewers__mediaviewer__video_ended__land.png` | MediaViewerActivity | Playback ended | land | navigated in portrait, rotated to landscape (user_rotation 1) just before capture: in landscape the row '⋮' sheet is clipped after 'Rename', so Info/Favorites/Compress are unreachable |
| `shots/viewers__mediaviewer__video_fullscreen__default.png` | MediaViewerActivity | Full screen | default | video > Full screen |
| `shots/viewers__mediaviewer__video_playing__default.png` | MediaViewerActivity | Video playing | default | media > test_video.mp4 |
| `shots/viewers__mediaviewer__video_playing__land.png` | MediaViewerActivity | Video playing | land | navigated in portrait, rotated to landscape (user_rotation 1) just before capture: in landscape the row '⋮' sheet is clipped after 'Rename', so Info/Favorites/Compress are unreachable |
| `shots/viewers__pdfviewer__corrupt_pdf__default.png` | PdfViewerActivity | Corrupt PDF error | default | corrupt > broken_document.pdf |
| `shots/viewers__pdfviewer__go_to_page_dialog__default.png` | PdfViewerActivity | Go to page dialog | default | PDF > tap page indicator |
| `shots/viewers__pdfviewer__go_to_page_dialog__land.png` | PdfViewerActivity | Go to page dialog | land | same as default + land setting |
| `shots/viewers__pdfviewer__page1__default.png` | PdfViewerActivity | Page 1 | default | docs > multipage.pdf |
| `shots/viewers__pdfviewer__search_hits__default.png` | PdfViewerActivity | Search 'fox' hits | default | search > type 'fox' > Enter |
| `shots/viewers__pdfviewer__search_hits__land.png` | PdfViewerActivity | Search 'fox' hits | land | navigated in portrait, rotated to landscape (user_rotation 1) just before capture: in landscape the row '⋮' sheet is clipped after 'Rename', so Info/Favorites/Compress are unreachable |
| `shots/viewers__pdfviewer__search_open__default.png` | PdfViewerActivity | In-document search open | default | PDF > Search in document |
| `shots/viewers__textviewer__empty_file__default.png` | TextViewerActivity | Empty file | default | docs > empty.md |
| `shots/viewers__textviewer__json__ar.png` | TextViewerActivity | JSON file | ar | docs > data.json — then + ar setting |
| `shots/viewers__textviewer__large_file_truncated__default.png` | TextViewerActivity | 17.6 MB file, truncated banner | default | docs > long_notes.md |
| `shots/viewers__textviewer__large_file_truncated__ar.png` | TextViewerActivity | 17.6 MB file, truncated banner | ar | same as default + ar setting |
| `shots/viewers__textviewer__large_file_truncated__tablet.png` | TextViewerActivity | 17.6 MB file, truncated banner | tablet | same as default + tablet setting |
| `shots/viewers__textviewer__markdown__land.png` | TextViewerActivity | Markdown file | land | navigated in portrait, rotated to landscape (user_rotation 1) just before capture: in landscape the row '⋮' sheet is clipped after 'Rename', so Info/Favorites/Compress are unreachable |
