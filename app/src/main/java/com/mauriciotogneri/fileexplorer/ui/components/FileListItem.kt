package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.thumbnailCacheKeyAtSize
import com.mauriciotogneri.fileexplorer.ui.theme.extendedColorScheme
import com.mauriciotogneri.fileexplorer.data.util.AppImageLoader
import com.mauriciotogneri.fileexplorer.data.util.ShortDateFormatter
import com.mauriciotogneri.fileexplorer.ui.util.getFileIcon
import com.mauriciotogneri.fileexplorer.ui.util.rememberShortDateFormatter
import java.io.File

/**
 * The smallest thumbnail a row asks for, whatever its own slot measures. Everything but a PDF is
 * drawn cropped to fill the square slot, while the extractors fit their output inside the box they
 * were asked for, so a box the size of the slot alone leaves a 16:9 frame short on the axis the
 * crop has to cover. This is the box the shipped build asked for, and it keeps such a frame near
 * the size it is drawn at on the densities where the slot is smaller.
 */
private const val MIN_ICON_REQUEST_PX = 120

/**
 * A row for one file or folder.
 *
 * [folderSecondLine], [fileSecondLine] and [dateFormatter] default to what rows showed before the
 * settings existed, so a caller that does not care about the preference — a preview or a test of
 * some other part of the row — reads unchanged.
 *
 * A screen showing a list of these passes the user's choice, and passes a [dateFormatter] it holds
 * itself: the default builds one per row, and building one parses two date patterns.
 *
 * [loadsChildCounts] states whether the screen counts a folder's children at all. Search does not,
 * so a folder row there would otherwise reserve a line for a count that is never coming.
 *
 * [isSelectionMode] is the screen's mode rather than this row's state, and it empties the trailing
 * slot of its menu for every row: while a selection exists the action bar is what acts on files, so
 * a per-row menu that quietly folds its row into that selection has no meaning to offer. Screens
 * without a selection mode leave it at its default, and [isSelected] hides the menu on its own row
 * regardless — the guard does not rely on a caller keeping the two consistent.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isRestricted: Boolean = false,
    isFavorite: Boolean = false,
    showMenu: Boolean = true,
    folderSecondLine: FolderSecondLine = FolderSecondLine.ITEM_COUNT,
    fileSecondLine: FileSecondLine = FileSecondLine.SIZE,
    dateFormatter: ShortDateFormatter = rememberShortDateFormatter(),
    loadsChildCounts: Boolean = true
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.extendedColorScheme.selectionBackground
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectableFileIcon(file = file, isSelected = isSelected, isRestricted = isRestricted)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val secondary = secondaryText(
                    file = file,
                    isRestricted = isRestricted,
                    folderSecondLine = folderSecondLine,
                    fileSecondLine = fileSecondLine,
                    dateFormatter = dateFormatter
                )

                // Dropped only when the setting says there will never be a second line: the Row
                // centers its children, so the name then sits in the middle of the row instead of
                // above a blank. A setting that does produce text keeps the line even while the text
                // is missing — a folder whose count is still loading, or a file whose modification
                // time cannot be read — so the name does not jump when the text arrives.
                //
                // Row height is unaffected either way: the 40dp icon and the 48dp menu slot are both
                // taller than the two lines of text they sit beside.
                if (secondary.isNotEmpty() || expectsSecondLine(file, isRestricted, folderSecondLine, fileSecondLine, loadsChildCounts)) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        minLines = 1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Selection mode empties the slot for every row, and a selected row empties its own
            // whatever the screen reports, so the two cannot disagree into a menu on a picked row.
            val menuIsHidden = isSelectionMode || isSelected

            // The favorite marker stays visible while selecting, but not in the same place: with a
            // menu to sit beside it takes the place before it, and without one it takes the menu's
            // own slot, so during selection mode every starred row shows its star on the same
            // trailing edge instead of floating short of it. Decorative — non-interactive either
            // way.
            val markerTakesMenuSlot = isFavorite && showMenu && menuIsHidden

            if (isFavorite && !markerTakesMenuSlot) {
                FavoriteMarker(modifier = Modifier.padding(start = 12.dp))
            }

            if (showMenu) {
                // The slot is reserved whether or not it draws anything: beside a single line of
                // text its 48dp, not the 40dp icon, is what sets the row's height, so collapsing it
                // would shorten every row the moment selection mode starts.
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !menuIsHidden -> {
                            IconButton(onClick = onMenuClick) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.content_description_more_options),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        isFavorite -> FavoriteMarker()
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteMarker(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Outlined.Star,
        contentDescription = stringResource(R.string.content_description_favorite),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(20.dp)
    )
}

/**
 * Whether this row's settings can ever produce a second line, which decides between reserving the
 * line while its text is missing and dropping it so the name centers.
 *
 * A restricted folder always has text, whatever the folder setting says. A count is expected only on
 * a screen that takes one: on one that does not, [FolderSecondLine.ITEM_COUNT] can produce nothing,
 * so reserving the line would leave every folder row permanently blank instead of centering it.
 */
