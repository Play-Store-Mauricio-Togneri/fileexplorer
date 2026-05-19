# File Operations: Move & Copy

## Overview

Move and copy files/folders using a destination picker approach. User selects items, chooses
Move/Copy, navigates to the target folder in a full-screen picker overlay, then confirms the
operation.

## User Flow

### Single Storage Device

1. User selects one or more files/folders
2. User taps **Move to** or **Copy to** from the action bar
3. Full-screen destination picker opens (slide up animation) at the storage root
4. User navigates to the target folder (can create new folders)
5. User taps **Move here** / **Copy here** to confirm
6. Picker closes, progress dialog shows during operation (with cancel option)
7. On completion, progress dialog closes silently and file list refreshes

### Multiple Storage Devices

1. User selects one or more files/folders
2. User taps **Move to** or **Copy to** from the action bar
3. Full-screen destination picker opens showing storage selector
4. User taps a storage (Internal storage, SD card, etc.)
5. Picker navigates to that storage's root
6. User navigates to the target folder (can create new folders)
7. User taps **Move here** / **Copy here** to confirm
8. Picker closes, progress dialog shows during operation (with cancel option)
9. On completion, progress dialog closes silently and file list refreshes

## Architecture

### Overlay with AnimatedVisibility

The destination picker is implemented as a **full-screen overlay** rendered within `FolderScreen`
using `AnimatedVisibility`, not a separate dialog window or navigation destination. This provides:

- Full control over slide animation
- No dialog window issues (focus, IME, system bars)
- Simple back handling via `BackHandler`
- Clean integration with parent composable

```kotlin
// In FolderScreen
Box(modifier = Modifier.fillMaxSize()) {
    // Normal folder content
    FolderContent(...)

    // Picker overlay when active
    AnimatedVisibility(
        visible = state.pickerRequest != null,
        enter = slideInVertically { it },  // from bottom
        exit = slideOutVertically { it }   // to bottom
    ) {
        DestinationPicker(...)
    }
}
```

### State-Based Navigation

The picker uses **state-based navigation** rather than a nested NavHost. The PickerViewModel holds
`currentPath: String?` where:

- `null` = show storage selector (only when multiple storages exist)
- non-null = show folder list at that path

This is simpler than nested navigation and matches the existing FolderViewModel pattern.

### State Ownership

**Picker trigger (in FolderUiState):**

```kotlin
data class PickerRequest(
    val items: List<FileItem>,
    val mode: OperationMode
)

// In FolderUiState:
val pickerRequest: PickerRequest? = null
```

When `pickerRequest` is non-null, FolderScreen renders the picker overlay. FolderScreen creates
the PickerViewModel based on this request.

**Picker ViewModel** owns its own navigation state. Communication with the parent:

**Input (passed to ViewModel):**

- `items: List<FileItem>` — files/folders being moved/copied
- `mode: OperationMode` — MOVE or COPY
- `sortMode: SortMode` — inherited from FolderScreen
- `showHidden: Boolean` — inherited from FolderScreen

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
| `ClipboardMode.kt`        | Delete (enum is inside Clipboard.kt) |
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
- Bottom bar is **hidden** (no "New folder" or confirm button)
- Toolbar shows title ("Move to" / "Copy to") with back button
- Back button closes the picker

### Folder Picker Screen

The main navigation screen within the picker.

**Content:**

- Shows **folders only** (files are completely hidden)
- **Restricted folders are hidden** (folders where the app cannot write)
- **Empty state:** Just an empty list (no message) — bottom bar has "New folder"
- **Breadcrumbs:** Shows current path, allows jumping to ancestor folders

**Toolbar:**

- **Back button:** Navigates up one folder level, or closes picker if at root/storage selector
- **Title:** "Move to" (for move) or "Copy to" (for copy)
- **No menu button** — picker inherits sort/hidden settings from FolderScreen

**Folder List Items (Simplified):**

