package com.mauriciotogneri.fileexplorer.ui.screens.permission

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.util.AndroidPermissionChecker

@Composable
fun PermissionScreen(
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionChecker = remember { AndroidPermissionChecker(context) }
    var hasNavigatedToSettings by remember { mutableStateOf(false) }
    var isFirstResume by remember { mutableStateOf(true) }

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
            }
        }
    }

    LaunchedEffect(Unit) {
        AnalyticsTracker.trackScreenPermission()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (isFirstResume) {
                isFirstResume = false
            } else {
                AnalyticsTracker.trackPermissionScreenResumed()
                if (hasNavigatedToSettings) {
                    hasNavigatedToSettings = false
                    if (!permissionChecker.hasStoragePermission()) {
                        AnalyticsTracker.trackPermissionReturnedWithoutGranting()
                    }
                }
            }
            if (permissionChecker.hasStoragePermission()) {
                onPermissionGranted()
            }
        }
    }

    PermissionScreenContent(
        onGrantClick = {
            val isAndroid11OrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            AnalyticsTracker.trackPermissionGrantButtonTapped(isAndroid11OrAbove)
            if (isAndroid11OrAbove) {
                hasNavigatedToSettings = true
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:${context.packageName}".toUri()
                }
                context.startActivity(intent)
            } else {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    )
}

@Composable
fun PermissionScreenContent(
    onGrantClick: () -> Unit
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
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
                    text = stringResource(R.string.permission_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(48.dp))

                Button(onClick = onGrantClick) {
                    Text(text = stringResource(R.string.permission_grant))
                }
            }
        }
    }
}
