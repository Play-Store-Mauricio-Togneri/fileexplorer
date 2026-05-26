package com.mauriciotogneri.fileexplorer.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Interface for checking storage permissions.
 * Extracted to allow for easier testing of ViewModels.
 */
interface PermissionChecker {
    fun hasStoragePermission(): Boolean
}

/**
 * Production implementation that checks actual Android permissions.
 */
class AndroidPermissionChecker(private val context: Context) : PermissionChecker {
    override fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
