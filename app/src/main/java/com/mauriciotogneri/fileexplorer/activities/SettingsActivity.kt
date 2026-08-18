package com.mauriciotogneri.fileexplorer.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.PickerRequest
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.components.BadgeDot
import com.mauriciotogneri.fileexplorer.ui.screens.picker.DestinationPicker
import com.mauriciotogneri.fileexplorer.ui.screens.settings.SettingsViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarTitleStyle
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AnalyticsTracker.trackScreenSettings()

        setContent {
            val context = LocalContext.current
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(context)
            )
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)
            val enabledLocations by viewModel.enabledLocations.collectAsState(initial = LocationType.entries.toSet())
            val availableLocationTypes by viewModel.availableLocationTypes.collectAsState()
            val isLoadingLocations by viewModel.isLoadingLocations.collectAsState()
            val recentFilesEnabled by viewModel.recentFilesEnabled.collectAsState(initial = true)
            val showHidden by viewModel.showHidden.collectAsState(initial = false)
            val hasRecentFiles by viewModel.hasRecentFiles.collectAsState()
            val hasFavorites by viewModel.hasFavorites.collectAsState()
            val showLocationsBadge by viewModel.showLocationsBadge.collectAsState()
            val showStartupBadge by viewModel.showStartupBadge.collectAsState()
            val showThemeBadge by viewModel.showThemeBadge.collectAsState()
            val showFolderSecondLineBadge by viewModel.showFolderSecondLineBadge.collectAsState()
            val showFileSecondLineBadge by viewModel.showFileSecondLineBadge.collectAsState()
            val folderSecondLine by viewModel.folderSecondLine.collectAsState(initial = FolderSecondLine.ITEM_COUNT)
            val fileSecondLine by viewModel.fileSecondLine.collectAsState(initial = FileSecondLine.SIZE)
            val startupScreen by viewModel.startupScreen.collectAsState(initial = StartupScreen.HOME)
            val startupFolderName by viewModel.startupFolderName.collectAsState()
            val sortMode by SortManager.sortMode.collectAsState()

            // Held here rather than inside SettingsScreen so that composable stays stateless and the
            // picker's repositories do not leak into its signature.
            var startupFolderPicker by remember { mutableStateOf<PickerRequest?>(null) }
            val fileRepository = remember { FileRepository() }
            val storageRepository = remember { StorageRepository(AndroidStorageSource(context)) }

            FileExplorerTheme(themeMode = themeMode) {
                // FileExplorerTheme emits no layout node of its own, so the picker overlay needs an
                // explicit container to be drawn over the settings list rather than beside it.
                Box(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = viewModel::setThemeMode,
                        startupScreen = startupScreen,
                        startupFolderName = startupFolderName,
                        onStartupHomeSelected = viewModel::setStartupHome,
                        onStartupFolderSelected = {
                            startupFolderPicker = PickerRequest(items = emptyList(), mode = null)
                        },
                        enabledLocations = enabledLocations,
                        availableLocationTypes = availableLocationTypes,
                        isLoadingLocations = isLoadingLocations,
                        onEnabledLocationsSave = viewModel::setEnabledLocations,
                        showHidden = showHidden,
                        onShowHiddenChange = viewModel::setShowHidden,
                        folderSecondLine = folderSecondLine,
                        onFolderSecondLineChange = viewModel::setFolderSecondLine,
                        fileSecondLine = fileSecondLine,
                        onFileSecondLineChange = viewModel::setFileSecondLine,
                        recentFilesEnabled = recentFilesEnabled,
                        hasRecentFiles = hasRecentFiles,
                        onRecentFilesEnabledChange = viewModel::setRecentFilesEnabled,
                        onClearRecentFiles = {
                            viewModel.clearRecentFiles()
                            Toast.makeText(context, R.string.settings_recent_files_cleared, Toast.LENGTH_SHORT).show()
                        },
                        hasFavorites = hasFavorites,
                        onClearFavorites = {
                            viewModel.clearFavorites()
                            Toast.makeText(context, R.string.settings_favorite_files_cleared, Toast.LENGTH_SHORT).show()
                        },
                        showLocationsBadge = showLocationsBadge,
                        onLocationsBadgeDismiss = viewModel::dismissLocationsBadge,
                        showStartupBadge = showStartupBadge,
                        onStartupBadgeDismiss = viewModel::dismissStartupBadge,
                        showThemeBadge = showThemeBadge,
                        onThemeBadgeDismiss = viewModel::dismissThemeBadge,
                        showFolderSecondLineBadge = showFolderSecondLineBadge,
                        onFolderSecondLineBadgeDismiss = viewModel::dismissFolderSecondLineBadge,
                        showFileSecondLineBadge = showFileSecondLineBadge,
                        onFileSecondLineBadgeDismiss = viewModel::dismissFileSecondLineBadge,
                        onBackClick = { finish() }
                    )

                    AnimatedVisibility(
                        visible = startupFolderPicker != null,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    ) {
                        startupFolderPicker?.let { request ->
                            DestinationPicker(
                                request = request,
                                sortMode = sortMode,
                                showHidden = showHidden,
                                fileRepository = fileRepository,
                                storageRepository = storageRepository,
                                // The choice is stored only here, on confirm: cancelling leaves the
                                // previous startup screen in place instead of saving a folder screen
                                // with no folder.
                                onConfirm = { folderPath ->
                                    viewModel.setStartupFolder(folderPath)
                                    startupFolderPicker = null
                                },
                                onCancel = { startupFolderPicker = null }
                            )
                        }
                    }
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    startupScreen: StartupScreen,
    startupFolderName: String?,
    onStartupHomeSelected: () -> Unit,
    onStartupFolderSelected: () -> Unit,
    enabledLocations: Set<LocationType>,
    availableLocationTypes: List<LocationType>,
    isLoadingLocations: Boolean,
    onEnabledLocationsSave: (Set<LocationType>) -> Unit,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    folderSecondLine: FolderSecondLine,
    onFolderSecondLineChange: (FolderSecondLine) -> Unit,
    fileSecondLine: FileSecondLine,
    onFileSecondLineChange: (FileSecondLine) -> Unit,
    recentFilesEnabled: Boolean,
    hasRecentFiles: Boolean,
    onRecentFilesEnabledChange: (Boolean) -> Unit,
    onClearRecentFiles: () -> Unit,
    hasFavorites: Boolean,
    onClearFavorites: () -> Unit,
    showLocationsBadge: Boolean,
    onLocationsBadgeDismiss: () -> Unit,
    showStartupBadge: Boolean,
    onStartupBadgeDismiss: () -> Unit,
    showThemeBadge: Boolean,
    onThemeBadgeDismiss: () -> Unit,
    showFolderSecondLineBadge: Boolean,
    onFolderSecondLineBadgeDismiss: () -> Unit,
    showFileSecondLineBadge: Boolean,
    onFileSecondLineBadgeDismiss: () -> Unit,
    onBackClick: () -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLocationsDialog by remember { mutableStateOf(false) }
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showStartupDialog by remember { mutableStateOf(false) }
    var showFolderSecondLineDialog by remember { mutableStateOf(false) }
    var showFileSecondLineDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawer_settings), style = AppBarTitleStyle) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        // Scrollable because the nine rows overflow a short viewport once the labels wrap: at
        // fontScale 1.3, or in a language that expands 30-40% over English. Without it the rows
        // below the fold cannot be reached at all.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            ShowHiddenSettingItem(
                enabled = showHidden,
                onEnabledChange = onShowHiddenChange
            )
            TrackRecentFilesSettingItem(
                enabled = recentFilesEnabled,
                onEnabledChange = onRecentFilesEnabledChange
            )
            ClearRecentFilesSettingItem(
                enabled = recentFilesEnabled && hasRecentFiles,
                onClick = onClearRecentFiles
            )
            ClearFavoritesSettingItem(
                enabled = hasFavorites,
                onClick = { showClearFavoritesDialog = true }
            )
            LocationsSettingItem(
                enabledLocations = enabledLocations,
                availableLocationTypes = availableLocationTypes,
                isLoading = isLoadingLocations,
                showBadge = showLocationsBadge,
                onClick = {
                    onLocationsBadgeDismiss()
                    AnalyticsTracker.trackSettingsLocationsDialogOpened()
                    showLocationsDialog = true
                }
            )
            StartupScreenSettingItem(
                startupScreen = startupScreen,
                folderName = startupFolderName,
                showBadge = showStartupBadge,
                onClick = {
                    onStartupBadgeDismiss()
                    AnalyticsTracker.trackSettingsStartupDialogOpened()
                    showStartupDialog = true
                }
            )
            FolderSecondLineSettingItem(
                secondLine = folderSecondLine,
                showBadge = showFolderSecondLineBadge,
                onClick = {
                    onFolderSecondLineBadgeDismiss()
                    AnalyticsTracker.trackSettingsFolderSecondLineDialogOpened()
                    showFolderSecondLineDialog = true
                }
            )
            FileSecondLineSettingItem(
                secondLine = fileSecondLine,
                showBadge = showFileSecondLineBadge,
                onClick = {
                    onFileSecondLineBadgeDismiss()
                    AnalyticsTracker.trackSettingsFileSecondLineDialogOpened()
                    showFileSecondLineDialog = true
                }
            )
            ThemeSettingItem(
                currentTheme = themeMode,
                showBadge = showThemeBadge,
                onClick = {
                    onThemeBadgeDismiss()
                    AnalyticsTracker.trackSettingsThemeDialogOpened()
                    showThemeDialog = true
                }
            )
        }
    }

    if (showLocationsDialog) {
        LocationsSelectionDialog(
            enabledLocations = enabledLocations,
            availableLocationTypes = availableLocationTypes,
            onSave = onEnabledLocationsSave,
            onDismiss = { showLocationsDialog = false }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = themeMode,
            onThemeSelected = { mode ->
                onThemeModeChange(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showStartupDialog) {
        StartupScreenSelectionDialog(
            startupScreen = startupScreen,
            onHomeSelected = {
                showStartupDialog = false
                onStartupHomeSelected()
            },
            // The folder is chosen next, in the picker; nothing is saved until it is confirmed.
            onFolderSelected = {
                showStartupDialog = false
                onStartupFolderSelected()
            },
            onDismiss = { showStartupDialog = false }
        )
    }

    if (showFolderSecondLineDialog) {
        FolderSecondLineSelectionDialog(
            secondLine = folderSecondLine,
            onSecondLineSelected = { selected ->
                onFolderSecondLineChange(selected)
                showFolderSecondLineDialog = false
            },
            onDismiss = { showFolderSecondLineDialog = false }
        )
    }

    if (showFileSecondLineDialog) {
        FileSecondLineSelectionDialog(
            secondLine = fileSecondLine,
            onSecondLineSelected = { selected ->
                onFileSecondLineChange(selected)
                showFileSecondLineDialog = false
            },
            onDismiss = { showFileSecondLineDialog = false }
        )
    }

    if (showClearFavoritesDialog) {
        ClearFavoritesConfirmDialog(
            onConfirm = {
                showClearFavoritesDialog = false
                onClearFavorites()
            },
            onDismiss = { showClearFavoritesDialog = false }
        )
    }
}

@Composable
internal fun LocationsSettingItem(
    enabledLocations: Set<LocationType>,
    availableLocationTypes: List<LocationType>,
    isLoading: Boolean,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    val enabledCount = enabledLocations.count { it in availableLocationTypes }
    val availableCount = availableLocationTypes.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeDot(showBadge = showBadge) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = stringResource(R.string.settings_locations),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(R.string.settings_locations),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "$enabledCount / $availableCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun ThemeSettingItem(
    currentTheme: ThemeMode,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    val themeLabel = when (currentTheme) {
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeDot(showBadge = showBadge) {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = stringResource(R.string.settings_theme),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = themeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun StartupScreenSettingItem(
    startupScreen: StartupScreen,
    folderName: String?,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    // The folder name is missing only if the two halves of the setting were written apart, which
    // the single-write setter prevents; the home label is then both the honest summary and what the
    // app actually opens.
    val label = if (startupScreen == StartupScreen.FOLDER && folderName != null) {
        folderName
    } else {
        stringResource(R.string.settings_startup_home)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeDot(showBadge = showBadge) {
            Icon(
                // Names the moment rather than the destination, so it stays right whichever option
                // is selected.
                imageVector = Icons.Outlined.RocketLaunch,
                contentDescription = stringResource(R.string.settings_startup),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(R.string.settings_startup),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The label for [secondLine], shared by the row's subtitle and the dialog's options. */
@Composable
internal fun folderSecondLineLabel(secondLine: FolderSecondLine): String = when (secondLine) {
    FolderSecondLine.NONE -> stringResource(R.string.settings_second_line_none)
    FolderSecondLine.ITEM_COUNT -> stringResource(R.string.settings_second_line_item_count)
    FolderSecondLine.LAST_MODIFIED -> stringResource(R.string.settings_second_line_last_modified)
}

/** The label for [secondLine], shared by the row's subtitle and the dialog's options. */
@Composable
internal fun fileSecondLineLabel(secondLine: FileSecondLine): String = when (secondLine) {
    FileSecondLine.NONE -> stringResource(R.string.settings_second_line_none)
    FileSecondLine.SIZE -> stringResource(R.string.settings_second_line_size)
    FileSecondLine.LAST_MODIFIED -> stringResource(R.string.settings_second_line_last_modified)
}

@Composable
internal fun FolderSecondLineSettingItem(
    secondLine: FolderSecondLine,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    SecondLineSettingItem(
        icon = Icons.Outlined.FolderOpen,
        title = stringResource(R.string.settings_folder_second_line),
        value = folderSecondLineLabel(secondLine),
        showBadge = showBadge,
        onClick = onClick
    )
}

@Composable
internal fun FileSecondLineSettingItem(
    secondLine: FileSecondLine,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    SecondLineSettingItem(
        icon = Icons.Outlined.Description,
        title = stringResource(R.string.settings_file_second_line),
        value = fileSecondLineLabel(secondLine),
        showBadge = showBadge,
        onClick = onClick
    )
}

/**
 * The two second-line rows differ only in icon, title and value, so they share a body rather than
 * repeating the layout twice.
 */
@Composable
private fun SecondLineSettingItem(
    icon: ImageVector,
    title: String,
    value: String,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeDot(showBadge = showBadge) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun TrackRecentFilesSettingItem(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnabledChange(!enabled) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_recent_files_enabled),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_recent_files_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.scale(0.85f)
        )
    }
}

@Composable
internal fun ShowHiddenSettingItem(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnabledChange(!enabled) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.show_hidden_items),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_show_hidden_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.scale(0.85f)
        )
    }
}

@Composable
internal fun ClearRecentFilesSettingItem(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.settings_recent_files_clear),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        )
    }
}

@Composable
internal fun ClearFavoritesSettingItem(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.StarBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.settings_favorite_files_clear),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        )
    }
}

