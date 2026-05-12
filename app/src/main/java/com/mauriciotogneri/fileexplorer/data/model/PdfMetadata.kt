package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class PdfMetadata(
    val pageCount: Int?
)
