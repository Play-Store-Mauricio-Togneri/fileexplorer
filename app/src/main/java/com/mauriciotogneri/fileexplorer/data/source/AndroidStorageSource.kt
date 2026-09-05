package com.mauriciotogneri.fileexplorer.data.source

import android.content.Context
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.util.VolumeInfo
import com.mauriciotogneri.fileexplorer.data.util.VolumeStats
import com.mauriciotogneri.fileexplorer.data.util.volumeInfoAt
import com.mauriciotogneri.fileexplorer.data.util.volumeStatsAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidStorageSource(
    private val context: Context,
    // Taken as parameters purely so JVM tests can reach the rest of this class. The defaults read
    // the volume through StatFs and StorageManager, neither of which the unit-test android.jar can
    // construct, so a test going through them would watch every volume be dropped before
    // enumeration, deduplication, typing, naming or the path derivation below had run at all.
    private val volumeStats: (String) -> VolumeStats? = ::volumeStatsAt,
    private val volumeInfo: (String) -> VolumeInfo? = { volumeInfoAt(context, it) }
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

        val volumes = stats.map { (path, stat) -> Volume(path, stat, volumeInfo(path)) }
        val types = volumes.map { type(it) }
        val names = StorageDevice.numberDuplicates(
            volumes.mapIndexed { index, volume -> name(volume, types[index]) }
        )

        volumes.mapIndexed { index, volume ->
            StorageDevice(
                path = volume.path,
                displayName = names[index],
                totalBytes = volume.stats.totalBytes,
                availableBytes = volume.stats.availableBytes,
                type = types[index]
            )
        }
    }

    private fun type(volume: Volume): StorageType =
        if (volume.isRemovable) StorageType.SD_CARD else StorageType.INTERNAL

    /**
     * Internal storage keeps this app's own name for it, which is translated. A removable volume is
     * named by the framework instead, since that is the only place its kind is recorded: the
     * framework answers "USB drive" or "SD card" in the system's own locale, and OEM builds answer
     * with the volume's label. That name is the whole reason a USB drive no longer reads as a
     * second SD card.
     *
     * A framework that answers with nothing falls back to the SD-card string, which is the name
     * every removable volume carried before this — a device that shows a card today keeps the name
     * it had rather than losing one.
     */
    private fun name(volume: Volume, type: StorageType): String =
        if (type == StorageType.INTERNAL) {
            context.getString(R.string.storage_internal)
        } else {
            volume.info?.description?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.storage_sd_card)
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

    private data class Volume(
        val path: String,
        val stats: VolumeStats,
        val info: VolumeInfo?
    ) {
        /**
         * Whether the volume can be detached. Taken from the framework when it recognises the
         * volume, and otherwise from the path, which is how every volume was classified before
         * this: emulated storage is the device's own, anything else is removable.
         */
        val isRemovable: Boolean get() = info?.isEmulated?.not() ?: !path.contains("emulated")
    }
}
