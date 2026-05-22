package com.mauriciotogneri.fileexplorer.activities

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.BuildConfig
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.components.BadgeDot
import com.mauriciotogneri.fileexplorer.ui.screens.about.AboutViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarTitleStyle
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val viewModel: AboutViewModel = viewModel(factory = AboutViewModel.Factory(context))
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)
            val showOtherAppsBadge by viewModel.showOtherAppsBadge.collectAsState()

            FileExplorerTheme(themeMode = themeMode) {
                AboutScreen(
                    showOtherAppsBadge = showOtherAppsBadge,
                    onOtherAppsBadgeDismiss = viewModel::dismissOtherAppsBadge,
                    onBackClick = { finish() }
                )
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(
    showOtherAppsBadge: Boolean,
    onOtherAppsBadgeDismiss: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawer_about), style = AppBarTitleStyle) },
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
            AboutRow(
                icon = Icons.Outlined.Apps,
                title = stringResource(R.string.about_other_apps),
                showBadge = showOtherAppsBadge,
                showChevron = true,
                onClick = {
                    onOtherAppsBadgeDismiss()
                    openOtherApps(context)
                }
            )
            AboutRow(
                icon = Icons.Outlined.Shield,
                title = stringResource(R.string.about_privacy_policy),
                showChevron = true,
                onClick = { openLegalDocument(context, LegalActivity.DOCUMENT_PRIVACY) }
            )
            AboutRow(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.about_terms),
                showChevron = true,
                onClick = { openLegalDocument(context, LegalActivity.DOCUMENT_TERMS) }
            )
            AboutRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.about_version),
                value = BuildConfig.VERSION_NAME,
                showChevron = false,
                onClick = null
            )
        }
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    showBadge: Boolean = false,
    showChevron: Boolean,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeDot(showBadge = showBadge) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun openOtherApps(context: Context) {
    context.startActivity(OtherAppsActivity.createIntent(context))
}

private fun openLegalDocument(context: Context, documentType: String) {
    context.startActivity(LegalActivity.createIntent(context, documentType))
}
