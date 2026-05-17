package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsTracker {
    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (analytics == null) {
            analytics = FirebaseAnalytics.getInstance(context)
        }
    }

    fun trackScreen(screenName: String) {
        analytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        })
    }

    fun trackEvent(eventName: String, params: Map<String, String>? = null) {
        val bundle = params?.let {
            Bundle().apply {
                it.forEach { (key, value) -> putString(key, value) }
            }
        }
        analytics?.logEvent(eventName, bundle)
    }

    fun setUserProperty(key: String, value: String) {
        analytics?.setUserProperty(key, value)
    }

    fun trackScreenSettings() {
        trackScreen("settings")
    }

    fun trackScreenFileList() {
        trackScreen("file_list")
    }

    fun trackFileOpened(extension: String, mimeType: String) {
        trackEvent(
            "file_opened", mapOf(
                "extension" to extension,
                "mime_type" to mimeType
            )
        )
    }

    fun trackThemeChanged(theme: String) {
        trackEvent("theme_changed", mapOf("theme" to theme))
    }
}
