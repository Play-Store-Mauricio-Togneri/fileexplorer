package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import androidx.annotation.StringRes
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import net.lingala.zip4j.exception.ZipException
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.ZipSlipException
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
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
import kotlinx.coroutines.launch

data class UncompressState(
    val itemToUncompress: FileItem? = null,
    val entryCount: Int = 0,
    val isPasswordProtected: Boolean = false,
    val progress: UncompressProgress? = null
)

sealed class UncompressEvent {
    data class ShowToast(@param:StringRes val messageResId: Int) : UncompressEvent()
    data object ExtractionComplete : UncompressEvent()
}

class UncompressHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val fileRepository: FileRepository,
    private val getTargetDirectory: () -> String
) {
    private val _state = MutableStateFlow(UncompressState())
    val state: StateFlow<UncompressState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UncompressEvent>()
    val events: SharedFlow<UncompressEvent> = _events.asSharedFlow()

    private var uncompressionJob: Job? = null

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
            } catch (e: Exception) {
                ErrorReporter.warning(e, "get_zip_info", "zip")
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

    private fun performUncompress(file: FileItem, password: String?, entryCount: Int) {
        val targetDir = getTargetDirectory()
        uncompressionJob = scope.launch {
            try {
                fileRepository.uncompressFile(file.path, targetDir, password)
                    .collect { progress ->
                        _state.update { it.copy(progress = progress) }
                        if (progress.isComplete) {
                            _state.update { it.copy(progress = null) }
                            MediaStoreUtil.scanFiles(context, progress.extractedPaths)
                            IntentUtil.trackRecentFile(context, file)
                            _events.emit(UncompressEvent.ExtractionComplete)
                        }
                    }
            } catch (e: ZipException) {
                _state.update { it.copy(progress = null) }
                if (e.type == ZipException.Type.WRONG_PASSWORD) {
                    _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error_wrong_password))
                    _state.update {
                        it.copy(
                            itemToUncompress = file,
                            entryCount = entryCount,
                            isPasswordProtected = true
                        )
                    }
                } else {
                    ErrorReporter.error(e, "uncompress_file", "zip")
                    _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error))
                }
            } catch (e: ZipSlipException) {
                _state.update { it.copy(progress = null) }
                ErrorReporter.error(e, "uncompress_malicious_zip", "zip")
                _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error_malicious))
            } catch (e: Exception) {
                _state.update { it.copy(progress = null) }
                if (e !is CancellationException) {
                    ErrorReporter.error(e, "uncompress_file", "zip")
                    _events.emit(UncompressEvent.ShowToast(R.string.uncompress_error))
                }
            }
        }
    }

    fun cancelUncompression() {
        uncompressionJob?.cancel()
        uncompressionJob = null
        _state.update { it.copy(progress = null) }
    }
}
