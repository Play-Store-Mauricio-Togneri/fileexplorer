# File Operations: Move & Copy

## Overview

Move and copy files/folders using a destination picker approach. User selects items, chooses
Move/Copy, navigates to the target folder in a full-screen picker dialog, then confirms the
operation.

## User Flow

### Single Storage Device

1. User selects one or more files/folders
2. User taps **Move to** or **Copy to** from the action bar
3. Full-screen destination picker dialog opens (slide up animation) at the storage root
4. User navigates to the target folder (can create new folders)
5. User taps **Move here** / **Copy here** to confirm
6. Dialog closes, progress dialog shows during operation (with cancel option)
7. On completion, progress dialog closes silently and file list refreshes

### Multiple Storage Devices

1. User selects one or more files/folders
2. User taps **Move to** or **Copy to** from the action bar
3. Full-screen destination picker dialog opens showing storage selector
4. User taps a storage (Internal storage, SD card, etc.)
5. Picker navigates to that storage's root
6. User navigates to the target folder (can create new folders)
7. User taps **Move here** / **Copy here** to confirm
8. Dialog closes, progress dialog shows during operation (with cancel option)
9. On completion, progress dialog closes silently and file list refreshes

## Architecture

### Dialog with Separate NavHost

The destination picker is implemented as a **full-screen dialog with its own NavHost**, not a
navigation destination in the main NavGraph. This provides:

- Isolated navigation (back = go up in picker, not affect main navigation)
- Clear modal boundary
- Clean cancel handling (close dialog)
- No pollution of main navigation backstack

### State Ownership

The picker dialog owns its own ViewModel for navigation state. Communication with the parent:

**Input (passed to dialog):**

- `items: List<FileItem>` — files/folders being moved/copied
- `mode: OperationMode` — MOVE or COPY

**Output (callbacks):**

- `onConfirm: (targetPath: String) -> Unit` — called when user confirms destination
- `onCancel: () -> Unit` — called when user cancels

The parent (FolderViewModel) handles executing the operation and showing progress.

### Code to Remove

The existing clipboard-based approach must be removed:

| File                      | Action                         |
|---------------------------|--------------------------------|
| `ClipboardManager.kt`     | Delete                         |
| `Clipboard.kt`            | Delete                         |
| `ClipboardMode.kt`        | Delete                         |
| `ClipboardTest.kt`        | Delete                         |
| `ClipboardManagerTest.kt` | Delete                         |
| `FolderViewModel.kt`      | Remove clipboard references    |
| `FolderViewModelTest.kt`  | Remove clipboard-related tests |

## UI Components

### Storage Selector Screen

Shown when multiple storage devices are available. Uses the same visual style as the folder list.

**Each storage row displays:**

- **Line 1:** Storage name (e.g., "Internal storage", "SD card 2")
- **Line 2:** Available space (e.g., "524 MB available")

**Behavior:**

- Tapping a storage navigates to that storage's root folder
- Bottom bar is hidden (no "New folder" or confirm button)
- Toolbar shows title ("Move to" / "Copy to") with close button

### Folder Picker Screen

The main navigation screen within the picker dialog.

**Content:**

- Shows **folders only** (files are completely hidden)
- **Restricted folders are hidden** (folders where the app cannot write)
- **Empty state:** Just an empty list (no message) — bottom bar has "New folder"

**Toolbar (same pattern as normal FolderScreen):**

- **Back button:** Navigates up one folder level, or closes dialog if at root/storage selector
- **Title:** "Move to" (for move) or "Copy to" (for copy)
- **Menu button:** Contextual menu with sort options, show/hide hidden files, etc.

**Bottom Bar:**

- **Left side:** "New folder" button (icon + text, vertical layout, secondary style)
- **Right side:** "Move here" / "Copy here" button (filled Material button, primary style)

**Bottom Bar States:**

| State                   | "New folder" | Confirm button     |
|-------------------------|--------------|--------------------|
| Normal                  | Enabled      | Enabled            |
| Storage selector screen | Hidden       | Hidden             |
| Same folder as source   | Enabled      | Disabled + warning |
| Inside selected folder  | Enabled      | Disabled + warning |

**Warning text** appears below the confirm button when disabled, explaining why:

- "Cannot move to the same folder"
- "Cannot copy to the same folder"
- "Cannot move a folder into itself"
- "Cannot copy a folder into itself"

### Create Folder Flow

Within the picker, user can create a new folder:

