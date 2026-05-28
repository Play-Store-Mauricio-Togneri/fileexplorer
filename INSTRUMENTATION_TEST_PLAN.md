# Instrumentation Test Implementation Plan

This document outlines a comprehensive plan for implementing instrumentation tests across 17 stages, covering all missing test scenarios in the File Explorer app.

---

## Overview

**Total Stages:** 15 (12 completed)  
**Estimated Test Count:** 0 remaining tests  
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
11. ~~**Stage 17: Edge Cases** - Corner cases~~ ✅ DONE
12. ~~**Stage 16: RTL Layout** - Localization testing~~ ✅ DONE

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
| 16 | RTL Layout | 12 | Low | ✅ Done |
| 17 | Edge Cases | 14 | Medium | ✅ Done |
| **Total** | | **~155** | | |
