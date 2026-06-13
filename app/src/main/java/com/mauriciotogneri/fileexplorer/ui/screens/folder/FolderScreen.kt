package com.mauriciotogneri.fileexplorer.ui.screens.folder

import android.app.Application
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.ui.components.ActionBar
import com.mauriciotogneri.fileexplorer.ui.components.ApkPermissionDialog
import com.mauriciotogneri.fileexplorer.ui.components.BadgeDot
import com.mauriciotogneri.fileexplorer.ui.components.Breadcrumbs
import com.mauriciotogneri.fileexplorer.ui.components.CompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.CompressProgressDialog
import com.mauriciotogneri.fileexplorer.ui.components.PasswordUncompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.UncompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.UncompressProgressDialog
import com.mauriciotogneri.fileexplorer.ui.components.CreateFolderDialog
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.DeleteProgressDialog
import com.mauriciotogneri.fileexplorer.ui.components.EmptyState
import com.mauriciotogneri.fileexplorer.ui.components.RestrictedEmptyState
import com.mauriciotogneri.fileexplorer.ui.components.FileAction
import com.mauriciotogneri.fileexplorer.ui.components.FileActionsBottomSheet
import com.mauriciotogneri.fileexplorer.ui.components.FullWidthDragHandle
import com.mauriciotogneri.fileexplorer.ui.components.OperationProgressDialog
import com.mauriciotogneri.fileexplorer.ui.components.RenameDialog
import com.mauriciotogneri.fileexplorer.activities.ItemInfoActivity
import com.mauriciotogneri.fileexplorer.activities.ImageViewerActivity
import com.mauriciotogneri.fileexplorer.activities.TextViewerActivity
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.components.SwipeableFileListItem
import com.mauriciotogneri.fileexplorer.ui.screens.picker.DestinationPicker
import com.mauriciotogneri.fileexplorer.ui.theme.MenuItemTextStyle
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.OpenFileResult
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    path: String,
    title: String? = null,
    rootPath: String? = null,
    rootDisplayName: String? = null,
    onNavigateToFolder: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: FolderViewModel = viewModel(
        key = path,
        factory = FolderViewModel.Factory(LocalContext.current.applicationContext as Application, path, title)
    )
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val childCounts by viewModel.childCounts.collectAsStateWithLifecycle()
    val showContextMenuBadge by viewModel.showFolderContextMenuBadge.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var fileForActions by remember { mutableStateOf<FileItem?>(null) }

    val fileRepository = remember { FileRepository() }
    val storageRepository = remember { StorageRepository(AndroidStorageSource(context)) }

    // Reading LocalConfiguration.current triggers recomposition on config changes
    LocalConfiguration.current
    @SuppressLint("LocalContextResourcesRead")
    val resources = context.resources

    LaunchedEffect(Unit) {
        AnalyticsTracker.trackScreenFolder()
    }

    // Handle UI events
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is FolderUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is FolderUiEvent.ShowToastRes -> {
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
                }
                is FolderUiEvent.ShowDeletePartialSuccess -> {
                    val message = resources.getQuantityString(
                        R.plurals.delete_partial_success,
                        event.deleted,
                        event.deleted,
                        event.failed
                    )
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                is FolderUiEvent.ShareFiles -> {
                    IntentUtil.shareFiles(context, event.files)
                }
            }
        }
    }

    // Handle back press in selection mode
    BackHandler(enabled = state.isSelectionMode) {
        viewModel.clearSelection()
    }

    // Refresh files when RETURNING to this screen (back from a child folder, a viewer, or Settings).
    // The ViewModel owns the "skip the first resume" guard (see onScreenResumed), because it
    // outlives the composition across child-folder navigation. Also re-check for a pending APK
    // install after returning from Settings (a no-op when nothing is pending).
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            viewModel.onScreenResumed()
            state.pendingApkInstall?.let { pendingApk ->
                if (IntentUtil.canInstallApks(context)) {
                    viewModel.clearPendingApkInstall()
                    IntentUtil.installApk(context, pendingApk, "folder")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                SelectionTopAppBar(
                    selectedCount = state.selectedCount,
                    allSelected = state.allSelected,
                    onClearSelection = {
                        AnalyticsTracker.trackFolderToolbarUnselectAll()
                        viewModel.clearSelection()
                    },
                    onSelectAll = {
                        AnalyticsTracker.trackFolderToolbarSelectAll()
                        viewModel.selectAll()
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            AnalyticsTracker.trackFolderBackButtonTapped()
                            onNavigateBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            AnalyticsTracker.trackFolderContextMenuOpened()
                            viewModel.dismissFolderContextMenuBadge()
                            showMenu = true
                        }) {
                            BadgeDot(showBadge = showContextMenuBadge, offset = 3.dp) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.content_description_more_options)
                                )
                            }
                        }
                        FolderMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            allSelected = state.allSelected,
                            hasFiles = state.files.isNotEmpty(),
                            showHidden = state.showHidden,
                            onSelectAll = {
                                AnalyticsTracker.trackFolderContextMenuSelectAll()
                                viewModel.selectAll()
                                showMenu = false
                            },
                            onUnselectAll = {
                                viewModel.clearSelection()
                                showMenu = false
                            },
                            onSortBy = {
                                AnalyticsTracker.trackFolderContextMenuSortBy()
                                showMenu = false
                                showSortBottomSheet = true
                            },
                            onNewFolder = {
                                AnalyticsTracker.trackFolderContextMenuNewFolder()
                                showMenu = false
                                viewModel.showCreateFolderDialog()
                            },
                            onToggleHidden = {
                                if (state.showHidden) {
                                    AnalyticsTracker.trackFolderContextMenuHideHiddenItems()
                                } else {
                                    AnalyticsTracker.trackFolderContextMenuShowHiddenItems()
                                }
                                showMenu = false
                                viewModel.toggleHiddenFiles()
                            }
                        )
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
            ActionBar(
                state = state,
                onAction = { action -> viewModel.onAction(action) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Breadcrumbs
            Breadcrumbs(
                currentPath = state.currentPath,
                onNavigateToPath = onNavigateToFolder,
                rootPath = rootPath,
                rootDisplayName = rootDisplayName
            )

            // File list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    state.isLoading && state.files.isEmpty() -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    state.error != null && state.files.isEmpty() -> {
                        Text(
                            text = state.error.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    state.files.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isCurrentFolderRestricted) {
                                RestrictedEmptyState()
                            } else {
                                EmptyState()
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = state.files,
                                key = { it.path }
                            ) { file ->
                                val childCount = childCounts[file.path]
                                val isRestricted = file.path in childCounts && childCount == null
                                // Memoize the copy so streaming count updates don't re-allocate
                                // every visible row on each emission (only when its count changes).
                                val displayFile = remember(file, childCount) {
                                    file.copy(childCount = childCount)
                                }
                                SwipeableFileListItem(
                                    file = displayFile,
                                    isRestricted = isRestricted,
                                    isSelected = file.path in state.selectedPaths,
                                    onClick = {
                                        if (state.isSelectionMode) {
                                            viewModel.toggleSelection(file)
                                        } else if (file.isDirectory) {
                                            AnalyticsTracker.trackFolderTappedToOpen()
                                            onNavigateToFolder(file.path)
                                        } else {
                                            when (val result = IntentUtil.openFile(context, file, "folder")) {
                                                is OpenFileResult.Handled -> { }
                                                is OpenFileResult.RequiresUncompress -> viewModel.showUncompressDialog(result.file)
                                                is OpenFileResult.RequiresInstallPermission -> viewModel.setPendingApkInstall(result.file)
                                                is OpenFileResult.RequiresTextViewer -> context.startActivity(TextViewerActivity.createIntent(context, result.file.path, "folder"))
                                                is OpenFileResult.RequiresImageViewer -> context.startActivity(ImageViewerActivity.createIntent(context, result.file.path, "folder"))
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!state.isSelectionMode) {
                                            AnalyticsTracker.trackFolderLongPressedToSelect()
                                        }
                                        viewModel.toggleSelection(file)
                                    },
                                    onMenuClick = {
                                        fileForActions = file
                                    },
                                    onDelete = {
                                        viewModel.showDeleteConfirmDialog(listOf(file))
                                    },
                                    onRename = {
                                        viewModel.showRenameDialog(file)
                                    }
                                )
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Sort bottom sheet
    if (showSortBottomSheet) {
        SortBottomSheet(
            currentSortMode = state.sortMode,
            onSortModeSelected = { sortMode ->
                AnalyticsTracker.trackFolderSortBySelected(sortMode.name)
                viewModel.setSortMode(sortMode)
                showSortBottomSheet = false
            },
            onDismiss = { showSortBottomSheet = false }
        )
    }

    // Create folder dialog
    if (state.showCreateFolderDialog) {
        CreateFolderDialog(
            existingNames = state.existingFileNames,
            onDismiss = { viewModel.dismissCreateFolderDialog() },
            onCreate = { name -> viewModel.onCreateFolder(name) }
        )
    }

    // File actions bottom sheet (always "icon" mode - long press enters selection mode, not bottom sheet)
    fileForActions?.let { file ->
        FileActionsBottomSheet(
            file = file,
            mode = "icon",
            onAction = { action ->
                fileForActions = null
                when (action) {
                    FileAction.Select -> viewModel.toggleSelection(file)
                    FileAction.Share -> {
                        IntentUtil.shareFiles(context, listOf(file))
                    }
                    FileAction.OpenWith -> {
                        IntentUtil.openFileWith(context, file, "folder")
                    }
                    FileAction.Compress -> {
                        viewModel.showCompressDialog(listOf(file))
                    }
                    FileAction.Uncompress -> {
                        viewModel.showUncompressDialog(file)
                    }
                    FileAction.MoveTo -> {
                        viewModel.toggleSelection(file)
                        viewModel.onAction(com.mauriciotogneri.fileexplorer.data.model.FileAction.MoveTo)
                    }
                    FileAction.CopyTo -> {
                        viewModel.toggleSelection(file)
                        viewModel.onAction(com.mauriciotogneri.fileexplorer.data.model.FileAction.CopyTo)
                    }
                    FileAction.Rename -> {
                        viewModel.showRenameDialog(file)
                    }
                    FileAction.Delete -> {
                        viewModel.showDeleteConfirmDialog(listOf(file))
                    }
                    FileAction.Info -> {
                        context.startActivity(ItemInfoActivity.createIntent(context, file.path))
                    }
                }
            },
            onDismiss = { fileForActions = null }
        )
    }

    // Rename dialog
    state.itemToRename?.let { file ->
        RenameDialog(
            file = file,
            existingNames = state.existingFileNames,
            onDismiss = { viewModel.dismissRenameDialog() },
            onRename = { newName -> viewModel.onRename(newName) }
        )
    }

    // Delete confirm dialog
    if (state.itemsToDelete.isNotEmpty()) {
        DeleteConfirmDialog(
            itemCount = state.itemsToDelete.size,
            itemName = state.itemsToDelete.singleOrNull()?.name,
            onDismiss = { viewModel.dismissDeleteConfirmDialog() },
            onConfirm = { viewModel.onDeleteConfirmed() }
        )
    }

    // Compress dialog
    if (state.itemsToCompress.isNotEmpty()) {
        CompressDialog(
            existingNames = state.existingFileNames,
            onDismiss = { viewModel.dismissCompressDialog() },
            onCompress = { zipName -> viewModel.onCompress(zipName) }
        )
    }

    // Compress progress dialog
    state.compressProgress?.let { progress ->
        CompressProgressDialog(
            progress = progress,
            onCancel = { viewModel.cancelCompression() }
        )
    }

    // Uncompress dialog
    state.itemToUncompress?.let {
        if (state.isPasswordProtected) {
            PasswordUncompressDialog(
                entryCount = state.uncompressEntryCount,
                onDismiss = { viewModel.dismissUncompressDialog() },
                onExtract = { password -> viewModel.confirmUncompress(password) }
            )
        } else {
            UncompressDialog(
                entryCount = state.uncompressEntryCount,
                onDismiss = { viewModel.dismissUncompressDialog() },
                onExtract = { viewModel.confirmUncompress() }
            )
        }
    }

    // Uncompress progress dialog
    state.uncompressProgress?.let { progress ->
        UncompressProgressDialog(
            progress = progress,
            onCancel = { viewModel.cancelUncompression() }
        )
    }

    // Delete progress dialog
    state.deleteProgress?.let { progress ->
        DeleteProgressDialog(
            progress = progress,
            onCancel = { viewModel.cancelDelete() }
        )
    }

    // Operation progress dialog (move/copy)
    state.operationProgress?.let { progress ->
        OperationProgressDialog(
            progress = progress,
            onCancel = { viewModel.cancelOperation() }
        )
    }

    // APK permission dialog
    state.pendingApkInstall?.let {
        ApkPermissionDialog(
            source = "folder",
            onDismiss = { viewModel.clearPendingApkInstall() },
            onOpenSettings = {
                IntentUtil.openInstallPermissionSettings(context)
            }
        )
    }

    // Destination picker overlay
    AnimatedVisibility(
        visible = state.pickerRequest != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        state.pickerRequest?.let { request ->
            DestinationPicker(
                request = request,
                sortMode = state.sortMode,
                showHidden = state.showHidden,
                fileRepository = fileRepository,
                storageRepository = storageRepository,
                onConfirm = { targetPath ->
                    viewModel.executeOperation(targetPath)
                },
                onCancel = {
                    viewModel.dismissPicker()
                }
            )
        }
    }
}

@Composable
private fun FolderMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    allSelected: Boolean,
    hasFiles: Boolean,
    showHidden: Boolean,
    onSelectAll: () -> Unit,
    onUnselectAll: () -> Unit,
    onSortBy: () -> Unit,
    onNewFolder: () -> Unit,
    onToggleHidden: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        // Select all / Unselect all
        if (hasFiles) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (allSelected) {
                            stringResource(R.string.action_unselect_all)
                        } else {
                            stringResource(R.string.action_select_all)
                        },
                        style = MenuItemTextStyle
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (allSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                        contentDescription = if (allSelected) {
                            stringResource(R.string.action_unselect_all)
                        } else {
                            stringResource(R.string.action_select_all)
                        }
                    )
                },
                onClick = if (allSelected) onUnselectAll else onSelectAll
            )
        }

        // Sort by
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_sort_by), style = MenuItemTextStyle) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = stringResource(R.string.menu_sort_by)
                )
            },
            onClick = onSortBy
        )

        // Show/hide hidden files
        DropdownMenuItem(
            text = {
                Text(
                    text = if (showHidden) {
                        stringResource(R.string.hide_hidden_items)
                    } else {
                        stringResource(R.string.show_hidden_items)
                    },
                    style = MenuItemTextStyle
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (showHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showHidden) {
                        stringResource(R.string.hide_hidden_items)
                    } else {
                        stringResource(R.string.show_hidden_items)
                    }
                )
            },
            onClick = onToggleHidden
        )

        // New folder
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_create_folder), style = MenuItemTextStyle) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.CreateNewFolder,
                    contentDescription = stringResource(R.string.action_create_folder)
                )
            },
            onClick = onNewFolder
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    currentSortMode: SortMode,
    onSortModeSelected: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { FullWidthDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            SortOptionItem(
                text = stringResource(R.string.sort_name_asc),
                isSelected = currentSortMode == SortMode.NAME_ASC,
                onClick = { onSortModeSelected(SortMode.NAME_ASC) }
            )
            SortOptionItem(
                text = stringResource(R.string.sort_name_desc),
                isSelected = currentSortMode == SortMode.NAME_DESC,
                onClick = { onSortModeSelected(SortMode.NAME_DESC) }
            )
            SortOptionItem(
                text = stringResource(R.string.sort_size_asc),
                isSelected = currentSortMode == SortMode.SIZE_ASC,
                onClick = { onSortModeSelected(SortMode.SIZE_ASC) }
            )
            SortOptionItem(
                text = stringResource(R.string.sort_size_desc),
                isSelected = currentSortMode == SortMode.SIZE_DESC,
                onClick = { onSortModeSelected(SortMode.SIZE_DESC) }
            )
            SortOptionItem(
                text = stringResource(R.string.sort_date_asc),
                isSelected = currentSortMode == SortMode.DATE_ASC,
                onClick = { onSortModeSelected(SortMode.DATE_ASC) }
            )
            SortOptionItem(
                text = stringResource(R.string.sort_date_desc),
                isSelected = currentSortMode == SortMode.DATE_DESC,
                onClick = { onSortModeSelected(SortMode.DATE_DESC) }
            )
        }
    }
}

@Composable
private fun SortOptionItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MenuItemTextStyle,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        },
        onClick = onClick
    )
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
