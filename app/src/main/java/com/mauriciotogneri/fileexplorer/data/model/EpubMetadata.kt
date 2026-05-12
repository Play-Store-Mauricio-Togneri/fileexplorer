package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class EpubMetadata(
    val title: String?,
    val creator: String?,
    val publisher: String?,
    val language: String?,
    val date: String?,
    val description: String?
)
