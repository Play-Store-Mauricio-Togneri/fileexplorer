package com.mauriciotogneri.fileexplorer.ui.screens.analyzer

import android.content.Context
import com.mauriciotogneri.fileexplorer.R
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry
import com.mauriciotogneri.fileexplorer.data.model.SearchFileType
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerRepository
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerResultsHolder
import com.mauriciotogneri.fileexplorer.data.repository.CategoryFiles
import com.mauriciotogneri.fileexplorer.data.repository.StorageUnavailableException
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.source.StorageSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AnalyzerStep {
    SELECTION,
    SCANNING,
    RESULTS
}

/** One row of the breakdown, and one slice of the chart. */
@Immutable
data class CategoryUsage(
    val category: AnalyzerCategory,
    val bytes: Long,
    /** [bytes] as a fraction of the volume's used space, in 0f..1f. */
    val fraction: Float
)

@Immutable
data class AnalyzerUiState(
    val isLoadingStorages: Boolean = true,
    val storages: List<StorageDevice> = emptyList(),
    val selectedPath: String? = null,
    val step: AnalyzerStep = AnalyzerStep.SELECTION,
    /** The used space of the volume being scanned, captured when the scan started. */
    val usedBytes: Long = 0L,
    val scannedBytes: Long = 0L,
    val fileCount: Int = 0,
    val currentFolder: String = "",
    val categories: List<CategoryUsage> = emptyList(),
    /**
     * Whether the "stop scanning?" prompt is up. Deliberately a flag on the scanning step rather
     * than a step of its own: the walk keeps running behind the dialog, so dismissing it resumes
     * nothing — there is nothing to resume.
     */
    val showCancelConfirmation: Boolean = false,
    /** A one-shot message for the screen to show and then clear through [AnalyzerViewModel.errorShown]. */
    @param:StringRes val errorResId: Int? = null
) {
    val selectedStorage: StorageDevice? get() = storages.firstOrNull { it.path == selectedPath }

    /** The share of the volume that is in use, in 0f..1f — the figure at the centre of the chart. */
    val usedFraction: Float
        get() = selectedStorage?.let { storage ->
            if (storage.totalBytes <= 0L) 0f
            else (usedBytes.toFloat() / storage.totalBytes).coerceIn(0f, 1f)
        } ?: 0f
}

