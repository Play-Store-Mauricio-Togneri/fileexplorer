package com.mauriciotogneri.fileexplorer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarTitleStyle
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import dev.jeziellago.compose.markdowntext.MarkdownText

class LegalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val documentType = intent.getStringExtra(EXTRA_DOCUMENT_TYPE) ?: DOCUMENT_PRIVACY

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory())
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)

            FileExplorerTheme(themeMode = themeMode) {
                LegalScreen(
                    documentType = documentType,
                    onBackClick = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_DOCUMENT_TYPE = "document_type"
        const val DOCUMENT_PRIVACY = "privacy"
        const val DOCUMENT_TERMS = "terms"

        fun createIntent(context: Context, documentType: String): Intent {
            return Intent(context, LegalActivity::class.java).apply {
                putExtra(EXTRA_DOCUMENT_TYPE, documentType)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalScreen(
    documentType: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val title = when (documentType) {
        LegalActivity.DOCUMENT_PRIVACY -> stringResource(R.string.about_privacy_policy)
        LegalActivity.DOCUMENT_TERMS -> stringResource(R.string.about_terms)
        else -> ""
    }

    val resources = context.applicationContext.resources
    val content by produceState(initialValue = "", key1 = documentType) {
        val resourceId = when (documentType) {
            LegalActivity.DOCUMENT_PRIVACY -> R.raw.privacy
            LegalActivity.DOCUMENT_TERMS -> R.raw.terms
            else -> return@produceState
        }
        value = withContext(Dispatchers.IO) {
            resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = AppBarTitleStyle) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            if (content.isNotEmpty()) {
                MarkdownText(
                    markdown = content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    linkColor = MaterialTheme.colorScheme.primary,
                    enableSoftBreakAddsNewLine = true
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
