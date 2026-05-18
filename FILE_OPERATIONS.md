# File Operations: Move & Copy

## Overview

Move and copy files/folders using a destination picker approach. User selects items, chooses
Move/Copy, navigates to the target folder in a full-screen picker, then confirms the operation.

## User Flow

1. User selects one or more files/folders
2. User taps **Move** or **Copy** from the action menu
3. Full-screen destination picker opens showing folder navigation
4. User navigates to the target folder (can create new folders)
5. User taps **Move here** / **Copy here** to confirm
6. Progress dialog shows during operation (with cancel option)
7. On completion, picker closes and file list refreshes

## UI Components

### Destination Picker Screen

Full-screen composable for selecting the target folder.

```kotlin
@Composable
fun DestinationPickerScreen(
    items: List<FileItem>,          // Items being moved/copied
    mode: OperationMode,            // MOVE or COPY
    onConfirm: (targetPath: Path) -> Unit,
    onCancel: () -> Unit
)
```

**Features:**

- Toolbar with title ("Move to" / "Copy to") and close button
- Breadcrumb or back navigation for folder hierarchy
- Folder list (only folders, not files)
- "New folder" FAB or toolbar action
- Confirm button at bottom ("Move here" / "Copy here")
- Disable confirm if target is inside a selected folder (prevent recursive move)

### Progress Dialog

Modal dialog shown during file operations.

```kotlin
@Composable
fun ProgressDialog(
    progress: OperationProgress,
    onCancel: () -> Unit
)

data class OperationProgress(
    val mode: OperationMode,        // MOVE or COPY
    val currentFile: String,        // File currently being processed
    val processedFiles: Int,
    val totalFiles: Int,
    val processedBytes: Long,
    val totalBytes: Long,
    val isCancelling: Boolean       // True when cancel requested, waiting for cleanup
)
```

**Features:**

- Title: "Moving..." or "Copying..."
- Current file name (ellipsized if long)
- Progress bar (determinate, based on bytes or file count)
- Cancel button (disabled while cancelling)

## Implementation

### State Management

```kotlin
enum class OperationMode { MOVE, COPY }

data class PendingOperation(
    val items: List<FileItem>,
    val mode: OperationMode
)

// In FolderViewModel or dedicated OperationViewModel
private val _pendingOperation = MutableStateFlow<PendingOperation?>(null)
private val _operationProgress = MutableStateFlow<OperationProgress?>(null)
```

**Flow:**

1. User triggers move/copy → set `_pendingOperation`
2. Navigate to DestinationPickerScreen
3. User confirms → call `executeOperation(targetPath)`
4. Collect progress from repository → update `_operationProgress`
5. On completion → clear state, refresh file list, notify MediaStore

### FileRepository Methods

```kotlin
interface FileRepository {
    fun copyFiles(
        sources: List<Path>,
        targetDir: Path,
        onConflict: ConflictStrategy = ConflictStrategy.OVERWRITE
    ): Flow<OperationProgress>

    fun moveFiles(
        sources: List<Path>,
        targetDir: Path,
        onConflict: ConflictStrategy = ConflictStrategy.OVERWRITE
    ): Flow<OperationProgress>
}

enum class ConflictStrategy { OVERWRITE, SKIP, ASK }
```

**Implementation notes:**

- Use `Flow` to emit progress updates
- For move: try `Files.move()` first (atomic on same filesystem), fall back to copy+delete
- Copy recursively for directories
- Emit progress after each file completes
- Support cancellation via `currentCoroutineContext().ensureActive()`

### Cancellation

```kotlin
fun executeOperation(targetPath: Path) {
    operationJob = viewModelScope.launch {
        fileRepository.moveFiles(sources, targetPath)
            .collect { progress ->
                _operationProgress.value = progress
            }
    }
}

fun cancelOperation() {
    _operationProgress.update { it?.copy(isCancelling = true) }
    operationJob?.cancel()
    // Cleanup: keep successfully copied files, don't delete source on cancelled move
}
```

### MediaStore Notifications

Notify Android's MediaStore so changes appear in Gallery, Music, etc.

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

    fun notifyDeleted(context: Context, path: String) {
        context.contentResolver.delete(
            MediaStore.Files.getContentUri("external"),
            "${MediaStore.Files.FileColumns.DATA}=?",
            arrayOf(path)
        )
    }
}
```

**When to call:**

| Operation | Action                                                  |
|-----------|---------------------------------------------------------|
| Copy      | `scanFiles(copiedFiles)`                                |
| Move      | `scanFiles(copiedFiles)` + `notifyDeleted(sourcePaths)` |

## Error Handling

| Error                            | Handling                                              |
|----------------------------------|-------------------------------------------------------|
| Target is subdirectory of source | Disable confirm button, show warning                  |
| Insufficient storage space       | Show error dialog before starting                     |
| Permission denied                | Show error with guidance to grant permission          |
| File locked / in use             | Skip file, report in summary                          |
| Operation cancelled              | Keep completed files, show partial completion message |

All error messages must use localized strings from `strings.xml`.

## Checklist

- [ ] Create `DestinationPickerScreen` composable
- [ ] Add folder-only navigation mode
- [ ] Implement "New folder" action in picker
- [ ] Add `OperationProgress` data class
- [ ] Create `ProgressDialog` composable
- [ ] Implement `FileRepository.copyFiles()` with progress Flow
- [ ] Implement `FileRepository.moveFiles()` with progress Flow
- [ ] Add cancellation support
- [ ] Create `MediaStoreUtil` helper
- [ ] Integrate MediaStore notifications after operations
- [ ] Add navigation from selection → picker → operation
- [ ] Handle edge cases (same folder, recursive move, no space)
- [ ] Add string resources for all UI text (all languages)
- [ ] Unit tests for repository operations
- [ ] UI tests for picker and progress dialog
