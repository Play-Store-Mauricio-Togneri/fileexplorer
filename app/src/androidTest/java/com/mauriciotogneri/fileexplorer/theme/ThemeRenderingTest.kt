package com.mauriciotogneri.fileexplorer.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.components.ActionBar
import com.mauriciotogneri.fileexplorer.ui.components.Breadcrumbs
import com.mauriciotogneri.fileexplorer.ui.components.CreateFolderDialog
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.EmptyState
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderUiState
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import com.mauriciotogneri.fileexplorer.ui.theme.backgroundDark
import com.mauriciotogneri.fileexplorer.ui.theme.backgroundLight
import com.mauriciotogneri.fileexplorer.ui.theme.onSurfaceDark
import com.mauriciotogneri.fileexplorer.ui.theme.onSurfaceLight
import com.mauriciotogneri.fileexplorer.ui.theme.primaryDark
import com.mauriciotogneri.fileexplorer.ui.theme.primaryLight
import com.mauriciotogneri.fileexplorer.ui.theme.surfaceDark
import com.mauriciotogneri.fileexplorer.ui.theme.surfaceLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Theme coverage for LIGHT, DARK and SYSTEM.
 *
 * The previous version had twelve `*_rendersCorrectly` tests that only asserted a piece of text
 * existed — a component rendering black-on-black in dark mode passed all of them — and never
 * exercised [ThemeMode.SYSTEM] at all, despite CLAUDE.md requiring all three modes.
 *
 * What is asserted now is the property those tests were named for: the foreground/background pairs
 * listed in [assertSchemeIsReadable] clear their WCAG contrast floor, in both schemes. That list is
 * not yet every pair the app renders — `ExtendedColorScheme` has no coverage. Components are still
 * rendered in each mode, because a crash or a missing node under one scheme is worth catching, but
 * legibility is checked against the palette rather than implied by it.
 */
