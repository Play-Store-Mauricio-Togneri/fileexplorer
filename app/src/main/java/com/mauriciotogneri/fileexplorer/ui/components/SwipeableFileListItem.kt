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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
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
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
    isRestricted: Boolean = false
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

    // Entering selection mode (via "Select All" or long-pressing any row) collapses a swiped-open
    // row, otherwise it would stay translated with its action button exposed and unable to be
    // swiped back. Keyed on selection mode, not this row's selection, so revealing one row and
    // then selecting a different one still collapses it.
    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode) {
            offsetX.animateTo(0f)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        SwipeActionButtons(
            offsetX = offsetX.value,
            isSelectionMode = isSelectionMode,
            deleteColor = deleteColor,
            renameColor = renameColor,
            onDelete = {
                scope.launch {
                    AnalyticsTracker.trackFolderSwipeDeleteTapped()
                    offsetX.animateTo(0f)
                    onDelete()
                }
            },
            onRename = {
                scope.launch {
                    AnalyticsTracker.trackFolderSwipeRenameTapped()
                    offsetX.animateTo(0f)
                    onRename()
                }
            }
        )

        FileListItem(
            file = file,
            onClick = {
                // In selection mode a tap toggles selection rather than collapsing: selection takes
                // priority and the row is already collapsing via the LaunchedEffect above.
                if (isRevealed && !isSelectionMode) {
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
            isRestricted = isRestricted,
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(isSelectionMode) {
                    if (isSelectionMode) return@pointerInput

                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offsetX.value > swipeThresholdPx -> {
                                        AnalyticsTracker.trackFolderSwipedRight()
                                        offsetX.animateTo(actionButtonWidthPx)
                                    }
                                    offsetX.value < -swipeThresholdPx -> {
                                        AnalyticsTracker.trackFolderSwipedLeft()
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
    isSelectionMode: Boolean,
    deleteColor: Color,
    renameColor: Color,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    val deleteLabel = stringResource(R.string.action_delete)
    val renameLabel = stringResource(R.string.action_rename)

    if (offsetX > 0 && !isSelectionMode) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(deleteColor)
                .clickable(onClick = onDelete)
                .clearAndSetSemantics {
                    contentDescription = deleteLabel
                    role = Role.Button
                },
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
                        contentDescription = deleteLabel,
                        tint = MaterialTheme.colorScheme.onError
                    )
                    Text(
                        text = deleteLabel,
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    if (offsetX < 0 && !isSelectionMode) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(renameColor)
                .clickable(onClick = onRename)
                .clearAndSetSemantics {
                    contentDescription = renameLabel
                    role = Role.Button
                },
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
                        contentDescription = renameLabel,
                        tint = MaterialTheme.extendedColorScheme.onSuccess
                    )
                    Text(
                        text = renameLabel,
                        color = MaterialTheme.extendedColorScheme.onSuccess,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
