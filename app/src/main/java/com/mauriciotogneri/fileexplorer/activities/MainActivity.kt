package com.mauriciotogneri.fileexplorer.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.ui.navigation.FileExplorerNavGraph
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.util.AndroidPermissionChecker
import com.mauriciotogneri.fileexplorer.util.StartupDestinationResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val permissionChecker by lazy { AndroidPermissionChecker(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only on a fresh Activity. A recreation (rotation, ...) must not launch the folder again,
        // or the user could never reach the home screen sitting behind it. Warm resumes never get
        // here at all: Android resumes the existing task without recreating this Activity, so the
        // startup folder applies to a cold start only.
        if (savedInstanceState == null) {
            openStartupFolder()
        }

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory())
            var hasPermission by remember { mutableStateOf(permissionChecker.hasStoragePermission()) }
            val lifecycleOwner = LocalLifecycleOwner.current
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)

            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    hasPermission = permissionChecker.hasStoragePermission()
                }
            }

            FileExplorerTheme(themeMode = themeMode) {
                FileExplorerNavGraph(
                    hasPermission = hasPermission
                )
            }
        }
    }

    /**
     * Opens the configured startup folder on top of the home screen, so pressing back once returns
     * home exactly as it does for a folder opened from there by hand.
     *
     * Reads block the main thread before the first frame is drawn, the way the theme and sort
     * preferences already do in the application's onCreate: the folder has to be launched before
     * the home screen is composed, or the user sees it flash past. Users on the default home screen
     * pay only the preference read; the storage lookup runs only for those who chose a folder.
     */
    private fun openStartupFolder() {
        if (!permissionChecker.hasStoragePermission()) return

        val preferencesRepository = PreferencesRepository(DataStorePreferencesSource(preferencesDataStore))
        val path = preferencesRepository.getInitialStartupFolderPath() ?: return

        val destination = StartupDestinationResolver.resolve(path, mountedStorages())
        if (destination == null) {
            Toast.makeText(this, R.string.settings_startup_folder_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        startActivity(
            FolderActivity.createIntent(
                context = this,
                path = destination.path,
                title = destination.title,
                rootPath = destination.rootPath,
                rootDisplayName = destination.rootDisplayName
            )
        )
    }

    /**
     * The mounted storage devices, or an empty list when they cannot be read. An empty list resolves
     * to no destination, which is the same outcome as a missing folder: open the home screen and say
     * so, rather than failing to start.
     */
    private fun mountedStorages() = try {
        runBlocking(Dispatchers.IO) {
            StorageRepository(AndroidStorageSource(this@MainActivity)).getStorages()
        }
    } catch (e: Exception) {
        ErrorReporter.warning(e, "read_startup_storages")
        emptyList()
    }
}