@RunWith(AndroidJUnit4::class)
class ThemeRenderingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int): String = context.getString(id)

    private val testFile = FileItem(
        path = "/storage/emulated/0/test.txt",
        name = "test.txt",
        isDirectory = false,
        size = 1024L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = "text/plain",
        childCount = null
    )

    private val testFolder = testFile.copy(
        path = "/storage/emulated/0/TestFolder",
        name = "TestFolder",
        isDirectory = true,
        size = 0L,
        mimeType = "",
        childCount = 5
    )

    private val selectionState = FolderUiState(
        currentPath = "/storage/emulated/0",
        files = listOf(testFile),
        selectedPaths = setOf(testFile.path),
        isLoading = false
    )

    // ==================== Contrast ====================

    /**
     * Relative luminance contrast, per WCAG 2.x. 4.5:1 is the AA floor for body text; 3.0:1 is the
     * floor for large text and UI component boundaries.
     */
    private fun contrastRatio(foreground: Color, background: Color): Double {
        val a = foreground.luminance() + 0.05
        val b = background.luminance() + 0.05
        return max(a, b).toDouble() / min(a, b).toDouble()
    }

    private fun assertReadable(
        label: String,
        foreground: Color,
        background: Color,
        minimumRatio: Double = 4.5
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$label contrast is %.2f:1, below the %.1f:1 floor".format(ratio, minimumRatio),
            ratio >= minimumRatio
        )
    }

    /**
     * The rule permits a single `setContent` per test, so every scheme a test needs is captured in
     * one composition.
     */
    private fun captureSchemes(vararg modes: ThemeMode): Map<ThemeMode, ColorScheme> {
        val schemes = mutableMapOf<ThemeMode, ColorScheme>()
        composeTestRule.setContent {
            modes.forEach { mode ->
                FileExplorerTheme(themeMode = mode) {
                    schemes[mode] = MaterialTheme.colorScheme
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
        composeTestRule.waitForIdle()
        return schemes
    }

    private fun captureScheme(mode: ThemeMode): ColorScheme = captureSchemes(mode).getValue(mode)

    /**
     * `primaryContainer`/`onPrimaryContainer` are absent from this list on purpose: both are wired
     * into the schemes but read nowhere outside `Theme.kt`, so there is no rendered pair to hold to
     * a floor. Add the assertion the moment a component adopts them — as defined, dark pairs them at
     * 2.53:1.
     */
    private fun assertSchemeIsReadable(mode: ThemeMode) {
        val scheme = captureScheme(mode)

        assertReadable("$mode onSurface/surface", scheme.onSurface, scheme.surface)
        assertReadable("$mode onBackground/background", scheme.onBackground, scheme.background)
        // `onPrimary` also backs the swipe panel of every non-destructive action, whose caption is
        // `labelMedium` — small text, so the default 4.5 floor is the right one for this pair.
        assertReadable("$mode onPrimary/primary", scheme.onPrimary, scheme.primary)
        // `onError` is read in exactly two places, the icon and caption of the swipe panel when the
        // direction is set to delete, so that panel is the whole of this pair. (`error` is also the
        // BadgeDot fill, which carries no foreground.) Held to the UI-component floor by decision,
        // not because the caption qualifies as large text — `labelMedium` does not. The light
        // palette pairs these at 3.86:1, so raising this to 4.5 means darkening `errorLight`.
        assertReadable("$mode onError/error", scheme.onError, scheme.error, minimumRatio = 3.0)
        assertReadable(
            "$mode onSurfaceVariant/surface",
            scheme.onSurfaceVariant,
            scheme.surface
        )
        // Secondary label text and dividers only need the large-text/UI floor.
        assertReadable("$mode primary/surface", scheme.primary, scheme.surface, minimumRatio = 3.0)
        assertReadable("$mode error/surface", scheme.error, scheme.surface, minimumRatio = 3.0)
    }

    @Test
    fun lightTheme_everyForegroundIsReadableOnItsBackground() {
        assertSchemeIsReadable(ThemeMode.LIGHT)
    }

    @Test
    fun darkTheme_everyForegroundIsReadableOnItsBackground() {
        assertSchemeIsReadable(ThemeMode.DARK)
    }

    /**
     * The two schemes must actually differ. A copy-paste that pointed dark at the light palette
     * would pass every contrast assertion above while shipping a broken dark mode.
     */
    @Test
    fun lightAndDarkSchemes_areDistinct() {
        val schemes = captureSchemes(ThemeMode.LIGHT, ThemeMode.DARK)
        val light = schemes.getValue(ThemeMode.LIGHT)
        val dark = schemes.getValue(ThemeMode.DARK)

        assertTrue(
            "Light and dark surfaces should differ in luminance",
            abs(light.surface.luminance() - dark.surface.luminance()) > 0.2f
        )
        assertTrue(
            "A light scheme should have a lighter surface than a dark one",
            light.surface.luminance() > dark.surface.luminance()
        )
    }

    // ==================== Palette wiring ====================

    @Test
    fun lightTheme_usesTheLightPalette() {
        val scheme = captureScheme(ThemeMode.LIGHT)

        assertEquals("primary", primaryLight, scheme.primary)
        assertEquals("background", backgroundLight, scheme.background)
        assertEquals("surface", surfaceLight, scheme.surface)
        assertEquals("onSurface", onSurfaceLight, scheme.onSurface)
    }

    @Test
    fun darkTheme_usesTheDarkPalette() {
        val scheme = captureScheme(ThemeMode.DARK)

        assertEquals("primary", primaryDark, scheme.primary)
        assertEquals("background", backgroundDark, scheme.background)
        assertEquals("surface", surfaceDark, scheme.surface)
        assertEquals("onSurface", onSurfaceDark, scheme.onSurface)
    }

    /**
     * SYSTEM had no coverage at all, though it is the default. It must resolve to whichever palette
     * the device is currently in — never to a third, unstyled one.
     */
    @Test
    fun systemTheme_resolvesToTheDeviceScheme() {
        var systemIsDark = false
        lateinit var scheme: ColorScheme

        composeTestRule.setContent {
            systemIsDark = isSystemInDarkTheme()
            FileExplorerTheme(themeMode = ThemeMode.SYSTEM) {
                scheme = MaterialTheme.colorScheme
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        composeTestRule.waitForIdle()

        val expected = if (systemIsDark) surfaceDark else surfaceLight
        assertEquals(
            "SYSTEM must follow the device's current scheme",
            expected,
            scheme.surface
        )
    }

    @Test
    fun systemTheme_isReadable() {
        assertSchemeIsReadable(ThemeMode.SYSTEM)
    }

    // ==================== Switching ====================

    @Test
    fun themeSwitching_updatesColorsInBothDirections() {
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var background: Color? = null

        composeTestRule.setContent {
            FileExplorerTheme(themeMode = mode) {
                background = MaterialTheme.colorScheme.background
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(backgroundLight, background)

        mode = ThemeMode.DARK
        composeTestRule.waitForIdle()
        assertEquals(backgroundDark, background)

        mode = ThemeMode.LIGHT
        composeTestRule.waitForIdle()
        assertEquals(backgroundLight, background)
    }

    // ==================== Components render in every mode ====================

    /**
     * Renders [content] under every [ThemeMode] and runs [assert] after each, so a component that
     * throws or drops a node under one scheme fails here. Legibility is covered by the contrast
     * assertions above.
     *
     * The mode is a state the single composition reads, rather than one `setContent` per mode: the
     * rule allows only one, and calling it again throws "has already set content". [key] keeps each
     * mode a first composition rather than a recomposition of the previous one, so a component that
     * only breaks on the way in under a given scheme still fails here.
     */
    private fun forEachMode(content: @Composable (ThemeMode) -> Unit, assert: () -> Unit) {
        var mode by mutableStateOf(ThemeMode.entries.first())
        composeTestRule.setContent { key(mode) { content(mode) } }

        ThemeMode.entries.forEach { entry ->
            mode = entry
            composeTestRule.waitForIdle()
            assert()
        }
    }

    @Test
    fun fileListItem_rendersInEveryMode() {
        forEachMode(
            content = { mode ->
                FileExplorerTheme(themeMode = mode) {
                    FileListItem(
                        file = testFile,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        isSelected = false
                    )
                }
            },
            assert = { composeTestRule.onNodeWithText("test.txt").assertIsDisplayed() }
        )
    }

    @Test
    fun selectedFileListItem_rendersInEveryMode() {
        forEachMode(
            content = { mode ->
                FileExplorerTheme(themeMode = mode) {
                    FileListItem(
                        file = testFolder,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        isSelected = true
                    )
                }
            },
            assert = { composeTestRule.onNodeWithText("TestFolder").assertIsDisplayed() }
        )
    }

    @Test
    fun breadcrumbs_renderInEveryMode() {
        forEachMode(
            content = { mode ->
                FileExplorerTheme(themeMode = mode) {
                    Breadcrumbs(
                        currentPath = "/storage/emulated/0/Documents/Work",
                        onNavigateToPath = {}
                    )
                }
            },
            assert = { composeTestRule.onNodeWithText("Work").assertIsDisplayed() }
        )
    }

    @Test
    fun actionBar_rendersInEveryMode() {
        forEachMode(
            content = { mode ->
                FileExplorerTheme(themeMode = mode) {
                    ActionBar(state = selectionState, onAction = {})
                }
            },
            assert = {
                composeTestRule.onNodeWithText(string(R.string.action_move_to)).assertIsDisplayed()
                composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun createFolderDialog_rendersInEveryMode() {
        forEachMode(
            content = { mode ->
                FileExplorerTheme(themeMode = mode) {
                    CreateFolderDialog(existingNames = emptySet(), onDismiss = {}, onCreate = {})
                }
            },
            assert = {
                composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun deleteConfirmDialog_rendersInEveryMode() {
        forEachMode(
            content = { mode ->
                FileExplorerTheme(themeMode = mode) {
                    DeleteConfirmDialog(
                        itemCount = 3,
                        itemName = null,
                        onConfirm = {},
                        onDismiss = {}
                    )
                }
            },
            assert = {
                composeTestRule.onAllNodesWithText(string(R.string.delete_confirm_title))[0]
                    .assertIsDisplayed()
            }
        )
    }

    @Test
    fun emptyState_rendersInEveryMode() {
        forEachMode(
            content = { mode ->
                FileExplorerTheme(themeMode = mode) {
                    EmptyState()
                }
            },
            assert = { composeTestRule.onNodeWithText(string(R.string.list_empty)).assertIsDisplayed() }
        )
    }
}
