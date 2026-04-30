# File Explorer v2 - Migration Plan

This document describes the plan to rewrite the File Explorer Android app from scratch using modern
technologies while preserving all existing functionality.

The "old project" is located in: /home/max/Repositories/personal/fileexplorer-old/

---

## Table of Contents

1. [Tech Stack](#tech-stack)
2. [Project Structure](#project-structure)
3. [Existing Features Specification](#existing-features-specification)
4. [New Features](#new-features)
5. [Future Considerations](#future-considerations)
6. [Development Phases](#development-phases)
7. [Data Models](#data-models)
8. [Screen Specifications](#screen-specifications)
9. [Old-to-New File Mapping](#old-to-new-file-mapping)
10. [Notes](#notes)
11. [Resources](#resources)

---

## Tech Stack

| Aspect           | Old App                     | New App                           |
|------------------|-----------------------------|-----------------------------------|
| Language         | Java                        | Kotlin                            |
| Min SDK          | 16                          | 23                                |
| Target SDK       | 28                          | 36                                |
| UI Framework     | XML + ListView              | Jetpack Compose + LazyColumn      |
| Architecture     | Fragment-based              | MVVM (ViewModel + StateFlow)      |
| Async            | AsyncTask                   | Kotlin Coroutines                 |
| Navigation       | Manual Fragment stack       | Compose Navigation                |
| Settings Storage | N/A                         | DataStore Preferences             |
| Recent Files     | N/A                         | JSON file (kotlinx.serialization) |
| Firebase         | Crashlytics, Analytics, FCM | Same (see Firebase section)       |

### Dependencies

```kotlin
// build.gradle.kts (app)
dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.xx.xx"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.x")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.x")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.x")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.x")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.x")

    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.x")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.x.x"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Coil for image loading (replaces manual thumbnail loading)
    implementation("io.coil-kt:coil-compose:2.5.x")
}
```

---

## Project Structure

```
com.mauriciotogneri.fileexplorer/
├── FileExplorerApp.kt              # Application class
├── MainActivity.kt                  # Single activity, hosts Compose
│
├── data/
│   ├── model/
│   │   ├── FileItem.kt             # File/folder representation
│   │   ├── StorageDevice.kt        # Storage device info
│   │   ├── Clipboard.kt            # Cut/copy clipboard
│   │   └── RecentFile.kt           # Recent file entry
│   │
│   ├── repository/
│   │   ├── FileRepository.kt       # File operations
│   │   ├── StorageRepository.kt    # Storage detection
│   │   ├── RecentFilesRepository.kt# Recent files (JSON)
│   │   └── PreferencesRepository.kt# Settings (DataStore)
│   │
│   └── util/
│       ├── MimeTypeUtil.kt         # MIME type detection
│       ├── FileSizeFormatter.kt    # Size formatting (B, KB, MB, GB)
│       └── FileExtensions.kt       # Kotlin extensions for File
│
├── ui/
│   ├── theme/
│   │   ├── Theme.kt                # Material 3 theme with dark mode
│   │   ├── Color.kt                # Color definitions
│   │   └── Type.kt                 # Typography
│   │
│   ├── components/
│   │   ├── FileListItem.kt         # Single file/folder row
│   │   ├── StorageListItem.kt      # Storage device row
│   │   ├── ActionBar.kt            # Bottom action buttons
│   │   ├── Breadcrumb.kt           # Folder path display
│   │   ├── EmptyState.kt           # "No items" display
│   │   ├── ProgressDialog.kt       # Progress indicator
│   │   ├── RenameDialog.kt         # Rename input dialog
│   │   ├── CreateFolderDialog.kt   # Create folder dialog
│   │   └── DeleteConfirmDialog.kt  # Delete confirmation
│   │
│   ├── screens/
│   │   ├── storage/
│   │   │   ├── StorageScreen.kt    # Storage list screen
│   │   │   └── StorageViewModel.kt
│   │   │
│   │   ├── folder/
│   │   │   ├── FolderScreen.kt     # Folder browser screen
│   │   │   └── FolderViewModel.kt
│   │   │
│   │   ├── search/
│   │   │   ├── SearchScreen.kt     # Search screen (NEW)
│   │   │   └── SearchViewModel.kt
│   │   │
│   │   └── recent/
│   │       ├── RecentScreen.kt     # Recent files screen (NEW)
│   │       └── RecentViewModel.kt
│   │
│   └── navigation/
│       └── NavGraph.kt             # Navigation setup
│
└── util/
    ├── CrashReporter.kt            # Firebase Crashlytics wrapper
    └── IntentUtil.kt               # Intent helpers for open/share
```

---

## Existing Features Specification

### 1. Storage Detection

**Current Implementation:** `MainActivity.java:112-156`

Detects all available storage devices (internal + external SD cards).

**Behavior:**

- Uses `ContextCompat.getExternalFilesDirs(context, null)` to get all storage paths
- Strips the app-specific path suffix (`/Android/data/{package}/files`) to get root path
- Validates each path using `StatFs` to ensure it's accessible
- If only one storage exists, navigates directly to folder browser
- If multiple storages exist, shows storage selection screen

**Display per storage:**

- Storage path (e.g., `/storage/emulated/0`, `/storage/sdcard1`)
- Total space (formatted: "Total: 64 GB")
- Available space (formatted: "Available: 32 GB")

**New Implementation:**

```kotlin
// StorageRepository.kt
class StorageRepository(private val context: Context) {
    fun getStorages(): List<StorageDevice> {
        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
        val basePath = "/Android/data/${context.packageName}/files"

        return externalDirs
            .filterNotNull()
            .mapNotNull { file ->
                val path = file.absolutePath.replace(basePath, "")
                if (isValidPath(path)) {
                    val stat = StatFs(path)
                    StorageDevice(
                        path = path,
                        totalBytes = stat.totalBytes,
                        availableBytes = stat.availableBytes
                    )
                } else null
            }
    }

    private fun isValidPath(path: String): Boolean {
        return try {
            StatFs(path).blockCountLong
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

---

### 2. Folder Browsing

**Current Implementation:** `FolderFragment.java:175-215`

Lists files and folders in the current directory.

**Behavior:**

- Lists all files/folders using `File.listFiles()`
- Sorts items: folders first, then files, alphabetically (case-insensitive)
- Displays in a scrollable list with pull-to-refresh
- Shows "No items" when folder is empty

**File item display:**

- **Icon:** Different icon based on type (folder, image thumbnail, PDF, audio, video, generic file)
- **Name:** File/folder name
- **Size/Count:** For files: formatted size (e.g., "2.5 MB"). For folders: item count (e.g., "5
  items")
- **Extension badge:** For non-media files, shows extension in a badge (e.g., "TXT", "DOC")

**Sorting Logic:**

```kotlin
files.sortedWith(
    compareBy(
        { !it.isDirectory },  // Folders first
        { it.name.lowercase() }  // Then alphabetically
    )
)
```

**Sort Options:**

Users can choose different sort modes via a menu in the toolbar:

```kotlin
enum class SortMode {
    NAME_ASC,      // A-Z
    NAME_DESC,     // Z-A
    SIZE_ASC,      // Smallest first
    SIZE_DESC,     // Largest first
    DATE_ASC,      // Oldest first
    DATE_DESC      // Newest first
}

fun sortFiles(files: List<FileItem>, sortMode: SortMode): List<FileItem> {
    // Always keep folders first, then apply sort within each group
    val folders = files.filter { it.isDirectory }
    val regularFiles = files.filter { !it.isDirectory }

    val comparator: Comparator<FileItem> = when (sortMode) {
        SortMode.NAME_ASC -> compareBy { it.name.lowercase() }
        SortMode.NAME_DESC -> compareByDescending { it.name.lowercase() }
        SortMode.SIZE_ASC -> compareBy { it.size }
        SortMode.SIZE_DESC -> compareByDescending { it.size }
        SortMode.DATE_ASC -> compareBy { it.lastModified }
        SortMode.DATE_DESC -> compareByDescending { it.lastModified }
    }

    return folders.sortedWith(comparator) + regularFiles.sortedWith(comparator)
}
```

---

### 3. File Type Detection

**Current Implementation:** `FileInfo.java:261-353`

Detects file type for icon display and MIME type for opening.

**MIME Type Detection (two-stage fallback):**

1. `URLConnection.guessContentTypeFromName(path)`
2. `MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)`
3. Fallback: `*/*`

**Type Checks:**

- `isImage()`: MIME type starts with `image/`
- `isPdf()`: MIME type is `application/pdf`
- `isAudio()`: MIME type starts with `audio/`
- `isVideo()`: MIME type starts with `video/`
- `isDirectory()`: `File.isDirectory()`

**Extension Extraction:**

- Gets substring after last `.`
- Only displays if 4 characters or less
- Converts to uppercase

```kotlin
// MimeTypeUtil.kt
object MimeTypeUtil {
    fun getMimeType(file: File): String {
        return URLConnection.guessContentTypeFromName(file.absolutePath)
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension)
            ?: "*/*"
    }

    fun isImage(mimeType: String) = mimeType.startsWith("image/")
    fun isPdf(mimeType: String) = mimeType == "application/pdf"
    fun isAudio(mimeType: String) = mimeType.startsWith("audio/")
    fun isVideo(mimeType: String) = mimeType.startsWith("video/")
}
```

---

### 4. Image Thumbnails

**Current Implementation:** `ThumbnailLoader.java`, `FileInfo.java:417-469`

Loads and caches image thumbnails for preview.

**Behavior:**

- Uses thread pool (10 threads) for background loading
- Downsamples images to max 24dp to prevent memory issues
- Uses `SoftReference` for bitmap caching (GC-friendly)
- Fades in thumbnail with animation when loaded

**Downsampling Algorithm:**

```kotlin
fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        while ((halfHeight / inSampleSize) > reqHeight &&
            (halfWidth / inSampleSize) > reqWidth
        ) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
```

**New Implementation:** Use Coil library for simpler image loading:

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(file)
        .size(Size(48.dp.toPx(), 48.dp.toPx()))
        .crossfade(true)
        .build(),
    contentDescription = null,
    modifier = Modifier.size(24.dp)
)
```

---

### 5. Selection Mode

**Current Implementation:** `FolderAdapter.java:119-177`

Allows selecting multiple files for batch operations.

**Behavior:**

- **Enter selection mode:** Long-press on any item
- **Toggle selection:** Tap on items while in selection mode
- **Exit selection mode:** Press back or tap empty area
- **Visual feedback:** Selected items have gray background (`R.color.gray4`)
- **Select all:** Button to select all items
- **Tracking:** Maintains count of selected items (`itemsSelected`)

**State Management:**

```kotlin
data class FolderUiState(
    val files: List<FileItem> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),  // Set of file paths
    val isSelectionMode: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

// In ViewModel
fun toggleSelection(file: FileItem) {
    val newSelected = if (file.path in _state.value.selectedFiles) {
        _state.value.selectedFiles - file.path
    } else {
        _state.value.selectedFiles + file.path
    }
    _state.update {
        it.copy(
            selectedFiles = newSelected,
            isSelectionMode = newSelected.isNotEmpty()
        )
    }
}

fun selectAll() {
    _state.update {
        it.copy(
            selectedFiles = it.files.map { f -> f.path }.toSet()
        )
    }
}

fun clearSelection() {
    _state.update {
        it.copy(
            selectedFiles = emptySet(),
            isSelectionMode = false
        )
    }
}
```

---

### 6. Clipboard (Cut/Copy/Paste)

**Current Implementation:** `Clipboard.java`

In-memory clipboard for file operations.

**Behavior:**

- **Cut:** Stores files + marks for deletion after paste
- **Copy:** Stores files for copying
- **Paste:** Copies files to target, deletes originals if cut mode
- **Validation:** Prevents pasting into parent folder (circular reference)
- **Validation:** Checks if source files still exist before paste
- **Clear:** Clipboard clears after paste

**Data Model:**

```kotlin
data class Clipboard(
    val items: List<FileItem> = emptyList(),
    val mode: ClipboardMode = ClipboardMode.NONE,
    val sourceParent: String? = null
) {
    enum class ClipboardMode { NONE, CUT, COPY }

    fun canPasteInto(targetPath: String): Boolean {
        if (items.isEmpty()) return false
        if (!items.any { it.exists() }) return false
        // Prevent pasting into source parent
        if (sourceParent == targetPath) return false
        // Prevent pasting into subdirectory of selected item
        return items.none { targetPath.startsWith(it.path) }
    }
}

// ClipboardManager.kt - Singleton to persist clipboard across screens
object ClipboardManager {
    private val _clipboard = MutableStateFlow(Clipboard())
    val clipboard: StateFlow<Clipboard> = _clipboard.asStateFlow()

    fun cut(items: List<FileItem>, sourceParent: String) {
        _clipboard.value = Clipboard(items, Clipboard.ClipboardMode.CUT, sourceParent)
    }

    fun copy(items: List<FileItem>, sourceParent: String) {
        _clipboard.value = Clipboard(items, Clipboard.ClipboardMode.COPY, sourceParent)
    }

    fun clear() {
        _clipboard.value = Clipboard()
    }
}
```

---

### 7. File Operations

#### 7.1 Open File

**Current Implementation:** `FolderFragment.java:239-285`

Opens file with appropriate external app.

**Behavior:**

1. Get MIME type for the file
2. Create `ACTION_VIEW` intent with data and type
3. Add `FLAG_GRANT_READ_URI_PERMISSION` for FileProvider URIs
4. Check if any app can handle the intent (`queryIntentActivities`)
5. If resolvable, start activity
6. If not resolvable, try without explicit MIME type
7. Show error toast if still not openable

**URI Generation (for Android 7+):**

```kotlin
fun getFileUri(context: Context, file: File): Uri {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    } else {
        Uri.fromFile(file)
    }
}
```

**Intent Creation:**

```kotlin
fun openFile(context: Context, file: File) {
    val uri = getFileUri(context, file)
    val mimeType = MimeTypeUtil.getMimeType(file)

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        // Fallback without MIME type
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(fallbackIntent)
        } catch (e: Exception) {
            // Show error
        }
    }
}
```

#### 7.2 Copy/Move Files

**Current Implementation:** `FileInfo.java:86-122`

Recursive copy with optional delete (for move).

**Duplicate File Handling:**

When a file with the same name exists at the target, append a number suffix:

```kotlin
fun getUniqueTargetFile(targetDir: File, name: String): File {
    var targetFile = File(targetDir, name)
    if (!targetFile.exists()) return targetFile

    val baseName = name.substringBeforeLast(".", name)
    val extension = name.substringAfterLast(".", "").let { if (it == name) "" else ".$it" }

    var counter = 1
    while (targetFile.exists()) {
        targetFile = File(targetDir, "$baseName ($counter)$extension")
        counter++
    }
    return targetFile
}
```

**Progress Reporting:**

For large operations, report progress via Flow:

```kotlin
data class CopyProgress(
    val currentFile: String,
    val copiedFiles: Int,
    val totalFiles: Int,
    val copiedBytes: Long,
    val totalBytes: Long
)

fun copyFilesWithProgress(
    sources: List<File>,
    targetDir: File,
    deleteAfter: Boolean
): Flow<CopyProgress> = flow {
    val totalBytes = sources.sumOf { it.totalSize() }
    val totalFiles = sources.sumOf { it.totalFileCount() }
    var copiedBytes = 0L
    var copiedFiles = 0

    suspend fun copyRecursive(source: File, target: File) {
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles()?.forEach { child ->
                copyRecursive(child, File(target, child.name))
            }
            if (deleteAfter) source.delete()
        } else {
            val uniqueTarget = getUniqueTargetFile(target.parentFile, target.name)
            source.inputStream().use { input ->
                uniqueTarget.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } >= 0) {
                        output.write(buffer, 0, bytes)
                        copiedBytes += bytes
                        emit(
                            CopyProgress(
                                source.name,
                                copiedFiles,
                                totalFiles,
                                copiedBytes,
                                totalBytes
                            )
                        )
                    }
                }
            }
            copiedFiles++
            if (deleteAfter) source.delete()
        }
    }

    sources.forEach { source ->
        copyRecursive(source, File(targetDir, source.name))
    }
}.flowOn(Dispatchers.IO)

// Extension functions
fun File.totalSize(): Long =
    if (isDirectory) listFiles()?.sumOf { it.totalSize() } ?: 0L else length()
fun File.totalFileCount(): Int =
    if (isDirectory) listFiles()?.sumOf { it.totalFileCount() } ?: 0 else 1
```

**Operation Cancellation:**

Use coroutine Job for cancellation support:

```kotlin
class FolderViewModel : ViewModel() {
    private var copyJob: Job? = null

    fun paste(targetDir: String) {
        copyJob = viewModelScope.launch {
            fileRepository.copyFilesWithProgress(...)
            .collect { progress ->
            _state.update { it.copy(copyProgress = progress) }
        }
        }
    }

    fun cancelOperation() {
        copyJob?.cancel()
        copyJob = null
        _state.update { it.copy(copyProgress = null) }
    }
}
```

**Simple Algorithm (for reference):**

```kotlin
suspend fun copyFile(source: File, targetDir: File, deleteAfter: Boolean): Boolean {
    return withContext(Dispatchers.IO) {
        if (source.isDirectory) {
            val newDir = File(targetDir, source.name)
            if (!newDir.exists()) newDir.mkdirs()

            var allCopied = true
            source.listFiles()?.forEach { child ->
                allCopied = allCopied && copyFile(child, newDir, deleteAfter)
            }

            if (deleteAfter && allCopied) {
                source.delete()
            }
            allCopied
        } else {
            val targetFile = getUniqueTargetFile(targetDir, source.name)
            try {
                source.inputStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                if (deleteAfter) source.delete()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
```

#### 7.3 Delete Files

**Current Implementation:** `FileInfo.java:175-190`

Recursive delete for files and folders.

**Behavior:**

- Shows confirmation dialog before deleting
- Shows progress dialog during deletion
- Recursively deletes folder contents first, then folder
- Reports partial failure if some files couldn't be deleted

```kotlin
suspend fun deleteFile(file: File): Boolean {
    return withContext(Dispatchers.IO) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteFile(child)
            }
        }
        file.delete()
    }
}
```

#### 7.4 Rename

**Current Implementation:** `FileInfo.java:79-84`, `Dialogs.java:41-114`

Renames a single file or folder.

**Behavior:**

- Only available when exactly 1 item is selected
- Shows dialog with current name pre-filled
- Selects filename without extension for easy editing
- Validates: name not empty, target doesn't already exist
- Keyboard action "Done" triggers rename

**Dialog UX Details:**

- Pre-fills with current filename
- If file has extension, selects only the name part (before last `.`)
- If no extension, selects all
- Disable "Rename" button if input is empty
- Auto-show keyboard when dialog opens

```kotlin
@Composable
fun RenameDialog(
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    val focusRequester = remember { FocusRequester() }

    // Calculate initial selection (exclude extension)
    val dotIndex = currentName.lastIndexOf('.')
    val selectionEnd = if (dotIndex > 0) dotIndex else currentName.length

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.isNotBlank() && name != currentName
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester)
            )
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
```

#### 7.5 Create Folder

**Current Implementation:** `FolderFragment.java:456-474`, `Dialogs.java:126-187`

Creates a new folder in current directory.

**Behavior:**

- Shows dialog with empty text field
- "Create" button disabled until name entered
- Creates folder using `File.mkdir()`
- Refreshes folder view on success
- Shows error toast on failure

#### 7.6 Share Files

**Current Implementation:** `FolderFragment.java:362-449`

Shares files with other apps.

**Single File:**

```kotlin
fun shareSingle(context: Context, file: File) {
    val uri = getFileUri(context, file)
    val mimeType = MimeTypeUtil.getMimeType(file)

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Share file"))
}
```

**Multiple Files:**

```kotlin
fun shareMultiple(context: Context, files: List<File>) {
    val uris = ArrayList(files.map { getFileUri(context, it) })

    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Share files"))
}
```

**Note:** Share only works for files, not folders. The current implementation flattens selected
folders to get all files within (`FileInfo.files()`).

---

### 8. Button Bar (Action Buttons)

**Current Implementation:** `ButtonBar.java`

Floating action buttons that appear based on context.

**Buttons:**

1. **Cut** - Scissors icon
2. **Copy** - Copy icon
3. **Paste** - Clipboard icon
4. **Select All** - Checkbox icon
5. **Rename** - Pencil icon
6. **Share** - Share icon
7. **Delete** - Trash icon
8. **Create** - Plus/folder icon

**Visibility Logic:**

| Button     | Visible When                                                                               |
|------------|--------------------------------------------------------------------------------------------|
| Cut        | Items selected                                                                             |
| Copy       | Items selected                                                                             |
| Paste      | No selection AND clipboard not empty AND clipboard items exist AND not pasting into parent |
| Select All | Items selected AND not all items selected                                                  |
| Rename     | Exactly 1 item selected                                                                    |
| Share      | Items selected AND selection contains files (not only folders)                             |
| Delete     | Items selected                                                                             |
| Create     | No items selected (while in folder view)                                                   |

**Implementation:**

```kotlin
@Composable
fun ActionBar(
    state: FolderUiState,
    clipboard: Clipboard,
    currentPath: String,
    onAction: (FileAction) -> Unit
) {
    val selectedCount = state.selectedFiles.size
    val hasSelection = selectedCount > 0
    val allSelected = selectedCount == state.files.size
    val canPaste = clipboard.canPasteInto(currentPath)
    val hasFiles = state.selectedFiles.any { path ->
        state.files.find { it.path == path }?.isDirectory == false
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        if (hasSelection) {
            ActionButton(Icons.Default.ContentCut, "Cut") { onAction(FileAction.Cut) }
            ActionButton(Icons.Default.ContentCopy, "Copy") { onAction(FileAction.Copy) }
            if (!allSelected) {
                ActionButton(
                    Icons.Default.SelectAll,
                    "Select All"
                ) { onAction(FileAction.SelectAll) }
            }
            if (selectedCount == 1) {
                ActionButton(Icons.Default.Edit, "Rename") { onAction(FileAction.Rename) }
            }
            if (hasFiles) {
                ActionButton(Icons.Default.Share, "Share") { onAction(FileAction.Share) }
            }
            ActionButton(Icons.Default.Delete, "Delete") { onAction(FileAction.Delete) }
        } else {
            if (canPaste) {
                ActionButton(Icons.Default.ContentPaste, "Paste") { onAction(FileAction.Paste) }
            }
            ActionButton(
                Icons.Default.CreateNewFolder,
                "Create"
            ) { onAction(FileAction.CreateFolder) }
        }
    }
}
```

---

### 9. Toolbar / Breadcrumb

**Current Implementation:** `ToolBar.java`

Displays current folder path in toolbar.

**Behavior:**

- Shows full absolute path (e.g., `/storage/emulated/0/Documents`)
- Updates when navigating between folders
- Shows app name when on storage selection screen

**New Implementation:** Could enhance with clickable breadcrumbs:

```kotlin
@Composable
fun Breadcrumb(path: String, onNavigate: (String) -> Unit) {
    val parts = path.split("/").filter { it.isNotEmpty() }

    LazyRow {
        items(parts.size) { index ->
            val partPath = "/" + parts.subList(0, index + 1).joinToString("/")
            Text(
                text = parts[index],
                modifier = Modifier.clickable { onNavigate(partPath) }
            )
            if (index < parts.size - 1) {
                Text(" / ")
            }
        }
    }
}
```

---

### 10. Navigation

**Current Implementation:** `MainActivity.java:175-266`

Stack-based navigation between folders.

**Behavior:**

- Maintains stack of folder fragments
- Push new fragment when entering folder
- Pop fragment on back press
- Custom slide animation (enter from right, exit to right)
- Back press in selection mode clears selection instead of navigating back
- If at root and back pressed, finish activity (or return to storage list)

**Back Press Handling:**

```kotlin
// In FolderScreen
BackHandler(enabled = state.isSelectionMode) {
    viewModel.clearSelection()
}

// Navigation handles normal back automatically
```

---

### 11. Pull-to-Refresh

**Current Implementation:** `FolderFragment.java:84-88`

Refreshes folder contents on swipe down.

**Behavior:**

- Uses SwipeRefreshLayout
- Blue accent color for refresh indicator
- Refreshes file list on release
- Immediately stops refreshing after file list updates

```kotlin
@Composable
fun FolderScreen(
    viewModel: FolderViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Material 3 Pull-to-Refresh (modern API)
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.refresh() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.files, key = { it.path }) { file ->
                FileListItem(file = file, ...)
            }
        }
    }
}
```

**Note:** `PullToRefreshBox` is available in Material 3 1.3.0+. For older versions, use:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(...) {
    val pullRefreshState = rememberPullToRefreshState()

    Box(Modifier.nestedScroll(pullRefreshState.nestedScrollConnection)) {
        LazyColumn { ... }

        PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.refresh()
            pullRefreshState.endRefresh()
        }
    }
}
```

---

### 12. Permissions

**Current Implementation:** `MainActivity.java:76-110`

Runtime permissions for storage access using the "Native Path" with `MANAGE_EXTERNAL_STORAGE`.

**Two Permission Eras:**

| Android Version  | Permission Type                      | Check Method                             |
|------------------|--------------------------------------|------------------------------------------|
| 6-10 (API 23-29) | Runtime Permission Dialog            | `ContextCompat.checkSelfPermission()`    |
| 11+ (API 30+)    | Special App Access (Settings Toggle) | `Environment.isExternalStorageManager()` |

**Behavior:**

- On launch, check Android version and request appropriate permission
- Android 6-10: Show standard runtime permission dialog
- Android 11+: Redirect to system settings for "All Files Access" toggle
- If granted, proceed to storage detection
- If denied, show explanation and prompt again (or close app)

**Manifest Configuration:**

```xml
<!-- AndroidManifest.xml -->
<manifest ...><!-- Legacy permissions for Android 6 to 10 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
android:maxSdkVersion="29" /><uses-permission
android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />

    <!-- "All Files Access" for Android 11+ -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

<application
android:requestLegacyExternalStorage="true" ...><!-- The flag above helps Android 10 devices specifically -->
    </application></manifest>
```

**Permission Logic:**

```kotlin
// In MainActivity or a PermissionHelper
fun checkAndRequestStoragePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // --- Android 11+ ---
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${packageName}")
            startActivity(intent)
        }
    } else {
        // --- Android 6 to 10 ---
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                PERMISSION_REQUEST_CODE
            )
        }
    }
}

fun hasStoragePermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
    }
}
```

**Known Limitations (Even with All Files Access):**

- `/Android/data` and `/Android/obb`: Cannot access on Android 13+ (app private data)
- `/data/user/0/...`: Requires root access (internal app storage)
- Play Store: Requires manual review and justification as a file manager app

**File API:**

Once permissions are granted, use the standard `java.io.File` API throughout:

```kotlin
val root = Environment.getExternalStorageDirectory() // /storage/emulated/0/
val files = root.listFiles()
```

---

### 13. Size Formatting

**Current Implementation:** `SpaceFormatter.java` (not shown but referenced)

Formats byte sizes to human-readable strings.

**Implementation:**

```kotlin
object FileSizeFormatter {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        val size = bytes / 1024.0.pow(digitGroups.toDouble())

