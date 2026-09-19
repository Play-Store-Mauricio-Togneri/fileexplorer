package com.mauriciotogneri.fileexplorer.data.util

import android.system.Os
import android.system.OsConstants
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry
import java.io.File

/**
 * A regular file's analyzer entry, read from one `lstat(2)` result.
 *
 * `lstat` keeps a symlink from borrowing its target's identity, and the regular-file check keeps a
 * directory that replaced a scanned file out of the delete path. Null covers every state that is
 * unsafe to act on: absent, unreadable, non-regular, or no longer on an available volume.
 */
internal fun analyzerFileEntryAt(file: File): AnalyzerFileEntry? =
    try {
        val stat = Os.lstat(file.path)
        if (!OsConstants.S_ISREG(stat.st_mode)) {
            null
        } else {
            AnalyzerFileEntry(
                path = file.path,
                size = stat.st_size,
                deviceId = stat.st_dev,
                inode = stat.st_ino,
                changeTimeSeconds = stat.st_ctim.tv_sec,
                changeTimeNanos = stat.st_ctim.tv_nsec
            )
        }
    } catch (_: Exception) {
        null
    }
