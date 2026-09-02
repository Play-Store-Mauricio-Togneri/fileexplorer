package com.mauriciotogneri.fileexplorer.data.util

import android.os.StatFs

/**
 * Whether the volume mounted at [rootPath] still answers a stat.
 *
 * The question [isStorageUnavailable] cannot always be asked. An errno only exists where a syscall
 * failed and the platform attached one, and the sharpest way a volume leaves a walk raises nothing
 * at all: `File.list()` answers null and the walk carries on over a subtree that is no longer
 * there. Asking the volume directly needs no errno and no failure to have been raised.
 *
 * Best effort in one direction only. A stat that fails means the volume is gone; a stat that
 * succeeds does not prove it is there — `statfs(2)` follows the path, so a mount point left behind
 * by an unmount answers for the filesystem underneath it, and a live root answers while a subtree
 * below it has gone. Callers use it to turn some silent losses into reported ones, never to
 * conclude that a walk was complete.
 *
 * Constructing [StatFs] is what performs the stat and what throws, exactly as
 * [volumeStatsAt] documents; the instance is discarded because only whether it
 * could be built is being asked.
 *
 * Its own file, and its own function, for the reason `DiskSpace.kt` is one: a storage predicate
 * that reaches for an `android.os` class, which JVM tests have to be able to answer for. Under the
 * unit-test `android.jar` this constructor neither stats anything nor fails, so a test that went
 * through it would silently always see an available volume — `FileRepositoryTest` stubs this
 * function rather than trusting that.
 */
internal fun storageAnswersAt(rootPath: String): Boolean =
    try {
        StatFs(rootPath)
        true
    } catch (_: Exception) {
        false
    }
