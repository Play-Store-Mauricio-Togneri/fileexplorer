package com.mauriciotogneri.fileexplorer.data.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
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
                ErrorReporter.warning(e.scrubbed(), "extract_apk_thumbnail", FILE_TYPE)
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

        val bitmap = loadIconFromArchive(appInfo) ?: return null

        val buffer = Buffer()
        val compressed = try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
        } finally {
            bitmap.recycle()
        }

        // A failed compress leaves the buffer empty or truncated, and caching that commits a broken
        // thumbnail to disk which is then served on every later request until the file's
        // modification time changes — where before the disk cache it cost one bad load. The source
        // drawable's Config comes from the archive, so the copy in rasterize is the one place here
        // that could hand compress a bitmap it cannot encode.
        if (compressed && buffer.size > 0) {
            // A copy, because writing consumes the buffer and Coil still has to decode it.
            thumbnailCache.write(buffer.copy())
        }

        return SourceFetchResult(
            source = ImageSource(buffer, options.fileSystem),
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
     *
     * A bitmap rather than the Drawable, because the archive's resources are closed before this
     * returns (see the `finally` below) and a Drawable read out of them may not outlive them.
     *
     * Unlike the handles the other fetchers in this package release, the AssetManager closed here
     * is vended by the framework rather than constructed, which is why the two guards around the
     * close are there.
     */
    private fun loadIconFromArchive(appInfo: ApplicationInfo): Bitmap? {
        val iconRes = appInfo.icon.takeIf { it != 0 } ?: return null
        // An archive claiming the framework's own package name is refused rather than read.
        // `getResourcesForApplication` short-circuits that one name and hands back the process's
        // shared SystemUI resources instead of anything opened for the archive, so the icon id
        // would resolve against the system's resource table rather than the archive's — and the
        // close below would destroy an AssetManager the rest of the process is still using. The
        // name is parsed out of a file the user happens to be browsing, so it is not this app's
        // to trust; the caller falls back to the file-type icon, as it does for any archive whose
        // icon cannot be read.
        if (appInfo.packageName == SYSTEM_PACKAGE) return null

        val resources = options.context.packageManager.getResourcesForApplication(appInfo)

        return try {
            val drawable = ResourcesCompat.getDrawableForDensity(
                resources,
                iconRes,
                options.context.resources.displayMetrics.densityDpi,
                null
            ) ?: return null

            rasterize(drawable)
        } finally {
            // Released here rather than left to the finalizer. The AssetManager behind these
            // resources maps this archive, and closing it drops that manager's last strong
            // reference to the ApkAssets holding the mapping — the framework keeps only a weak one
            // — so the archive is unmapped at the next collection. Left open, one accumulates per
            // row of a folder of archives, and unmapping them overruns the ten seconds the
            // finalizer daemon is given per object whenever a delete or a media scan has the
            // filesystem busy at the same time: "ApkAssets.finalize() timed out", which is a
            // process kill. The capped fetcher dispatcher in AppImageLoader bounds how many are
            // mapped at once but not how many are waiting to be released, which is this.
            //
            // From P only. ApkAssets is what that crash names and it does not exist below P, so
            // there is nothing there this would buy. It would also cost: the framework caches one
            // Resources per archive path and rejects a cached entry by asking its AssetManager
            // whether it is still up to date, which from P is a field read that reports a closed
            // manager as stale, and below P a native call on a freed handle.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    resources.assets.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * The icon as a bitmap that holds nothing from the resources it was read out of, so the
     * archive can be closed the moment [loadIconFromArchive] returns.
     */
    private fun rasterize(drawable: Drawable): Bitmap = when (drawable) {
        is BitmapDrawable -> {
            val original = drawable.bitmap
            original.copy(original.config ?: Bitmap.Config.ARGB_8888, false)
        }
        else -> {
            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val file = data.toFileOrNull() ?: return null
            if (!file.exists() || !file.canRead()) {
                return null
            }
            if (!MimeTypeUtil.isApk(MimeTypeUtil.getMimeType(file))) {
                return null
            }
            return ApkThumbnailFetcher(file, options, imageLoader.diskCache)
        }
    }
}

// The one package name PackageManager resolves to something other than the archive asked for.
private const val SYSTEM_PACKAGE = "system"
private const val FILE_TYPE = ThumbnailFileType.APK
private const val MIME_TYPE = "image/png"
