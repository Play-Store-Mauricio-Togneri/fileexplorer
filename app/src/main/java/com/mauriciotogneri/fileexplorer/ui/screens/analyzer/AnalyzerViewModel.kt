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
    /**
     * The capacity of that same volume, captured at the same moment.
     *
     * Held here rather than read back from [selectedStorage] so that every figure the chart draws
     * comes from the one volume the walk measured: the selection is reconciled against the volumes
     * as they now are, so a volume that goes away moves it with nothing the user did.
     *
     * Unlike [usedBytes] it never moves again — deleting files frees space, it does not change what
     * the volume holds.
     */
    val totalBytes: Long = 0L,
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
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
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
        observeResults()
    }

    /**
     * Redraws the chart for files the category listing deleted.
     *
     * Only while the results are up: the scan hands its lists over before this screen moves to
     * [AnalyzerStep.RESULTS], so during a scan the emission that arrives is the scan's own and
     * there is nothing here to correct.
     *
     * [AnalyzerUiState.usedBytes] comes down by what left, which is what keeps the arithmetic
     * whole — the categories are drawn as shares of it, and [AnalyzerCategory.SYSTEM] is whatever
     * it has left over, so a used total that stayed put would hand every deleted byte to the one
     * row that cannot account for it.
     */
    private fun observeResults() {
        viewModelScope.launch {
            AnalyzerResultsHolder.categories.collect { held ->
                val state = _uiState.value
                if (held == null || state.step != AnalyzerStep.RESULTS) return@collect

                val sizesByType = held.mapNotNull { (category, files) ->
                    category.fileType?.let { type -> type to files.totalBytes }
                }.toMap()

                val scannedBefore = state.categories.sumOf { usage ->
                    if (usage.category.fileType != null) usage.bytes else 0L
                }
                val freedBytes = scannedBefore - sizesByType.values.sum()
                if (freedBytes <= 0L) return@collect

                // The volumes as they now measure, not as they measured when this screen opened.
                // Both figures the list states are stale by what was just freed, and the card the
                // user lands back on draws its bar from them — so leaving them would have that
                // card still counting the space the user just reclaimed as in use.
                val storages = withContext(ioDispatcher) { storageRepository.getStorages() }
                val usedBytes = (state.usedBytes - freedBytes).coerceAtLeast(0L)

                _uiState.update {
                    it.copy(
                        storages = storages,
                        selectedPath = resolvedPath(it.selectedPath, storages),
                        usedBytes = usedBytes,
                        categories = breakdown(sizesByType, usedBytes)
                    )
                }
            }
        }
    }

    fun selectStorage(path: String) {
        _uiState.update { it.copy(selectedPath = path) }
    }

    fun startScan() {
        val selectedPath = _uiState.value.selectedStorage?.path ?: return

        scanJob?.cancel()
        // The previous scan's lists describe a volume that is no longer what the screen is about,
        // and they are the largest thing the app holds.
        AnalyzerResultsHolder.clear()

        scanJob = viewModelScope.launch {
            // The volume as it measures now, rather than as it measured when this screen opened.
            // Free space moves while the analyzer is up — the category listing offers a file's
            // folder to open and delete from, and the rest of the device goes on writing — and a
            // used total carried over from the opening read would be too high by exactly what was
            // freed since, with every one of those bytes handed to AnalyzerCategory.SYSTEM. A scan
            // runs for minutes, so a second one is always well after that read.
            val storages = withContext(ioDispatcher) { storageRepository.getStorages() }
            val storage = storages.firstOrNull { it.path == selectedPath }

            if (storage == null) {
                // The volume left before the walk began: the same answer as leaving mid-walk,
                // reached without walking a dead path to arrive at it.
                storageUnavailable(storages)
                return@launch
            }

            _uiState.update {
                it.copy(
                    storages = storages,
                    step = AnalyzerStep.SCANNING,
                    usedBytes = storage.totalBytes - storage.availableBytes,
                    totalBytes = storage.totalBytes,
                    scannedBytes = 0L,
                    fileCount = 0,
                    currentFolder = storage.path,
                    categories = emptyList(),
                    showCancelConfirmation = false,
                    errorResId = null
                )
            }

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

                    val refreshed = withContext(ioDispatcher) { storageRepository.getStorages() }
                    storageUnavailable(refreshed)
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

    /**
     * Releases the scan's file lists.
     *
     * Here rather than in the activity's `onDestroy`, because a view model is cleared exactly when
     * the results can no longer be reached: the user finished with the analyzer, or the system
     * destroyed it to reclaim memory. A rotation clears nothing, so a category listing on top of a
     * rotating analyzer still finds what it was opened with. The lists are the largest thing the
     * app holds, and they are worth the most in precisely the case the activity's own guard missed
     * — the system took the screen away because memory had run short.
     */
    override fun onCleared() {
        AnalyzerResultsHolder.clear()
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
     * Sends the user back to the volume list because the volume they picked is not there, under
     * [storages] as it now measures rather than as this screen opened on it.
     *
     * Re-read by the caller rather than kept, because what brought it here was the volume going
     * away: the list the user lands back on would otherwise go on offering it, under the capacity
     * it had while it was still there, and every retry would walk the same dead path to the same
     * message.
     */
    private fun storageUnavailable(storages: List<StorageDevice>) {
        _uiState.update { state ->
            state.copy(
                storages = storages,
                selectedPath = resolvedPath(state.selectedPath, storages),
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

    /**
     * [path], unless [storages] no longer holds the volume it names — in which case the rule [init]
     * opens on applies again: one volume is no choice at all, so it is chosen.
     *
     * A selection nothing on the list resolves to is one [startScan] declines without a word and
     * the list draws no card as chosen for, so it is dropped rather than left pointing at a volume
     * that has gone.
     */
    private fun resolvedPath(path: String?, storages: List<StorageDevice>): String? =
        path?.takeIf { selected -> storages.any { it.path == selected } }
            ?: storages.singleOrNull()?.path

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