1. User taps "New folder" button in bottom bar
2. Create folder dialog appears (reuse existing dialog)
3. User enters folder name and confirms
4. New folder is created
5. **Picker auto-navigates into the new folder** (saves user a tap)

### Dialog Animation

- **Enter:** Slide up from bottom
- **Exit:** Slide down

### Dialog Dismissal

| Trigger                              | Behavior                       |
|--------------------------------------|--------------------------------|
| Back button at root/storage selector | Close dialog, cancel operation |
| Back button in subfolder             | Navigate up one level          |
| Close button in toolbar              | Close dialog, cancel operation |
| System back gesture                  | Same as back button            |

**No confirmation dialog** when canceling — user hasn't performed any action yet.

### Progress Dialog

Modal dialog shown during file operations. **Cannot be dismissed** except via Cancel button.

**Layout (top to bottom):**

1. **Title:** "Moving…" or "Copying…"
2. **Progress bar:** Determinate, based on bytes (no percentage text)
3. **Current file name:** Name of file currently being processed (ellipsized if long)
4. **Cancel button:** Cancels the operation

**Behavior:**

- Back button is **ignored** (user must tap Cancel explicitly)
- Cancel button triggers cancellation flow
- Dialog closes automatically on completion

**Data class:**

```kotlin
data class OperationProgress(
    val mode: OperationMode,        // MOVE or COPY
    val currentFile: String,        // File currently being processed
    val copiedBytes: Long,
    val totalBytes: Long,
    val isCancelling: Boolean       // True when cancel requested
)
```

## Implementation

### Data Models

```kotlin
enum class OperationMode { MOVE, COPY }

data class StorageInfo(
    val path: String,
    val name: String,              // "Internal storage", "SD card", etc.
    val availableBytes: Long,
    val totalBytes: Long
)
```

### Picker ViewModel

The picker dialog has its own ViewModel managing:

```kotlin
class PickerViewModel : ViewModel() {
    // Current path being viewed
    private val _currentPath = MutableStateFlow<String?>(null)

    // List of folders in current path (files excluded, restricted folders excluded)
    private val _folders = MutableStateFlow<List<FileItem>>(emptyList())

    // Available storages (only used when multiple exist)
    private val _storages = MutableStateFlow<List<StorageInfo>>(emptyList())

    // Whether we're showing storage selector or folder list
    private val _showStorageSelector = MutableStateFlow(false)

    // Validation state
    private val _isValidDestination = MutableStateFlow(true)
    private val _validationMessage = MutableStateFlow<String?>(null)

    // Items being moved/copied (for validation)
    private lateinit var sourceItems: List<FileItem>
    private lateinit var operationMode: OperationMode

    fun initialize(items: List<FileItem>, mode: OperationMode) {
        ...
    }
    fun navigateToStorage(storage: StorageInfo) {
        ...
    }
    fun navigateToFolder(path: String) {
        ...
    }
    fun navigateUp(): Boolean {
        ...
    }  // Returns false if should close dialog
    fun createFolder(name: String) {
        ...
    }
    fun validateDestination(path: String) {
        ...
    }
}
```

### Validation Logic

```kotlin
fun validateDestination(targetPath: String): ValidationResult {
    val sourcePaths = sourceItems.map { it.path }
    val sourceParent = File(sourceItems.first().path).parent

    // Check 1: Same folder as source
    if (targetPath == sourceParent) {
        return ValidationResult.Invalid(
            if (operationMode == MOVE) "Cannot move to the same folder"
            else "Cannot copy to the same folder"
        )
    }

    // Check 2: Target is inside a selected folder (recursive)
    for (sourcePath in sourcePaths) {
        if (targetPath.startsWith("$sourcePath/")) {
            return ValidationResult.Invalid(
                if (operationMode == MOVE) "Cannot move a folder into itself"
                else "Cannot copy a folder into itself"
            )
        }
    }

    return ValidationResult.Valid
}
```

### Folder Filtering

When listing folders for the picker:

```kotlin
fun listPickerFolders(path: String): List<FileItem> {
    return File(path).listFiles()
        ?.filter { it.isDirectory }           // Only folders
        ?.filter { !it.isHidden || showHidden } // Respect show hidden preference
        ?.filter { it.canWrite() }            // Only writable (not restricted)
        ?.map { it.toFileItem() }
        ?.sortedWith(currentSortComparator)
        ?: emptyList()
}
```

### Pre-Operation Validation

Before starting the operation, check available space:

