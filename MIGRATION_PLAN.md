# File Explorer v2 - Migration Plan

This document tracks remaining work for the File Explorer Android app rewrite.

**Development Standards:** See `CLAUDE.md` for coding standards that must be followed during
implementation, including theming, testing, localization, architecture, and performance
requirements.

**Old project reference:** `/home/max/Repositories/personal/fileexplorer-old/`

---

## Table of Contents

1. [Remaining Work](#remaining-work)
2. [Pending Feature Specifications](#pending-feature-specifications)
3. [Future Considerations](#future-considerations)
4. [Notes](#notes)

---

- [ ] Create ProgressDialog composable for long copy/move operations
- [ ] Add operation cancellation support (cancel button in progress dialog)
- [ ] Add MediaStore notifications after file operations (see spec below)

## Pending Feature Specifications

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

### DataStore Migration

Replace `UserPreferencesManager` SharedPreferences with DataStore:

```kotlin
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(context: Context) {
    private val dataStore = context.dataStore

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[THEME_KEY] ?: ThemeMode.SYSTEM.name)
    }

    val sortMode: Flow<SortMode> = dataStore.data.map { prefs ->
        SortMode.valueOf(prefs[SORT_KEY] ?: SortMode.NAME_ASC.name)
    }

    val showHiddenFiles: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_HIDDEN_KEY] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_KEY] = mode.name }
    }

    suspend fun setSortMode(mode: SortMode) {
        dataStore.edit { it[SORT_KEY] = mode.name }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        dataStore.edit { it[SHOW_HIDDEN_KEY] = show }
    }

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_mode")
        private val SORT_KEY = stringPreferencesKey("sort_mode")
        private val SHOW_HIDDEN_KEY = booleanPreferencesKey("show_hidden_files")
    }
}
```

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
