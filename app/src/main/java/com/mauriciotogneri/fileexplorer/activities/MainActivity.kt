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
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.ui.navigation.FileExplorerNavGraph
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.util.AndroidPermissionChecker
import com.mauriciotogneri.fileexplorer.util.StartupDestination
import com.mauriciotogneri.fileexplorer.util.StartupDestinationResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private val permissionChecker by lazy { AndroidPermissionChecker(this) }

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
     * Resolving reads the filesystem, which can stall on a volume that is slow, spinning up or
     * wedged, so it runs on [Dispatchers.IO] and this Activity keeps drawing meanwhile.
     * [startupPending] is cleared only once the launch has been issued: the navigation graph enters
     * composition behind the folder rather than ahead of it.
     *
     * Nothing is opened or said once the user has left. The launch belongs to the cold start that
     * began it, and issuing it from the background either pushes the folder over whatever they
     * switched to or is dropped by the platform unseen; the home screen is what they come back to
     * instead.
     *
     * A folder the resolver rejected is reported, one that merely outlasted
     * [STARTUP_RESOLUTION_TIMEOUT_MS] is not: the message names the folder as gone, which is a claim
     * about the user's storage, and a wait that ran out establishes nothing about it.
     */
    private suspend fun openStartupFolder(path: String) {
        val resolution = startupDestination(path)
        val destination = resolution?.destination

        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (destination != null) {
                startActivity(
                    FolderActivity.createIntent(
                        context = this,
                        path = destination.path,
                        title = destination.title,
                        rootPath = destination.rootPath,
                        rootDisplayName = destination.rootDisplayName
                    )
                )
            } else if (resolution != null) {
                Toast.makeText(this, R.string.settings_startup_folder_unavailable, Toast.LENGTH_LONG).show()
            }
        }

        startupPending = false
    }

    /**
     * What resolving [path] concluded, or null when it concluded nothing within
     * [STARTUP_RESOLUTION_TIMEOUT_MS].
     *
     * Resolution is its own coroutine so that the timeout can stop *waiting* for it. Bounding the
     * work itself would bound nothing: coroutine cancellation is cooperative, a stat of a wedged
     * volume is an uninterruptible syscall, and a builder wrapping that call does not return until
     * its block does. Awaiting is cancellable, so that is what the timeout is placed around; the
     * abandoned coroutine finishes on its own thread and its result is discarded.
     */
    private suspend fun startupDestination(path: String): Resolution? {
        val resolution = lifecycleScope.async(Dispatchers.IO) { Resolution(resolvedDestination(path)) }
        val outcome = withTimeoutOrNull(STARTUP_RESOLUTION_TIMEOUT_MS) { resolution.await() }

        resolution.cancel()

        return outcome
    }

    /**
     * Reads the storage list and stats the folder, both of which are file I/O.
     *
     * A failure resolves to no destination, which is the same outcome as a missing folder: open the
     * home screen and say so, rather than failing to start.
     *
     * Cancellation is rethrown rather than treated as a failure: it means this Activity is being
     * destroyed, or the wait above has already given up, and neither is a fact about the folder.
     */
    private suspend fun resolvedDestination(path: String): StartupDestination? = try {
        val storages = StorageRepository(AndroidStorageSource(applicationContext)).getStorages()
        StartupDestinationResolver.resolve(path, storages)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ErrorReporter.warning(e, "resolve_startup_destination")
        null
    }

    /**
     * A resolution that finished. Its [destination] is null when the folder cannot be opened —
     * deleted, replaced by a file, unreadable, or on a volume that is not mounted. A resolution that
     * never finished is absent rather than empty, so a wait that ran out is not mistaken for a
     * folder that is gone.
     */
    private class Resolution(val destination: StartupDestination?)

    private companion object {
        /**
         * How long a launch waits for the startup folder before falling back to the home screen.
         *
         * The stats involved are sub-millisecond on a healthy volume, so this is not a budget for
         * the normal case — it is the point at which a volume that is spinning up stops being worth
         * waiting for. Long enough that a slow SD card still opens its folder, short enough that a
         * stalled one costs the user a fallback rather than a screen that never resolves.
         */
        const val STARTUP_RESOLUTION_TIMEOUT_MS = 2_000L
        const val STATE_STARTUP_PENDING = "startup_pending"
    }
}
