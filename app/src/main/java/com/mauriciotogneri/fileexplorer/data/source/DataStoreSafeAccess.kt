package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

// DataStore reads and writes throw IOException on I/O failure (e.g. ENOSPC when the device runs out
// of storage). These helpers report the failure and degrade gracefully instead of crashing the app:
// writes become no-ops, reads and flows fall back to a default. Non-IOException errors are rethrown
// so genuine bugs are not silently swallowed.

internal suspend fun DataStore<Preferences>.editSafely(
    operation: String,
    transform: suspend (MutablePreferences) -> Unit
) {
    try {
        edit(transform)
    } catch (e: IOException) {
        ErrorReporter.warning(e, operation)
    }
}

internal suspend fun <T> DataStore<Preferences>.readSafely(
    operation: String,
    fallback: T,
    read: (Preferences) -> T
): T {
    return try {
        read(data.first())
    } catch (e: IOException) {
        ErrorReporter.warning(e, operation)
        fallback
    }
}

internal fun <T> Flow<T>.catchIO(operation: String, fallback: T): Flow<T> = catch { e ->
    if (e !is IOException) throw e
    ErrorReporter.warning(e, operation)
    emit(fallback)
}
