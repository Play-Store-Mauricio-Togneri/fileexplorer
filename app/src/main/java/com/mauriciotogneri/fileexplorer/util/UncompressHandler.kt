package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import androidx.annotation.StringRes
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.util.scrubbed
import net.lingala.zip4j.exception.ZipException
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.InsufficientStorageException
import com.mauriciotogneri.fileexplorer.data.repository.ZipBombException
import com.mauriciotogneri.fileexplorer.data.repository.ZipSlipException
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.FileExtensionUtil
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

data class UncompressState(
    val itemToUncompress: FileItem? = null,
    val entryCount: Int = 0,
    val isPasswordProtected: Boolean = false,
    val progress: UncompressProgress? = null,
    /**
     * Extractions running in the owner's scope. Leaving the screen cancels that scope and stops
     * each of them part-way, so while any runs a way out first shows the progress dialog still
     * held back by its delay: see [UncompressHandler.showPendingProgress].
     */
    val runningExtractions: Int = 0
)

sealed class UncompressEvent {
    data class ShowToast(@param:StringRes val messageResId: Int) : UncompressEvent()
    data object ExtractionComplete : UncompressEvent()
}

class UncompressHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val fileRepository: FileRepository,
    private val getTargetDirectory: () -> String,
    private val getAllowedRoots: suspend () -> List<String>
) {
    private val _state = MutableStateFlow(UncompressState())
    val state: StateFlow<UncompressState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UncompressEvent>()
    val events: SharedFlow<UncompressEvent> = _events.asSharedFlow()

    private var uncompressionJob: Job? = null

    // The running extraction's dialog, kept so that a way out of the screen can show it before its
    // delay is up. A cancelled extraction still unwinding has given its dialog up, so a new one
    // takes the slot. Only read and written on the main thread.
    private var uncompressionDialog: DelayedProgress<UncompressProgress>? = null

    fun showUncompressDialog(file: FileItem) {
        scope.launch {
            try {
                val zipInfo = fileRepository.getZipInfo(file.path)
                _state.update {
                    it.copy(
                        itemToUncompress = file,
                        entryCount = zipInfo.entryCount,
                        isPasswordProtected = zipInfo.isEncrypted
                    )
                }
            } catch (e: ZipException) {
                // File has an archive extension but isn't a readable zip (corrupt,
                // truncated, or renamed). Expected bad input, not an app error.
                AnalyticsTracker.trackOperationFailed("uncompress", "invalid_zip")
                _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error_invalid_archive))
            } catch (e: Exception) {
                ErrorReporter.warning(e.scrubbed(), "get_zip_info", "zip")
                _state.update {
                    it.copy(itemToUncompress = file, entryCount = 0, isPasswordProtected = false)
                }
            }
        }
    }

    fun dismissUncompressDialog() {
        _state.update { it.copy(itemToUncompress = null, entryCount = 0, isPasswordProtected = false) }
    }

    fun confirmUncompress(password: String? = null) {
        val file = _state.value.itemToUncompress ?: return
        val entryCount = _state.value.entryCount
        dismissUncompressDialog()
        performUncompress(file, password, entryCount)
    }

    /**
     * Every catch below closes the dialog through [DelayedProgress.clear], which leaves it alone
     * while a newer extraction owns it: a cancelled one can finish unwinding after the next has
     * started, and clearing then would hide that one's progress, its only Cancel button, while it
     * goes on extracting. A plain cancel has nulled the handle and closed the dialog already.
     */
    private fun performUncompress(file: FileItem, password: String?, entryCount: Int) {
        val targetDir = getTargetDirectory()
        uncompressionJob = scope.launchWithDelayedProgress(
            // Until the archive's headers have been read there is no fraction to show.
            placeholder = UncompressProgress(
                currentFile = "",
                extractedFiles = 0,
                totalFiles = 0,
                extractedBytes = 0L,
                totalBytes = 0L
            ),
            owner = { uncompressionJob },
            publish = { progress -> _state.update { it.copy(progress = progress) } }
        ) { dialog ->
            uncompressionDialog = dialog
            countRunningExtraction(1)
            try {
                val allowedRoots = getAllowedRoots()
                fileRepository.uncompressFile(
                    zipPath = file.path,
                    targetDir = targetDir,
                    password = password,
                    allowedRoots = allowedRoots
                )
                    .collect { progress ->
                        dialog.update(progress)
                        if (progress.isComplete) {
                            dialog.clear()
                            IntentUtil.trackRecentFile(context, file)
                            AnalyticsTracker.trackUncompressCompleted(
                                FileExtensionUtil.getExtension(file.path)
                            )
                            ReviewPrompt.onActionSucceeded()
                            _events.emit(UncompressEvent.ExtractionComplete)
                        }
                    }
            } catch (e: ZipException) {
                dialog.clear()
                if (e.type == ZipException.Type.WRONG_PASSWORD) {
                    AnalyticsTracker.trackOperationFailed("uncompress", "wrong_password")
                    _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error_wrong_password))
                    _state.update {
                        it.copy(
                            itemToUncompress = file,
                            entryCount = entryCount,
                            isPasswordProtected = true
                        )
                    }
                } else {
                    // Corrupt/unreadable archive — expected bad input, not an app error.
                    AnalyticsTracker.trackOperationFailed("uncompress", "zip_exception")
                    _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error))
                }
            } catch (_: ZipSlipException) {
                dialog.clear()
                AnalyticsTracker.trackOperationFailed("uncompress", "malicious_zip")
                _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error_malicious))
            } catch (_: ZipBombException) {
                dialog.clear()
                AnalyticsTracker.trackOperationFailed("uncompress", "zip_bomb")
                _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error_too_large))
            } catch (_: InsufficientStorageException) {
                dialog.clear()
                AnalyticsTracker.trackOperationFailed("uncompress", "insufficient_storage")
                _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error_insufficient_storage))
            } catch (_: SecurityException) {
                dialog.clear()
                AnalyticsTracker.trackOperationFailed("uncompress", "invalid_target")
                _events.emit(UncompressEvent.ShowToast(R.string.error_invalid_target_path))
            } catch (_: IOException) {
                // An I/O error while the entries were being written (e.g. removable storage
                // unmounted mid-extraction, or the target folder deleted underneath the write). A
                // volume that fills up mid-extraction is not one of them — uncompressFile converts
                // that to InsufficientStorageException, two clauses above, so the user gets the
                // actionable message instead of this one. Environmental, not an app bug: the
                // extraction has already rolled back what it created, so show the failure toast but
                // don't report it to Crashlytics. Caught after the ZipException clause above, which
                // is the archive-shaped subtype of this one.
                dialog.clear()
                AnalyticsTracker.trackOperationFailed("uncompress", "storage_io_error")
                _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error))
            } catch (e: Exception) {
                dialog.clear()
                if (e !is CancellationException) {
                    AnalyticsTracker.trackOperationFailed("uncompress", "exception")
                    // Everything the extraction is known to fail with is handled above, so what
                    // reaches here is an app bug and has to be reportable — as it is on the
                    // copy/move and compress paths. Reported through a stand-in rather than
                    // directly, for the reason [scrubbed] gives: what still reaches here is free to
                    // be carrying an absolute path — the pre-flight StatFs rejects a target it
                    // cannot stat with that path in the message.
                    ErrorReporter.error(e.scrubbed(), "uncompress_file", "zip")
                    _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error))
                }
            } finally {
                // Only its own handle: a cancelled extraction can finish unwinding after the next
                // one has started, and clearing that one's would leave its Cancel stopping nothing.
                if (uncompressionJob === coroutineContext.job) uncompressionJob = null
                if (uncompressionDialog === dialog) uncompressionDialog = null
                countRunningExtraction(-1)
            }
        }
    }

    /**
     * Shows at once the progress dialog of an extraction still inside its delay, for a way out of
     * the screen taken in that window: leaving cancels the owner's scope and stops the extraction
     * part-way, with nothing left on screen to say so. The press then lands on the dialog, as it
     * would once the delay were over.
     *
     * @return whether the extraction owns a progress dialog that is up, shown by this call or
     * already; false lets the screen be left. A cancelled extraction has given its dialog up, and
     * leaving then only stops what is stopping already.
     */
    fun showPendingProgress(): Boolean = uncompressionDialog?.reveal() == true

    private fun countRunningExtraction(change: Int) {
        _state.update { it.copy(runningExtractions = it.runningExtractions + change) }
    }

    fun cancelUncompression() {
        uncompressionJob?.cancel()
        uncompressionJob = null
        _state.update { it.copy(progress = null) }
    }
}
