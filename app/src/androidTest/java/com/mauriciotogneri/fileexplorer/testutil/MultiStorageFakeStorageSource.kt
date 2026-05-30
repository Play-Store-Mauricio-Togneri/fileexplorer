package com.mauriciotogneri.fileexplorer.testutil

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.source.StorageSource
import java.io.File

/**
 * A [StorageSource] that exposes multiple test storage roots — used by picker storage-switching
 * tests where [FakeStorageSource] (single root) would skip the selector.
 */
class MultiStorageFakeStorageSource(
    private val storages: List<StorageDevice>
) : StorageSource {
    override suspend fun getStorages(): List<StorageDevice> = storages

    companion object {
        fun of(vararg dirs: Pair<File, String>): MultiStorageFakeStorageSource =
            MultiStorageFakeStorageSource(
                dirs.map { (dir, name) ->
                    StorageDevice(
                        path = dir.absolutePath,
                        displayName = name,
                        totalBytes = 1_000_000_000L,
                        availableBytes = 500_000_000L
                    )
                }
            )
    }
}
