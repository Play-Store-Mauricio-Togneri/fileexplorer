package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class OfficeMetadata(
    val title: String?,
    val creator: String?,
    val subject: String?,
    val keywords: String?,
    val createdDate: String?,
    val modifiedDate: String?
)
