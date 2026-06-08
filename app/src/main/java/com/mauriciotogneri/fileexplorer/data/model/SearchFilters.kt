package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

/**
 * Whether a search should surface files, folders, or both. Folders have no file type, so the
 * [SearchFileType] filter is only applied to files (see [SearchFilters.matchesType]).
 */
enum class SearchItemKind {
    FILES,
    FOLDERS,
    ANY
}

/**
 * High-level file categories the user can filter search results by. Each category maps onto the
 * existing [FileTypeInfo] flags. [OTHER] is the catch-all for anything not covered by the named
 * categories (APKs, archives, fonts, databases, …).
 */
enum class SearchFileType {
    IMAGES {
        override fun matches(info: FileTypeInfo) = info.isImage || info.isSvg
    },
    AUDIO {
        override fun matches(info: FileTypeInfo) = info.isAudio
    },
    VIDEOS {
        override fun matches(info: FileTypeInfo) = info.isVideo
    },
    DOCUMENTS {
        override fun matches(info: FileTypeInfo) =
            info.isPdf || info.isOfficeDocument || info.isText || info.isCsv ||
                info.isEpub || info.isVCard || info.isICalendar
    },
    OTHER {
        override fun matches(info: FileTypeInfo) =
            !IMAGES.matches(info) && !AUDIO.matches(info) &&
                !VIDEOS.matches(info) && !DOCUMENTS.matches(info)
    };

    abstract fun matches(info: FileTypeInfo): Boolean
}

/**
 * The set of filters applied to a search. An empty [selectedTypes] means "all types" (no type
 * restriction); [includeHidden] is seeded from the global show-hidden preference but overridden
 * locally for the duration of a search session.
 */
@Immutable
data class SearchFilters(
    val itemKind: SearchItemKind = SearchItemKind.FILES,
    val includeHidden: Boolean = false,
    val selectedTypes: Set<SearchFileType> = emptySet()
) {
    /**
     * True when [info] passes the type filter. An empty [selectedTypes] passes everything. Only
     * meaningful for files; callers must not apply it to folders, which have no type.
     */
    fun matchesType(info: FileTypeInfo): Boolean =
        selectedTypes.isEmpty() || selectedTypes.any { it.matches(info) }
}
