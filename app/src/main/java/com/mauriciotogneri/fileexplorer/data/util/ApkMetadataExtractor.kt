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

            runCatching {
                packageInfo.applicationInfo?.sourceDir = file.absolutePath
                packageInfo.applicationInfo?.publicSourceDir = file.absolutePath
            }

            val appName = runCatching {
                packageInfo.applicationInfo?.loadLabel(packageManager)?.toString()
            }.getOrNull()

            val versionCode = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            }.getOrNull()

            val permissions = runCatching {
                packageInfo.requestedPermissions
                    ?.map { it.substringAfterLast('.') }
                    ?.sorted()
            }.getOrNull()

            ApkMetadata(
                packageName = runCatching { packageInfo.packageName }.getOrNull(),
                appName = appName?.takeIf { it.isNotBlank() && it != packageInfo.packageName },
                versionName = runCatching { packageInfo.versionName?.takeIf { it.isNotBlank() } }.getOrNull(),
                versionCode = versionCode?.takeIf { it > 0 },
                minSdk = runCatching { packageInfo.applicationInfo?.minSdkVersion }.getOrNull(),
                targetSdk = runCatching { packageInfo.applicationInfo?.targetSdkVersion }.getOrNull(),
                permissions = permissions?.takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            null
        }
    }
}
