package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import com.mauriciotogneri.fileexplorer.BuildConfig
import com.mauriciotogneri.fileexplorer.util.DeviceInfo

/**
 * Whether Crashlytics and Analytics may collect on this device: never on debug builds, emulators or
 * Firebase Test Lab devices (Play Console's pre-launch report). A release build's manifest says
 * "on", so on a pre-launch device this check is the only thing keeping test runs out of the
 * production project. [ErrorReporter.init] and [AnalyticsTracker.init] share this one copy so the
 * two cannot drift apart.
 *
 * [isDebugBuild] is a parameter only so a test can answer for a release build.
 */
internal fun isTelemetryCollectionEnabled(
    context: Context,
    isDebugBuild: Boolean = BuildConfig.DEBUG
): Boolean = !(isDebugBuild || DeviceInfo.isEmulator() || DeviceInfo.isTestLab(context))
