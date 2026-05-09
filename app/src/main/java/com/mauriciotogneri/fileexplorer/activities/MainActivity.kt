package com.mauriciotogneri.fileexplorer.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mauriciotogneri.fileexplorer.ui.navigation.FileExplorerNavGraph
import com.mauriciotogneri.fileexplorer.ui.navigation.StartMode
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startMode = parseStartMode(intent)

        setContent {
            FileExplorerTheme {
                FileExplorerNavGraph(startMode = startMode)
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
