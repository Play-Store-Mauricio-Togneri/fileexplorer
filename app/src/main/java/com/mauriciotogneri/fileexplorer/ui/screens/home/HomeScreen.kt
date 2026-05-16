package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.AboutActivity
import com.mauriciotogneri.fileexplorer.activities.FeedbackActivity
import com.mauriciotogneri.fileexplorer.activities.ItemInfoActivity
import com.mauriciotogneri.fileexplorer.activities.SearchActivity
import com.mauriciotogneri.fileexplorer.activities.SettingsActivity
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.ui.components.BadgeDot
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.HomeSearchBar
import com.mauriciotogneri.fileexplorer.ui.components.LocationsSection
import com.mauriciotogneri.fileexplorer.ui.components.RecentFileAction
import com.mauriciotogneri.fileexplorer.ui.components.RecentFileActionsBottomSheet
import com.mauriciotogneri.fileexplorer.ui.components.RecentFilesSection
import com.mauriciotogneri.fileexplorer.ui.components.StoragesSection
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToFolder: (path: String, title: String?, rootPath: String?, rootDisplayName: String?) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val showMenuBadge by viewModel.showMenuBadge.collectAsState()
    val showSettingsBadge by viewModel.showSettingsBadge.collectAsState()
    val showFeedbackBadge by viewModel.showFeedbackBadge.collectAsState()
    val showAboutBadge by viewModel.showAboutBadge.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            viewModel.dismissMenuBadge()
        }
    }

    // Refresh data when screen becomes visible
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadData()
        }
    }

    // Show delete error toast
    LaunchedEffect(uiState.showDeleteError) {
        if (uiState.showDeleteError) {
            Toast.makeText(context, context.getString(R.string.delete_error), Toast.LENGTH_SHORT).show()
            viewModel.dismissDeleteError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                NavigationDrawerItem(
                    icon = {
                        BadgeDot(showBadge = showSettingsBadge) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.drawer_settings)) },
                    selected = false,
                    onClick = {
                        viewModel.dismissSettingsBadge()
                        scope.launch { drawerState.close() }
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = {
                        BadgeDot(showBadge = showFeedbackBadge) {
                            Icon(
                                imageVector = Icons.Outlined.Feedback,
                                contentDescription = null
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.drawer_feedback)) },
                    selected = false,
                    onClick = {
                        viewModel.dismissFeedbackBadge()
                        scope.launch { drawerState.close() }
                        context.startActivity(Intent(context, FeedbackActivity::class.java))
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = {
                        BadgeDot(showBadge = showAboutBadge) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.drawer_about)) },
                    selected = false,
                    onClick = {
                        viewModel.dismissAboutBadge()
                        scope.launch { drawerState.close() }
                        context.startActivity(Intent(context, AboutActivity::class.java))
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        HomeSearchBar(
                            onMenuClick = {
                                scope.launch { drawerState.open() }
                            },
                            onSearchClick = {
                                context.startActivity(Intent(context, SearchActivity::class.java))
                            },
                            showMenuBadge = showMenuBadge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        RecentFilesSection(
                            recentFiles = uiState.recentFiles,
                            onFileClick = { recentFile ->
                                openRecentFile(context, recentFile)
                            },
                            onMenuClick = { recentFile ->
                                val fileExists = viewModel.showRecentFileActions(recentFile)
                                if (!fileExists) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.recent_file_not_found),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )

                        if (uiState.recentFiles.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        LocationsSection(
                            locations = uiState.locations,
                            onLocationClick = { location, title ->
                                onNavigateToFolder(location.path, title, location.path, null)
                            }
                        )

                        if (uiState.locations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        StoragesSection(
                            storages = uiState.storages,
                            onStorageClick = { storage ->
                                onNavigateToFolder(storage.path, storage.displayName, storage.path, storage.displayName)
                            }
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    val recentTitle = stringResource(R.string.section_recent)
    uiState.selectedRecentFile?.let { recentFile ->
        RecentFileActionsBottomSheet(
            onAction = { action ->
                when (action) {
                    RecentFileAction.OpenWith -> {
                        viewModel.dismissRecentFileActions()
                        val fileItem = FileItem(
                            path = recentFile.path,
                            name = recentFile.name,
                            isDirectory = false,
                            size = 0,
                            lastModified = 0,
                            createdTime = 0,
                            mimeType = recentFile.mimeType
                        )
                        IntentUtil.openFileWith(context, fileItem)
                    }
                    RecentFileAction.OpenFolder -> {
                        viewModel.dismissRecentFileActions()
                        val parentPath = File(recentFile.path).parent ?: return@RecentFileActionsBottomSheet
                        onNavigateToFolder(parentPath, recentTitle, parentPath, null)
                    }
                    RecentFileAction.Share -> {
                        viewModel.dismissRecentFileActions()
                        val fileItem = FileItem(
                            path = recentFile.path,
                            name = recentFile.name,
                            isDirectory = false,
                            size = 0,
                            lastModified = 0,
                            createdTime = 0,
                            mimeType = recentFile.mimeType
                        )
                        IntentUtil.shareFiles(context, listOf(fileItem))
                    }
                    RecentFileAction.RemoveFromRecents -> {
                        viewModel.removeFromRecents(recentFile)
                    }
                    RecentFileAction.Delete -> {
                        viewModel.showDeleteConfirmation(recentFile)
                    }
                    RecentFileAction.Info -> {
                        viewModel.dismissRecentFileActions()
                        context.startActivity(ItemInfoActivity.createIntent(context, recentFile.path))
                    }
                }
            },
            onDismiss = { viewModel.dismissRecentFileActions() }
        )
    }

    uiState.recentFileToDelete?.let { recentFile ->
        DeleteConfirmDialog(
            itemCount = 1,
            itemName = recentFile.name,
            onDismiss = { viewModel.dismissDeleteConfirmation() },
            onConfirm = { viewModel.confirmDeleteRecentFile() }
        )
    }
}

private fun openRecentFile(context: android.content.Context, recentFile: RecentFile) {
    val file = File(recentFile.path)
    if (!file.exists()) return

    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, recentFile.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)

        // Update recent files
        kotlinx.coroutines.MainScope().launch {
            RecentFilesRepository(context.recentFilesDataStore).addRecentFile(file)
        }
    } catch (e: Exception) {
        ErrorReporter.error(e, "open_recent_file")
        Toast.makeText(context, R.string.open_file_error, Toast.LENGTH_SHORT).show()
    }
}
