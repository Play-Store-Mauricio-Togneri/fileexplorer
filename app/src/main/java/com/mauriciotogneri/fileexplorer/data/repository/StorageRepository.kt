package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.source.StorageSource

class StorageRepository(private val source: StorageSource) {

    suspend fun getStorages(): List<StorageDevice> = source.getStorages()
}