@Composable
internal fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == currentTheme,
                                onClick = { onThemeSelected(mode) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentTheme,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                AnalyticsTracker.trackThemeDialogCancelled()
                onDismiss()
            }) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
internal fun StartupScreenSelectionDialog(
    startupScreen: StartupScreen,
    onHomeSelected: () -> Unit,
    onFolderSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_startup),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                StartupScreen.entries.forEach { option ->
                    val label = when (option) {
                        StartupScreen.HOME -> stringResource(R.string.settings_startup_home)
                        StartupScreen.FOLDER -> stringResource(R.string.settings_startup_folder)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == startupScreen,
                                onClick = {
                                    when (option) {
                                        StartupScreen.HOME -> onHomeSelected()
                                        StartupScreen.FOLDER -> onFolderSelected()
                                    }
                                },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == startupScreen,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                AnalyticsTracker.trackStartupDialogCancelled()
                onDismiss()
            }) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
internal fun FolderSecondLineSelectionDialog(
    secondLine: FolderSecondLine,
    onSecondLineSelected: (FolderSecondLine) -> Unit,
    onDismiss: () -> Unit
) {
    SecondLineSelectionDialog(
        title = stringResource(R.string.settings_folder_second_line),
        options = FolderSecondLine.entries,
        selected = secondLine,
        label = { option -> folderSecondLineLabel(option) },
        onSelected = onSecondLineSelected,
        onDismiss = {
            AnalyticsTracker.trackFolderSecondLineDialogCancelled()
            onDismiss()
        },
        onDismissRequest = onDismiss
    )
}

