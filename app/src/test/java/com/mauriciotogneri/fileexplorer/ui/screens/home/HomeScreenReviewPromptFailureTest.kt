package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.content.Context
import android.widget.Toast
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.util.ReviewPrompt
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * A tap on a recents entry or a favorite whose file is gone shows a toast and nothing else. That is
 * a failed file open, and no failure event reports it, so it must block the rating ask itself:
 * without the direct call, the rating dialog could follow a tap that just failed.
 *
 * The stored path sits in a directory that is never created, so nothing occupies it.
 */
class HomeScreenReviewPromptFailureTest {

    private val context = mockk<Context>(relaxed = true)

    private val missingPath = File(
        System.getProperty("java.io.tmpdir"),
        "home_screen_review_prompt_${System.nanoTime()}/gone.pdf"
    ).absolutePath

    @Before
    fun setUp() {
        mockkObject(ReviewPrompt)

        mockkStatic(Toast::class)
        every { Toast.makeText(any<Context>(), any<Int>(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a tap on a recent file that is gone blocks the rating ask`() = runTest {
        openRecentFile(
            context = context,
            recentFile = RecentFile(missingPath, "gone.pdf", "application/pdf", 1_700_000_000_000L),
            onUncompressRequired = {},
            onInstallPermissionRequired = {}
        )

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `a tap on a favorite that is gone blocks the rating ask`() = runTest {
        openFavorite(
            context = context,
            favorite = Favorite(missingPath, "gone.pdf", false, "application/pdf", 1_700_000_000_000L),
            onUncompressRequired = {},
            onInstallPermissionRequired = {}
        )

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }
}