```kotlin
suspend fun validateSpace(sources: List<FileItem>, targetStorage: StorageInfo): Boolean {
    val requiredBytes = sources.sumOf { File(it.path).totalSize() }
    return targetStorage.availableBytes >= requiredBytes
}
```

If insufficient space, show toast: "Not enough space" (localized).

### File Operations (Already Implemented)

The existing `FileRepository.copyFiles()` already supports:

- Progress emission via Flow
- Move via `deleteAfter = true` parameter
- Auto-rename on conflict via `getUniqueTargetFile()`
- Recursive directory copying
- Byte-level progress tracking

**No changes needed** to the core copy logic.

### Cancellation

```kotlin
fun executeOperation(targetPath: String) {
    operationJob = viewModelScope.launch {
        try {
            fileRepository.copyFiles(
                sources = selectedItems,
                targetDir = targetPath,
                deleteAfter = (operationMode == MOVE)
            ).collect { progress ->
                _operationProgress.value = progress
            }
            // Success: silent close, refresh
            _operationProgress.value = null
            loadFiles()
        } catch (e: CancellationException) {
            // Cancelled: keep completed files, don't delete source
            _operationProgress.value = null
            loadFiles()
        } catch (e: Exception) {
            // Error: show toast
            _operationProgress.value = null
            _events.emit(ShowToast(getErrorMessage(operationMode)))
            loadFiles()
        }
    }
}

fun cancelOperation() {
    _operationProgress.update { it?.copy(isCancelling = true) }
    operationJob?.cancel()
}
```

### MediaStore Notifications

After operations complete, notify MediaStore so changes appear in Gallery, Music, etc.

```kotlin
object MediaStoreUtil {
    fun scanFiles(context: Context, files: List<File>) {
        MediaScannerConnection.scanFile(
            context,
            files.map { it.absolutePath }.toTypedArray(),
            null,
            null
        )
    }

    fun notifyDeleted(context: Context, paths: List<String>) {
        paths.forEach { path ->
            context.contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.Files.FileColumns.DATA}=?",
                arrayOf(path)
            )
        }
    }
}
```

**When to call:**

| Operation | Action                                                  |
|-----------|---------------------------------------------------------|
| Copy      | `scanFiles(copiedFiles)`                                |
| Move      | `scanFiles(copiedFiles)` + `notifyDeleted(sourcePaths)` |

## Error Handling

**All errors show a simple toast message.** No dialogs, no detailed explanations.

| Error               | Toast Message                 |
|---------------------|-------------------------------|
| Insufficient space  | "Not enough space"            |
| Permission denied   | "Move failed" / "Copy failed" |
| File locked/in use  | "Move failed" / "Copy failed" |
| Any other error     | "Move failed" / "Copy failed" |
| Operation cancelled | No toast (silent)             |

## Completion Behavior

| Outcome          | Behavior                                                  |
|------------------|-----------------------------------------------------------|
| Success          | Silent close, refresh folder, stay in original location   |
| Partial failure  | Toast "Move failed" / "Copy failed", refresh folder       |
| Cancelled        | Silent close, keep completed files, refresh folder        |
| App backgrounded | Operation continues, completes silently (no notification) |

## Conflict Resolution

When a file with the same name exists in the destination:

**Auto-rename** using pattern: `filename (1).ext`, `filename (2).ext`, etc.

This is already implemented in `FileRepository.getUniqueTargetFile()`.

## String Resources

All strings must be added to all supported languages:

| Key                           | English                            | Context                                     |
|-------------------------------|------------------------------------|---------------------------------------------|
| `picker_title_move`           | "Move to"                          | Picker toolbar title                        |
| `picker_title_copy`           | "Copy to"                          | Picker toolbar title                        |
| `picker_confirm_move`         | "Move here"                        | Confirm button                              |
| `picker_confirm_copy`         | "Copy here"                        | Confirm button                              |
| `picker_new_folder`           | "New folder"                       | Create folder button                        |
| `progress_moving`             | "Moving…"                          | Progress dialog title                       |
| `progress_copying`            | "Copying…"                         | Progress dialog title                       |
| `progress_cancel`             | "Cancel"                           | Progress dialog button                      |
| `error_move_failed`           | "Move failed"                      | Error toast                                 |
| `error_copy_failed`           | "Copy failed"                      | Error toast                                 |
| `error_not_enough_space`      | "Not enough space"                 | Error toast                                 |
| `validation_same_folder_move` | "Cannot move to the same folder"   | Warning text                                |
| `validation_same_folder_copy` | "Cannot copy to the same folder"   | Warning text                                |
| `validation_recursive_move`   | "Cannot move a folder into itself" | Warning text                                |
| `validation_recursive_copy`   | "Cannot copy a folder into itself" | Warning text                                |
| `storage_available`           | "%s available"                     | Storage selector (e.g., "524 MB available") |

