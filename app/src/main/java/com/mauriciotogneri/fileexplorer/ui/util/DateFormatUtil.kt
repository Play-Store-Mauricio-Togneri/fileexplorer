package com.mauriciotogneri.fileexplorer.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.mauriciotogneri.fileexplorer.data.util.ShortDateFormatter

/**
 * A [ShortDateFormatter] for the locale the app is currently displaying, rebuilt when that changes.
 *
 * Meant to be held by a screen and handed to its rows: building one parses two date patterns, and a
 * LazyColumn creates and disposes rows on every scroll.
 */
@Composable
fun rememberShortDateFormatter(): ShortDateFormatter {
    val locale = LocalConfiguration.current.locales[0]

    return remember(locale) { ShortDateFormatter(locale) }
}
