# Instrumentation Test Implementation Plan

This document outlines a comprehensive plan for implementing instrumentation tests across 17 stages, covering all missing test scenarios in the File Explorer app.

---

## Overview

**Total Stages:** 16  
**Estimated Test Count:** ~198 new tests  
**Test Location:** `app/src/androidTest/java/com/mauriciotogneri/fileexplorer/`

### Workflow

After implementing each stage:

1. Provide the Gradle command to run the new tests:

   ```bash
   ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.TestClassName>
   ```

   For multiple test classes in the same stage, use comma-separated class names:

   ```bash
   ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.Test1,com.example.Test2
   ```

2. Remove the completed stage from this document to track remaining work.

### Directory Structure

```
androidTest/java/com/mauriciotogneri/fileexplorer/
├── integration/
│   ├── BreadcrumbsIntegrationTest.kt
│   ├── SelectionModeIntegrationTest.kt
│   ├── SwipeActionsIntegrationTest.kt
│   ├── NavigationIntegrationTest.kt
│   └── FileOperationsEndToEndTest.kt
├── ui/
│   ├── components/
│   │   ├── BreadcrumbsTest.kt
│   │   ├── SwipeableFileListItemTest.kt
│   │   ├── RecentFilesCarouselTest.kt
│   │   └── RecentFileActionsBottomSheetTest.kt
│   └── screens/
│       ├── permission/
│       │   └── PermissionScreenTest.kt
│       ├── home/
│       │   ├── RecentFilesSectionTest.kt
│       │   └── HomeDialogsTest.kt
│       ├── folder/
│       │   ├── FolderDialogsTest.kt
│       │   ├── FolderErrorStatesTest.kt
│       │   └── FolderSelectionModeTest.kt
│       ├── search/
│       │   └── SearchBehaviorTest.kt
│       ├── iteminfo/
│       │   └── ItemInfoScreenTest.kt
│       └── settings/
│           └── SettingsDialogsTest.kt
├── theme/
│   └── ThemeRenderingTest.kt
├── rtl/
│   └── RtlLayoutTest.kt
└── edge/
    └── EdgeCasesTest.kt
```

---

## Stage 3: Home Screen - Dialogs

**File:** `ui/screens/home/HomeDialogsTest.kt`  
**Priority:** High  
**Estimated Tests:** 18

### Test Cases

#### Delete Confirm Dialog
| Test Method | Description |
|-------------|-------------|
| `deleteConfirmDialog_displaysFileName` | Shows file name to delete |
| `deleteConfirmDialog_displaysWarningMessage` | Warning text visible |
| `deleteConfirmDialog_confirmButton_triggersDelete` | Confirm fires delete callback |
| `deleteConfirmDialog_cancelButton_dismisses` | Cancel dismisses without action |
| `deleteConfirmDialog_outsideClick_dismisses` | Clicking outside dismisses |

#### Uncompress Dialog
| Test Method | Description |
|-------------|-------------|
| `uncompressDialog_displaysZipName` | Shows ZIP file name |
| `uncompressDialog_displaysEntryCount` | Shows "X entries" count |
| `uncompressDialog_confirmButton_triggersExtract` | Confirm fires extract callback |
| `uncompressDialog_cancelButton_dismisses` | Cancel dismisses |

#### Password Uncompress Dialog
| Test Method | Description |
|-------------|-------------|
| `passwordDialog_displaysPasswordField` | Password input visible |
| `passwordDialog_emptyPassword_disablesConfirm` | Confirm disabled when empty |
| `passwordDialog_withPassword_enablesConfirm` | Confirm enabled with text |
| `passwordDialog_confirmButton_passesPassword` | Callback receives password |
| `passwordDialog_wrongPassword_showsError` | Error state displayed |

#### Uncompress Progress Dialog
| Test Method | Description |
|-------------|-------------|
| `uncompressProgressDialog_displaysProgress` | Progress bar/text shown |
| `uncompressProgressDialog_cancelButton_triggersCancellation` | Cancel fires callback |
| `uncompressProgressDialog_cancelButton_disablesWhileCancelling` | Button disabled during cancel |

#### APK Permission Dialog
| Test Method | Description |
|-------------|-------------|
| `apkPermissionDialog_displaysMessage` | Permission explanation shown |
| `apkPermissionDialog_settingsButton_triggersCallback` | Settings button fires callback |

### Implementation Notes

```kotlin
@Test
fun passwordDialog_confirmButton_passesPassword() {
    var receivedPassword: String? = null
    composeTestRule.setContent {
        PasswordUncompressDialog(
            zipName = "archive.zip",
            onConfirm = { receivedPassword = it },
            onDismiss = {}
        )
    }
    
    composeTestRule
        .onNodeWithContentDescription("Password")
        .performTextInput("secret123")
    
    composeTestRule
        .onNodeWithText("Extract")
        .performClick()
    
    assertThat(receivedPassword).isEqualTo("secret123")
}
```

---

## Stage 4: Folder Screen - Breadcrumbs

