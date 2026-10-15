package com.mauriciotogneri.fileexplorer.activities

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.screens.mediaviewer.MediaViewerScreen
import com.mauriciotogneri.fileexplorer.ui.screens.mediaviewer.MediaViewerViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager

/**
 * In-app fallback player for audio and video files that no installed app could open. Launched by
 * [com.mauriciotogneri.fileexplorer.util.IntentUtil.openFile] via
 * [com.mauriciotogneri.fileexplorer.util.OpenFileResult.RequiresMediaViewer].
 */
class MediaViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            finish()
            return
        }
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: DEFAULT_SOURCE

        // The hardware volume keys adjust what is playing here, not the ringer.
        volumeControlStream = AudioManager.STREAM_MUSIC

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory())
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)

            FileExplorerTheme(themeMode = themeMode) {
                val mediaViewerViewModel: MediaViewerViewModel = viewModel(
                    factory = MediaViewerViewModel.Factory(
                        filePath,
                        source,
                        this@MediaViewerActivity.application
                    )
                )
                MediaViewerScreen(
                    viewModel = mediaViewerViewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_FILE_PATH = "extra_file_path"
        private const val EXTRA_SOURCE = "extra_source"
        private const val DEFAULT_SOURCE = "unknown"

        fun createIntent(context: Context, filePath: String, source: String): Intent {
            return Intent(context, MediaViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_SOURCE, source)
            }
        }
    }
}
