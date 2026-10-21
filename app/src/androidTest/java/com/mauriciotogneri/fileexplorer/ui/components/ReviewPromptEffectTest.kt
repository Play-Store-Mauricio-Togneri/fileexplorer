package com.mauriciotogneri.fileexplorer.ui.components

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [ReviewPromptEffect] in a real Activity: it asks only once a success has armed it and the screen
 * has stayed calm and resumed for [REVIEW_PROMPT_SETTLE_MILLIS], and losing any of the three before
 * then cancels the ask rather than pausing it.
 *
 * `pending` and `ask` are the effect's test seams, so nothing here reads `ReviewPrompt`'s real state
 * or reaches Play, which the emulator does not have. Whether an ask is due, and what asking does,
 * belong to `ReviewPromptGate` and its unit tests.
 *
 * Time is the Compose test clock's, with auto-advance off. The effect's `delay` runs on the
 * composition's test dispatcher, so the settle delay passes only when [advanceBy] moves the clock,
 * and each test pins the ask to the exact millisecond it is due.
 *
 * The lifecycle case provides a [TestLifecycleOwner] as `LocalLifecycleOwner` instead of pausing the
 * Activity. In production the effect follows its navigation destination's lifecycle, which is not
 * the Activity's, and this keeps the state changes synchronous on the main thread.
 */
@RunWith(AndroidJUnit4::class)
class ReviewPromptEffectTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private val pending = MutableStateFlow(true)

    private var isCalm by mutableStateOf(true)

    /** Every Activity the effect asked with. Written on the main thread, read from the test thread. */
    private val asked = CopyOnWriteArrayList<Activity>()

    @Before
    fun setUp() {
        composeTestRule.mainClock.autoAdvance = false
    }

    @Test
    fun pendingCalmAndResumed_asksOnceWithTheActivity_afterTheSettleDelay() {
        render()

        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS - 1)
        assertEquals("asked before the settle delay ended", 0, asked.size)

        advanceBy(1)
        assertEquals("did not ask once the settle delay ended", 1, asked.size)
        assertSame(activity, asked.single())

        // Still pending and calm: the ask already made is not repeated.
        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS * 4)
        assertEquals("asked again without a new trigger", 1, asked.size)
    }

    @Test
    fun notCalm_neverAsks_andCalmingDownStartsTheFullDelay() {
        isCalm = false
        render()

        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS * 4)
        assertEquals("asked while the screen was busy", 0, asked.size)

        updateCalm(true)
        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS - 1)
        assertEquals("asked before the settle delay ended", 0, asked.size)

        advanceBy(1)
        assertEquals("did not ask once the settle delay ended", 1, asked.size)
    }

    @Test
    fun losingCalmBeforeTheDelayEnds_cancelsTheAsk_andRegainingItRestartsTheDelay() {
        render()
        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS / 2)

        updateCalm(false)
        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS * 4)
        assertEquals("asked although the screen got busy before the delay ended", 0, asked.size)

        // A delay that resumed instead of restarting would fire about half a delay from here.
        updateCalm(true)
        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS - 1)
        assertEquals("asked before a fresh settle delay ended", 0, asked.size)

        advanceBy(1)
        assertEquals("did not ask once the fresh settle delay ended", 1, asked.size)
    }

    @Test
    fun notPending_neverAsks_andArmingStartsTheFullDelay() {
        pending.value = false
        render()

        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS * 4)
        assertEquals("asked with nothing pending", 0, asked.size)

        // A success arming it while the screen is in front, as a finished copy does.
        setPending(true)
        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS - 1)
        assertEquals("asked before the settle delay ended", 0, asked.size)

        advanceBy(1)
        assertEquals("did not ask once the settle delay ended", 1, asked.size)
    }

    @Test
    fun leavingResumedBeforeTheDelayEnds_cancelsTheAsk_andResumingRestartsTheDelay() {
        val owner = TestLifecycleOwner()
        composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        render(lifecycleOwner = owner)
        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS / 2)

        // Started but not resumed: still visible, with something else in front of it.
        composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS * 4)
        assertEquals("asked while the screen was not resumed", 0, asked.size)

        composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        advanceBy(REVIEW_PROMPT_SETTLE_MILLIS - 1)
        assertEquals("asked before a fresh settle delay ended", 0, asked.size)

        advanceBy(1)
        assertEquals("did not ask once the fresh settle delay ended", 1, asked.size)
    }

    // ==================== Helpers ====================

    private fun render(lifecycleOwner: LifecycleOwner = activity) {
        composeTestRule.setContent {
            // The effect reads its lifecycle from the composition, so providing it is the only seam.
            // It defaults to the host Activity, which is what the composition supplies anyway.
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                ReviewPromptEffect(isCalm = isCalm, pending = pending, ask = { asked += it })
            }
        }
    }

    /**
     * Moves the clock the settle delay runs on forward by exactly [millis]. Without
     * `ignoreFrameDuration` the clock rounds up to whole 16 ms frames, so asking for 1_499 ms would
     * move it 1_504 ms, past the delay this is meant to stop just short of.
     */
    private fun advanceBy(millis: Long) {
        composeTestRule.mainClock.advanceTimeBy(millis, ignoreFrameDuration = true)
    }

    /**
     * Changes [isCalm] the way a screen does, through recomposition. The effect only sees the new
     * value once the next frame recomposes it, so this runs that one frame, and a settle delay the
     * change starts is counted from the end of this call.
     */
    private fun updateCalm(calm: Boolean) {
        composeTestRule.runOnUiThread { isCalm = calm }
        // Applies the write, which schedules the recomposition for one frame from now.
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    /**
     * Writes [pending] on the main thread. The composition's test dispatcher resumes the effect's
     * collector on whichever thread writes the flow, so a write from the test thread would run the
     * effect there, racing the main thread that the clock runs it on.
     */
    private fun setPending(value: Boolean) {
        composeTestRule.runOnUiThread { pending.value = value }
    }

    /** A lifecycle this test owns and drives, standing in for a navigation destination's. */
    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle get() = registry
    }
}
