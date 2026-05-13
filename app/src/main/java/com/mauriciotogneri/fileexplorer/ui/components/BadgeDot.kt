package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BadgeColor = Color(0xFFE53935)
private val BadgeSize = 8.dp
private val DefaultBadgeOffset = 4.dp

@Composable
fun BadgeDot(
    showBadge: Boolean,
    modifier: Modifier = Modifier,
    offset: Dp = DefaultBadgeOffset,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = offset, y = -offset)
                    .size(BadgeSize)
                    .background(color = BadgeColor, shape = CircleShape)
            )
        }
    }
}
