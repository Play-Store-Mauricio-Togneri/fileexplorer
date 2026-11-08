package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.source.DataStoreReviewPromptSource
import com.mauriciotogneri.fileexplorer.data.source.FakeInMemoryDataStore
import com.mauriciotogneri.fileexplorer.data.source.FakeThrowingDataStore
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.ReviewEligibility
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ReviewPromptRepositoryTest {

    @Before
    fun setUp() {
        mockkObject(ErrorReporter)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(ErrorReporter)
    }

    private fun repository() = ReviewPromptRepository(DataStoreReviewPromptSource(FakeInMemoryDataStore()))

    private suspend fun ReviewPromptRepository.spendBothAsks() {
        recordAsked(FIRST_ASK_AT)
        recordAsked(FIRST_ASK_AT + RETRY_GAP)
    }

    @Test
    fun `the first active day is counted`() = runTest {
        val record = repository().recordActiveDay(DAY)

        assertEquals(1, record?.activeDays)
        assertEquals(DAY, record?.lastActiveDay)
    }

    @Test
    fun `the same day seen twice counts once`() = runTest {
        val repository = repository()
        repository.recordActiveDay(DAY)

        val record = repository.recordActiveDay(DAY)

        assertEquals(1, record?.activeDays)
    }

    @Test
    fun `the next day counts again`() = runTest {
        val repository = repository()
        repository.recordActiveDay(DAY)

        val record = repository.recordActiveDay(DAY + 1)

        assertEquals(2, record?.activeDays)
        assertEquals(DAY + 1, record?.lastActiveDay)
    }

    @Test
    fun `a day before the last one counted adds nothing`() = runTest {
        val repository = repository()
        repository.recordActiveDay(DAY)

        val record = repository.recordActiveDay(DAY - 1)

        assertEquals(1, record?.activeDays)
        assertEquals(DAY, record?.lastActiveDay)
    }

    @Test
    fun `active days stop at the threshold while the last day counted keeps moving`() = runTest {
        val repository = repository()

        (DAY until DAY + 7).forEach { repository.recordActiveDay(it) }

        val record = repository.getRecord()
        assertEquals(ReviewEligibility.MIN_ACTIVE_DAYS, record?.activeDays)
        assertEquals(DAY + 6, record?.lastActiveDay)
    }

    // Why the last day keeps moving once the counter is full: the day of an ask has already been
    // seen, so it must not start the retry's count.
    @Test
    fun `a day seen while the counter was full is not counted again after an ask resets it`() = runTest {
        val repository = repository()
        (DAY until DAY + 7).forEach { repository.recordActiveDay(it) }
        repository.recordAsked(FIRST_ASK_AT)

        val record = repository.recordActiveDay(DAY + 6)

        assertEquals(0, record?.activeDays)
    }

    @Test
    fun `actions stop at the threshold`() = runTest {
        val repository = repository()

        repeat(ReviewEligibility.MIN_ACTIONS + 5) { repository.recordAction() }

        assertEquals(ReviewEligibility.MIN_ACTIONS, repository.getRecord()?.actionCount)
    }

    @Test
    fun `recordAction returns the record as stored`() = runTest {
        val repository = repository()
        repository.recordAction()

        val returned = repository.recordAction()

        assertEquals(2, returned?.actionCount)
        assertEquals(repository.getRecord(), returned)
    }

    @Test
    fun `the first ask is spent and stamped with its time`() = runTest {
        val record = repository().recordAsked(FIRST_ASK_AT)

        assertEquals(1, record?.askCount)
        assertEquals(FIRST_ASK_AT, record?.firstAskAt)
    }

    // The retry gap is measured from the first ask, so the second must not move it.
    @Test
    fun `a second ask keeps the time of the first`() = runTest {
        val repository = repository()
        repository.recordAsked(FIRST_ASK_AT)

        val record = repository.recordAsked(FIRST_ASK_AT + RETRY_GAP)

        assertEquals(2, record?.askCount)
        assertEquals(FIRST_ASK_AT, record?.firstAskAt)
    }

    @Test
    fun `asking resets the engagement counters but remembers the last day counted`() = runTest {
        val repository = repository()
        (DAY until DAY + ReviewEligibility.MIN_ACTIVE_DAYS).forEach { repository.recordActiveDay(it) }
        repeat(ReviewEligibility.MIN_ACTIONS) { repository.recordAction() }

        val record = repository.recordAsked(FIRST_ASK_AT)

        assertEquals(0, record?.activeDays)
        assertEquals(0, record?.actionCount)
        assertEquals(DAY + ReviewEligibility.MIN_ACTIVE_DAYS - 1, record?.lastActiveDay)
        assertEquals(repository.getRecord(), record)
    }

    @Test
    fun `once both asks are spent a new day changes nothing`() = runTest {
        val repository = repository()
        repository.recordActiveDay(DAY)
        repository.spendBothAsks()
        val before = repository.getRecord()

        val returned = repository.recordActiveDay(DAY + 1)

        assertEquals(before, returned)
        assertEquals(before, repository.getRecord())
    }

    @Test
    fun `once both asks are spent an action changes nothing`() = runTest {
        val repository = repository()
        repository.spendBothAsks()
        val before = repository.getRecord()

        val returned = repository.recordAction()

        assertEquals(before, returned)
        assertEquals(before, repository.getRecord())
    }

    @Test
    fun `every method answers null when the store fails`() = runTest {
        val repository = ReviewPromptRepository(DataStoreReviewPromptSource(FakeThrowingDataStore()))

        assertNull(repository.getRecord())
        assertNull(repository.recordActiveDay(DAY))
        assertNull(repository.recordAction())
        assertNull(repository.recordAsked(FIRST_ASK_AT))
    }

    private companion object {
        const val DAY = 20_541L
        const val FIRST_ASK_AT = 1_700_000_000_000L
        const val RETRY_GAP = 120L * 24 * 60 * 60 * 1000
    }
}
