package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class VCardMetadata(
    val contactCount: Int?,
    val hasPhoneNumbers: Boolean?,
    val hasEmails: Boolean?,
    val hasPhotos: Boolean?
)
