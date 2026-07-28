package com.mauriciotogneri.fileexplorer.data.util

import android.content.pm.PackageManager
import android.content.res.Resources

/**
 * Returns true when [e] indicates an APK archive whose icon could not be read. These are
 * expected, unactionable conditions (not bugs) and must not be reported to crash analytics:
 *  - [PackageManager.NameNotFoundException] — the framework could not open the archive's
 *    resource table: the file was deleted or its volume unmounted after it was parsed, or
 *    its `resources.arsc` is damaged or absent.
 *  - [Resources.NotFoundException] — the icon's resource id is declared in the archive but
 *    resolves to no loadable entry. Normal for a base APK split out of an app bundle, whose
 *    density-specific launcher icons live in a separate `split_config.*.apk`.
 *  - [NullPointerException] naming [android.content.pm.ApplicationInfo] — some ROMs
 *    dereference a null `ApplicationInfo` inside `PackageManager`'s own icon lookup for a
 *    package that is not installed (observed as `ApplicationPackageManager
 *    .loadUnbadgedItemIcon` reading `publicSourceDir`). Nothing the caller passes in
 *    prevents it; [ApkThumbnailFetcher] avoids that lookup entirely for this reason.
 *
 * The first two are matched by type: they are the only failures the framework raises for an
 * archive it cannot turn into resources, and no other call in the guarded block throws them.
 * [NullPointerException] is matched by message instead — as [isUndecodableImage] is, and
 * unlike every other helper in this package — because matching that type alone would be the
 * broadest net here by far and would silently swallow any genuine null-safety bug introduced
 * in the fetcher later. The message is ART's own wording for a null dereference and names the
 * declaring class of the field or method being read, so it holds across Android versions and
 * OEMs. A ROM that instead throws a [NullPointerException] carrying no message keeps being
 * reported, which is visible rather than dangerous.
 */
internal fun isUnreadableApk(e: Throwable): Boolean =
    e is PackageManager.NameNotFoundException ||
        e is Resources.NotFoundException ||
        (e is NullPointerException && e.message?.contains(APPLICATION_INFO_CLASS) == true)

private const val APPLICATION_INFO_CLASS = "android.content.pm.ApplicationInfo"
