package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import kotlin.math.abs

internal val CardRowEdgePadding = 16.dp
internal val CardRowSpacing = 12.dp

// The strip under the square thumbnail that holds the name and the menu button.
internal val CardLabelHeight = 40.dp

private val TargetCardWidth = 120.dp
private const val PEEK_FRACTION = 0.4f

/**
 * The width of a Home row card for a row [rowWidth] wide, chosen so that some whole number of
 * cards plus [PEEK_FRACTION] of the next one fill the row after its leading padding. A fixed width
 * lines up with some screen width exactly — 120 dp shows three whole cards and nothing more on a
 * 411 dp phone — and a row that ends on a card edge gives no sign that it scrolls. Of the widths
 * that leave that peek, this picks the one closest to [TargetCardWidth].
 */
internal fun peekingCardWidth(rowWidth: Dp): Dp {
    if (!rowWidth.isFinite) return TargetCardWidth

    val available = (rowWidth - CardRowEdgePadding).value
    val target = TargetCardWidth.value

    fun widthFor(wholeCards: Int): Float =
        (available - CardRowSpacing.value * wholeCards) / (wholeCards + PEEK_FRACTION)

    // Widths shrink as the count grows, so the closest one is where the distance stops falling.
    var wholeCards = 1
    while (abs(widthFor(wholeCards + 1) - target) < abs(widthFor(wholeCards) - target)) {
        wholeCards++
    }

    return widthFor(wholeCards).coerceAtLeast(0f).dp
}
