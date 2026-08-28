package com.mauriciotogneri.fileexplorer.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.ui.navigation.FileExplorerNavGraph
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.util.AndroidPermissionChecker
import com.mauriciotogneri.fileexplorer.util.StartupFolder
import com.mauriciotogneri.fileexplorer.util.StartupFolderResolver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissionChecker by lazy { AndroidPermissionChecker(this) }

    private val startupFolderResolver by lazy {
        StartupFolderResolver(
            scope = lifecycleScope,
            storages = { StorageRepository(AndroidStorageSource(applicationContext)).getStorages() }
        )
    }

    /**
     * Whether a startup folder is still being resolved. While it is, the navigation graph is held
     * back, so the home screen is never drawn ahead of the folder that is about to cover it.
     */
    private var startupPending by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only on a fresh Activity, unless a recreation interrupted an in-flight resolution. A
        // recreation after routing finished must not launch the folder again, or the user could
        // never reach the home screen sitting behind it. Warm resumes never get here at all:
        // Android resumes the existing task without recreating this Activity, so the startup folder
        // applies to a cold start only.
        val shouldResolveStartup = savedInstanceState == null ||
            savedInstanceState.getBoolean(STATE_STARTUP_PENDING)

        if (shouldResolveStartup) {
            val path = startupFolderPath()

            if (path != null) {
                startupPending = true
                lifecycleScope.launch { openStartupFolder(path) }
            }
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
                if (startupPending) {
                    // Deliberately empty, in the colour the home screen's Scaffold fills its own
                    // container with: the hold normally lasts a few frames, so anything drawn here
                    // — a spinner, a logo — would itself be the flash this exists to remove, and on
                    // a slow volume the eventual swap to the home screen stays invisible.
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {}
                } else {
                    FileExplorerNavGraph(
                        hasPermission = hasPermission
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_STARTUP_PENDING, startupPending)
    }

    /**
     * The configured startup folder path, or null when the app should open on the home screen.
     *
     * Blocks the main thread on the preferences store, the way the theme and sort preferences
     * already do in the application's onCreate: the decision has to be made before anything is
     * composed. The store is warm by then, so this is an in-memory read; the filesystem work that
     * follows is what runs off this thread, in [openStartupFolder].
     */
    private fun startupFolderPath(): String? {
        if (!permissionChecker.hasStoragePermission()) return null

        val preferencesRepository = PreferencesRepository(DataStorePreferencesSource(preferencesDataStore))

        return preferencesRepository.getInitialStartupFolderPath()
    }

    /**
     * Opens the configured startup folder on top of the home screen, so pressing back once returns
     * home exactly as it does for a folder opened from there by hand.
     *
     * [startupPending] is cleared only once the launch has been issued: the navigation graph enters
     * composition behind the folder rather than ahead of it.
     *
     * Nothing is opened or said once the user has left. The launch belongs to the cold start that
     * began it, and issuing it from the background either pushes the folder over whatever they
     * switched to or is dropped by the platform unseen; the home screen is what they come back to
     * instead.
     *
     * A folder the resolver rejected is reported, one that merely timed out is not: the message
     * names the folder as gone, which is a claim about the user's storage that a wait running out
     * does not establish.
     */
    private suspend fun openStartupFolder(path: String) {
        val startupFolder = startupFolderResolver.resolve(path)

        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            when (startupFolder) {
                is StartupFolder.Open -> startActivity(
                    FolderActivity.createIntent(
                        context = this,
                        path = startupFolder.destination.path,
                        title = startupFolder.destination.title,
                        rootPath = startupFolder.destination.rootPath,
                        rootDisplayName = startupFolder.destination.rootDisplayName
                    )
                )

                StartupFolder.Unavailable ->
                    Toast.makeText(this, R.string.settings_startup_folder_unavailable, Toast.LENGTH_LONG).show()

                StartupFolder.TimedOut -> Unit
            }
        }

        startupPending = false
    }

    private companion object {
        const val STATE_STARTUP_PENDING = "startup_pending"
    }
}
