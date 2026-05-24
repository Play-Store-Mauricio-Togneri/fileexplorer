# Testing Improvement Plan

## Executive Summary

**Primary Goal:** Prevent regressions and ensure the app never crashes.

**Definition of Done:** Critical path coverage — FileRepository, UncompressHandler, and core ViewModels are fully tested with both happy paths and error scenarios.

**Current State:**
- 142 Kotlin source files in the codebase
- 15 unit test files with 178 test cases
- 11 instrumentation test files with ~100 test cases
- Estimated coverage: 25-30% of business logic
- **No error scenario testing** — crashes possible

**Key Decisions (from review session 2026-05-24):**
- Real filesystem in temp dirs for file I/O tests (no mocking)
- Add JUnit 5 for parameterized tests
- Fix locale-dependent test assertions
- Parameterize duplicate Move/Copy tests
- Skip automated accessibility/RTL tests (manual testing sufficient)
- ZIP security tests deprioritized to P3 (functional bugs first)
- All 4 repositories need tests
- No CI integration yet — tests run locally

---

## Critical Requirement: Crash Resilience

**The app must never crash.** Every error or exception must be caught and converted to a user-friendly error message.

### Crash Resilience Testing Strategy

Every component that can fail must have tests verifying:
1. **Exception is caught** — no unhandled exceptions propagate
2. **Error state is set** — ViewModel exposes error to UI
3. **User sees feedback** — error message is displayed, not a crash
4. **App remains usable** — user can retry or navigate away

### Error Scenarios to Test

| Component | Error Scenario | Expected Behavior |
|-----------|---------------|-------------------|
| FileRepository | IOException during copy | Show "Copy failed" error, clean up partial files |
| FileRepository | Permission denied | Show "Permission denied" error |
| FileRepository | Disk full | Show "Not enough storage" error |
| FileRepository | File not found | Show "File no longer exists" error |
| FileRepository | Invalid path characters | Show validation error before operation |
| UncompressHandler | Corrupted ZIP | Show "Cannot extract: file is corrupted" |
| UncompressHandler | Wrong password | Show "Incorrect password" and retry dialog |
| UncompressHandler | Out of memory | Show "File too large to extract" |
| HomeViewModel | Storage unavailable | Show empty state, not crash |
| SearchViewModel | Search in deleted folder | Handle gracefully, show error |
| ItemInfoViewModel | Metadata extraction fails | Show "Unknown" for failed fields |
| All ViewModels | Repository throws exception | Catch in viewModelScope, set error state |

### Test Pattern for Crash Resilience

```kotlin
@Test
fun `operation handles IOException without crashing`() = runTest {
    // Arrange: Make repository throw
    coEvery { repository.copyFiles(any(), any()) } throws IOException("Disk full")
    
    // Act: Trigger the operation
    viewModel.onCopyConfirmed(destination)
    
    // Assert: Error state is set, no exception propagates
    viewModel.uiState.test {
        val state = awaitItem()
        assertTrue(state.error != null)
        assertEquals("Not enough storage space", state.error?.message)
        assertFalse(state.isLoading)
    }
}

@Test
fun `UI shows error message when operation fails`() {
    // Compose UI test verifying error is displayed
    composeTestRule.setContent {
        FolderScreen(viewModel = viewModelWithError)
    }
    
    composeTestRule
        .onNodeWithText("Not enough storage space")
        .assertIsDisplayed()
}
```

---

## Phase 1: Critical Business Logic + Crash Resilience (P0)

### 1.1 FileRepository — Core File Operations
**File:** `app/src/main/java/.../data/repository/FileRepository.kt`
**Current Coverage:** Only `sortFiles()` tested
**Risk Level:** CRITICAL — Data loss and crash potential

**Testing Approach:** Real filesystem in temp directories (no mocking)

