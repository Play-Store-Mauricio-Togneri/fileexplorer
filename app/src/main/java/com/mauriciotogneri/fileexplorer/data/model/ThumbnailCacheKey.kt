package com.mauriciotogneri.fileexplorer.data.model

/**
 * Builds the memory cache key a file's thumbnail is stored under.
 *
 * Passing this to Coil explicitly lets it skip building a key of its own, which stats the file on
 * the main thread (see AppImageLoader). The modification time is always read off the main thread
 * instead — when the directory is listed, or when the favorites/recents store is read — and stays
 * part of the key so an edited file gets a fresh thumbnail rather than the cached one. How promptly
 * depends on the screen: the folder list re-stats on every listing, while favorites and recents
 * re-stat only when their store emits, so an in-place edit alone may not refresh those cards until
 * the store is next written.
 *
 * Defined once because every model that can show a thumbnail must agree on the format: the same
 * file reached through the folder list, favorites and recents should resolve to a single cache
 * entry rather than one per screen.
 */
fun thumbnailCacheKey(path: String, lastModified: Long): String = "$path:$lastModified"
