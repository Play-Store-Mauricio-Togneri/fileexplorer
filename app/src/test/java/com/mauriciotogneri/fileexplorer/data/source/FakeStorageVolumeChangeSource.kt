package com.mauriciotogneri.fileexplorer.data.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A [StorageVolumeChangeSource] a test drives by hand, standing in for the BroadcastReceiver the
 * real one registers against a platform no JVM test has.
 */
class FakeStorageVolumeChangeSource : StorageVolumeChangeSource {

    // Buffered so a test can publish a burst without a collector having run yet, which is what
    // reaching the coalescing path requires.
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = BURST_CAPACITY)

    override fun changes(): Flow<Unit> = changes

    /** Publishes one change, as the platform would once a volume had been mounted or removed. */
    suspend fun emitChange() = changes.emit(Unit)

    private companion object {
        const val BURST_CAPACITY = 64
    }
}
