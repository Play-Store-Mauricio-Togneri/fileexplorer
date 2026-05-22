package com.mauriciotogneri.fileexplorer.data.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mauriciotogneri.fileexplorer.BuildConfig
import kotlin.coroutines.cancellation.CancellationException

object ErrorReporter {
    private const val TAG = "ErrorReporter"
    private const val KEY_SEVERITY = "severity"
    private const val KEY_OPERATION = "operation"
    private const val KEY_FILE_TYPE = "file_type"

    fun critical(e: Throwable, operation: String, fileType: String? = null) {
        report(e, "critical", operation, fileType)
    }

    fun error(e: Throwable, operation: String, fileType: String? = null) {
        report(e, "error", operation, fileType)
    }

    fun warning(e: Throwable, operation: String, fileType: String? = null) {
        report(e, "warning", operation, fileType)
    }

    private fun report(e: Throwable, severity: String, operation: String, fileType: String?) {
        if (e is CancellationException) return

        if (BuildConfig.DEBUG) {
            Log.e(TAG, "[$severity][$operation] ${e.message}", e)
        }

        FirebaseCrashlytics.getInstance().apply {
            setCustomKey(KEY_SEVERITY, severity)
            setCustomKey(KEY_OPERATION, operation)
            fileType?.let { setCustomKey(KEY_FILE_TYPE, it) }
            recordException(e)
        }
    }
}
