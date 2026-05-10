package com.mauriciotogneri.fileexplorer.ui.screens.folder

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.components.ActionBar
import com.mauriciotogneri.fileexplorer.ui.components.Breadcrumbs
import com.mauriciotogneri.fileexplorer.ui.components.CreateFolderDialog
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.EmptyState
import com.mauriciotogneri.fileexplorer.ui.components.FileAction
import com.mauriciotogneri.fileexplorer.ui.components.FileActionsBottomSheet
import com.mauriciotogneri.fileexplorer.ui.components.FileInfoDialog
import com.mauriciotogneri.fileexplorer.ui.components.RenameDialog
import com.mauriciotogneri.fileexplorer.ui.components.SwipeableFileListItem
import com.mauriciotogneri.fileexplorer.ui.theme.appBarContainer
import com.mauriciotogneri.fileexplorer.ui.theme.appBarContent
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    path: String,
    title: String? = null,
    onNavigateToFolder: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: FolderViewModel = viewModel(
        key = path,
        factory = FolderViewModel.Factory(path, title)
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard by viewModel.clipboard.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var fileForActions by remember { mutableStateOf<FileItem?>(null) }

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
                is FolderUiEvent.ShareFiles -> {
                    val shared = IntentUtil.shareFiles(context, event.files)
                    if (!shared) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.share_files_unable),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // Handle back press in selection mode
    BackHandler(enabled = state.isSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                SelectionTopAppBar(
                    selectedCount = state.selectedCount,
                    onClearSelection = { viewModel.clearSelection() }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = state.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = null
                            )
                        }
                        FolderMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            allSelected = state.allSelected,
                            hasFiles = state.files.isNotEmpty(),
                            showHidden = state.showHidden,
                            onSelectAll = {
                                viewModel.selectAll()
                                showMenu = false
                            },
                            onUnselectAll = {
                                viewModel.clearSelection()
                                showMenu = false
                            },
                            onSortBy = {
                                showMenu = false
                                showSortBottomSheet = true
                            },
                            onNewFolder = {
                                showMenu = false
                                viewModel.showCreateFolderDialog()
                            },
                            onToggleHidden = {
                                showMenu = false
                                viewModel.toggleHiddenFiles()
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = appBarContainer,
                        titleContentColor = appBarContent,
                        navigationIconContentColor = appBarContent,
                        actionIconContentColor = appBarContent
                    )
                )
            }
        },
        bottomBar = {
            ActionBar(
                state = state,
                clipboard = clipboard,
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
                onNavigateToPath = onNavigateToFolder
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
                            text = state.error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    state.files.isEmpty() -> {
                        EmptyState()
                    }

                    else -> {
                        PullToRefreshBox(
                            isRefreshing = state.isLoading,
                            onRefresh = { viewModel.refresh() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = state.files,
                                    key = { it.path }
                                ) { file ->
                                    SwipeableFileListItem(
                                        file = file,
                                        isSelected = file.path in state.selectedPaths,
                                        onClick = {
                                            if (state.isSelectionMode) {
                                                viewModel.toggleSelection(file)
                                            } else if (file.isDirectory) {
                                                onNavigateToFolder(file.path)
                                            } else {
                                                val opened = IntentUtil.openFile(context, file)
                                                if (!opened) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.open_unable),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleSelection(file)
                                        },
                                        onMenuClick = {
                                            fileForActions = file
                                        },
                                        onDelete = {
                                            viewModel.showDeleteConfirmDialog(file)
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
    }

    // Sort bottom sheet
    if (showSortBottomSheet) {
        SortBottomSheet(
            currentSortMode = state.sortMode,
            onSortModeSelected = { sortMode ->
                viewModel.setSortMode(sortMode)
                showSortBottomSheet = false
            },
            onDismiss = { showSortBottomSheet = false }
        )
    }

    // Create folder dialog
    if (state.showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { viewModel.dismissCreateFolderDialog() },
            onCreate = { name -> viewModel.onCreateFolder(name) }
        )
    }

    // File actions bottom sheet
    fileForActions?.let { file ->
        FileActionsBottomSheet(
            file = file,
            onAction = { action ->
                fileForActions = null
                when (action) {
                    FileAction.Select -> viewModel.toggleSelection(file)
                    FileAction.Share -> {
                        val shared = IntentUtil.shareFiles(context, listOf(file))
                        if (!shared) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.share_files_unable),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    FileAction.OpenWith -> {
                        val opened = IntentUtil.openFileWith(context, file)
                        if (!opened) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.open_unable),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    FileAction.Compress -> {
                        // TODO: Implement compress
                    }
                    FileAction.MoveTo -> {
                        // TODO: Implement move to
                    }
                    FileAction.CopyTo -> {
                        // TODO: Implement copy to
                    }
                    FileAction.Rename -> {
                        viewModel.showRenameDialog(file)
                    }
                    FileAction.Delete -> {
                        viewModel.showDeleteConfirmDialog(file)
                    }
                    FileAction.Info -> {
                        viewModel.showInfoDialog(file)
                    }
                }
            },
            onDismiss = { fileForActions = null }
        )
    }

    // File info dialog
    state.infoDialogFile?.let { file ->
        FileInfoDialog(
            file = file,
            onDismiss = { viewModel.dismissInfoDialog() }
        )
    }

    // Rename dialog
    state.itemToRename?.let { file ->
        RenameDialog(
            file = file,
            onDismiss = { viewModel.dismissRenameDialog() },
            onRename = { newName -> viewModel.onRename(newName) }
        )
    }

    // Delete confirm dialog
    state.itemToDelete?.let { file ->
        DeleteConfirmDialog(
            itemName = file.name,
            onDismiss = { viewModel.dismissDeleteConfirmDialog() },
            onConfirm = { viewModel.onDeleteConfirmed() }
        )
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
                        }
                    )
                },
                onClick = if (allSelected) onUnselectAll else onSelectAll
            )
        }

        // Sort by
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_sort_by)) },
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
                    }
                )
            },
            onClick = onToggleHidden
        )

        // New folder
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_create_folder)) },
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
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.menu_sort_by),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

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
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
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
    onClearSelection: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(
                    R.plurals.selection_count,
                    selectedCount,
                    selectedCount
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = appBarContainer,
            titleContentColor = appBarContent,
            navigationIconContentColor = appBarContent
        )
    )
}
