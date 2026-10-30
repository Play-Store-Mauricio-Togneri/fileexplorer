package com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerResultsHolder
import com.mauriciotogneri.fileexplorer.data.repository.CategoryFiles
import com.mauriciotogneri.fileexplorer.data.repository.DeleteProgress
import com.mauriciotogneri.fileexplorer.data.repository.DeleteResult
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.locationsCacheDataStore
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreLocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.deleteFailureFor
import com.mauriciotogneri.fileexplorer.data.util.isSymlink
import com.mauriciotogneri.fileexplorer.data.util.reportableErrno
import com.mauriciotogneri.fileexplorer.data.util.scrubbed
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import com.mauriciotogneri.fileexplorer.util.ReviewPrompt
import com.mauriciotogneri.fileexplorer.util.UncompressEvent
import com.mauriciotogneri.fileexplorer.util.UncompressHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class AnalyzerCategoryUiState(
    /** The category's share of the volume, as its chart row stated it. */
    val totalBytes: Long = 0L,
    /** The pages loaded so far, biggest file first. */
    val files: List<FileItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoadingPage: Boolean = false,
    val hasMore: Boolean = true,
    val itemsToDelete: List<FileItem> = emptyList(),
    val deleteProgress: DeleteProgress? = null,
    val itemToUncompress: FileItem? = null,
    val uncompressEntryCount: Int = 0,
    val isPasswordProtected: Boolean = false,
    val uncompressProgress: UncompressProgress? = null,
    val pendingApkInstall: FileItem? = null
) {
    /**
     * Only once the first page has come back and brought nothing. Until then the list is empty
     * because it has not been read yet, which is a different thing to show.
     */
    val isEmpty: Boolean get() = files.isEmpty() && !hasMore

    val isSelectionMode: Boolean get() = selectedPaths.isNotEmpty()
    val selectedCount: Int get() = selectedPaths.size

    /**
     * Every loaded row is picked. The listing is paged, so this is what the screen has, not what
     * the category holds: a page arriving behind a full selection makes it false again, which is
     * the toolbar offering to take in the rows that just appeared.
     */
    val allSelected: Boolean get() = files.isNotEmpty() && selectedPaths.size == files.size

    // selectedPaths is small; resolve it with a single pass over files (cached per instance)
    // instead of building a map of every row just to look a few up.
    val selectedFiles: List<FileItem> by lazy {
        if (selectedPaths.isEmpty()) emptyList() else files.filter { it.path in selectedPaths }
    }
}

/** One-time UI events emitted by the ViewModel. */
sealed interface AnalyzerCategoryUiEvent {
    data class ShowToastRes(@param:StringRes val messageResId: Int) : AnalyzerCategoryUiEvent
    data class ShowDeletePartialSuccess(val deleted: Int, val failed: Int) : AnalyzerCategoryUiEvent
}

/**
 * Lists one analyzer category's files, a page at a time.
 *
 * The scan retained a path and a size per file and nothing else, so everything a row draws — the
 * name, the MIME type behind its icon, the timestamps — has to be read back from disk. That read
 * is why the list is paged at all: [FileItem.from] costs a `readAttributes` per file, so a category
 * of ten thousand files would spend ten thousand syscalls before showing a single row. A page pays
 * a hundred.
 *
 * The size a row displays is the one the scan measured, not the one the read-back reports. They are
 * the same figure for any file nobody touched in between, and using the scanned one keeps a row's
 * label agreeing with the position it was sorted into.
 *
 * Deleting is answered from the same list rather than by re-scanning: the rows go, the category's
 * total comes down by their sizes, and the same subtraction reaches the chart through
 * [AnalyzerResultsHolder]. Tapping a row opens the file the way the folder screen does, so the
 * uncompress and pending-APK state that path can produce is held here too.
 */
