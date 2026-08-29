package com.mauriciotogneri.fileexplorer.data.model

/**
 * Builds the memory cache key a file's thumbnail is stored under.
 *
 * Passing this to Coil explicitly lets it skip building a key of its own, which stats the file on
 * the main thread (see AppImageLoader). The modification time is always read off the main thread
 * instead — when the directory is listed, when the favorites/recents store is read, or when the
 * home screen re-stats its cards on resume (HomeViewModel.refreshThumbnailTimestamps, needed
 * because those stores emit only when written) — and stays part of the key so an edited file gets
 * a fresh thumbnail rather than the cached one.
 *
 * Defined once because every model that can show a thumbnail must agree on the format: the same
 * file reached through the folder list, favorites and recents resolves to one cache entry rather
 * than one per screen, wherever it is drawn at the same size. Screens drawing it at a different
 * size qualify the key with [thumbnailCacheKeyAtSize].
 */
fun thumbnailCacheKey(path: String, lastModified: Long): String = "$path:$lastModified"

/**
 * Qualifies [baseKey] with the pixel size the thumbnail was requested at.
 *
 * Coil returns an entry smaller than the request instead of re-decoding: a bitmap only fails the
 * size check if it was downsampled on the way in, and a thumbnail an extractor rendered to fit the
 * box it was asked for was not. So screens drawing the same file at different sizes must not share
 * one entry — whichever loaded first would be stretched into every larger slot. What separating
 * them costs is bounded: the disk cache still keeps one entry per file, and it grows to the
 * largest size any screen has asked for, so a size is extracted again only until the entry has
 * settled at the largest of them, and decoded again after that.
 */
fun thumbnailCacheKeyAtSize(baseKey: String, sizePx: Int): String = "$baseKey@$sizePx"
