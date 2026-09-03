package com.mauriciotogneri.fileexplorer.ui.screens.analyzer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarTitleStyle
import com.mauriciotogneri.fileexplorer.ui.theme.extendedColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerScreen(
    viewModel: AnalyzerViewModel,
    onCloseClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.errorResId) {
        val messageResId = uiState.errorResId ?: return@LaunchedEffect
        Toast.makeText(context, messageResId, Toast.LENGTH_SHORT).show()
        viewModel.errorShown()
    }

    // Back means the same thing as the navigation arrow at every step, including during a scan:
    // the prompt is what guards a long walk from a mistaken swipe, so an unguarded back press
    // would leave the hole the prompt exists to close.
    val onBack: () -> Unit = when (uiState.step) {
        AnalyzerStep.SELECTION -> onCloseClick
        AnalyzerStep.SCANNING -> viewModel::requestCancelScan
        AnalyzerStep.RESULTS -> viewModel::backToSelection
    }

    BackHandler(enabled = uiState.step != AnalyzerStep.SELECTION) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawer_analyzer), style = AppBarTitleStyle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoadingStorages -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                uiState.step == AnalyzerStep.SELECTION -> VolumeSelection(
                    storages = uiState.storages,
                    selectedPath = uiState.selectedPath,
                    onSelect = viewModel::selectStorage,
                    onAnalyze = viewModel::startScan
                )

                uiState.step == AnalyzerStep.SCANNING -> ScanningProgress(
                    currentFolder = uiState.currentFolder,
                    scannedBytes = uiState.scannedBytes,
                    fileCount = uiState.fileCount,
                    onCancel = viewModel::requestCancelScan
                )

                else -> ScanResults(
                    categories = uiState.categories,
                    totalBytes = uiState.selectedStorage?.totalBytes ?: 0L,
                    usedBytes = uiState.usedBytes,
                    usedFraction = uiState.usedFraction
                )
            }
        }
    }

    if (uiState.showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCancelScan,
            title = { Text(stringResource(R.string.analyzer_stop_scanning_title)) },
            text = { Text(stringResource(R.string.analyzer_stop_scanning_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCancelScan) {
                    Text(stringResource(R.string.analyzer_stop_scanning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCancelScan) {
                    Text(stringResource(R.string.analyzer_stop_scanning_dismiss))
                }
            }
        )
    }
}

@Composable
private fun VolumeSelection(
    storages: List<StorageDevice>,
    selectedPath: String?,
    onSelect: (String) -> Unit,
    onAnalyze: () -> Unit
) {
    if (storages.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.analyzer_no_storages),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .selectableGroup(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(storages, key = { it.path }) { storage ->
                VolumeCard(
                    storage = storage,
                    selected = storage.path == selectedPath,
                    onSelect = { onSelect(storage.path) }
                )
            }
        }

        Button(
            onClick = onAnalyze,
            enabled = selectedPath != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text(stringResource(R.string.analyzer_analyze))
        }
    }
}

@Composable
private fun VolumeCard(
    storage: StorageDevice,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val usedBytes = storage.totalBytes - storage.availableBytes
    val usedFraction = if (storage.totalBytes > 0) {
        usedBytes.toFloat() / storage.totalBytes
    } else {
        0f
    }

    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Null description: the row as a whole is already the selectable, and the card
                // states its own name on the next line.
                RadioButton(selected = selected, onClick = null)

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = storageIcon(storage),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = storage.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(
                    R.string.storage_capacity_format,
                    storage.formattedAvailable,
                    storage.formattedTotal
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            UsageBar(fraction = usedFraction, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ScanningProgress(
    currentFolder: String,
    scannedBytes: Long,
    fileCount: Int,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.analyzer_scanning),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // One line, elided in the middle: the volume root and the folder's own name are what
        // identify where the walk is, and a trailing elision would drop the second of them.
        Text(
            text = currentFolder,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // A running total rather than a bar. The only denominator available is the volume's used
        // space, which counts the apps and app-private data no walk can reach, so a determinate bar
        // would crawl to a few percent and then jump — see AnalyzerCategory.SYSTEM. This moves
        // continuously and every figure on it is one the walk actually measured.
        Text(
            text = pluralStringResource(
                R.plurals.analyzer_found,
                fileCount,
                FileSizeFormatter.format(scannedBytes),
                fileCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onCancel) {
            Text(stringResource(R.string.dialog_cancel))
        }
    }
}

@Composable
private fun ScanResults(
    categories: List<CategoryUsage>,
    totalBytes: Long,
    usedBytes: Long,
    usedFraction: Float
) {
    val usedSizeLabel = FileSizeFormatter.format(usedBytes)
    val usedPercentLabel = percentLabel(usedFraction, decimals = 0)
    val tones = MaterialTheme.extendedColorScheme.categoryTones

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 24.dp,
            bottom = 24.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "chart") {
            StorageDonutChart(
                categories = categories,
                usedFraction = usedFraction,
                usedPercentLabel = usedPercentLabel,
                usedSizeLabel = usedSizeLabel,
                totalLabel = stringResource(
                    R.string.analyzer_of_total,
                    FileSizeFormatter.format(totalBytes)
                )
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        items(categories, key = { it.category.name }) { usage ->
            CategoryRow(
                usage = usage,
                tone = tones.getOrElse(usage.category.ordinal) { MaterialTheme.colorScheme.primary }
            )
        }
    }
}

@Composable
private fun CategoryRow(
    usage: CategoryUsage,
    tone: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = usage.category.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = stringResource(usage.category.labelResId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = FileSizeFormatter.format(usage.bytes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Drawn in the same tone as this category's arc, which is what identifies the arc. See the
        // ramp's note in Color.kt.
        UsageBar(fraction = usage.fraction, color = tone)
    }
}

/** The app's storage usage bar, as the home screen's storage cards draw it. */
@Composable
private fun UsageBar(fraction: Float, color: Color) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

private fun storageIcon(storage: StorageDevice): ImageVector =
    if (StorageDevice.isSdCard(storage.path)) Icons.Outlined.SdCard else Icons.Outlined.PhoneAndroid

/**
 * [fraction] as a percentage, to [decimals] places, with the percent sign placed by the locale.
 *
 * The locale comes from [LocalLocale] rather than `Locale.getDefault()`: the latter is not
 * observable state, so a label formatted from it would keep the digits of whatever locale was
 * current when the screen was first composed.
 */
@Composable
private fun percentLabel(fraction: Float, decimals: Int): String = stringResource(
    R.string.analyzer_percent_format,
    String.format(LocalLocale.current.platformLocale, "%.${decimals}f", fraction * 100f)
)
