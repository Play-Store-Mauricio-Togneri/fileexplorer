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
5. [Development Phases](#development-phases)
6. [Data Models](#data-models)
7. [Screen Specifications](#screen-specifications)
8. [Implementation Details](#implementation-details)

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
| Firebase         | Crashlytics, Analytics, FCM | Same                              |

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
))
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
- `isVideo()`: MIME type starts with `video`
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
    fun isVideo(mimeType: String) = mimeType.startsWith("video")
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
        .size(Size(24.dp.toPx(), 24.dp.toPx()))
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

**Algorithm:**

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
            val targetFile = File(targetDir, source.name)
            try {
                source.inputStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 1024)
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
fun FolderScreen(...) {
    val pullRefreshState = rememberPullToRefreshState()

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
            pullRefreshState.endRefresh()
        }
    }

    Box(Modifier.nestedScroll(pullRefreshState.nestedScrollConnection)) {
        LazyColumn { ... }
        PullToRefreshContainer(state = pullRefreshState)
    }
}
```

---

### 12. Permissions

**Current Implementation:** `MainActivity.java:76-110`

Runtime permissions for storage access using the "Native Path" with `MANAGE_EXTERNAL_STORAGE`.

**Two Permission Eras:**

| Android Version | Permission Type | Check Method |
|-----------------|-----------------|--------------|
| 6-10 (API 23-29) | Runtime Permission Dialog | `ContextCompat.checkSelfPermission()` |
| 11+ (API 30+) | Special App Access (Settings Toggle) | `Environment.isExternalStorageManager()` |

**Behavior:**

- On launch, check Android version and request appropriate permission
- Android 6-10: Show standard runtime permission dialog
- Android 11+: Redirect to system settings for "All Files Access" toggle
- If granted, proceed to storage detection
- If denied, show explanation and prompt again (or close app)

**Manifest Configuration:**

```xml
<!-- AndroidManifest.xml -->
<manifest ...>
    <!-- Legacy permissions for Android 6 to 10 -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
                     android:maxSdkVersion="29" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
                     android:maxSdkVersion="29" />

    <!-- "All Files Access" for Android 11+ -->
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

    <application
        android:requestLegacyExternalStorage="true" ...>
        <!-- The flag above helps Android 10 devices specifically -->
    </application>
</manifest>
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
            != PackageManager.PERMISSION_GRANTED) {
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
- [ ] Create Clipboard data class
- [ ] Implement FileRepository (list, copy, move, delete, rename, create)
- [ ] Implement StorageRepository
- [ ] Implement MimeTypeUtil
- [ ] Implement FileSizeFormatter

### Phase 3: Storage Screen

- [ ] Create StorageViewModel
- [ ] Create StorageScreen composable
- [ ] Create StorageListItem composable
- [ ] Handle permissions request
- [ ] Navigate to FolderScreen on selection

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
- [ ] Implement clipboard (cut/copy/paste)
- [ ] Create progress dialog for long operations
- [ ] Implement delete with confirmation
- [ ] Create RenameDialog
- [ ] Implement rename
- [ ] Create CreateFolderDialog
- [ ] Implement create folder
- [ ] Implement share (single and multiple)

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
- [ ] Create settings screen
- [ ] Add theme toggle
- [ ] Wire theme to app

### Phase 12: Polish & Release

- [ ] Add all localized strings
- [ ] Test on multiple devices/APIs
- [ ] Configure ProGuard/R8
- [ ] Set up signing config
- [ ] Firebase Crashlytics testing
- [ ] Performance optimization

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
    val isVideo: Boolean get() = mimeType.startsWith("video")

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
    val totalBytes: Long,
    val availableBytes: Long
) {
    val formattedTotal: String get() = FileSizeFormatter.format(totalBytes)
    val formattedAvailable: String get() = FileSizeFormatter.format(availableBytes)
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
