package com.mauriciotogneri.fileexplorer.ui.screens.picker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
            application = context.applicationContext as android.app.Application,
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

    BackHandler {
        if (!viewModel.navigateUp()) {
            onCancel()
        }
    }

    Scaffold(
        topBar = {
            PickerTopBar(
                title = if (request.mode == OperationMode.MOVE) {
                    stringResource(R.string.picker_title_move)
                } else {
                    stringResource(R.string.picker_title_copy)
                },
                onBackClick = {
                    if (!viewModel.navigateUp()) {
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
                    onNewFolder = { viewModel.showCreateFolderDialog() },
                    onConfirm = {
                        currentPath?.let { onConfirm(it) }
                    }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!showStorageSelector && currentPath != null) {
                val storageRoot = viewModel.getCurrentStorageRoot()
                Breadcrumbs(
                    currentPath = currentPath!!,
                    onNavigateToPath = { path -> viewModel.navigateToPath(path) },
                    rootPath = storageRoot?.path,
                    rootDisplayName = storageRoot?.displayName
                )
            }

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

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            existingNames = viewModel.getExistingNames(),
            onDismiss = { viewModel.dismissCreateFolderDialog() },
            onCreate = { name -> viewModel.createFolder(name) }
        )
    }
}
