package com.mauriciotogneri.fileexplorer.ui.screens.analyzer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import kotlin.math.PI

/**
 * The volume's used space as a ring, one arc in a single colour, with the headline figures in the
 * middle.
 *
 * The arc fills [usedFraction] of the circle, so the ring answers the same question as the
 * percentage at its centre: how full the volume is. The remainder is left as bare track and reads
 * as free space. The breakdown by category is left to the rows below, whose bars measure each
 * category against the used bytes — split into slices here, the smaller categories were too thin to
 * read and the slices' tones too close to tell apart.
 *
 * The ring is drawn to scale exactly once, when the results appear, growing round from twelve
 * o'clock. Nothing else on this screen moves. [reveal] is what it is drawn through, and it belongs
 * to the caller: this composable is a lazy item, whose composition is disposed the moment it leaves
 * the viewport, so a reveal remembered here would start over every time the ring scrolled back
 * into view.
 *
 * Taken as a function rather than a value so that the reveal is read in the draw phase, where a
 * frame of it costs a redraw rather than a recomposition of the list the ring sits in.
 */
@Composable
fun StorageDonutChart(
    usedFraction: Float,
    usedPercentLabel: String,
    usedSizeLabel: String,
    totalLabel: String,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 220.dp,
    thickness: Dp = 28.dp
) {
    val fill = MaterialTheme.colorScheme.primary
    val emptyTrack = MaterialTheme.colorScheme.surfaceVariant

    // No contentDescription, and deliberately no clearAndSetSemantics: the ring is a picture of
    // figures that are already written inside it and listed in full below it, so it is decorative.
    // Clearing semantics here would take the labelled centre text away from a screen reader in
    // order to replace it with a sentence saying the same thing.
    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val revealed = reveal()
            val strokePx = thickness.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            val radius = arcSize.width / 2f
            val stroke = Stroke(width = strokePx)

            // The whole circle, under the arc: what the arc leaves uncovered reads as free space.
            drawArc(
                color = emptyTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            // From the top, clockwise. Scaled by the reveal, so the ring grows round from twelve
            // o'clock.
            val sweep = usedFraction * 360f * revealed

            // An arc shorter than a pixel cannot be seen, but it is not harmless: the renderer cuts
            // an arc out of the ring between two lines through the centre, and when they all but
            // coincide their anti-aliasing leaks a hairline on the opposite side of the ring. A
            // nearly empty volume draws one of these, and so do the first frames of the reveal.
            if (sweep * PI.toFloat() / 180f * radius >= MIN_ARC_LENGTH_PX) {
                drawArc(
                    color = fill,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            }
        }

        // Both figures say "used", in different units, so each carries the shortest word that tells
        // them apart: the share is labelled as such, and the amount is given the total it is a
        // share of.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // The percentage keeps no leading below its glyphs, so its own label sits close under
            // it. The two pairs are then separated by a gap several times that, which is what makes
            // "used" read as belonging to the figure above it rather than the one below.
            val percentStyle = MaterialTheme.typography.headlineLarge

            Text(
                text = usedPercentLabel,
                style = percentStyle.copy(lineHeight = percentStyle.fontSize),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.analyzer_used),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = usedSizeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = totalLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The shortest arc, measured along the middle of the stroke, that the ring draws at all. */
private const val MIN_ARC_LENGTH_PX = 1f
