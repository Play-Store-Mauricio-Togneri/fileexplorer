package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsTracker {
    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (analytics == null) {
            analytics = FirebaseAnalytics.getInstance(context)
        }
    }

    private fun trackScreen(screenName: String) {
        analytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        })
    }

    private fun trackEvent(eventName: String, params: Map<String, String>? = null) {
        val bundle = params?.let {
            Bundle().apply {
                it.forEach { (key, value) -> putString(key, value) }
            }
        }
        analytics?.logEvent(eventName, bundle)
    }

    // ---------- Properties ---------- \\

    fun setUserProperty(key: String, value: String) {
        analytics?.setUserProperty(key, value)
    }

    fun setUserProperties(context: Context) {
        setSystemTheme(context)
        setHasSdCard()
    }

    private fun setSystemTheme(context: Context) {
        val nightModeFlags =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val theme = when (nightModeFlags) {
            Configuration.UI_MODE_NIGHT_YES -> "dark"
            Configuration.UI_MODE_NIGHT_NO -> "light"
            else -> "unknown"
        }
        setUserProperty("system_theme", theme)
    }

    private fun setHasSdCard() {
        val hasExternalStorage = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        setUserProperty("has_sd_card", hasExternalStorage.toString())
    }

    // ---------- Screens ---------- \\

    fun trackScreenHome() {
        trackScreen("home")
    }

    fun trackScreenFolder() {
        trackScreen("folder")
    }

    fun trackScreenSearch() {
        trackScreen("search")
    }

    fun trackScreenItemInfo() {
        trackScreen("item_info")
    }

    fun trackScreenSettings() {
        trackScreen("settings")
    }

    fun trackScreenAbout() {
        trackScreen("about")
    }

    fun trackScreenFeedback() {
        trackScreen("feedback")
    }

    fun trackScreenLegal() {
        trackScreen("legal")
    }

    fun trackScreenOtherApps() {
        trackScreen("other_apps")
    }

    fun trackScreenPermission() {
        trackScreen("permission")
    }

    // ---------- Events ---------- \\

    // TODO: Add source parameter (recent, folder, etc)
    fun trackFileOpened(extension: String, mimeType: String) {
        trackEvent(
            "file_opened", mapOf(
                "extension" to extension,
                "mime_type" to mimeType
            )
        )
    }

    // TODO: Add tracking for: open with, open folder, share, remove from recents, delete, info (with source parameter)

    fun trackThemeChanged(theme: String) {
        trackEvent("theme_changed", mapOf("theme" to theme))
    }

    // ---------- Home Events ---------- \\

    fun trackHomeDrawerOpened() {
        trackEvent("home_drawer_opened")
    }

    fun trackHomeSearchContainerTapped() {
        trackEvent("home_search_container_tapped")
    }

    fun trackHomeSearchIconTapped() {
        trackEvent("home_search_icon_tapped")
    }

    fun trackHomeRecentFileContextMenuOpened() {
        trackEvent("home_recent_file_context_menu_opened")
    }

    fun trackHomeLocationCardOpened(location: String) {
        trackEvent("home_location_card_opened", mapOf("location" to location))
    }

    fun trackHomeStorageCardOpened(storage: String) {
        trackEvent("home_storage_card_opened", mapOf("storage" to storage))
    }
}
