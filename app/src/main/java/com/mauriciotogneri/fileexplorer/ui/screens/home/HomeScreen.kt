package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.AboutActivity
import com.mauriciotogneri.fileexplorer.activities.FeedbackActivity
import com.mauriciotogneri.fileexplorer.activities.FolderActivity
import com.mauriciotogneri.fileexplorer.activities.ItemInfoActivity
import com.mauriciotogneri.fileexplorer.activities.ImageViewerActivity
import com.mauriciotogneri.fileexplorer.activities.TextViewerActivity
import com.mauriciotogneri.fileexplorer.activities.SearchActivity
import com.mauriciotogneri.fileexplorer.activities.SettingsActivity
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.ui.components.ApkPermissionDialog
import com.mauriciotogneri.fileexplorer.ui.components.BadgeDot
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.FavoriteFileAction
import com.mauriciotogneri.fileexplorer.ui.components.FavoriteFileActionsBottomSheet
import com.mauriciotogneri.fileexplorer.ui.components.FavoritesSection
import com.mauriciotogneri.fileexplorer.ui.components.HomeSearchBar
import com.mauriciotogneri.fileexplorer.ui.components.LocationsSection
import com.mauriciotogneri.fileexplorer.ui.components.RecentFileAction
import com.mauriciotogneri.fileexplorer.ui.components.RecentFileActionsBottomSheet
import com.mauriciotogneri.fileexplorer.ui.components.RecentFilesSection
import com.mauriciotogneri.fileexplorer.ui.components.StoragesSection
import com.mauriciotogneri.fileexplorer.ui.components.PasswordUncompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.UncompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.UncompressProgressDialog
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.OpenFileResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(LocalContext.current.applicationContext as Application))
) {
    val uiState by viewModel.uiState.collectAsState()
    val showMenuBadge by viewModel.showMenuBadge.collectAsState()
    val showSettingsBadge by viewModel.showSettingsBadge.collectAsState()
    val showFeedbackBadge by viewModel.showFeedbackBadge.collectAsState()
    val showAboutBadge by viewModel.showAboutBadge.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val recentFilesListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val deleteErrorMessage = stringResource(R.string.delete_error)

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            viewModel.dismissMenuBadge()
            AnalyticsTracker.trackHomeDrawerOpened()
        }
    }

    // Refresh data when screen becomes visible and check for pending APK install
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadData()
            // Auto-retry APK install if permission was granted
            viewModel.uiState.value.pendingApkInstall?.let { pendingApk ->
                if (IntentUtil.canInstallApks(context)) {
                    viewModel.clearPendingApkInstall()
                    IntentUtil.installApk(context, pendingApk, "recent")
                }
            }
        }
    }

    // Show delete error toast
    LaunchedEffect(uiState.showDeleteError) {
        if (uiState.showDeleteError) {
            Toast.makeText(context, deleteErrorMessage, Toast.LENGTH_SHORT).show()
            viewModel.dismissDeleteError()
        }
    }

    LaunchedEffect(Unit) {
        AnalyticsTracker.trackScreenHome()
    }

    // Handle UI events
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeUiEvent.ShowToast -> {
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
                }
            }
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
                                contentDescription = stringResource(R.string.drawer_settings)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.drawer_settings)) },
                    selected = false,
                    onClick = {
                        AnalyticsTracker.trackHomeDrawerSettingsTapped()
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
                                contentDescription = stringResource(R.string.drawer_feedback)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.drawer_feedback)) },
                    selected = false,
                    onClick = {
                        AnalyticsTracker.trackHomeDrawerFeedbackTapped()
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
                                contentDescription = stringResource(R.string.drawer_about)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.drawer_about)) },
                    selected = false,
                    onClick = {
                        AnalyticsTracker.trackHomeDrawerAboutTapped()
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    HomeSearchBar(
                        onMenuClick = {
                            scope.launch { drawerState.open() }
                        },
                        onSearchContainerClick = {
                            AnalyticsTracker.trackHomeSearchContainerTapped()
                            context.startActivity(Intent(context, SearchActivity::class.java))
                        },
                        onSearchIconClick = {
                            AnalyticsTracker.trackHomeSearchIconTapped()
                            context.startActivity(Intent(context, SearchActivity::class.java))
                        },
                        showMenuBadge = showMenuBadge,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    RecentFilesSection(
                        recentFiles = uiState.recentFiles,
                        onFileClick = { recentFile ->
                            openRecentFile(
                                context = context,
                                recentFile = recentFile,
                                onUncompressRequired = { file ->
                                    viewModel.showUncompressDialog(file)
                                },
                                onInstallPermissionRequired = { file ->
                                    viewModel.setPendingApkInstall(file)
                                }
                            )
                        },
                        onMenuClick = { recentFile, mode ->
                            AnalyticsTracker.trackHomeRecentFileContextMenuOpened()
                            viewModel.showRecentFileActions(recentFile, mode)
                        },
                        lazyListState = recentFilesListState
                    )

                    if (uiState.recentFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    FavoritesSection(
                        favorites = uiState.favorites,
                        onFileClick = { favorite ->
                            openFavorite(
                                context = context,
                                favorite = favorite,
                                onUncompressRequired = { file ->
                                    viewModel.showUncompressDialog(file)
                                },
                                onInstallPermissionRequired = { file ->
                                    viewModel.setPendingApkInstall(file)
                                }
                            )
                        },
                        onMenuClick = { favorite, mode ->
                            AnalyticsTracker.trackHomeFavoriteContextMenuOpened()
                            viewModel.showFavoriteActions(favorite, mode)
                        }
                    )

                    if (uiState.favorites.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    LocationsSection(
                        locations = uiState.locations,
                        onLocationClick = { location, title ->
                            AnalyticsTracker.trackHomeLocationCardOpened(location.type.name.lowercase())
                            context.startActivity(FolderActivity.createIntent(context, location.path, title, location.path, null))
                        }
                    )

                    if (uiState.locations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    StoragesSection(
                        storages = uiState.storages,
                        onStorageClick = { storage ->
                            AnalyticsTracker.trackHomeStorageCardOpened(storage.analyticsType)
                            context.startActivity(FolderActivity.createIntent(context, storage.path, storage.displayName, storage.path, storage.displayName))
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    val recentTitle = stringResource(R.string.section_recent)
    uiState.selectedRecentFile?.let { recentFile ->
        RecentFileActionsBottomSheet(
            recentFile = recentFile,
            mode = uiState.recentFileMode,
            isFavorite = uiState.favoritePaths.contains(recentFile.path),
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
                        IntentUtil.openFileWith(context, fileItem, "recent")
                    }
                    RecentFileAction.OpenFolder -> {
                        viewModel.dismissRecentFileActions()
                        val parentPath = File(recentFile.path).parent ?: return@RecentFileActionsBottomSheet
                        context.startActivity(FolderActivity.createIntent(context, parentPath, recentTitle, parentPath, null))
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
                    RecentFileAction.AddToFavorites -> {
                        viewModel.dismissRecentFileActions()
                        viewModel.addRecentToFavorites(recentFile)
                    }
                    RecentFileAction.RemoveFromFavorites -> {
                        viewModel.dismissRecentFileActions()
                        viewModel.removeRecentFromFavorites(recentFile)
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

    val favoritesTitle = stringResource(R.string.section_favorites)
    uiState.selectedFavorite?.let { favorite ->
        FavoriteFileActionsBottomSheet(
            favorite = favorite,
            mode = uiState.favoriteFileMode,
            onAction = { action ->
                when (action) {
                    FavoriteFileAction.OpenWith -> {
                        viewModel.dismissFavoriteActions()
                        val fileItem = FileItem(
                            path = favorite.path,
                            name = favorite.name,
                            isDirectory = false,
                            size = 0,
                            lastModified = 0,
                            createdTime = 0,
                            mimeType = favorite.mimeType
                        )
                        IntentUtil.openFileWith(context, fileItem, "favorite")
                    }
                    FavoriteFileAction.Share -> {
                        viewModel.dismissFavoriteActions()
                        val fileItem = FileItem(
                            path = favorite.path,
                            name = favorite.name,
                            isDirectory = false,
                            size = 0,
                            lastModified = 0,
                            createdTime = 0,
                            mimeType = favorite.mimeType
                        )
                        IntentUtil.shareFiles(context, listOf(fileItem))
                    }
                    FavoriteFileAction.OpenFolder -> {
                        viewModel.dismissFavoriteActions()
                        val parentPath = File(favorite.path).parent ?: return@FavoriteFileActionsBottomSheet
                        context.startActivity(FolderActivity.createIntent(context, parentPath, favoritesTitle, parentPath, null))
                    }
                    FavoriteFileAction.RemoveFromFavorites -> {
                        viewModel.removeFromFavorites(favorite)
                    }
                    FavoriteFileAction.Delete -> {
                        viewModel.showFavoriteDeleteConfirmation(favorite)
                    }
                    FavoriteFileAction.Info -> {
                        viewModel.dismissFavoriteActions()
                        context.startActivity(ItemInfoActivity.createIntent(context, favorite.path))
                    }
                }
            },
            onDismiss = { viewModel.dismissFavoriteActions() }
        )
    }

    uiState.favoriteToDelete?.let { favorite ->
        DeleteConfirmDialog(
            itemCount = 1,
            itemName = favorite.name,
            onDismiss = { viewModel.dismissFavoriteDeleteConfirmation() },
            onConfirm = { viewModel.confirmDeleteFavorite() }
        )
    }

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

    uiState.uncompressProgress?.let { progress ->
        UncompressProgressDialog(
            progress = progress,
            onCancel = { viewModel.cancelUncompression() }
        )
    }

    // APK permission dialog
    uiState.pendingApkInstall?.let {
        ApkPermissionDialog(
            source = "recent",
            onDismiss = { viewModel.clearPendingApkInstall() },
            onOpenSettings = {
                IntentUtil.openInstallPermissionSettings(context)
            }
        )
    }
}

private fun openRecentFile(
    context: Context,
    recentFile: RecentFile,
    onUncompressRequired: (FileItem) -> Unit,
    onInstallPermissionRequired: (FileItem) -> Unit
) {
    val file = File(recentFile.path)
    if (!file.exists()) {
        Toast.makeText(context, R.string.recent_file_not_found, Toast.LENGTH_SHORT).show()
        return
    }

    val fileItem = FileItem(
        path = recentFile.path,
        name = recentFile.name,
        isDirectory = false,
        size = 0,
        lastModified = 0,
        createdTime = 0,
        mimeType = recentFile.mimeType
    )

    openFileItem(context, fileItem, "recent", onUncompressRequired, onInstallPermissionRequired)
}

private fun openFavorite(
    context: Context,
    favorite: Favorite,
    onUncompressRequired: (FileItem) -> Unit,
    onInstallPermissionRequired: (FileItem) -> Unit
) {
    val file = File(favorite.path)
    if (!file.exists()) {
        Toast.makeText(context, R.string.recent_file_not_found, Toast.LENGTH_SHORT).show()
        return
    }

    if (favorite.isDirectory) {
        context.startActivity(FolderActivity.createIntent(context, favorite.path, favorite.name, favorite.path, null))
        return
    }

    val fileItem = FileItem(
        path = favorite.path,
        name = favorite.name,
        isDirectory = false,
        size = 0,
        lastModified = 0,
        createdTime = 0,
        mimeType = favorite.mimeType
    )

    openFileItem(context, fileItem, "favorite", onUncompressRequired, onInstallPermissionRequired)
}

private fun openFileItem(
    context: Context,
    fileItem: FileItem,
    source: String,
    onUncompressRequired: (FileItem) -> Unit,
    onInstallPermissionRequired: (FileItem) -> Unit
) {
    when (val result = IntentUtil.openFile(context, fileItem, source)) {
        is OpenFileResult.Handled -> { }
        is OpenFileResult.RequiresUncompress -> {
            onUncompressRequired(result.file)
        }
        is OpenFileResult.RequiresInstallPermission -> {
            onInstallPermissionRequired(result.file)
        }
        is OpenFileResult.RequiresTextViewer -> {
            context.startActivity(TextViewerActivity.createIntent(context, result.file.path, source))
        }
        is OpenFileResult.RequiresImageViewer -> {
            context.startActivity(ImageViewerActivity.createIntent(context, result.file.path, source))
        }
    }
}
