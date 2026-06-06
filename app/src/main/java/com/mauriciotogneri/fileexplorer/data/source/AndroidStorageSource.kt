package com.mauriciotogneri.fileexplorer.data.source

import android.content.Context
import android.os.StatFs
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidStorageSource(private val context: Context) : StorageSource {

    override suspend fun getStorages(): List<StorageDevice> = withContext(Dispatchers.IO) {
        val externalDirs = context.getExternalFilesDirs(null)
        val basePath = "/Android/data/${context.packageName}/files"

        val validPaths = externalDirs
            .filterNotNull()
            .map { it.absolutePath.replace(basePath, "") }
            .filter { isValidPath(it) }

        val sdCardPaths = validPaths.filter { StorageDevice.isSdCard(it) }

        validPaths.map { path ->
            val stat = StatFs(path)
            StorageDevice(
                path = path,
                displayName = resolveLabel(
                    StorageDevice.getLabel(
                        path = path,
                        sdCardIndex = sdCardPaths.indexOf(path),
                        sdCardCount = sdCardPaths.size
                    )
                ),
                totalBytes = stat.totalBytes,
                availableBytes = stat.availableBytes
            )
        }
    }

    private fun resolveLabel(label: StorageLabel): String = when (label) {
        is StorageLabel.Internal -> context.getString(R.string.storage_internal)
        is StorageLabel.InternalNumbered -> "${context.getString(R.string.storage_internal)} ${label.suffix}"
        is StorageLabel.SdCard -> context.getString(R.string.storage_sd_card)
        is StorageLabel.SdCardNumbered -> "${context.getString(R.string.storage_sd_card)} ${label.number}"
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
