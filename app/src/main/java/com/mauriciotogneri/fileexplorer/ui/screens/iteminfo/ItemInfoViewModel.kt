package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.ApkMetadata
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.model.AudioMetadata
import com.mauriciotogneri.fileexplorer.data.model.CsvMetadata
import com.mauriciotogneri.fileexplorer.data.model.EpubMetadata
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.ICalendarMetadata
import com.mauriciotogneri.fileexplorer.data.model.ImageMetadata
import com.mauriciotogneri.fileexplorer.data.model.OfficeMetadata
import com.mauriciotogneri.fileexplorer.data.model.PdfMetadata
import com.mauriciotogneri.fileexplorer.data.model.SqliteMetadata
import com.mauriciotogneri.fileexplorer.data.model.VCardMetadata
import com.mauriciotogneri.fileexplorer.data.model.VideoMetadata
import com.mauriciotogneri.fileexplorer.data.model.ZipMetadata
import com.mauriciotogneri.fileexplorer.data.util.ApkMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.AudioMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.CsvMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.EpubMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.ICalendarMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.ImageMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.OfficeMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.PdfMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.SqliteMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.VCardMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.VideoMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.ZipMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.util.UncompressEvent
import com.mauriciotogneri.fileexplorer.util.UncompressHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@Immutable
data class ItemInfoUiState(
    val isLoading: Boolean = true,
    val file: FileItem? = null,
    val folderSize: Long? = null,
    val imageMetadata: ImageMetadata? = null,
    val audioMetadata: AudioMetadata? = null,
    val videoMetadata: VideoMetadata? = null,
    val pdfMetadata: PdfMetadata? = null,
    val apkMetadata: ApkMetadata? = null,
    val zipMetadata: ZipMetadata? = null,
    val officeMetadata: OfficeMetadata? = null,
    val epubMetadata: EpubMetadata? = null,
    val sqliteMetadata: SqliteMetadata? = null,
    val vcardMetadata: VCardMetadata? = null,
    val icalendarMetadata: ICalendarMetadata? = null,
    val csvMetadata: CsvMetadata? = null,
    val error: Boolean = false,
    val itemToUncompress: FileItem? = null,
    val uncompressEntryCount: Int = 0,
    val isPasswordProtected: Boolean = false,
    val uncompressProgress: UncompressProgress? = null
)

sealed interface ItemInfoUiEvent {
    data class OpenFile(val file: FileItem) : ItemInfoUiEvent
    data class ShowToast(val messageResId: Int) : ItemInfoUiEvent
}

class ItemInfoViewModel(
    private val filePath: String,
    application: Application,
    private val fileRepository: FileRepository
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    private val _state = MutableStateFlow(ItemInfoUiState())
    val state: StateFlow<ItemInfoUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ItemInfoUiEvent>()
    val events: SharedFlow<ItemInfoUiEvent> = _events.asSharedFlow()

    private val uncompressHandler = UncompressHandler(
        context = context,
        scope = viewModelScope,
        fileRepository = fileRepository,
        getTargetDirectory = { File(filePath).parent ?: "" }
    )

    init {
        loadFileInfo()
        observeUncompressHandler()
    }

    private fun observeUncompressHandler() {
        viewModelScope.launch {
            uncompressHandler.state.collect { uncompressState ->
                _state.update {
                    it.copy(
                        itemToUncompress = uncompressState.itemToUncompress,
                        uncompressEntryCount = uncompressState.entryCount,
                        isPasswordProtected = uncompressState.isPasswordProtected,
                        uncompressProgress = uncompressState.progress
                    )
                }
            }
        }
        viewModelScope.launch {
            uncompressHandler.events.collect { event ->
                when (event) {
                    is UncompressEvent.ShowToast -> {
                        _events.emit(ItemInfoUiEvent.ShowToast(event.messageResId))
                    }
                    is UncompressEvent.ExtractionComplete -> {
                        // No refresh needed for info screen
                    }
                }
            }
        }
    }

    fun onOpenFile() {
        val file = _state.value.file ?: return
        if (file.isDirectory) return
        viewModelScope.launch {
            _events.emit(ItemInfoUiEvent.OpenFile(file))
        }
    }

    private fun loadFileInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = false) }
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val fileItem = FileItem.from(file)
                    val imageMetadata = if (fileItem.isImage) {
                        ImageMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    val audioMetadata = if (fileItem.isAudio) {
                        AudioMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    val videoMetadata = if (fileItem.isVideo) {
                        VideoMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    val pdfMetadata = if (fileItem.isPdf) {
                        PdfMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    val apkMetadata = if (fileItem.isApk) {
                        ApkMetadataExtractor.extract(context, file)
                    } else {
                        null
                    }
                    val zipMetadata = if (fileItem.isZip) {
                        ZipMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    val officeMetadata = if (fileItem.isOfficeDocument) {
                        OfficeMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    val epubMetadata = if (fileItem.isEpub) {
                        EpubMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    val sqliteMetadata = if (fileItem.isSqlite) {
                        SqliteMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    val vcardMetadata = if (fileItem.isVCard) {
                        VCardMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    val icalendarMetadata = if (fileItem.isICalendar) {
                        ICalendarMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    val csvMetadata = if (fileItem.isCsv) {
                        CsvMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            file = fileItem,
                            imageMetadata = imageMetadata,
                            audioMetadata = audioMetadata,
                            videoMetadata = videoMetadata,
                            pdfMetadata = pdfMetadata,
                            apkMetadata = apkMetadata,
                            zipMetadata = zipMetadata,
                            officeMetadata = officeMetadata,
                            epubMetadata = epubMetadata,
                            sqliteMetadata = sqliteMetadata,
                            vcardMetadata = vcardMetadata,
                            icalendarMetadata = icalendarMetadata,
                            csvMetadata = csvMetadata
                        )
                    }
                    // Calculate folder size asynchronously after showing basic info
                    if (fileItem.isDirectory) {
                        loadFolderSize(file)
                    }
                } else {
                    _state.update { it.copy(isLoading = false, error = true) }
                }
            } catch (e: Exception) {
                ErrorReporter.error(e, "load_file_info")
                _state.update { it.copy(isLoading = false, error = true) }
            }
        }
    }

    private fun loadFolderSize(folder: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val size = calculateFolderSize(folder)
                _state.update { it.copy(folderSize = size) }
            } catch (e: Exception) {
                ErrorReporter.error(e, "calculate_folder_size")
            }
        }
    }

    private fun calculateFolderSize(folder: File): Long {
        var size = 0L
        folder.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }

    fun showUncompressDialog(file: FileItem) {
        uncompressHandler.showUncompressDialog(file)
    }

    fun dismissUncompressDialog() {
        uncompressHandler.dismissUncompressDialog()
    }

    fun confirmUncompress(password: String? = null) {
        uncompressHandler.confirmUncompress(password)
    }

    fun cancelUncompression() {
        uncompressHandler.cancelUncompression()
    }

    class Factory(
        private val filePath: String,
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ItemInfoViewModel(
                filePath = filePath,
                application = application,
                fileRepository = FileRepository()
            ) as T
        }
    }
}
