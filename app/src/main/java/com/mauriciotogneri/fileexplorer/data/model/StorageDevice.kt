package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter

@Immutable
data class StorageDevice(
    val path: String,
    val displayName: String,
    val totalBytes: Long,
    val availableBytes: Long,
    val type: StorageType
) {
    val formattedTotal: String get() = FileSizeFormatter.format(totalBytes)
    val formattedAvailable: String get() = FileSizeFormatter.format(availableBytes)
    val analyticsType: String get() = type.analyticsName

    companion object {
        /**
         * [names] with a 1-based number appended to each name that appears more than once, numbered
         * in the order given; a name that appears once is returned untouched.
         *
         * Numbering keys on the collision that actually happened rather than on the kind of volume:
         * a USB drive sitting next to an SD card needs no number to be told apart, while two USB
         * drives do.
         *
         * That the result is itself free of duplicates rests on where [names] come from — a closed
         * set of this app's own strings, no shipped translation of which ends in a number. Given a
         * name that already read "SD Card 1", numbering a pair of "SD Card"s would hand that label
         * to two volumes at once. A caller that ever passes free-form names, a volume label among
         * them, needs a disambiguator that cannot collide rather than a count.
         */
        fun numberDuplicates(names: List<String>): List<String> {
            val totals = names.groupingBy { it }.eachCount()
            val assigned = mutableMapOf<String, Int>()

            return names.map { name ->
                if (totals.getValue(name) == 1) {
                    name
                } else {
                    val number = assigned.getOrDefault(name, 0) + 1
                    assigned[name] = number
                    "$name $number"
                }
            }
        }
    }
}

/**
 * The kind of volume a [StorageDevice] sits on: the device's own storage, or one of the two kinds
 * that can be detached.
 *
 * A detachable volume is a [USB_DRIVE] only where the framework's own generic name for it says so,
 * and an [SD_CARD] otherwise — which is what every removable volume was taken for before that name
 * was read. No public API reports a volume's disk type, so a volume the framework names with a
 * label of its own says nothing about its kind and keeps the one it has always had.
 *
 * Every volume is described from this one value: [StorageDevice.displayName] and the icon drawn
 * beside it both follow it, so a row cannot name one kind and draw another.
 *
 * [analyticsName] is the value reported for the storage dimension, and is part of the analytics
 * contract — the names are stable regardless of what the enum entries are called.
 */
enum class StorageType(val analyticsName: String) {
    INTERNAL("internal"),
    SD_CARD("sd_card"),
    USB_DRIVE("usb_drive")
}