**File:** `ui/components/BreadcrumbsTest.kt`  
**Priority:** High  
**Estimated Tests:** 12

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `breadcrumbs_displaysAllSegments` | All path segments shown | Path `/storage/emulated/0/Documents/Work` | 4 segments visible |
| `breadcrumbs_rootSegment_showsStorageName` | Root shows "Internal Storage" | Path with storage root | Storage label displayed |
| `breadcrumbs_segmentTap_triggersNavigation` | Tap navigates to ancestor | Tap "Documents" segment | Callback with correct path |
| `breadcrumbs_currentSegment_notClickable` | Last segment not interactive | Tap last segment | No callback fired |
| `breadcrumbs_horizontalScroll_works` | Long paths scroll | 10-segment path | Can scroll horizontally |
| `breadcrumbs_autoScrollsToEnd` | Auto-scroll to current | Long path | Rightmost segment visible |
| `breadcrumbs_singleSegment_displaysCorrectly` | Root-only path works | Path at storage root | Single segment visible |
| `breadcrumbs_deepPath_displaysAllSegments` | Deep nesting works | 8-level deep path | All 8 segments visible |
| `breadcrumbs_separatorIcons_displayed` | Chevrons between segments | Multi-segment path | Separator icons visible |
| `breadcrumbs_sdCardRoot_showsSdCardName` | SD card label shown | SD card path | "SD Card" label displayed |
| `breadcrumbs_specialCharactersInPath_displayCorrectly` | Unicode folder names | Path with emoji folder | Emoji displayed correctly |
| `breadcrumbs_veryLongSegmentName_truncates` | Long names ellipsized | Folder with 50-char name | Name truncated with ellipsis |

### Integration Test

**File:** `integration/BreadcrumbsIntegrationTest.kt`

| Test Method | Description |
|-------------|-------------|
| `breadcrumbs_tapAncestor_navigatesBackCorrectLevels` | Full navigation integration |
| `breadcrumbs_navigateDeep_thenTapRoot_returnsToRoot` | Deep → root navigation |
| `breadcrumbs_backButton_matchesBreadcrumbState` | Back and breadcrumbs stay in sync |

### Implementation Notes

```kotlin
@Test
fun breadcrumbs_segmentTap_triggersNavigation() {
    var navigatedPath: String? = null
    val pathSegments = listOf(
        BreadcrumbItem("Internal Storage", "/storage/emulated/0"),
        BreadcrumbItem("Documents", "/storage/emulated/0/Documents"),
        BreadcrumbItem("Work", "/storage/emulated/0/Documents/Work")
    )
    
    composeTestRule.setContent {
        Breadcrumbs(
            items = pathSegments,
            onItemClick = { navigatedPath = it.path }
        )
    }
    
    composeTestRule
        .onNodeWithText("Documents")
        .performClick()
    
    assertThat(navigatedPath).isEqualTo("/storage/emulated/0/Documents")
}
```

### Dependencies
- `BreadcrumbPathParser` for generating test data
- `FakeStorageSource` for storage root paths

---

## Stage 5: Folder Screen - Swipe Actions

**File:** `ui/components/SwipeableFileListItemTest.kt`  
**Priority:** High  
**Estimated Tests:** 14

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `swipeRight_revealsDeleteAction` | Swipe right shows delete | Swipe right on item | Red delete background visible |
| `swipeRight_deleteIcon_displayed` | Delete icon shown | Swipe right | Delete icon visible |
| `swipeRight_deleteLabel_displayed` | "Delete" text shown | Swipe right | "Delete" text visible |
| `swipeRight_release_triggersDeleteCallback` | Swipe completes delete | Full swipe right | `onDelete` callback invoked |
| `swipeLeft_revealsRenameAction` | Swipe left shows rename | Swipe left on item | Green rename background visible |
| `swipeLeft_renameIcon_displayed` | Rename icon shown | Swipe left | Rename icon visible |
| `swipeLeft_renameLabel_displayed` | "Rename" text shown | Swipe left | "Rename" text visible |
| `swipeLeft_release_triggersRenameCallback` | Swipe completes rename | Full swipe left | `onRename` callback invoked |
| `partialSwipe_doesNotTriggerAction` | Partial swipe cancels | Swipe 20% then release | No callback invoked |
| `swipeInSelectionMode_disabled` | Swipe blocked in selection | `isSelectionMode = true`, swipe | No action revealed |
| `tapWhileRevealed_collapsesAction` | Tap collapses swipe | Swipe, then tap | Item returns to normal |
| `swipeOneItem_othersUnaffected` | Only one item swipes | Swipe item 1 | Items 2,3 unchanged |
| `swipeThenScrollList_collapsesAction` | Scroll collapses swipe | Swipe, then scroll list | Swipe collapses |
| `folder_swipeActionsWork` | Folders support swipe | Swipe on folder item | Actions work same as files |

### Implementation Notes

```kotlin
@Test
fun swipeRight_release_triggersDeleteCallback() {
    var deletedFile: FileItem? = null
    composeTestRule.setContent {
        SwipeableFileListItem(
            fileItem = testFile,
            isSelectionMode = false,
            onDelete = { deletedFile = it },
            onRename = {},
            onClick = {},
            onLongClick = {}
        )
    }
    
    composeTestRule
        .onNodeWithText("document.pdf")
        .performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
    
    composeTestRule.waitForIdle()
    assertThat(deletedFile?.name).isEqualTo("document.pdf")
}

@Test
fun swipeInSelectionMode_disabled() {
    var deleteTriggered = false
    composeTestRule.setContent {
        SwipeableFileListItem(
            fileItem = testFile,
            isSelectionMode = true,  // Selection mode active
            onDelete = { deleteTriggered = true },
            onRename = {},
            onClick = {},
            onLongClick = {}
        )
    }
    
    composeTestRule
        .onNodeWithText("document.pdf")
        .performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
    
    composeTestRule.waitForIdle()
    assertThat(deleteTriggered).isFalse()
}
```

