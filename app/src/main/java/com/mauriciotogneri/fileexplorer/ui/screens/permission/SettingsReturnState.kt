package com.mauriciotogneri.fileexplorer.ui.screens.permission

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.util.SettingsPage

/**
 * The permission screen's trip to Settings: which page the grant button opened, and whether the
 * user came back from it without access, which puts a hint above the button saying what to turn on.
 *
 * Held with `rememberSaveable` through [Saver] because the trip often outlives the screen: Android
 * may kill the process while Settings is in front, and a theme, language or font-size change
 * rebuilds the activity. With plain `remember` both cases forgot the trip, so the return was
 * neither counted nor answered. A cold start still begins fresh.
 *
 * Once shown, the hint stays until access is granted: the screen is hidden while the user retries,
 * and clearing it on the tap would only make it blink off and on with every failed retry.
 */
@Stable
internal class SettingsReturnState(
    awayOn: SettingsPage? = null,
    hintFrom: SettingsPage? = null
) {
    /** The page the grant button opened that the user has not come back from yet. */
    var awayOn by mutableStateOf(awayOn)
        private set

    /** The page the user last came back from without access; non-null shows the hint. */
    var hintFrom by mutableStateOf(hintFrom)
        private set

    val hintShown: Boolean get() = hintFrom != null

    /** [page] is `null` when no Settings page opened, which leaves nothing to come back from. */
    fun onSettingsOpened(page: SettingsPage?) {
        if (page != null) awayOn = page
    }

    /**
     * Ends a trip to Settings, if one is under way, and reports how it ended.
     *
     * @param recreated whether this is the first resume of a rebuilt screen. The button cannot be
     * tapped before the screen first resumes, so a trip seen then was saved before the rebuild.
     */
    fun onResumed(hasPermission: Boolean, recreated: Boolean, sdkInt: Int) {
        val page = awayOn ?: return
        awayOn = null
        if (hasPermission) {
            AnalyticsTracker.trackPermissionGrantedOnReturn(page, hintShown, recreated)
        } else {
            AnalyticsTracker.trackPermissionReturnedWithoutGranting(page, hintShown, recreated)
            if (hintFor(page, sdkInt) != null) hintFrom = page
        }
    }

    @StringRes
    fun hint(sdkInt: Int): Int? = hintFrom?.let { hintFor(it, sdkInt) }

    companion object {
        // Saved by name, as FolderScreen saves its sort mode, not as a string resource, whose IDs
        // change between builds. A name that no longer exists restores as no trip and no hint.
        val Saver = listSaver<SettingsReturnState, String>(
            save = { listOf(it.awayOn?.name.orEmpty(), it.hintFrom?.name.orEmpty()) },
            restore = { SettingsReturnState(pageNamed(it[0]), pageNamed(it[1])) }
        )

        private fun pageNamed(name: String): SettingsPage? =
            SettingsPage.entries.firstOrNull { it.name == name }
    }
}

@StringRes
private fun hintFor(page: SettingsPage, sdkInt: Int): Int? = when {
    // Below Android 11 the only page is App info after a permanent denial, and the denied message
    // the screen already shows is that help.
    sdkInt < Build.VERSION_CODES.R -> null
    // App info has no single switch to point at.
    page == SettingsPage.APP_DETAILS -> R.string.permission_settings_unavailable
    // The all-apps list leads to the same switch once the user picks this app.
    else -> R.string.permission_all_files_hint
}
