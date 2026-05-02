package com.mauriciotogneri.fileexplorer.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import java.io.File

object IntentUtil {

    fun openFile(context: Context, file: FileItem): Boolean {
        val uri = getFileUri(context, File(file.path))
        val mimeType = file.mimeType.ifEmpty { MimeTypeUtil.getMimeType(File(file.path)) }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                openWithFallback(context, uri)
            }
        } catch (e: ActivityNotFoundException) {
            openWithFallback(context, uri)
        }
    }

    private fun openWithFallback(context: Context, uri: Uri): Boolean {
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(fallbackIntent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun getFileUri(context: Context, file: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } else {
            Uri.fromFile(file)
        }
    }
}
