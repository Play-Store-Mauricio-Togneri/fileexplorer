package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BadgeSize = 8.dp
private val DefaultBadgeOffset = 4.dp

/** Test tag on the badge dot itself (the dot has no other semantics to match on). */
const val BADGE_DOT_TEST_TAG = "badge_dot"

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
                    .testTag(BADGE_DOT_TEST_TAG)
                    .background(color = MaterialTheme.colorScheme.error, shape = CircleShape)
            )
        }
    }
}
