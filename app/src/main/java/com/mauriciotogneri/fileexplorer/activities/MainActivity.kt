package com.mauriciotogneri.fileexplorer.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.ui.navigation.FileExplorerNavGraph
import com.mauriciotogneri.fileexplorer.ui.navigation.StartMode
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.util.AndroidPermissionChecker

class MainActivity : ComponentActivity() {
    private val permissionChecker by lazy { AndroidPermissionChecker(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startMode = parseStartMode(intent)

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory())
            var hasPermission by remember { mutableStateOf(permissionChecker.hasStoragePermission()) }
            val lifecycleOwner = LocalLifecycleOwner.current
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)

            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    hasPermission = permissionChecker.hasStoragePermission()
                }
            }

            FileExplorerTheme(themeMode = themeMode) {
                FileExplorerNavGraph(
                    startMode = startMode,
                    hasPermission = hasPermission
                )
            }
        }
    }

    private fun parseStartMode(intent: Intent?): StartMode {
        return when (intent?.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.path?.let { path ->
                    StartMode.OpenPath(path = path)
                } ?: StartMode.Normal
            }
            else -> StartMode.Normal
        }
    }
}
