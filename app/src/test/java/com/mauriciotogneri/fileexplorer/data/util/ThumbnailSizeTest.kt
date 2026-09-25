package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Size
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailSizeTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `pxOrElse returns pixel value when dimension is Dimension Pixels`() {
        val dimension = Dimension.Pixels(240)
        assertEquals(240, dimension.pxOrElse { DEFAULT_THUMBNAIL_SIZE })
    }

    @Test
    fun `pxOrElse returns default when dimension is Dimension Undefined`() {
        val dimension = Dimension.Undefined
        assertEquals(DEFAULT_THUMBNAIL_SIZE, dimension.pxOrElse { DEFAULT_THUMBNAIL_SIZE })
    }

    @Test
    fun `thumbnailWidth returns pixel width when defined`() {
        val options = Options(
            context = context,
            size = Size(Dimension.Pixels(300), Dimension.Undefined)
        )
        assertEquals(300, options.thumbnailWidth())
    }

    @Test
    fun `thumbnailWidth falls back to DEFAULT_THUMBNAIL_SIZE when undefined`() {
        val options = Options(
            context = context,
            size = Size(Dimension.Undefined, Dimension.Pixels(300))
        )
        assertEquals(DEFAULT_THUMBNAIL_SIZE, options.thumbnailWidth())
    }

    @Test
    fun `thumbnailHeight returns pixel height when defined`() {
        val options = Options(
            context = context,
            size = Size(Dimension.Undefined, Dimension.Pixels(450))
        )
        assertEquals(450, options.thumbnailHeight())
    }

    @Test
    fun `thumbnailHeight falls back to DEFAULT_THUMBNAIL_SIZE when undefined`() {
        val options = Options(
            context = context,
            size = Size(Dimension.Pixels(300), Dimension.Undefined)
        )
        assertEquals(DEFAULT_THUMBNAIL_SIZE, options.thumbnailHeight())
    }
}
