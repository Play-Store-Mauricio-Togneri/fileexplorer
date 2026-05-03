package com.mauriciotogneri.fileexplorer.ui.screens.folder

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.ui.components.ActionBar
import com.mauriciotogneri.fileexplorer.ui.components.EmptyState
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarContainer
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarContent
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    path: String,
    onNavigateToFolder: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: FolderViewModel = viewModel(
        key = path,
        factory = FolderViewModel.Factory(path)
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard by viewModel.clipboard.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    // Handle UI events
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is FolderUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
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
                            text = state.currentPath,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null
                            )
                        }
                        SortMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            currentSortMode = state.sortMode,
                            showHidden = state.showHidden,
                            onSortModeSelected = { sortMode ->
                                viewModel.setSortMode(sortMode)
                                showMenu = false
                            },
                            onToggleHidden = {
                                viewModel.toggleHiddenFiles()
                                showMenu = false
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppBarContainer,
                        titleContentColor = AppBarContent,
                        navigationIconContentColor = AppBarContent,
                        actionIconContentColor = AppBarContent
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                                FileListItem(
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
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    currentSortMode: SortMode,
    showHidden: Boolean,
    onSortModeSelected: (SortMode) -> Unit,
    onToggleHidden: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        SortMenuItem(
            text = stringResource(R.string.sort_name_asc),
            isSelected = currentSortMode == SortMode.NAME_ASC,
            onClick = { onSortModeSelected(SortMode.NAME_ASC) }
        )
        SortMenuItem(
            text = stringResource(R.string.sort_name_desc),
            isSelected = currentSortMode == SortMode.NAME_DESC,
            onClick = { onSortModeSelected(SortMode.NAME_DESC) }
        )
        SortMenuItem(
            text = stringResource(R.string.sort_size_asc),
            isSelected = currentSortMode == SortMode.SIZE_ASC,
            onClick = { onSortModeSelected(SortMode.SIZE_ASC) }
        )
        SortMenuItem(
            text = stringResource(R.string.sort_size_desc),
            isSelected = currentSortMode == SortMode.SIZE_DESC,
            onClick = { onSortModeSelected(SortMode.SIZE_DESC) }
        )
        SortMenuItem(
            text = stringResource(R.string.sort_date_asc),
            isSelected = currentSortMode == SortMode.DATE_ASC,
            onClick = { onSortModeSelected(SortMode.DATE_ASC) }
        )
        SortMenuItem(
            text = stringResource(R.string.sort_date_desc),
            isSelected = currentSortMode == SortMode.DATE_DESC,
            onClick = { onSortModeSelected(SortMode.DATE_DESC) }
        )

        HorizontalDivider()

        DropdownMenuItem(
            text = {
                Text(
                    text = if (showHidden) {
                        stringResource(R.string.hide_hidden_files)
                    } else {
                        stringResource(R.string.show_hidden_files)
                    }
                )
            },
            onClick = onToggleHidden
        )
    }
}

@Composable
private fun SortMenuItem(
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
                    imageVector = Icons.Default.Close,
                    contentDescription = null
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AppBarContainer,
            titleContentColor = AppBarContent,
            navigationIconContentColor = AppBarContent
        )
    )
}
