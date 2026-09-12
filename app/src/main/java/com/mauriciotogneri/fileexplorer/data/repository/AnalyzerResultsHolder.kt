package com.mauriciotogneri.fileexplorer.data.repository

import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry

/** What one analyzer category holds: the figure its chart row shows, and the files behind it. */
@Immutable
data class CategoryFiles(
    /** The category's share of the volume, exactly as the chart row states it. */
    val totalBytes: Long,
    /** Biggest first, capped at the per-type limit [AnalyzerRepository] retains. */
    val entries: List<AnalyzerFileEntry>
)

/**
 * Where a completed scan leaves its file lists for the category listing to pick up.
 *
 * A process-level object rather than an argument: the listing is a separate activity with its own
 * `ViewModelStore`, so it cannot reach [AnalyzerViewModel][com.mauriciotogneri.fileexplorer.ui.screens.analyzer.AnalyzerViewModel],
 * and tens of thousands of paths are orders of magnitude past what an `Intent` can carry.
 *
 * [store] always writes an entry for every category that has files, empty list included, so a null
 * from [filesFor] means one thing only: no scan's results are held. That is the state the listing
 * finds when the process was killed and the task restored, and it is why an empty category and a
 * missing one do not have to be told apart by counting.
 *
 * The results are the largest thing the app keeps in memory, so [clear] is called the moment they
 * can no longer be read: when a scan starts, when one is cancelled or abandoned, and when the
 * analyzer is finished with.
 */
object AnalyzerResultsHolder {
    // Written from the scan's collector and read from another activity's main thread.
    @Volatile
    private var categories: Map<AnalyzerCategory, CategoryFiles>? = null

    fun store(categories: Map<AnalyzerCategory, CategoryFiles>) {
        this.categories = categories
    }

    /** Null when no results are held at all — never merely because [category] has no files. */
    fun filesFor(category: AnalyzerCategory): CategoryFiles? = categories?.get(category)

    fun clear() {
        categories = null
    }
}
