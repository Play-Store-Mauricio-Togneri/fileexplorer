package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ShortDateFormatter
import com.mauriciotogneri.fileexplorer.ui.util.rememberShortDateFormatter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val ActionButtonWidth = 80.dp
private val SwipeThreshold = 40.dp

/**
 * A file row that reveals a configurable action when dragged sideways.
 *
 * Directions are physical, not the layout's start and end: [rightAction] is what dragging the row
 * towards the right edge of the screen reveals, in every language. That is why the row is placed
 * with [Modifier.absoluteOffset] and the buttons with [AbsoluteAlignment] — their layout-aware
 * counterparts mirror under RTL while the drag, reported in raw screen pixels, does not, which left
 * the row moving away from the finger in Arabic and Urdu.
 *
 * A direction set to [SwipeAction.NONE] does not move at all: there is nothing to reveal, so the
 * drag is clamped to zero that way and a row with both directions off never claims the gesture.
 */
@Composable
fun SwipeableFileListItem(
    file: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSwipeAction: (SwipeAction) -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
    leftAction: SwipeAction = SwipeAction.RENAME,
    rightAction: SwipeAction = SwipeAction.DELETE,
    isRestricted: Boolean = false,
    isFavorite: Boolean = false,
    folderSecondLine: FolderSecondLine = FolderSecondLine.ITEM_COUNT,
    fileSecondLine: FileSecondLine = FileSecondLine.SIZE,
    dateFormatter: ShortDateFormatter = rememberShortDateFormatter()
) {
    val density = LocalDensity.current
    val actionButtonWidthPx = with(density) { ActionButtonWidth.toPx() }
    val swipeThresholdPx = with(density) { SwipeThreshold.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val minOffset = if (leftAction == SwipeAction.NONE) 0f else -actionButtonWidthPx
    val maxOffset = if (rightAction == SwipeAction.NONE) 0f else actionButtonWidthPx

    val isRevealed by remember {
        derivedStateOf { abs(offsetX.value) > 1f }
    }

    // Which button is uncovered, derived from the offset's sign rather than read from it directly:
    // a drag changes the offset every frame but crosses zero once, so the row is not recomposed
    // while it follows the finger.
    val revealedRightAction by remember(rightAction) {
        derivedStateOf { rightAction.takeIf { it != SwipeAction.NONE && offsetX.value > 0f } }
    }
    val revealedLeftAction by remember(leftAction) {
        derivedStateOf { leftAction.takeIf { it != SwipeAction.NONE && offsetX.value < 0f } }
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

    // Switching a direction off in Settings while a row sits open behind them would otherwise leave
    // its button exposed with the drag now clamped, so nothing could close it again.
    LaunchedEffect(leftAction, rightAction) {
        if (offsetX.value < minOffset || offsetX.value > maxOffset) {
            offsetX.animateTo(0f)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Each button is drawn on the edge its own direction uncovers: dragging right moves the row
        // right and exposes the left edge, and the other way round.
        if (!isSelectionMode) {
            revealedRightAction?.let { action ->
                SwipeActionButton(
                    action = action,
                    alignment = AbsoluteAlignment.CenterLeft,
                    onClick = {
                        scope.launch {
                            AnalyticsTracker.trackFolderSwipeActionTapped(action.name.lowercase())
                            offsetX.animateTo(0f)
                            onSwipeAction(action)
                        }
                    }
                )
            }

            revealedLeftAction?.let { action ->
                SwipeActionButton(
                    action = action,
                    alignment = AbsoluteAlignment.CenterRight,
                    onClick = {
                        scope.launch {
                            AnalyticsTracker.trackFolderSwipeActionTapped(action.name.lowercase())
                            offsetX.animateTo(0f)
                            onSwipeAction(action)
                        }
                    }
                )
            }
        }

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
            isFavorite = isFavorite,
            folderSecondLine = folderSecondLine,
            fileSecondLine = fileSecondLine,
            dateFormatter = dateFormatter,
            modifier = Modifier
                .absoluteOffset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(isSelectionMode, leftAction, rightAction) {
                    if (isSelectionMode || (minOffset == 0f && maxOffset == 0f)) return@pointerInput

                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offsetX.value > swipeThresholdPx -> {
                                        AnalyticsTracker.trackFolderSwipedRight()
                                        offsetX.animateTo(maxOffset)
                                    }
                                    offsetX.value < -swipeThresholdPx -> {
                                        AnalyticsTracker.trackFolderSwipedLeft()
                                        offsetX.animateTo(minOffset)
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
                                    .coerceIn(minOffset, maxOffset)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        )
    }
}

/**
 * The label for [action], shared by the button a swipe reveals and the settings rows that choose it.
 */
@Composable
internal fun swipeActionLabel(action: SwipeAction): String = when (action) {
    SwipeAction.NONE -> stringResource(R.string.settings_swipe_action_none)
    SwipeAction.RENAME -> stringResource(R.string.action_rename)
    SwipeAction.DELETE -> stringResource(R.string.action_delete)
    SwipeAction.MOVE_TO -> stringResource(R.string.action_move_to)
    SwipeAction.COPY_TO -> stringResource(R.string.action_copy_to)
    SwipeAction.INFO -> stringResource(R.string.action_info)
}

/**
 * The button one direction uncovers, filling the row so the whole strip behind it is tappable while
 * the icon and label stay on the [alignment] edge, where the row has actually moved away from.
 *
 * Colour says one thing only: red for the action that destroys something, the app's neutral surface
 * for the rest. Which action it is, the icon and the label say.
 */
@Composable
private fun BoxScope.SwipeActionButton(
    action: SwipeAction,
    alignment: Alignment,
    onClick: () -> Unit
) {
    val icon = when (action) {
        SwipeAction.RENAME -> Icons.Outlined.Edit
        SwipeAction.DELETE -> Icons.Outlined.Delete
        SwipeAction.MOVE_TO -> Icons.AutoMirrored.Outlined.DriveFileMove
        SwipeAction.COPY_TO -> Icons.Outlined.ContentCopy
        SwipeAction.INFO -> Icons.Outlined.Info
        // A direction set to NONE reveals nothing, so it never reaches here; the branch exists only
        // because the enum has to be covered.
        SwipeAction.NONE -> return
    }
    val label = swipeActionLabel(action)
    val isDestructive = action == SwipeAction.DELETE
    val backgroundColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = alignment
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
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor
                )
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
