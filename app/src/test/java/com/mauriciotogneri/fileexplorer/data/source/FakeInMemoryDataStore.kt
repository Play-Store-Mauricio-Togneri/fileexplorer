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

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