```kotlin
// FileRepositoryTest.kt — expand existing file

// === Happy Path Tests ===
@Test fun `listFiles returns empty list for empty directory`()
@Test fun `listFiles returns files and folders sorted`()
@Test fun `listFiles filters hidden files when showHidden is false`()
@Test fun `listFiles includes hidden files when showHidden is true`()
@Test fun `createFolder creates new folder successfully`()
@Test fun `rename renames file successfully`()
@Test fun `rename handles case-only rename`()
@Test fun `delete removes file successfully`()
@Test fun `delete removes folder with contents recursively`()
@Test fun `copyFiles copies file with correct content`()
@Test fun `copyFiles handles name collision with incrementing suffix`()
@Test fun `moveFiles moves file and removes source`()
@Test fun `searchFilesStreaming finds files by partial name match`()
@Test fun `searchFilesStreaming is case insensitive`()

// === Error Handling Tests (Crash Resilience) ===
@Test fun `listFiles returns error for non-existent path`()
@Test fun `listFiles returns error for file path instead of directory`()
@Test fun `createFolder returns error for existing folder name`()
@Test fun `createFolder returns error for invalid characters in name`()
@Test fun `createFolder rejects path traversal attempt`()
@Test fun `rename returns error for existing target name`()
@Test fun `rename returns error for invalid characters`()
@Test fun `delete handles non-existent file gracefully`()
@Test fun `delete handles permission denied gracefully`()
@Test fun `copyFiles handles source not found`()
@Test fun `copyFiles handles destination not writable`()
@Test fun `copyFiles handles disk full`()
@Test fun `copyFiles cleans up partial files on failure`()
@Test fun `moveFiles handles source locked by another process`()
@Test fun `searchFilesStreaming handles empty query`()
@Test fun `searchFilesStreaming handles deleted directory during search`()
```

### 1.2 UncompressHandler — ZIP Operations
**File:** `app/src/main/java/.../util/UncompressHandler.kt`
**Current Coverage:** NONE
**Risk Level:** HIGH — Functional bugs reported

```kotlin
// UncompressHandlerTest.kt — new file

// === Happy Path Tests ===
@Test fun `showDialog sets correct initial state`()
@Test fun `dismissDialog clears state`()
@Test fun `startUncompress extracts files successfully`()
@Test fun `startUncompress reports progress accurately`()
@Test fun `startUncompress handles password-protected ZIP`()
@Test fun `cancelUncompress stops operation`()
@Test fun `cancelUncompress cleans up partial extraction`()

// === Error Handling Tests (Crash Resilience) ===
@Test fun `startUncompress handles corrupted ZIP file`()
@Test fun `startUncompress handles wrong password`()
@Test fun `startUncompress handles insufficient storage`()
@Test fun `startUncompress handles read-only destination`()
@Test fun `startUncompress handles missing source file`()
@Test fun `startUncompress handles out of memory`()
@Test fun `extraction failure does not crash app`()
@Test fun `error state shows user-friendly message`()
```

### 1.3 ItemInfoViewModel
**File:** `app/src/main/java/.../ui/screens/iteminfo/ItemInfoViewModel.kt`
**Current Coverage:** NONE

```kotlin
// ItemInfoViewModelTest.kt — new file

// === Happy Path Tests ===
@Test fun `loadFileInfo sets loading state initially`()
@Test fun `loadFileInfo displays file name and path`()
@Test fun `loadFileInfo displays formatted file size`()
@Test fun `loadFileInfo displays last modified date`()
@Test fun `loadFileInfo calculates folder size asynchronously`()
@Test fun `loadFileInfo extracts image metadata for images`()
@Test fun `loadFileInfo extracts audio metadata for audio files`()

// === Error Handling Tests (Crash Resilience) ===
@Test fun `loadFileInfo handles missing file gracefully`()
@Test fun `loadFileInfo handles metadata extraction failure`()
@Test fun `loadFileInfo shows unknown for unreadable metadata`()
@Test fun `folder size calculation handles permission denied`()
@Test fun `corrupted file metadata does not crash`()
```

### 1.4 HomeViewModel — Expand Beyond Badges
**File:** `app/src/main/java/.../ui/screens/home/HomeViewModel.kt`
**Current Coverage:** Only badge functionality

