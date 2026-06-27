package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.util.AppImageLoader
import com.mauriciotogneri.fileexplorer.ui.util.getFileIcon
import java.io.File

private val FavoriteCardWidth = 120.dp
private val FavoriteCardHeight = 160.dp

@Composable
fun FavoritesSection(
    favorites: List<Favorite>,
    onFileClick: (Favorite) -> Unit,
    onMenuClick: (Favorite, String) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState()
) {
    if (favorites.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.section_favorites),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        LazyRow(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = favorites,
                key = { it.path }
            ) { favorite ->
                FavoriteFileCard(
                    favorite = favorite,
                    onClick = { onFileClick(favorite) },
                    onIconClick = { onMenuClick(favorite, "icon") },
                    onLongPress = { onMenuClick(favorite, "press") }
                )
            }
        }
    }
}

@Composable
private fun FavoriteFileCard(
    favorite: Favorite,
    onClick: () -> Unit,
    onIconClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = modifier
            .width(FavoriteCardWidth)
            .height(FavoriteCardHeight)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Folders never have thumbnail support, so they fall through to getFileIcon()
                // which returns the folder glyph.
                if (favorite.hasThumbnailSupport) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(favorite.path))
                            .crossfade(true)
                            .build(),
                        imageLoader = AppImageLoader.get(context),
                        contentDescription = favorite.name,
                        modifier = Modifier.fillMaxSize(),
                        success = {
                            SubcomposeAsyncImageContent(
                                contentScale = if (favorite.isPdf) ContentScale.Fit else ContentScale.Crop
                            )
                        },
                        error = {
                            Icon(
                                imageVector = getFileIcon(favorite),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(0.5f),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                } else {
                    Icon(
                        imageVector = getFileIcon(favorite),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.5f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = favorite.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onIconClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.content_description_more_options),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