- **Icon:** Folder icon only
- **Text:** Folder name only (no metadata like item count or date)
- **No swipe actions**
- **No contextual menu icon**
- **Single tap:** Navigate into folder

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
2. Create folder dialog appears (reuse existing `CreateFolderDialog`)
3. User enters folder name and confirms
4. New folder is created
5. **Picker auto-navigates into the new folder** (saves user a tap)

### Picker Animation

- **Enter:** Slide up from bottom (`slideInVertically { it }`)
- **Exit:** Slide down to bottom (`slideOutVertically { it }`)

### Picker Dismissal

| Trigger                              | Behavior                       |
|--------------------------------------|--------------------------------|
| Back button at root/storage selector | Close picker, cancel operation |
| Back button in subfolder             | Navigate up one level          |
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

## Implementation

### Data Models

**Location:** `data/model/`

```kotlin
// OperationMode.kt
enum class OperationMode { MOVE, COPY }

// OperationProgress.kt
data class OperationProgress(
    val mode: OperationMode,
    val currentFile: String,
    val copiedBytes: Long,
    val totalBytes: Long,
    val isCancelling: Boolean = false
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) copiedBytes.toFloat() / totalBytes else 0f
}

// PickerRequest.kt (or inline in FolderUiState)
data class PickerRequest(
    val items: List<FileItem>,
    val mode: OperationMode
)
```

**Reuse existing:** `StorageDevice` (do not create separate `StorageInfo`)

### Picker ViewModel

**Location:** `ui/screens/picker/PickerViewModel.kt`

Uses the same Factory pattern as FolderViewModel:

```kotlin
class PickerViewModel(
    private val context: Context,
    private val fileRepository: FileRepository,
    private val storageRepository: StorageRepository,
    private val sourceItems: List<FileItem>,
    private val operationMode: OperationMode,
    private val sortMode: SortMode,
    private val showHidden: Boolean
) : ViewModel() {

    // Current path being viewed (null = storage selector)
    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    // List of folders in current path
    private val _folders = MutableStateFlow<List<FileItem>>(emptyList())
    val folders: StateFlow<List<FileItem>> = _folders.asStateFlow()

    // Available storages
    private val _storages = MutableStateFlow<List<StorageDevice>>(emptyList())
    val storages: StateFlow<List<StorageDevice>> = _storages.asStateFlow()

    // Whether showing storage selector (multiple storages and currentPath is null)
    val showStorageSelector: StateFlow<Boolean> = combine(_storages, _currentPath) { storages, path ->
        storages.size > 1 && path == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Validation state
    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    val isValidDestination: StateFlow<Boolean> = _validationError.map { it == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Create folder dialog
    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog: StateFlow<Boolean> = _showCreateFolderDialog.asStateFlow()

    init {
        loadStorages()
    }

    private fun loadStorages() {
        viewModelScope.launch {
            val storageList = storageRepository.getStorages()
            _storages.value = storageList

            // If single storage, go directly to its root
            if (storageList.size == 1) {
                navigateToPath(storageList.first().path)
            }
        }
    }

    fun navigateToStorage(storage: StorageDevice) {
        navigateToPath(storage.path)
    }

    fun navigateToFolder(folder: FileItem) {
        navigateToPath(folder.path)
    }

    private fun navigateToPath(path: String) {
        _currentPath.value = path
        loadFolders(path)
        validateDestination(path)
    }

    private fun loadFolders(path: String) {
        viewModelScope.launch {
            val allItems = fileRepository.listFiles(path, sortMode, showHidden)
            val folders = allItems.filter { item ->
                item.isDirectory && File(item.path).canWrite()
            }
            _folders.value = folders
        }
    }

    /**
     * Navigate up one level.
     * @return true if navigated up, false if should close picker
     */
    fun navigateUp(): Boolean {
        val current = _currentPath.value ?: return false

        // Check if at storage root
        val storageRoots = _storages.value.map { it.path }
        if (current in storageRoots) {
            // At storage root
            return if (_storages.value.size > 1) {
                // Multiple storages: go back to storage selector
                _currentPath.value = null
                _folders.value = emptyList()
                _validationError.value = null
                true
            } else {
                // Single storage: close picker
                false
            }
        }

        // Navigate to parent
        val parent = File(current).parent
        if (parent != null) {
            navigateToPath(parent)
            return true
        }

        return false
    }

    private fun validateDestination(targetPath: String) {
        val sourceParent = File(sourceItems.first().path).parent

        // Check 1: Same folder as source
        if (targetPath == sourceParent) {
            _validationError.value = if (operationMode == OperationMode.MOVE) {
                context.getString(R.string.validation_same_folder_move)
            } else {
                context.getString(R.string.validation_same_folder_copy)
            }
            return
        }

        // Check 2: Target is inside a selected folder
        for (sourceItem in sourceItems) {
            if (targetPath.startsWith(sourceItem.path + "/")) {
                _validationError.value = if (operationMode == OperationMode.MOVE) {
                    context.getString(R.string.validation_recursive_move)
                } else {
                    context.getString(R.string.validation_recursive_copy)
                }
                return
            }
        }

        _validationError.value = null
    }

    fun showCreateFolderDialog() {
        _showCreateFolderDialog.value = true
    }

    fun dismissCreateFolderDialog() {
        _showCreateFolderDialog.value = false
    }

    fun createFolder(name: String) {
        val currentPath = _currentPath.value ?: return
        viewModelScope.launch {
            val success = fileRepository.createFolder(currentPath, name)
            dismissCreateFolderDialog()
            if (success) {
                // Auto-navigate into the new folder
                val newFolderPath = "$currentPath/$name"
                navigateToPath(newFolderPath)
            }
        }
    }

    fun getCurrentPath(): String? = _currentPath.value

    class Factory(
        private val context: Context,
        private val fileRepository: FileRepository,
        private val storageRepository: StorageRepository,
        private val sourceItems: List<FileItem>,
        private val operationMode: OperationMode,
        private val sortMode: SortMode,
        private val showHidden: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PickerViewModel(
                context.applicationContext,
                fileRepository,
                storageRepository,
                sourceItems,
                operationMode,
                sortMode,
                showHidden
            ) as T
        }
    }
}
```

