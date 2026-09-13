package com.mauriciotogneri.fileexplorer.theme

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.components.ActionBar
import com.mauriciotogneri.fileexplorer.ui.components.Breadcrumbs
import com.mauriciotogneri.fileexplorer.ui.components.CreateFolderDialog
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.EmptyState
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderUiState
import com.mauriciotogneri.fileexplorer.ui.theme.ExtendedColorScheme
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import com.mauriciotogneri.fileexplorer.ui.theme.backgroundDark
import com.mauriciotogneri.fileexplorer.ui.theme.backgroundLight
import com.mauriciotogneri.fileexplorer.ui.theme.extendedColorScheme
import com.mauriciotogneri.fileexplorer.ui.theme.onSurfaceDark
import com.mauriciotogneri.fileexplorer.ui.theme.onSurfaceLight
import com.mauriciotogneri.fileexplorer.ui.theme.primaryDark
import com.mauriciotogneri.fileexplorer.ui.theme.primaryLight
import com.mauriciotogneri.fileexplorer.ui.theme.selectionBackgroundDark
import com.mauriciotogneri.fileexplorer.ui.theme.selectionBackgroundLight
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
 * Three kinds of assertion live here, and the distinction is the point of the file:
 *
 * - **Palette**: the foreground/background pairs listed in [assertSchemeIsReadable] clear their WCAG
 *   contrast floor, each mode is wired to its own constants, and the analyzer ramp is a ramp. Every
 *   one of these compares two `Theme.kt` values against each other, so none of them can see a
 *   component that reads the wrong token.
 * - **Rendered**: a pixel sampled out of a production composable with [captureToImage], which is
 *   what fails when a row hardcodes a colour or paints `surface` where it meant
 *   `selectionBackground`.
 * - **Structural**: every component still renders under every mode, because a crash or a missing
 *   node under one scheme is worth catching on its own.
 *
 * [ThemeMode.SYSTEM] is exercised by forcing `LocalConfiguration`'s night-mode bits rather than by
 * reading the device's. The previous test called `isSystemInDarkTheme()` itself and compared the
 * result against what `FileExplorerTheme` had computed from that same call, so it held one source of
 * truth against itself: changing `Theme.kt` to `ThemeMode.SYSTEM -> false` kept it green on a light
 * emulator. Nothing in either source set put anything into night mode either (`UI_MODE_NIGHT` under
 * `app/src` matches production reads only), so the dark half of that branch had never run anywhere.
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

    // ==================== Capturing ====================

    /**
     * Both schemes [FileExplorerTheme] provides for one mode.
     *
     * They are captured together because they are only meaningful together: `categoryTones` is a set
     * of progress-bar fills whose track is [ColorScheme.surfaceVariant], so a tone from one mode held
     * against the other mode's track measures nothing.
     */
    private data class Schemes(val base: ColorScheme, val extended: ExtendedColorScheme)

    /**
     * The rule permits a single `setContent` per test, so every scheme a test needs is captured in
     * one composition.
     */
    private fun captureSchemes(vararg modes: ThemeMode): Map<ThemeMode, Schemes> {
        val schemes = mutableMapOf<ThemeMode, Schemes>()
        composeTestRule.setContent {
            modes.forEach { mode ->
                FileExplorerTheme(themeMode = mode) {
                    schemes[mode] = Schemes(
                        base = MaterialTheme.colorScheme,
                        extended = MaterialTheme.extendedColorScheme
                    )
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
        composeTestRule.waitForIdle()
        return schemes
    }

    private fun captureScheme(mode: ThemeMode): Schemes = captureSchemes(mode).getValue(mode)

    /**
     * The schemes [ThemeMode.SYSTEM] resolves to when the device configuration is forced to each of
     * [nightModes], keyed by the `Configuration.UI_MODE_NIGHT_*` constant that produced them.
     *
     * `isSystemInDarkTheme()` reads `LocalConfiguration.current.uiMode`, so overriding that local is
     * what forces the branch — and forcing it is the only way this assertion can fail for the right
     * reason, since a test that reads the device's own night mode is comparing production's input to
     * itself.
     */
    private fun captureSystemSchemes(vararg nightModes: Int): Map<Int, Schemes> {
        val schemes = mutableMapOf<Int, Schemes>()
        composeTestRule.setContent {
            val deviceConfiguration = LocalConfiguration.current
            nightModes.forEach { nightMode ->
                CompositionLocalProvider(
                    LocalConfiguration provides forcingNightMode(deviceConfiguration, nightMode)
                ) {
                    FileExplorerTheme(themeMode = ThemeMode.SYSTEM) {
                        schemes[nightMode] = Schemes(
                            base = MaterialTheme.colorScheme,
                            extended = MaterialTheme.extendedColorScheme
                        )
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        return schemes
    }

    /** [base] with its night-mode bits replaced by [nightMode]; everything else is the device's. */
    private fun forcingNightMode(base: Configuration, nightMode: Int): Configuration =
        Configuration(base).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }

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
     * `primaryContainer`/`onPrimaryContainer` are absent from this list on purpose: both are wired
     * into the schemes but read nowhere outside `Theme.kt`, so there is no rendered pair to hold to
     * a floor. Add the assertion the moment a component adopts them — as defined, dark pairs them at
     * 2.53:1.
     */
    private fun assertSchemeIsReadable(label: String, scheme: ColorScheme) {
        assertReadable("$label onSurface/surface", scheme.onSurface, scheme.surface)
        assertReadable("$label onBackground/background", scheme.onBackground, scheme.background)
        // `onPrimary` also backs the swipe panel of every non-destructive action, whose caption is
        // `labelMedium` — small text, so the default 4.5 floor is the right one for this pair.
        assertReadable("$label onPrimary/primary", scheme.onPrimary, scheme.primary)
        // `onError` is read in exactly two places, the icon and caption of the swipe panel when the
        // direction is set to delete, so that panel is the whole of this pair. (`error` is also the
        // BadgeDot fill, which carries no foreground.) Held to the UI-component floor by decision,
        // not because the caption qualifies as large text — `labelMedium` does not. The light
        // palette pairs these at 3.86:1, so raising this to 4.5 means darkening `errorLight`.
        assertReadable("$label onError/error", scheme.onError, scheme.error, minimumRatio = 3.0)
        assertReadable(
            "$label onSurfaceVariant/surface",
            scheme.onSurfaceVariant,
            scheme.surface
        )
        // Secondary label text and dividers only need the large-text/UI floor.
        assertReadable("$label primary/surface", scheme.primary, scheme.surface, minimumRatio = 3.0)
        assertReadable("$label error/surface", scheme.error, scheme.surface, minimumRatio = 3.0)
    }

    @Test
    fun lightPalette_everyForegroundIsReadableOnItsBackground() {
        assertSchemeIsReadable("LIGHT", captureScheme(ThemeMode.LIGHT).base)
    }

    @Test
    fun darkPalette_everyForegroundIsReadableOnItsBackground() {
        assertSchemeIsReadable("DARK", captureScheme(ThemeMode.DARK).base)
    }

    /**
     * The two schemes must actually differ. A copy-paste that pointed dark at the light palette
     * would pass every contrast assertion above while shipping a broken dark mode.
     */
    @Test
    fun lightAndDarkPalettes_areDistinct() {
        val schemes = captureSchemes(ThemeMode.LIGHT, ThemeMode.DARK)
        val light = schemes.getValue(ThemeMode.LIGHT).base
        val dark = schemes.getValue(ThemeMode.DARK).base

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
        val scheme = captureScheme(ThemeMode.LIGHT).base

        assertEquals("primary", primaryLight, scheme.primary)
        assertEquals("background", backgroundLight, scheme.background)
        assertEquals("surface", surfaceLight, scheme.surface)
        assertEquals("onSurface", onSurfaceLight, scheme.onSurface)
    }

    @Test
    fun darkTheme_usesTheDarkPalette() {
        val scheme = captureScheme(ThemeMode.DARK).base

        assertEquals("primary", primaryDark, scheme.primary)
        assertEquals("background", backgroundDark, scheme.background)
        assertEquals("surface", surfaceDark, scheme.surface)
        assertEquals("onSurface", onSurfaceDark, scheme.onSurface)
    }

    /**
     * SYSTEM is the default mode, and both of its outcomes are asserted against the palette
     * constants rather than against the device's own night mode — see the note on
     * [captureSystemSchemes] for why that distinction is the whole test.
     *
     * Readability of whichever palette it lands on is not re-asserted here: it resolves to the
     * *same* `LightColorScheme` or `DarkColorScheme` the two palette tests above already hold to
     * their floors, so the `systemTheme_isReadable` that used to sit here re-ran one of them
     * verbatim — on a light emulator, `lightPalette_everyForegroundIsReadableOnItsBackground`.
     */
    @Test
    fun systemTheme_followsTheDeviceNightModeInBothDirections() {
        val schemes = captureSystemSchemes(
            Configuration.UI_MODE_NIGHT_YES,
            Configuration.UI_MODE_NIGHT_NO
        )

        assertEquals(
            "SYSTEM on a device in night mode must resolve to the dark palette",
            surfaceDark,
            schemes.getValue(Configuration.UI_MODE_NIGHT_YES).base.surface
        )
        assertEquals(
            "SYSTEM on a device not in night mode must resolve to the light palette",
            surfaceLight,
            schemes.getValue(Configuration.UI_MODE_NIGHT_NO).base.surface
        )
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

    // ==================== Extended scheme ====================

    /**
     * The analyzer chart is the only reader of `categoryTones`, and [ExtendedColorScheme] had no
     * coverage of any kind: a ramp pointed at the wrong mode's list, shuffled, or short a step
     * repainted every arc and every category bar with nothing to fail.
     *
     * The floor is the one the ramp's own note in `Color.kt` derives: each tone is the fill of a
     * progress bar whose track is `surfaceVariant`, so even the faintest — SYSTEM, routinely the
     * largest slice — has to clear WCAG 1.4.11's 3:1 against that track. Holding the dark ramp
     * against the light track lands at about 1.1:1, so this is also what fails if the two lists are
     * swapped.
     */
    @Test
    fun categoryTones_areOneDistinctTonePerCategoryAndReadableOnTheirTrack() {
        val schemes = captureSchemes(ThemeMode.LIGHT, ThemeMode.DARK)

        schemes.forEach { (mode, captured) ->
            val tones = captured.extended.categoryTones
            val track = captured.base.surfaceVariant

            assertEquals(
                "$mode needs one category tone per analyzer category",
                AnalyzerCategory.entries.size,
                tones.size
            )
            assertEquals(
                "$mode category tones must all differ, or two categories are drawn alike",
                tones.size,
                tones.distinct().size
            )

            tones.forEachIndexed { index, tone ->
                assertReadable(
                    "$mode ${AnalyzerCategory.entries[index]} tone against its bar track",
                    tone,
                    track,
                    minimumRatio = 3.0
                )
            }

            // Declaration order is display order, and the ramp is a luminance ramp: every step has
            // to sit closer to the track than the one before it. Distinctness and the floor above
            // both survive a shuffled list, which would repaint the chart while the rows that act
            // as its legend stayed put.
            tones.zipWithNext().forEachIndexed { index, (previous, next) ->
                val from = AnalyzerCategory.entries[index]
                val to = AnalyzerCategory.entries[index + 1]
                assertTrue(
                    "$mode tone for $to must fade further towards the track than $from's",
                    abs(next.luminance() - track.luminance()) <
                        abs(previous.luminance() - track.luminance())
                )
            }
        }
    }

    // ==================== Rendered colors ====================

    /**
     * The colour a rendered row is actually filled with, read back out of the pixels.
     *
     * Every assertion above this point compares two `Theme.kt` constants against each other:
     * [captureSchemes] composes the theme around an empty `Box` and reads the palette back, so no
     * component is in the composition and no pixel is ever looked at. A row painting `Color.White`
     * instead of its token, or `surface` where it meant `selectionBackground`, passed all of them.
     *
     * The sample sits 8dp in from the row's leading edge at half its height: a solid run of the
     * `Surface` fill, clear of the icon that starts at 16dp and of every glyph. Antialiased edges are
     * never sampled — a pixel on the boundary of a letter is a blend of two colours, and asserting
     * on one would be flaky by construction.
     */
    private fun rowFill(name: String): Color {
        val image = composeTestRule.onNodeWithText(name).captureToImage()
        val x = with(composeTestRule.density) { 8.dp.roundToPx() }
        return image.toPixelMap()[x, image.height / 2]
    }

    private fun assertFill(label: String, expected: Color, actual: Color) {
        // Channel-wise rather than equality: the capture round-trips through the window's 8-bit
        // buffer, so a 1/255 rounding difference must not fail. What these assertions exist to catch
        // is far coarser — 31/255 between the two light row fills, 24/255 between the dark ones.
        val difference = max(
            abs(expected.red - actual.red),
            max(abs(expected.green - actual.green), abs(expected.blue - actual.blue))
        )
        assertTrue(
            "$label rendered as ${hex(actual)}, expected ${hex(expected)}",
            difference <= 0.02f
        )
    }

    private fun hex(color: Color): String = "#%08X".format(color.toArgb())

    /**
     * The one assertion in this file that reads a rendered pixel of a production composable, and the
     * only coverage `selectionBackground` has.
     *
     * `selectedFileListItem_rendersInEveryMode` asserted that a selected row displayed its name, so
     * swapping `selectionBackgroundLight` and `selectionBackgroundDark` in `Theme.kt` — which gives
     * dark-mode selected rows a near-white fill under `onSurfaceDark` text — left the suite green.
     * So did hardcoding either fill, or reading `surface` for the selected row.
     *
     * SYSTEM is not among the modes: which palette it resolves to is the device's to decide, so
     * there is no constant to compare a pixel against. [systemTheme_followsTheDeviceNightModeInBothDirections]
     * covers that branch by forcing the configuration instead.
     */
    // The SdkSuppress is captureToImage()'s: it is annotated @RequiresApi(O), and minSdk is 24.
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun fileListItem_drawsTheSurfaceAndSelectionFillsOfItsMode() {
        val expectedFills = mapOf(
            ThemeMode.LIGHT to (surfaceLight to selectionBackgroundLight),
            ThemeMode.DARK to (surfaceDark to selectionBackgroundDark)
        )

        forEachMode(
            modes = expectedFills.keys.toList(),
            content = { mode ->
                FileExplorerTheme(themeMode = mode) {
                    Column {
                        FileListItem(
                            file = testFile,
                            onClick = {},
                            onLongClick = {},
                            onMenuClick = {},
                            isSelected = false
                        )
                        FileListItem(
                            file = testFolder,
                            onClick = {},
                            onLongClick = {},
                            onMenuClick = {},
                            isSelected = true,
                            isSelectionMode = true
                        )
                    }
                }
            },
            assert = { mode ->
                val (surface, selection) = expectedFills.getValue(mode)
                assertFill("$mode unselected row fill", surface, rowFill(testFile.name))
                assertFill("$mode selected row fill", selection, rowFill(testFolder.name))
            }
        )
    }

    // ==================== Components render in every mode ====================

    /**
     * Renders [content] under every mode in [modes] and runs [assert] after each, so a component
     * that throws or drops a node under one scheme fails here. Legibility is covered by the contrast
     * assertions above, and the fills a row paints by the rendered-colour test.
     *
     * The mode is a state the single composition reads, rather than one `setContent` per mode: the
     * rule allows only one, and calling it again throws "has already set content". [key] keeps each
     * mode a first composition rather than a recomposition of the previous one, so a component that
     * only breaks on the way in under a given scheme still fails here.
     *
     * The mode is passed to [assert] — the rendered-colour test compares against the palette it
     * implies — and is also prefixed onto whatever [assert] throws. `assertIsDisplayed()` takes no
     * message, so a component that renders under LIGHT and not under DARK used to fail with a bare
     * "node is not displayed" naming neither the mode nor which of the 21 cases had failed.
     */
    private fun forEachMode(
        modes: List<ThemeMode> = ThemeMode.entries,
        content: @Composable (ThemeMode) -> Unit,
        assert: (ThemeMode) -> Unit
    ) {
        var mode by mutableStateOf(modes.first())
        composeTestRule.setContent { key(mode) { content(mode) } }

        modes.forEach { entry ->
            mode = entry
            composeTestRule.waitForIdle()
            try {
                assert(entry)
            } catch (error: AssertionError) {
                throw AssertionError("In $entry mode: ${error.message}", error)
            }
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
            assert = { composeTestRule.onNodeWithText(testFile.name).assertIsDisplayed() }
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
                        isSelected = true,
                        isSelectionMode = true
                    )
                }
            },
            assert = { composeTestRule.onNodeWithText(testFolder.name).assertIsDisplayed() }
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
                // `delete_confirm_title` and `dialog_delete` are the same word, so two nodes carry
                // that text — the title and the confirm button — and `onNodeWithText` alone cannot
                // name one. The `onAllNodesWithText(…)[0]` that stood here picked whichever came
                // first: a dialog that had lost its title still passed on the button alone, and one
                // rendering a duplicate title passed instead of failing on the ambiguity. Only the
                // title has no click action, so this addresses it exactly and still fails if a
                // second such node appears.
                composeTestRule
                    .onNode(hasText(string(R.string.delete_confirm_title)) and hasNoClickAction())
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
