package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class ICalendarMetadata(
    val eventCount: Int?,
    val todoCount: Int?,
    val earliestDate: String?,
    val latestDate: String?
)
