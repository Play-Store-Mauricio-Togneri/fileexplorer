package com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.PdfLink
import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.model.PdfRectPt
import com.mauriciotogneri.fileexplorer.data.model.PdfSearchMatch
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.locationsCacheDataStore
import com.mauriciotogneri.fileexplorer.data.source.AndroidPdfDocumentOpener
import com.mauriciotogneri.fileexplorer.data.source.DataStoreLocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.source.PdfDocumentOpener
import com.mauriciotogneri.fileexplorer.data.source.PdfDocumentSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.FileExtensionUtil
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerMode
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerSupport
import com.mauriciotogneri.fileexplorer.data.util.ThumbnailFileType
import com.mauriciotogneri.fileexplorer.data.util.deleteFailureFor
import com.mauriciotogneri.fileexplorer.data.util.isUnreadablePdf
import com.mauriciotogneri.fileexplorer.data.util.scrubbed
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** What the viewer's body shows. */
@Immutable
sealed interface PdfViewerContent {
    data object Loading : PdfViewerContent

    /** The document is encrypted. [wrongPassword] when the last password tried did not unlock it. */
    data class PasswordRequired(val wrongPassword: Boolean) : PdfViewerContent

    /** The document is open; one entry per page, in points. */
    data class Loaded(val pageSizes: List<PdfPageSize>) : PdfViewerContent

    /** Missing, unreadable, corrupted or not a PDF. */
    data object LoadError : PdfViewerContent

    /** Encrypted, on a device whose renderer cannot unlock it (see [PdfViewerMode.VIEW_ONLY]). */
    data object PasswordUnsupported : PdfViewerContent
}

@Immutable
data class PdfSearchState(
    val active: Boolean = false,
    val query: String = "",
    /** The query the current [matches] belong to, or null before the first search. */
    val searchedQuery: String? = null,
    val matches: List<PdfSearchMatch> = emptyList(),
    val currentIndex: Int = -1,
    val inProgress: Boolean = false
)

@Immutable
data class PdfViewerUiState(
    val fileName: String = "",
    val file: FileItem? = null,
    val mode: PdfViewerMode = PdfViewerMode.VIEW_ONLY,
    val content: PdfViewerContent = PdfViewerContent.Loading,
    val search: PdfSearchState = PdfSearchState()
)

sealed interface PdfViewerUiEvent {
    data object Finish : PdfViewerUiEvent
    data class ShowToast(val messageResId: Int) : PdfViewerUiEvent

    /** Bring [page] (0-based) into view, with [area] on it when one is known. */
    data class ScrollToPage(val page: Int, val area: PdfRectPt? = null) : PdfViewerUiEvent

    /** Open [url], already checked against [PdfViewerSupport.isAllowedExternalLink]. */
    data class OpenExternalLink(val url: String) : PdfViewerUiEvent
}

/**
 * The in-app fallback PDF viewer. Every call into the document runs on [rendererDispatcher], which
 * must run one task at a time: the platform renderer is not thread-safe and allows a single open
 * page. Each call opens and closes its page without suspending in between, so the confinement is
 * all the locking the document needs.
 */
