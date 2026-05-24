# Testing Next Steps

**Status:** Follow-up to TESTING_IMPROVEMENT_PLAN.md (completed 2026-05-24)
**Current:** 233 tests in 18 files, all passing

---

## 1. Add Dependency Injection for Repository Testing

**Goal:** Enable unit testing of PreferencesRepository, LocationsRepository, RecentFilesRepository, StorageRepository

### 1.1 PreferencesRepository

**Current (not testable):**
```kotlin
class PreferencesRepository(private val dataStore: DataStore<Preferences>)
```

**Problem:** Tests can't create a real DataStore without Android Context.

**Solution:** Create an interface and fake implementation:

```kotlin
// PreferencesSource.kt
interface PreferencesSource {
    val showHidden: Flow<Boolean>
    suspend fun setShowHidden(show: Boolean)
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    // ... other properties
}

// DataStorePreferencesSource.kt (production)
class DataStorePreferencesSource(
    private val dataStore: DataStore<Preferences>
) : PreferencesSource { ... }

// FakePreferencesSource.kt (test)
class FakePreferencesSource : PreferencesSource {
    private val _showHidden = MutableStateFlow(false)
    override val showHidden: Flow<Boolean> = _showHidden
    override suspend fun setShowHidden(show: Boolean) { _showHidden.value = show }
    // ... simple in-memory implementations
}
```

**Then update PreferencesRepository:**
```kotlin
class PreferencesRepository(private val source: PreferencesSource)
```

### 1.2 RecentFilesRepository

**Current:** Uses DataStore for JSON storage

**Solution:** Same pattern - extract `RecentFilesSource` interface

### 1.3 LocationsRepository

**Current:** Depends on PreferencesRepository + filesystem

**Solution:** 
- Accept PreferencesRepository via constructor (already done)
- Add `FileSystem` interface for size calculations

### 1.4 StorageRepository

**Current:** Uses `Context.getExternalFilesDirs()`

**Solution:**
```kotlin
interface StorageSource {
    fun getStorageDevices(): List<StorageDevice>
}

class AndroidStorageSource(context: Context) : StorageSource { ... }
class FakeStorageSource(private val storages: List<StorageDevice>) : StorageSource { ... }
```

### Files to Create
```
app/src/main/java/.../data/source/
├── PreferencesSource.kt
├── DataStorePreferencesSource.kt
├── RecentFilesSource.kt
├── DataStoreRecentFilesSource.kt
├── StorageSource.kt
└── AndroidStorageSource.kt

app/src/test/java/.../data/source/
├── FakePreferencesSource.kt
├── FakeRecentFilesSource.kt
└── FakeStorageSource.kt

app/src/test/java/.../data/repository/
├── PreferencesRepositoryTest.kt
├── RecentFilesRepositoryTest.kt
├── LocationsRepositoryTest.kt
└── StorageRepositoryTest.kt
```

### Estimated Effort
- Interface extraction: 2-3 hours
- Fake implementations: 1-2 hours
- Repository tests: 2-3 hours
- **Total: ~8 hours**

---

## 2. Parameterize Move/Copy Integration Tests

**Goal:** Consolidate duplicate tests in `MoveOperationIntegrationTest` and `CopyOperationIntegrationTest`

**Current State:**
- JUnit 5 with `junit-jupiter-params` already added to build.gradle.kts
- Two nearly identical test files (~200 lines each)

**Solution:**

```kotlin
// FileOperationIntegrationTest.kt
@RunWith(JUnitPlatform::class)
class FileOperationIntegrationTest {

    companion object {
        @JvmStatic
        @ParameterizedTest
        @MethodSource("operationModes")
        fun operationModes() = listOf(
            Arguments.of(OperationMode.MOVE, "Move here", "Move to"),
            Arguments.of(OperationMode.COPY, "Copy here", "Copy to")
        )
    }

    @ParameterizedTest
    @MethodSource("operationModes")
    fun `displays correct action button text`(
        mode: OperationMode,
        actionText: String,
        titleText: String
    ) {
        composeTestRule.setContent {
            DestinationPicker(operationMode = mode, ...)
        }
        composeTestRule.onNodeWithText(actionText).assertIsDisplayed()
    }

    // ... consolidate all shared tests
}
```

**Files to Modify:**
```
app/src/androidTest/java/.../integration/
├── MoveOperationIntegrationTest.kt  → DELETE after merge
├── CopyOperationIntegrationTest.kt  → DELETE after merge
└── FileOperationIntegrationTest.kt  → CREATE (parameterized)
```

**Estimated Effort:** 1-2 hours

---

## 3. Add Error Handling Tests

**Goal:** Test crash resilience for ViewModels

**Prerequisite:** Item 1 (DI) makes this easier but not strictly required

### 3.1 FolderViewModelTest Additions

```kotlin
// Error scenarios to add
@Test fun `listFiles IOException sets error state`()
@Test fun `rename failure shows error snackbar`()
@Test fun `createFolder with invalid name shows validation error`()
@Test fun `delete partial failure reports failed files`()
@Test fun `copy to full disk shows storage error`()
@Test fun `move failure does not delete source files`()
```

### 3.2 SearchViewModelTest Additions

```kotlin
@Test fun `search in inaccessible folder shows error`()
@Test fun `repository exception during search sets error state`()
@Test fun `delete failure shows error message`()
```

### 3.3 PickerViewModelTest Additions

```kotlin
@Test fun `navigate to deleted folder shows error`()
@Test fun `createFolder in read-only location shows error`()
@Test fun `storage unmounted during operation shows error`()
```

**Estimated Effort:** 3-4 hours

---

## 4. Fix Locale-Dependent Tests

**Goal:** Ensure FileSizeFormatterTest passes regardless of system locale

**Current Problem:**
```kotlin
// This assertion fails on non-US locales (e.g., German uses "1,5 KB")
assertEquals("1.5 KB", FileSizeFormatter.format(1536))
```

**Solution:**

```kotlin
// FileSizeFormatterTest.kt
class FileSizeFormatterTest {

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

    @Test
    fun `format returns KB for kilobyte values`() {
        assertEquals("1.5 KB", FileSizeFormatter.format(1536))
    }
    
    // ... existing tests
}
```

**Alternative:** Test the numeric value separately from formatting:
```kotlin
@Test
fun `format returns correct unit for kilobyte values`() {
    val result = FileSizeFormatter.format(1536)
    assertTrue(result.endsWith("KB"))
    assertTrue(result.contains("1") && result.contains("5"))
}
```

**Files to Modify:**
```
app/src/test/java/.../data/util/FileSizeFormatterTest.kt
```

**Estimated Effort:** 15-30 minutes

---

## Priority Order

| # | Task | Effort | Impact | Dependencies |
|---|------|--------|--------|--------------|
| 1 | Fix locale tests | 30 min | Low | None |
| 2 | Parameterize integration tests | 2 hrs | Medium | None |
| 3 | Add DI for repositories | 8 hrs | High | None |
| 4 | Add error handling tests | 4 hrs | High | Easier after #3 |

**Recommended order:** 1 → 2 → 3 → 4

---

## Success Criteria

After completing all items:

| Metric | Current | Target |
|--------|---------|--------|
| Unit test count | 233 | 280+ |
| Repository test coverage | 1/5 | 5/5 |
| Error handling tests | ~10 | 30+ |
| Duplicate test code | High | Minimal |
| Locale-dependent failures | Possible | None |
