package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mauriciotogneri.fileexplorer.ui.theme.extendedColorScheme
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val ActionButtonWidth = 80.dp
private val SwipeThreshold = 40.dp

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
    val actionButtonWidthPx = with(density) { ActionButtonWidth.toPx() }
    val swipeThresholdPx = with(density) { SwipeThreshold.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val deleteColor = MaterialTheme.colorScheme.error
    val renameColor = MaterialTheme.extendedColorScheme.success

    val isRevealed by remember {
        derivedStateOf { abs(offsetX.value) > 1f }
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        SwipeActionButtons(
            offsetX = offsetX.value,
            deleteColor = deleteColor,
            renameColor = renameColor,
            onDelete = {
                scope.launch {
                    offsetX.animateTo(0f)
                    onDelete()
                }
            },
            onRename = {
                scope.launch {
                    offsetX.animateTo(0f)
                    onRename()
                }
            }
        )

        FileListItem(
            file = file,
            onClick = {
                if (isRevealed) {
                    scope.launch { offsetX.animateTo(0f) }
                } else {
                    onClick()
                }
            },
            onLongClick = {
                if (!isRevealed) {
                    onLongClick()
                }
            },
            onMenuClick = {
                if (!isRevealed) {
                    onMenuClick()
                }
            },
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
                                        offsetX.animateTo(actionButtonWidthPx)
                                    }
                                    offsetX.value < -swipeThresholdPx -> {
                                        offsetX.animateTo(-actionButtonWidthPx)
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
                                val newOffset = (offsetX.value + dragAmount)
                                    .coerceIn(-actionButtonWidthPx, actionButtonWidthPx)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        )
    }
}

@Composable
private fun BoxScope.SwipeActionButtons(
    offsetX: Float,
    deleteColor: Color,
    renameColor: Color,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    if (offsetX > 0) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(deleteColor)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(ActionButtonWidth),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onError
                    )
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.onError,
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
                .background(renameColor)
                .clickable(onClick = onRename),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(ActionButtonWidth),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.action_rename),
                        tint = MaterialTheme.extendedColorScheme.onSuccess
                    )
                    Text(
                        text = stringResource(R.string.action_rename),
                        color = MaterialTheme.extendedColorScheme.onSuccess,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