```kotlin
// HomeViewModelTest.kt — new file (separate from badge tests)

// === Happy Path Tests ===
@Test fun `loadData loads storages successfully`()
@Test fun `loadData loads locations successfully`()
@Test fun `loadData loads recent files successfully`()
@Test fun `showRecentFileActions sets selected file`()
@Test fun `removeFromRecents removes file and dismisses dialog`()
@Test fun `confirmDeleteRecentFile deletes file and removes from recents`()

// === Error Handling Tests (Crash Resilience) ===
@Test fun `loadData handles empty storages`()
@Test fun `loadData handles storage read failure`()
@Test fun `onOpenFile shows error for deleted file`()
@Test fun `confirmDeleteRecentFile handles already deleted file`()
@Test fun `recent file with missing path shows error not crash`()
```

---

## Phase 2: Repository Layer (P1)

All repositories need tests. Use real DataStore/SharedPreferences where possible.

### 2.1 PreferencesRepository
```kotlin
// PreferencesRepositoryTest.kt — new file

@Test fun `sortMode flow emits saved value`()
@Test fun `setSortMode persists value`()
@Test fun `showHiddenFiles flow emits saved value`()
@Test fun `setShowHiddenFiles persists value`()
@Test fun `enabledLocations flow emits saved value`()
@Test fun `setEnabledLocations persists value`()
@Test fun `badge flows emit correct initial state`()
@Test fun `dismissBadge persists dismissed state`()

// === Error Handling ===
@Test fun `corrupted preferences return defaults not crash`()
```

### 2.2 LocationsRepository
```kotlin
// LocationsRepositoryTest.kt — new file

@Test fun `getLocations returns all location types`()
@Test fun `getLocations filters by enabled locations`()
@Test fun `getLocations calculates size for each location`()
@Test fun `location types have correct paths`()

// === Error Handling ===
@Test fun `getLocations handles missing directory gracefully`()
@Test fun `getLocations handles permission denied`()
@Test fun `size calculation failure returns zero not crash`()
```

### 2.3 RecentFilesRepository
```kotlin
// RecentFilesRepositoryTest.kt — new file

@Test fun `addRecentFile adds to list`()
@Test fun `addRecentFile moves existing file to top`()
@Test fun `addRecentFile respects max file limit`()
@Test fun `removeRecentFile removes from list`()
@Test fun `clearRecentFiles empties list`()
@Test fun `recentFiles flow filters non-existent files`()
@Test fun `JSON serialization round-trips correctly`()

// === Error Handling ===
@Test fun `corrupted JSON returns empty list not crash`()
@Test fun `malformed file entry is skipped not crash`()
```

### 2.4 StorageRepository
```kotlin
// StorageRepositoryTest.kt — new file

@Test fun `getStorages returns internal storage`()
@Test fun `getStorages returns SD card if available`()
@Test fun `isValidPath returns true for accessible paths`()
@Test fun `isValidPath returns false for invalid paths`()

// === Error Handling ===
@Test fun `unmounted storage handled gracefully`()
@Test fun `storage read error returns empty list`()
```

---

## Phase 3: ViewModel Error Handling (P1)

Expand existing ViewModel tests with error scenarios.

### 3.1 FolderViewModel — Add Error Handling Tests
**Current:** 47 tests (good coverage of happy paths)

```kotlin
// Add to FolderViewModelTest.kt

// === Error Handling Tests ===
@Test fun `listFiles error sets error state`()
@Test fun `rename failure shows error message`()
@Test fun `createFolder failure shows error message`()
@Test fun `delete failure shows error message`()
@Test fun `delete partial failure reports which files failed`()
@Test fun `copy failure cleans up and shows error`()
@Test fun `move failure does not delete source`()
@Test fun `operation on deleted file shows file not found error`()
@Test fun `concurrent operations do not crash`()
```

### 3.2 SearchViewModel — Add Error Handling Tests
**Current:** 8 tests

