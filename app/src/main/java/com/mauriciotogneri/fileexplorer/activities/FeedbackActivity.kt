package com.mauriciotogneri.fileexplorer.activities

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone

class FeedbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory())
            val themeMode by mainViewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)

            FileExplorerTheme(themeMode = themeMode) {
                FeedbackScreen(
                    onBackClick = { finish() },
                    onSubmitSuccess = { finish() }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}

class FeedbackViewModel(private val context: Context) : ViewModel() {
    private val _feedbackText = MutableStateFlow("")
    val feedbackText: StateFlow<String> = _feedbackText.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val client = OkHttpClient()

    fun updateFeedbackText(text: String) {
        if (text.length <= MAX_CHARACTERS) {
            _feedbackText.value = text
        }
    }

    fun submitFeedback(onSuccess: () -> Unit, onError: () -> Unit) {
        if (_feedbackText.value.isBlank() || _isSubmitting.value) return

        _isSubmitting.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonObject = buildPayload()
                val body = jsonObject.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(FEEDBACK_URL)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    launch(Dispatchers.Main) {
                        _isSubmitting.value = false
                        if (response.isSuccessful) {
                            onSuccess()
                        } else {
                            onError()
                        }
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    _isSubmitting.value = false
                    onError()
                }
            }
        }
    }

    private fun buildPayload(): JSONObject {
        val displayMetrics = context.resources.displayMetrics
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val statFs = StatFs(Environment.getDataDirectory().path)
        val storageAvailableMb = statFs.availableBytes / (1024 * 1024)
        val storageTotalMb = statFs.totalBytes / (1024 * 1024)

        val ramAvailableMb = memoryInfo.availMem / (1024 * 1024)
        val ramTotalMb = memoryInfo.totalMem / (1024 * 1024)

        val isDarkMode =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        return JSONObject().apply {
            put("message", _feedbackText.value.trim())
            put("device", JSONObject().apply {
                put("model", Build.MODEL)
                put("manufacturer", Build.MANUFACTURER)
                put("board", Build.BOARD)
                put("resolution", "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
                put("name", Build.DEVICE)
                put("brand", Build.BRAND)
                put("density", displayMetrics.densityDpi)
                put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
                put("kernel", System.getProperty("os.version") ?: "unknown")
                put("emulator", isEmulator())
                put("darkMode", isDarkMode)
            })
            put("system", JSONObject().apply {
                put("version", Build.VERSION.RELEASE)
                put("api", Build.VERSION.SDK_INT)
                put("language", Locale.getDefault().toLanguageTag())
                put("timezone", TimeZone.getDefault().id)
            })
            put("ram", JSONObject().apply {
                put("available", ramAvailableMb)
                put("total", ramTotalMb)
            })
            put("storage", JSONObject().apply {
                put("available", storageAvailableMb)
                put("total", storageTotalMb)
            })
            put("app", JSONObject().apply {
                put("versionName", packageInfo.versionName ?: "unknown")
                put("versionCode", versionCode)
            })
        }
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") ||
                Build.DEVICE.startsWith("generic") ||
                Build.PRODUCT == "sdk" ||
                Build.PRODUCT == "sdk_gphone64_arm64" ||
                Build.PRODUCT.startsWith("sdk_google")
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FeedbackViewModel(context.applicationContext) as T
        }
    }

    companion object {
        const val MAX_CHARACTERS = 1000
        private const val FEEDBACK_URL =
            "https://script.google.com/macros/s/AKfycbwxsFwopuJpd4FI3gvWFaKkgK_9PTxIb-2_cOduwvFbsSbixssYcefhMbrK29NOPUWt/exec"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackScreen(
    onBackClick: () -> Unit,
    onSubmitSuccess: () -> Unit,
    viewModel: FeedbackViewModel = viewModel(factory = FeedbackViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val feedbackText by viewModel.feedbackText.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var hasSubmitBeenPressed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val errorMessage = stringResource(R.string.feedback_error)
    val successMessage = stringResource(R.string.feedback_success)

    val hasContent = feedbackText.isNotBlank()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val handleBack = {
        if (hasContent && !hasSubmitBeenPressed) {
            showDiscardDialog = true
        } else {
            onBackClick()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawer_feedback)) },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
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
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = feedbackText,
                onValueChange = viewModel::updateFeedbackText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .focusRequester(focusRequester),
                enabled = !isSubmitting,
                placeholder = { Text(stringResource(R.string.feedback_hint)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text("${feedbackText.length} / ${FeedbackViewModel.MAX_CHARACTERS}")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    hasSubmitBeenPressed = true
                    viewModel.submitFeedback(
                        onSuccess = {
                            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                            onSubmitSuccess()
                        },
                        onError = {
                            hasSubmitBeenPressed = false
                            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                enabled = hasContent && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.feedback_submit))
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.feedback_discard_title)) },
            text = { Text(stringResource(R.string.feedback_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBackClick()
                }) {
                    Text(stringResource(R.string.feedback_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.feedback_keep_editing))
                }
            }
        )
    }
}
