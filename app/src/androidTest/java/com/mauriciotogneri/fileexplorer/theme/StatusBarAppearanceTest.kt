package com.mauriciotogneri.fileexplorer.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `FileExplorerTheme` asks the window for dark status bar icons in a light theme and light ones in a
 * dark theme — `isAppearanceLightStatusBars = !darkTheme`.
 *
 * Nothing asserted that. No test in either source set named `isAppearanceLightStatusBars`,
 * `WindowCompat` or the status bar at all, so dropping the `!` — which leaves every light-mode user
 * with white icons on a near-white bar, invisible — would have shipped green.
 *
 * The flag is window state, not composition state, so it is read back off the Activity's own window
 * through the same compat controller production writes through: on API 30+ that reads the platform
 * controller's appearance, and below it the decor view's flags, in both cases the value production
 * set rather than one this test computed. `Theme.kt` writes it from a `SideEffect`, which runs after
 * the composition applies, so every read here goes through `runOnIdle`.
 */
@RunWith(AndroidJUnit4::class)
class StatusBarAppearanceTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Whether the window is currently asking for a *light* status bar, which is the platform's way of
     * saying the icons drawn on it are dark.
     */
    private fun statusBarIconsAreDark(): Boolean = composeTestRule.runOnIdle {
        val window = composeTestRule.activity.window
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
    }

    @Test
    fun statusBarIcons_invertWithTheTheme() {
        var mode by mutableStateOf(ThemeMode.LIGHT)

        composeTestRule.setContent {
            FileExplorerTheme(themeMode = mode) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        assertTrue(
            "A light theme needs dark status bar icons, or they disappear into the light bar",
            statusBarIconsAreDark()
        )

        mode = ThemeMode.DARK
        assertFalse(
            "A dark theme needs light status bar icons, or they disappear into the dark bar",
            statusBarIconsAreDark()
        )

        // Switched back rather than left in dark mode: the activity may well start out with the flag
        // the first assertion wants, so only driving it in both directions proves the theme is what
        // writes it. A flag set once, or a SideEffect keyed on something that never changes, fails
        // one of the three.
        mode = ThemeMode.LIGHT
        assertTrue(
            "Returning to a light theme must restore dark status bar icons",
            statusBarIconsAreDark()
        )
    }
}