```kotlin
// Add to SearchViewModelTest.kt

@Test fun `search in deleted folder shows error`()
@Test fun `search handles repository exception`()
@Test fun `onDeleteConfirmed handles delete failure`()
@Test fun `file action on missing file shows error`()
@Test fun `rapid query changes do not crash`()
```

### 3.3 PickerViewModel — Add Error Handling Tests
**Current:** 23 tests

```kotlin
// Add to PickerViewModelTest.kt

@Test fun `navigation to deleted folder shows error`()
@Test fun `createFolder failure shows error in picker`()
@Test fun `storage unmounted during navigation shows error`()
```

---

## Phase 4: Test Infrastructure (P2)

### 4.1 Add JUnit 5 for Parameterized Tests

**build.gradle.kts changes:**
```kotlin
testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.0") // JUnit 4 compat
```

### 4.2 Parameterize Duplicate Tests

Consolidate `MoveOperationIntegrationTest` and `CopyOperationIntegrationTest`:

```kotlin
@RunWith(Parameterized::class)
class FileOperationIntegrationTest(
    private val mode: OperationMode,
    private val actionText: String,
    private val titleText: String
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(
            arrayOf(OperationMode.MOVE, "Move here", "Move to"),
            arrayOf(OperationMode.COPY, "Copy here", "Copy to")
        )
    }
    
    @Test fun `displays correct title for operation`() { ... }
    @Test fun `action button shows correct text`() { ... }
    // ... all shared tests
}
```

### 4.3 Fix Locale-Dependent Tests

```kotlin
// FileSizeFormatterTest.kt

private lateinit var originalLocale: Locale

@Before
fun setUp() {
    originalLocale = Locale.getDefault()
    Locale.setDefault(Locale.US)
}

@After
fun tearDown() {
    Locale.setDefault(originalLocale)
}
```

### 4.4 Fix Incomplete Test

**File:** `MoveOperationIntegrationTest.kt`
**Issue:** `cancelTriggersCallback` declares callback but never clicks cancel.

```kotlin
@Test
fun cancelTriggersCallback() {
    var cancelClicked = false
    composeTestRule.setContent {
        FileExplorerTheme {
            DestinationPicker(
                // ...
                onCancel = { cancelClicked = true }
            )
        }
    }
    
    // THIS WAS MISSING:
    composeTestRule.onNodeWithContentDescription("Close").performClick()
    
    assertTrue(cancelClicked)
}
```

### 4.5 Fix FileListItemTest Missing Coverage

```kotlin
// Add to FileListItemTest.kt

@Test
fun `menu click triggers onMenuClick callback`() {
    var menuClicked = false
    composeTestRule.setContent {
        FileListItem(
            file = testFile,
            onMenuClick = { menuClicked = true },
            // ...
        )
    }
    
    composeTestRule.onNodeWithContentDescription("More options").performClick()
    assertTrue(menuClicked)
}
```

---

## Phase 5: Lower Priority Items (P3)

### 5.1 ZIP Security Tests (Functional First)
```kotlin
// ZipSecurityTest.kt — instrumentation test

@Test fun `ZIP with deeply nested folders extracts correctly`()
@Test fun `ZIP with many small files extracts correctly`()
@Test fun `large ZIP file extracts with progress updates`()

// Security (lower priority per review)
@Test fun `ZIP slip attack is detected and blocked`()
@Test fun `ZIP bomb by compression ratio is detected`()
```

### 5.2 Utility Expansion

```kotlin
// MimeTypeUtilTest.kt additions
@Test fun `getMimeType returns correct type for common extensions`()
@Test fun `getMimeType returns octet-stream for unknown extensions`()

// FileNameValidatorTest.kt — new file
@Test fun `validate rejects empty name`()
@Test fun `validate rejects invalid characters`()
@Test fun `validate accepts valid names`()
```

### 5.3 Metadata Extractors (Low Priority)

Only add tests if regressions occur. These are display-only features.

---

## Implementation Priority Matrix

