package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.AboutActivity
import com.mauriciotogneri.fileexplorer.activities.SearchActivity
import com.mauriciotogneri.fileexplorer.activities.SettingsActivity
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.ui.components.HomeSearchBar
import com.mauriciotogneri.fileexplorer.ui.components.LocationsSection
import com.mauriciotogneri.fileexplorer.ui.components.RecentFilesSection
import com.mauriciotogneri.fileexplorer.ui.components.StoragesSection
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeScreen(
    onNavigateToFolder: (path: String, title: String?) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh data when screen becomes visible
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadData()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.drawer_settings)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.drawer_about)) },
                    selected = false,
                    onClick = {
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
                        onSearchClick = {
                            context.startActivity(Intent(context, SearchActivity::class.java))
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    RecentFilesSection(
                        recentFiles = uiState.recentFiles,
                        onFileClick = { recentFile ->
                            openRecentFile(context, recentFile)
                        }
                    )

                    if (uiState.recentFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    LocationsSection(
                        locations = uiState.locations,
                        onLocationClick = { location, title ->
                            onNavigateToFolder(location.path, title)
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    StoragesSection(
                        storages = uiState.storages,
                        onStorageClick = { storage ->
                            onNavigateToFolder(storage.path, storage.displayName)
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // TODO: Remove this preview section after choosing an icon
                    DownloadIconPreviewSection()

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DownloadIconPreviewSection() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Download Icon Alternatives",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconPreviewRow("Download", Icons.Outlined.Download)
            IconPreviewRow("FileDownload", Icons.Outlined.FileDownload)
            IconPreviewRow("DownloadForOffline", Icons.Outlined.DownloadForOffline)
            IconPreviewRow("CloudDownload", Icons.Outlined.CloudDownload)
            IconPreviewRow("GetApp", Icons.Outlined.GetApp)
            IconPreviewRow("SaveAlt", Icons.Outlined.SaveAlt)
            IconPreviewRow("MoveToInbox", Icons.Outlined.MoveToInbox)
            IconPreviewRow("ArrowDownward", Icons.Outlined.ArrowDownward)
            IconPreviewRow("ArrowCircleDown", Icons.Outlined.ArrowCircleDown)
        }
    }
}

@Composable
private fun IconPreviewRow(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(180.dp)
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
            RecentFilesRepository(context).addRecentFile(file)
        }
    } catch (e: Exception) {
        // Could not open file
    }
}
