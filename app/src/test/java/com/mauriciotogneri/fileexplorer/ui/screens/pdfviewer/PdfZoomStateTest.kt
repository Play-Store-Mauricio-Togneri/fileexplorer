package com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerSupport
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The zoom geometry. The list here is never laid out, so it cannot scroll: every vertical movement
 * lands on [PdfZoomState.offsetY], which is exactly the edge-of-document case — the top of the first
 * page, the bottom of the last — that the offset exists for.
 */
class PdfZoomStateTest {

    private val listState = LazyListState()

    private fun zoom() = PdfZoomState().apply { viewport = IntSize(1000, 2000) }

    private fun assertNear(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.01f)
        assertEquals(expected.y, actual.y, 0.01f)
    }

    @Test
    fun `at 1x the screen and the list coincide`() {
        val zoom = zoom()

        assertNear(Offset(123f, 456f), zoom.toLocal(Offset(123f, 456f)))
        assertEquals(Rect(0f, 0f, 1000f, 2000f), zoom.visibleLocalRect())
    }

    @Test
    fun `zooming keeps the point under the fingers where it was`() {
        val zoom = zoom()
        val focus = Offset(700f, 900f)
        val before = zoom.toLocal(focus)

        zoom.zoomTo(2f, focus, listState)

        assertEquals(2f, zoom.scale)
        assertNear(before, zoom.toLocal(focus))
    }

    @Test
    fun `zoom is clamped to the supported range`() {
        val zoom = zoom()
        zoom.zoomTo(50f, Offset(500f, 1000f), listState)
        assertEquals(PdfViewerSupport.MAX_ZOOM, zoom.scale)

        zoom.zoomTo(0.1f, Offset(500f, 1000f), listState)
        assertEquals(1f, zoom.scale)
        assertEquals(0f, zoom.offsetX)
        assertEquals(0f, zoom.offsetY)
    }

    @Test
    fun `the zoomed document always covers the screen`() {
        val zoom = zoom()
        // Zooming about a corner would pull the opposite edge into view; the bounds refuse.
        zoom.zoomTo(2f, Offset(0f, 0f), listState)
        zoom.pan(Offset(5000f, 5000f), listState)

        val visible = zoom.visibleLocalRect()
        assertEquals(0f, visible.left, 0.01f)
        assertEquals(0f, visible.top, 0.01f)

        zoom.pan(Offset(-10_000f, -10_000f), listState)
        val after = zoom.visibleLocalRect()
        assertEquals(1000f, after.right, 0.01f)
        assertEquals(2000f, after.bottom, 0.01f)
    }

    @Test
    fun `aligning to the top shows the top of the list at the current zoom`() {
        val zoom = zoom()
        zoom.zoomTo(3f, Offset(500f, 1500f), listState)

        zoom.alignTop()

        assertEquals(0f, zoom.visibleLocalRect().top, 0.01f)
        assertEquals(3f, zoom.scale)
    }

    @Test
    fun `placing a point puts it at the requested screen height when the bounds allow`() {
        val zoom = zoom()
        zoom.zoomTo(4f, Offset(500f, 1000f), listState)

        // Visible local band at 4x is 500 tall; 1600 local sits in the lower part of the list.
        zoom.placeAt(localY = 1600f, screenY = 500f)
        assertEquals(1600f, zoom.toLocal(Offset(0f, 500f)).y, 0.01f)

        // Past the bottom edge the bounds win: the list's bottom stays at the screen's bottom.
        zoom.placeAt(localY = 1990f, screenY = 0f)
        assertEquals(2000f, zoom.visibleLocalRect().bottom, 0.01f)
    }

    @Test
    fun `centring on a point puts it in the middle of the screen when the bounds allow`() {
        val zoom = zoom()
        zoom.zoomTo(2f, Offset(500f, 1000f), listState)

        zoom.centerOn(400f)
        assertEquals(400f, zoom.visibleLocalRect().center.x, 0.01f)

        zoom.centerOn(0f)
        assertEquals(0f, zoom.visibleLocalRect().left, 0.01f)
    }
}
