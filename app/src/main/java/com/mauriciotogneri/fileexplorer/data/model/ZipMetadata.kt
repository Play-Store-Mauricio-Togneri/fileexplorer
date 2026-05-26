package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class ZipMetadata(
    val entryCount: Int?,
    val compressedSize: Long?,
    val uncompressedSize: Long?
)
