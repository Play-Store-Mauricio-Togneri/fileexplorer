package com.mauriciotogneri.fileexplorer.activities

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.screens.iteminfo.ItemInfoScreen
import com.mauriciotogneri.fileexplorer.ui.screens.iteminfo.ItemInfoViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme

class ItemInfoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyEnterTransition()

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                val viewModel: ItemInfoViewModel = viewModel(
                    factory = ItemInfoViewModel.Factory(filePath)
                )
                ItemInfoScreen(
                    viewModel = viewModel,
                    onCloseClick = { finish() }
                )
            }
        }
    }

    private fun applyEnterTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_bottom, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_bottom, 0)
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.slide_out_bottom)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.slide_out_bottom)
        }
    }

    companion object {
        private const val EXTRA_FILE_PATH = "extra_file_path"

        fun createIntent(context: Context, filePath: String): Intent {
            return Intent(context, ItemInfoActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
            }
        }
    }
}
