package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice

interface StorageSource {
    suspend fun getStorages(): List<StorageDevice>
}