class PdfViewerViewModel(
    private val filePath: String,
    private val source: String,
    application: Application,
    private val fileRepository: FileRepository,
    private val opener: PdfDocumentOpener,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val rendererDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val bitmapFactory: (Int, Int) -> Bitmap = { width, height -> createBitmap(width, height) },
    private val cacheBudgetBytes: Long = PdfViewerSupport.cacheBudgetBytes(Runtime.getRuntime().maxMemory())
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    private val _state = MutableStateFlow(
        PdfViewerUiState(fileName = File(filePath).name, mode = opener.mode)
    )
    val state: StateFlow<PdfViewerUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PdfViewerUiEvent>()
    val events: SharedFlow<PdfViewerUiEvent> = _events.asSharedFlow()

    // Confined to rendererDispatcher.
    private var document: PdfDocumentSource? = null
    private var closed = false
    private val linksByPage = HashMap<Int, List<PdfLink>>()

    // Rendered whole pages, least recently used first, bounded by bytes rather than entries since a
    // page's size depends on its shape. Evicted bitmaps are left to the GC rather than recycled: a
    // page on screen may still be drawing one.
    private val pageCache = LinkedHashMap<PageKey, CachedPage>(16, 0.75f, true)
    private var pageCacheBytes = 0L

    private var searchJob: Job? = null

    init {
        loadFileItem()
        load(password = null)
    }

    private fun loadFileItem() {
        viewModelScope.launch {
            val item = withContext(ioDispatcher) { FileItem.from(File(filePath)) }
            _state.update { it.copy(file = item) }
        }
    }

    private fun load(password: String?) {
        viewModelScope.launch {
            _state.update { it.copy(content = PdfViewerContent.Loading) }
            val pageSizes = try {
                withContext(rendererDispatcher) { openDocument(password) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onLoadFailed(e, password)
                return@launch
            }
            if (pageSizes == null) {
                // A document with no pages has nothing to show: treat it like an unreadable one.
                AnalyticsTracker.trackPdfViewerLoadError(source, REASON_UNREADABLE)
                _state.update { it.copy(content = PdfViewerContent.LoadError) }
                return@launch
            }
            _state.update { it.copy(content = PdfViewerContent.Loaded(pageSizes)) }
            // Rendered pages are the largest thing this screen holds. Recorded so an
            // OutOfMemoryError report — whose stack names an unrelated allocation — shows how big a
            // document was open behind it.
            ErrorReporter.setCount(KEY_PDF_PAGES, pageSizes.size)
            ErrorReporter.recordHeap()
            trackOpened()
        }
    }

    /** Opens the document and reads every page size, or returns null for an empty document. */
    private fun openDocument(password: String?): List<PdfPageSize>? {
        val opened = opener.open(File(filePath), password)
        if (closed) {
            // The screen went away while this was opening; onCleared has already run.
            opened.close()
            throw CancellationException("viewer closed")
        }
        val sizes = try {
            val count = opened.pageCount
            val sizes = ArrayList<PdfPageSize>(count.coerceAtLeast(0))
            for (index in 0 until count) {
                sizes += pageSizeOrFallback(opened, index, sizes.lastOrNull())
            }
            sizes
        } catch (e: Exception) {
            opened.close()
            throw e
        }
        if (sizes.isEmpty()) {
            opened.close()
            return null
        }
        document = opened
        return sizes
    }

    /**
     * A page whose size cannot be read still takes a slot, sized like its neighbour, so one broken
     * page shows its own error instead of costing the whole document.
     */
    private fun pageSizeOrFallback(
        document: PdfDocumentSource,
        index: Int,
        previous: PdfPageSize?
    ): PdfPageSize {
        val size = try {
            document.pageSize(index)
        } catch (e: Exception) {
            if (!isUnreadablePdf(e)) {
                ErrorReporter.warning(e.scrubbed(), "pdf_viewer_page_size", ThumbnailFileType.PDF)
            }
            null
        }
        return size?.takeIf { it.width > 0 && it.height > 0 }?.let(PdfViewerSupport::layoutPageSize)
            ?: previous
            ?: FALLBACK_PAGE_SIZE
    }

    private fun onLoadFailed(e: Exception, password: String?) {
        // Checked before isUnreadablePdf, which matches SecurityException too: an encrypted
        // document is a question for the user, not a broken file.
        if (e is SecurityException) {
            if (opener.mode == PdfViewerMode.FULL) {
                _state.update {
                    it.copy(content = PdfViewerContent.PasswordRequired(wrongPassword = password != null))
                }
            } else {
                AnalyticsTracker.trackPdfViewerLoadError(source, REASON_PASSWORD_UNSUPPORTED)
                _state.update { it.copy(content = PdfViewerContent.PasswordUnsupported) }
            }
            return
        }
        if (isUnreadablePdf(e)) {
            // Missing, corrupted or not a PDF: expected, and already shown in the error UI.
            AnalyticsTracker.trackPdfViewerLoadError(source, REASON_UNREADABLE)
        } else {
            ErrorReporter.warning(e.scrubbed(), "pdf_viewer_open", ThumbnailFileType.PDF)
            AnalyticsTracker.trackPdfViewerLoadError(source, REASON_ERROR)
        }
        _state.update { it.copy(content = PdfViewerContent.LoadError) }
    }

    private suspend fun trackOpened() {
        val item = _state.value.file ?: withContext(ioDispatcher) { FileItem.from(File(filePath)) }
        IntentUtil.trackRecentFile(context, item)
        AnalyticsTracker.trackFileOpened(
            FileExtensionUtil.getExtension(filePath),
            item.mimeType,
            source
        )
        AnalyticsTracker.trackPdfViewerOpened(source, opener.mode.name.lowercase(Locale.US))
    }

    // ==================== Password ====================

    /** Tries [password]. It is handed to the renderer and never kept. */
    fun submitPassword(password: String) {
        if (_state.value.content !is PdfViewerContent.PasswordRequired) return
        load(password)
    }

    /** The user would rather not unlock the document: there is nothing else to show, so leave. */
    fun cancelPassword() {
        viewModelScope.launch { _events.emit(PdfViewerUiEvent.Finish) }
    }

    // ==================== Rendering ====================

    /** The cached render of page [index] at [widthPx], if there is one. Never renders. */
    fun cachedPageBitmap(index: Int, widthPx: Int): Bitmap? = synchronized(pageCache) {
        pageCache[PageKey(index, widthPx)]?.bitmap
    }

    /**
     * Page [index] rendered [widthPx] wide, from the cache when possible. Null when the page cannot
     * be rendered — the page shows its own error — or the viewer is closing.
     */
    suspend fun pageBitmap(index: Int, widthPx: Int): Bitmap? {
        cachedPageBitmap(index, widthPx)?.let { return it }
        val pageSizes = loadedPageSizes() ?: return null
        val pageSize = pageSizes.getOrNull(index) ?: return null
        return withContext(rendererDispatcher) {
            // Another request for the same page may have rendered it while this one queued.
            cachedPageBitmap(index, widthPx)?.let { return@withContext it }
            val size = PdfViewerSupport.renderSize(pageSize.width, pageSize.height, widthPx, 1f)
            val bitmap = renderPage { document ->
                bitmapFactory(size.width, size.height).also {
                    document.render(index, it, size.width.toFloat() / pageSize.width, 0f, 0f)
                }
            } ?: return@withContext null
            putInCache(PageKey(index, widthPx), CachedPage(bitmap, PdfViewerSupport.byteCount(size)))
            bitmap
        }
    }

    /**
     * The part of page [index] at [zoom] that is on screen, rendered sharp. The page is laid out
     * [pageWidthPx] wide at zoom 1, and the region is [left], [top], [width], [height] in those
     * same unzoomed pixels; the bitmap covers it at [zoom] times the density, within the bitmap caps.
     * Not cached: the next gesture moves the region.
     */
    suspend fun regionBitmap(
        index: Int,
        pageWidthPx: Int,
        zoom: Float,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ): Bitmap? {
        if (width <= 0f || height <= 0f || pageWidthPx <= 0) return null
        val pageSizes = loadedPageSizes() ?: return null
        val pageSize = pageSizes.getOrNull(index) ?: return null
        val clampedZoom = zoom.coerceIn(1f, PdfViewerSupport.MAX_ZOOM)
        val size = PdfViewerSupport.fitWithin(width * clampedZoom, height * clampedZoom)
        // Bitmap pixels per unzoomed layout pixel: the zoom, less whatever the caps took off it.
        // Applied to the whole page, so the region and the page it is cut from stay aligned.
        val density = size.width / width
        val scale = pageWidthPx * density / pageSize.width
        return withContext(rendererDispatcher) {
            renderPage { document ->
                bitmapFactory(size.width, size.height).also {
                    document.render(index, it, scale, left * density, top * density)
                }
            }
        }
    }

    /** Runs [render] against the open document, turning a page that cannot be drawn into null. */
    private inline fun renderPage(render: (PdfDocumentSource) -> Bitmap): Bitmap? {
        val document = document ?: return null
        return try {
            render(document)
        } catch (e: Exception) {
            if (!isUnreadablePdf(e)) {
                ErrorReporter.warning(e.scrubbed(), "pdf_viewer_render", ThumbnailFileType.PDF)
            }
            null
        }
    }

    private fun putInCache(key: PageKey, page: CachedPage) = synchronized(pageCache) {
        pageCache.put(key, page)?.let { pageCacheBytes -= it.bytes }
        pageCacheBytes += page.bytes
        val iterator = pageCache.entries.iterator()
        // The newest entry stays even when it alone is over budget: it is the page on screen.
        while (pageCacheBytes > cacheBudgetBytes && pageCache.size > 1 && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest.key == key) continue
            pageCacheBytes -= eldest.value.bytes
            iterator.remove()
        }
    }

    private fun loadedPageSizes(): List<PdfPageSize>? =
        (_state.value.content as? PdfViewerContent.Loaded)?.pageSizes

    // ==================== Navigation ====================

    /** Scrolls to page [index], 0-based. Out-of-range pages are ignored. */
    fun goToPage(index: Int) {
        val count = loadedPageSizes()?.size ?: return
        if (index !in 0 until count) return
        viewModelScope.launch { _events.emit(PdfViewerUiEvent.ScrollToPage(index)) }
    }

    /**
     * A tap at ([xPt], [yPt]) points on page [index]. Follows a link there, when the renderer can
     * see links: a jump within the document scrolls to its page, and a web or mail link opens
     * outside the app. Every other scheme a document can carry is dropped.
     */
    fun onPageTapped(index: Int, xPt: Float, yPt: Float) {
        if (opener.mode != PdfViewerMode.FULL) return
        val count = loadedPageSizes()?.size ?: return
        if (index !in 0 until count) return
        viewModelScope.launch {
            val links = withContext(rendererDispatcher) { linksOn(index) }
            val link = links.firstOrNull { candidate ->
                candidate.rects.any { it.contains(xPt, yPt) }
            } ?: return@launch
            when (link) {
                is PdfLink.GoTo -> if (link.page in 0 until count) {
                    AnalyticsTracker.trackPdfViewerLinkOpened(LINK_INTERNAL)
                    _events.emit(PdfViewerUiEvent.ScrollToPage(link.page))
                }
                is PdfLink.External -> if (PdfViewerSupport.isAllowedExternalLink(link.url)) {
                    AnalyticsTracker.trackPdfViewerLinkOpened(LINK_EXTERNAL)
                    _events.emit(PdfViewerUiEvent.OpenExternalLink(link.url))
                }
            }
        }
    }

    private fun linksOn(index: Int): List<PdfLink> {
        linksByPage[index]?.let { return it }
        val document = document ?: return emptyList()
        val links = try {
            document.links(index)
        } catch (e: Exception) {
            if (!isUnreadablePdf(e)) {
                ErrorReporter.warning(e.scrubbed(), "pdf_viewer_links", ThumbnailFileType.PDF)
            }
            emptyList()
        }
        linksByPage[index] = links
        return links
    }

    // ==================== Search ====================

    fun openSearch() {
        if (opener.mode != PdfViewerMode.FULL || loadedPageSizes() == null) return
        _state.update { it.copy(search = it.search.copy(active = true)) }
    }

    fun closeSearch() {
        searchJob?.cancel()
        searchJob = null
        _state.update { it.copy(search = PdfSearchState()) }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(search = it.search.copy(query = query)) }
    }

    /**
     * Searches the whole document for the current query, a page at a time so matches appear as
     * they are found and a new search or closing the bar stops the old one between pages. The
     * first match is scrolled to as soon as it turns up.
     */
    fun submitSearch() {
        if (opener.mode != PdfViewerMode.FULL) return
        val pageCount = loadedPageSizes()?.size ?: return
        val query = _state.value.search.query.trim()
        searchJob?.cancel()
        if (query.isEmpty()) {
            _state.update { it.copy(search = it.search.copy(searchedQuery = null, matches = emptyList(), currentIndex = -1, inProgress = false)) }
            return
        }
        _state.update {
            it.copy(
                search = it.search.copy(
                    searchedQuery = query,
                    matches = emptyList(),
                    currentIndex = -1,
                    inProgress = true
                )
            )
        }
        searchJob = viewModelScope.launch {
            var total = 0
            for (page in 0 until pageCount) {
                ensureActive()
                val found = withContext(rendererDispatcher) { searchPage(page, query) }
                // A newer search may have replaced this one while the page was being searched; its
                // matches must not land in the newer search's results.
                ensureActive()
                if (found.isEmpty()) continue
                val matches = found.map { PdfSearchMatch(page, it) }
                total += matches.size
                val isFirst = _state.value.search.currentIndex < 0
                _state.update {
                    it.copy(
                        search = it.search.copy(
                            matches = it.search.matches + matches,
                            currentIndex = if (isFirst) 0 else it.search.currentIndex
                        )
                    )
                }
                if (isFirst) {
                    scrollTo(matches.first())
                }
            }
            _state.update { it.copy(search = it.search.copy(inProgress = false)) }
            AnalyticsTracker.trackPdfViewerSearch(total)
        }
    }

    private fun searchPage(page: Int, query: String): List<List<PdfRectPt>> {
        val document = document ?: return emptyList()
        return try {
            document.search(page, query)
        } catch (e: Exception) {
            if (!isUnreadablePdf(e)) {
                ErrorReporter.warning(e.scrubbed(), "pdf_viewer_search", ThumbnailFileType.PDF)
            }
            emptyList()
        }
    }

    fun nextMatch() = moveMatch(1)

    fun previousMatch() = moveMatch(-1)

    private fun moveMatch(step: Int) {
        val search = _state.value.search
        if (search.matches.isEmpty()) return
        val next = Math.floorMod(search.currentIndex + step, search.matches.size)
        _state.update { it.copy(search = it.search.copy(currentIndex = next)) }
        viewModelScope.launch { scrollTo(search.matches[next]) }
    }

    private suspend fun scrollTo(match: PdfSearchMatch) {
        _events.emit(PdfViewerUiEvent.ScrollToPage(match.page, match.rects.firstOrNull()))
    }

    // ==================== Share / delete ====================

    fun onShareClicked() {
        AnalyticsTracker.trackPdfViewerShare(source)
    }

    fun onDeleteConfirmed() {
        viewModelScope.launch {
            // Stat'd here rather than read from state.file, which holds the stat taken when the
            // screen opened and is never refreshed. See ImageViewerViewModel.onDeleteConfirmed.
            val item = withContext(ioDispatcher) { FileItem.from(File(filePath)) }
            // This screen only ever identifies one file: refuse anything else.
            if (item.isDirectory) {
                _events.emit(PdfViewerUiEvent.ShowToast(R.string.delete_error))
                return@launch
            }
            val result = fileRepository.delete(listOf(item))
            if (result.success) {
                if (result.removedPaths.isNotEmpty()) {
                    MediaStoreUtil.notifyDeleted(context, result.removedPaths)
                }
                MediaStoreUtil.scanFiles(context, result.alreadyAbsentPaths)
                _events.emit(PdfViewerUiEvent.Finish)
            } else {
                _events.emit(PdfViewerUiEvent.ShowToast(deleteFailureFor(result.failureErrno).messageResId))
            }
        }
    }

    // ==================== Lifecycle ====================

    override fun onCleared() {
        searchJob?.cancel()
        // viewModelScope is already cancelled here, and the close has to queue behind whatever
        // render is running on the renderer thread rather than race it.
        CoroutineScope(rendererDispatcher + NonCancellable).launch {
            closed = true
            val open = document
            document = null
            linksByPage.clear()
            try {
                open?.close()
            } catch (e: Exception) {
                ErrorReporter.warning(e.scrubbed(), "pdf_viewer_close", ThumbnailFileType.PDF)
            }
        }
        synchronized(pageCache) {
            pageCache.clear()
            pageCacheBytes = 0
        }
    }

    private data class PageKey(val index: Int, val widthPx: Int)

    private class CachedPage(val bitmap: Bitmap, val bytes: Long)

    class Factory(
        private val filePath: String,
        private val source: String,
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // Drops the cached home-screen location sizes whenever this screen deletes a file.
            val locationsCacheSource = DataStoreLocationsCacheSource(application.locationsCacheDataStore)
            return PdfViewerViewModel(
                filePath = filePath,
                source = source,
                application = application,
                fileRepository = FileRepository { locationsCacheSource.clearCache() },
                opener = AndroidPdfDocumentOpener()
            ) as T
        }
    }

    companion object {
        private const val KEY_PDF_PAGES = "pdf_pages"
        private const val REASON_UNREADABLE = "unreadable"
        private const val REASON_PASSWORD_UNSUPPORTED = "password_unsupported"
        private const val REASON_ERROR = "error"
        private const val LINK_INTERNAL = "internal"
        private const val LINK_EXTERNAL = "external"

        /** US Letter, for a page whose size cannot be read and has no neighbour to borrow from. */
        private val FALLBACK_PAGE_SIZE = PdfPageSize(612, 792)
    }
}
