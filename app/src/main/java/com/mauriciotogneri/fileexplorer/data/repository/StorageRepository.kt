package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.source.StorageSource

class StorageRepository(private val source: StorageSource) {

    // Enforce unique paths regardless of the source: storage paths are used as keys in lazy
    // lists (e.g. the destination picker's storage selector), and duplicate keys crash Compose
    // measurement. distinctBy keeps the first occurrence, preserving order.
    suspend fun getStorages(): List<StorageDevice> = source.getStorages().distinctBy { it.path }
}
