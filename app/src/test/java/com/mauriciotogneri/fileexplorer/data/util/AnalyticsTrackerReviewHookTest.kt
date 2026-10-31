package com.mauriciotogneri.fileexplorer.data.util

import android.util.Log
import com.mauriciotogneri.fileexplorer.util.ReviewPrompt
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A failure event is also what keeps the app from asking for a rating right after letting the user
 * down. Dropping the hook from one of these events weakens that rule with nothing else noticing, so
 * each one is pinned here.
 *
 * The events run for real. Firebase is never initialised in a unit test, so they send nothing;
 * [Log] is stubbed only because a debug build echoes every event to it, and the JVM's android.jar
 * throws on every framework call.
 */
class AnalyticsTrackerReviewHookTest {

    @Before
    fun setUp() {
        mockkObject(ReviewPrompt)
        every { ReviewPrompt.onActionFailed() } just Runs
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a file that fails to open blocks the review prompt`() {
        AnalyticsTracker.trackFileOpenFailed("pdf", "application/pdf", "home", "no_handler")

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `a text file that fails to read blocks the review prompt`() {
        AnalyticsTracker.trackTextViewerReadError("folder")

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `an image that fails to load blocks the review prompt`() {
        AnalyticsTracker.trackImageViewerLoadError("folder")

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `a PDF that fails to load blocks the review prompt`() {
        AnalyticsTracker.trackPdfViewerLoadError("folder", "unreadable")

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `a media file that fails to load blocks the review prompt`() {
        AnalyticsTracker.trackMediaViewerLoadError("folder", "unplayable")

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `a failed operation blocks the review prompt`() {
        AnalyticsTracker.trackOperationFailed("copy", "storage_io_error")

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `a failed delete blocks the review prompt`() {
        AnalyticsTracker.trackDeleteFailed(
            errorType = "system_folder",
            outcome = "partial",
            source = "folder",
            diagnostics = DeleteDiagnostics(
                cause = DeleteFailure.SYSTEM_FOLDER,
                errno = null,
                systemFolders = 1,
                unlistedFolders = 0,
                failedListing = null,
                failedLocation = null
            )
        )

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `a failed APK install blocks the review prompt`() {
        AnalyticsTracker.trackApkInstallFailed("folder")

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `feedback that fails to send blocks the review prompt`() {
        AnalyticsTracker.trackFeedbackSubmitError()

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `success events leave the review prompt alone`() {
        AnalyticsTracker.trackRenameCompleted("txt", "text/plain")
        AnalyticsTracker.trackCompressCompleted(3)
        AnalyticsTracker.trackReviewPromptRequested(1)

        verify(exactly = 0) { ReviewPrompt.onActionFailed() }
    }

    /** A swipe opened and left unused is not a failure: most openings end without a tap. */
    @Test
    fun `swipe events leave the review prompt alone`() {
        AnalyticsTracker.trackFolderSwipeOpened("right", "delete")
        AnalyticsTracker.trackFolderSwipeActionTapped("delete", "right")
        AnalyticsTracker.trackFolderSwipeClosed("right", "delete", "scroll", "under_1s")

        verify(exactly = 0) { ReviewPrompt.onActionFailed() }
    }
}
