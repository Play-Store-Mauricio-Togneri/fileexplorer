package com.mauriciotogneri.fileexplorer.data.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mauriciotogneri.fileexplorer.BuildConfig
import com.mauriciotogneri.fileexplorer.util.DeviceInfo
import kotlin.coroutines.cancellation.CancellationException

object ErrorReporter {
    private const val TAG = "ErrorReporter"
    private const val KEY_SEVERITY = "severity"
    private const val KEY_OPERATION = "operation"
    private const val KEY_FILE_TYPE = "file_type"
    private const val KEY_SCREEN = "screen"
    private const val KEY_HEAP_USED_MB = "heap_used_mb"
    private const val KEY_HEAP_MAX_MB = "heap_max_mb"
    private const val BYTES_PER_MB = 1024L * 1024L

    /**
     * Suppresses all crash reporting — fatal and non-fatal alike — on debug builds and emulators so
     * dev and test runs don't pollute production. Debug and release ship the same applicationId, so
     * without this every failure path an instrumentation test drives on purpose lands in the same
     * Crashlytics app as real user crashes, indistinguishable from them.
     *
     * The debug build also carries `firebase_crashlytics_collection_enabled=false` in its manifest,
     * which is what covers the window before this runs; this call is what additionally catches a
     * release build running on an emulator. The flag persists across launches, so set it explicitly
     * every init — mirroring [AnalyticsTracker.init].
     */
    fun init() {
        val collectionEnabled = !(BuildConfig.DEBUG || DeviceInfo.isEmulator())
        withCrashlytics { setCrashlyticsCollectionEnabled(collectionEnabled) }
    }

    /**
     * Keeps a [KEY_SCREEN] key pointed at the foreground Activity for the life of the process.
     *
     * Custom keys ride along with *fatal* crashes too, unlike [report], and an OutOfMemoryError
     * names only the allocation that lost the race for the last free bytes — never what exhausted
     * the heap. This key is what makes such a report attributable to a screen.
     *
     * Driven by resume rather than by screen entry: the app runs one Activity per screen, so
     * returning from a child screen never re-runs the screen's entry code, and a key written on
     * entry alone would keep naming the screen the user already left.
     */
    fun trackForegroundScreen(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                withCrashlytics { setCustomKey(KEY_SCREEN, activity::class.java.simpleName) }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /**
     * Records how many items a screen is holding in memory (directory entries, parsed lines), for
     * the same reason as [trackForegroundScreen].
     */
    fun setCount(key: String, value: Int) {
        withCrashlytics { setCustomKey(key, value) }
    }

    /**
     * Snapshots Java heap usage so a later OutOfMemoryError report shows how close to the limit the
     * app already was at this point. Cheap: [Runtime] reads no more than a few counters.
     */
    fun recordHeap() {
        val runtime = Runtime.getRuntime()
        val usedMb = ((runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB).toInt()
        val maxMb = (runtime.maxMemory() / BYTES_PER_MB).toInt()
        withCrashlytics {
            setCustomKey(KEY_HEAP_USED_MB, usedMb)
            setCustomKey(KEY_HEAP_MAX_MB, maxMb)
        }
    }

    /**
     * Custom keys are fire-and-forget diagnostics written from the middle of working code paths —
     * a load that already produced its result, a screen that already resumed. An unavailable
     * reporter (no Firebase in unit tests, a failed init in production) must never surface as a
     * failure of the operation being diagnosed, so nothing it raises is allowed out. [init] is
     * held to the same rule: a reporter that cannot be reached sends nothing to suppress, and
     * `Application.onCreate` is the last place that should crash over it.
     */
    private fun withCrashlytics(block: FirebaseCrashlytics.() -> Unit) {
        try {
            FirebaseCrashlytics.getInstance().block()
        } catch (_: Throwable) {
        }
    }

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
