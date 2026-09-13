package com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.FolderActivity
import com.mauriciotogneri.fileexplorer.activities.ImageViewerActivity
import com.mauriciotogneri.fileexplorer.activities.ItemInfoActivity
import com.mauriciotogneri.fileexplorer.activities.TextViewerActivity
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.data.util.ShortDateFormatter
import com.mauriciotogneri.fileexplorer.ui.components.ActionButton
import com.mauriciotogneri.fileexplorer.ui.components.AnalyzerFileAction
import com.mauriciotogneri.fileexplorer.ui.components.AnalyzerFileActionsBottomSheet
import com.mauriciotogneri.fileexplorer.ui.components.ApkPermissionDialog
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.EmptyState
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.components.PasswordUncompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.UncompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.UncompressProgressDialog
import com.mauriciotogneri.fileexplorer.ui.util.rememberShortDateFormatter
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.OpenFileResult
import kotlinx.coroutines.flow.collectLatest
import java.io.File

/** The size of the loading indicator that sits below the last loaded page. */
private val PageLoaderSize = 24.dp

/** The screen these rows are opened, acted on and deleted from. */
private const val SOURCE = "analyzer_category"

/**
 * The files behind one slice of the storage analyzer's chart, biggest first.
 *
 * Rows behave the way the folder screen's do — tap opens, long press selects, the trailing icon
 * opens a menu — because a listing of what is filling the volume that cannot act on what it names
 * sends the user off to find the same file somewhere else.
 *
 * What it offers is shorter than the folder screen's: open with, the folder the file sits in, info
 * and delete, with delete the only thing a selection can do. Everything else a file manager does is
 * about where a file lives, and this list is not a place — it spans the whole volume.
 *
 * Deleting is answered here rather than by re-scanning. The rows go, the header's total comes down
 * with them, and the chart the user came from is corrected through
 * [AnalyzerResultsHolder][com.mauriciotogneri.fileexplorer.data.repository.AnalyzerResultsHolder].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerCategoryScreen(
    category: AnalyzerCategory,
    viewModel: AnalyzerCategoryViewModel,
    onCloseClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var fileForActions by remember { mutableStateOf<FileItem?>(null) }

    // Held here rather than per row: building one parses two date patterns.
    val dateFormatter = rememberShortDateFormatter()

    // Reading LocalConfiguration.current triggers recomposition on config changes
    LocalConfiguration.current
    @SuppressLint("LocalContextResourcesRead")
    val resources = context.resources

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AnalyzerCategoryUiEvent.ShowToastRes -> {
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
                }
                is AnalyzerCategoryUiEvent.ShowDeletePartialSuccess -> {
                    val message = resources.getQuantityString(
                        R.plurals.delete_partial_success,
                        event.deleted,
                        event.deleted,
                        event.failed
                    )
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Handle back press in selection mode
    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    // Re-check for a pending APK install after returning from Settings (a no-op when nothing is
    // pending). Nothing else is re-read on resume: the listing describes a scan, not a folder.
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            uiState.pendingApkInstall?.let { pendingApk ->
                if (IntentUtil.canInstallApks(context)) {
                    viewModel.clearPendingApkInstall()
                    IntentUtil.installApk(context, pendingApk, SOURCE)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                SelectionTopAppBar(
                    selectedCount = uiState.selectedCount,
                    allSelected = uiState.allSelected,
                    onClearSelection = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll
                )
            } else {
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
                        if (uiState.files.isNotEmpty()) {
                            IconButton(onClick = viewModel::selectAll) {
                                Icon(
                                    imageVector = Icons.Outlined.SelectAll,
                                    contentDescription = stringResource(R.string.action_select_all)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            SelectionActionBar(
                isSelectionMode = uiState.isSelectionMode,
                onDelete = { viewModel.showDeleteConfirmDialog(uiState.selectedFiles) }
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
                    selectedPaths = uiState.selectedPaths,
                    isSelectionMode = uiState.isSelectionMode,
                    hasMore = uiState.hasMore,
                    dateFormatter = dateFormatter,
                    onLoadNextPage = viewModel::loadNextPage,
                    onClick = { file ->
                        if (uiState.isSelectionMode) {
                            viewModel.toggleSelection(file)
                        } else {
                            when (val result = IntentUtil.openFile(context, file, SOURCE)) {
                                is OpenFileResult.Handled -> { }
                                is OpenFileResult.RequiresUncompress -> viewModel.showUncompressDialog(result.file)
                                is OpenFileResult.RequiresInstallPermission -> viewModel.setPendingApkInstall(result.file)
                                is OpenFileResult.RequiresTextViewer -> context.startActivity(TextViewerActivity.createIntent(context, result.file.path, SOURCE))
                                is OpenFileResult.RequiresImageViewer -> context.startActivity(ImageViewerActivity.createIntent(context, result.file.path, SOURCE))
                            }
                        }
                    },
                    onLongClick = viewModel::toggleSelection,
                    onMenuClick = { file -> fileForActions = file }
                )
            }
        }
    }

    // File actions bottom sheet (always "icon" mode - long press enters selection mode, not bottom sheet)
    fileForActions?.let { file ->
        AnalyzerFileActionsBottomSheet(
            file = file,
            mode = "icon",
            onAction = { action ->
                fileForActions = null
                when (action) {
                    AnalyzerFileAction.OpenWith -> {
                        IntentUtil.openFileWith(context, file, SOURCE)
                    }
                    AnalyzerFileAction.OpenFolder -> {
                        val parentPath = File(file.path).parent ?: return@AnalyzerFileActionsBottomSheet
                        context.startActivity(FolderActivity.createIntent(context, parentPath, File(parentPath).name, parentPath, null))
                    }
                    AnalyzerFileAction.Delete -> {
                        viewModel.showDeleteConfirmDialog(listOf(file))
                    }
                    AnalyzerFileAction.Info -> {
                        context.startActivity(ItemInfoActivity.createIntent(context, file.path))
                    }
                }
            },
            onDismiss = { fileForActions = null }
        )
    }

    // Delete confirm dialog
    if (uiState.itemsToDelete.isNotEmpty()) {
        DeleteConfirmDialog(
            itemCount = uiState.itemsToDelete.size,
            itemName = uiState.itemsToDelete.singleOrNull()?.name,
            onDismiss = { viewModel.dismissDeleteConfirmDialog() },
            onConfirm = { viewModel.onDeleteConfirmed() }
        )
    }

    // Uncompress dialog
    uiState.itemToUncompress?.let {
        if (uiState.isPasswordProtected) {
            PasswordUncompressDialog(
                entryCount = uiState.uncompressEntryCount,
                onDismiss = { viewModel.dismissUncompressDialog() },
                onExtract = { password -> viewModel.confirmUncompress(password) }
            )
        } else {
            UncompressDialog(
                entryCount = uiState.uncompressEntryCount,
                onDismiss = { viewModel.dismissUncompressDialog() },
                onExtract = { viewModel.confirmUncompress() }
            )
        }
    }

    // Uncompress progress dialog
    uiState.uncompressProgress?.let { progress ->
        UncompressProgressDialog(
            progress = progress,
            onCancel = { viewModel.cancelUncompression() }
        )
    }

    // APK permission dialog
    uiState.pendingApkInstall?.let {
        ApkPermissionDialog(
            source = SOURCE,
            onDismiss = { viewModel.clearPendingApkInstall() },
            onOpenSettings = {
                IntentUtil.openInstallPermissionSettings(context)
            }
        )
    }
}

@Composable
private fun FileList(
    files: List<FileItem>,
    selectedPaths: Set<String>,
    isSelectionMode: Boolean,
    hasMore: Boolean,
    dateFormatter: ShortDateFormatter,
    onLoadNextPage: () -> Unit,
    onClick: (FileItem) -> Unit,
    onLongClick: (FileItem) -> Unit,
    onMenuClick: (FileItem) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = files,
            key = { it.path }
        ) { file ->
            FileListItem(
                file = file,
                onClick = { onClick(file) },
                onLongClick = { onLongClick(file) },
                onMenuClick = { onMenuClick(file) },
                isSelected = file.path in selectedPaths,
                isSelectionMode = isSelectionMode,
                dateFormatter = dateFormatter,
                // Nothing here is a folder, so no row is waiting on a child count.
                loadsChildCounts = false
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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

/**
 * What a selection can do here: delete it.
 *
 * The folder screen's bar offers eight actions because a folder is where a file is managed. This
 * list is a report on a volume, and the one thing it is read to decide is what to get rid of.
 */
@Composable
private fun SelectionActionBar(
    isSelectionMode: Boolean,
    onDelete: () -> Unit
) {
    if (!isSelectionMode) return

    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.action_delete),
                onClick = onDelete
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopAppBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(
                    R.plurals.selection_count,
                    selectedCount,
                    selectedCount
                ),
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.content_description_clear_selection)
                )
            }
        },
        actions = {
            if (!allSelected) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Outlined.SelectAll,
                        contentDescription = stringResource(R.string.action_select_all)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
