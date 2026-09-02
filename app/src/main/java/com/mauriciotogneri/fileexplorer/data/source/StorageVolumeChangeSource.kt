package com.mauriciotogneri.fileexplorer.data.source

import kotlinx.coroutines.flow.Flow

interface StorageVolumeChangeSource {
    /**
     * Emits once for every volume that is mounted or goes away — an SD card inserted, ejected,
     * removed, or pulled without being ejected first.
     *
     * Distinct from [MediaChangeSource], which reports writes *within* the volumes that are already
     * mounted. Nothing a media provider publishes says that the set of volumes itself changed, so
     * without this a card inserted while the home screen is in the foreground stays invisible until
     * the user leaves the screen and comes back.
     *
     * Coalescing is the collector's job: a single insertion can publish an unmount and a mount.
     */
    fun changes(): Flow<Unit>
}
