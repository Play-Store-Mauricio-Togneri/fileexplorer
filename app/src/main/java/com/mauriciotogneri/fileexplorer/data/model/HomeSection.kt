package com.mauriciotogneri.fileexplorer.data.model

import com.mauriciotogneri.fileexplorer.R

/**
 * A block of the home screen, in the order the user arranged it.
 *
 * Declaration order is the default arrangement, and also where a section added by a later release
 * lands for users who already stored one: [reconcile] appends whatever the stored order did not
 * mention rather than rebuilding it, so an update never rearranges a home screen set up by hand.
 */
enum class HomeSection(val titleResId: Int) {
    RECENT(R.string.section_recent),
    FAVORITES(R.string.section_favorites),
    LOCATIONS(R.string.section_locations),
    STORAGE(R.string.section_storage);

    companion object {
        /** What the home screen showed before the setting existed, and the fallback for every read. */
        val DEFAULT_ORDER: List<HomeSection> = entries.toList()

        /**
         * The stored [names] read back as sections: names no section answers to are dropped, and
         * every section they left out is appended in declaration order.
         *
         * Dropping rather than failing keeps a partially unreadable order costly only for the
         * sections it actually names. That covers a store written by a later release the user has
         * since rolled back from, a name this release renamed, and a duplicated entry.
         */
        fun reconcile(names: List<String>): List<HomeSection> {
            val stored = names.mapNotNull { name -> entries.find { it.name == name } }.distinct()

            return stored + DEFAULT_ORDER.filterNot { section -> section in stored }
        }
    }
}

/**
 * A copy of [this] with the section at [from] moved to [to].
 *
 * An index outside the list leaves the order untouched instead of throwing: a drag that runs past
 * the first or last row is how the gesture normally ends, not a caller error.
 */
fun List<HomeSection>.move(from: Int, to: Int): List<HomeSection> {
    if (from !in indices || to !in indices || from == to) {
        return this
    }

    return toMutableList().apply { add(to, removeAt(from)) }
}
