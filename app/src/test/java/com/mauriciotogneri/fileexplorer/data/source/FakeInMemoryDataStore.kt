package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Working in-memory [DataStore], the counterpart to [FakeThrowingDataStore]: that one covers the
 * failure paths, this one covers behaviour that depends on what is actually stored (cache hits,
 * TTL expiry, round-tripping a write).
 */
internal class FakeInMemoryDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)

    /**
     * Number of writes this store has been *asked* to perform, which is what callers that batch to
     * keep their flush count down need to assert against.
     *
     * An upper bound on real flushes, not an equivalent: a real DataStore skips the file write
     * entirely when a transform leaves the contents unchanged, so a caller writing a value equal to
     * the stored one is counted here and costs nothing on disk.
     */
    var writeCount: Int = 0
        private set

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writeCount++
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
