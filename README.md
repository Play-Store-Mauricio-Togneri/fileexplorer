# File Explorer

* Menu
    * Use red badges in hamburger menu call the attention of users and make them click to discover
      the app
    * Implement PreferencesRepository with DataStore (replace SharedPreferences)
    * Settings
        * Swipe configuration (left/right)
        * Dark/ligth theme (Light/Dark/System)
        * Default sort mode
    * About
        * Version
        * Other apps
        * Privacy policy
        * Terms and conditions
    * Feedback

* Implement analytics

* Implement crashlytics

* Grid View: Alternative to list view for visual browsing

* Bookmarks / Favorites: Quick access to favorite folders

* Implement move/copy:

- [ ] Create ProgressDialog composable for long copy/move operations
- [ ] Add operation cancellation support (cancel button in progress dialog)
- [ ] Add MediaStore notifications after file operations (see spec below)

### Paste Operation

Wire up the paste action in `FolderViewModel.onPaste()`:

```kotlin
private fun onPaste() {
    val clipboard = ClipboardManager.clipboard.value
    if (!clipboard.canPasteInto(_state.value.currentPath)) return

    viewModelScope.launch {
        _state.update { it.copy(copyProgress = CopyProgress(...)) }

        fileRepository.copyFiles(
            sources = clipboard.items,
            targetDir = _state.value.currentPath,
            deleteAfter = clipboard.mode == Clipboard.ClipboardMode.CUT
        ).collect { progress ->
            _state.update { it.copy(copyProgress = progress) }
        }

        ClipboardManager.clear()
        loadFiles()
    }
}
```

### Progress Dialog

```kotlin
@Composable
fun ProgressDialog(
    progress: CopyProgress,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
        title = { Text(stringResource(R.string.clipboard_copy)) },
        text = {
            Column {
                Text(progress.currentFile)
                LinearProgressIndicator(progress = { progress.progressPercent })
                Text("${progress.copiedFiles} / ${progress.totalFiles}")
            }
        }
    )
}
```

### MediaStore Notifications

After file operations, notify MediaStore so changes appear in Gallery, Music, etc.:

```kotlin
object MediaStoreUtil {
    fun scanFile(context: Context, file: File) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null,
            null
        )
    }

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

Call after:

- Paste: `MediaStoreUtil.scanFiles(context, copiedFiles)`
- Delete: `deletedFiles.forEach { MediaStoreUtil.notifyDeleted(context, it.path) }`
- Rename: `MediaStoreUtil.notifyDeleted(context, oldPath)` then
  `MediaStoreUtil.scanFile(context, newFile)`

# Make file info a separate activity

Location: show copy button

Audio (via MediaMetadataRetriever - built-in)

- Duration (formatted as HH:MM:SS)
- Artist
- Album
- Title
- Genre
- Year
- Bitrate (kbps)

Video (via MediaMetadataRetriever - built-in)

- Duration
- Resolution (width × height)
- Frame rate (FPS)
- Bitrate
- Rotation

PDF (via PdfRenderer - built-in)

- Page count (easy)

---

Things to avoid:

* Unreliable Transfers: File moves that fail halfway or don't provide clear error messages when a
  process stops.
* Slow Search: Taking several seconds to find a file in a large directory instead of providing
  instant results.
* Thumbnail Lag: Waiting for image or video thumbnails to generate while scrolling through large
  galleries.

## ProGuard Rules

Add to `proguard-rules.pro`:

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

# Keep data classes used with serialization
-keep class com.mauriciotogneri.fileexplorer.data.model.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
```

---

# Check for

* What features don't work in some old Android versions?
* Search for TODOs
* Bugs
* Security vulnerabilities
* Different Android version problems
* Problems with screen size
* Problems in some devices
* Performance problems
* Unused or dead code
* Accessibility problems
* Localize app in all major languages and existing used languages in the app
* Is there hardcoded text or it's all localized?
* Run project inspections (Problems tab)
* Verify test coverage for ViewModels, repositories, and utilities
* Test on multiple devices/APIs
* Configure ProGuard/R8 (see rules below)
* Set up signing config
* Update library versions in libs.versions.toml
* Performance optimization (profile with Layout Inspector)
* Unit tests required for all business logic. Use JUnit 4 + Mockk for mocking, and Turbine for Flow
  testing.