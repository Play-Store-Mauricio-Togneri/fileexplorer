package com.mauriciotogneri.fileexplorer.data.source

import android.content.Context
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageLabel
import com.mauriciotogneri.fileexplorer.data.util.VolumeStats
import com.mauriciotogneri.fileexplorer.data.util.volumeStatsAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidStorageSource(
    private val context: Context,
    // Taken as a parameter purely so JVM tests can reach the rest of this class. The default reads
    // the volume through StatFs, which the unit-test android.jar cannot construct, so a test going
    // through it would watch every volume be dropped before enumeration, deduplication, labelling
    // or the path derivation below had run at all.
    private val volumeStats: (String) -> VolumeStats? = ::volumeStatsAt
) : StorageSource {

    override suspend fun getStorages(): List<StorageDevice> = withContext(Dispatchers.IO) {
        val externalDirs = context.getExternalFilesDirs(null)
        val basePath = "/Android/data/${context.packageName}/files"

        // getExternalFilesDirs() can return more than one entry collapsing to the same storage
        // root (duplicate/emulated mounts on some devices). Deduplicate before building the
        // device list so labels are numbered correctly and path-keyed lazy lists never receive
        // duplicate keys (which crashes Compose measurement).
        val stats = externalDirs
            .filterNotNull()
            .map { volumeRootOf(it.absolutePath, basePath) }
            .distinct()
            .mapNotNull { path -> volumeStats(path)?.let { path to it } }

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

    /**
     * The root of the volume that [dirPath] sits on: the app-private directory this app is handed
     * on each volume, with that suffix taken off.
     *
     * Only a trailing occurrence is removed, and only once it has been confirmed to be there. The
     * suffix is built from the package name and will not realistically appear anywhere else in the
     * path, but a plain replace would take an interior one too, and it would rewrite a path the
     * framework returned in some other shape rather than leave it alone. A path that does not end
     * in the suffix is returned unchanged — the same volume the previous replace produced for it,
     * so no device that shows a card today loses one.
     */
    private fun volumeRootOf(dirPath: String, basePath: String): String =
        if (dirPath.endsWith(basePath)) dirPath.removeSuffix(basePath) else dirPath

    private fun resolveLabel(label: StorageLabel): String = when (label) {
        is StorageLabel.Internal -> context.getString(R.string.storage_internal)
        is StorageLabel.InternalNumbered -> "${context.getString(R.string.storage_internal)} ${label.number}"
        is StorageLabel.SdCard -> context.getString(R.string.storage_sd_card)
        is StorageLabel.SdCardNumbered -> "${context.getString(R.string.storage_sd_card)} ${label.number}"
    }
}
