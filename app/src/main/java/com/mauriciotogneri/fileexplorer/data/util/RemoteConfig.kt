package com.mauriciotogneri.fileexplorer.data.util

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.mauriciotogneri.fileexplorer.BuildConfig

/**
 * Switches the owner can flip from the Firebase console without shipping a release.
 *
 * Every switch reads as its in-app default until a fetch has delivered a value, and whenever Remote
 * Config cannot be reached — no Firebase in unit tests, a failed init, a blocked or offline fetch —
 * so an app that never hears from the console behaves as if the switch did not exist. Like the
 * telemetry objects, nothing here may throw into the caller.
 *
 * Reading a value can block on the first load of the stored config, so read off the main thread.
 */
object RemoteConfig {
    private const val KEY_IN_APP_REVIEW = "in_app_review"
    private const val DEFAULT_IN_APP_REVIEW = true

    // The SDK's own default. Debug builds refetch after minutes instead, so a switch can be tested by
    // flipping it in the console and restarting the app. Not on every launch: the instrumentation
    // suite starts one process per test, and ~700 fetches in a row can get the installation
    // throttled, which the SDK then honours for hours across restarts.
    private const val RELEASE_FETCH_INTERVAL_SECONDS = 12L * 60 * 60
    private const val DEBUG_FETCH_INTERVAL_SECONDS = 5L * 60

    /**
     * Fetches the console's values and applies them as soon as they arrive. The fetch interval
     * persists across launches, and debug and release share an applicationId, so it is set
     * explicitly every init; the fetch waits for it so it never runs under the previous one.
     */
    fun init() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(
                    if (BuildConfig.DEBUG) DEBUG_FETCH_INTERVAL_SECONDS else RELEASE_FETCH_INTERVAL_SECONDS
                )
                .build()
            remoteConfig.setConfigSettingsAsync(settings)
                .continueWithTask { remoteConfig.fetchAndActivate() }
        } catch (_: Exception) {
        }
    }

    /** Whether the review prompt may ask for a Play Store rating. */
    fun isInAppReviewEnabled(): Boolean = getBoolean(KEY_IN_APP_REVIEW, DEFAULT_IN_APP_REVIEW)

    // A key with no fetched value reads as false from the SDK rather than as missing, so the source
    // decides whether the console said anything at all.
    private fun getBoolean(key: String, default: Boolean): Boolean {
        return try {
            val value = FirebaseRemoteConfig.getInstance().getValue(key)

            if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) default else value.asBoolean()
        } catch (_: Exception) {
            default
        }
    }
}