        return DecimalFormat("#,##0.#").format(size) + " " + units[digitGroups]
    }
}
```

---

### 14. Localization

**Current Strings:**

| Key                 | English                |
|---------------------|------------------------|
| app_name            | File Explorer          |
| dialog.delete       | Delete                 |
| dialog.rename       | Rename                 |
| dialog.create       | Create                 |
| dialog.cancel       | Cancel                 |
| list.empty          | No items               |
| itemAmount (plural) | %d items / 1 item      |
| space.total         | Total                  |
| space.available     | Available              |
| rename.error        | Error renaming         |
| open.unable         | Cannot open file       |
| clipboard.cut       | Moving...              |
| clipboard.copy      | Copying...             |
| create.error        | Error creating folder  |
| delete.confirm      | Delete selected items? |
| delete.deleting     | Deleting...            |
| delete.error        | Error deleting         |
| shareFile.title     | Share file             |
| shareFile.unable    | Error sharing file     |
| shareFiles.unable   | Error sharing files    |

**Existing Translations:** French, Spanish, Portuguese, Greek

Migrate all string resources to the new project's `res/values/strings.xml` and corresponding locale
folders.

---

### 15. FileProvider Configuration

**Current Implementation:** Uses custom `LegacyCompatFileProvider`

**Required in new project:**

```xml
<!-- AndroidManifest.xml -->
<provider android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider" android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/provider_paths" />
</provider>

    <!-- res/xml/provider_paths.xml -->
