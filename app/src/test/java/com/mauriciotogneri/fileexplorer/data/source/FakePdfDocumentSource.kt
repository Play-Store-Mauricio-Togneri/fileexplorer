package com.mauriciotogneri.fileexplorer.data.source

import android.graphics.Bitmap
import com.mauriciotogneri.fileexplorer.data.model.PdfLink
import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.model.PdfRectPt
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * An in-memory PDF document. [renderDelayMs] keeps each call busy for a while, so a test running on
 * real threads can catch two calls overlapping; [maxConcurrentCalls] records whether any did.
 */
class FakePdfDocumentSource(
    val pageSizes: List<PdfPageSize> = List(3) { PdfPageSize(200, 300) },
    private val matches: Map<Int, List<List<PdfRectPt>>> = emptyMap(),
    private val linksByPage: Map<Int, List<PdfLink>> = emptyMap(),
    private val failingPages: Map<Int, Exception> = emptyMap(),
    private val searchFailure: Exception? = null,
    private val renderDelayMs: Long = 0
) : PdfDocumentSource {

    private val active = AtomicInteger()
    val maxConcurrentCalls = AtomicInteger()
    val renders = CopyOnWriteArrayList<Render>()
    val searchedPages = CopyOnWriteArrayList<Pair<Int, String>>()

    /** Runs inside every search call, on the renderer — a hook to act mid-search. */
    var onSearch: (index: Int, query: String) -> Unit = { _, _ -> }
    val linkRequests = CopyOnWriteArrayList<Int>()
    var closeCount = 0
        private set
    var callsAfterClose = 0
        private set

    data class Render(val index: Int, val scale: Float, val offsetX: Float, val offsetY: Float)

    override val pageCount: Int get() = call { pageSizes.size }

    override fun pageSize(index: Int): PdfPageSize = call {
        failingPages[index]?.let { throw it }
        pageSizes[index]
    }

    override fun render(index: Int, bitmap: Bitmap, scale: Float, offsetX: Float, offsetY: Float) = call {
        failingPages[index]?.let { throw it }
        if (renderDelayMs > 0) Thread.sleep(renderDelayMs)
        renders += Render(index, scale, offsetX, offsetY)
    }

    override fun search(index: Int, query: String): List<List<PdfRectPt>> = call {
        searchedPages += index to query
        onSearch(index, query)
        searchFailure?.let { throw it }
        matches[index].orEmpty()
    }

    override fun links(index: Int): List<PdfLink> = call {
        linkRequests += index
        linksByPage[index].orEmpty()
    }

    override fun close() {
        call { closeCount++ }
    }

    private fun <T> call(block: () -> T): T {
        if (closeCount > 0) callsAfterClose++
        val now = active.incrementAndGet()
        maxConcurrentCalls.accumulateAndGet(now) { a, b -> maxOf(a, b) }
        try {
            return block()
        } finally {
            active.decrementAndGet()
        }
    }
}

/**
 * Opens [document], or throws [failure] — or [SecurityException] until [password] is supplied when
 * one is set. [passwordsTried] records every attempt so a test can see what reached the renderer.
 */
class FakePdfDocumentOpener(
    override val mode: PdfViewerMode = PdfViewerMode.FULL,
    var document: FakePdfDocumentSource = FakePdfDocumentSource(),
    var failure: Exception? = null,
    private val password: String? = null
) : PdfDocumentOpener {
    val passwordsTried = CopyOnWriteArrayList<String?>()
    val openedFiles = CopyOnWriteArrayList<File>()

    override fun open(file: File, password: String?): PdfDocumentSource {
        openedFiles += file
        passwordsTried += password
        failure?.let { throw it }
        if (this.password != null && (mode != PdfViewerMode.FULL || password != this.password)) {
            throw SecurityException("password required")
        }
        return document
    }
}