private fun expectsSecondLine(
    file: FileItem,
    isRestricted: Boolean,
    folderSecondLine: FolderSecondLine,
    fileSecondLine: FileSecondLine,
    loadsChildCounts: Boolean
): Boolean = if (file.isDirectory) {
    when {
        isRestricted -> true
        folderSecondLine == FolderSecondLine.NONE -> false
        folderSecondLine == FolderSecondLine.ITEM_COUNT -> loadsChildCounts
        else -> true
    }
} else {
    fileSecondLine != FileSecondLine.NONE
}

/**
 * The row's second line, or an empty string when there is nothing to show for the chosen setting.
 *
 * A restricted folder answers "Restricted" whatever [folderSecondLine] says: it has no count and no
 * date worth reporting, and the lock badge on its icon is decorative, so this text is the only thing
 * naming the state. [FolderSecondLine.ITEM_COUNT] is blank until the count arrives, and stays blank
 * on screens that never load one.
 */
@Composable
private fun secondaryText(
    file: FileItem,
    isRestricted: Boolean,
    folderSecondLine: FolderSecondLine,
    fileSecondLine: FileSecondLine,
    dateFormatter: ShortDateFormatter
): String = if (file.isDirectory) {
    when {
        isRestricted -> stringResource(R.string.folder_restricted)

        folderSecondLine == FolderSecondLine.ITEM_COUNT -> file.childCount?.let { count ->
            pluralStringResource(R.plurals.item_amount, count, count)
        }.orEmpty()

        folderSecondLine == FolderSecondLine.LAST_MODIFIED -> dateFormatter.format(file.lastModified)

        else -> ""
    }
} else {
    when (fileSecondLine) {
        FileSecondLine.NONE -> ""
        FileSecondLine.SIZE -> file.formattedSize
        FileSecondLine.LAST_MODIFIED -> dateFormatter.format(file.lastModified)
    }
}

@Composable
private fun SelectableFileIcon(
    file: FileItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isRestricted: Boolean = false
) {
    val iconSize = 40.dp

    when {
        isSelected -> {
            Box(
                modifier = modifier
                    .size(iconSize)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.content_description_selected),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        isRestricted -> {
            // Folder icon with a small lock badge in the bottom-end corner. The badge
            // sits on a surface-colored circle so it reads clearly over the folder, and is
            // decorative (the "Restricted" subtitle already announces the state).
            Box(modifier = modifier.size(iconSize)) {
                FileIcon(file = file)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        else -> {
            FileIcon(file = file, modifier = modifier)
        }
    }
}

@Composable
private fun FileIcon(
    file: FileItem,
    modifier: Modifier = Modifier
) {
    val iconSize = 40.dp
    val context = LocalContext.current

    when {
        file.isDirectory -> {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = stringResource(R.string.content_description_folder),
                modifier = modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        file.hasThumbnailSupport -> {
            // The slot in pixels, so a dense screen is not handed a thumbnail rendered for a
            // smaller box. It qualifies the key too: the cards and the Item Info preview draw the
            // same file larger, and must not be served what this row cached.
            val requestSizePx = with(LocalDensity.current) {
                maxOf(iconSize.roundToPx(), MIN_ICON_REQUEST_PX)
            }
            // Remembered so scrolling doesn't rebuild the request (and its File and cache key) on
            // every recomposition of a row; the identity only changes when the file itself does.
            val request = remember(file.path, file.lastModified, requestSizePx) {
                ImageRequest.Builder(context)
                    .data(File(file.path))
                    .memoryCacheKey(thumbnailCacheKeyAtSize(file.thumbnailCacheKey, requestSizePx))
                    .size(requestSizePx)
                    .crossfade(true)
                    .build()
            }

            SubcomposeAsyncImage(
                model = request,
                imageLoader = AppImageLoader.thumbnails(context),
                contentDescription = file.name,
                modifier = modifier.size(iconSize),
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)),
                        // A PDF page is rendered fitted inside the box requested, so it is never
                        // as wide as the square slot: cropping it to fill would upscale it.
                        contentScale = if (file.isPdf) ContentScale.Fit else ContentScale.Crop
                    )
                },
                error = {
                    Icon(
                        imageVector = getFileIcon(file),
                        contentDescription = file.name,
                        modifier = Modifier.size(iconSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }

        else -> {
            Icon(
                imageVector = getFileIcon(file),
                contentDescription = file.name,
                modifier = modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

