package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataStoreRecentFilesSourceTest {

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
    fun `recentFilesFlow falls back to empty list when the store fails`() = runTest {
        val source = DataStoreRecentFilesSource(FakeThrowingDataStore())
        assertTrue(source.recentFilesFlow.first().isEmpty())
    }

    @Test
    fun `getRecentFiles falls back to empty list when the store fails`() = runTest {
        val source = DataStoreRecentFilesSource(FakeThrowingDataStore())
        assertTrue(source.getRecentFiles().isEmpty())
    }

    @Test
    fun `updateRecentFiles does not throw when the store fails`() = runTest {
        val source = DataStoreRecentFilesSource(FakeThrowingDataStore())
        source.updateRecentFiles { it }
    }

    @Test
    fun `clearRecentFiles does not throw when the store fails`() = runTest {
        val source = DataStoreRecentFilesSource(FakeThrowingDataStore())
        source.clearRecentFiles()
    }
}