### Dependencies
- Compose gesture testing utilities
- May need custom swipe threshold handling

---

## Stage 6: Folder Screen - Selection Mode

**File:** `ui/screens/folder/FolderSelectionModeTest.kt`  
**Priority:** High  
**Estimated Tests:** 16

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `longPress_entersSelectionMode` | Long press activates selection | Long press file item | Action bar appears, item selected |
| `longPress_selectsItem` | Long pressed item is selected | Long press item | Checkmark visible on item |
| `selectionMode_tapTogglesSelection` | Tap toggles in selection mode | In selection mode, tap item | Selection state toggles |
| `selectionMode_tapSelectedItem_deselects` | Tap deselects | Tap selected item | Item deselected |
| `selectionMode_tapUnselectedItem_selects` | Tap selects | Tap unselected item | Item selected |
| `selectionMode_multipleSelection_works` | Multiple items selectable | Select 3 items | All 3 show checkmarks |
| `selectionMode_closeButton_clearsSelection` | X button exits mode | Tap close (X) button | Selection cleared, action bar hidden |
| `selectionMode_backPress_clearsSelection` | Back exits selection mode | Press back | Selection cleared, no navigation |
| `selectionMode_backPress_doesNotNavigate` | Back doesn't navigate | Press back in selection | Still on same screen |
| `selectionMode_selectAll_selectsAllItems` | Select all from menu | Tap "Select All" in menu | All items selected |
| `selectionMode_unselectAll_deselectsAllItems` | Unselect all from menu | Tap "Unselect All" | All items deselected |
| `selectionMode_selectAllIcon_showsWhenNotAllSelected` | Toolbar icon visibility | Select 2 of 5 items | Select all icon visible |
| `selectionMode_selectAllIcon_hiddenWhenAllSelected` | Icon hidden when all selected | Select all 5 items | Select all icon hidden |
| `selectionMode_titleShowsCount` | Title shows "X selected" | Select 3 items | "3 selected" in title |
| `selectionMode_titleShowsCount_singular` | Singular form | Select 1 item | "1 selected" in title |
| `selectionMode_lastItemDeselected_exitsMode` | Auto-exit when empty | Deselect last item | Selection mode exits |

### Integration Test

**File:** `integration/SelectionModeIntegrationTest.kt`

| Test Method | Description |
|-------------|-------------|
| `selectionMode_deleteMultiple_deletesAllSelected` | Delete flow with selection |
| `selectionMode_moveMultiple_movesAllSelected` | Move flow with selection |
| `selectionMode_copyMultiple_copiesAllSelected` | Copy flow with selection |
| `selectionMode_compressMultiple_compressesAllSelected` | Compress flow with selection |

### Implementation Notes

```kotlin
@Test
fun longPress_entersSelectionMode() {
    composeTestRule.setContent {
        FolderScreen(
            viewModel = testViewModel,
            files = testFiles,
            selectedFiles = emptySet(),
            onSelectionChange = {}
        )
    }
    
    // Initially no action bar
    composeTestRule
        .onNodeWithContentDescription("Move to")
        .assertDoesNotExist()
    
    // Long press to enter selection mode
    composeTestRule
        .onNodeWithText("document.pdf")
        .performTouchInput { longClick() }
    
    // Action bar should appear
    composeTestRule
        .onNodeWithContentDescription("Move to")
        .assertIsDisplayed()
    
    // Item should show checkmark
    composeTestRule
        .onNodeWithText("document.pdf")
        .assertIsSelected()
}
```

---

## Stage 7: Folder Screen - Dialogs

**File:** `ui/screens/folder/FolderDialogsTest.kt`  
**Priority:** High  
**Estimated Tests:** 24

### Test Cases

