package com.mauriciotogneri.fileexplorer.data.source

import android.content.Context
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.util.GenericVolumeKind
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
     * Every volume is named from this app's own resources, so the storage list reads in one
     * language — the app's — whatever language the device is set to.
     *
     * The framework is asked one question and told nothing else: is this a USB drive. It can answer
     * only for a volume it named generically, which is one carrying no label of its own; a labelled
     * volume is described by that label and there is no public API that would say what kind of disk
     * it sits on. So a volume the framework does not call a USB drive is called a card, which is
     * what every removable volume was called before the kind was read at all.
     *
     * The volume's own label is deliberately not shown. It is the one part of a description that is
     * not a translation — but it is also the part that hides the kind, and a card labelled
     * "SDCARD" or "UNTITLED" by whoever formatted it is not a better name than the app's own.
     * [StorageDevice.numberDuplicates] tells two volumes of the same kind apart instead.
     */
    private fun name(volume: Volume, type: StorageType): String = when {
        type == StorageType.INTERNAL -> context.getString(R.string.storage_internal)

        volume.info?.genericKind == GenericVolumeKind.USB_DRIVE ->
            context.getString(R.string.storage_usb_drive)

        else -> context.getString(R.string.storage_sd_card)
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
         *
         * The framework answer is either flag rather than [VolumeInfo.isRemovable] on its own. A
         * card adopted as internal storage is emulated and removable at once, so the removable flag
         * has to count; and a volume the framework calls neither is a card under the path rule this
         * replaced, so the emulated flag has to keep counting.
         */
        val isRemovable: Boolean
            get() = info?.let { !it.isEmulated || it.isRemovable } ?: !path.contains("emulated")
    }
}
