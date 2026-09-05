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
    fun `analyticsType reports the kind of volume`() {
        // The reported names are the analytics contract, not the enum entry names: a dashboard
        // reading them cannot see a rename.
        assertEquals("internal", createStorageDevice(type = StorageType.INTERNAL).analyticsType)
        assertEquals("sd_card", createStorageDevice(type = StorageType.SD_CARD).analyticsType)
    }

    @Test
    fun `numberDuplicates leaves names that appear once alone`() {
        assertEquals(
            listOf("Internal Storage", "SD card", "USB drive"),
            StorageDevice.numberDuplicates(listOf("Internal Storage", "SD card", "USB drive"))
        )
    }

    @Test
    fun `numberDuplicates numbers repeated names from 1 in the order given`() {
        assertEquals(
            listOf("USB drive 1", "USB drive 2", "USB drive 3"),
            StorageDevice.numberDuplicates(listOf("USB drive", "USB drive", "USB drive"))
        )
    }

    @Test
    fun `numberDuplicates numbers only the name that collided`() {
        // A USB drive next to an SD card is already distinguishable, so numbering either of them
        // would only add noise. Only the pair that actually shares a name is numbered.
        assertEquals(
            listOf("Internal Storage", "SD card 1", "SD card 2", "USB drive"),
            StorageDevice.numberDuplicates(
                listOf("Internal Storage", "SD card", "SD card", "USB drive")
            )
        )
    }

    @Test
    fun `numberDuplicates numbers each colliding name on its own count`() {
        assertEquals(
            listOf("SD card 1", "USB drive 1", "SD card 2", "USB drive 2"),
            StorageDevice.numberDuplicates(
                listOf("SD card", "USB drive", "SD card", "USB drive")
            )
        )
    }

    @Test
    fun `numberDuplicates returns nothing for no volumes`() {
        assertEquals(emptyList<String>(), StorageDevice.numberDuplicates(emptyList()))
    }

    private fun createStorageDevice(
        path: String = "/storage/emulated/0",
        displayName: String = "Internal Storage",
        totalBytes: Long = 64L * 1024 * 1024 * 1024,
        availableBytes: Long = 32L * 1024 * 1024 * 1024,
        type: StorageType = StorageType.INTERNAL
    ) = StorageDevice(
        path = path,
        displayName = displayName,
        totalBytes = totalBytes,
        availableBytes = availableBytes,
        type = type
    )
}
