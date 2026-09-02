package com.mauriciotogneri.fileexplorer.data.util

import java.io.File

/**
 * Whether the entry stored for [path] is one a prune may forget: its file is gone *and* the volume
 * it lived on is currently mounted, so "gone" is an answer about the file rather than about the
 * volume.
 *
 * `File.exists()` cannot tell those two apart, and an unmounted volume is the case where it is
 * confidently wrong about every path at once. A prune that trusted it alone would delete the user's
 * favorites and recents on an SD card they had merely ejected, and putting the card back would not
 * bring them back — the store has already been rewritten, and nothing restores it.
 *
 * Stale references are ordinary here, so the bias runs one way: an entry whose volume is missing,
 * unrecognised, or could not be enumerated at all is kept. Keeping one leaves a card the user may
 * still see until something writes the store — both lists filter non-existent files, but only when
 * an emission makes them re-read — and that is a state this app already expects and survives.
 * Forgetting one is permanent, and no volume coming back undoes it.
 *
 * [mountedRoots] are the storage roots mounted right now, as `StorageRepository.getStorages()`
 * reports them. Matching mirrors `StartupDestinationResolver`: a path is on a volume when it is the
 * root itself or sits beneath it. The separator is appended only where the root does not already
 * end in one, so that a root reported as "/" matches the tree under it rather than nothing.
 */
internal fun isForgettable(path: String, mountedRoots: List<String>): Boolean =
    !File(path).exists() && mountedRoots.any { root ->
        path == root || path.startsWith(if (root.endsWith(File.separatorChar)) root else root + File.separatorChar)
    }
