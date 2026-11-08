package com.mauriciotogneri.fileexplorer.util

import android.app.Activity
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests never call [ReviewPrompt.init], so every caller they reach — AnalyticsTracker's
 * failure events, the success hooks — meets an uninitialised facade, which has to stay inert.
 */
class ReviewPromptTest {

    @Test
    fun `nothing is pending before init`() {
        assertFalse(ReviewPrompt.pending.value)
    }

    @Test
    fun `the action hooks do nothing before init`() {
        ReviewPrompt.onActionSucceeded()
        ReviewPrompt.onActionFailed()
        ReviewPrompt.disarm()

        assertFalse(ReviewPrompt.pending.value)
    }

    @Test
    fun `ask does nothing before init`() = runTest {
        val activity = mockk<Activity>(relaxed = true)

        ReviewPrompt.ask(activity)

        verify { activity wasNot Called }
    }
}
