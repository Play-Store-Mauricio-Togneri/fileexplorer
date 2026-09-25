package com.mauriciotogneri.fileexplorer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerResultsHolder
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory.AnalyzerCategoryScreen
import com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory.AnalyzerCategoryViewModel
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager

/**
 * The files behind one slice of the storage analyzer's chart.
 *
 * Reads them from [AnalyzerResultsHolder], which holds them only for as long as the results they
 * belong to can be reached — and reads them through
 * [AnalyzerCategoryViewModel.Factory][com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory.AnalyzerCategoryViewModel.Factory],
 * which runs only when no view model survived. Finding nothing there means the process was killed
 * and the task restored, or the analyzer released its lists before this screen was composed; either
 * way there is no scan to report on, and this screen closes onto the analyzer, which has restarted
 * at its volume list — the same thing [FolderActivity] and [ItemInfoActivity] do when the file they
 * were opened for is not named.
 *
 * Declares no `configChanges`: a recreation keeps the ViewModel, so the pages already read stay
 * read, and the holder is not consulted again.
 */
class AnalyzerCategoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val category = intent.getStringExtra(EXTRA_CATEGORY)
            ?.let { name -> AnalyzerCategory.entries.firstOrNull { it.name == name } }
            ?: run {
                finish()
                return
            }

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory())
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)

            FileExplorerTheme(themeMode = themeMode) {
                val categoryViewModel: AnalyzerCategoryViewModel = viewModel(
                    factory = AnalyzerCategoryViewModel.Factory(application, category)
                )

                // Asked of the view model rather than of the holder, because only the view model
                // knows whether the holder was read at all: a recreation keeps the entries it was
                // built with, and the holder behind it may since have been released. Asking the
                // holder here would close a listing that is still perfectly able to draw itself.
                if (categoryViewModel.hasResults) {
                    // Reported from here rather than from onCreate, so that the branch below — a
                    // screen that closes before it draws — is not counted as a screen the user saw.
                    LaunchedEffect(Unit) { AnalyticsTracker.trackScreenAnalyzerCategory(category) }

                    AnalyzerCategoryScreen(
                        category = category,
                        viewModel = categoryViewModel,
                        onCloseClick = { finish() }
                    )
                } else {
                    LaunchedEffect(Unit) { finish() }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_CATEGORY = "extra_category"

        fun createIntent(context: Context, category: AnalyzerCategory): Intent {
            return Intent(context, AnalyzerCategoryActivity::class.java).apply {
                putExtra(EXTRA_CATEGORY, category.name)
            }
        }
    }
}
