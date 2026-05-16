package com.mauriciotogneri.fileexplorer.data.util

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import java.io.File

class ApkThumbnailFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        return try {
            extractApkIcon()
        } catch (e: Exception) {
            ErrorReporter.warning(e, "extract_apk_thumbnail", "apk")
            null
        }
    }

    private fun extractApkIcon(): FetchResult? {
        val packageManager = options.context.packageManager
        val packageInfo = packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_ACTIVITIES
        ) ?: return null

        packageInfo.applicationInfo?.let { appInfo ->
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
        } ?: return null

        val drawable = packageInfo.applicationInfo?.loadIcon(packageManager) ?: return null

        val bitmap = when (drawable) {
            is BitmapDrawable -> {
                val original = drawable.bitmap
                original.copy(original.config ?: Bitmap.Config.ARGB_8888, false)
            }
            else -> {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        }

        val buffer = Buffer()
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
        } finally {
            bitmap.recycle()
        }

        return SourceResult(
            source = ImageSource(buffer, options.context),
            mimeType = "image/png",
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.exists() || !data.canRead()) {
                return null
            }
            if (!MimeTypeUtil.isApk(MimeTypeUtil.getMimeType(data))) {
                return null
            }
            return ApkThumbnailFetcher(data, options)
        }
    }
}
