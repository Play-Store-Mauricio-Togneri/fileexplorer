package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
}
