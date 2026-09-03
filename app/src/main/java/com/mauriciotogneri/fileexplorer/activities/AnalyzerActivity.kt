package com.mauriciotogneri.fileexplorer.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.screens.analyzer.AnalyzerScreen
import com.mauriciotogneri.fileexplorer.ui.screens.analyzer.AnalyzerViewModel
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager

/**
 * Deliberately declares no `configChanges` in the manifest, unlike MainActivity and FolderActivity:
 * a scan runs in the ViewModel's scope, which a recreation keeps, so a rotation mid-scan resumes
 * against the same walk rather than needing the activity held together to survive.
 */
class AnalyzerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AnalyticsTracker.trackScreenAnalyzer()

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory())
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)

            FileExplorerTheme(themeMode = themeMode) {
                val analyzerViewModel: AnalyzerViewModel = viewModel(
                    factory = AnalyzerViewModel.Factory(this@AnalyzerActivity)
                )
                AnalyzerScreen(
                    viewModel = analyzerViewModel,
                    onCloseClick = { finish() }
                )
            }
        }
    }
}