<paths>
<external-path name="external" path="." />
<root-path name="root" path="/" />
</paths>
```

---

### 16. Firebase Integration

**Crashlytics:**

```kotlin
// CrashReporter.kt
object CrashReporter {
    fun report(e: Throwable) {
        Firebase.crashlytics.recordException(e)
    }

    fun log(message: String) {
        Firebase.crashlytics.log(message)
    }
}
```

**Analytics:**

```kotlin
// Track screen views, file operations, etc.
Firebase.analytics.logEvent("file_opened") {
    param("mime_type", mimeType)
}
```

**Firebase Cloud Messaging (FCM):**

FCM is included for potential future features. Current/planned uses:

- **App update notifications:** Notify users when a critical update is available
- **Feature announcements:** Announce new features to users
- **Remote configuration:** Could be combined with Firebase Remote Config

If FCM is not needed immediately, it can be removed from the initial implementation and added later.
The dependency is included to maintain parity with the old app.

```kotlin
// MyFirebaseMessagingService.kt (optional - implement when needed)
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Handle FCM messages
        remoteMessage.notification?.let { notification ->
            showNotification(notification.title, notification.body)
        }
    }

    override fun onNewToken(token: String) {
        // Send token to your server if needed
    }
}
```

---

### 17. Error Handling Strategy

Unified approach to handling and displaying errors throughout the app.

**Error Model:**

```kotlin
sealed class FileOperationResult<out T> {
    data class Success<T>(val data: T) : FileOperationResult<T>()
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val errorType: ErrorType = ErrorType.GENERIC
    ) : FileOperationResult<Nothing>()
}

