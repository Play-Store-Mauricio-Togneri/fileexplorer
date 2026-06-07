package com.mauriciotogneri.fileexplorer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.ui.screens.imageviewer.ImageViewerScreen
import com.mauriciotogneri.fileexplorer.ui.screens.imageviewer.ImageViewerViewModel
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager

/**
 * In-app fallback viewer for image files that no installed app could open. Launched by
 * [com.mauriciotogneri.fileexplorer.util.IntentUtil.openFile] via
 * [com.mauriciotogneri.fileexplorer.util.OpenFileResult.RequiresImageViewer].
 */
class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            finish()
            return
        }
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: DEFAULT_SOURCE

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory())
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)

            FileExplorerTheme(themeMode = themeMode) {
                val imageViewerViewModel: ImageViewerViewModel = viewModel(
                    factory = ImageViewerViewModel.Factory(
                        filePath,
                        source,
                        this@ImageViewerActivity.application
                    )
                )
                ImageViewerScreen(
                    viewModel = imageViewerViewModel,
                    onBackClick = { finish() },
                    onFinish = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_FILE_PATH = "extra_file_path"
        private const val EXTRA_SOURCE = "extra_source"
        private const val DEFAULT_SOURCE = "unknown"

        fun createIntent(context: Context, filePath: String, source: String): Intent {
            return Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_SOURCE, source)
            }
        }
    }
}
