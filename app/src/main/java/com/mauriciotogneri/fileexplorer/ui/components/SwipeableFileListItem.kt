package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SwipeThreshold = 150.dp

@Composable
fun SwipeableFileListItem(
    file: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { SwipeThreshold.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val deleteColor = MaterialTheme.colorScheme.error
    val renameColor = Color(0xFF4CAF50)

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        SwipeBackground(
            offsetX = offsetX.value,
            deleteColor = deleteColor,
            renameColor = renameColor
        )

        FileListItem(
            file = file,
            onClick = onClick,
            onLongClick = onLongClick,
            onMenuClick = onMenuClick,
            isSelected = isSelected,
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(isSelected) {
                    if (isSelected) return@pointerInput

                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offsetX.value > swipeThresholdPx -> {
                                        offsetX.animateTo(swipeThresholdPx * 2)
                                        onDelete()
                                        offsetX.snapTo(0f)
                                    }
                                    offsetX.value < -swipeThresholdPx -> {
                                        offsetX.animateTo(-swipeThresholdPx * 2)
                                        onRename()
                                        offsetX.snapTo(0f)
                                    }
                                    else -> {
                                        offsetX.animateTo(0f)
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newOffset = offsetX.value + dragAmount
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        )
    }
}

@Composable
private fun BoxScope.SwipeBackground(
    offsetX: Float,
    deleteColor: Color,
    renameColor: Color
) {
    if (offsetX > 0) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(deleteColor),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(SwipeThreshold),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    if (offsetX < 0) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(renameColor),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(SwipeThreshold),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(
                        text = stringResource(R.string.action_rename),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
