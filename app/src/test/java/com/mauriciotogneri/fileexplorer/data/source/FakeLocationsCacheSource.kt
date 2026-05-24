package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.LocationType

class FakeLocationsCacheSource : LocationsCacheSource {

    private val cache = mutableMapOf<LocationType, Pair<Long, Boolean>>()

    fun setCache(type: LocationType, size: Long, isValid: Boolean = true) {
        cache[type] = size to isValid
    }

    override suspend fun getCachedSize(type: LocationType): CachedSizeResult {
        val cached = cache[type]
        return CachedSizeResult(
            size = cached?.first,
            isValid = cached?.second ?: false
        )
    }

    override suspend fun updateCache(type: LocationType, size: Long) {
        cache[type] = size to true
    }

    override suspend fun clearCache() {
        cache.entries.forEach { (type, pair) ->
            cache[type] = pair.first to false
        }
    }
}
