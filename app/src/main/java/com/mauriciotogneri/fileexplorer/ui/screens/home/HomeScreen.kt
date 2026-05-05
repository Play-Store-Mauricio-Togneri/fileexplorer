package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
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
    onNavigateToSearch: () -> Unit,
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
                // Placeholder drawer content - to be specified later
                Text(
                    text = stringResource(R.string.drawer_placeholder),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
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
                        onSearchClick = onNavigateToSearch,
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
                }
            }
        }
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
