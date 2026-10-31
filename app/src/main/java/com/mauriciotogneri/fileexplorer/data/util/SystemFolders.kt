package com.mauriciotogneri.fileexplorer.data.util

import android.os.Build
import java.io.File

/**
 * A storage volume's root as this app reaches it: the primary volume for any user or work profile
 * (`/storage/emulated/<n>`), its `self/primary` alias, and a removable one (`/storage/<id>`). The
 * same path-shape idea as [com.mauriciotogneri.fileexplorer.ui.components.BreadcrumbPathParser].
 * The alternatives are tried in order, so `/storage/emulated/0` wins over a bare `/storage/emulated`.
 */
private val VOLUME_ROOT = Regex("/storage/(?:emulated/\\d+|self/primary|[^/]+)(?=/|$)")

/**
 * Whether [path] is a volume's `Android/data` or `Android/obb`, the two folders Android 11 (API 30)
 * withholds from every other app, All Files Access included. A delete can never remove them: their
 * listing comes back null, so they look empty, and their own removal then fails as "not empty" or
 * "busy" (some are mount points). The delete walk leaves them in place instead, and the user is told
 * why rather than being blamed for items they cannot see.
 *
 * Gated to API 30 because on Android 10 and below `requestLegacyExternalStorage` still lets this app
 * read and delete them, and they keep working there exactly as before.
 *
 * Matched by path rather than by comparing device IDs with the parent (`st_dev`), for three
 * reasons: it costs no syscall per directory, it runs as a JVM test with the SDK level passed in
 * (the [PdfViewerSupport.mode] pattern), and it also catches the folders Android hides without a
 * mount point — removable volumes, where the listing names entries that cannot be opened — which a
 * device-ID test would miss. The cost is knowing only these two folders: an alias path
 * (`/sdcard/...`) or an unknown OEM folder slips past, and falls back to the walk's handling of a
 * folder it could not list, which still tells the user the items are hidden rather than "in use".
 *
 * Exact match only, and case-sensitive: a descendant (`Android/data/com.foo`) is not matched — a
 * walk that started above the protected folder already knows it is inside one, and one that starts
 * at the descendant reports its real errno — and neither is a user's own folder of the same name
 * further down (`Download/Android/data`). The cheap name test runs first so the regex only ever sees
 * a folder called `data` or `obb` under one called `Android`.
 */
internal fun isProtectedSystemFolder(path: String, sdkInt: Int): Boolean {
    if (sdkInt < Build.VERSION_CODES.R) return false

    val file = File(path)
    val name = file.name

    if (name != "data" && name != "obb") return false

    val androidFolder = file.parentFile ?: return false

    if (androidFolder.name != "Android") return false

    val volume = androidFolder.parent ?: return false

    return VOLUME_ROOT.matches(volume)
}

/**
 * Whether a failure anywhere inside [protectedFolder] — one [isProtectedSystemFolder] matched — is
 * Android's doing by that fact alone.
 *
 * True for `Android/data`, whose contents Android 11+ never hands to another app. False for
 * `Android/obb`, which an app allowed to install packages (this one holds
 * `REQUEST_INSTALL_PACKAGES`) may read and write: a file it can see there that still will not go
 * failed for a real reason, so the walk treats it like any other visible failure. A folder inside
 * it the walk cannot list is still explained, by the walk's own unlisted-folder rule.
 */
internal fun explainsFailuresInside(protectedFolder: File): Boolean = protectedFolder.name != "obb"

/**
 * Roughly where a delete failed, for the analytics event. Only the label leaves the device, never
 * the path it was worked out from.
 */
enum class DeleteLocation(val analyticsLabel: String) {
    /** A volume's `Android/data`, or anything under it. */
    ANDROID_DATA("android_data"),

    /** A volume's `Android/obb`, or anything under it. */
    ANDROID_OBB("android_obb"),

    /** A volume's `Android` folder itself, or anything else under it. */
    ANDROID("android"),

    /** A volume's root. */
    VOLUME_ROOT("volume_root"),

    /** Anywhere else, including a path on no volume [isProtectedSystemFolder] knows. */
    OTHER("other")
}

/** Where [path] sits, as [DeleteLocation] buckets it. */
internal fun deleteLocationOf(path: String): DeleteLocation {
    val normalised = File(path).path
    val volume = VOLUME_ROOT.matchAt(normalised, 0) ?: return DeleteLocation.OTHER
    val rest = normalised.substring(volume.range.last + 1)

    return when {
        rest.isEmpty() -> DeleteLocation.VOLUME_ROOT
        rest.isUnder("/Android/data") -> DeleteLocation.ANDROID_DATA
        rest.isUnder("/Android/obb") -> DeleteLocation.ANDROID_OBB
        rest.isUnder("/Android") -> DeleteLocation.ANDROID
        else -> DeleteLocation.OTHER
    }
}

private fun String.isUnder(folder: String): Boolean = this == folder || startsWith("$folder/")