#### Create Folder Dialog
| Test Method | Description |
|-------------|-------------|
| `createFolderDialog_displaysTitle` | "New folder" title shown |
| `createFolderDialog_displaysTextField` | Text input visible |
| `createFolderDialog_emptyName_disablesCreate` | Create disabled when empty |
| `createFolderDialog_validName_enablesCreate` | Create enabled with valid name |
| `createFolderDialog_invalidChars_showsError` | Error for `/`, `\`, `*`, etc. |
| `createFolderDialog_dotName_showsError` | Error for `.` or `..` |
| `createFolderDialog_existingName_showsError` | Error for collision |
| `createFolderDialog_createButton_triggersCallback` | Callback receives folder name |
| `createFolderDialog_cancelButton_dismisses` | Cancel closes dialog |

#### Rename Dialog
| Test Method | Description |
|-------------|-------------|
| `renameDialog_prefillsCurrentName` | Current name in text field |
| `renameDialog_cursorBeforeExtension` | Cursor positioned correctly |
| `renameDialog_invalidChars_showsError` | Error for invalid characters |
| `renameDialog_existingName_showsError` | Error for collision |
| `renameDialog_sameName_disablesRename` | Disabled if unchanged |
| `renameDialog_renameButton_triggersCallback` | Callback receives new name |

#### Compress Dialog
| Test Method | Description |
|-------------|-------------|
| `compressDialog_displaysDefaultZipName` | Default name suggested |
| `compressDialog_autoAppendsZipExtension` | `.zip` added automatically |
| `compressDialog_existingZipName_showsError` | Error for collision |
| `compressDialog_compressButton_triggersCallback` | Callback receives zip name |

#### Compress Progress Dialog
| Test Method | Description |
|-------------|-------------|
| `compressProgressDialog_showsCurrentFile` | Current file name displayed |
| `compressProgressDialog_showsByteProgress` | Progress bytes shown |
| `compressProgressDialog_cancelButton_triggersCancellation` | Cancel works |

#### Delete Progress Dialog
| Test Method | Description |
|-------------|-------------|
| `deleteProgressDialog_showsProgress` | "Deleting X of Y" shown |
| `deleteProgressDialog_cancelButton_stops` | Cancel stops deletion |
| `deleteProgressDialog_partialSuccess_showsCount` | Shows deleted/failed counts |

### Implementation Notes

```kotlin
@Test
fun createFolderDialog_invalidChars_showsError() {
    composeTestRule.setContent {
        CreateFolderDialog(
            existingNames = emptySet(),
            onConfirm = {},
            onDismiss = {}
        )
    }
    
    composeTestRule
        .onNodeWithContentDescription("Folder name")
        .performTextInput("invalid/name")
    
    composeTestRule
        .onNodeWithText("Name contains invalid characters")
        .assertIsDisplayed()
    
    composeTestRule
        .onNodeWithText("Create")
        .assertIsNotEnabled()
}
```

---

## Stage 8: Folder Screen - File Operations Integration

**File:** `integration/FileOperationsEndToEndTest.kt`  
**Priority:** High  
**Estimated Tests:** 16

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `copyFile_sameName_createsUniqueFile` | Copy with conflict resolution | Copy file to folder with same name | `file (1).txt` created |
| `copyFile_multipleConflicts_incrementsCounter` | Multiple conflicts | Copy to folder with `(1)`, `(2)` | `file (3).txt` created |
| `moveFile_removesSource` | Move deletes original | Move file | Source gone, target exists |
| `moveFile_sameName_createsUniqueName` | Move with conflict | Move to folder with same name | Unique name in target |
| `compressFiles_createsZip` | Compress creates archive | Select files, compress | `.zip` file appears |
| `compressFolder_includesAllContents` | Folder compression | Compress folder | All contents in zip |
| `uncompressZip_extractsAllFiles` | Extraction works | Uncompress zip | All files extracted |
| `uncompressZip_passwordProtected_requiresPassword` | Password required | Uncompress encrypted zip | Password dialog shown |
| `uncompressZip_wrongPassword_showsError` | Wrong password | Enter wrong password | Error displayed |
| `deleteMultipleFiles_showsProgress` | Batch delete progress | Delete 15 files | Progress dialog shown |
| `deleteMultipleFiles_partialFailure_showsResults` | Partial failure | Delete with 2 failures | "Deleted 13, failed 2" |
| `operationCancellation_stopsOperation` | Cancel stops work | Cancel during copy | Operation stops |
| `operationCancellation_cleansUpPartialState` | Cleanup on cancel | Cancel mid-copy | Partial file removed |
| `copyLargeFile_showsProgress` | Large file progress | Copy 50MB file | Progress updates shown |
| `moveBetweenStorages_copiesThenDeletes` | Cross-storage move | Move from internal to SD | File on SD, gone from internal |
| `createFolderInEmptyFolder_works` | Create in empty parent | Create folder in empty dir | Folder created |

### Implementation Notes

```kotlin
@Test
fun copyFile_sameName_createsUniqueFile() {
    // Create source file
    val sourceDir = tempFolder.newFolder("source")
    val sourceFile = File(sourceDir, "document.txt").apply { 
        writeText("content") 
    }
    
    // Create target with existing file of same name
    val targetDir = tempFolder.newFolder("target")
    File(targetDir, "document.txt").apply { 
        writeText("existing") 
    }
    
    // Execute copy through UI
    composeTestRule.setContent {
        FolderScreen(/* ... */)
    }
    
    // Select file, tap copy, navigate to target, confirm
    // ... UI interactions ...
    
    // Verify unique name created
    assertThat(File(targetDir, "document (1).txt").exists()).isTrue()
    assertThat(File(targetDir, "document.txt").readText()).isEqualTo("existing")
}
```

### Dependencies
- `FakeStorageSource` for real file operations
- `TemporaryFolder` JUnit rule
- Real `FileRepository` instance

---

## Stage 9: Folder Screen - Error States

**File:** `ui/screens/folder/FolderErrorStatesTest.kt`  
**Priority:** Medium  
**Estimated Tests:** 12

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `loadFilesError_displaysErrorMessage` | Error loading files | ViewModel with error state | Error text centered |
| `loadFilesError_showsRetryButton` | Retry option shown | Error state | "Retry" button visible |
| `loadFilesError_retryButton_reloadsFiles` | Retry triggers reload | Tap retry | Load attempted again |
| `insufficientSpace_copy_showsToast` | No space for copy | Copy 1GB, 100MB free | Toast "Not enough space" |
| `insufficientSpace_move_showsToast` | No space for move | Cross-storage move, low space | Toast shown |
| `insufficientSpace_uncompress_showsToast` | No space for extract | Uncompress 5GB zip, 1GB free | Toast shown |
| `deleteFailure_showsToast` | Delete fails | Delete protected file | Toast "Failed to delete" |
| `renameFailure_showsToast` | Rename fails | Rename to invalid | Toast shown |
| `createFolderFailure_showsToast` | Create fails | Create in read-only | Toast shown |
| `fileNotFound_openFile_showsToast` | File deleted externally | Open deleted file | Toast "File not found" |
| `permissionDenied_showsToast` | Access denied | Access protected folder | Toast shown |
| `zipBomb_showsError` | Zip bomb detected | Uncompress 15GB archive | Error toast, cleanup done |

### Implementation Notes

```kotlin
@Test
fun insufficientSpace_copy_showsToast() {
    // Mock StatFs to return low space
    val viewModel = FolderViewModel(
        fileRepository = fakeFileRepository,
        spaceChecker = FakeSpaceChecker(availableBytes = 100_000L)
    )
    
    composeTestRule.setContent {
        FolderScreen(viewModel = viewModel)
    }
    
    // Select large file
    composeTestRule
        .onNodeWithText("large_file.zip") // 1GB file
        .performTouchInput { longClick() }
    
    // Tap copy
    composeTestRule
        .onNodeWithContentDescription("Copy to")
        .performClick()
    
    // Navigate to destination and confirm
    // ... picker interactions ...
    
    // Verify toast shown (need toast testing setup)
    // Or verify operation was never started
}
```

### Dependencies
- `FakeSpaceChecker` for disk space mocking
- Toast testing utilities or scaffold snackbar testing

---

## Stage 10: Search Screen - Additional Coverage

**File:** `ui/screens/search/SearchBehaviorTest.kt`  
**Priority:** Medium  
**Estimated Tests:** 12

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `search_debounces300ms` | Search delays 300ms | Type query | Search not called immediately |
| `search_afterDebounce_executesSearch` | Search runs after delay | Type, wait 300ms | Search callback invoked |
| `search_typingContinuously_debounceResets` | Continuous typing delays | Type chars over 500ms | Search called once at end |
| `search_progressiveResults_showsSpinner` | Spinner during streaming | Search in progress | Spinner at list bottom |
| `search_progressiveResults_showsPartialResults` | Results appear incrementally | Streaming results | Items appear as found |
| `search_maxResults_stopsAt100` | Results capped | Search with 150 matches | Only 100 shown |
| `search_emptyQuery_showsEmptyState` | Blank state | Empty/whitespace query | "Start typing" message |
| `search_noResults_showsNoResultsMessage` | No matches state | Query with no matches | "No results" message |
| `search_hiddenFilesSkipped` | Hidden files excluded | Search for `.hidden` | Not in results |
| `search_directoriesExcluded` | Only files returned | Search matching folder | Folder not in results |
| `search_cancelDuringSearch_stopsSearch` | Cancel works | Tap back during search | Search stops |
| `search_newQuery_cancelsOldSearch` | New query cancels old | Type new query during search | Old search stops |

### Implementation Notes

```kotlin
@Test
fun search_debounces300ms() {
    var searchCalled = false
    val viewModel = SearchViewModel(
        searchRepository = object : SearchRepository {
            override suspend fun search(query: String) = flow<FileItem> {
                searchCalled = true
            }
        }
    )
    
    composeTestRule.setContent {
        SearchScreen(viewModel = viewModel)
    }
    
    composeTestRule
        .onNodeWithContentDescription("Search")
        .performTextInput("test")
    
    // Immediately after typing, search should not be called
    assertThat(searchCalled).isFalse()
    
    // Wait for debounce
    composeTestRule.mainClock.advanceTimeBy(350)
    composeTestRule.waitForIdle()
    
    assertThat(searchCalled).isTrue()
}
```

### Dependencies
- Test clock for debounce testing (`mainClock.advanceTimeBy`)
- `FakeSearchRepository` for controlling results

---

## Stage 11: Item Info Screen

**File:** `ui/screens/iteminfo/ItemInfoScreenTest.kt`  
**Priority:** Medium  
**Estimated Tests:** 28

### Test Cases

#### Basic Info
| Test Method | Description |
|-------------|-------------|
| `itemInfo_displaysFileName` | File name shown |
| `itemInfo_displaysLocation` | Parent path shown |
| `itemInfo_displaysCreatedDate` | Created date shown |
| `itemInfo_displaysModifiedDate` | Modified date shown |
| `itemInfo_displaysFileSize` | Size shown for files |
| `itemInfo_displaysFolderItemCount` | Item count for folders |
| `itemInfo_displaysFolderSize` | Calculated size for folders |
| `itemInfo_displaysMimeType` | MIME type shown |
| `itemInfo_tapRow_copiesToClipboard` | Tap copies value |
| `itemInfo_tapRow_showsToast` | Toast on copy (API < 33) |
| `itemInfo_thumbnailTap_opensFile` | Thumbnail opens file |
| `itemInfo_closeButton_dismisses` | Close button works |
| `itemInfo_fileNotFound_showsError` | Error for missing file |

#### Image Metadata
| Test Method | Description |
|-------------|-------------|
| `imageInfo_displaysDimensions` | Width × Height shown |
| `imageInfo_displaysCameraInfo` | Make/model shown |
| `imageInfo_displaysGpsCoordinates` | Lat/long shown |
| `imageInfo_gpsMapButton_opensMap` | Map button launches geo: URI |

#### Audio Metadata
| Test Method | Description |
|-------------|-------------|
| `audioInfo_displaysDuration` | Duration shown |
| `audioInfo_displaysArtist` | Artist shown |
| `audioInfo_displaysAlbum` | Album shown |
| `audioInfo_displaysBitrate` | Bitrate shown |

#### Video Metadata
| Test Method | Description |
|-------------|-------------|
| `videoInfo_displaysDuration` | Duration shown |
| `videoInfo_displaysResolution` | Resolution shown |
| `videoInfo_displaysFrameRate` | Frame rate shown |

#### Archive/Document Metadata
| Test Method | Description |
|-------------|-------------|
| `apkInfo_displaysPackageName` | Package name shown |
| `apkInfo_displaysPermissionCount` | Permission count shown |
| `zipInfo_displaysEntryCount` | Entry count shown |
| `zipInfo_displaysUncompressedSize` | Uncompressed size shown |

### Implementation Notes

```kotlin
@Test
fun imageInfo_gpsMapButton_opensMap() {
    var openedUri: Uri? = null
    val imageFile = createTestImageWithGps(lat = 37.7749, lon = -122.4194)
    
    composeTestRule.setContent {
        ItemInfoScreen(
            filePath = imageFile.absolutePath,
            onOpenMap = { openedUri = it }
        )
    }
    
    composeTestRule
        .onNodeWithContentDescription("Open in Maps")
        .performClick()
    
    assertThat(openedUri.toString()).contains("geo:37.7749,-122.4194")
}
```

### Dependencies
- Test files with metadata (images with EXIF, audio with tags, etc.)
- `MediaMetadataRetriever` mocking or real test assets
- Clipboard testing utilities

---

## Stage 12: Settings Screen - Additional Coverage

**File:** `ui/screens/settings/SettingsDialogsTest.kt`  
**Priority:** Medium  
**Estimated Tests:** 10

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `clearRecentFiles_disabled_whenNoRecentFiles` | Button disabled | Empty recent files | Button not enabled |
| `clearRecentFiles_disabled_whenTrackingOff` | Button disabled | Tracking disabled | Button not enabled |
| `clearRecentFiles_tap_clearsAndShowsToast` | Clear works | With recent files, tap clear | List cleared, toast shown |
| `locationsDialog_checkboxes_toggleCorrectly` | Checkboxes toggle | Tap checkbox | State changes |
| `locationsDialog_saveButton_persistsSelection` | Save persists | Change checkboxes, save | Callback with new set |
| `locationsDialog_cancelButton_discardsChanges` | Cancel discards | Change checkboxes, cancel | Original state preserved |
| `locationsDialog_unselectAll_savesEmptySet` | Empty set allowed | Unselect all, save | Empty set in callback |
| `themeDialog_lightSelection_appliesTheme` | Light theme | Select Light | Theme callback with Light |
| `themeDialog_darkSelection_appliesTheme` | Dark theme | Select Dark | Theme callback with Dark |
| `themeDialog_systemSelection_appliesTheme` | System theme | Select System | Theme callback with System |

### Implementation Notes

```kotlin
@Test
fun clearRecentFiles_disabled_whenNoRecentFiles() {
    composeTestRule.setContent {
        SettingsScreen(
            recentFilesEnabled = true,
            recentFilesCount = 0,  // No recent files
            onClearRecentFiles = {}
        )
    }
    
    composeTestRule
        .onNodeWithText("Clear recent files")
        .assertIsNotEnabled()
}
```

---

## Stage 13: Feedback Screen - Additional Coverage

**File:** `ui/screens/feedback/FeedbackScreenAdditionalTest.kt`  
**Priority:** Low  
**Estimated Tests:** 6

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `feedbackScreen_characterCounter_updatesOnTyping` | Counter updates | Type 50 chars | "50 / 1000" shown |
| `feedbackScreen_atCharacterLimit_showsMaxCount` | At limit | Type 1000 chars | "1000 / 1000" shown |
| `feedbackScreen_atCharacterLimit_disablesFurtherInput` | Input capped | Try typing past 1000 | No more chars added |
| `feedbackScreen_submitSuccess_showsToast` | Success feedback | Submit valid feedback | Success toast shown |
| `feedbackScreen_submitError_showsErrorToast` | Error feedback | Submit fails | Error toast shown |
| `feedbackScreen_submitInProgress_disablesButton` | Button during submit | Submit in progress | Button disabled |

---

## Stage 14: Navigation Integration

**File:** `integration/NavigationIntegrationTest.kt`  
**Priority:** High  
**Estimated Tests:** 14

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `home_tapStorage_navigatesToFolder` | Storage navigation | Tap Internal Storage | FolderScreen at root |
| `home_tapLocation_navigatesToFolder` | Location navigation | Tap Downloads | FolderScreen at Downloads |
| `folder_tapFolder_navigatesDeeper` | Subfolder navigation | Tap folder item | FolderScreen at subfolder |
| `folder_backButton_navigatesToParent` | Back to parent | Tap back | Previous folder shown |
| `folder_backAtRoot_navigatesToHome` | Back to home | Back from storage root | HomeScreen shown |
| `folder_systemBack_navigatesToParent` | System back works | System back press | Parent folder shown |
| `folder_systemBack_inSelectionMode_exitsSelection` | Back exits selection | Back in selection mode | Selection cleared, no navigation |
| `search_openFolder_navigatesToParent` | Open folder action | "Open folder" on search result | FolderScreen at parent |
| `search_backButton_dismissesSearch` | Back dismisses search | Tap back | SearchActivity finishes |
| `drawer_settings_opensSettingsActivity` | Settings navigation | Tap Settings | SettingsActivity shown |
| `drawer_about_opensAboutActivity` | About navigation | Tap About | AboutActivity shown |
| `deepLink_opensFolder` | Intent handling | `OpenPath` intent | FolderScreen at path |
| `deepLink_invalidPath_showsError` | Invalid deep link | Intent with bad path | Error toast |
| `recentFile_openFolder_navigatesToParent` | Recent → folder | "Open folder" on recent | FolderScreen at parent |

### Implementation Notes

```kotlin
@HiltAndroidTest
class NavigationIntegrationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Test
    fun home_tapStorage_navigatesToFolder() {
        // Wait for home screen
        composeTestRule
            .onNodeWithText("Internal Storage")
            .assertIsDisplayed()
        
        // Tap storage
        composeTestRule
            .onNodeWithText("Internal Storage")
            .performClick()
        
        // Verify folder screen shown
        composeTestRule
            .onNodeWithContentDescription("Navigate up")
            .assertIsDisplayed()
    }
}
```

### Dependencies
- Full app with Hilt injection
- Navigation testing utilities
- Activity scenario rules

---

## Stage 15: Theme Testing

**File:** `theme/ThemeRenderingTest.kt`  
**Priority:** Medium  
**Estimated Tests:** 12

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `homeScreen_lightTheme_rendersCorrectly` | Home in light | Light theme | Correct background/text colors |
| `homeScreen_darkTheme_rendersCorrectly` | Home in dark | Dark theme | Correct background/text colors |
| `folderScreen_lightTheme_rendersCorrectly` | Folder in light | Light theme | Correct colors |
| `folderScreen_darkTheme_rendersCorrectly` | Folder in dark | Dark theme | Correct colors |
| `searchScreen_lightTheme_rendersCorrectly` | Search in light | Light theme | Correct colors |
| `searchScreen_darkTheme_rendersCorrectly` | Search in dark | Dark theme | Correct colors |
| `dialogs_lightTheme_renderCorrectly` | Dialogs in light | Light theme | Correct colors |
| `dialogs_darkTheme_renderCorrectly` | Dialogs in dark | Dark theme | Correct colors |
| `actionBar_lightTheme_rendersCorrectly` | Action bar light | Light theme | Correct colors |
| `actionBar_darkTheme_rendersCorrectly` | Action bar dark | Dark theme | Correct colors |
| `bottomSheet_lightTheme_rendersCorrectly` | Bottom sheet light | Light theme | Correct colors |
| `bottomSheet_darkTheme_rendersCorrectly` | Bottom sheet dark | Dark theme | Correct colors |

### Implementation Notes

```kotlin
@Test
fun homeScreen_darkTheme_rendersCorrectly() {
    composeTestRule.setContent {
        FileExplorerTheme(darkTheme = true) {
            HomeScreen(/* ... */)
        }
    }
    
    // Capture screenshot or verify semantic colors
    composeTestRule
        .onRoot()
        .captureToImage()
        .assertBackgroundColor(expectedDarkBackground)
    
    // Or use semantic matchers for Material3 colors
    composeTestRule
        .onNodeWithText("Internal Storage")
        .assertTextColor(MaterialTheme.colorScheme.onSurface)
}
```

### Dependencies
- Screenshot testing library (optional)
- Color assertion utilities
- Theme-aware test setup

---

## Stage 16: RTL Layout Testing

**File:** `rtl/RtlLayoutTest.kt`  
**Priority:** Low  
**Estimated Tests:** 10

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `homeScreen_rtl_layoutCorrect` | Home RTL layout | Arabic locale | Icons/text mirrored |
| `folderScreen_rtl_layoutCorrect` | Folder RTL layout | Arabic locale | Content mirrored |
| `breadcrumbs_rtl_orderedCorrectly` | Breadcrumbs RTL | Arabic locale | Segments in RTL order |
| `swipeActions_rtl_directionsCorrect` | Swipe in RTL | Arabic locale | Swipe directions work |
| `navigationDrawer_rtl_opensFromRight` | Drawer RTL | Arabic locale | Opens from right edge |
| `fileList_rtl_iconsOnRight` | Icons positioned | Arabic locale | Icons on right side |
| `actionBar_rtl_buttonsReversed` | Action bar RTL | Arabic locale | Buttons mirrored |
| `searchField_rtl_textAligned` | Search RTL | Arabic locale | Text right-aligned |
| `dialogs_rtl_layoutCorrect` | Dialogs RTL | Arabic locale | Button order correct |
| `bottomSheet_rtl_layoutCorrect` | Bottom sheet RTL | Arabic locale | Content mirrored |

### Implementation Notes

```kotlin
@Config(qualifiers = "ar")  // Arabic locale
@Test
fun breadcrumbs_rtl_orderedCorrectly() {
    composeTestRule.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Breadcrumbs(
                items = listOf(
                    BreadcrumbItem("Root", "/"),
                    BreadcrumbItem("Folder", "/Folder")
                ),
                onItemClick = {}
            )
        }
    }
    
    // Verify RTL layout
    val rootBounds = composeTestRule
        .onNodeWithText("Root")
        .getBoundsInRoot()
    val folderBounds = composeTestRule
        .onNodeWithText("Folder")
        .getBoundsInRoot()
    
    // In RTL, root should be on the right
    assertThat(rootBounds.left).isGreaterThan(folderBounds.left)
}
```

### Dependencies
- Locale configuration for tests
- `CompositionLocalProvider` with `LocalLayoutDirection`

---

## Stage 17: Edge Cases

**File:** `edge/EdgeCasesTest.kt`  
**Priority:** Medium  
**Estimated Tests:** 14

### Test Cases

| Test Method | Description | Setup | Key Assertions |
|-------------|-------------|-------|----------------|
| `emptyFolder_createFolder_works` | Create in empty | Navigate to empty folder, create | Folder created |
| `caseOnlyRename_works` | Case-only rename | Rename `File.txt` to `file.txt` | Name changed |
| `symlinkFile_notDisplayed` | Symlinks hidden | Folder with symlink | Symlink not in list |
| `symlinkFile_skippedInOperations` | Symlinks skipped | Copy folder with symlink | Symlink not copied |
| `veryLongFileName_truncatesWithEllipsis` | Long name display | 100-char filename | Ellipsis shown |
| `veryLongFileName_infoScreen_showsFull` | Full name in info | 100-char filename | Full name visible |
| `unicodeFileName_displaysCorrectly` | Unicode support | File with emoji name | Emoji displayed |
| `unicodeFileName_operationsWork` | Unicode in operations | Rename/copy emoji file | Operations succeed |
| `specialCharactersInPath_navigationWorks` | Special chars | Folder with `#`, `&`, spaces | Navigation works |
| `largeFileOperation_completes` | Large file | Copy 500MB file | Copy completes |
| `manyFiles_listPerformance` | Many files | Folder with 1000 files | List renders smoothly |
| `deepNesting_navigationWorks` | Deep folders | 20-level deep path | Navigation works |
| `emptyFileName_rejected` | Empty name validation | Try creating "" folder | Error shown |
| `whitespaceOnlyName_rejected` | Whitespace validation | Try creating "   " folder | Error shown |

