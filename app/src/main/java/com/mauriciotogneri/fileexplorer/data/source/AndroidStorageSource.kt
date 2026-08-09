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
        val stats = externalDirs
            .filterNotNull()
            .map { it.absolutePath.replace(basePath, "") }
            .distinct()
            .mapNotNull { path -> statOrNull(path)?.let { path to it } }

        val validPaths = stats.map { (path, _) -> path }
        val sdCardPaths = validPaths.filter { StorageDevice.isSdCard(it) }
        val internalPaths = validPaths.filterNot { StorageDevice.isSdCard(it) }

        stats.map { (path, stat) ->
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

    /**
     * The [StatFs] for [path], or null when the volume cannot be read: it was never mounted, or it
     * was unmounted while the list was being built.
     *
     * Statted once per path and kept, rather than statted to test the path and statted again to
     * read its size: constructing [StatFs] is what performs the stat and what throws, so the second
     * call brings down the app whenever a volume disappears in the window between the two.
     */
    private fun statOrNull(path: String): StatFs? {
        return try {
            StatFs(path)
        } catch (_: Exception) {
            null
        }
    }
}
