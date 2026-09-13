package com.mauriciotogneri.fileexplorer.data.repository

import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
 *
 * Observable rather than merely readable, because the listing can now delete the files it lists:
 * the chart is drawn from a scan that is no longer true the moment one goes, and the analyzer
 * screen is behind the listing rather than gone, so it watches [categories] instead of re-reading
 * on resume.
 */
object AnalyzerResultsHolder {
    // Written by the scan's collector and by the listing's deletes, and read from both
    // activities' main threads.
    private val _categories = MutableStateFlow<Map<AnalyzerCategory, CategoryFiles>?>(null)
    val categories: StateFlow<Map<AnalyzerCategory, CategoryFiles>?> = _categories.asStateFlow()

    fun store(categories: Map<AnalyzerCategory, CategoryFiles>) {
        _categories.value = categories
    }

    /** Null when no results are held at all — never merely because [category] has no files. */
    fun filesFor(category: AnalyzerCategory): CategoryFiles? = _categories.value?.get(category)

    fun clear() {
        _categories.value = null
    }

    /**
     * Drops [paths] from [category] and takes their bytes off its total, so that what the chart
     * says about the category matches what the listing behind it still holds.
     *
     * The total covers every file of that type on the volume, while [CategoryFiles.entries] stops
     * at the per-type cap — but only a listed file can be deleted, so a path that reaches here is
     * always one of the entries, and subtracting its size is the same arithmetic either way.
     */
    fun remove(category: AnalyzerCategory, paths: Set<String>) {
        if (paths.isEmpty()) return

        _categories.update { held ->
            val files = held?.get(category) ?: return@update held
            val (removed, kept) = files.entries.partition { it.path in paths }
            if (removed.isEmpty()) return@update held

            held + (category to files.copy(
                totalBytes = (files.totalBytes - removed.sumOf { it.size }).coerceAtLeast(0L),
                entries = kept
            ))
        }
    }
}
