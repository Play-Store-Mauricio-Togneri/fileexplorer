package com.mauriciotogneri.fileexplorer.ui.screens.permission

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.util.AndroidPermissionChecker
import com.mauriciotogneri.fileexplorer.util.PermissionChecker
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.SettingsPage

@Composable
fun PermissionScreen(
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val permissionChecker = remember(context) { AndroidPermissionChecker(context) }
    PermissionScreen(onPermissionGranted, permissionChecker)
}

// Internal seam keeps lifecycle permission checks testable without changing device-wide grants.
@Composable
internal fun PermissionScreen(
    onPermissionGranted: () -> Unit,
    permissionChecker: PermissionChecker
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsReturn = rememberSaveable(saver = SettingsReturnState.Saver) { SettingsReturnState() }
    var isFirstResume by remember { mutableStateOf(true) }
    // Below Android 11, once the runtime dialog is permanently denied it returns at once with no UI,
    // so the button has to lead to the app's Settings page instead.
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            AnalyticsTracker.trackPermissionDialogGranted()
            onPermissionGranted()
        } else {
            AnalyticsTracker.trackPermissionDialogDenied()
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            } ?: true
            if (!shouldShowRationale) {
                AnalyticsTracker.trackPermissionPermanentlyDenied()
                permanentlyDenied = true
            }
        }
    }

    LaunchedEffect(Unit) {
        AnalyticsTracker.trackScreenPermission()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val recreated = isFirstResume
            if (isFirstResume) {
                isFirstResume = false
            } else {
                AnalyticsTracker.trackPermissionScreenResumed()
            }
            val hasPermission = permissionChecker.hasStoragePermission()
            // The first resume is checked too: a trip saved before the screen was rebuilt, by
            // process death or a configuration change while Settings was in front, ends there.
            settingsReturn.onResumed(hasPermission, recreated, Build.VERSION.SDK_INT)
            if (hasPermission) {
                onPermissionGranted()
            }
        }
    }

    PermissionScreenContent(
        onGrantClick = {
            val isAndroid11OrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            AnalyticsTracker.trackPermissionGrantButtonTapped(isAndroid11OrAbove, settingsReturn.hintShown)
            if (isAndroid11OrAbove) {
                settingsReturn.onSettingsOpened(IntentUtil.openAllFilesAccessSettings(context))
            } else if (permanentlyDenied) {
                settingsReturn.onSettingsOpened(
                    SettingsPage.APP_DETAILS.takeIf { IntentUtil.openAppDetailsSettings(context) }
                )
            } else {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        },
        permanentlyDenied = permanentlyDenied,
        settingsHint = settingsReturn.hint(Build.VERSION.SDK_INT)
    )
}

@Composable
fun PermissionScreenContent(
    onGrantClick: () -> Unit,
    permanentlyDenied: Boolean = false,
    @StringRes settingsHint: Int? = null
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            // Scrolls so the button stays reachable in landscape and at large font sizes, where the
            // hint can push the content past the window.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(144.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(
                        if (permanentlyDenied) R.string.permission_denied_message else R.string.permission_message
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (settingsHint != null) {
                    Spacer(modifier = Modifier.height(24.dp))

                    SettingsHint(settingsHint)

                    Spacer(modifier = Modifier.height(32.dp))
                } else {
                    Spacer(modifier = Modifier.height(48.dp))
                }

                Button(onClick = onGrantClick) {
                    Text(
                        text = stringResource(
                            if (permanentlyDenied) R.string.apk_permission_settings else R.string.permission_grant
                        )
                    )
                }
            }
        }
    }
}

/**
 * What to do in Settings, shown after the user came back from there without access. Boxed, with an
 * icon and full-strength text, so it doesn't read as more of the message the user already passed
 * over. It is announced when it appears, since it only ever appears on a return.
 */
@Composable
private fun SettingsHint(@StringRes text: Int) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
