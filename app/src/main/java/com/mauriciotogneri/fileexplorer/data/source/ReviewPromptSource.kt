package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.ReviewPromptRecord

interface ReviewPromptSource {
    /** The stored record, or null when the store could not be read. */
    suspend fun getRecord(): ReviewPromptRecord?

    /**
     * Replaces the stored record with [transform] applied to it, as one atomic step, and returns
     * what was stored — or null when the write did not reach the store.
     */
    suspend fun updateRecord(transform: (ReviewPromptRecord) -> ReviewPromptRecord): ReviewPromptRecord?
}
