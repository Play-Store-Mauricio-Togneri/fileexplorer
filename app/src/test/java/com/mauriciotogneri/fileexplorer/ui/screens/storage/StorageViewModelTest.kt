package com.mauriciotogneri.fileexplorer.ui.screens.storage

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.util.PermissionChecker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storageRepository: StorageRepository
    private lateinit var permissionChecker: PermissionChecker

    private val testStorages = listOf(
        StorageDevice(
            path = "/storage/emulated/0",
            displayName = "Internal Storage",
            totalBytes = 64_000_000_000L,
            availableBytes = 32_000_000_000L
        ),
        StorageDevice(
            path = "/storage/sdcard1",
            displayName = "SD Card",
            totalBytes = 32_000_000_000L,
            availableBytes = 16_000_000_000L
        )
    )

    private val singleStorage = listOf(
        StorageDevice(
            path = "/storage/emulated/0",
            displayName = "Internal Storage",
            totalBytes = 64_000_000_000L,
            availableBytes = 32_000_000_000L
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        storageRepository = mockk()
        permissionChecker = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state without permission has isLoading false`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns false

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.hasPermission)
        assertFalse(state.isLoading)
        assertTrue(state.storages.isEmpty())
    }

    @Test
    fun `initial state with permission loads storages`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns true
        coEvery { storageRepository.getStorages() } returns testStorages

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasPermission)
        assertFalse(state.isLoading)
        assertEquals(2, state.storages.size)
    }

    @Test
    fun `onPermissionResult with granted true loads storages`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns false
        coEvery { storageRepository.getStorages() } returns testStorages

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially no permission
        assertFalse(viewModel.state.value.hasPermission)

        // Grant permission
        viewModel.onPermissionResult(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should now have permission and loaded storages
        val state = viewModel.state.value
        assertTrue(state.hasPermission)
        assertFalse(state.isLoading)
        assertEquals(2, state.storages.size)
        assertEquals("Internal Storage", state.storages[0].displayName)
        assertEquals("SD Card", state.storages[1].displayName)
    }

    @Test
    fun `onPermissionResult with granted false does not load storages`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns false

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPermissionResult(false)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.hasPermission)
        assertTrue(state.storages.isEmpty())
    }

    @Test
    fun `shouldAutoNavigate returns true when single storage and has permission`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns false
        coEvery { storageRepository.getStorages() } returns singleStorage

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially should not auto-navigate (no permission)
        assertFalse(viewModel.shouldAutoNavigate())

        // Grant permission and load storages
        viewModel.onPermissionResult(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Now should auto-navigate
        assertTrue(viewModel.shouldAutoNavigate())
    }

    @Test
    fun `shouldAutoNavigate returns false when multiple storages`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns false
        coEvery { storageRepository.getStorages() } returns testStorages

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPermissionResult(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.shouldAutoNavigate())
    }

    @Test
    fun `shouldAutoNavigate returns false when still loading`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns true
        coEvery { storageRepository.getStorages() } returns singleStorage

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        // Don't advance scheduler - keep it in loading state

        assertFalse(viewModel.shouldAutoNavigate())
    }

    @Test
    fun `shouldAutoNavigate returns false when no permission`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns false

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.shouldAutoNavigate())
    }

    @Test
    fun `getSingleStoragePath returns path when single storage`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns false
        coEvery { storageRepository.getStorages() } returns singleStorage

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPermissionResult(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("/storage/emulated/0", viewModel.getSingleStoragePath())
    }

    @Test
    fun `getSingleStoragePath returns null when multiple storages`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns false
        coEvery { storageRepository.getStorages() } returns testStorages

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPermissionResult(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.getSingleStoragePath())
    }

    @Test
    fun `getSingleStoragePath returns null when no permission`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns false

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.getSingleStoragePath())
    }

    @Test
    fun `error state is set when repository throws exception`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns false
        coEvery { storageRepository.getStorages() } throws RuntimeException("Storage access failed")

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPermissionResult(true)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Storage access failed", state.error)
        assertTrue(state.storages.isEmpty())
    }

    @Test
    fun `checkPermissionAndLoad reloads when called again`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns true
        coEvery { storageRepository.getStorages() } returns singleStorage

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.storages.size)

        // Change the mock to return different data
        coEvery { storageRepository.getStorages() } returns testStorages

        viewModel.checkPermissionAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.storages.size)
    }

    @Test
    fun `empty storage list is handled correctly`() = runTest {
        every { permissionChecker.hasStoragePermission() } returns true
        coEvery { storageRepository.getStorages() } returns emptyList()

        val viewModel = StorageViewModel(storageRepository, permissionChecker)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasPermission)
        assertFalse(state.isLoading)
        assertTrue(state.storages.isEmpty())
        assertFalse(viewModel.shouldAutoNavigate())
    }
}
