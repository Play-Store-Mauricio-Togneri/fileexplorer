package com.mauriciotogneri.fileexplorer.data.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import java.io.File

class ApkThumbnailFetcher(
    private val file: File,
    private val options: Options,
    diskCache: DiskCache?
) : Fetcher {

    // The icon is loaded at the screen's density rather than the requested size (see
    // loadIconFromArchive), so one entry covers every size.
    private val thumbnailCache = ThumbnailDiskCache(diskCache, options, FILE_TYPE, file, variesWithSize = false)

    override suspend fun fetch(): FetchResult? {
        thumbnailCache.read(MIME_TYPE)?.let { return it }

        return try {
            extractApkIcon()
        } catch (e: Exception) {
            // An archive whose resources cannot be opened, or whose icon resource resolves to
            // nothing, is an expected, unactionable condition and not worth reporting.
            if (!isUnreadableApk(e)) {
                ErrorReporter.warning(e, "extract_apk_thumbnail", FILE_TYPE)
            }
            null
        }
    }

    private fun extractApkIcon(): FetchResult? {
        val packageManager = options.context.packageManager
        val packageInfo = packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_ACTIVITIES
        ) ?: return null

        val appInfo = packageInfo.applicationInfo ?: return null
        // The archive is not installed, so the framework filled in no paths for it: point the
        // ApplicationInfo at the file itself so its own resources can be opened.
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath

        val drawable = loadIconFromArchive(appInfo) ?: return null

        val bitmap = when (drawable) {
            is BitmapDrawable -> {
                val original = drawable.bitmap
                original.copy(original.config ?: Bitmap.Config.ARGB_8888, false)
            }
            else -> {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = createBitmap(width, height, Bitmap.Config.ARGB_8888)
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

        // A copy, because writing consumes the buffer and Coil still has to decode it.
        thumbnailCache.write(buffer.copy())

        return SourceResult(
            source = ImageSource(buffer, options.context),
            mimeType = MIME_TYPE,
            dataSource = DataSource.DISK
        )
    }

    /**
     * Reads the icon straight from the archive's own resources.
     *
     * [ApplicationInfo.loadIcon] would be the obvious call, but it routes through
     * `PackageManager.loadUnbadgedItemIcon`, which some ROMs cannot complete for a package
     * that is not installed and which throws there instead (see [isUnreadableApk]) — code the
     * caller cannot influence. Reading the archive's resources directly also skips the
     * framework's icon cache, keyed by package name and resource id, which can otherwise serve
     * the *installed* app's icon for an archive carrying the same package name.
     *
     * Only [ApplicationInfo.icon] is considered. `android:logo` is a wide banner asset rather
     * than a launcher icon, and would be cropped to a square thumbnail; when there is no icon
     * the caller falls back to the file-type icon, which reads better.
     */
    private fun loadIconFromArchive(appInfo: ApplicationInfo): Drawable? {
        val iconRes = appInfo.icon.takeIf { it != 0 } ?: return null
        val resources = options.context.packageManager.getResourcesForApplication(appInfo)

        return ResourcesCompat.getDrawableForDensity(
            resources,
            iconRes,
            options.context.resources.displayMetrics.densityDpi,
            null
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
            return ApkThumbnailFetcher(data, options, imageLoader.diskCache)
        }
    }
}

private const val FILE_TYPE = ThumbnailFileType.APK
private const val MIME_TYPE = "image/png"
