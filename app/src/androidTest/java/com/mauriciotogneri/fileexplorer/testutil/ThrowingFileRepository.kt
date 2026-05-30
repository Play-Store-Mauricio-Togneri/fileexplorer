package com.mauriciotogneri.fileexplorer.testutil

import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import java.io.IOException

/**
 * A [FileRepository] whose `listFiles` throws, to drive [com.mauriciotogneri.fileexplorer
 * .ui.screens.folder.FolderViewModel]'s load-error branch (the real repo returns an empty list for
 * bad paths and never throws). Toggle [shouldThrow] to simulate recovery.
 */
class ThrowingFileRepository : FileRepository() {

    @Volatile
    var shouldThrow: Boolean = true

    @Volatile
    var filesOnSuccess: List<FileItem> = emptyList()

    override suspend fun listFiles(
        path: String,
        showHidden: Boolean,
        sortMode: SortMode
    ): List<FileItem> {
        if (shouldThrow) throw IOException("test load failure")
        return filesOnSuccess
    }
}
