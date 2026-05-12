package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.mauriciotogneri.fileexplorer.data.model.ApkMetadata
import java.io.File

object ApkMetadataExtractor {

    fun extract(context: Context, file: File): ApkMetadata? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            val packageManager = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.GET_PERMISSIONS
                )
            } ?: return null

            packageInfo.applicationInfo?.sourceDir = file.absolutePath
            packageInfo.applicationInfo?.publicSourceDir = file.absolutePath

            val appName = try {
                packageInfo.applicationInfo?.loadLabel(packageManager)?.toString()
            } catch (e: Exception) {
                null
            }

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val permissions = packageInfo.requestedPermissions
                ?.map { it.substringAfterLast('.') }
                ?.sorted()

            ApkMetadata(
                packageName = packageInfo.packageName,
                appName = appName?.takeIf { it.isNotBlank() && it != packageInfo.packageName },
                versionName = packageInfo.versionName?.takeIf { it.isNotBlank() },
                versionCode = versionCode.takeIf { it > 0 },
                minSdk = packageInfo.applicationInfo?.minSdkVersion,
                targetSdk = packageInfo.applicationInfo?.targetSdkVersion,
                permissions = permissions?.takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            null
        }
    }
}
