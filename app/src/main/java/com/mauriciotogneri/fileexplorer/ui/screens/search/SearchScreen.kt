package com.mauriciotogneri.fileexplorer.ui.screens.search

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.ItemInfoActivity
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.components.ApkPermissionDialog
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.components.SearchFileAction
import com.mauriciotogneri.fileexplorer.ui.components.SearchFileActionsBottomSheet
import com.mauriciotogneri.fileexplorer.ui.components.PasswordUncompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.UncompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.UncompressProgressDialog
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.OpenFileResult
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(LocalContext.current.applicationContext as Application))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val shareFilesUnableMessage = stringResource(R.string.share_files_unable)
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var fileForActions by remember { mutableStateOf<FileItem?>(null) }
    var bottomSheetMode by remember { mutableStateOf("icon") }

    LaunchedEffect(Unit) {
        AnalyticsTracker.trackScreenSearch()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Auto-retry APK install if permission was granted
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.uiState.value.pendingApkInstall?.let { pendingApk ->
                if (IntentUtil.canInstallApks(context)) {
                    viewModel.clearPendingApkInstall()
                    IntentUtil.installApk(context, pendingApk, "search")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SearchUiEvent.ShowToastRes -> {
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    BackHandler {
        viewModel.trackCloseWithoutTyping()
        onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { keyboardController?.hide() }
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(x = (-12).dp)
                            .focusRequester(focusRequester)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.trackCloseWithoutTyping()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                actions = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.trackClearInputTapped()
                            viewModel.clearQuery()
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.search_clear)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
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
                state.query.isEmpty() -> {
                    // Empty state - show nothing
                }

                state.showNoResults -> {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 120.dp)
                    )
                }

                state.results.isNotEmpty() -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = state.results,
                            key = { it.path }
                        ) { file ->
                            FileListItem(
                                file = file,
                                isSelected = false,
                                onClick = {
                                    when (val result = IntentUtil.openFile(context, file, "search")) {
                                        is OpenFileResult.Handled -> { }
                                        is OpenFileResult.RequiresUncompress -> {
                                            viewModel.showUncompressDialog(result.file)
                                        }
                                        is OpenFileResult.RequiresInstallPermission -> {
                                            viewModel.setPendingApkInstall(result.file)
                                        }
                                    }
                                },
                                onLongClick = {
                                    bottomSheetMode = "press"
                                    fileForActions = file
                                },
                                onMenuClick = {
                                    bottomSheetMode = "icon"
                                    fileForActions = file
                                },
                                showMenu = true
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        if (state.isSearching) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }

                state.isSearching -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 120.dp)
                    )
                }
            }
        }
    }

    fileForActions?.let { file ->
        SearchFileActionsBottomSheet(
            file = file,
            mode = bottomSheetMode,
            onAction = { action ->
                fileForActions = null
                when (action) {
                    SearchFileAction.OpenWith -> {
                        IntentUtil.openFileWith(context, file, "search")
                    }
                    SearchFileAction.Share -> {
                        val shared = IntentUtil.shareFiles(context, listOf(file))
                        if (!shared) {
                            Toast.makeText(
                                context,
                                shareFilesUnableMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    SearchFileAction.Delete -> {
                        viewModel.showDeleteDialog(file)
                    }
                    SearchFileAction.Info -> {
                        context.startActivity(ItemInfoActivity.createIntent(context, file.path))
                    }
                }
            },
            onDismiss = { fileForActions = null }
        )
    }

    state.fileToDelete?.let { file ->
        DeleteConfirmDialog(
            itemCount = 1,
            itemName = file.name,
            onDismiss = { viewModel.dismissDeleteDialog() },
            onConfirm = { viewModel.onDeleteConfirmed() }
        )
    }

    state.itemToUncompress?.let {
        if (state.isPasswordProtected) {
            PasswordUncompressDialog(
                entryCount = state.uncompressEntryCount,
                onDismiss = { viewModel.dismissUncompressDialog() },
                onExtract = { password -> viewModel.confirmUncompress(password) }
            )
        } else {
            UncompressDialog(
                entryCount = state.uncompressEntryCount,
                onDismiss = { viewModel.dismissUncompressDialog() },
                onExtract = { viewModel.confirmUncompress() }
            )
        }
    }

    state.uncompressProgress?.let { progress ->
        UncompressProgressDialog(
            progress = progress,
            onCancel = { viewModel.cancelUncompression() }
        )
    }

    // APK permission dialog
    state.pendingApkInstall?.let {
        ApkPermissionDialog(
            source = "search",
            onDismiss = { viewModel.clearPendingApkInstall() },
            onOpenSettings = {
                context.startActivity(IntentUtil.getInstallPermissionSettingsIntent(context))
            }
        )
    }
}