class AnalyzerViewModel(
    private val storageRepository: StorageRepository,
    private val analyzerRepository: AnalyzerRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyzerUiState())
    val uiState: StateFlow<AnalyzerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            val storages = withContext(ioDispatcher) { storageRepository.getStorages() }

            _uiState.update { state ->
                state.copy(
                    isLoadingStorages = false,
                    storages = storages,
                    // A device with one volume has nothing to choose between, so the choice is made
                    // for the user and the screen opens on the confirmation they still have to give.
                    selectedPath = storages.singleOrNull()?.path
                )
            }
        }
    }

    fun selectStorage(path: String) {
        _uiState.update { it.copy(selectedPath = path) }
    }

    fun startScan() {
        val storage = _uiState.value.selectedStorage ?: return
        val usedBytes = storage.totalBytes - storage.availableBytes

        scanJob?.cancel()
        // The previous scan's lists describe a volume that is no longer what the screen is about,
        // and they are the largest thing the app holds.
        AnalyzerResultsHolder.clear()
        _uiState.update {
            it.copy(
                step = AnalyzerStep.SCANNING,
                usedBytes = usedBytes,
                scannedBytes = 0L,
                fileCount = 0,
                currentFolder = storage.path,
                categories = emptyList(),
                showCancelConfirmation = false,
                errorResId = null
            )
        }

        scanJob = viewModelScope.launch {
            analyzerRepository.analyze(storage.path)
                .flowOn(ioDispatcher)
                // The volume left mid-walk. The partial tally describes nothing — every directory
                // it never reached would be charted as system space — so it is discarded and the
                // user is sent back to pick a volume, exactly as cancelling does.
                .catch { cause ->
                    if (cause !is StorageUnavailableException) throw cause

                    // Defence in depth rather than a reachable case: the walk raises this before
                    // its completing emission, and startScan cleared on the way in, so there is
                    // nothing held to drop unless a future walk learns to fail after handing over.
                    AnalyzerResultsHolder.clear()
                    _uiState.update { state ->
                        state.copy(
                            step = AnalyzerStep.SELECTION,
                            scannedBytes = 0L,
                            fileCount = 0,
                            currentFolder = "",
                            categories = emptyList(),
                            showCancelConfirmation = false,
                            errorResId = R.string.analyzer_error_storage_unavailable
                        )
                    }
                }
                .collect { progress ->
                    // Computed and handed over outside the update below: that lambda is retried on
                    // a losing compare-and-set, and handing the results over is not something to
                    // repeat. usedBytes is fixed when the scan starts and nothing here moves it.
                    val categories = if (progress.isComplete) {
                        breakdown(progress.sizesByType, _uiState.value.usedBytes).also {
                            AnalyzerResultsHolder.store(categoryFiles(it, progress.largestByType))
                        }
                    } else {
                        null
                    }

                    _uiState.update { state ->
                        if (categories != null) {
                            state.copy(
                                step = AnalyzerStep.RESULTS,
                                scannedBytes = progress.scannedBytes,
                                fileCount = progress.fileCount,
                                currentFolder = progress.currentFolder,
                                categories = categories,
                                showCancelConfirmation = false
                            )
                        } else {
                            state.copy(
                                scannedBytes = progress.scannedBytes,
                                fileCount = progress.fileCount,
                                currentFolder = progress.currentFolder
                            )
                        }
                    }
                }
        }
    }

    /** Puts the "stop scanning?" prompt up. The scan is untouched until the user confirms. */
    fun requestCancelScan() {
        if (_uiState.value.step != AnalyzerStep.SCANNING) return
        _uiState.update { it.copy(showCancelConfirmation = true) }
    }

    fun dismissCancelScan() {
        _uiState.update { it.copy(showCancelConfirmation = false) }
    }

    /**
     * Stops the scan and goes back to the volume list, discarding the partial tally. A half-walked
     * volume would draw a chart whose unaccounted remainder swallowed everything not reached yet,
     * which is a wrong answer rather than an incomplete one.
     */
    fun confirmCancelScan() {
        scanJob?.cancel()
        scanJob = null
        AnalyzerResultsHolder.clear()
        _uiState.update {
            it.copy(
                step = AnalyzerStep.SELECTION,
                showCancelConfirmation = false,
                scannedBytes = 0L,
                fileCount = 0,
                currentFolder = "",
                categories = emptyList()
            )
        }
    }

    /** Clears the one-shot error once the screen has shown it. */
    fun errorShown() {
        _uiState.update { it.copy(errorResId = null) }
    }

    /** Returns from the results to the volume list, keeping the volume selected. */
    fun backToSelection() {
        // The listing is only reachable from the results, so leaving them takes the file lists out
        // of reach as well.
        AnalyzerResultsHolder.clear()
        _uiState.update {
            it.copy(
                step = AnalyzerStep.SELECTION,
                scannedBytes = 0L,
                fileCount = 0,
                currentFolder = "",
                categories = emptyList()
            )
        }
    }

    /**
     * The six rows the results screen shows, in [AnalyzerCategory] order.
     *
     * [AnalyzerCategory.SYSTEM] is what [usedBytes] has left over once every scanned file is
     * accounted for: installed apps, the `Android/data` and `Android/obb` trees that are closed even
     * to All Files Access, and filesystem overhead. Floored at zero because a scan can legitimately
     * overshoot — `StatFs` reports whole allocated blocks while `length()` reports apparent sizes,
     * so sparse or hard-linked files can add up past the used total. The six shares then sum to
     * slightly over one, a rounding artefact in the chart, against a negative slice that cannot be
     * drawn at all.
     */
    private fun breakdown(
        sizesByType: Map<SearchFileType, Long>,
        usedBytes: Long
    ): List<CategoryUsage> {
        val scannedBytes = sizesByType.values.sum()

        return AnalyzerCategory.entries.map { category ->
            val bytes = when (val type = category.fileType) {
                null -> (usedBytes - scannedBytes).coerceAtLeast(0L)
                else -> sizesByType[type] ?: 0L
            }

            CategoryUsage(
                category = category,
                bytes = bytes,
                fraction = if (usedBytes <= 0L) 0f else (bytes.toFloat() / usedBytes).coerceIn(0f, 1f)
            )
        }
    }

    /**
     * The breakdown paired with the files behind it, for the categories that have files.
     *
     * [AnalyzerCategory.SYSTEM] is left out: it is the volume's unaccounted remainder, so there is
     * no set of files to list and its row is not one the user can open.
     */
    private fun categoryFiles(
        categories: List<CategoryUsage>,
        largestByType: Map<SearchFileType, List<AnalyzerFileEntry>>
    ): Map<AnalyzerCategory, CategoryFiles> = categories.mapNotNull { usage ->
        val type = usage.category.fileType ?: return@mapNotNull null

        usage.category to CategoryFiles(
            totalBytes = usage.bytes,
            entries = largestByType[type].orEmpty()
        )
    }.toMap()

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val source: StorageSource = AndroidStorageSource(context.applicationContext)
            return AnalyzerViewModel(
                storageRepository = StorageRepository(source),
                analyzerRepository = AnalyzerRepository()
            ) as T
        }
    }
}
