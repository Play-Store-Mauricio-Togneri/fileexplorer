package com.mauriciotogneri.fileexplorer.data.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A [MediaChangeSource] a test drives by hand, standing in for the ContentObserver the real one
 * registers against a media provider no JVM test has.
 */
class FakeMediaChangeSource : MediaChangeSource {

    // Buffered so a test can publish a burst without a collector having run yet, which is what
    // reaching the coalescing path requires.
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = BURST_CAPACITY)

    override fun changes(): Flow<Unit> = changes

    /** Publishes one change, as a provider would once another app had written a file. */
    suspend fun emitChange() = changes.emit(Unit)

    private companion object {
        const val BURST_CAPACITY = 64
    }
}
