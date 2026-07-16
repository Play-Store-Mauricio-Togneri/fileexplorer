package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.isNoSpaceLeft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

// DataStore reads and writes throw IOException on I/O failure. These helpers degrade gracefully
// instead of crashing the app: writes become no-ops, reads and flows fall back to a default. The
// failure is reported to Crashlytics unless it is a full device (see reportUnlessDiskFull), which is
// unactionable. Non-IOException errors are rethrown so genuine bugs are not silently swallowed.

internal suspend fun DataStore<Preferences>.editSafely(
    operation: String,
    transform: suspend (MutablePreferences) -> Unit
) {
    try {
        edit(transform)
    } catch (e: IOException) {
        reportUnlessDiskFull(e, operation)
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
        reportUnlessDiskFull(e, operation)
        fallback
    }
}

internal fun <T> Flow<T>.catchIO(operation: String, fallback: T): Flow<T> = catch { e ->
    if (e !is IOException) throw e
    reportUnlessDiskFull(e, operation)
    emit(fallback)
}

/**
 * Reports every I/O failure except a full device, which is an environmental condition rather than an
 * app defect: nothing here can free the user's storage. This app is a file explorer, so users with
 * no space left are exactly the ones who open it, and every failed write (a file opened, a return to
 * the home screen, a setting toggled) would report again — burying the actionable failures the store
 * can still hit, such as corruption or a data directory that is no longer writable.
 *
 * Suppressing the report does not change behaviour: the caller degrades gracefully either way.
 */
private fun reportUnlessDiskFull(e: IOException, operation: String) {
    if (e.isNoSpaceLeft()) return

    ErrorReporter.warning(e, operation)
}
