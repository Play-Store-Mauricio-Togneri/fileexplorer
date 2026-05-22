package com.mauriciotogneri.fileexplorer.activities

import android.content.Context
import android.content.Intent
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import android.os.Bundle
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarTitleStyle
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager

class OtherAppsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory())
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)

            FileExplorerTheme(themeMode = themeMode) {
                OtherAppsScreen(onBackClick = { finish() })
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, OtherAppsActivity::class.java)
        }
    }
}

private data class OtherApp(
    val name: String,
    val iconRes: Int,
    val playStoreUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtherAppsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current

    val apps = remember {
        listOf(
        OtherApp(
            name = "Tension Tunnel",
            iconRes = R.drawable.ic_tension_tunnel,
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.atomicinstinct.tensiontunnel"
        ),
        OtherApp(
            name = "Hextrategic",
            iconRes = R.drawable.ic_hextrategic,
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.atomicinstinct.hextrategic"
        )
    )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_other_apps), style = AppBarTitleStyle) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
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
        ) {
            apps.forEach { app ->
                AppRow(
                    app = app,
                    onClick = { openPlayStore(context, app.playStoreUrl) }
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: OtherApp,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = app.iconRes),
            contentDescription = app.name,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = app.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openPlayStore(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    } catch (e: Exception) {
        ErrorReporter.error(e, "open_play_store")
        Toast.makeText(context, R.string.other_apps_open_error, Toast.LENGTH_SHORT).show()
    }
}
