package com.mauriciotogneri.fileexplorer.util

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import java.io.File

/**
 * A folder to open at launch, resolved against the storage devices mounted at that moment.
 *
 * [rootPath] and [rootDisplayName] carry the storage device the folder lives on. Without them the
 * breadcrumb trail renders an SD-card path as its raw `/storage/XXXX-XXXX` segments and lets the
 * user navigate above the storage root; only internal storage is collapsed to a friendly name on
 * its own.
 */
data class StartupDestination(
    val path: String,
    val title: String,
    val rootPath: String,
    val rootDisplayName: String
)

object StartupDestinationResolver {

    /**
     * Resolves the stored startup folder [path] against the mounted [storages], or returns null when
     * the folder cannot be opened: nothing is configured, it was deleted, it was replaced by a file,
     * it is no longer readable, or it sits on a volume that is not mounted. Callers open the home
     * screen instead and tell the user.
     *
     * The longest matching storage root wins, so a volume mounted inside another one is preferred
     * over the volume containing it.
     *
     * The storage device is resolved fresh on every launch rather than stored alongside the path:
     * its display name is localized, and its number ("Internal storage 2") depends on which other
     * volumes are mounted right now.
     */
    fun resolve(path: String?, storages: List<StorageDevice>): StartupDestination? {
        if (path.isNullOrBlank()) return null

        // The picker only ever stores absolute, already-resolved paths, but this value comes back
        // out of app-private storage and is then opened with MANAGE_EXTERNAL_STORAGE, so it is
        // validated rather than trusted. A ".." segment would satisfy the storage-root prefix check
        // below and still resolve outside the volume.
        //
        // Only "." and ".." are rejected, not symlinks: on most devices /storage/emulated/0 is
        // itself a link to /data/media/0, so canonicalizing would leave every real startup folder
        // matching no storage root at all.
        if (path.split('/').any { it == ".." || it == "." }) return null

        val root = storages
            .filter { path == it.path || path.startsWith("${it.path}/") }
            .maxByOrNull { it.path.length }
            ?: return null

        val folder = File(path)
        if (!folder.isDirectory || !folder.canRead()) return null

        return StartupDestination(
            path = path,
            title = label(path, storages),
            rootPath = root.path,
            rootDisplayName = root.displayName
        )
    }

    /**
     * The name to show for [path]. A storage root has no meaningful folder name of its own — the
     * last segment of internal storage is "0", and of an SD card its volume ID — so it is named the
     * way the home screen's storage cards name it.
     *
     * Falls back to the last path segment when [storages] has not loaded yet or does not contain the
     * path, which is correct for every folder that is not itself a storage root.
     */
    fun label(path: String, storages: List<StorageDevice>): String =
        storages.firstOrNull { it.path == path }?.displayName ?: File(path).name
}
