package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class SqliteMetadata(
    val tableCount: Int?,
    val tableNames: List<String>?,
    val totalRowCount: Long?
)
