package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class PickerRequest(
    val items: List<FileItem>,
    val mode: OperationMode
)
