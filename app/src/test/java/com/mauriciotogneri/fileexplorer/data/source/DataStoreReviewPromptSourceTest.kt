package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.ReviewPromptRecord
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
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

class DataStoreReviewPromptSourceTest {

    private val stored = ReviewPromptRecord(
        askCount = 1,
        firstAskAt = 1_700_000_000_000L,
        activeDays = 3,
        lastActiveDay = 20_541L,
        actionCount = 9
    )

    @Before
    fun setUp() {
        mockkObject(ErrorReporter)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(ErrorReporter)
    }

    @Test
    fun `an empty store reads as a user never asked and with nothing counted`() = runTest {
        val record = DataStoreReviewPromptSource(FakeInMemoryDataStore()).getRecord()

        assertEquals(0, record?.askCount)
        assertEquals(ReviewPromptRecord.NEVER, record?.firstAskAt)
        assertEquals(0, record?.activeDays)
        assertEquals(ReviewPromptRecord.NEVER, record?.lastActiveDay)
        assertEquals(0, record?.actionCount)
    }

    @Test
    fun `every field of a written record is read back`() = runTest {
        val dataStore = FakeInMemoryDataStore()
        DataStoreReviewPromptSource(dataStore).updateRecord { stored }

        assertEquals(stored, DataStoreReviewPromptSource(dataStore).getRecord())
    }

    @Test
    fun `updateRecord returns the record it stored`() = runTest {
        val source = DataStoreReviewPromptSource(FakeInMemoryDataStore())

        assertEquals(stored, source.updateRecord { stored })
    }

    @Test
    fun `updateRecord transforms the stored record, not a default one`() = runTest {
        val source = DataStoreReviewPromptSource(FakeInMemoryDataStore())
        source.updateRecord { stored }

        val updated = source.updateRecord { it.copy(actionCount = it.actionCount + 1) }

        assertEquals(stored.copy(actionCount = 10), updated)
        assertEquals(updated, source.getRecord())
    }

    // Null, not a default record: a fresh record would say "never asked", and the repository
    // treats an unreadable store as "never ask" instead.
    @Test
    fun `getRecord answers null when the store fails`() = runTest {
        val source = DataStoreReviewPromptSource(FakeThrowingDataStore())

        assertNull(source.getRecord())
    }

    @Test
    fun `updateRecord answers null when the store fails`() = runTest {
        val source = DataStoreReviewPromptSource(FakeThrowingDataStore())

        assertNull(source.updateRecord { stored })
    }
}