class AnalyzerCategoryViewModel(
    application: Application,
    private val category: AnalyzerCategory,
    categoryFiles: CategoryFiles?,
    private val fileRepository: FileRepository,
    storageRepository: StorageRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    /**
     * Whether a scan's results were behind this screen when it was built.
     *
     * False means [AnalyzerResultsHolder] held nothing at that moment: the process was killed and
     * the task restored, or the analyzer released its lists before this screen was ever composed.
     * There is no scan to report on, so the screen closes rather than drawing an empty category —
     * which is a different thing, and one this view model reports through [AnalyzerCategoryUiState.isEmpty].
     */
    val hasResults: Boolean = categoryFiles != null

    /** The scan's list for this category, less whatever has been deleted from this screen. */
    private var entries: List<AnalyzerFileEntry> = categoryFiles?.entries.orEmpty()

    /**
     * How many of [entries] the pages read so far have consumed, which is what the next page
     * starts from. Not `files.size`: a delete takes rows out of the list without making their
     * entries unread, and paging from the row count would then re-read entries already shown.
     */
    private var loadedEntries = 0

    private val _uiState = MutableStateFlow(
        AnalyzerCategoryUiState(totalBytes = categoryFiles?.totalBytes ?: 0L)
    )
    val uiState: StateFlow<AnalyzerCategoryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AnalyzerCategoryUiEvent>()
    val events: SharedFlow<AnalyzerCategoryUiEvent> = _events.asSharedFlow()

    /** The running walked delete, which the progress dialog's Cancel stops. */
    private var deleteJob: Job? = null

    // Extracts beside the archive, the way search does: this screen lists files from all over the
    // volume and has no folder of its own to put the contents in.
    private var currentUncompressTarget: String = ""

    private val uncompressHandler = UncompressHandler(
        context = context,
        scope = viewModelScope,
        fileRepository = fileRepository,
        getTargetDirectory = { currentUncompressTarget },
        getAllowedRoots = { storageRepository.getStorages().map { it.path } }
    )

    init {
        observeUncompressHandler()
        loadNextPage()
    }

    private fun observeUncompressHandler() {
        viewModelScope.launch {
            uncompressHandler.state.collect { uncompressState ->
                _uiState.update {
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
                        _events.emit(AnalyzerCategoryUiEvent.ShowToastRes(event.messageResId))
                    }
                    // The extracted files land beside the archive, which is somewhere else on the
                    // volume — this listing is unaffected by them.
                    is UncompressEvent.ExtractionComplete -> { }
                }
            }
        }
    }

    /**
     * Appends the next page, or does nothing if one is already in flight or the end has been
     * reached. The screen asks on every composition of its trailing item, and a list that is being
     * scrolled hard composes it more than once.
     */
    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoadingPage || !state.hasMore) return

        _uiState.update { it.copy(isLoadingPage = true) }

        viewModelScope.launch {
            // The list this page is read against, captured before the read rather than read back
            // off the field afterwards. A delete landing while the page is on disk replaces
            // [entries] and rewinds [loadedEntries] — and a delete is free to land, because
            // nothing blocks the list while one runs: clearing the selection drops the action bar,
            // which grows the list by a bar's height and can bring the trailing loader into
            // composition on its own. Indexing the field from the IO thread would then read a
            // window past the end of the shorter list.
            val source = entries
            val from = loadedEntries
            val to = minOf(from + PAGE_SIZE, source.size)

            val page = withContext(ioDispatcher) {
                source.subList(from, to).map { entry ->
                    FileItem.from(File(entry.path)).copy(size = entry.size)
                }
            }

            if (source !== entries) {
                // A delete rewrote the list under the read. The page holds entries that may no
                // longer be listed and starts at a cursor that has since moved, so committing it
                // would both show deleted rows and skip the entries the recount stepped back over.
                // Dropped, and asked for again against the list as it now stands.
                _uiState.update { it.copy(isLoadingPage = false) }
                loadNextPage()
                return@launch
            }

            loadedEntries = to
            _uiState.update {
                it.copy(
                    files = it.files + page,
                    isLoadingPage = false,
                    hasMore = to < entries.size
                )
            }
        }
    }

    fun toggleSelection(file: FileItem) {
        _uiState.update { state ->
            val newSelected = if (file.path in state.selectedPaths) {
                state.selectedPaths - file.path
            } else {
                state.selectedPaths + file.path
            }
            state.copy(selectedPaths = newSelected)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedPaths = state.files.mapTo(mutableSetOf()) { it.path })
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet()) }
    }

    fun showDeleteConfirmDialog(files: List<FileItem>) {
        _uiState.update { it.copy(itemsToDelete = files) }
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.update { it.copy(itemsToDelete = emptyList()) }
    }

    /**
     * Deletes the confirmed selection the way the folder screen does: a selection of
     * [DELETE_PROGRESS_THRESHOLD] or more is walked behind a progress dialog the user can cancel,
     * and a smaller one is deleted in one call with nothing shown.
     *
     * The dialog is what keeps the same rows from being deleted twice. Without it nothing on screen
     * changed until the walk and the MediaStore updates behind it had finished: the selection was
     * already cleared, the rows stood there to be selected again, and a select-all over a page was
     * routinely confirmed a second time, its walk finding every file already gone.
     *
     * Counted in rows rather than in the walked nodes the folder screen counts: the scan classifies
     * files and lists no directories, so a row stands for a single unlink. The repository still
     * deletes recursively, which is what a path that has become a directory since the scan would
     * get — the same staleness every delete in the app lives with, and the reason each outcome is
     * reported rather than assumed.
     */
    fun onDeleteConfirmed() {
        val files = _uiState.value.itemsToDelete
        if (files.isEmpty()) return
        dismissDeleteConfirmDialog()
        clearSelection()

        if (files.size < DELETE_PROGRESS_THRESHOLD) {
            deleteInOneCall(files)
        } else {
            deleteWithProgress(files)
        }
    }

    private fun deleteInOneCall(files: List<FileItem>) {
        val itemCount = files.size

        // NonCancellable from the delete itself, not merely around the bookkeeping after it:
        // the repository's walk is blocking and unlinks the files whatever this scope does, and
        // the cancellation surfaces only when its `withContext(Dispatchers.IO)` resumes — which
        // throws out of the call below and would take everything under it with it. The same
        // reasoning `FileRepository.notifyFilesMutated` is NonCancellable for, with more riding
        // on it here: the listing and the chart read [AnalyzerResultsHolder], which outlives this
        // view model, so a skipped `dropDeleted` leaves the analyzer charging the freed bytes to
        // this category and drawing rows for files that are gone for the rest of the session. The
        // toasts sit inside too and cost nothing there: this flow drops what nobody is
        // subscribed to rather than suspending on it, and a collector still attached in the
        // moment the screen goes takes the value at once.
        viewModelScope.launch {
            withContext(NonCancellable) {
                val result = fileRepository.delete(files)

                // Only a path this app emptied is reported deleted; one that was already gone
                // is scanned instead, which drops its row without touching whatever may occupy
                // the path now. The scan these rows come from is minutes old at best, so the
                // already-absent case is an ordinary one here.
                if (result.removedPaths.isNotEmpty()) {
                    MediaStoreUtil.notifyTreeDeleted(context, result.removedPaths)
                }
                MediaStoreUtil.scanFiles(context, result.alreadyAbsentPaths)

                dropDeleted((result.removedPaths + result.alreadyAbsentPaths).toSet())

                when {
                    result.success -> {
                        AnalyticsTracker.trackDeleteCompleted(
                            itemCount,
                            SOURCE,
                            removedCount = result.removedPaths.size,
                            alreadyAbsentCount = result.alreadyAbsentPaths.size
                        )
                        ReviewPrompt.onActionSucceeded()
                    }

                    // Some of the selection came away and some did not. Calling the whole
                    // thing an error reads as "nothing happened" about a list that just lost most
                    // of its rows.
                    result.clearedCount > 0 -> {
                        reportDeleteFailure(result, "partial")
                        _events.emit(
                            AnalyzerCategoryUiEvent.ShowDeletePartialSuccess(
                                deleted = result.clearedCount,
                                failed = result.failedCount
                            )
                        )
                    }

                    else -> {
                        reportDeleteFailure(result, "all_failed")
                        _events.emit(
                            AnalyzerCategoryUiEvent.ShowToastRes(
                                deleteFailureFor(result.failureErrno).messageResId
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * The folder screen's walked delete, behind its progress dialog.
     *
     * Unlike [deleteInOneCall] this one stops when asked — by the dialog's Cancel, or by the screen
     * going, which cancels [viewModelScope] — and a walk that stopped has removed part of the
     * selection without saying which part: its root lists arrive only with the final emission. The
     * rows are then reconciled against the disk instead, by [dropGone].
     */
    private fun deleteWithProgress(files: List<FileItem>) {
        val itemCount = files.size

        deleteJob = viewModelScope.launch {
            try {
                fileRepository.deleteWithProgress(files)
                    // The flow reports every file it deletes, which is more updates than the
                    // dialog can draw. Conflating samples the latest and still delivers the final
                    // value.
                    .conflate()
                    .collect { progress ->
                        if (progress.isComplete) {
                            // The walk is over and nothing is left to cancel. What remains is
                            // bookkeeping [AnalyzerResultsHolder] depends on, and the holder
                            // outlives this view model.
                            withContext(NonCancellable) {
                                handleDeleteResult(progress, itemCount)
                            }
                        } else {
                            _uiState.update { it.copy(deleteProgress = progress) }
                        }
                    }
            } catch (e: Exception) {
                // Not while a newer delete owns the handle: a cancelled one can finish unwinding
                // after the next has started, and clearing then would hide that one's progress —
                // its only Cancel button — while it goes on deleting.
                if (deleteJob == null || deleteJob === coroutineContext.job) {
                    _uiState.update { it.copy(deleteProgress = null) }
                }

                // NonCancellable because a cancel is what brought most of these here: the screen
                // collects the events, so the emit suspends, and suspending in a cancelled
                // coroutine would throw again before the rows were reconciled.
                withContext(NonCancellable) {
                    if (e is CancellationException) {
                        dropGone(files)
                        _events.emit(AnalyzerCategoryUiEvent.ShowToastRes(R.string.delete_cancelled))
                    } else {
                        AnalyticsTracker.trackOperationFailed("delete", "exception", source = SOURCE)
                        ErrorReporter.error(e.scrubbed(), "delete_files")
                        dropGone(files)
                        _events.emit(AnalyzerCategoryUiEvent.ShowToastRes(R.string.delete_error))
                    }
                }
            } finally {
                // Only its own handle: a cancelled delete can finish unwinding after the next one
                // has started, and clearing that one's would leave its Cancel stopping nothing.
                if (deleteJob === coroutineContext.job) deleteJob = null
            }
        }
    }

    fun cancelDelete() {
        deleteJob?.cancel()
        deleteJob = null
        _uiState.update { it.copy(deleteProgress = null) }
    }

    /**
     * Reads a finished walk the way the folder screen does: the error type is the shape of the
     * failure, the errno its cause, and the toast counts selected rows.
     */
    private suspend fun handleDeleteResult(progress: DeleteProgress, itemCount: Int) {
        // Rows first, dialog second. The MediaStore updates below cost a call per path, and rows
        // left standing while they ran would be selectable again — the double delete the dialog is
        // there to prevent.
        dropDeleted((progress.removedRootPaths + progress.absentRootPaths).toSet())
        _uiState.update { it.copy(deleteProgress = null) }

        // The same split as [deleteInOneCall]: only a root this walk emptied is reported deleted,
        // and one that was already gone is scanned.
        if (progress.removedRootPaths.isNotEmpty()) {
            MediaStoreUtil.notifyTreeDeleted(context, progress.removedRootPaths)
        }
        MediaStoreUtil.scanFiles(context, progress.absentRootPaths)

        val errno = reportableErrno(progress.failureErrno)
        val messageResId = deleteFailureFor(progress.failureErrno).messageResId
        val clearedRoots = progress.removedRootPaths.size + progress.absentRootPaths.size
        val failedRoots = itemCount - clearedRoots

        fun report(outcome: String) = AnalyticsTracker.trackOperationFailed(
            operation = "delete",
            errorType = outcome,
            errno = errno,
            source = SOURCE,
            outcome = outcome
        )

        when {
            progress.failedFiles == 0 && !progress.structuralDeleteFailed -> {
                AnalyticsTracker.trackDeleteCompleted(
                    itemCount,
                    SOURCE,
                    removedCount = progress.removedRootPaths.size,
                    alreadyAbsentCount = progress.absentRootPaths.size
                )
                ReviewPrompt.onActionSucceeded()
            }

            clearedRoots > 0 -> {
                report("partial")
                _events.emit(
                    AnalyzerCategoryUiEvent.ShowDeletePartialSuccess(
                        deleted = clearedRoots,
                        failed = failedRoots
                    )
                )
            }

            progress.failedFiles > 0 -> {
                report("all_failed")
                _events.emit(AnalyzerCategoryUiEvent.ShowToastRes(messageResId))
            }

            else -> {
                report("structural")
                _events.emit(AnalyzerCategoryUiEvent.ShowToastRes(messageResId))
            }
        }
    }

    /**
     * Drops whichever of [files] are no longer on disk, for a walk that stopped before it could say
     * which of them it removed.
     *
     * The folder screen answers the same question by listing its folder again. This list is a scan
     * nobody is going to repeat, so each selected path is checked instead: a stat per row, over a
     * selection that is at most the rows loaded so far. A dangling symlink still answers false to
     * `exists`, so it is kept as the entry it still is. The paths that went are scanned rather than
     * reported deleted — nothing watched them empty, which is what the prefix delete requires.
     */
    private suspend fun dropGone(files: List<FileItem>) {
        val gone = withContext(ioDispatcher) {
            files.map { it.path }.filter { path ->
                val file = File(path)
                !file.exists() && !file.isSymlink()
            }
        }

        dropDeleted(gone.toSet())
        MediaStoreUtil.scanFiles(context, gone)
    }

    /**
     * Takes [paths] out of the listing, off its total, and off the chart behind it.
     *
     * Nothing is re-scanned: the walk that produced these figures took minutes, and the one thing
     * that changed about the volume is known exactly. The rows go, the category's total comes down
     * by their sizes, and [AnalyzerResultsHolder] carries the same subtraction to the chart the
     * user came from.
     *
     * Matched by exact path, not by prefix. A row whose path has become a directory since the scan
     * is deleted recursively, but nothing under it was ever recorded: the walk pushes a directory
     * onto its stack instead of listing it (`AnalyzerRepository`), so an entry is always a leaf and
     * no entry is ever an ancestor of another. The bytes freed under such a path were never on any
     * category's total to take off.
     */
    private fun dropDeleted(paths: Set<String>) {
        if (paths.isEmpty()) return

        val removed = entries.filter { it.path in paths }
        if (removed.isEmpty()) return

        // Counted against the entries already read rather than assumed to be all of them: every
        // deleted file is a row on screen today, but a caller that ever deletes an unread entry
        // would otherwise pull the page window backwards over rows the user has seen.
        loadedEntries = entries.subList(0, loadedEntries).count { it.path !in paths }
        entries = entries.filterNot { it.path in paths }

        AnalyzerResultsHolder.remove(category, paths)

        _uiState.update { state ->
            state.copy(
                totalBytes = (state.totalBytes - removed.sumOf { it.size }).coerceAtLeast(0L),
                files = state.files.filterNot { it.path in paths },
                selectedPaths = state.selectedPaths - paths,
                hasMore = loadedEntries < entries.size
            )
        }

        // A delete that took every loaded row leaves nothing on screen to ask for the next page:
        // the request comes from the trailing loader, the loader is an item of the list, and there
        // is no list. Select-all over a category larger than a page reaches this on two taps, so
        // without the backfill the screen would sit on a loading indicator nobody was going to
        // answer, with the rest of the category unreachable.
        val state = _uiState.value
        if (state.files.isEmpty() && state.hasMore) {
            loadNextPage()
        }
    }

    /**
     * Reports a failed delete with the same fields the folder screen uses: the shape ([outcome])
     * only this caller can tell, the cause the errno names, and the screen.
     */
    private fun reportDeleteFailure(result: DeleteResult, outcome: String) {
        AnalyticsTracker.trackOperationFailed(
            operation = "delete",
            errorType = deleteFailureFor(result.failureErrno).analyticsLabel,
            errno = reportableErrno(result.failureErrno),
            source = SOURCE,
            outcome = outcome
        )
    }

    fun showUncompressDialog(file: FileItem) {
        currentUncompressTarget = file.parentPath
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

    fun setPendingApkInstall(file: FileItem?) {
        _uiState.update { it.copy(pendingApkInstall = file) }
    }

    fun clearPendingApkInstall() {
        _uiState.update { it.copy(pendingApkInstall = null) }
    }

    /**
     * Reads the held results itself rather than being handed them.
     *
     * A factory runs only when no view model survived, which is exactly when the holder is the only
     * place this screen's entries can come from — so reading here cannot disagree with what the
     * screen is built on. Read in the activity instead, the answer would be a snapshot taken before
     * it was known whether it would be used, and a recreation that kept its view model would have
     * to be told apart from one that did not.
     */
    class Factory(
        private val application: Application,
        private val category: AnalyzerCategory
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // Drops the cached home-screen location sizes whenever this screen deletes something,
            // so a card is not left reporting a pre-delete total until the cache TTL lapses.
            val locationsCacheSource = DataStoreLocationsCacheSource(application.locationsCacheDataStore)
            return AnalyzerCategoryViewModel(
                application = application,
                category = category,
                categoryFiles = AnalyzerResultsHolder.filesFor(category),
                fileRepository = FileRepository { locationsCacheSource.clearCache() },
                storageRepository = StorageRepository(AndroidStorageSource(application))
            ) as T
        }
    }

    companion object {
        const val PAGE_SIZE = 100

        /** The folder screen's threshold, so a selection gets the same delete on either screen. */
        private const val DELETE_PROGRESS_THRESHOLD = 10

        /** The screen these events came from, in the vocabulary the other screens report in. */
        private const val SOURCE = "analyzer_category"
    }
}
