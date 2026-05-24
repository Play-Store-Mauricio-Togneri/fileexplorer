package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.source.FakeStorageSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageRepositoryTest {

    @Test
    fun `getStorages returns empty list when no storages available`() = runTest {
        val repository = StorageRepository(FakeStorageSource())

        val result = repository.getStorages()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getStorages returns single storage device`() = runTest {
        val storage = StorageDevice(
            path = "/storage/emulated/0",
            displayName = "Internal Storage",
            totalBytes = 32_000_000_000L,
            availableBytes = 16_000_000_000L
        )
        val repository = StorageRepository(FakeStorageSource(listOf(storage)))

        val result = repository.getStorages()

        assertEquals(1, result.size)
        assertEquals("/storage/emulated/0", result[0].path)
        assertEquals("Internal Storage", result[0].displayName)
        assertEquals(32_000_000_000L, result[0].totalBytes)
        assertEquals(16_000_000_000L, result[0].availableBytes)
    }

    @Test
    fun `getStorages returns multiple storage devices`() = runTest {
        val internalStorage = StorageDevice(
            path = "/storage/emulated/0",
            displayName = "Internal Storage",
            totalBytes = 32_000_000_000L,
            availableBytes = 16_000_000_000L
        )
        val sdCard = StorageDevice(
            path = "/storage/sdcard1",
            displayName = "SD Card",
            totalBytes = 64_000_000_000L,
            availableBytes = 50_000_000_000L
        )
        val repository = StorageRepository(FakeStorageSource(listOf(internalStorage, sdCard)))

        val result = repository.getStorages()

        assertEquals(2, result.size)
        assertEquals("Internal Storage", result[0].displayName)
        assertEquals("SD Card", result[1].displayName)
    }
}
