package com.mauriciotogneri.fileexplorer.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.FileExtensionUtil
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

sealed class OpenFileResult {
    data object Handled : OpenFileResult()
    data class RequiresUncompress(val file: FileItem) : OpenFileResult()
}

object IntentUtil {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun shareFiles(context: Context, files: List<FileItem>): Boolean {
        if (files.isEmpty()) return false

        return try {
            if (files.size == 1) {
                shareSingleFile(context, files.first())
            } else {
                shareMultipleFiles(context, files)
            }
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun shareSingleFile(context: Context, file: FileItem) {
        val uri = getFileUri(context, File(file.path))
        val mimeType = file.mimeType.ifEmpty { MimeTypeUtil.getMimeType(File(file.path)) }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun shareMultipleFiles(context: Context, files: List<FileItem>) {
        val uris = ArrayList(
            files.map { getFileUri(context, File(it.path)) }
        )

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, null))
    }

    fun openFile(context: Context, file: FileItem, source: String): OpenFileResult {
        if (file.isApk) {
            Toast.makeText(context, R.string.apk_install_not_supported, Toast.LENGTH_SHORT).show()
            return OpenFileResult.Handled
        }

        if (file.isZip) {
            return OpenFileResult.RequiresUncompress(file)
        }

        val uri = getFileUri(context, File(file.path))
        val mimeType = file.mimeType.ifEmpty { MimeTypeUtil.getMimeType(File(file.path)) }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val opened = try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                openWithFallback(context, uri)
            }
        } catch (e: ActivityNotFoundException) {
            openWithFallback(context, uri)
        }

        if (opened) {
            trackRecentFile(context, file)
            trackFileOpened(file, mimeType, source)
        } else {
            Toast.makeText(context, R.string.open_file_error, Toast.LENGTH_SHORT).show()
        }

        return OpenFileResult.Handled
    }

    fun openFileWith(context: Context, file: FileItem, source: String): Boolean {
        val uri = getFileUri(context, File(file.path))
        val mimeType = file.mimeType.ifEmpty { MimeTypeUtil.getMimeType(File(file.path)) }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val opened = try {
            context.startActivity(Intent.createChooser(intent, null))
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.open_file_error, Toast.LENGTH_SHORT).show()
            false
        }

        if (opened) {
            trackRecentFile(context, file)
            trackFileOpened(file, mimeType, source)
        }

        return opened
    }

    private fun trackFileOpened(file: FileItem, mimeType: String, source: String) {
        AnalyticsTracker.trackFileOpened(
            FileExtensionUtil.getExtension(file.path),
            mimeType,
            source
        )
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

    fun trackRecentFile(context: Context, file: FileItem) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val preferencesRepository = PreferencesRepository(appContext.preferencesDataStore)
                val isEnabled = preferencesRepository.recentFilesEnabled.first()
                if (!isEnabled) return@launch

                RecentFilesRepository(appContext.recentFilesDataStore).addRecentFile(File(file.path))
            } catch (e: Exception) {
                ErrorReporter.warning(e, "add_recent_file")
            }
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
