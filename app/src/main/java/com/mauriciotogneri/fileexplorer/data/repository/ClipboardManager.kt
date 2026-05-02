package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.Clipboard
import com.mauriciotogneri.fileexplorer.data.model.ClipboardMode
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ClipboardManager {

    private val _clipboard = MutableStateFlow(Clipboard())
    val clipboard: StateFlow<Clipboard> = _clipboard.asStateFlow()

    fun cut(items: List<FileItem>, sourceParent: String) {
        _clipboard.value = Clipboard(
            items = items,
            mode = ClipboardMode.CUT,
            sourceParent = sourceParent
        )
    }

    fun copy(items: List<FileItem>, sourceParent: String) {
        _clipboard.value = Clipboard(
            items = items,
            mode = ClipboardMode.COPY,
            sourceParent = sourceParent
        )
    }

    fun clear() {
        _clipboard.value = Clipboard()
    }
}
