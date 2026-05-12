package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class CsvMetadata(
    val rowCount: Int?,
    val columnCount: Int?
)
