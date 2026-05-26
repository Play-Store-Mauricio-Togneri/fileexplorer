package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice

class FakeStorageSource(
    private val storages: List<StorageDevice> = emptyList()
) : StorageSource {

    override suspend fun getStorages(): List<StorageDevice> = storages
}
