package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

class PeekingCardWidthTest {

    // How much of the first partly visible card shows: whatever the whole cards and their gaps
    // leave of the row after its leading padding. Zero when the row ends on a gap or a card edge.
    private fun peek(rowWidth: Dp): Float {
        val available = (rowWidth - CardRowEdgePadding).value
        val card = peekingCardWidth(rowWidth).value
        val slot = card + CardRowSpacing.value
        val remainder = available - floor(available / slot) * slot
        return if (remainder <= card) remainder else 0f
    }

    @Test
    fun `411 dp phone shows three whole cards and part of a fourth`() {
        assertEquals(105.6f, peekingCardWidth(411.dp).value, 0.1f)
        assertEquals(42.2f, peek(411.dp), 0.1f)
    }

    @Test
    fun `next card peeks at every common width`() {
        listOf(320, 360, 384, 393, 400, 411, 412, 430, 480, 600, 700, 840, 1280).forEach { width ->
            val peek = peek(width.dp)
            assertTrue("peek at $width dp was $peek dp", peek >= 30f)
        }
    }

    // Widest and narrowest either side of where two whole cards give way to three: 142.5 dp at
    // 382 dp, 97.4 dp at 383 dp. Every other count switch lands closer to the target.
    @Test
    fun `card width stays near the 120 dp target`() {
        (320..1280).forEach { width ->
            val card = peekingCardWidth(width.dp).value
            assertTrue("card at $width dp was $card dp", card in 97f..143f)
        }
    }

    @Test
    fun `unbounded row falls back to the target width`() {
        assertEquals(120.dp, peekingCardWidth(Dp.Infinity))
    }

    @Test
    fun `row too narrow for a card yields zero width`() {
        assertEquals(0.dp, peekingCardWidth(10.dp))
    }
}
