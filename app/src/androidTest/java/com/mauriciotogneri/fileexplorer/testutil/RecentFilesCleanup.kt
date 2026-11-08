package com.mauriciotogneri.fileexplorer.testutil

import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private const val RECENTS_WRITE_TIMEOUT_MS = 2_000L
private const val RECENTS_POLL_INTERVAL_MS = 25L

/**
 * Removes the entries that opening each of [paths] wrote to the app's own recents store.
 *
 * A launched open counts as an open, so `IntentUtil` writes the file to recents, and the suite
 * shares one data directory across the orchestrator's per-test processes: on-disk state a test
 * persists is its own to undo.
 *
 * `IntentUtil.trackRecentFile` writes from a fire-and-forget coroutine, so the entry need not have
 * landed by the time the test body returns: removing straight away would run against a store not
 * yet written and leave the entry behind. Waiting for each entry first is what makes the cleanup
 * deterministic; the timeout bounds the case where the write never comes, which leaves an entry a
 * later read drops anyway, the fixture being gone. The source is read rather than the repository,
 * whose reads filter out entries whose file no longer exists.
 */
fun removeRecentFilesOnceWritten(paths: Collection<String>) {
    if (paths.isEmpty()) return
    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val source = DataStoreRecentFilesSource(context.recentFilesDataStore)
    val repository = RecentFilesRepository(source)

    runBlocking {
        paths.forEach { path ->
            withTimeoutOrNull(RECENTS_WRITE_TIMEOUT_MS) {
                while (source.getRecentFiles().none { it.path == path }) {
                    delay(RECENTS_POLL_INTERVAL_MS)
                }
            }
            repository.removeRecentFile(path)
        }
    }
}
