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

        // getExternalFilesDirs() can return more than one entry collapsing to the same storage
        // root (duplicate/emulated mounts on some devices). Deduplicate before building the
        // device list so labels are numbered correctly and path-keyed lazy lists never receive
        // duplicate keys (which crashes Compose measurement).
        val validPaths = externalDirs
            .filterNotNull()
            .map { it.absolutePath.replace(basePath, "") }
            .filter { isValidPath(it) }
            .distinct()

        val sdCardPaths = validPaths.filter { StorageDevice.isSdCard(it) }
        val internalPaths = validPaths.filterNot { StorageDevice.isSdCard(it) }

        validPaths.map { path ->
            val stat = StatFs(path)
            val group = if (StorageDevice.isSdCard(path)) sdCardPaths else internalPaths
            StorageDevice(
                path = path,
                displayName = resolveLabel(
                    StorageDevice.getLabel(
                        path = path,
                        index = group.indexOf(path),
                        count = group.size
                    )
                ),
                totalBytes = stat.totalBytes,
                availableBytes = stat.availableBytes
            )
        }
    }

    private fun resolveLabel(label: StorageLabel): String = when (label) {
        is StorageLabel.Internal -> context.getString(R.string.storage_internal)
        is StorageLabel.InternalNumbered -> "${context.getString(R.string.storage_internal)} ${label.number}"
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
