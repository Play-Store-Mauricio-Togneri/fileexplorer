package com.mauriciotogneri.fileexplorer.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hit test in `PdfModels` that decides whether a tap lands on a link. */
class PdfModelsTest {

    private val rect = PdfRectPt(left = 10f, top = 20f, right = 110f, bottom = 40f)

    @Test
    fun `a point inside or on the edge of a rectangle hits it`() {
        assertTrue(rect.contains(60f, 30f))
        assertTrue(rect.contains(10f, 20f))
        assertTrue(rect.contains(110f, 40f))
    }

    @Test
    fun `a point outside a rectangle misses it`() {
        assertFalse(rect.contains(9.9f, 30f))
        assertFalse(rect.contains(110.1f, 30f))
        assertFalse(rect.contains(60f, 19.9f))
        assertFalse(rect.contains(60f, 40.1f))
    }

    @Test
    fun `top-left coordinates grow downwards`() {
        // A rectangle whose top is below its bottom — a bottom-left rectangle passed through
        // unconverted — contains nothing, which is what makes a conversion slip show up as dead links.
        assertFalse(PdfRectPt(10f, 40f, 110f, 20f).contains(60f, 30f))
    }
}
