package com.mauriciotogneri.fileexplorer.util

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What a launch concluded about the configured startup folder.
 *
 * [TimedOut] is separate from [Unavailable] because only the second is a fact about the user's
 * storage: a wait that ran out establishes nothing about the folder, which may well open on the next
 * launch. Callers that tell the user their folder is gone may only do so for [Unavailable].
 */
sealed interface StartupFolder {
    data class Open(val destination: StartupDestination) : StartupFolder
    data object Unavailable : StartupFolder
    data object TimedOut : StartupFolder
}

/**
 * Resolves the configured startup folder without letting a stalled volume hold up the launch.
 *
 * [storages] and the folder stat inside [StartupDestinationResolver] are both file I/O, so they run
 * on [dispatcher]; a caller on the main thread keeps drawing meanwhile.
 *
 * @param scope owns the resolution coroutine. It has to outlive a [resolve] call and die with the
 *   caller: a scope of its own would leak a stalled resolution for good, and the caller's *own*
 *   scope would make the abandoned coroutine a child of the very block waiting on it, so the
 *   timeout would stop bounding anything.
 * @param storages the mounted storage devices to resolve against, read on [dispatcher]
 * @param dispatcher where the file I/O runs
 * @param timeoutMs how long [resolve] waits before giving up on the folder
 */
class StartupFolderResolver(
    private val scope: CoroutineScope,
    private val storages: suspend () -> List<StorageDevice>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMs: Long = TIMEOUT_MS
) {
    /**
     * What [path] resolves to, or [StartupFolder.TimedOut] when nothing was concluded within
     * [timeoutMs].
     *
     * Resolution is its own coroutine in [scope] so that the timeout can stop *waiting* for it.
     * Bounding the work itself would bound nothing: coroutine cancellation is cooperative, a stat of
     * a wedged volume is an uninterruptible syscall, and a builder wrapping that call does not return
     * until its block does. Awaiting is cancellable, so that is what the timeout is placed around;
     * the abandoned coroutine finishes on its own thread and its result is discarded.
     */
    suspend fun resolve(path: String): StartupFolder {
        val resolution = scope.async(dispatcher) { destination(path) }
        val outcome = withTimeoutOrNull(timeoutMs) { resolution.await() }

        resolution.cancel()

        return outcome ?: StartupFolder.TimedOut
    }

    /**
     * Reads the storage list and stats the folder.
     *
     * A failure resolves to [StartupFolder.Unavailable], which is the same outcome as a missing
     * folder: open the home screen and say so, rather than failing to start.
     *
     * Cancellation is rethrown rather than treated as a failure: it means the caller is going away,
     * or the wait above has already given up, and neither is a fact about the folder.
     */
    private suspend fun destination(path: String): StartupFolder = try {
        val destination = StartupDestinationResolver.resolve(path, storages())

        if (destination != null) StartupFolder.Open(destination) else StartupFolder.Unavailable
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ErrorReporter.warning(e, "resolve_startup_destination")
        StartupFolder.Unavailable
    }

    companion object {
        /**
         * How long a launch waits for the startup folder before falling back to the home screen.
         *
         * The stats involved are sub-millisecond on a healthy volume, so this is not a budget for the
         * normal case — it is the point at which a volume that is spinning up stops being worth
         * waiting for. Long enough that a slow SD card still opens its folder, short enough that a
         * stalled one costs the user a fallback rather than a screen that never resolves.
         */
        const val TIMEOUT_MS = 2_000L
    }
}
