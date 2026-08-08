package com.mauriciotogneri.fileexplorer.data.source

import kotlinx.coroutines.flow.Flow

interface MediaChangeSource {
    /**
     * Emits once for every change to shared storage that a media provider publishes, whoever made
     * it — a camera shot, a completed download, another file manager's delete, and this app's own
     * operations alike.
     *
     * Coalescing is the collector's job: a single copy can publish one notification per file.
     */
    fun changes(): Flow<Unit>
}
