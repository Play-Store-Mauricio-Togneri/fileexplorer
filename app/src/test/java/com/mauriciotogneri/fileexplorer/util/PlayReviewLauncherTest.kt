package com.mauriciotogneri.fileexplorer.util

import android.app.Activity
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gate's rule that a refusal from Play spends no ask rests on [PlayReviewLauncher.prepare]
 * answering null, rather than throwing, whenever Play does not hand over a review flow.
 *
 * Play's tasks are mocked already complete, which is the path review-ktx resumes on at once, so no
 * listener or main looper is involved.
 */
class PlayReviewLauncherTest {

    private val activity = mockk<Activity>(relaxed = true)
    private val reviewInfo = mockk<ReviewInfo>()
    private val manager = mockk<ReviewManager>()

    @Test
    fun `a request Play refuses prepares nothing`() = runTest {
        every { manager.requestReviewFlow() } returns failedTask()

        assertNull(launcher().prepare())
    }

    @Test
    fun `a request that throws prepares nothing`() = runTest {
        every { manager.requestReviewFlow() } throws IllegalStateException("Play Store missing")

        assertNull(launcher().prepare())
    }

    @Test
    fun `a granted request launches the flow Play granted`() = runTest {
        every { manager.requestReviewFlow() } returns succeededTask(reviewInfo)
        every { manager.launchReviewFlow(activity, reviewInfo) } returns succeededTask(null)

        val review = launcher().prepare()
        assertNotNull(review)
        review!!.launch(activity)

        verify(exactly = 1) { manager.launchReviewFlow(activity, reviewInfo) }
    }

    @Test
    fun `a launch Play fails does not throw`() = runTest {
        every { manager.requestReviewFlow() } returns succeededTask(reviewInfo)
        every { manager.launchReviewFlow(activity, reviewInfo) } returns failedTask()

        launcher().prepare()!!.launch(activity)
    }

    @Test
    fun `the manager is built on first use, once`() = runTest {
        every { manager.requestReviewFlow() } returns failedTask()
        var built = 0
        val launcher = PlayReviewLauncher(mockk<Context>(relaxed = true)) {
            built++
            manager
        }

        assertEquals(0, built)
        launcher.prepare()
        launcher.prepare()
        assertEquals(1, built)
    }

    // ==================== Helpers ====================

    private fun launcher() = PlayReviewLauncher(mockk<Context>(relaxed = true)) { manager }

    private fun <T> succeededTask(result: T): Task<T> = mockk {
        every { isComplete } returns true
        every { isSuccessful } returns true
        every { getResult() } returns result
    }

    private fun <T> failedTask(): Task<T> = mockk {
        every { isComplete } returns true
        every { isSuccessful } returns false
        every { exception } returns IllegalStateException("review refused")
    }
}
