package com.mauriciotogneri.fileexplorer.activities

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.ui.components.BadgeDot
import com.mauriciotogneri.fileexplorer.ui.screens.settings.SettingsViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarTitleStyle
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(context)
            )
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)
            val enabledLocations by viewModel.enabledLocations.collectAsState(initial = LocationType.entries.toSet())
            val recentFilesEnabled by viewModel.recentFilesEnabled.collectAsState(initial = true)
            val showLocationsBadge by viewModel.showLocationsBadge.collectAsState()
            val showThemeBadge by viewModel.showThemeBadge.collectAsState()
            val showRecentFilesBadge by viewModel.showRecentFilesBadge.collectAsState()

            FileExplorerTheme(themeMode = themeMode) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = viewModel::setThemeMode,
                    enabledLocations = enabledLocations,
                    onEnabledLocationsSave = viewModel::setEnabledLocations,
                    recentFilesEnabled = recentFilesEnabled,
                    onRecentFilesEnabledChange = viewModel::setRecentFilesEnabled,
                    onClearRecentFiles = {
                        viewModel.clearRecentFiles()
                        Toast.makeText(context, R.string.settings_recent_files_cleared, Toast.LENGTH_SHORT).show()
                    },
                    showRecentFilesBadge = showRecentFilesBadge,
                    onRecentFilesBadgeDismiss = viewModel::dismissRecentFilesBadge,
                    showLocationsBadge = showLocationsBadge,
                    onLocationsBadgeDismiss = viewModel::dismissLocationsBadge,
                    showThemeBadge = showThemeBadge,
                    onThemeBadgeDismiss = viewModel::dismissThemeBadge,
                    onBackClick = { finish() }
                )
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    enabledLocations: Set<LocationType>,
    onEnabledLocationsSave: (Set<LocationType>) -> Unit,
    recentFilesEnabled: Boolean,
    onRecentFilesEnabledChange: (Boolean) -> Unit,
    onClearRecentFiles: () -> Unit,
    showRecentFilesBadge: Boolean,
    onRecentFilesBadgeDismiss: () -> Unit,
    showLocationsBadge: Boolean,
    onLocationsBadgeDismiss: () -> Unit,
    showThemeBadge: Boolean,
    onThemeBadgeDismiss: () -> Unit,
    onBackClick: () -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLocationsDialog by remember { mutableStateOf(false) }
    var showRecentFilesDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawer_settings), style = AppBarTitleStyle) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            RecentFilesSettingItem(
                showBadge = showRecentFilesBadge,
                onClick = {
                    onRecentFilesBadgeDismiss()
                    showRecentFilesDialog = true
                }
            )
            LocationsSettingItem(
                enabledLocations = enabledLocations,
                showBadge = showLocationsBadge,
                onClick = {
                    onLocationsBadgeDismiss()
                    showLocationsDialog = true
                }
            )
            ThemeSettingItem(
                currentTheme = themeMode,
                showBadge = showThemeBadge,
                onClick = {
                    onThemeBadgeDismiss()
                    showThemeDialog = true
                }
            )
        }
    }

    if (showLocationsDialog) {
        LocationsSelectionDialog(
            enabledLocations = enabledLocations,
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

    if (showRecentFilesDialog) {
        RecentFilesDialog(
            recentFilesEnabled = recentFilesEnabled,
            onRecentFilesEnabledChange = onRecentFilesEnabledChange,
            onClearRecentFiles = onClearRecentFiles,
            onDismiss = { showRecentFilesDialog = false }
        )
    }
}

@Composable
private fun LocationsSettingItem(
    enabledLocations: Set<LocationType>,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    val availableTypes = getAvailableLocationTypes()
    val enabledCount = enabledLocations.count { it in availableTypes }
    val availableCount = availableTypes.size

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
                contentDescription = null,
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
            Text(
                text = "$enabledCount / $availableCount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeSettingItem(
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
                contentDescription = null,
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
private fun RecentFilesSettingItem(
    showBadge: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeDot(showBadge = showBadge) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.settings_recent_files),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
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
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun LocationsSelectionDialog(
    enabledLocations: Set<LocationType>,
    onSave: (Set<LocationType>) -> Unit,
    onDismiss: () -> Unit
) {
    val availableTypes = getAvailableLocationTypes()
    var selectedLocations by remember { mutableStateOf(enabledLocations) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_locations),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                availableTypes.forEach { locationType ->
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
            androidx.compose.material3.TextButton(
                onClick = {
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
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun RecentFilesDialog(
    recentFilesEnabled: Boolean,
    onRecentFilesEnabledChange: (Boolean) -> Unit,
    onClearRecentFiles: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_recent_files),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = recentFilesEnabled,
                            onValueChange = onRecentFilesEnabledChange,
                            role = Role.Switch
                        )
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_recent_files_enabled),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = recentFilesEnabled,
                        onCheckedChange = null
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        onClearRecentFiles()
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_recent_files_clear))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.info_close))
            }
        }
    )
}

private fun getAvailableLocationTypes(): List<LocationType> {
    return LocationType.entries.filter { locationType ->
        when (locationType) {
            LocationType.PODCASTS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            else -> true
        }
    }
}
