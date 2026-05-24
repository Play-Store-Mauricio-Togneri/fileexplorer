package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.mauriciotogneri.fileexplorer.BuildConfig

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
        if (BuildConfig.DEBUG) {
            val paramsStr = params?.entries?.joinToString { "${it.key}=${it.value}" } ?: ""
            Log.d("Analytics", "Event: $eventName $paramsStr")
        }
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

    fun trackBottomSheetOpened(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_opened", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackFileOpened(extension: String, mimeType: String, source: String) {
        trackEvent(
            "file_opened", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    // ---------- Bottom Sheet Actions ---------- \\

    fun trackBottomSheetSelect(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_select", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetOpenWith(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_open_with", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetOpenFolder(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_open_folder", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetShare(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_share", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetMoveTo(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_move_to", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetCopyTo(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_copy_to", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetRename(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_rename", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetCompress(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_compress", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetUncompress(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_uncompress", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetRemoveFromRecents(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_remove_from_recents", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetDelete(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_delete", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetInfo(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_info", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    fun trackBottomSheetDismissed(extension: String, mimeType: String, source: String) {
        trackEvent(
            "bottom_sheet_dismissed", mapOf(
                "extension" to extension,
                "mime_type" to mimeType,
                "source" to source
            )
        )
    }

    // ---------- Folder Screen Events ---------- \\

    fun trackFolderSwipedRight() {
        trackEvent("folder_swiped_right")
    }

    fun trackFolderSwipedLeft() {
        trackEvent("folder_swiped_left")
    }

    fun trackFolderSwipeDeleteTapped() {
        trackEvent("folder_swipe_delete_tapped")
    }

    fun trackFolderSwipeRenameTapped() {
        trackEvent("folder_swipe_rename_tapped")
    }

    fun trackFolderContextMenuOpened() {
        trackEvent("folder_context_menu_opened")
    }

    fun trackFolderContextMenuSelectAll() {
        trackEvent("folder_context_menu_select_all")
    }

    fun trackFolderContextMenuSortBy() {
        trackEvent("folder_context_menu_sort_by")
    }

    fun trackFolderContextMenuHideHiddenItems() {
        trackEvent("folder_context_menu_hide_hidden_items")
    }

    fun trackFolderContextMenuShowHiddenItems() {
        trackEvent("folder_context_menu_show_hidden_items")
    }

    fun trackFolderContextMenuNewFolder() {
        trackEvent("folder_context_menu_new_folder")
    }

    fun trackFolderLongPressedToSelect() {
        trackEvent("folder_long_pressed_to_select")
    }

    fun trackFolderToolbarSelectAll() {
        trackEvent("folder_toolbar_select_all")
    }

    fun trackFolderToolbarUnselectAll() {
        trackEvent("folder_toolbar_unselect_all")
    }

    fun trackFolderSortBySelected(sortMode: String) {
        trackEvent("folder_sort_by_selected", mapOf("sort_mode" to sortMode))
    }

    fun trackFolderBottomBarShare() {
        trackEvent("folder_bottom_bar_share")
    }

    fun trackFolderBottomBarMoveTo() {
        trackEvent("folder_bottom_bar_move_to")
    }

    fun trackFolderBottomBarCopyTo() {
        trackEvent("folder_bottom_bar_copy_to")
    }

    fun trackFolderBottomBarRename() {
        trackEvent("folder_bottom_bar_rename")
    }

    fun trackFolderBottomBarCompress() {
        trackEvent("folder_bottom_bar_compress")
    }

    fun trackFolderBottomBarUncompress() {
        trackEvent("folder_bottom_bar_uncompress")
    }

    fun trackFolderBottomBarDelete() {
        trackEvent("folder_bottom_bar_delete")
    }

    fun trackFolderBreadcrumbTapped() {
        trackEvent("folder_breadcrumb_tapped")
    }

    fun trackFolderTappedToOpen() {
        trackEvent("folder_tapped_to_open")
    }

    fun trackFolderBackButtonTapped() {
        trackEvent("folder_back_button_tapped")
    }

    // ---------- Settings Events ---------- \\

    fun trackSettingsRecentFilesTracking(enabled: Boolean) {
        trackEvent("settings_recent_files_tracking", mapOf("enabled" to enabled.toString()))
    }

    fun trackSettingsRecentFilesClear() {
        trackEvent("settings_recent_files_clear")
    }

    fun trackSettingsLocationsDialogOpened() {
        trackEvent("settings_locations_dialog_opened")
    }

    fun trackSettingsLocationsChanged(locations: Set<String>) {
        trackEvent(
            "settings_locations_changed",
            mapOf("locations" to locations.sorted().joinToString(","))
        )
    }

    fun trackSettingsThemeDialogOpened() {
        trackEvent("settings_theme_dialog_opened")
    }

    fun trackSettingsTheme(theme: String) {
        trackEvent("settings_theme", mapOf("theme" to theme))
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

    fun trackHomeDrawerSettingsTapped() {
        trackEvent("home_drawer_settings_tapped")
    }

    fun trackHomeDrawerFeedbackTapped() {
        trackEvent("home_drawer_feedback_tapped")
    }

    fun trackHomeDrawerAboutTapped() {
        trackEvent("home_drawer_about_tapped")
    }

    // ---------- About Events ---------- \\

    fun trackAboutOtherAppsTapped() {
        trackEvent("about_other_apps_tapped")
    }

    fun trackAboutPrivacyPolicyTapped() {
        trackEvent("about_privacy_policy_tapped")
    }

    fun trackAboutTermsTapped() {
        trackEvent("about_terms_tapped")
    }

    fun trackAboutAppVersionTapped() {
        trackEvent("about_app_version_tapped")
    }

    // ---------- Feedback Events ---------- \\

    fun trackFeedbackTypingStarted() {
        trackEvent("feedback_typing_started")
    }

    fun trackFeedbackSubmitSuccess() {
        trackEvent("feedback_submit_success")
    }

    fun trackFeedbackSubmitError() {
        trackEvent("feedback_submit_error")
    }

    fun trackFeedbackCancelWithText() {
        trackEvent("feedback_cancel_with_text")
    }

    fun trackFeedbackDiscardDialogChoice(choice: String) {
        trackEvent("feedback_discard_dialog_choice", mapOf("choice" to choice))
    }

    fun trackFeedbackCloseWithoutSubmit() {
        trackEvent("feedback_close_without_submit")
    }

    // ---------- Legal Events ---------- \\

    fun trackLegalScrollReachedEnd(documentType: String) {
        trackEvent("legal_scroll_reached_end", mapOf("document_type" to documentType))
    }

    fun trackLegalBackBeforeScrollEnd(documentType: String, scrollPercentage: Int) {
        trackEvent(
            "legal_back_before_scroll_end", mapOf(
                "document_type" to documentType,
                "scroll_percentage" to scrollPercentage.toString()
            )
        )
    }

    // ---------- Other Apps Events ---------- \\

    fun trackOtherAppsTensionTunnelTapped() {
        trackEvent("other_apps_tension_tunnel_tapped")
    }

    fun trackOtherAppsHextrategicTapped() {
        trackEvent("other_apps_hextrategic_tapped")
    }

    fun trackOtherAppsOpenError(appName: String) {
        trackEvent("other_apps_open_error", mapOf("app_name" to appName))
    }

    fun trackOtherAppsBackWithoutTap() {
        trackEvent("other_apps_back_without_tap")
    }

    // ---------- Permission Events ---------- \\

    fun trackPermissionGrantButtonTapped(isAndroid11OrAbove: Boolean) {
        trackEvent(
            "permission_grant_button_tapped",
            mapOf("is_android_11_or_above" to isAndroid11OrAbove.toString())
        )
    }

    fun trackPermissionReturnedWithoutGranting() {
        trackEvent("permission_returned_without_granting")
    }

    fun trackPermissionDialogDenied() {
        trackEvent("permission_dialog_denied")
    }

    fun trackPermissionDialogGranted() {
        trackEvent("permission_dialog_granted")
    }

    fun trackPermissionScreenResumed() {
        trackEvent("permission_screen_resumed")
    }

    fun trackPermissionPermanentlyDenied() {
        trackEvent("permission_permanently_denied")
    }

    // ---------- Search Events ---------- \\

    fun trackSearchTypingStarted() {
        trackEvent("search_typing_started")
    }

    fun trackSearchCloseWithoutTyping() {
        trackEvent("search_close_without_typing")
    }

    fun trackSearchClearInputTapped() {
        trackEvent("search_clear_input_tapped")
    }

    // ---------- Item Info Events ---------- \\

    fun trackItemInfoCopyToClipboard() {
        trackEvent("item_info_copy_to_clipboard")
    }

    fun trackItemInfoOpenMaps() {
        trackEvent("item_info_open_maps")
    }

    // ---------- Destination Picker Events ---------- \\

    fun trackDestinationPickerShown(action: String) {
        trackEvent("destination_picker_shown", mapOf("action" to action))
    }

    fun trackDestinationPickerClosed() {
        trackEvent("destination_picker_closed")
    }

    fun trackDestinationPickerConfirmed(action: String) {
        trackEvent("destination_picker_confirmed", mapOf("action" to action))
    }

    fun trackDestinationPickerOperationFinished(action: String, success: Boolean) {
        trackEvent(
            "destination_picker_operation_finished",
            mapOf("action" to action, "success" to success.toString())
        )
    }

    fun trackDestinationPickerNewFolderTapped() {
        trackEvent("destination_picker_new_folder_tapped")
    }

    fun trackDestinationPickerBreadcrumbClicked() {
        trackEvent("destination_picker_breadcrumb_clicked")
    }

    fun trackDestinationPickerFolderNavigated() {
        trackEvent("destination_picker_folder_navigated")
    }

    fun trackDestinationPickerStorageSelected() {
        trackEvent("destination_picker_storage_selected")
    }

    fun trackDestinationPickerNavigatedUp() {
        trackEvent("destination_picker_navigated_up")
    }

    fun trackDestinationPickerFolderCreated() {
        trackEvent("destination_picker_folder_created")
    }

    fun trackDestinationPickerFolderCreationCancelled() {
        trackEvent("destination_picker_folder_creation_cancelled")
    }

    // ---------- Dialog Events ---------- \\

    fun trackCreateFolderConfirmed() {
        trackEvent("create_folder_confirmed")
    }

    fun trackCreateFolderCancelled() {
        trackEvent("create_folder_cancelled")
    }

    fun trackRenameConfirmed() {
        trackEvent("rename_confirmed")
    }

    fun trackRenameCancelled() {
        trackEvent("rename_cancelled")
    }

    fun trackCompressConfirmed() {
        trackEvent("compress_confirmed")
    }

    fun trackCompressCancelled() {
        trackEvent("compress_cancelled")
    }

    fun trackUncompressConfirmed() {
        trackEvent("uncompress_confirmed")
    }

    fun trackUncompressCancelled() {
        trackEvent("uncompress_cancelled")
    }

    fun trackPasswordUncompressConfirmed() {
        trackEvent("password_uncompress_confirmed")
    }

    fun trackPasswordUncompressCancelled() {
        trackEvent("password_uncompress_cancelled")
    }

    fun trackPasswordVisibilityToggled(visible: Boolean) {
        trackEvent("password_visibility_toggled", mapOf("visible" to visible.toString()))
    }

    fun trackDeleteConfirmed() {
        trackEvent("delete_confirmed")
    }

    fun trackDeleteCancelled() {
        trackEvent("delete_cancelled")
    }

    // ---------- Operation Completions ---------- \\

    fun trackRenameCompleted(extension: String, mimeType: String) {
        trackEvent(
            "rename_completed", mapOf(
                "extension" to extension,
                "mime_type" to mimeType
            )
        )
    }

    fun trackDeleteCompleted(itemCount: Int, source: String) {
        trackEvent(
            "delete_completed", mapOf(
                "item_count" to itemCount.toString(),
                "source" to source
            )
        )
    }

    fun trackCompressCompleted(itemCount: Int) {
        trackEvent("compress_completed", mapOf("item_count" to itemCount.toString()))
    }

    fun trackUncompressCompleted(extension: String) {
        trackEvent("uncompress_completed", mapOf("extension" to extension))
    }

    // ---------- Operation Failures ---------- \\

    fun trackOperationFailed(operation: String, errorType: String) {
        trackEvent(
            "operation_failed", mapOf(
                "operation" to operation,
                "error_type" to errorType
            )
        )
    }

    // ---------- Recent Files ---------- \\

    fun trackRecentFileRemoved() {
        trackEvent("recent_file_removed")
    }

    fun trackThemeDialogCancelled() {
        trackEvent("theme_dialog_cancelled")
    }

    fun trackLocationsDialogConfirmed() {
        trackEvent("locations_dialog_confirmed")
    }

    fun trackLocationsDialogCancelled() {
        trackEvent("locations_dialog_cancelled")
    }
}
