package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * A [DataStore] whose reads and writes always fail, used to verify the source layer degrades
 * gracefully instead of crashing. Defaults to [IOException] (the disk-full / ENOSPC case); pass a
 * different factory to check that non-IOException errors are not swallowed.
 */
internal class FakeThrowingDataStore(
    private val failWith: () -> Throwable = { IOException("no space left on device") }
) : DataStore<Preferences> {

    override val data: Flow<Preferences> = flow { throw failWith() }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        throw failWith()
    }
}
