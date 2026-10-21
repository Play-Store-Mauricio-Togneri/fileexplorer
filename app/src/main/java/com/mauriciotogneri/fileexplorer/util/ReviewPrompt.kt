package com.mauriciotogneri.fileexplorer.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.mauriciotogneri.fileexplorer.data.repository.ReviewPromptRepository
import com.mauriciotogneri.fileexplorer.data.repository.reviewPromptDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStoreReviewPromptSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * App-wide entry point for asking engaged users to rate the app on the Play Store. The rules are
 * in [ReviewPromptGate] and [com.mauriciotogneri.fileexplorer.data.util.ReviewEligibility].
 *
 * Every clean success of an action that counts — copy, move, rename, delete, compress, extract, or
 * opening a file — calls [onActionSucceeded]. Failures reach [onActionFailed] through
 * AnalyticsTracker's failure events, so a new failure path that reports one of those is already
 * covered; one that does not must call [onActionFailed] itself.
 *
 * Like the telemetry objects, nothing here may surface as a failure of the operation that called
 * it, and every call is a no-op until [init] runs — which unit tests never do.
 */
object ReviewPrompt {
    private val neverPending: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    @Volatile
    private var gate: ReviewPromptGate? = null

    /** Whether a success has made an ask due. Read by [com.mauriciotogneri.fileexplorer.ui.components.ReviewPromptEffect]. */
    val pending: StateFlow<Boolean>
        get() = gate?.pending ?: neverPending

    fun init(application: Application) {
        if (gate != null) return

        try {
            val created = ReviewPromptGate(
                repository = ReviewPromptRepository(
                    DataStoreReviewPromptSource(application.reviewPromptDataStore)
                ),
                launcher = PlayReviewLauncher(application),
                installedAt = { withContext(Dispatchers.IO) { firstInstallTime(application) } },
                scope = CoroutineScope(
                    SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }
                )
            )
            application.registerActivityLifecycleCallbacks(DaysUsedCallbacks(created))
            gate = created
        } catch (_: Exception) {
        }
    }

    fun onActionSucceeded() = withGate { onActionSucceeded() }

    fun onActionFailed() = withGate { onActionFailed() }

    fun disarm() = withGate { disarm() }

    suspend fun ask(activity: Activity) {
        try {
            gate?.ask(activity)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private inline fun withGate(block: ReviewPromptGate.() -> Unit) {
        try {
            gate?.block()
        } catch (_: Exception) {
        }
    }

    private fun firstInstallTime(context: Context): Long? {
        return try {
            val packageManager = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.firstInstallTime
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Counts a day as used whenever any screen comes to the front. Every screen is its own Activity,
     * so this sees all of them, the way ErrorReporter.trackForegroundScreen does.
     */
    private class DaysUsedCallbacks(
        private val gate: ReviewPromptGate
    ) : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            try {
                gate.onScreenResumed()
            } catch (_: Exception) {
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
