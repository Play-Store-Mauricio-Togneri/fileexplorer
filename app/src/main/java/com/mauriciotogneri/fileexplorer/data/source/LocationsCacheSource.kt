package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.LocationType

interface LocationsCacheSource {
    suspend fun getCachedSize(type: LocationType): CachedSizeResult
    suspend fun updateCache(type: LocationType, size: Long)
    suspend fun clearCache()
}

data class CachedSizeResult(
    val size: Long?,
    val isValid: Boolean
)