enum class ErrorType {
    GENERIC,
    PERMISSION_DENIED,
    FILE_NOT_FOUND,
    STORAGE_FULL,
    NAME_CONFLICT,
    OPERATION_CANCELLED
}
```

**UI Error Display:**

```kotlin
@Composable
fun ErrorSnackbar(
    error: String?,
    onDismiss: () -> Unit
) {
    error?.let {
        Snackbar(
            action = {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        ) {
            Text(it)
        }
    }
}

// In ViewModel
fun handleError(result: FileOperationResult.Error) {
    val message = when (result.errorType) {
        ErrorType.PERMISSION_DENIED -> "Permission denied"
        ErrorType.FILE_NOT_FOUND -> "File no longer exists"
        ErrorType.STORAGE_FULL -> "Not enough storage space"
        ErrorType.NAME_CONFLICT -> "A file with this name already exists"
        ErrorType.OPERATION_CANCELLED -> "Operation cancelled"
        ErrorType.GENERIC -> result.message
    }
    _state.update { it.copy(error = message) }
    CrashReporter.report(result.cause ?: Exception(message))
}
```

---

### 18. Intent Handling

Handle intents from other apps (file picker mode, opening files via content URIs).

**Manifest Configuration:**

```xml

<activity android:name=".MainActivity" ...><!-- Standard launcher -->
<intent-filter>
<action android:name="android.intent.action.MAIN" />
<category android:name="android.intent.category.LAUNCHER" />
</intent-filter>

    <!-- File picker mode -->
<intent-filter>
<action android:name="android.intent.action.GET_CONTENT" />
<category android:name="android.intent.category.DEFAULT" />
<category android:name="android.intent.category.OPENABLE" />
<data android:mimeType="*/*" />
</intent-filter>

    <!-- Open folder -->
<intent-filter>
<action android:name="android.intent.action.VIEW" />
<category android:name="android.intent.category.DEFAULT" />
<data android:mimeType="resource/folder" />
</intent-filter></activity>
```

**Intent Processing:**

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startMode = when (intent?.action) {
            Intent.ACTION_GET_CONTENT -> StartMode.Picker(intent.type ?: "*/*")
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    StartMode.OpenPath(uri.path ?: "/")
                } ?: StartMode.Normal
            }
            else -> StartMode.Normal
        }

        setContent {
            FileExplorerApp(startMode = startMode)
        }
    }
}

sealed class StartMode {
    object Normal : StartMode()
    data class Picker(val mimeType: String) : StartMode()
    data class OpenPath(val path: String) : StartMode()
}
```

**Picker Mode Behavior:**

In picker mode, tapping a file returns it to the calling app instead of opening it:

```kotlin
fun onFileSelected(file: FileItem, startMode: StartMode) {
    when (startMode) {
        is StartMode.Picker -> {
            val uri =
                FileProvider.getUriForFile(context, "${packageName}.provider", File(file.path))
            val result = Intent().apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }
        else -> openFile(file)
    }
}
```

---

### 19. Hidden Files Toggle

Allow users to show or hide dotfiles (files starting with `.`).

**Preference:**

```kotlin
// PreferencesRepository.kt
val showHiddenFiles: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[SHOW_HIDDEN_KEY] ?: false
}

suspend fun setShowHiddenFiles(show: Boolean) {
    dataStore.edit { prefs ->
        prefs[SHOW_HIDDEN_KEY] = show
    }
}

companion object {
    private val SHOW_HIDDEN_KEY = booleanPreferencesKey("show_hidden_files")
}
```

**File Filtering:**

```kotlin
// FileRepository.kt
fun listFiles(path: String, showHidden: Boolean): List<FileItem> {
    return File(path).listFiles()
        ?.filter { showHidden || !it.name.startsWith(".") }
        ?.map { FileItem.from(it) }
        ?.let { sortFiles(it, sortMode) }
        ?: emptyList()
}
```

**UI Toggle:**

Add to toolbar overflow menu or settings screen:

```kotlin
DropdownMenuItem(
    text = { Text(if (showHidden) "Hide hidden files" else "Show hidden files") },
    onClick = {
        viewModel.toggleHiddenFiles()
        expanded = false
    },
    leadingIcon = {
        Icon(
            if (showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = null
        )
    }
)
```

---

### 20. MediaStore Notification

After file operations, notify MediaStore so changes appear in Gallery, Music, and other apps.

```kotlin
// MediaStoreUtil.kt
object MediaStoreUtil {
    fun scanFile(context: Context, file: File) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null
        ) { path, uri ->
            // Scan completed
        }
    }

    fun scanFiles(context: Context, files: List<File>) {
        MediaScannerConnection.scanFile(
            context,
            files.map { it.absolutePath }.toTypedArray(),
            null,
            null
        )
    }

    // Call after delete to remove from MediaStore
    fun notifyDeleted(context: Context, path: String) {
        context.contentResolver.delete(
            MediaStore.Files.getContentUri("external"),
            "${MediaStore.Files.FileColumns.DATA}=?",
            arrayOf(path)
        )
    }
}
```

**Usage:**

```kotlin
// After paste operation
MediaStoreUtil.scanFiles(context, copiedFiles)

// After delete operation
deletedFiles.forEach { MediaStoreUtil.notifyDeleted(context, it.path) }

// After rename
MediaStoreUtil.notifyDeleted(context, oldPath)
MediaStoreUtil.scanFile(context, newFile)
```

---

## New Features

### 1. Search

**Description:** Search for files by name within current folder or recursively.

**UI:**

- Search icon in top bar
- Opens search screen with text field
- Results displayed as file list
- Tap result to navigate to containing folder

**Implementation:**

```kotlin
class SearchViewModel(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<FileItem>>(emptyList())
    private val _isSearching = MutableStateFlow(false)

    fun search(query: String, rootPath: String, recursive: Boolean = true) {
        viewModelScope.launch {
            _isSearching.value = true
            _results.value = fileRepository.searchFiles(rootPath, query, recursive)
            _isSearching.value = false
        }
    }
}

// FileRepository
suspend fun searchFiles(
    rootPath: String,
    query: String,
    recursive: Boolean
): List<FileItem> = withContext(Dispatchers.IO) {
    val root = File(rootPath)
    val results = mutableListOf<FileItem>()

    fun searchIn(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.name.contains(query, ignoreCase = true)) {
                results.add(FileItem(file))
            }
            if (recursive && file.isDirectory) {
                searchIn(file)
            }
        }
    }

    searchIn(root)
    results.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}
```

---

### 2. Recent Files

**Description:** Track and display recently opened/modified files.

**Storage:** JSON file in app's private storage

**Data Model:**

```kotlin
@Serializable
data class RecentFile(
    val path: String,
    val name: String,
    val accessedAt: Long,  // Epoch millis
    val mimeType: String
)

@Serializable
data class RecentFilesData(
    val files: List<RecentFile> = emptyList()
)
```

**Repository:**

```kotlin
class RecentFilesRepository(private val context: Context) {
    private val file = File(context.filesDir, "recent_files.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val maxRecentFiles = 50

    suspend fun getRecentFiles(): List<RecentFile> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<RecentFilesData>(file.readText()).files
                .filter { File(it.path).exists() }  // Filter deleted files
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addRecentFile(fileItem: FileItem) = withContext(Dispatchers.IO) {
        val current = getRecentFiles().toMutableList()
        current.removeAll { it.path == fileItem.path }  // Remove duplicates
        current.add(
            0, RecentFile(
                path = fileItem.path,
                name = fileItem.name,
                accessedAt = System.currentTimeMillis(),
                mimeType = fileItem.mimeType
            )
        )

        val data = RecentFilesData(current.take(maxRecentFiles))
        file.writeText(json.encodeToString(data))
    }

    suspend fun clearRecentFiles() = withContext(Dispatchers.IO) {
        file.delete()
    }
}
```

**UI:**

- Bottom navigation or drawer item for "Recent"
- List of recently accessed files with timestamp
- Tap to open file
- Long press to remove from recent
- Clear all button

---

### 3. Dark Mode

**Description:** Support system dark mode and manual toggle.

**DataStore Setup:**

```kotlin
// DataStoreExtensions.kt
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
```

**Implementation:**

```kotlin
// PreferencesRepository.kt
class PreferencesRepository(context: Context) {
    private val dataStore = context.dataStore

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[THEME_KEY] ?: ThemeMode.SYSTEM.name)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[THEME_KEY] = mode.name
        }
    }

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_mode")
    }
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}
```

**Theme Setup:**

```kotlin
// Theme.kt
@Composable
fun FileExplorerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        typography = Typography,
        content = content
    )
}
```

**Settings Screen:**

- Toggle or radio buttons for Light/Dark/System
- Persisted using DataStore

---

## Future Considerations

Features to potentially add in future versions:

### 1. File Properties Dialog

Display detailed file information:

```kotlin
@Composable
fun FilePropertiesDialog(file: FileItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Properties") },
        text = {
            Column {
                PropertyRow("Name", file.name)
                PropertyRow("Path", file.path)
                PropertyRow("Size", file.formattedSize)
                PropertyRow("Type", file.mimeType)
                PropertyRow("Modified", formatDate(file.lastModified))
                if (file.isDirectory) {
                    PropertyRow("Contents", "${file.childCount} items")
                }
            }
        }
    )
}
```

### 2. Grid View

Alternative to list view for visual browsing:

```kotlin
enum class ViewMode { LIST, GRID }

@Composable
fun FileGrid(files: List<FileItem>, ...) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp)
    ) {
        items(files, key = { it.path }) { file ->
            FileGridItem(file = file, ...)
        }
    }
}
```

### 3. Bookmarks / Favorites

Quick access to favorite folders:

```kotlin
@Serializable
data class Bookmark(
    val path: String,
    val name: String,
    val addedAt: Long
)

class BookmarksRepository(context: Context) {
    private val file = File(context.filesDir, "bookmarks.json")

    suspend fun addBookmark(path: String, name: String) {
        ...
    }
    suspend fun removeBookmark(path: String) {
        ...
    }
    suspend fun getBookmarks(): List<Bookmark> {
        ...
    }
}
```

### 4. Undo Delete (Trash)

Move deleted files to trash instead of permanent deletion:

```kotlin
class TrashRepository(context: Context) {
    private val trashDir = File(context.filesDir, ".trash")

    suspend fun moveToTrash(file: File): Boolean {
        ...
    }
    suspend fun restoreFromTrash(trashedFile: File, originalPath: String): Boolean {
        ...
    }
    suspend fun emptyTrash() {
        ...
    }
    suspend fun getTrashContents(): List<TrashedFile> {
        ...
    }
}

data class TrashedFile(
    val trashedPath: String,
    val originalPath: String,
    val deletedAt: Long
)
```

### 5. Zip/Archive Support

Create and extract archives:

```kotlin
// Would require additional dependency like zip4j or use java.util.zip
suspend fun extractZip(zipFile: File, targetDir: File): Boolean {
    ...
}
suspend fun createZip(files: List<File>, outputZip: File): Boolean {
    ...
}
```

### 6. Batch Rename

Rename multiple files with patterns:

```kotlin
data class RenamePattern(
    val find: String,
    val replace: String,
    val useRegex: Boolean = false,
    val addPrefix: String = "",
    val addSuffix: String = "",
    val addCounter: Boolean = false
)
```

---

## Development Phases

### Phase 1: Project Setup

- [ ] Create new Android project with Kotlin + Compose
- [ ] Configure Gradle with all dependencies
- [ ] Set up Firebase (copy google-services.json)
- [ ] Set up Material 3 theme with dark mode support
- [ ] Configure FileProvider
- [ ] Add all string resources (copy from old project)

### Phase 2: Core Data Layer

- [ ] Create FileItem data class
- [ ] Create StorageDevice data class
- [ ] Create Clipboard data class and ClipboardManager
- [ ] Implement FileRepository (list, copy, move, delete, rename, create)
- [ ] Implement StorageRepository
- [ ] Implement MimeTypeUtil
- [ ] Implement FileSizeFormatter

### Phase 3: Storage Screen & Core Infrastructure

- [ ] Create error handling utilities (FileOperationResult, ErrorType)
- [ ] Create StorageViewModel
- [ ] Create StorageScreen composable
- [ ] Create StorageListItem composable (with display names)
- [ ] Handle permissions request
- [ ] Navigate to FolderScreen on selection
- [ ] Set up intent handling in MainActivity (normal, picker, open path modes)

### Phase 4: Folder Browser

- [ ] Create FolderViewModel with StateFlow
- [ ] Create FolderScreen composable
- [ ] Create FileListItem composable with icons
- [ ] Implement sorting (folders first, alphabetical)
- [ ] Add pull-to-refresh
- [ ] Add empty state
- [ ] Implement image thumbnails with Coil

### Phase 5: Selection Mode

- [ ] Add selection state to FolderViewModel
- [ ] Implement long-press to select
- [ ] Implement tap to toggle in selection mode
- [ ] Add visual feedback for selected items
- [ ] Implement select all / clear selection
- [ ] Handle back press in selection mode

### Phase 6: Action Bar

- [ ] Create ActionBar composable
- [ ] Implement visibility logic
- [ ] Wire up all button actions

### Phase 7: File Operations

- [ ] Implement open file with intent
- [ ] Implement clipboard (cut/copy/paste) with ClipboardManager
- [ ] Implement duplicate file handling (name conflicts)
- [ ] Create progress dialog for long operations
- [ ] Add operation cancellation support
- [ ] Implement delete with confirmation
- [ ] Create RenameDialog
- [ ] Implement rename
- [ ] Create CreateFolderDialog
- [ ] Implement create folder
- [ ] Implement share (single and multiple)
- [ ] Add MediaStore notifications after file operations

### Phase 8: Navigation

- [ ] Set up Compose Navigation
- [ ] Implement folder navigation
- [ ] Add breadcrumb/path display
- [ ] Add transition animations

### Phase 9: New Features - Search

- [ ] Create SearchViewModel
- [ ] Create SearchScreen
- [ ] Implement search logic (filename matching)
- [ ] Add search icon to top bar

### Phase 10: New Features - Recent Files

- [ ] Create RecentFile data class
- [ ] Implement RecentFilesRepository with JSON storage
- [ ] Create RecentViewModel
- [ ] Create RecentScreen
- [ ] Track file opens
- [ ] Add recent files navigation item

### Phase 11: New Features - Dark Mode & Settings

- [ ] Implement PreferencesRepository with DataStore
- [ ] Create DataStore extension property
- [ ] Create settings screen
- [ ] Add theme toggle (Light/Dark/System)
- [ ] Add hidden files toggle
- [ ] Add sort mode selector
- [ ] Wire theme to app
- [ ] Persist sort mode preference

### Phase 12: Polish & Release

- [ ] Add all localized strings
- [ ] Test on multiple devices/APIs
- [ ] Configure ProGuard/R8 (see rules below)
- [ ] Set up signing config
- [ ] Firebase Crashlytics testing
- [ ] Performance optimization
- [ ] Set up release workflow

**ProGuard Rules (proguard-rules.pro):**

```proguard
# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mauriciotogneri.fileexplorer.**$$serializer { *; }
-keepclassmembers class com.mauriciotogneri.fileexplorer.** {
    *** Companion;
}
-keepclasseswithmembers class com.mauriciotogneri.fileexplorer.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes used with Gson/serialization
-keep class com.mauriciotogneri.fileexplorer.data.model.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
```

---

## Data Models

### FileItem

```kotlin
data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val childCount: Int? = null  // Only for directories
) {
    val extension: String
        get() = name.substringAfterLast('.', "")
            .takeIf { it.length <= 4 }
            ?.uppercase() ?: ""

    val formattedSize: String
        get() = FileSizeFormatter.format(size)

    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isPdf: Boolean get() = mimeType == "application/pdf"
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")

    fun exists(): Boolean = File(path).exists()

    companion object {
        fun from(file: File): FileItem {
            return FileItem(
                path = file.absolutePath,
                name = file.name,
                isDirectory = file.isDirectory,
                size = file.length(),
                lastModified = file.lastModified(),
                mimeType = MimeTypeUtil.getMimeType(file),
                childCount = if (file.isDirectory) file.listFiles()?.size else null
            )
        }
    }
}
```

### StorageDevice

```kotlin
data class StorageDevice(
    val path: String,
    val displayName: String,  // "Internal Storage", "SD Card", etc.
    val totalBytes: Long,
    val availableBytes: Long
) {
    val formattedTotal: String get() = FileSizeFormatter.format(totalBytes)
    val formattedAvailable: String get() = FileSizeFormatter.format(availableBytes)

    companion object {
        fun getDisplayName(path: String, index: Int): String {
            return when {
                path.contains("emulated/0") -> "Internal Storage"
                path.contains("emulated") -> "Internal Storage ${path.substringAfterLast("emulated/")}"
                else -> "SD Card${if (index > 0) " ${index + 1}" else ""}"
            }
        }
    }
}
```

Update `StorageRepository` to use display names:

```kotlin
return externalDirs
    .filterNotNull()
    .mapIndexedNotNull { index, file ->
        val path = file.absolutePath.replace(basePath, "")
        if (isValidPath(path)) {
            val stat = StatFs(path)
            StorageDevice(
                path = path,
                displayName = StorageDevice.getDisplayName(path, index),
                totalBytes = stat.totalBytes,
                availableBytes = stat.availableBytes
            )
        } else null
    }
```

---

## Screen Specifications

### Storage Screen

- **Route:** `/` or `/storage`
- **State:** List of StorageDevice
- **Actions:** Tap storage -> navigate to FolderScreen with path
- **Skip if:** Only one storage exists

### Folder Screen

- **Route:** `/folder/{path}`
- **State:** FolderUiState (files, selectedFiles, isSelectionMode, isLoading, error)
- **Actions:**
    - Tap file -> open
    - Tap folder -> navigate deeper
    - Long press -> select
    - Pull down -> refresh
    - Action bar buttons -> file operations

### Search Screen

- **Route:** `/search?root={path}`
- **State:** query, results, isSearching
- **Actions:**
    - Type query -> search
    - Tap result -> navigate to folder containing file

### Recent Screen

- **Route:** `/recent`
- **State:** List of RecentFile
- **Actions:**
    - Tap -> open file
    - Long press -> remove from recent
    - Clear all button

### Settings Screen

- **Route:** `/settings`
- **State:** ThemeMode
- **Actions:** Select theme mode

---

## Old-to-New File Mapping

Reference table mapping old Java files to new Kotlin equivalents:

| Old File (Java)                 | New File (Kotlin)                                                                  | Notes                               |
|---------------------------------|------------------------------------------------------------------------------------|-------------------------------------|
| `MainActivity.java`             | `MainActivity.kt`                                                                  | Single activity, hosts Compose      |
| `FolderFragment.java`           | `ui/screens/folder/FolderScreen.kt` + `FolderViewModel.kt`                         | Split into screen + ViewModel       |
| `FolderAdapter.java`            | `ui/components/FileListItem.kt`                                                    | Now a Composable                    |
| `FileInfo.java`                 | `data/model/FileItem.kt` + `data/repository/FileRepository.kt`                     | Split: data model + operations      |
| `Clipboard.java`                | `data/model/Clipboard.kt` + `ClipboardManager.kt`                                  | Added singleton manager             |
| `ThumbnailLoader.java`          | (Removed - using Coil)                                                             | Replaced by Coil library            |
| `ButtonBar.java`                | `ui/components/ActionBar.kt`                                                       | Now a Composable                    |
| `ToolBar.java`                  | `ui/components/Breadcrumb.kt`                                                      | Enhanced with clickable breadcrumbs |
| `Dialogs.java`                  | `ui/components/RenameDialog.kt`, `CreateFolderDialog.kt`, `DeleteConfirmDialog.kt` | Split into separate Composables     |
| `SpaceFormatter.java`           | `data/util/FileSizeFormatter.kt`                                                   | Same logic, Kotlin syntax           |
| `LegacyCompatFileProvider.java` | (Removed - using AndroidX)                                                         | Using standard FileProvider         |
| `strings.xml`                   | `res/values/strings.xml`                                                           | Copy and update                     |
| `strings-fr.xml`, etc.          | `res/values-fr/strings.xml`, etc.                                                  | Copy all locales                    |

**Files with no direct equivalent (logic absorbed elsewhere):**

| Old File                   | Where the logic went               |
|----------------------------|------------------------------------|
| `R.java` (colors, styles)  | `ui/theme/Theme.kt`, `Color.kt`    |
| Fragment transaction logic | Compose Navigation (`NavGraph.kt`) |
| AsyncTask usage            | Kotlin Coroutines in ViewModels    |
| ListView/Adapter pattern   | `LazyColumn` + Composables         |

---

## Notes

1. **Storage Access:** This plan uses `MANAGE_EXTERNAL_STORAGE` (All Files Access) for full
   filesystem access. This allows using the standard `java.io.File` API but requires Play Store
   justification as a file manager app. Note: `/Android/data` and `/Android/obb` are inaccessible
   on Android 13+.

2. **Testing:** Consider adding unit tests for repositories and ViewModels using Turbine for Flow
   testing.

3. **Performance:** For folders with thousands of files, consider pagination or virtual scrolling.

4. **Accessibility:** Ensure all interactive elements have content descriptions.

5. **Edge Cases:**
    - Handle permission denial gracefully (both runtime and special app access)
    - Handle storage unmounting during operations
    - Handle file deletion during long operations
    - Handle rename conflicts
    - Handle inaccessible directories gracefully (Android 13+ restrictions)

---

## Resources

- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Material 3 Design](https://m3.material.io/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [Firebase Android SDK](https://firebase.google.com/docs/android/setup)
