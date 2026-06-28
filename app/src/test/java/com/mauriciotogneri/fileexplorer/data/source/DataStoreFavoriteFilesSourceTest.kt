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

class DataStoreFavoriteFilesSourceTest {

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
    fun `favoritesFlow falls back to empty list when the store fails`() = runTest {
        val source = DataStoreFavoriteFilesSource(FakeThrowingDataStore())
        assertTrue(source.favoritesFlow.first().isEmpty())
    }

    @Test
    fun `getFavorites falls back to empty list when the store fails`() = runTest {
        val source = DataStoreFavoriteFilesSource(FakeThrowingDataStore())
        assertTrue(source.getFavorites().isEmpty())
    }

    @Test
    fun `updateFavorites does not throw when the store fails`() = runTest {
        val source = DataStoreFavoriteFilesSource(FakeThrowingDataStore())
        source.updateFavorites { it }
    }

    @Test
    fun `clearFavorites does not throw when the store fails`() = runTest {
        val source = DataStoreFavoriteFilesSource(FakeThrowingDataStore())
        source.clearFavorites()
    }
}
