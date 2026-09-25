package com.mauriciotogneri.fileexplorer.data.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.ui.graphics.vector.ImageVector
import com.mauriciotogneri.fileexplorer.R

/**
 * A slice of a volume's used space, as the storage analyzer reports it.
 *
 * The first five wrap [SearchFileType] rather than restating it, so that what counts as a document
 * here is by construction what the search filters call a document. [SYSTEM] has no [SearchFileType]
 * because no file is ever classified into it: it is what is left of the volume's used space once
 * everything the walk could see has been added up.
 *
 * Declaration order is display order, and is deliberately stable rather than sorted by size — the
 * tone each category is drawn in comes from its position, so a re-scan must not repaint the chart.
 */
enum class AnalyzerCategory(
    val fileType: SearchFileType?,
    val icon: ImageVector,
    @param:StringRes val labelResId: Int
) {
    IMAGES(SearchFileType.IMAGES, Icons.Outlined.Image, R.string.location_images),
    VIDEOS(SearchFileType.VIDEOS, Icons.Outlined.PlayCircle, R.string.location_videos),
    AUDIO(SearchFileType.AUDIO, Icons.Outlined.MusicNote, R.string.location_audio),
    DOCUMENTS(SearchFileType.DOCUMENTS, Icons.Outlined.Description, R.string.location_documents),
    OTHER(SearchFileType.OTHER, Icons.Outlined.Category, R.string.search_filter_type_other),

    /**
     * Everything the analyzer cannot see but the volume still counts as used: installed apps and
     * their private data, `Android/data` and `Android/obb` (closed even to All Files Access since
     * Android 11), and the filesystem's own overhead.
     *
     * Without it the six figures would not add up to the used space the chart is drawn against, and
     * every category's share would be measured against a denominator no row accounted for.
     */
    SYSTEM(null, Icons.Outlined.Memory, R.string.analyzer_category_system)
}
