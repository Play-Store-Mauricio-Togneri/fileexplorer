package com.mauriciotogneri.fileexplorer.ui.screens.picker

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.PickerRequest
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.components.Breadcrumbs
import com.mauriciotogneri.fileexplorer.ui.components.CreateFolderDialog

@Composable
fun DestinationPicker(
    request: PickerRequest,
    sortMode: SortMode,
    showHidden: Boolean,
    fileRepository: FileRepository,
    storageRepository: StorageRepository,
    onConfirm: (targetPath: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val viewModel: PickerViewModel = viewModel(
        key = "picker_${request.id}",
        factory = PickerViewModel.Factory(
            application = context.applicationContext as Application,
            fileRepository = fileRepository,
            storageRepository = storageRepository,
            sourceItems = request.items,
            operationMode = request.mode,
            sortMode = sortMode,
            showHidden = showHidden
        )
    )

    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val storages by viewModel.storages.collectAsStateWithLifecycle()
    val showStorageSelector by viewModel.showStorageSelector.collectAsStateWithLifecycle()
    val validationError by viewModel.validationError.collectAsStateWithLifecycle()
    val isValidDestination by viewModel.isValidDestination.collectAsStateWithLifecycle()
    val showCreateFolderDialog by viewModel.showCreateFolderDialog.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val storageLoadError by viewModel.storageLoadError.collectAsStateWithLifecycle()

    val actionName = when (request.mode) {
        OperationMode.MOVE -> "move"
        OperationMode.COPY -> "copy"
        null -> "select"
    }

    LaunchedEffect(Unit) {
        AnalyticsTracker.trackDestinationPickerShown(actionName)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PickerUiEvent.ShowToast ->
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        if (!viewModel.navigateUp()) {
            AnalyticsTracker.trackDestinationPickerClosed()
            onCancel()
        }
    }

    Scaffold(
        topBar = {
            PickerTopBar(
                title = when (request.mode) {
                    OperationMode.MOVE -> stringResource(R.string.picker_title_move)
                    OperationMode.COPY -> stringResource(R.string.picker_title_copy)
                    null -> stringResource(R.string.picker_title_select)
                },
                onBackClick = {
                    if (!viewModel.navigateUp()) {
                        AnalyticsTracker.trackDestinationPickerClosed()
                        onCancel()
                    }
                }
            )
        },
        bottomBar = {
            if (!showStorageSelector && currentPath != null) {
                PickerBottomBar(
                    mode = request.mode,
                    isValidDestination = isValidDestination,
                    validationError = validationError,
                    onNewFolder = {
                        AnalyticsTracker.trackDestinationPickerNewFolderTapped()
                        viewModel.showCreateFolderDialog()
                    },
                    onConfirm = {
                        currentPath?.let {
                            AnalyticsTracker.trackDestinationPickerConfirmed(actionName)
                            onConfirm(it)
                        }
                    }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        // Only the bars' heights pad the column. The side insets (the display cutout in landscape)
        // go inside the breadcrumb band and the list, so their backgrounds reach the screen edge.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding(), bottom = paddingValues.calculateBottomPadding())
        ) {
            currentPath?.takeIf { !showStorageSelector }?.let { path ->
                val storageRoot = viewModel.getCurrentStorageRoot()
                Breadcrumbs(
                    currentPath = path,
                    onNavigateToPath = {
                        AnalyticsTracker.trackDestinationPickerBreadcrumbClicked()
                        viewModel.navigateToPath(it)
                    },
                    rootPath = storageRoot?.path,
                    rootDisplayName = storageRoot?.displayName
                )
            }

            // The strip beside the rows takes their colour, so the list looks edge to edge.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal))
            ) {
                if (showStorageSelector) {
                    StorageSelectorContent(
                        storages = storages,
                        onStorageClick = { viewModel.navigateToStorage(it) }
                    )
                } else {
                    FolderPickerContent(
                        folders = folders,
                        isLoading = isLoading,
                        error = storageLoadError,
                        onFolderClick = { viewModel.navigateToFolder(it) }
                    )
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            existingNames = viewModel.getExistingNames(),
            onDismiss = {
                AnalyticsTracker.trackDestinationPickerFolderCreationCancelled()
                viewModel.dismissCreateFolderDialog()
            },
            onCreate = { name -> viewModel.createFolder(name) }
        )
    }
}