**Supported languages:** English (default), German, Greek, Spanish, French, Portuguese, Turkish.

## ActionBar Changes

Update existing ActionBar to trigger picker instead of clipboard:

| Current                                        | New Behavior                                   |
|------------------------------------------------|------------------------------------------------|
| "Move to" → `FileAction.Cut` (sets clipboard)  | "Move to" → `FileAction.MoveTo` (opens picker) |
| "Copy to" → `FileAction.Copy` (sets clipboard) | "Copy to" → `FileAction.CopyTo` (opens picker) |

Remove `FileAction.Cut`, `FileAction.Copy`, `FileAction.Paste`. Add `FileAction.MoveTo`,
`FileAction.CopyTo`.

Icons remain the same:

- Move: `Icons.AutoMirrored.Outlined.DriveFileMove`
- Copy: `Icons.Outlined.ContentCopy`

## Checklist

### Phase 1: Cleanup

- [ ] Delete `ClipboardManager.kt`
- [ ] Delete `Clipboard.kt`
- [ ] Delete `ClipboardMode.kt`
- [ ] Delete `ClipboardTest.kt`
- [ ] Delete `ClipboardManagerTest.kt`
- [ ] Remove clipboard references from `FolderViewModel.kt`
- [ ] Remove clipboard-related tests from `FolderViewModelTest.kt`
- [ ] Update `FileAction` sealed interface (remove Cut/Copy/Paste, add MoveTo/CopyTo)
- [ ] Update `ActionBar.kt` to use new actions

### Phase 2: Data Layer

- [ ] Create `OperationMode` enum
- [ ] Create `StorageInfo` data class
- [ ] Create `OperationProgress` data class (if not reusing existing `CopyProgress`)
- [ ] Add `listStorages(): List<StorageInfo>` to FileRepository
- [ ] Add `listPickerFolders(path: String, showHidden: Boolean): List<FileItem>` to FileRepository
- [ ] Create `MediaStoreUtil` helper object
- [ ] Add space validation method

### Phase 3: Picker UI

- [ ] Create `PickerViewModel`
- [ ] Create `DestinationPickerDialog` composable (full-screen dialog with NavHost)
- [ ] Create `StorageSelectorScreen` composable
- [ ] Create `PickerFolderScreen` composable (reuse components from FolderScreen)
- [ ] Create `PickerBottomBar` composable
- [ ] Add slide up/down animation to dialog
- [ ] Implement folder filtering (folders only, writable only)
- [ ] Implement destination validation with warning display
- [ ] Implement create folder flow with auto-navigation

### Phase 4: Progress Dialog

- [ ] Create `ProgressDialog` composable
- [ ] Implement progress tracking from existing `CopyProgress` flow
- [ ] Implement cancel button with `isCancelling` state
- [ ] Block back button during progress

### Phase 5: Integration

- [ ] Connect ActionBar MoveTo/CopyTo to show picker dialog
- [ ] Connect picker confirmation to execute operation
- [ ] Integrate MediaStore notifications after operations
- [ ] Handle completion (silent close, refresh)
- [ ] Handle errors (toast messages)
- [ ] Handle cancellation (keep completed files)

### Phase 6: Strings

- [ ] Add all string resources to `values/strings.xml` (English)
- [ ] Add translations to `values-de/strings.xml` (German)
- [ ] Add translations to `values-el/strings.xml` (Greek)
- [ ] Add translations to `values-es/strings.xml` (Spanish)
- [ ] Add translations to `values-fr/strings.xml` (French)
- [ ] Add translations to `values-pt/strings.xml` (Portuguese)
- [ ] Add translations to `values-tr/strings.xml` (Turkish)

### Phase 7: Testing

- [ ] Unit tests for `PickerViewModel`
- [ ] Unit tests for destination validation logic
- [ ] Unit tests for space validation
- [ ] UI tests for storage selector screen
- [ ] UI tests for picker folder navigation
- [ ] UI tests for create folder in picker
- [ ] UI tests for progress dialog
- [ ] Integration tests for full move/copy flow
