package com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.IntSize
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerSupport

/**
 * Zoom over the page list. The list keeps laying out at its own size and is drawn scaled by [scale]
 * about the viewport's centre, then shifted by [offsetX] / [offsetY], so a pinch costs a layer
 * transform rather than a re-render. The shift is bounded so the scaled list always covers the
 * viewport; vertical movement past that bound scrolls the list instead, which is how a zoomed
 * reader moves through the document, and how the top of the first page and the bottom of the last
 * stay reachable.
 *
 * "Local" coordinates below are the list's own, unscaled ones; "screen" coordinates are the
 * viewport's.
 */
@Stable
internal class PdfZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    /** True while fingers are moving the document, when a sharp re-render would be wasted. */
    var gestureActive by mutableStateOf(false)

    var viewport by mutableStateOf(IntSize.Zero)

    private val centerX get() = viewport.width / 2f
    private val centerY get() = viewport.height / 2f
    private val maxOffsetX get() = (scale - 1f) * centerX
    private val maxOffsetY get() = (scale - 1f) * centerY

    fun toLocal(screen: Offset): Offset = Offset(
        centerX + (screen.x - centerX - offsetX) / scale,
        centerY + (screen.y - centerY - offsetY) / scale
    )

    /** The part of the list's local space that is on screen. */
    fun visibleLocalRect(): Rect = Rect(
        toLocal(Offset.Zero),
        toLocal(Offset(viewport.width.toFloat(), viewport.height.toFloat()))
    )

    /** Moves the document by [delta] screen pixels: horizontally within bounds, vertically by scrolling first. */
    fun pan(delta: Offset, listState: LazyListState) {
        offsetX = (offsetX + delta.x).coerceIn(-maxOffsetX, maxOffsetX)
        val consumed = listState.dispatchRawDelta(-delta.y / scale)
        val remaining = delta.y + consumed * scale
        offsetY = (offsetY + remaining).coerceIn(-maxOffsetY, maxOffsetY)
    }

    /** Zooms to [target], keeping the document point under [focus] (screen) where it is. */
    fun zoomTo(target: Float, focus: Offset, listState: LazyListState) {
        val newScale = target.coerceIn(1f, PdfViewerSupport.MAX_ZOOM)
        val local = toLocal(focus)
        scale = newScale
        offsetX = (focus.x - centerX - (local.x - centerX) * newScale).coerceIn(-maxOffsetX, maxOffsetX)
        val desiredY = focus.y - centerY - (local.y - centerY) * newScale
        offsetY = desiredY.coerceIn(-maxOffsetY, maxOffsetY)
        val excess = desiredY - offsetY
        if (excess != 0f) {
            listState.dispatchRawDelta(-excess / newScale)
        }
    }

    /** Puts the top of the list's viewport at the top of the screen, keeping the zoom. */
    fun alignTop() {
        offsetY = maxOffsetY
    }

    /**
     * Moves the document vertically, zoom unchanged, so local y [localY] lands at screen y [screenY]
     * as far as the bounds allow. The list cannot scroll past its ends, so this is what finishes
     * bringing a point into view when the scroll that should have done it stopped short.
     */
    fun placeAt(localY: Float, screenY: Float) {
        offsetY = (screenY - centerY - (localY - centerY) * scale).coerceIn(-maxOffsetY, maxOffsetY)
    }

    /** Brings local x [localX] to the horizontal centre of the screen, as far as the bounds allow. */
    fun centerOn(localX: Float) {
        offsetX = (-(localX - centerX) * scale).coerceIn(-maxOffsetX, maxOffsetX)
    }
}

/**
 * Pinch to zoom, and one-finger panning while zoomed. Runs on the initial pass so the list below
 * never sees what this handles: two fingers are never a scroll, and neither is one finger on a
 * zoomed document, whose drags [PdfZoomState.pan] turns into scrolling itself. One finger at 1x is
 * left alone, for the list's own scrolling and fling.
 */
internal suspend fun PointerInputScope.detectZoomAndPan(zoom: PdfZoomState, listState: LazyListState) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var panning = false
        var travelled = Offset.Zero
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                panning = true
                zoom.gestureActive = true
                val centroid = event.calculateCentroid(useCurrent = true)
                if (centroid != Offset.Unspecified) {
                    zoom.zoomTo(zoom.scale * event.calculateZoom(), centroid, listState)
                    zoom.pan(event.calculatePan(), listState)
                }
                event.changes.forEach { it.consume() }
            } else if (pressed == 1 && zoom.scale > 1f) {
                val delta = event.calculatePan()
                if (!panning) {
                    travelled += delta
                    if (travelled.getDistance() > touchSlop) {
                        panning = true
                        zoom.gestureActive = true
                    }
                }
                if (panning) {
                    zoom.pan(delta, listState)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
        zoom.gestureActive = false
    }
}
