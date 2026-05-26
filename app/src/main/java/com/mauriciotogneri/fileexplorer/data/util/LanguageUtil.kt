package com.mauriciotogneri.fileexplorer.data.util

import java.util.Locale

fun String.toDisplayLanguage(): String {
    val locale = Locale.forLanguageTag(this)
    val displayName = locale.getDisplayLanguage(Locale.getDefault())
    return if (displayName.isNotBlank() && displayName != this) displayName else this
}
