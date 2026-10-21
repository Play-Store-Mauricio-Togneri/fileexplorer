package com.mauriciotogneri.fileexplorer.util

import android.app.Activity
import com.mauriciotogneri.fileexplorer.data.model.ReviewPromptRecord
import com.mauriciotogneri.fileexplorer.data.repository.ReviewPromptRepository
import com.mauriciotogneri.fileexplorer.data.source.ReviewPromptSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ReviewEligibility
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * The gate is given the test's own scope rather than `backgroundScope`, which would better match
 * the app-wide scope it runs on in production: `advanceUntilIdle` stops once only background work
 * is left, so a success or a resume launched there would never be recorded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewPromptGateTest {

    private val activity = mockk<Activity>(relaxed = true)
    private var nowMillis = INSTALLED_AT + 200 * DAY_MILLIS
    private var timeZone: TimeZone = TimeZone.getTimeZone("UTC")

    /** One success short of eligible, so the next success is the one that makes the user eligible. */
    private val oneActionShort = ReviewPromptRecord(
        activeDays = ReviewEligibility.MIN_ACTIVE_DAYS,
        actionCount = ReviewEligibility.MIN_ACTIONS - 1
    )
    private val eligible = oneActionShort.copy(actionCount = ReviewEligibility.MIN_ACTIONS)

    private fun retryDue() = ReviewPromptRecord(
        askCount = 1,
        firstAskAt = nowMillis - 120 * DAY_MILLIS,
        activeDays = ReviewEligibility.MIN_ACTIVE_DAYS,
        actionCount = ReviewEligibility.MIN_ACTIONS
    )

    @Before
    fun setUp() {
        mockkObject(AnalyticsTracker)
        every { AnalyticsTracker.trackReviewPromptRequested(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun TestScope.gate(
        source: FakeReviewPromptSource,
        launcher: FakeReviewLauncher = FakeReviewLauncher(),
        installedAt: Long? = INSTALLED_AT
    ) = ReviewPromptGate(
        repository = ReviewPromptRepository(source),
        launcher = launcher,
        installedAt = { installedAt },
        scope = this,
        now = { nowMillis },
        timeZone = { timeZone }
    )

    // ---------- Arming ---------- \\

    @Test
    fun `a success that leaves the user short of the thresholds does not arm the ask`() = runTest {
        val source = FakeReviewPromptSource()
        val gate = gate(source)

        gate.onActionSucceeded()
        advanceUntilIdle()

        assertFalse(gate.pending.value)
        assertEquals(1, source.record.actionCount)
    }

    @Test
    fun `the success that makes the user eligible arms the ask`() = runTest {
        val gate = gate(FakeReviewPromptSource(oneActionShort))

        gate.onActionSucceeded()
        advanceUntilIdle()

        assertTrue(gate.pending.value)
    }

    @Test
    fun `a success does not arm the ask before the install is old enough`() = runTest {
        nowMillis = INSTALLED_AT + 13 * DAY_MILLIS
        val gate = gate(FakeReviewPromptSource(oneActionShort))

        gate.onActionSucceeded()
        advanceUntilIdle()

        assertFalse(gate.pending.value)
    }

    @Test
    fun `a success the store cannot record does not arm the ask`() = runTest {
        val gate = gate(FakeReviewPromptSource(oneActionShort).apply { writesFail = true })

        gate.onActionSucceeded()
        advanceUntilIdle()

        assertFalse(gate.pending.value)
    }

    @Test
    fun `without an install time the ask is never armed nor launched`() = runTest {
        val launcher = FakeReviewLauncher()
        val gate = gate(FakeReviewPromptSource(oneActionShort), launcher, installedAt = null)

        gate.onActionSucceeded()
        advanceUntilIdle()
        gate.ask(activity)

        assertFalse(gate.pending.value)
        assertEquals(0, launcher.prepareCount)
        assertTrue(launcher.launchedOn.isEmpty())
    }

    @Test
    fun `disarm drops an armed ask without spending it`() = runTest {
        val source = FakeReviewPromptSource(oneActionShort)
        val gate = gate(source)
        gate.onActionSucceeded()
        advanceUntilIdle()

        gate.disarm()

        assertFalse(gate.pending.value)
        assertEquals(0, source.record.askCount)

        // Unlike a failure, it blocks nothing: the next success arms the ask again.
        gate.onActionSucceeded()
        advanceUntilIdle()

        assertTrue(gate.pending.value)
    }

    // ---------- Failures ---------- \\

    @Test
    fun `a failure clears an armed ask`() = runTest {
        val gate = gate(FakeReviewPromptSource(oneActionShort))
        gate.onActionSucceeded()
        advanceUntilIdle()

        gate.onActionFailed()

        assertFalse(gate.pending.value)
    }

    // The failure blocks the ask, not the engagement it measures: the next process still needs
    // this success counted.
    @Test
    fun `after a failure a success is still counted but never arms the ask`() = runTest {
        val source = FakeReviewPromptSource(oneActionShort)
        val gate = gate(source)
        gate.onActionFailed()

        gate.onActionSucceeded()
        advanceUntilIdle()

        assertFalse(gate.pending.value)
        assertEquals(ReviewEligibility.MIN_ACTIONS, source.record.actionCount)
    }

    @Test
    fun `a failure reported while an earlier success is still being recorded keeps the ask unarmed`() = runTest {
        val gate = gate(FakeReviewPromptSource(oneActionShort))

        gate.onActionSucceeded()
        gate.onActionFailed()
        advanceUntilIdle()

        assertFalse(gate.pending.value)
    }

    @Test
    fun `after a failure ask launches nothing and spends nothing`() = runTest {
        val source = FakeReviewPromptSource(eligible)
        val launcher = FakeReviewLauncher()
        val gate = gate(source, launcher)

        gate.onActionFailed()
        gate.ask(activity)

        assertEquals(0, launcher.prepareCount)
        assertTrue(launcher.launchedOn.isEmpty())
        assertEquals(0, source.record.askCount)
        verify(exactly = 0) { AnalyticsTracker.trackReviewPromptRequested(any()) }
    }

    // ---------- Asking ---------- \\

    @Test
    fun `ask launches the review, logs the first attempt and spends it`() = runTest {
        val source = FakeReviewPromptSource(oneActionShort.copy(lastActiveDay = 20_541L))
        val launcher = FakeReviewLauncher()
        val gate = gate(source, launcher)
        gate.onActionSucceeded()
        advanceUntilIdle()
        assertTrue(gate.pending.value)

        gate.ask(activity)

        assertEquals(listOf(activity), launcher.launchedOn)
        verify(exactly = 1) { AnalyticsTracker.trackReviewPromptRequested(1) }
        assertEquals(
            ReviewPromptRecord(
                askCount = 1,
                firstAskAt = nowMillis,
                activeDays = 0,
                lastActiveDay = 20_541L,
                actionCount = 0
            ),
            source.record
        )
        assertFalse(gate.pending.value)
    }

    @Test
    fun `a retry that is due is launched as the second attempt`() = runTest {
        val record = retryDue()
        val source = FakeReviewPromptSource(record)
        val launcher = FakeReviewLauncher()
        val gate = gate(source, launcher)

        gate.ask(activity)

        assertEquals(listOf(activity), launcher.launchedOn)
        verify(exactly = 1) { AnalyticsTracker.trackReviewPromptRequested(2) }
        assertEquals(2, source.record.askCount)
        assertEquals(record.firstAskAt, source.record.firstAskAt)
    }

    @Test
    fun `a second ask in the same process launches nothing, even with a retry due`() = runTest {
        val source = FakeReviewPromptSource(eligible)
        val launcher = FakeReviewLauncher()
        val gate = gate(source, launcher)
        gate.ask(activity)

        // Everything the retry needs, so only the one-ask-per-process rule stands in the way.
        source.record = retryDue()
        gate.ask(activity)

        assertEquals(1, launcher.prepareCount)
        assertEquals(1, launcher.launchedOn.size)
        verify(exactly = 1) { AnalyticsTracker.trackReviewPromptRequested(any()) }
    }

    @Test
    fun `two asks racing launch the review once`() = runTest {
        val playAnswers = CompletableDeferred<Unit>()
        val launcher = FakeReviewLauncher(answerAfter = playAnswers)
        val gate = gate(FakeReviewPromptSource(eligible), launcher)

        launch { gate.ask(activity) }
        launch { gate.ask(activity) }
        runCurrent()
        // The first ask is waiting on Play and the second has already run.
        playAnswers.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, launcher.prepareCount)
        assertEquals(1, launcher.launchedOn.size)
    }

    @Test
    fun `an ask made while the screen watches the armed flag runs to the launch`() = runTest {
        // The screen calls ask from inside collectLatest on pending, so an ask that changed
        // pending before settling would cancel itself — and spend the process's only ask doing so.
        val answer = CompletableDeferred<Unit>()
        val launcher = FakeReviewLauncher(answerAfter = answer)
        val source = FakeReviewPromptSource(oneActionShort)
        val gate = gate(source, launcher)
        gate.onActionSucceeded()
        advanceUntilIdle()

        val screen = launch { gate.pending.collectLatest { armed -> if (armed) gate.ask(activity) } }
        runCurrent()
        answer.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(activity), launcher.launchedOn)
        assertEquals(1, source.record.askCount)
        assertFalse(gate.pending.value)
        screen.cancel()
    }

    @Test
    fun `an ask cancelled before it settles stays armed and can be made again`() = runTest {
        val answer = CompletableDeferred<Unit>()
        val launcher = FakeReviewLauncher(answerAfter = answer)
        val source = FakeReviewPromptSource(oneActionShort)
        val gate = gate(source, launcher)
        gate.onActionSucceeded()
        advanceUntilIdle()

        // The screen paused while Play was still answering.
        val first = launch { gate.ask(activity) }
        runCurrent()
        first.cancel()
        advanceUntilIdle()

        assertTrue(gate.pending.value)
        assertEquals(0, source.record.askCount)

        answer.complete(Unit)
        gate.ask(activity)

        assertEquals(listOf(activity), launcher.launchedOn)
        assertEquals(1, source.record.askCount)
    }

    @Test
    fun `a review Play refuses spends no ask, and the process does not try again`() = runTest {
        val source = FakeReviewPromptSource(eligible)
        val launcher = FakeReviewLauncher(available = false)
        val gate = gate(source, launcher)

        gate.ask(activity)
        launcher.available = true
        gate.ask(activity)

        assertEquals(1, launcher.prepareCount)
        assertTrue(launcher.launchedOn.isEmpty())
        assertEquals(0, source.record.askCount)
        verify(exactly = 0) { AnalyticsTracker.trackReviewPromptRequested(any()) }
    }

    // An ask the store forgot would be repeated on the next eligible success.
    @Test
    fun `an ask the store cannot save launches nothing`() = runTest {
        val source = FakeReviewPromptSource(eligible).apply { askWritesFail = true }
        val launcher = FakeReviewLauncher()
        val gate = gate(source, launcher)

        gate.ask(activity)

        assertTrue(launcher.launchedOn.isEmpty())
        assertEquals(0, source.record.askCount)
        verify(exactly = 0) { AnalyticsTracker.trackReviewPromptRequested(any()) }
    }

    @Test
    fun `a record the store cannot read launches nothing`() = runTest {
        val launcher = FakeReviewLauncher()
        val gate = gate(FakeReviewPromptSource(eligible).apply { readsFail = true }, launcher)

        gate.ask(activity)

        assertEquals(0, launcher.prepareCount)
        assertTrue(launcher.launchedOn.isEmpty())
    }

    // Short of the thresholds, so the ask is claimed but spends nothing and leaves the record one
    // success from eligible: only the claim can keep that success from arming the ask.
    @Test
    fun `after this process has asked a success is still counted but never arms the ask`() = runTest {
        val source = FakeReviewPromptSource(oneActionShort)
        val launcher = FakeReviewLauncher()
        val gate = gate(source, launcher)
        gate.ask(activity)

        gate.onActionSucceeded()
        advanceUntilIdle()

        assertFalse(gate.pending.value)
        assertEquals(ReviewEligibility.MIN_ACTIONS, source.record.actionCount)
        assertTrue(launcher.launchedOn.isEmpty())
    }

    // ---------- Active days ---------- \\

    @Test
    fun `screen resumes count each day once, and the next day again`() = runTest {
        val source = FakeReviewPromptSource()
        val gate = gate(source)

        gate.onScreenResumed()
        gate.onScreenResumed()
        advanceUntilIdle()

        assertEquals(1, source.record.activeDays)

        nowMillis += DAY_MILLIS
        gate.onScreenResumed()
        advanceUntilIdle()

        assertEquals(2, source.record.activeDays)
        assertEquals(nowMillis / DAY_MILLIS, source.record.lastActiveDay)
    }

    @Test
    fun `days are counted in the device's time zone, not in UTC`() = runTest {
        timeZone = TimeZone.getTimeZone("GMT+05:00")
        val utcDay = nowMillis / DAY_MILLIS
        val source = FakeReviewPromptSource()
        val gate = gate(source)

        // 12:00 and 20:00 UTC are one UTC day, but 17:00 and 01:00 the next morning locally.
        nowMillis = utcDay * DAY_MILLIS + 12 * HOUR_MILLIS
        gate.onScreenResumed()
        advanceUntilIdle()
        nowMillis = utcDay * DAY_MILLIS + 20 * HOUR_MILLIS
        gate.onScreenResumed()
        advanceUntilIdle()

        assertEquals(2, source.record.activeDays)
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60 * 1000
        const val DAY_MILLIS = 24 * HOUR_MILLIS
        const val INSTALLED_AT = 1_700_000_000_000L
    }
}

/** An in-memory store whose reads, writes, or only the write that spends an ask can be made to fail. */
private class FakeReviewPromptSource(
    var record: ReviewPromptRecord = ReviewPromptRecord()
) : ReviewPromptSource {

    var readsFail = false
    var writesFail = false
    var askWritesFail = false

    override suspend fun getRecord(): ReviewPromptRecord? = if (readsFail) null else record

    override suspend fun updateRecord(
        transform: (ReviewPromptRecord) -> ReviewPromptRecord
    ): ReviewPromptRecord? {
        val updated = transform(record)
        val spendsAnAsk = updated.askCount > record.askCount
        if (writesFail || (askWritesFail && spendsAnAsk)) return null

        record = updated
        return updated
    }
}

/**
 * Stands in for Play. [available] false is Play refusing; [answerAfter] holds `prepare` until it
 * completes, the way the real request waits on the network.
 */
private class FakeReviewLauncher(
    var available: Boolean = true,
    private val answerAfter: CompletableDeferred<Unit>? = null
) : ReviewLauncher {

    var prepareCount = 0
        private set
    val launchedOn = mutableListOf<Activity>()

    override suspend fun prepare(): PreparedReview? {
        prepareCount++
        answerAfter?.await()

        return if (available) PreparedReview { activity -> launchedOn.add(activity) } else null
    }
}