### FolderViewModel Changes

Add picker and operation state:

```kotlin
// In FolderUiState, add:
val pickerRequest: PickerRequest? = null,
val operationProgress: OperationProgress? = null

// In FolderViewModel, add:
private var operationJob: Job? = null

fun onMoveTo() {
    val selectedItems = getSelectedFiles()
    if (selectedItems.isEmpty()) return

    _state.update {
        it.copy(
            pickerRequest = PickerRequest(
                items = selectedItems,
                mode = OperationMode.MOVE
            ),
            selectedPaths = emptySet()  // Clear selection
        )
    }
}

fun onCopyTo() {
    val selectedItems = getSelectedFiles()
    if (selectedItems.isEmpty()) return

    _state.update {
        it.copy(
            pickerRequest = PickerRequest(
                items = selectedItems,
                mode = OperationMode.COPY
            ),
            selectedPaths = emptySet()  // Clear selection
        )
    }
}

fun dismissPicker() {
    _state.update { it.copy(pickerRequest = null) }
}

fun executeOperation(targetPath: String) {
    val request = _state.value.pickerRequest ?: return
    dismissPicker()

    // Validate space before starting
    viewModelScope.launch {
        val totalSize = request.items.sumOf { File(it.path).totalSize() }
        val targetStat = StatFs(targetPath)
        if (targetStat.availableBytes < totalSize) {
            _events.emit(FolderUiEvent.ShowToastRes(R.string.error_not_enough_space))
            return@launch
        }

        executeOperationInternal(request.items, targetPath, request.mode)
    }
}

private fun executeOperationInternal(
    items: List<FileItem>,
    targetPath: String,
    mode: OperationMode
) {
    operationJob = viewModelScope.launch {
        try {
            val sourcePaths = items.map { it.path }

            fileRepository.copyFiles(
                sources = items,
                targetDir = targetPath,
                deleteAfter = (mode == OperationMode.MOVE)
            ).collect { copyProgress ->
                _state.update {
                    it.copy(
                        operationProgress = OperationProgress(
                            mode = mode,
                            currentFile = copyProgress.currentFile,
                            copiedBytes = copyProgress.copiedBytes,
                            totalBytes = copyProgress.totalBytes,
                            isCancelling = it.operationProgress?.isCancelling ?: false
                        )
                    )
                }
            }

            // Success: notify MediaStore
            val copiedPaths = items.map { item ->
                "$targetPath/${File(item.path).name}"
            }
            MediaStoreUtil.scanFiles(context, copiedPaths)
            if (mode == OperationMode.MOVE) {
                MediaStoreUtil.notifyDeleted(context, sourcePaths)
            }

            // Silent close, refresh
            _state.update { it.copy(operationProgress = null) }
            loadFiles()

        } catch (e: CancellationException) {
            // Cancelled: keep completed files, silent close
            _state.update { it.copy(operationProgress = null) }
            loadFiles()

        } catch (e: Exception) {
            // Error: show toast
            _state.update { it.copy(operationProgress = null) }
            val errorRes = if (mode == OperationMode.MOVE) {
                R.string.error_move_failed
            } else {
                R.string.error_copy_failed
            }
            _events.emit(FolderUiEvent.ShowToastRes(errorRes))
            loadFiles()
        }
    }
}

fun cancelOperation() {
    _state.update { currentState ->
        currentState.copy(
            operationProgress = currentState.operationProgress?.copy(isCancelling = true)
        )
    }
    operationJob?.cancel()
}

// Update onAction to handle new actions:
fun onAction(action: FileAction) {
    when (action) {
        FileAction.MoveTo -> onMoveTo()
        FileAction.CopyTo -> onCopyTo()
        // ... other actions
    }
}
```