@Composable
internal fun FileSecondLineSelectionDialog(
    secondLine: FileSecondLine,
    onSecondLineSelected: (FileSecondLine) -> Unit,
    onDismiss: () -> Unit
) {
    SecondLineSelectionDialog(
        title = stringResource(R.string.settings_file_second_line),
        options = FileSecondLine.entries,
        selected = secondLine,
        label = { option -> fileSecondLineLabel(option) },
        onSelected = onSecondLineSelected,
        onDismiss = {
            AnalyticsTracker.trackFileSecondLineDialogCancelled()
            onDismiss()
        },
        onDismissRequest = onDismiss
    )
}

/**
 * A radio list over [options], shared by the folder and file dialogs, which differ only in the enum
 * they choose from.
 *
 * [onDismiss] backs the Cancel button and [onDismissRequest] a tap outside, matching the other
 * dialogs on this screen: only the button reports a cancellation.
 */
@Composable
private fun <T> SecondLineSelectionDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = { onSelected(option) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
internal fun ClearFavoritesConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_favorite_files_clear),
                style = MaterialTheme.typography.titleMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.dialog_clear),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
internal fun LocationsSelectionDialog(
    enabledLocations: Set<LocationType>,
    availableLocationTypes: List<LocationType>,
    onSave: (Set<LocationType>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLocations by remember { mutableStateOf(enabledLocations) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_locations),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                availableLocationTypes.forEach { locationType ->
                    val isEnabled = locationType in selectedLocations
                    val label = stringResource(locationType.titleResId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = isEnabled,
                                onValueChange = { enabled ->
                                    selectedLocations = if (enabled) {
                                        selectedLocations + locationType
                                    } else {
                                        selectedLocations - locationType
                                    }
                                },
                                role = Role.Checkbox
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isEnabled,
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    AnalyticsTracker.trackLocationsDialogConfirmed()
                    onSave(selectedLocations)
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                AnalyticsTracker.trackLocationsDialogCancelled()
                onDismiss()
            }) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

