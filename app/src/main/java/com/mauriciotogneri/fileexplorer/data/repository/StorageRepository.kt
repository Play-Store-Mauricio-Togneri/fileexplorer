package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import android.os.StatFs
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageRepository(private val context: Context) {

    suspend fun getStorages(): List<StorageDevice> = withContext(Dispatchers.IO) {
        val externalDirs = context.getExternalFilesDirs(null)
        val basePath = "/Android/data/${context.packageName}/files"

        externalDirs
            .filterNotNull()
            .mapIndexedNotNull { index, file ->
                val path = file.absolutePath.replace(basePath, "")
                if (isValidPath(path)) {
                    val stat = StatFs(path)
                    StorageDevice(
                        path = path,
                        displayName = StorageDevice.getDisplayName(path, index),
                        totalBytes = stat.totalBytes,
                        availableBytes = stat.availableBytes
                    )
                } else {
                    null
                }
            }
    }

    private fun isValidPath(path: String): Boolean {
        return try {
            StatFs(path).blockCountLong
            true
        } catch (e: Exception) {
            false
        }
    }
}
