package com.mauriciotogneri.fileexplorer.data.util

import android.os.StatFs

/** How much space a mounted volume holds, and how much of it is still free. */
data class VolumeStats(
    val totalBytes: Long,
    val availableBytes: Long
)

/**
 * The [VolumeStats] for the volume mounted at [rootPath], or null when it cannot be read: it was
 * never mounted, or it was unmounted while the list was being built.
 *
 * Both sizes come from the one [StatFs], rather than the volume being statted to test it and
 * statted again to measure it: constructing [StatFs] is what performs the stat and what throws, so
 * a second call brings down the app whenever a volume disappears in the window between the two.
 *
 * Its own file, and its own function, for the reason [storageAnswersAt] is one: a storage predicate
 * that reaches for an `android.os` class, which JVM tests have to be able to answer for. The
 * unit-test `android.jar` cannot construct [StatFs] at all, so a test going through this would see
 * every volume dropped before enumeration, deduplication or labelling had been exercised at all —
 * `AndroidStorageSource` takes it as a parameter so a test can answer for it instead.
 */
fun volumeStatsAt(rootPath: String): VolumeStats? =
    try {
        val stats = StatFs(rootPath)
        VolumeStats(totalBytes = stats.totalBytes, availableBytes = stats.availableBytes)
    } catch (_: Exception) {
        null
    }
