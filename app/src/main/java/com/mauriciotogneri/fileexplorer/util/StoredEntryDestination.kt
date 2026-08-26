package com.mauriciotogneri.fileexplorer.util

import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import java.io.File

/**
 * What a tap on a stored home screen entry — a recents entry or a favorite — should do.
 */
sealed interface StoredEntryDestination {
    /** Nothing occupies the stored path any more; tell the user. */
    data object Missing : StoredEntryDestination

    /** A directory occupies the stored path; navigate into it. */
    data class Folder(val path: String, val title: String) : StoredEntryDestination

    /** A file occupies the stored path; open it. */
    data class Open(val file: FileItem) : StoredEntryDestination
}

object StoredEntryDestinationResolver {

    /**
     * Resolves a stored entry — its [path], the [name] and [mimeType] recorded alongside it — into
     * what tapping it should do.
     *
     * Routed by what is on disk now, not by the type the entry recorded when it was stored. A
     * recents entry is a file by contract (`RecentFile.isDirectory` is a constant and `addRecentFile`
     * refuses directories) and a favorite records the type it had when it was added, but
     * `getRecentFiles` and `getFavorites` both re-validate the stored path with `exists()` alone,
     * which a directory satisfies. Without the [StoredEntryDestination.Folder] branch the
     * [FileItem] below would go out with `isDirectory = false` and a mimeType derived from the
     * stored name's extension, so `IntentUtil.openFile` would route a directory on its name alone:
     * to the APK installer for one ending in .apk, to the uncompress dialog for .zip, to a viewer
     * otherwise. Navigating into it is what the folder and search lists do with a directory.
     *
     * The reverse drift — a favorited directory replaced by a file — falls through to
     * [StoredEntryDestination.Open], which is why the mimeType is refilled: a favorited directory is
     * stored with an empty mimeType, so such an entry arrives with nothing to classify it, and
     * `isApk`/`isZip` are the two [FileItem] predicates with no by-extension fallback, so `openFile`
     * would test them against "" and skip the APK and uncompress branches before its own `ifEmpty`
     * refill runs. Recents never needs the refill — `addRecentFile` refuses directories, so its
     * mimeType is never empty — and it is harmless there.
     */
    fun resolve(path: String, name: String, mimeType: String): StoredEntryDestination {
        val file = File(path)

        if (!file.exists()) return StoredEntryDestination.Missing

        if (file.isDirectory) return StoredEntryDestination.Folder(path, name)

        return StoredEntryDestination.Open(
            FileItem(
                path = path,
                name = name,
                isDirectory = false,
                size = 0,
                lastModified = 0,
                createdTime = 0,
                mimeType = mimeType.ifEmpty { MimeTypeUtil.getMimeType(file) }
            )
        )
    }
}
