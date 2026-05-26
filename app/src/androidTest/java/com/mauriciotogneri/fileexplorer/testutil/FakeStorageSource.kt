package com.mauriciotogneri.fileexplorer.testutil

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.source.StorageSource
import java.io.File

class FakeStorageSource(
    private val testDirectory: File
) : StorageSource {
    override suspend fun getStorages(): List<StorageDevice> {
        return listOf(
            StorageDevice(
                path = testDirectory.absolutePath,
                displayName = "Test Storage",
                totalBytes = 1_000_000_000L,
                availableBytes = 500_000_000L
            )
        )
    }
}
