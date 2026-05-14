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

- Move: `MediaStoreUtil.scanFiles(context, copiedFiles)`
- Copy: `MediaStoreUtil.scanFiles(context, copiedFiles)`
- Delete: `deletedFiles.forEach { MediaStoreUtil.notifyDeleted(context, it.path) }`
- Rename: `MediaStoreUtil.notifyDeleted(context, oldPath)` then
  `MediaStoreUtil.scanFile(context, newFile)`

Things to avoid:

* Unreliable Transfers: File moves that fail halfway or don't provide clear error messages when a
  process stops.