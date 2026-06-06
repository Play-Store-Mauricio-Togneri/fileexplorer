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
        val name = StorageDevice.getDisplayName("/storage/emulated/0", sdCardIndex = -1, sdCardCount = 0)
        assertEquals("Internal Storage", name)
    }

    @Test
    fun `getDisplayName returns Internal Storage with number for other emulated`() {
        val name = StorageDevice.getDisplayName("/storage/emulated/1", sdCardIndex = -1, sdCardCount = 0)
        assertEquals("Internal Storage 1", name)
    }

    @Test
    fun `getDisplayName returns SD Card without number for a single SD card`() {
        val name = StorageDevice.getDisplayName("/storage/sdcard1", sdCardIndex = 0, sdCardCount = 1)
        assertEquals("SD Card", name)
    }

    @Test
    fun `getDisplayName numbers SD cards from 1 when more than one is present`() {
        assertEquals("SD Card 1", StorageDevice.getDisplayName("/storage/sdcard1", sdCardIndex = 0, sdCardCount = 2))
        assertEquals("SD Card 2", StorageDevice.getDisplayName("/storage/ABCD-EFGH", sdCardIndex = 1, sdCardCount = 2))
    }

    @Test
    fun `isSdCard is false for emulated paths`() {
        assertEquals(false, StorageDevice.isSdCard("/storage/emulated/0"))
        assertEquals(false, StorageDevice.isSdCard("/storage/emulated/10"))
    }

    @Test
    fun `isSdCard is true for non-emulated paths`() {
        assertEquals(true, StorageDevice.isSdCard("/storage/1234-5678"))
        assertEquals(true, StorageDevice.isSdCard("/storage/sdcard1"))
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
