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
        source.updateOne(LocationType.DOWNLOADS, 1234L)
    }

    @Test
    fun `clearCache absorbs a store failure and reports that nothing was cleared`() = runTest {
        // Absorbed rather than thrown, but the caller still has to be able to tell:
        // LocationsRepository spends a one-shot staleness mark to make this call, and a swallowed
        // clear that read as done would take the mark with it.
        val source = DataStoreLocationsCacheSource(FakeThrowingDataStore())
        assertFalse(source.clearCache())
    }

    // Between mutations the TTL is what invalidates a location size — the home screen no longer
    // clears the cache before each load — so the hit/expiry contract below is the sole guard
    // against either re-walking every location on each resume or never refreshing a size at all.

    @Test
    fun `getCachedSize returns a size written within the cache duration`() = runTest {
        val source = DataStoreLocationsCacheSource(FakeInMemoryDataStore())
        source.updateOne(LocationType.DOWNLOADS, 4096L)

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
        source.updateOne(LocationType.DOWNLOADS, 4096L)

        val result = source.getCachedSize(LocationType.IMAGES)

        assertNull(result.size)
        assertFalse(result.isValid)
    }

    // A real store flushes to disk on every write, and the home screen updates the cache for every
    // location it just measured. The tests below pin the batching that keeps that one flush, and
    // the generation guard that makes batching safe to defer to the end of the pass.

    @Test
    fun `updateCache stores every size in a single write`() = runTest {
        val dataStore = FakeInMemoryDataStore()
        val source = DataStoreLocationsCacheSource(dataStore)

        source.updateCache(
            mapOf(
                LocationType.DOWNLOADS to 4096L,
                LocationType.IMAGES to 512L,
                LocationType.VIDEOS to 128L
            ),
            source.generation()
        )

        assertEquals(1, dataStore.writeCount)
        assertEquals(4096L, source.getCachedSize(LocationType.DOWNLOADS).size)
        assertEquals(512L, source.getCachedSize(LocationType.IMAGES).size)
        assertEquals(128L, source.getCachedSize(LocationType.VIDEOS).size)
    }

    @Test
    fun `updateCache does not write when there is nothing to store`() = runTest {
        // Every location hit the cache, so the load must cost no write at all.
        val dataStore = FakeInMemoryDataStore()
        val source = DataStoreLocationsCacheSource(dataStore)

        source.updateCache(emptyMap(), source.generation())

        assertEquals(0, dataStore.writeCount)
    }

    @Test
    fun `updateCache discards the batch when the cache was cleared while it was being measured`() = runTest {
        // The whole point of deferring the write to the end of the pass: a delete that finishes
        // while the pass is measuring clears the cache, and the batch is still holding the totals
        // read before it. Writing them would hide the deletion for the full TTL.
        val source = DataStoreLocationsCacheSource(FakeInMemoryDataStore())
        val generation = source.generation()

        source.clearCache()
        source.updateCache(mapOf(LocationType.DOWNLOADS to 4096L), generation)

        assertNull(source.getCachedSize(LocationType.DOWNLOADS).size)
        assertFalse(source.getCachedSize(LocationType.DOWNLOADS).isValid)
    }

    @Test
    fun `updateCache sees a clear made through another source over the same store`() = runTest {
        // Production never has one instance: every ViewModel builds its own
        // DataStoreLocationsCacheSource over the shared locationsCacheDataStore, so the pass that
        // measures and the mutation that clears are usually different objects. Pins the guard state
        // as living in the store rather than in a field on whichever instance ran first.
        val dataStore = FakeInMemoryDataStore()
        val measuring = DataStoreLocationsCacheSource(dataStore)
        val mutating = DataStoreLocationsCacheSource(dataStore)
        val generation = measuring.generation()

        mutating.clearCache()
        measuring.updateCache(mapOf(LocationType.DOWNLOADS to 4096L), generation)

        assertNull(measuring.getCachedSize(LocationType.DOWNLOADS).size)
    }

    @Test
    fun `updateCache stores the batch when nothing cleared the cache`() = runTest {
        // The complement of the test above: the guard must not reject an ordinary pass, or every
        // load would recompute and the cache would never serve anything.
        val source = DataStoreLocationsCacheSource(FakeInMemoryDataStore())
        val generation = source.generation()

        source.updateCache(mapOf(LocationType.DOWNLOADS to 4096L), generation)

        assertEquals(4096L, source.getCachedSize(LocationType.DOWNLOADS).size)
        assertTrue(source.getCachedSize(LocationType.DOWNLOADS).isValid)
    }

    @Test
    fun `clearCache invalidates a previously valid entry`() = runTest {
        val source = DataStoreLocationsCacheSource(FakeInMemoryDataStore())
        source.updateOne(LocationType.DOWNLOADS, 4096L)

        // The complement of the failure case above: a clear that reached the store says so, or the
        // caller would put its staleness mark back and clear again on every load.
        assertTrue(source.clearCache())

        assertFalse(source.getCachedSize(LocationType.DOWNLOADS).isValid)
    }

    /**
     * Writes one location at the store's current generation. Production always batches; these are
     * the tests whose subject is a single entry's hit, expiry or clearing, not the batching.
     */
    private suspend fun DataStoreLocationsCacheSource.updateOne(type: LocationType, size: Long) =
        updateCache(mapOf(type to size), generation())

    private fun minutesAgo(minutes: Long): Long = System.currentTimeMillis() - minutes * 60 * 1000L

    private fun minutesFromNow(minutes: Long): Long = System.currentTimeMillis() + minutes * 60 * 1000L

    private fun storeStampedAt(type: LocationType, size: Long, timestamp: Long) = FakeInMemoryDataStore(
        mutablePreferencesOf(
            longPreferencesKey("size_${type.name}") to size,
            longPreferencesKey("timestamp_${type.name}") to timestamp
        )
    )
}
