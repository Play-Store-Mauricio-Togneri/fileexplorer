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
 * The kind of volume a [StorageDevice] sits on: the device's own storage, or one that can be
 * detached.
 *
 * A detachable volume is not divided further. No public API reports a volume's disk type, and the
 * only signal available — that a USB mass-storage device is attached — says nothing about which
 * volume it was mounted at, so a USB drive and an SD card cannot be told apart without guessing.
 * What each one is called comes from the framework instead, in [displayName].
 *
 * [analyticsName] is the value reported for the storage dimension, and is part of the analytics
 * contract — the names are stable regardless of what the enum entries are called.
 */
enum class StorageType(val analyticsName: String) {
    INTERNAL("internal"),
    SD_CARD("sd_card")
}
