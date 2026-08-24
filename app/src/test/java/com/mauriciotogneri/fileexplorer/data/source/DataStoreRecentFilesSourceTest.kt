package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataStoreRecentFilesSourceTest {

    @Before
    fun setUp() {
        mockkObject(ErrorReporter)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
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

    @Test
    fun `a blob that cannot be parsed is reported through the scrub`() {
        // The blob is a hand-rolled JSON array of the user's own paths, and on device
        // JSONTokener builds its message as "<reason> at character N of <the entire input>", so
        // reporting the parse failure raw publishes the whole recent-files list rather than one
        // path. That message cannot be produced here — org.json is not mocked on the JVM, so the
        // parse throws the harness's own RuntimeException before any JSON is read — which is why
        // this pins the scrub by shape: the reported object carries the failing type as its
        // message and nothing else. Reverting the call site to a raw `e` fails on that.
        val corrupt = """[{"path":"/storage/emulated/0/Documents/tax-return.pdf","name":"tax"""
        val store = FakeInMemoryDataStore(preferencesOf(stringPreferencesKey("files") to corrupt))
        val reported = slot<Throwable>()
        every { ErrorReporter.error(capture(reported), any(), any()) } just Runs

        val files = runBlocking { DataStoreRecentFilesSource(store).getRecentFiles() }

        assertTrue(files.isEmpty())
        val chain = generateSequence<Throwable>(reported.captured) { it.cause }.toList()
        assertEquals(1, chain.size)
        assertEquals(RuntimeException::class.java.name, chain.single().message)
    }
}
