package com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.data.util.ShortDateFormatter
import com.mauriciotogneri.fileexplorer.ui.components.EmptyState
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.util.rememberShortDateFormatter

/** The size of the loading indicator that sits below the last loaded page. */
private val PageLoaderSize = 24.dp

/**
 * The files behind one slice of the storage analyzer's chart, biggest first.
 *
 * A report rather than a file manager: rows do not respond to a tap, carry no menu and cannot be
 * selected. Everything the user might want to do with a file it names, the folder and search
 * screens already do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerCategoryScreen(
    category: AnalyzerCategory,
    viewModel: AnalyzerCategoryViewModel,
    onCloseClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Held here rather than per row: building one parses two date patterns.
    val dateFormatter = rememberShortDateFormatter()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(category.labelResId),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // The figure the user tapped, restated so it stays with them once they
                        // have scrolled away from the chart.
                        Text(
                            text = FileSizeFormatter.format(uiState.totalBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCloseClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                actions = {
                    // Placed now so the bar has its final shape; selection itself is not wired up
                    // yet, so the press deliberately does nothing.
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Outlined.SelectAll,
                            contentDescription = stringResource(R.string.action_select_all)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // Before the first page lands, the indicator is its own screen rather than the
                // list's only item. A LazyColumn whose sole item is the loader anchors its scroll
                // position to that item's key, and follows it down to index 100 when the page
                // arrives underneath — opening a biggest-first listing on its smallest rows.
                uiState.files.isEmpty() && !uiState.isEmpty -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                uiState.isEmpty -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    messageResId = R.string.analyzer_category_empty
                )

                else -> FileList(
                    files = uiState.files,
                    hasMore = uiState.hasMore,
                    dateFormatter = dateFormatter,
                    onLoadNextPage = viewModel::loadNextPage
                )
            }
        }
    }
}

@Composable
private fun FileList(
    files: List<FileItem>,
    hasMore: Boolean,
    dateFormatter: ShortDateFormatter,
    onLoadNextPage: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = files,
            key = { it.path }
        ) { file ->
            FileListItem(
                file = file,
                onClick = {},
                onLongClick = {},
                onMenuClick = {},
                isSelected = false,
                showMenu = false,
                dateFormatter = dateFormatter,
                // Nothing here is a folder, so no row is waiting on a child count.
                loadsChildCounts = false,
                isClickable = false
            )
        }

        if (hasMore) {
            item(key = "loader") {
                // Composing this item is what asks for the next page — LazyColumn brings it into
                // composition as the end of the list is reached, and drops it again once a page
                // has pushed it out of view. Keyed on the loaded count rather than on Unit so that
                // a viewport still not filled by the page that just arrived asks for another one
                // instead of stopping short.
                LaunchedEffect(files.size) { onLoadNextPage() }

                PageLoader()
            }
        }
    }
}

@Composable
private fun PageLoader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(PageLoaderSize))
    }
}
