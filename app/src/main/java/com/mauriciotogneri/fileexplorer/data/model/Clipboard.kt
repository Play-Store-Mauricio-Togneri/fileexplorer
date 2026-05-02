package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Clipboard(
    val items: List<FileItem> = emptyList(),
    val mode: ClipboardMode = ClipboardMode.NONE,
    val sourceParent: String? = null
) {
    fun canPasteInto(targetPath: String): Boolean {
        if (items.isEmpty()) return false
        if (items.none { it.exists() }) return false
        if (sourceParent == targetPath) return false
        return items.none { targetPath.startsWith(it.path + "/") || targetPath == it.path }
    }

    fun isEmpty(): Boolean = items.isEmpty()
}

enum class ClipboardMode {
    NONE,
    CUT,
    COPY
}
