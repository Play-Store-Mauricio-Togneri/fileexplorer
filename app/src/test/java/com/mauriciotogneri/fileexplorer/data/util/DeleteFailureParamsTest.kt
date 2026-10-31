package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The `operation_failed` params a failed delete sends. Built without Firebase, so the buckets, the
 * omissions and the fixed values can be pinned on the JVM.
 */
class DeleteFailureParamsTest {

    private fun diagnostics(
        cause: DeleteFailure = DeleteFailure.SYSTEM_FOLDER,
        errno: Int? = 39,
        systemFolders: Int = 1,
        unlistedFolders: Int = 0,
        failedListing: FailedListing? = FailedListing.UNLISTED,
        failedLocation: DeleteLocation? = DeleteLocation.ANDROID
    ) = DeleteDiagnostics(cause, errno, systemFolders, unlistedFolders, failedListing, failedLocation)

    @Test
    fun `a failed delete sends the shared params and the walk's findings`() {
        val params = AnalyticsTracker.deleteFailureParams(
            errorType = "partial",
            outcome = "partial",
            source = "folder",
            diagnostics = diagnostics()
        )

        assertEquals(
            mapOf(
                "operation" to "delete",
                "error_type" to "partial",
                "errno" to "39",
                "source" to "folder",
                "outcome" to "partial",
                "cause" to "system_folder",
                "system_folders" to "1",
                "unlisted_folders" to "0",
                "failed_listing" to "unlisted",
                "failed_location" to "android"
            ),
            params
        )
    }

    @Test
    fun `folder counts are bucketed at three`() {
        val buckets = listOf(0 to "0", 1 to "1", 2 to "2", 3 to "3+", 250 to "3+")

        for ((count, expected) in buckets) {
            val params = AnalyticsTracker.deleteFailureParams(
                "all_failed",
                "all_failed",
                "folder",
                diagnostics(systemFolders = count, unlistedFolders = count)
            )

            assertEquals("system_folders for $count", expected, params["system_folders"])
            assertEquals("unlisted_folders for $count", expected, params["unlisted_folders"])
        }
    }

    @Test
    fun `a delete where no node failed omits the errno, listing and location`() {
        // A protected root alone: left in place without a syscall, so nothing failed with an errno.
        val params = AnalyticsTracker.deleteFailureParams(
            "system_folder",
            "all_failed",
            "search",
            diagnostics(errno = null, failedListing = null, failedLocation = null)
        )

        assertFalse(params.containsKey("errno"))
        assertFalse(params.containsKey("failed_listing"))
        assertFalse(params.containsKey("failed_location"))
        assertEquals("system_folder", params["cause"])
    }

    @Test
    fun `a failure without an errno is not reported as errno zero`() {
        val params = AnalyticsTracker.deleteFailureParams(
            "unknown",
            "all_failed",
            "folder",
            diagnostics(cause = DeleteFailure.UNKNOWN, errno = ERRNO_UNKNOWN)
        )

        assertFalse(params.containsKey("errno"))
    }

    @Test
    fun `every listing and cause value comes from the fixed lists`() {
        assertEquals(
            listOf("file", "unlisted", "empty", "listed"),
            FailedListing.entries.map { it.analyticsLabel }
        )
        assertEquals(
            listOf(
                "permission_denied",
                "read_only",
                "not_empty",
                "busy",
                "storage_unavailable",
                "errno_other",
                "unknown",
                "system_folder",
                "hidden_contents"
            ),
            DeleteFailure.entries.map { it.analyticsLabel }
        )
    }
}
