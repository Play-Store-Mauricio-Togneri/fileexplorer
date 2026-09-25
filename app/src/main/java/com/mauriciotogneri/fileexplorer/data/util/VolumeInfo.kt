package com.mauriciotogneri.fileexplorer.data.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.storage.StorageManager
import java.io.File

/**
 * What the framework knows about a mounted volume beyond its size: how it is attached, and what it
 * is called.
 *
 * Both flags are carried because neither answers on its own whether the volume can be detached. A
 * card the user formatted as internal storage is surfaced as an emulated volume stacked on a
 * removable one, so it is emulated *and* removable — [isEmulated] alone calls a card the device's
 * own storage. And the two flags are not opposites: a volume the framework calls neither emulated
 * nor removable is one this app has shown as a card for as long as it has read the path, so
 * [isRemovable] alone would take that card away.
 */
data class VolumeInfo(
    val isEmulated: Boolean,
    val isRemovable: Boolean,
    /**
     * Which kind of volume this is, as far as the framework's own name for it gives that away, and
     * null when it does not.
     *
     * Null is the answer for a volume carrying a label, because the framework describes such a
     * volume by its label alone and a label says nothing about the kind — and for a build whose
     * generic names could not be read. A caller that needs a name for a null has to pick one.
     */
    val genericKind: GenericVolumeKind? = null
)

/** A kind of volume the framework has a generic name of its own for. */
enum class GenericVolumeKind {
    SD_CARD,
    USB_DRIVE
}

/**
 * The [VolumeInfo] for the volume mounted at [rootPath], or null when the framework does not
 * recognise the path as a volume root or the lookup fails.
 *
 * The framework's [android.os.storage.StorageVolume.getDescription] is read for one thing only:
 * whether the volume is a card or a USB drive. There is no public API that reports a volume's disk
 * type, and the description is the nearest thing to one — for an unlabelled volume the framework
 * answers with its own "SD card" or "USB drive", which [genericKindOf] turns back into a kind. The
 * description itself is not kept: it is composed in the system's locale, and a labelled volume is
 * described by its label, neither of which is a name this app would show.
 *
 * Its own file, and its own function, for the reason [volumeStatsAt] is one: a storage lookup that
 * reaches for an `android.os` class, which JVM tests have to be able to answer for. The unit-test
 * `android.jar` cannot produce a [StorageManager], so a test going through this would see every
 * volume fall back to its unnamed, untyped shape before naming or typing had been exercised at all
 * — `AndroidStorageSource` takes it as a parameter so a test can answer for it instead.
 */
fun volumeInfoAt(context: Context, rootPath: String): VolumeInfo? =
    try {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        storageManager?.getStorageVolume(File(rootPath))?.let {
            VolumeInfo(
                isEmulated = it.isEmulated,
                isRemovable = it.isRemovable,
                genericKind = genericKindOf(it.getDescription(context))
            )
        }
    } catch (_: Exception) {
        null
    }

/**
 * Which of the framework's generic names [description] is, or null when it is not one of them.
 *
 * An unlabelled volume's description is composed in system_server out of `Resources.getSystem()`.
 * Reading the same two resources here produces the same two strings, so a description that matches
 * one identifies the kind the framework meant by it. A labelled volume matches neither, and its
 * kind is simply not knowable: the framework replaces the generic name with the label rather than
 * combining them.
 *
 * Looked up by name because these are `com.android.internal` resources, which no app can reference
 * by constant — the reason lint discourages [android.content.res.Resources.getIdentifier], and the
 * reason it is suppressed here rather than avoided. A build that has renamed them answers 0, and
 * the description is then taken for a label — what this app did with every description before.
 */
@SuppressLint("DiscouragedApi")
private fun genericKindOf(description: String?): GenericVolumeKind? {
    if (description.isNullOrBlank()) return null

    // Caught here rather than left to the caller's: a lookup that fails is only a name this app
    // could not translate, while the caller's catch answers null for the whole volume and would
    // cost it the framework's removability flags as well — the volume would be typed off its path
    // again, which is the guess reading the framework replaced.
    return try {
        val resources = Resources.getSystem()

        GENERIC_NAMES.entries.firstOrNull { (resourceName, _) ->
            val id = resources.getIdentifier(resourceName, "string", "android")
            id != 0 && resources.getString(id) == description
        }?.value
    } catch (_: Exception) {
        null
    }
}

private val GENERIC_NAMES = mapOf(
    "storage_sd_card" to GenericVolumeKind.SD_CARD,
    "storage_usb_drive" to GenericVolumeKind.USB_DRIVE
)
