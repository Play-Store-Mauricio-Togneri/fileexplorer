package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import android.os.storage.StorageManager
import java.io.File

/** What the framework knows about a mounted volume beyond its size: how it is attached, and what it is called. */
data class VolumeInfo(
    val isEmulated: Boolean,
    val description: String?
)

/**
 * The [VolumeInfo] for the volume mounted at [rootPath], or null when the framework does not
 * recognise the path as a volume root or the lookup fails.
 *
 * [android.os.storage.StorageVolume.getDescription] is the only name for a removable volume that is
 * not a guess: the framework answers "USB drive" or "SD card" in the system's own locale, and OEM
 * builds answer with the volume's label. There is no public API that reports a volume's disk type,
 * so the name has to come from here rather than be derived from one.
 *
 * Its own file, and its own function, for the reason [volumeStatsAt] is one: a storage lookup that
 * reaches for an `android.os` class, which JVM tests have to be able to answer for. The unit-test
 * `android.jar` cannot produce a [StorageManager], so a test going through this would see every
 * volume fall back to its unnamed, untyped shape before naming or typing had been exercised at all
 * — `AndroidStorageSource` takes it as a parameter so a test can answer for it instead.
 */
fun volumeInfoAt(context: Context, rootPath: String): VolumeInfo? =
    try {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        storageManager?.getStorageVolume(File(rootPath))?.let {
            VolumeInfo(isEmulated = it.isEmulated, description = it.getDescription(context))
        }
    } catch (_: Exception) {
        null
    }

