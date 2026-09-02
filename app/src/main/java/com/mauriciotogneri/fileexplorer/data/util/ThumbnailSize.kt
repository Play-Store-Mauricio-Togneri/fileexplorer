package com.mauriciotogneri.fileexplorer.data.util

import coil3.request.Options
import coil3.size.Dimension

/**
 * The size a request needs covered, resolving the dimensions Coil leaves undefined the same way the
 * fetchers do when they extract.
 */
internal fun Options.thumbnailWidth(): Int = size.width.pxOrElse { DEFAULT_THUMBNAIL_SIZE }

internal fun Options.thumbnailHeight(): Int = size.height.pxOrElse { DEFAULT_THUMBNAIL_SIZE }

internal const val DEFAULT_THUMBNAIL_SIZE = 120

internal fun Dimension.pxOrElse(default: () -> Int): Int =
    if (this is Dimension.Pixels) px else default()