### Implementation Notes

```kotlin
@Test
fun caseOnlyRename_works() {
    val testFile = tempFolder.newFile("TestFile.txt")
    
    composeTestRule.setContent {
        FolderScreen(path = tempFolder.root.absolutePath)
    }
    
    // Open rename dialog
    composeTestRule
        .onNodeWithText("TestFile.txt")
        .performTouchInput { longClick() }
    
    composeTestRule
        .onNodeWithText("Rename")
        .performClick()
    
    // Clear and type new name
    composeTestRule
        .onNodeWithText("TestFile.txt")
        .performTextClearance()
    
    composeTestRule
        .onNodeWithContentDescription("New name")
        .performTextInput("testfile.txt")
    
    composeTestRule
        .onNodeWithText("Rename")
        .performClick()
    
    // Verify file renamed (case-insensitive filesystem handling)
    composeTestRule.waitForIdle()
    assertThat(File(tempFolder.root, "testfile.txt").exists()).isTrue()
}
```

---

## Shared Test Utilities

### Files to Create

**`testutil/TestFileBuilder.kt`**
```kotlin
object TestFileBuilder {
    fun createImageWithExif(
        tempFolder: TemporaryFolder,
        width: Int = 1920,
        height: Int = 1080,
        lat: Double? = null,
        lon: Double? = null
    ): File { /* ... */ }
    
    fun createAudioWithTags(
        tempFolder: TemporaryFolder,
        title: String = "Test Song",
        artist: String = "Test Artist"
    ): File { /* ... */ }
    
    fun createZipArchive(
        tempFolder: TemporaryFolder,
        entries: List<String>,
        password: String? = null
    ): File { /* ... */ }
}
```

