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

    /**
     * Invalidates every cached size — their timestamps are dropped, the values themselves stay —
     * and bumps [generation].
     *
     * Returns whether the store actually changed. An I/O failure is absorbed rather than thrown, so
     * a caller that gave up a one-shot record of "something invalidated these sizes" to make this
     * call needs to be told the difference between a clear that happened and one that was
     * swallowed.
     */
    suspend fun clearCache(): Boolean
}

data class CachedSizeResult(
    val size: Long?,
    val isValid: Boolean
)
