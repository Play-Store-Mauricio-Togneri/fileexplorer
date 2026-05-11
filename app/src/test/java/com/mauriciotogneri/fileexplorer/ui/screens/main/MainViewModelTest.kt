package com.mauriciotogneri.fileexplorer.ui.screens.main

import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ThemeManager.setTheme(ThemeMode.SYSTEM)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `themeMode reflects ThemeManager value`() = runTest {
        ThemeManager.setTheme(ThemeMode.DARK)

        val viewModel = MainViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `themeMode updates when ThemeManager changes`() = runTest {
        val viewModel = MainViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())

            ThemeManager.setTheme(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem())

            ThemeManager.setTheme(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, awaitItem())
        }
    }
}
