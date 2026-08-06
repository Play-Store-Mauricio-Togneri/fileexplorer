package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataStoreLocationsCacheSourceTest {

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
    fun `getCachedSize falls back to an invalid empty result when the store fails`() = runTest {
        val source = DataStoreLocationsCacheSource(FakeThrowingDataStore())
        val result = source.getCachedSize(LocationType.DOWNLOADS)
        assertNull(result.size)
        assertFalse(result.isValid)
    }

    @Test
    fun `updateCache does not throw when the store fails`() = runTest {
        val source = DataStoreLocationsCacheSource(FakeThrowingDataStore())
        source.updateCache(LocationType.DOWNLOADS, 1234L)
    }

    @Test
    fun `clearCache does not throw when the store fails`() = runTest {
        val source = DataStoreLocationsCacheSource(FakeThrowingDataStore())
        source.clearCache()
    }

    // The TTL is the only thing that invalidates a location size — nothing clears the cache up
    // front any more — so the hit/expiry contract below is the sole guard against the home screen
    // either re-walking every location on each resume or never refreshing a size at all.

    @Test
    fun `getCachedSize returns a size written within the cache duration`() = runTest {
        val source = DataStoreLocationsCacheSource(FakeInMemoryDataStore())
        source.updateCache(LocationType.DOWNLOADS, 4096L)

        val result = source.getCachedSize(LocationType.DOWNLOADS)

        assertEquals(4096L, result.size)
        assertTrue(result.isValid)
    }

    @Test
    fun `getCachedSize reports an entry older than the cache duration as invalid`() = runTest {
        val source = DataStoreLocationsCacheSource(storeStampedAt(LocationType.DOWNLOADS, 4096L, minutesAgo(6)))

        val result = source.getCachedSize(LocationType.DOWNLOADS)

        assertEquals(4096L, result.size)
        assertFalse(result.isValid)
    }

    @Test
    fun `getCachedSize reports a future timestamp as invalid`() = runTest {
        // The device clock moved backwards after the write (NTP correction, manual change, a bad
        // RTC across a reboot). A plain `now - stamped < duration` test reads that as fresh and
        // pins the size until wall-clock catches up, which can be hours.
        val source = DataStoreLocationsCacheSource(storeStampedAt(LocationType.DOWNLOADS, 4096L, minutesFromNow(60)))

        val result = source.getCachedSize(LocationType.DOWNLOADS)

        // Asserted alongside isValid so the fixture is proven to have been read: an unread store
        // would report invalid too, for the wrong reason.
        assertEquals(4096L, result.size)
        assertFalse(result.isValid)
    }

    @Test
    fun `getCachedSize reports an unwritten location as invalid`() = runTest {
        val source = DataStoreLocationsCacheSource(FakeInMemoryDataStore())

        val result = source.getCachedSize(LocationType.DOWNLOADS)

        assertNull(result.size)
        assertFalse(result.isValid)
    }

    @Test
    fun `getCachedSize is not affected by another location's entry`() = runTest {
        val source = DataStoreLocationsCacheSource(FakeInMemoryDataStore())
        source.updateCache(LocationType.DOWNLOADS, 4096L)

        val result = source.getCachedSize(LocationType.IMAGES)

        assertNull(result.size)
        assertFalse(result.isValid)
    }

    @Test
    fun `clearCache invalidates a previously valid entry`() = runTest {
        val source = DataStoreLocationsCacheSource(FakeInMemoryDataStore())
        source.updateCache(LocationType.DOWNLOADS, 4096L)

        source.clearCache()

        assertFalse(source.getCachedSize(LocationType.DOWNLOADS).isValid)
    }

    private fun minutesAgo(minutes: Long): Long = System.currentTimeMillis() - minutes * 60 * 1000L

    private fun minutesFromNow(minutes: Long): Long = System.currentTimeMillis() + minutes * 60 * 1000L

    private fun storeStampedAt(type: LocationType, size: Long, timestamp: Long) = FakeInMemoryDataStore(
        mutablePreferencesOf(
            longPreferencesKey("size_${type.name}") to size,
            longPreferencesKey("timestamp_${type.name}") to timestamp
        )
    )
}
