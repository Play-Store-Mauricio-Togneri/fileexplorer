package com.mauriciotogneri.fileexplorer.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
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
    data class RequiresInstallPermission(val file: FileItem) : OpenFileResult()
    data class RequiresTextViewer(val file: FileItem) : OpenFileResult()
    data class RequiresImageViewer(val file: FileItem) : OpenFileResult()
}

object IntentUtil {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun shareFiles(context: Context, files: List<FileItem>) {
        if (files.isEmpty()) return

        try {
            if (files.size == 1) {
                shareSingleFile(context, files.first())
            } else {
                shareMultipleFiles(context, files)
            }
        } catch (e: Exception) {
            ErrorReporter.warning(e, "share_files")
            Toast.makeText(context, R.string.share_files_error, Toast.LENGTH_SHORT).show()
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
            return openApkFile(context, file, source)
        }

        if (file.isZip) {
            return OpenFileResult.RequiresUncompress(file)
        }

        val uri = try {
            getFileUri(context, File(file.path))
        } catch (e: IllegalArgumentException) {
            ErrorReporter.warning(e, "open_file_uri")
            Toast.makeText(context, R.string.open_file_error, Toast.LENGTH_SHORT).show()
            return OpenFileResult.Handled
        }
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
        } catch (_: ActivityNotFoundException) {
            openWithFallback(context, uri)
        } catch (e: Exception) {
            ErrorReporter.warning(e, "open_file")
            openWithFallback(context, uri)
        }

        if (opened) {
            trackRecentFile(context, file)
            trackFileOpened(file, mimeType, source)
            return OpenFileResult.Handled
        }

        // No installed app could handle the file: offer a built-in viewer for text or image files.
        // Recent/analytics tracking happens in the viewer once the content loads successfully.
        if (file.isText) {
            return OpenFileResult.RequiresTextViewer(file)
        }

        if (file.isViewableImage) {
            return OpenFileResult.RequiresImageViewer(file)
        }

        Toast.makeText(context, R.string.open_file_error, Toast.LENGTH_SHORT).show()
        return OpenFileResult.Handled
    }

    fun openFileWith(context: Context, file: FileItem, source: String): Boolean {
        val uri = try {
            getFileUri(context, File(file.path))
        } catch (e: IllegalArgumentException) {
            ErrorReporter.warning(e, "open_file_with_uri")
            Toast.makeText(context, R.string.open_file_error, Toast.LENGTH_SHORT).show()
            return false
        }
        val mimeType = file.mimeType.ifEmpty { MimeTypeUtil.getMimeType(File(file.path)) }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val opened = try {
            context.startActivity(Intent.createChooser(intent, null))
            true
        } catch (e: Exception) {
            ErrorReporter.warning(e, "open_file_with")
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
        } catch (_: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            ErrorReporter.warning(e, "open_file_fallback")
            false
        }
    }

    fun trackRecentFile(context: Context, file: FileItem) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val preferencesRepository =
                    PreferencesRepository(DataStorePreferencesSource(appContext.preferencesDataStore))
                val isEnabled = preferencesRepository.recentFilesEnabled.first()
                if (!isEnabled) return@launch

                RecentFilesRepository(DataStoreRecentFilesSource(appContext.recentFilesDataStore)).addRecentFile(
                    File(file.path)
                )
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

    fun canInstallApks(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun openApkFile(context: Context, file: FileItem, source: String): OpenFileResult {
        if (!canInstallApks(context)) {
            return OpenFileResult.RequiresInstallPermission(file)
        }

        return installApk(context, file, source)
    }

    fun installApk(context: Context, file: FileItem, source: String): OpenFileResult {
        val uri = try {
            getFileUri(context, File(file.path))
        } catch (e: IllegalArgumentException) {
            ErrorReporter.warning(e, "install_apk_uri")
            Toast.makeText(context, R.string.apk_install_error, Toast.LENGTH_SHORT).show()
            return OpenFileResult.Handled
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val opened = try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            ErrorReporter.warning(e, "install_apk")
            false
        }

        if (opened) {
            trackRecentFile(context, file)
            AnalyticsTracker.trackApkInstallTriggered(source)
            AnalyticsTracker.trackFileOpened(
                FileExtensionUtil.getExtension(file.path),
                "application/vnd.android.package-archive",
                source
            )
        } else {
            AnalyticsTracker.trackApkInstallFailed(source)
            Toast.makeText(context, R.string.apk_install_error, Toast.LENGTH_SHORT).show()
        }

        return OpenFileResult.Handled
    }

    /**
     * Launches the system "install unknown apps" permission settings, guarding against ROMs
     * where the settings Activity is missing or denies the launch — so the action shows a toast
     * instead of crashing.
     */
    fun openInstallPermissionSettings(context: Context) {
        try {
            context.startActivity(getInstallPermissionSettingsIntent(context))
        } catch (e: Exception) {
            ErrorReporter.warning(e, "install_permission_settings")
            Toast.makeText(
                context,
                R.string.install_permission_settings_unavailable,
                Toast.LENGTH_LONG
            )
                .show()
        }
    }

    private fun getInstallPermissionSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            )
        } else {
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    /**
     * Opens the system "All files access" settings (Android R+). Prefers the per-app deep-link;
     * if the device has no Activity for it (some OEM ROMs/emulators), falls back to the global
     * all-files-access list, then to a toast — so the grant action never crashes or silently fails.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun openAllFilesAccessSettings(context: Context) {
        val appIntent = Intent(
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:${context.packageName}".toUri()
        )

        try {
            context.startActivity(appIntent)
        } catch (_: ActivityNotFoundException) {
            openAllFilesAccessList(context)
        } catch (e: Exception) {
            ErrorReporter.warning(e, "manage_app_all_files_access")
            openAllFilesAccessList(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun openAllFilesAccessList(context: Context) {
        val listIntent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

        try {
            context.startActivity(listIntent)
        } catch (e: Exception) {
            ErrorReporter.warning(e, "manage_all_files_access")
            Toast.makeText(context, R.string.permission_settings_unavailable, Toast.LENGTH_LONG)
                .show()
        }
    }
}
