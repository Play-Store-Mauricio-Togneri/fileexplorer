package com.mauriciotogneri.fileexplorer.testutil

import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.source.CachedSizeResult
import com.mauriciotogneri.fileexplorer.data.source.LocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.source.MediaChangeSource
import com.mauriciotogneri.fileexplorer.data.source.StorageVolumeChangeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Sources that make a real `HomeViewModel` deterministic, for the tests that render the real
 * `HomeScreen` instead of its sections in isolation.
 *
 * [WarmLocationSizes] is the one that matters. `LocationsRepository.getLocations()` only walks a
 * directory tree on a cache *miss*, so answering every lookup with a valid cached size is what
 * removes the scan — faking the storage source alone leaves the walk in place. That scan behind
 * `uiState.isLoading` is why `NavigationDrawerTest` was annotated `@Retry` on all six of its tests.
 */
internal object WarmLocationSizes : LocationsCacheSource {
    override suspend fun getCachedSize(type: LocationType): CachedSizeResult =
        CachedSizeResult(size = 0L, isValid = true)

    override suspend fun generation(): Long = 0L

    override suspend fun updateCache(sizes: Map<LocationType, Long>, generation: Long) = Unit

    override suspend fun clearCache(): Boolean = true
}

/** Keeps the device's media store and volume mounts from reloading the screen mid-test. */
internal object NoExternalChanges : MediaChangeSource, StorageVolumeChangeSource {
    override fun changes(): Flow<Unit> = emptyFlow()
}
