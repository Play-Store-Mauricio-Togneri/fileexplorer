package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreUtil {

    fun scanFile(context: Context, path: String) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(path),
            null,
            null
        )
    }

    fun scanFiles(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(
            context,
            paths.toTypedArray(),
            null,
            null
        )
    }

    suspend fun notifyDeleted(context: Context, paths: List<String>) = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext
        val uri = MediaStore.Files.getContentUri("external")
        paths.forEach { path ->
            context.contentResolver.delete(
                uri,
                "${MediaStore.Files.FileColumns.DATA}=?",
                arrayOf(path)
            )
        }
    }
}
