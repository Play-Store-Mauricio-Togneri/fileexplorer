# Instrumentation Test Implementation Plan

This document outlines a comprehensive plan for implementing instrumentation tests across 17 stages, covering all missing test scenarios in the File Explorer app.

---

## Overview

**Total Stages:** 15 (10 completed)  
**Estimated Test Count:** ~24 remaining tests  
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

1. ~~**Stage 6: Selection Mode** - Core interaction pattern~~ ✅ DONE
2. ~~**Stage 7: Folder Dialogs** - Dialogs used everywhere~~ ✅ DONE
3. ~~**Stage 8: File Operations Integration** - End-to-end flows~~ ✅ DONE
4. ~~**Stage 14: Navigation Integration** - Full app flows~~ ✅ DONE
5. ~~**Stage 9: Error States** - Error handling coverage~~ ✅ DONE
6. ~~**Stage 10: Search Behavior** - Search-specific logic~~ ✅ DONE
7. ~~**Stage 11: Item Info Screen** - Metadata display~~ ✅ DONE
8. ~~**Stage 12: Settings Dialogs** - Settings enhancements~~ ✅ DONE
9. ~~**Stage 13: Feedback Additional** - Minor additions~~ ✅ DONE
10. ~~**Stage 15: Theme Testing** - Visual verification~~ ✅ DONE
11. **Stage 17: Edge Cases** - Corner cases
12. **Stage 16: RTL Layout** - Localization testing

---

## Summary

| Stage | Feature Area | Test Count | Priority | Status |
|-------|--------------|------------|----------|--------|
| 6 | Selection Mode | 20 | High | ✅ Done |
| 7 | Folder Dialogs | 24 | High | ✅ Done |
| 8 | File Operations E2E | 16 | High | ✅ Done |
| 9 | Error States | 12 | Medium | ✅ Done |
| 10 | Search Behavior | 12 | Medium | ✅ Done |
| 11 | Item Info Screen | 28 | Medium | ✅ Done |
| 12 | Settings Dialogs | 11 | Medium | ✅ Done |
| 13 | Feedback Additional | 8 | Low | ✅ Done |
| 14 | Navigation Integration | 12 | High | ✅ Done |
| 15 | Theme Testing | 20 | Medium | ✅ Done |
| 16 | RTL Layout | 10 | Low | |
| 17 | Edge Cases | 14 | Medium | |
| **Total** | | **~155** | | |