### File Size Helper

Add extension function if not already present:

```kotlin
// In FileRepository or a util file
private fun File.totalSize(): Long =
    if (isDirectory) listFiles()?.sumOf { it.totalSize() } ?: 0L else length()
```

### Cross-Storage Moves

When moving files between different storage devices (internal to SD card or vice versa):

- The operation is handled **transparently** — no warning or different UI
- Internally it's a copy + delete, but user doesn't need to know
- Progress dialog shows the same "Moving…" text
- If copy succeeds but delete fails, source files remain (safe failure mode)

## UI Components Detail

### DestinationPicker Composable

**Location:** `ui/screens/picker/DestinationPicker.kt`

```kotlin
@Composable
fun DestinationPicker(
    request: PickerRequest,
    sortMode: SortMode,
    showHidden: Boolean,
    onConfirm: (targetPath: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fileRepository = remember { FileRepository() }
    val storageRepository = remember { StorageRepository(context) }

    val viewModel: PickerViewModel = viewModel(
        factory = PickerViewModel.Factory(
            context = context,
            fileRepository = fileRepository,
            storageRepository = storageRepository,
            sourceItems = request.items,
            operationMode = request.mode,
            sortMode = sortMode,
            showHidden = showHidden
        )
    )

    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val storages by viewModel.storages.collectAsStateWithLifecycle()
    val showStorageSelector by viewModel.showStorageSelector.collectAsStateWithLifecycle()
    val validationError by viewModel.validationError.collectAsStateWithLifecycle()
    val isValidDestination by viewModel.isValidDestination.collectAsStateWithLifecycle()
    val showCreateFolderDialog by viewModel.showCreateFolderDialog.collectAsStateWithLifecycle()

    // Handle back button
    BackHandler {
        if (!viewModel.navigateUp()) {
            onCancel()
        }
    }

    Scaffold(
        topBar = {
            PickerTopBar(
                title = if (request.mode == OperationMode.MOVE) {
                    stringResource(R.string.picker_title_move)
                } else {
                    stringResource(R.string.picker_title_copy)
                },
                onBackClick = {
                    if (!viewModel.navigateUp()) {
                        onCancel()
                    }
                }
            )
        },
        bottomBar = {
            if (!showStorageSelector) {
                PickerBottomBar(
                    mode = request.mode,
                    isValidDestination = isValidDestination,
                    validationError = validationError,
                    onNewFolder = { viewModel.showCreateFolderDialog() },
                    onConfirm = {
                        currentPath?.let { onConfirm(it) }
                    }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Breadcrumbs (only when viewing folders)
            if (!showStorageSelector && currentPath != null) {
                Breadcrumbs(
                    currentPath = currentPath!!,
                    rootPath = storages.firstOrNull()?.path,
                    rootDisplayName = storages.firstOrNull()?.displayName,
                    onPathClick = { path -> viewModel.navigateToPath(path) }
                )
            }

            // Content
            if (showStorageSelector) {
                StorageSelectorContent(
                    storages = storages,
                    onStorageClick = { viewModel.navigateToStorage(it) }
                )
            } else {
                FolderPickerContent(
                    folders = folders,
                    onFolderClick = { viewModel.navigateToFolder(it) }
                )
            }
        }
    }

    // Create folder dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { viewModel.dismissCreateFolderDialog() },
            onConfirm = { name -> viewModel.createFolder(name) }
        )
    }
}
```

