package com.mauriciotogneri.fileexplorer.ui.screens.analyzer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.ui.theme.extendedColorScheme

/**
 * The volume's used space as a ring, one arc per category, with the headline figures in the middle.
 *
 * The arcs carry no labels of their own. Each category's row below draws its bar in the same tone,
 * which is what ties an arc to a name — a legend inside the ring would repeat the list underneath it.
 *
 * The ring is drawn to scale exactly once, on first appearance, so the eye is taken round it in the
 * order the rows are listed. Nothing else on this screen moves.
 */
@Composable
fun StorageDonutChart(
    categories: List<CategoryUsage>,
    usedPercentLabel: String,
    usedSizeLabel: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 220.dp,
    thickness: Dp = 28.dp
) {
    val tones = MaterialTheme.extendedColorScheme.categoryTones
    val fallbackTone = MaterialTheme.colorScheme.primary
    val emptyTrack = MaterialTheme.colorScheme.surfaceVariant

    // An Animatable started at zero rather than animateFloatAsState(targetValue = 1f): that helper
    // remembers its Animatable at the first target it is given and only animates when a later
    // target differs, so a constant target animates nothing and the ring would appear fully drawn.
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        sweep.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 700))
    }

    Box(
        modifier = modifier
            .size(diameter)
            // The ring is a picture of the list below it, so it is announced once as a whole rather
            // than as six unlabelled arcs a screen reader would have to read out in tone order.
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val strokePx = thickness.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            val stroke = Stroke(width = strokePx)

            // Drawn under the arcs rather than instead of them, so a volume whose categories do not
            // quite close the ring shows a gap in the track's colour instead of the background.
            drawArc(
                color = emptyTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            // From the top, clockwise, in row order. Both the offset and the length of every arc
            // are scaled by the reveal, so the ring grows round from twelve o'clock rather than
            // every slice swelling in place from a fixed start.
            var offset = 0f

            categories.forEachIndexed { index, usage ->
                val fullSweep = usage.fraction * 360f
                if (fullSweep <= 0f) return@forEachIndexed

                // A hairline of the gap between neighbours, so two adjacent tones separated by one
                // step of the ramp still read as two arcs. Never wider than the arc itself, or a
                // sliver category would invert into a negative sweep.
                val gap = ARC_GAP_DEGREES.coerceAtMost(fullSweep / 2f)

                drawArc(
                    color = tones.getOrElse(index) { fallbackTone },
                    startAngle = -90f + offset * sweep.value,
                    sweepAngle = (fullSweep - gap) * sweep.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )

                offset += fullSweep
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = usedPercentLabel,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = usedSizeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val ARC_GAP_DEGREES = 2f
