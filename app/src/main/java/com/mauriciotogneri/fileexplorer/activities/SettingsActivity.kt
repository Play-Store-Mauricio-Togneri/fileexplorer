package com.mauriciotogneri.fileexplorer.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.SwipeRight
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.DragIndicator
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.move
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.PickerRequest
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.components.BadgeDot
import com.mauriciotogneri.fileexplorer.ui.components.swipeActionLabel
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
            val showSwipeLeftBadge by viewModel.showSwipeLeftBadge.collectAsState()
            val showSwipeRightBadge by viewModel.showSwipeRightBadge.collectAsState()
            val showHomeSectionsBadge by viewModel.showHomeSectionsBadge.collectAsState()
            val homeSectionOrder by viewModel.homeSectionOrder.collectAsState(initial = HomeSection.DEFAULT_ORDER)
            val folderSecondLine by viewModel.folderSecondLine.collectAsState(initial = FolderSecondLine.ITEM_COUNT)
            val fileSecondLine by viewModel.fileSecondLine.collectAsState(initial = FileSecondLine.SIZE)
            val swipeLeftAction by viewModel.swipeLeftAction.collectAsState(initial = SwipeAction.RENAME)
            val swipeRightAction by viewModel.swipeRightAction.collectAsState(initial = SwipeAction.DELETE)
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
                        swipeLeftAction = swipeLeftAction,
                        onSwipeLeftActionChange = viewModel::setSwipeLeftAction,
                        swipeRightAction = swipeRightAction,
                        onSwipeRightActionChange = viewModel::setSwipeRightAction,
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
                        showSwipeLeftBadge = showSwipeLeftBadge,
                        onSwipeLeftBadgeDismiss = viewModel::dismissSwipeLeftBadge,
                        showSwipeRightBadge = showSwipeRightBadge,
                        onSwipeRightBadgeDismiss = viewModel::dismissSwipeRightBadge,
                        homeSectionOrder = homeSectionOrder,
                        onHomeSectionOrderSave = viewModel::setHomeSectionOrder,
                        showHomeSectionsBadge = showHomeSectionsBadge,
                        onHomeSectionsBadgeDismiss = viewModel::dismissHomeSectionsBadge,
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
    swipeLeftAction: SwipeAction,
    onSwipeLeftActionChange: (SwipeAction) -> Unit,
    swipeRightAction: SwipeAction,
    onSwipeRightActionChange: (SwipeAction) -> Unit,
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
    showSwipeLeftBadge: Boolean,
    onSwipeLeftBadgeDismiss: () -> Unit,
    showSwipeRightBadge: Boolean,
    onSwipeRightBadgeDismiss: () -> Unit,
    homeSectionOrder: List<HomeSection>,
    onHomeSectionOrderSave: (List<HomeSection>) -> Unit,
    showHomeSectionsBadge: Boolean,
    onHomeSectionsBadgeDismiss: () -> Unit,
    onBackClick: () -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLocationsDialog by remember { mutableStateOf(false) }
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showStartupDialog by remember { mutableStateOf(false) }
    var showFolderSecondLineDialog by remember { mutableStateOf(false) }
    var showFileSecondLineDialog by remember { mutableStateOf(false) }
    var showSwipeLeftDialog by remember { mutableStateOf(false) }
    var showSwipeRightDialog by remember { mutableStateOf(false) }
    var showHomeSectionsDialog by remember { mutableStateOf(false) }

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
        // Scrollable because the twelve rows overflow a short viewport once the labels wrap: at
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
            HomeSectionsSettingItem(
                order = homeSectionOrder,
                showBadge = showHomeSectionsBadge,
                onClick = {
                    onHomeSectionsBadgeDismiss()
                    AnalyticsTracker.trackSettingsHomeSectionsDialogOpened()
                    showHomeSectionsDialog = true
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
            SwipeLeftActionSettingItem(
                action = swipeLeftAction,
                showBadge = showSwipeLeftBadge,
                onClick = {
                    onSwipeLeftBadgeDismiss()
                    AnalyticsTracker.trackSettingsSwipeLeftDialogOpened()
                    showSwipeLeftDialog = true
                }
            )
            SwipeRightActionSettingItem(
                action = swipeRightAction,
                showBadge = showSwipeRightBadge,
                onClick = {
                    onSwipeRightBadgeDismiss()
                    AnalyticsTracker.trackSettingsSwipeRightDialogOpened()
                    showSwipeRightDialog = true
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

    if (showHomeSectionsDialog) {
        HomeSectionsOrderDialog(
            order = homeSectionOrder,
            onSave = onHomeSectionOrderSave,
            onDismiss = { showHomeSectionsDialog = false }
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

    if (showSwipeLeftDialog) {
        SwipeLeftActionSelectionDialog(
            action = swipeLeftAction,
            onActionSelected = { selected ->
                onSwipeLeftActionChange(selected)
                showSwipeLeftDialog = false
            },
            onDismiss = { showSwipeLeftDialog = false }
        )
    }

    if (showSwipeRightDialog) {
        SwipeRightActionSelectionDialog(
            action = swipeRightAction,
            onActionSelected = { selected ->
                onSwipeRightActionChange(selected)
                showSwipeRightDialog = false
            },
            onDismiss = { showSwipeRightDialog = false }
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
                imageVector = Icons.Outlined.Category,
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

@Composable
internal fun HomeSectionsSettingItem(
    order: List<HomeSection>,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    // The whole arrangement, ellipsised by the subtitle when it does not fit. What survives the
    // truncation is the front of the list, which is the half of an arrangement a user reads it for.
    val label = order.map { section -> stringResource(section.titleResId) }.joinToString(", ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeDot(showBadge = showBadge) {
            Icon(
                imageVector = Icons.Outlined.SwapVert,
                contentDescription = stringResource(R.string.settings_home_sections),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(R.string.settings_home_sections),
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

/**
 * The drag handle of [section]. Shared with the instrumentation tests, which have no other way in:
 * the handle carries no content description, since dragging is the only way to reorder.
 */
internal fun homeSectionHandleTag(section: HomeSection): String = "home_section_handle_${section.name}"

/** The row of [section], whose position and height the instrumentation tests read. */
internal fun homeSectionRowTag(section: HomeSection): String = "home_section_row_${section.name}"

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
    ValueSettingItem(
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
    ValueSettingItem(
        icon = Icons.Outlined.Description,
        title = stringResource(R.string.settings_file_second_line),
        value = fileSecondLineLabel(secondLine),
        showBadge = showBadge,
        onClick = onClick
    )
}

@Composable
internal fun SwipeLeftActionSettingItem(
    action: SwipeAction,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    ValueSettingItem(
        icon = Icons.Outlined.SwipeLeft,
        title = stringResource(R.string.settings_swipe_left),
        value = swipeActionLabel(action),
        showBadge = showBadge,
        onClick = onClick
    )
}

@Composable
internal fun SwipeRightActionSettingItem(
    action: SwipeAction,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    ValueSettingItem(
        icon = Icons.Outlined.SwipeRight,
        title = stringResource(R.string.settings_swipe_right),
        value = swipeActionLabel(action),
        showBadge = showBadge,
        onClick = onClick
    )
}

/**
 * The rows that open a picker differ only in icon, title and the value they currently hold, so they
 * share a body rather than repeating the layout four times.
 */
@Composable
private fun ValueSettingItem(
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
    OptionSelectionDialog(
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
    OptionSelectionDialog(
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

@Composable
internal fun SwipeLeftActionSelectionDialog(
    action: SwipeAction,
    onActionSelected: (SwipeAction) -> Unit,
    onDismiss: () -> Unit
) {
    OptionSelectionDialog(
        title = stringResource(R.string.settings_swipe_left),
        options = SwipeAction.entries,
        selected = action,
        label = { option -> swipeActionLabel(option) },
        onSelected = onActionSelected,
        onDismiss = {
            AnalyticsTracker.trackSwipeLeftDialogCancelled()
            onDismiss()
        },
        onDismissRequest = onDismiss
    )
}

@Composable
internal fun SwipeRightActionSelectionDialog(
    action: SwipeAction,
    onActionSelected: (SwipeAction) -> Unit,
    onDismiss: () -> Unit
) {
    OptionSelectionDialog(
        title = stringResource(R.string.settings_swipe_right),
        options = SwipeAction.entries,
        selected = action,
        label = { option -> swipeActionLabel(option) },
        onSelected = onActionSelected,
        onDismiss = {
            AnalyticsTracker.trackSwipeRightDialogCancelled()
            onDismiss()
        },
        onDismissRequest = onDismiss
    )
}

/**
 * A radio list over [options], shared by the second-line and swipe-action dialogs, which differ only
 * in the enum they choose from.
 *
 * [onDismiss] backs the Cancel button and [onDismissRequest] a tap outside, matching the other
 * dialogs on this screen: only the button reports a cancellation.
 */
@Composable
private fun <T> OptionSelectionDialog(
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


/**
 * Arranges the home screen's sections by dragging their handles.
 *
 * Edits a local copy and hands it over only on Save, so dismissing leaves the stored arrangement
 * untouched. Every section is listed whether or not it currently has anything to show: this is
 * where the order is decided, and what makes a section appear is decided elsewhere.
 */
@Composable
internal fun HomeSectionsOrderDialog(
    order: List<HomeSection>,
    onSave: (List<HomeSection>) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(order) }
    var draggedSection by remember { mutableStateOf<HomeSection?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Measured per row rather than once for the list. The rows are only interchangeable while every
    // translated label fits on one line: at the largest font scales the longest of them wraps, and
    // the dialog's content slot does not scroll, so it can also squeeze a row that no longer fits.
    // A row whose height is not known yet is simply never swapped past.
    val rowHeights = remember { mutableStateMapOf<HomeSection, Int>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_home_sections),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                draft.forEach { section ->
                    // Keyed by section so a row's gesture detector follows the section that was
                    // grabbed rather than the slot it started in. A swap mid-drag moves the
                    // composable, and unkeyed rows are reused by position, which would leave the
                    // finger dragging whichever section had just taken the slot.
                    key(section) {
                        HomeSectionDragRow(
                            section = section,
                            isDragged = draggedSection == section,
                            dragOffset = dragOffset,
                            onMeasured = { height ->
                                // Zero means the row was squeezed rather than measured, and a zero
                                // height would make its swap threshold meaningless.
                                if (height > 0) {
                                    rowHeights[section] = height
                                }
                            },
                            // Ignored while another row is already held. Every row runs its own
                            // gesture detector, so a second finger would otherwise repoint the drag
                            // at its own section and leave the first finger steering it.
                            onDragStart = {
                                if (draggedSection == null) {
                                    draggedSection = section
                                    dragOffset = 0f
                                }
                            },
                            onDrag = { delta ->
                                val draggedHeight = rowHeights[section]

                                if (draggedSection == section && draggedHeight != null) {
                                    dragOffset += delta

                                    // The row stays where the finger put it: each swap hands back
                                    // exactly the height of the row just crossed, so the arrangement
                                    // depends on where the finger is rather than on how it got
                                    // there, and over-dragging past either end costs nothing but the
                                    // travel back. Looped because one drag event can cross several
                                    // rows when the finger moves fast.
                                    //
                                    // A swap fires once the dragged row's centre reaches its
                                    // neighbour's — half of the two heights, which cannot swap back
                                    // in the same pass whichever row is the taller.
                                    var current = draft.indexOf(section)
                                    var swapped = true

                                    while (swapped) {
                                        swapped = false
                                        val below = draft.getOrNull(current + 1)?.let { rowHeights[it] }
                                        val above = draft.getOrNull(current - 1)?.let { rowHeights[it] }

                                        if (below != null && dragOffset > (draggedHeight + below) / 2f) {
                                            draft = draft.move(current, current + 1)
                                            dragOffset -= below
                                            current++
                                            swapped = true
                                        } else if (above != null && dragOffset < -(draggedHeight + above) / 2f) {
                                            draft = draft.move(current, current - 1)
                                            dragOffset += above
                                            current--
                                            swapped = true
                                        }
                                    }
                                }
                            },
                            onDragStop = {
                                if (draggedSection == section) {
                                    draggedSection = null
                                    dragOffset = 0f
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    AnalyticsTracker.trackHomeSectionsDialogConfirmed()
                    onSave(draft)
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
                AnalyticsTracker.trackHomeSectionsDialogCancelled()
                onDismiss()
            }) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/**
 * One draggable row of [HomeSectionsOrderDialog].
 *
 * Carries no padding of its own, so the height it reports is also the distance between two settled
 * rows — the figure the caller's swap threshold is measured against.
 */
@Composable
private fun HomeSectionDragRow(
    section: HomeSection,
    isDragged: Boolean,
    dragOffset: Float,
    onMeasured: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStop: () -> Unit
) {
    // Tracks the finger exactly while held, then springs to the settled position on release, which
    // after the last swap is up to half a row away.
    val translation by animateFloatAsState(
        targetValue = if (isDragged) dragOffset else 0f,
        animationSpec = if (isDragged) snap() else spring(),
        label = "home_section_translation"
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragged) 6.dp else 0.dp,
        label = "home_section_elevation"
    )

    Surface(
        // primaryContainer rather than a surface token: the dialog's own container is
        // surfaceContainerHigh, which surfaceVariant sits within two steps of in the dark
        // palette, leaving the held row indistinguishable there while it reads clearly in
        // light. The shadow cannot make up the difference on a dark surface.
        color = if (isDragged) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shadowElevation = elevation,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(homeSectionRowTag(section))
            // Drawn over its neighbours, which it overlaps for most of a drag.
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer { translationY = translation }
            .onSizeChanged { size -> onMeasured(size.height) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.DragIndicator,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .testTag(homeSectionHandleTag(section))
                    // Sized before the gesture and padded after it, so the whole 48dp target drags
                    // while the icon itself stays at 24dp.
                    .size(48.dp)
                    .pointerInput(section) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragStop() },
                            onDragCancel = { onDragStop() },
                            onDrag = { change, dragAmount ->
                                // Claimed here so a vertical drag reorders rather than scrolling the
                                // dialog, which becomes scrollable at large font scales.
                                change.consume()
                                onDrag(dragAmount.y)
                            }
                        )
                    }
                    .padding(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(section.titleResId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
