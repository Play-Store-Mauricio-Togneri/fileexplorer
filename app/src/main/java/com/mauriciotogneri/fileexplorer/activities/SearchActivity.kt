package com.mauriciotogneri.fileexplorer.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mauriciotogneri.fileexplorer.ui.screens.search.SearchScreen
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme

class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FileExplorerTheme {
                SearchScreen(onBackClick = { finish() })
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