| Priority | Item | Effort | Impact |
|----------|------|--------|--------|
| **P0** | FileRepository error handling | High | Critical — prevents crashes |
| **P0** | UncompressHandler tests | Medium | High — reported bugs |
| **P1** | ItemInfoViewModel tests | Medium | Medium |
| **P1** | HomeViewModel tests | Medium | Medium |
| **P1** | All 4 Repository tests | Medium | Medium |
| **P1** | ViewModel error handling expansion | Medium | High — crash resilience |
| **P2** | Add JUnit 5 | Low | Quality |
| **P2** | Parameterize duplicate tests | Low | Maintenance |
| **P2** | Fix locale tests | Low | CI stability |
| **P2** | Fix incomplete/missing tests | Low | Coverage |
| **P3** | ZIP functional tests | Medium | Medium |
| **P3** | Utility expansion | Low | Low |
| **P4** | Metadata extractors | Low | Low |

---

## Success Metrics

| Metric | Current | Target |
|--------|---------|--------|
| Unit test count | 178 | 300+ |
| Instrumentation test count | ~100 | 130+ |
| ViewModels with tests | 6/10 | 10/10 |
| Repositories with tests | 1/5 | 5/5 |
| Error handling test coverage | 0% | 100% of critical paths |
| App crash scenarios tested | 0 | All identified scenarios |
| Test file duplication | High | Minimal (parameterized) |

---

## Test File Summary

### Files to Create
```
app/src/test/
├── data/repository/
│   ├── LocationsRepositoryTest.kt
│   ├── PreferencesRepositoryTest.kt
│   ├── RecentFilesRepositoryTest.kt
│   └── StorageRepositoryTest.kt
├── ui/screens/
│   ├── about/AboutViewModelTest.kt
│   ├── home/HomeViewModelTest.kt
│   └── iteminfo/ItemInfoViewModelTest.kt
└── util/
    ├── FileNameValidatorTest.kt
    └── UncompressHandlerTest.kt
```

### Files to Expand
```
app/src/test/
├── data/repository/FileRepositoryTest.kt  ← Add 20+ error handling tests
└── ui/screens/
    ├── folder/FolderViewModelTest.kt      ← Add 10+ error tests
    ├── search/SearchViewModelTest.kt      ← Add 5+ error tests
    └── picker/PickerViewModelTest.kt      ← Add 3+ error tests
```

### Files to Fix/Refactor
```
app/src/test/data/util/FileSizeFormatterTest.kt     ← Fix locale
app/src/androidTest/integration/
├── MoveOperationIntegrationTest.kt                  ← Fix incomplete test, then merge
└── CopyOperationIntegrationTest.kt                  ← Merge into parameterized test
app/src/androidTest/ui/components/FileListItemTest.kt ← Add onMenuClick test
```

### Files to Delete (after parameterization)
```
app/src/androidTest/integration/CopyOperationIntegrationTest.kt  ← Merge into FileOperationIntegrationTest
app/src/androidTest/integration/MoveOperationIntegrationTest.kt  ← Merge into FileOperationIntegrationTest
```

---

## Decisions Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-05-24 | Goal: Prevent regressions | User has been burned by file ops, UI state, ZIP bugs |
| 2026-05-24 | Critical path as done criteria | Prevents infinite backlog |
| 2026-05-24 | File ops > ZIP > Navigation > Search | Ranked by damage potential |
| 2026-05-24 | Real filesystem (no mocks) | Higher confidence for file I/O |
| 2026-05-24 | Add JUnit 5 | Enables parameterized tests |
| 2026-05-24 | Fix locale in tests | Prevents CI flakiness |
| 2026-05-24 | Skip rotation tests | Trust the framework |
| 2026-05-24 | Skip accessibility tests | Manual testing sufficient |
| 2026-05-24 | ZIP security → P3 | Functional bugs first |
| 2026-05-24 | Metadata extractors → P4 | Display-only, low risk |
| 2026-05-24 | All 4 repositories need tests | Complete coverage requested |
| 2026-05-24 | No CI integration | Tests run locally only |
| 2026-05-24 | **Crash resilience required** | App must never crash, all errors caught |