**`testutil/FakeSpaceChecker.kt`**
```kotlin
class FakeSpaceChecker(
    private val availableBytes: Long = Long.MAX_VALUE
) : SpaceChecker {
    override fun getAvailableSpace(path: String): Long = availableBytes
}
```

**`testutil/ComposeTestExtensions.kt`**
```kotlin
fun SemanticsNodeInteraction.assertTextColor(expected: Color) { /* ... */ }
fun SemanticsNodeInteraction.assertBackgroundColor(expected: Color) { /* ... */ }
fun ComposeTestRule.waitForToast(text: String) { /* ... */ }
```

---

## Execution Order

The stages should be implemented in this order to build on dependencies:

1. **Stage 4: Breadcrumbs** - Foundational navigation component
2. **Stage 6: Selection Mode** - Core interaction pattern
3. **Stage 5: Swipe Actions** - Depends on selection mode understanding
4. **Stage 7: Folder Dialogs** - Dialogs used everywhere
5. **Stage 8: File Operations Integration** - End-to-end flows
6. **Stage 3: Home Dialogs** - Reuses patterns from Stage 7
7. **Stage 14: Navigation Integration** - Full app flows
8. **Stage 9: Error States** - Error handling coverage
9. **Stage 10: Search Behavior** - Search-specific logic
10. **Stage 11: Item Info Screen** - Metadata display
11. **Stage 12: Settings Dialogs** - Settings enhancements
12. **Stage 13: Feedback Additional** - Minor additions
13. **Stage 15: Theme Testing** - Visual verification
14. **Stage 17: Edge Cases** - Corner cases
15. **Stage 16: RTL Layout** - Localization testing

---

## Summary

| Stage | Feature Area | Test Count | Priority |
|-------|--------------|------------|----------|
| 3 | Home Dialogs | 18 | High |
| 4 | Breadcrumbs | 12 | High |
| 5 | Swipe Actions | 14 | High |
| 6 | Selection Mode | 16 | High |
| 7 | Folder Dialogs | 24 | High |
| 8 | File Operations E2E | 16 | High |
| 9 | Error States | 12 | Medium |
| 10 | Search Behavior | 12 | Medium |
| 11 | Item Info Screen | 28 | Medium |
| 12 | Settings Dialogs | 10 | Medium |
| 13 | Feedback Additional | 6 | Low |
| 14 | Navigation Integration | 14 | High |
| 15 | Theme Testing | 12 | Medium |
| 16 | RTL Layout | 10 | Low |
| 17 | Edge Cases | 14 | Medium |
| **Total** | | **~198** | |
