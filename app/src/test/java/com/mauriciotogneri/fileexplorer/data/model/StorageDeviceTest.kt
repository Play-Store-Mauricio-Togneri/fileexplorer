package com.mauriciotogneri.fileexplorer.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageDeviceTest {

    @Test
    fun `formattedTotal returns formatted total bytes`() {
        val storage = createStorageDevice(totalBytes = 64L * 1024 * 1024 * 1024)
        assertEquals("64 GB", storage.formattedTotal)
    }

    @Test
    fun `formattedAvailable returns formatted available bytes`() {
        val storage = createStorageDevice(availableBytes = 32L * 1024 * 1024 * 1024)
        assertEquals("32 GB", storage.formattedAvailable)
    }

    @Test
    fun `getDisplayName returns Internal Storage for emulated 0`() {
        val name = StorageDevice.getDisplayName("/storage/emulated/0", 0)
        assertEquals("Internal Storage", name)
    }

    @Test
    fun `getDisplayName returns Internal Storage with number for other emulated`() {
        val name = StorageDevice.getDisplayName("/storage/emulated/1", 1)
        assertEquals("Internal Storage 1", name)
    }

    @Test
    fun `getDisplayName returns SD Card for first external storage`() {
        val name = StorageDevice.getDisplayName("/storage/sdcard1", 0)
        assertEquals("SD Card", name)
    }

    @Test
    fun `getDisplayName returns SD Card with number for additional external storage`() {
        val name = StorageDevice.getDisplayName("/storage/sdcard2", 1)
        assertEquals("SD Card 2", name)
    }

    @Test
    fun `getDisplayName handles various SD card paths`() {
        assertEquals("SD Card", StorageDevice.getDisplayName("/storage/1234-5678", 0))
        assertEquals("SD Card 2", StorageDevice.getDisplayName("/storage/ABCD-EFGH", 1))
    }

    private fun createStorageDevice(
        path: String = "/storage/emulated/0",
        displayName: String = "Internal Storage",
        totalBytes: Long = 64L * 1024 * 1024 * 1024,
        availableBytes: Long = 32L * 1024 * 1024 * 1024
    ) = StorageDevice(
        path = path,
        displayName = displayName,
        totalBytes = totalBytes,
        availableBytes = availableBytes
    )
}