### PickerTopBar

```kotlin
@Composable
fun PickerTopBar(
    title: String,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_back)
                )
            }
        }
    )
}
```

### PickerBottomBar

```kotlin
@Composable
fun PickerBottomBar(
    mode: OperationMode,
    isValidDestination: Boolean,
    validationError: String?,
    onNewFolder: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // New folder button (secondary style)
                TextButton(
                    onClick = onNewFolder
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CreateNewFolder,
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(R.string.picker_new_folder),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Confirm button (primary style)
                Button(
                    onClick = onConfirm,
                    enabled = isValidDestination
                ) {
                    Text(
                        text = if (mode == OperationMode.MOVE) {
                            stringResource(R.string.picker_confirm_move)
                        } else {
                            stringResource(R.string.picker_confirm_copy)
                        }
                    )
                }
            }

            // Validation error message
            if (validationError != null) {
                Text(
                    text = validationError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
```

### StorageSelectorContent

```kotlin
@Composable
fun StorageSelectorContent(
    storages: List<StorageDevice>,
    onStorageClick: (StorageDevice) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = storages,
            key = { it.path }
        ) { storage ->
            StoragePickerItem(
                storage = storage,
                onClick = { onStorageClick(storage) }
            )
        }
    }
}

@Composable
fun StoragePickerItem(
    storage: StorageDevice,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text = storage.displayName)
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.storage_available,
                    storage.formattedAvailable
                )
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Storage,
                contentDescription = null
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
```

### FolderPickerContent

```kotlin
@Composable
fun FolderPickerContent(
    folders: List<FileItem>,
    onFolderClick: (FileItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = folders,
            key = { it.path }
        ) { folder ->
            FolderPickerItem(
                folder = folder,
                onClick = { onFolderClick(folder) }
            )
        }
    }
}

@Composable
fun FolderPickerItem(
    folder: FileItem,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = folder.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
```

### OperationProgressDialog

**Location:** `ui/components/OperationProgressDialog.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationProgressDialog(
    progress: OperationProgress,
    onCancel: () -> Unit
) {
    // Block back button
    BackHandler { /* Do nothing */ }

    BasicAlertDialog(onDismissRequest = {}) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Title
                Text(
                    text = if (progress.mode == OperationMode.MOVE) {
                        stringResource(R.string.progress_moving)
                    } else {
                        stringResource(R.string.progress_copying)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Progress bar
                LinearProgressIndicator(
                    progress = { progress.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Current file name
                Text(
                    text = progress.currentFile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Cancel button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onCancel,
                        enabled = !progress.isCancelling
                    ) {
                        Text(
                            text = if (progress.isCancelling) {
                                stringResource(R.string.progress_cancelling)
                            } else {
                                stringResource(R.string.progress_cancel)
                            }
                        )
                    }
                }
            }
        }
    }
}
```

## Error Handling

**All errors show a simple toast message.** No dialogs, no detailed explanations.

