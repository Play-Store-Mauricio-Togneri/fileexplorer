package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.LocationType

interface LocationsCacheSource {
    suspend fun getCachedSize(type: LocationType): CachedSizeResult

    /**
     * Identifies the current contents of the cache, and is bumped by [clearCache]. A caller that
     * captures this before it starts measuring can hand it back to [updateCache] as proof that
     * nothing invalidated the cache while the measurements were being taken.
     */
    suspend fun generation(): Long

    /**
     * Stores every size in one write, discarding the whole batch when [generation] no longer
     * matches the store's own.
     *
     * Takes the entire batch rather than a single location because the store is flushed to disk on
     * every write: updating per location cost one flush for each of [LocationType.entries] on a
     * single home load. Writes nothing for an empty map.
     */
    suspend fun updateCache(sizes: Map<LocationType, Long>, generation: Long)

    suspend fun clearCache()
}

data class CachedSizeResult(
    val size: Long?,
    val isValid: Boolean
)
