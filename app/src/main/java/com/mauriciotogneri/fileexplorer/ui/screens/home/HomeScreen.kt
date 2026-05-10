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
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.ContactSupport
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilePresent
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LinkedCamera
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.Wallpaper
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

                    // TODO: Remove this preview section after choosing icons
                    IconAlternativesSection()

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun IconAlternativesSection() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        IconCategorySection("Menu Icon Alternatives") {
            IconPreviewRow("Menu", Icons.Outlined.Menu)
            IconPreviewRow("MenuOpen", Icons.AutoMirrored.Outlined.MenuOpen)
            IconPreviewRow("MoreVert", Icons.Outlined.MoreVert)
            IconPreviewRow("MoreHoriz", Icons.Outlined.MoreHoriz)
            IconPreviewRow("Dehaze", Icons.Outlined.Dehaze)
            IconPreviewRow("Reorder", Icons.Outlined.Reorder)
        }

        IconCategorySection("Search Icon Alternatives") {
            IconPreviewRow("Search", Icons.Outlined.Search)
            IconPreviewRow("ManageSearch", Icons.AutoMirrored.Outlined.ManageSearch)
            IconPreviewRow("FindInPage", Icons.Outlined.FindInPage)
            IconPreviewRow("TravelExplore", Icons.Outlined.TravelExplore)
            IconPreviewRow("ImageSearch", Icons.Outlined.ImageSearch)
            IconPreviewRow("Troubleshoot", Icons.Outlined.Troubleshoot)
        }

        IconCategorySection("Download Icon Alternatives") {
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

        IconCategorySection("Settings Icon Alternatives") {
            IconPreviewRow("Settings", Icons.Outlined.Settings)
            IconPreviewRow("Tune", Icons.Outlined.Tune)
            IconPreviewRow("Build", Icons.Outlined.Build)
            IconPreviewRow("DisplaySettings", Icons.Outlined.DisplaySettings)
            IconPreviewRow("ManageAccounts", Icons.Outlined.ManageAccounts)
            IconPreviewRow("AdminPanelSettings", Icons.Outlined.AdminPanelSettings)
        }

        IconCategorySection("Info Icon Alternatives") {
            IconPreviewRow("Info", Icons.Outlined.Info)
            IconPreviewRow("Help", Icons.AutoMirrored.Outlined.Help)
            IconPreviewRow("HelpOutline", Icons.AutoMirrored.Outlined.HelpOutline)
            IconPreviewRow("QuestionMark", Icons.Outlined.QuestionMark)
            IconPreviewRow("Lightbulb", Icons.Outlined.Lightbulb)
            IconPreviewRow("ContactSupport", Icons.AutoMirrored.Outlined.ContactSupport)
        }

        IconCategorySection("PDF Icon Alternatives") {
            IconPreviewRow("PictureAsPdf", Icons.Outlined.PictureAsPdf)
            IconPreviewRow("Article", Icons.AutoMirrored.Outlined.Article)
            IconPreviewRow("Description", Icons.Outlined.Description)
            IconPreviewRow("FileCopy", Icons.Outlined.FileCopy)
        }

        IconCategorySection("Audio Icon Alternatives") {
            IconPreviewRow("AudioFile", Icons.Outlined.AudioFile)
            IconPreviewRow("MusicNote", Icons.Outlined.MusicNote)
            IconPreviewRow("Headphones", Icons.Outlined.Headphones)
            IconPreviewRow("VolumeUp", Icons.AutoMirrored.Outlined.VolumeUp)
            IconPreviewRow("GraphicEq", Icons.Outlined.GraphicEq)
        }

        IconCategorySection("Video Icon Alternatives") {
            IconPreviewRow("VideoFile", Icons.Outlined.VideoFile)
            IconPreviewRow("Movie", Icons.Outlined.Movie)
            IconPreviewRow("Videocam", Icons.Outlined.Videocam)
            IconPreviewRow("PlayCircle", Icons.Outlined.PlayCircle)
            IconPreviewRow("OndemandVideo", Icons.Outlined.OndemandVideo)
        }

        IconCategorySection("Document Icon Alternatives") {
            IconPreviewRow("Description", Icons.Outlined.Description)
            IconPreviewRow("Article", Icons.AutoMirrored.Outlined.Article)
            IconPreviewRow("Subject", Icons.AutoMirrored.Outlined.Subject)
            IconPreviewRow("Notes", Icons.AutoMirrored.Outlined.Notes)
            IconPreviewRow("TextSnippet", Icons.AutoMirrored.Outlined.TextSnippet)
        }

        IconCategorySection("Generic File Icon Alternatives") {
            IconPreviewRow("InsertDriveFile", Icons.AutoMirrored.Outlined.InsertDriveFile)
            IconPreviewRow("FileCopy", Icons.Outlined.FileCopy)
            IconPreviewRow("FilePresent", Icons.Outlined.FilePresent)
            IconPreviewRow("AttachFile", Icons.Outlined.AttachFile)
            IconPreviewRow("Note", Icons.AutoMirrored.Outlined.Note)
        }

        IconCategorySection("Camera Icon Alternatives") {
            IconPreviewRow("PhotoCamera", Icons.Outlined.PhotoCamera)
            IconPreviewRow("CameraAlt", Icons.Outlined.CameraAlt)
            IconPreviewRow("LinkedCamera", Icons.Outlined.LinkedCamera)
            IconPreviewRow("AddAPhoto", Icons.Outlined.AddAPhoto)
            IconPreviewRow("PhotoLibrary", Icons.Outlined.PhotoLibrary)
        }

        IconCategorySection("Image Icon Alternatives") {
            IconPreviewRow("Image", Icons.Outlined.Image)
            IconPreviewRow("Photo", Icons.Outlined.Photo)
            IconPreviewRow("Collections", Icons.Outlined.Collections)
            IconPreviewRow("Panorama", Icons.Outlined.Panorama)
            IconPreviewRow("Wallpaper", Icons.Outlined.Wallpaper)
        }

        IconCategorySection("Screenshot Icon Alternatives") {
            IconPreviewRow("ScreenshotMonitor", Icons.Outlined.ScreenshotMonitor)
            IconPreviewRow("Crop", Icons.Outlined.Crop)
            IconPreviewRow("ContentCopy", Icons.Outlined.ContentCopy)
            IconPreviewRow("Fullscreen", Icons.Outlined.Fullscreen)
        }

        IconCategorySection("Podcasts Icon Alternatives") {
            IconPreviewRow("Podcasts", Icons.Outlined.Podcasts)
            IconPreviewRow("Mic", Icons.Outlined.Mic)
            IconPreviewRow("RecordVoiceOver", Icons.Outlined.RecordVoiceOver)
            IconPreviewRow("Radio", Icons.Outlined.Radio)
            IconPreviewRow("Hearing", Icons.Outlined.Hearing)
        }

        IconCategorySection("Phone/Internal Storage Icon Alternatives") {
            IconPreviewRow("Smartphone", Icons.Outlined.Smartphone)
            IconPreviewRow("DevicesOther", Icons.Outlined.DevicesOther)
            IconPreviewRow("Memory", Icons.Outlined.Memory)
        }

        IconCategorySection("SD Card Icon Alternatives") {
            IconPreviewRow("SdCard", Icons.Outlined.SdCard)
            IconPreviewRow("Save", Icons.Outlined.Save)
            IconPreviewRow("Album", Icons.Outlined.Album)
            IconPreviewRow("SimCard", Icons.Outlined.SimCard)
        }

        IconCategorySection("Storage Icon Alternatives") {
            IconPreviewRow("Storage", Icons.Outlined.Storage)
            IconPreviewRow("Inventory2", Icons.Outlined.Inventory2)
            IconPreviewRow("Dns", Icons.Outlined.Dns)
            IconPreviewRow("FolderOpen", Icons.Outlined.FolderOpen)
        }
    }
}

@Composable
private fun IconCategorySection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
    }
    Spacer(modifier = Modifier.height(24.dp))
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