| Error               | Toast Message                 |
|---------------------|-------------------------------|
| Insufficient space  | "Not enough space"            |
| Permission denied   | "Move failed" / "Copy failed" |
| File locked/in use  | "Move failed" / "Copy failed" |
| Partial failure     | "Move failed" / "Copy failed" |
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
| `progress_cancelling`         | "Cancelling…"                      | Progress dialog button (disabled state)     |
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

## Existing Code to Reuse

| Component              | Location                        | Usage in Picker                   |
|------------------------|---------------------------------|-----------------------------------|
| `StorageDevice`        | `data/model/StorageDevice.kt`   | Storage selector data             |
| `StorageRepository`    | `data/repository/`              | Get available storages            |
| `FileRepository`       | `data/repository/`              | List files, create folders, copy  |
| `CopyProgress`         | `data/repository/FileRepository`| Map to OperationProgress          |
| `MediaStoreUtil`       | `util/MediaStoreUtil.kt`        | Notify after operations           |
| `Breadcrumbs`          | `ui/components/Breadcrumbs.kt`  | Path navigation                   |
| `CreateFolderDialog`   | `ui/components/`                | Create new folder in picker       |

## Checklist

### Phase 1: Cleanup

- [ ] Delete `ClipboardManager.kt`
- [ ] Delete `Clipboard.kt` (includes `ClipboardMode` enum)
- [ ] Delete `ClipboardTest.kt`
- [ ] Delete `ClipboardManagerTest.kt`
- [ ] Remove clipboard references from `FolderViewModel.kt`
- [ ] Remove clipboard-related tests from `FolderViewModelTest.kt`
- [ ] Update `FileAction` sealed interface (remove Cut/Copy/Paste, add MoveTo/CopyTo)
- [ ] Update `ActionBar.kt` to use new actions

### Phase 2: Data Layer

- [ ] Create `OperationMode.kt` enum in `data/model/`
- [ ] Create `OperationProgress.kt` data class in `data/model/`
- [ ] Create `PickerRequest.kt` data class in `data/model/` (or inline in FolderUiState)
- [ ] Add `pickerRequest` and `operationProgress` to `FolderUiState`

### Phase 3: Picker UI

- [ ] Create `ui/screens/picker/` package
- [ ] Create `PickerViewModel.kt` with Factory
- [ ] Create `DestinationPicker.kt` composable
- [ ] Create `PickerTopBar.kt` composable
- [ ] Create `PickerBottomBar.kt` composable
- [ ] Create `StorageSelectorContent.kt` composable
- [ ] Create `FolderPickerContent.kt` composable
- [ ] Add slide up/down animation with `AnimatedVisibility`
- [ ] Integrate `Breadcrumbs` component
- [ ] Integrate `CreateFolderDialog` with auto-navigation
- [ ] Implement destination validation with warning display

### Phase 4: Progress Dialog

- [ ] Create `OperationProgressDialog.kt` composable
- [ ] Map `CopyProgress` to `OperationProgress` in FolderViewModel
- [ ] Implement cancel button with `isCancelling` state
- [ ] Block back button during progress

### Phase 5: Integration

- [ ] Add `onMoveTo()` and `onCopyTo()` to FolderViewModel
- [ ] Add `executeOperation()` to FolderViewModel
- [ ] Add `cancelOperation()` to FolderViewModel
- [ ] Connect ActionBar MoveTo/CopyTo to show picker
- [ ] Render picker overlay in FolderScreen when `pickerRequest != null`
- [ ] Render progress dialog when `operationProgress != null`
- [ ] Handle completion (silent close, refresh)
- [ ] Handle errors (toast messages)
- [ ] Handle cancellation (keep completed files)
- [ ] Integrate MediaStore notifications after operations

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
- [ ] Unit tests for `FolderViewModel` operation methods
- [ ] UI tests for storage selector screen
- [ ] UI tests for picker folder navigation
- [ ] UI tests for create folder in picker
- [ ] UI tests for progress dialog
- [ ] Integration tests for full move/copy flow
