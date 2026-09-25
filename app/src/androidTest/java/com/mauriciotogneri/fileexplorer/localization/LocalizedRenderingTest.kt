package com.mauriciotogneri.fileexplorer.localization

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.FileSecondLineSettingItem
import com.mauriciotogneri.fileexplorer.activities.FolderSecondLineSettingItem
import com.mauriciotogneri.fileexplorer.activities.StartupScreenSettingItem
import com.mauriciotogneri.fileexplorer.activities.SwipeLeftActionSettingItem
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Production rows laid out under a locale that is not the device's — which nothing else in the suite
 * does. `LocalizationParityTest` checks all 20 `values-*` statically on the JVM, and the RTL tests
 * force `LocalLayoutDirection` without touching the locale, so before this file no test had ever
 * laid out a translated string, let alone an Arabic one.
 *
 * What it guards: the settings rows that show what a setting is currently at put
 * `maxLines = 1, overflow = Ellipsis` on that subtitle, and German and Romanian run roughly a third
 * longer than English. A translation that outgrows the line ellipsises the only place the row says
 * what it is set to, and no static check can see it — the string is valid, it just does not fit.
 *
 * `HomeSectionsSettingItem` is deliberately absent: its subtitle joins every section name and is
 * documented as ellipsising by design, so asserting that it fits would contradict the component.
 *
 * How the locale is applied: `stringResource` resolves against `LocalResources`, which is computed
 * from `LocalContext` and `LocalConfiguration`, so providing a `createConfigurationContext` context
 * is what makes the production composables resolve the translation. Layout direction comes from the
 * host `View` rather than from the configuration, so the RTL case provides `LocalLayoutDirection` as
 * well: the platform derives one from the other, a Compose test has to state both.
 *
 * Every assertion reads the resolved string off the rendered node — no translated literal appears
 * here — and [assertTranslationsResolve] fails first if the override quietly fell back to English,
 * which would otherwise leave the rest of the file unable to fail.
 *
 * What it cannot assert: there is no `BidiFormatter`/`unicodeWrap` anywhere in `app/src/main`, so an
 * Arabic name inside otherwise Latin text has no isolation around it. Asserting the correct
 * rendering would need that production change first; asserting the current one would enshrine the
 * defect, so it is reported instead of tested.
 */
@RunWith(AndroidJUnit4::class)
class LocalizedRenderingTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun germanValueRows_keepTheirSubtitleOnItsLine() {
        renderValueRows(GERMAN, LayoutDirection.Ltr)

        assertTranslationsResolve(GERMAN)
        assertNothingEllipsised(GERMAN)
    }

    @Test
    fun romanianValueRows_keepTheirSubtitleOnItsLine() {
        renderValueRows(ROMANIAN, LayoutDirection.Ltr)

        assertTranslationsResolve(ROMANIAN)
        assertNothingEllipsised(ROMANIAN)
    }

    @Test
    fun arabicValueRows_keepTheirSubtitleOnItsLineAndReadRightToLeft() {
        renderValueRows(ARABIC, LayoutDirection.Rtl)

        assertTranslationsResolve(ARABIC)
        assertNothingEllipsised(ARABIC)
        assertEveryParagraphReadsRightToLeft()
    }

    // ==================== Rendering ====================

    /**
     * Every value a `maxLines = 1` settings subtitle can hold, each in its own real row: a label
     * that outgrows the line is found whichever setting happens to be at that value.
     */
    private fun renderValueRows(locale: Locale, layoutDirection: LayoutDirection) {
        val localized = localizedContext(locale)
        val configuration = localized.resources.configuration

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalLayoutDirection provides layoutDirection
            ) {
                FileExplorerTheme {
                    // Scrollable, so the rows past the bottom of the screen are measured against
                    // their own height rather than against the space the screen has left.
                    Column(
                        modifier = Modifier
                            .width(BASELINE_PHONE_WIDTH)
                            .verticalScroll(rememberScrollState())
                    ) {
                        FolderSecondLine.entries.forEach { secondLine ->
                            FolderSecondLineSettingItem(secondLine = secondLine, onClick = {})
                        }
                        FileSecondLine.entries.forEach { secondLine ->
                            FileSecondLineSettingItem(secondLine = secondLine, onClick = {})
                        }
                        SwipeAction.entries.forEach { action ->
                            SwipeLeftActionSettingItem(action = action, onClick = {})
                        }
                        StartupScreenSettingItem(
                            startupScreen = StartupScreen.HOME,
                            folderName = null,
                            onClick = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun localizedContext(locale: Locale): Context {
        val activity = composeTestRule.activity
        val configuration = Configuration(activity.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }

        return activity.createConfigurationContext(configuration)
    }

    // ==================== Assertions ====================

    /**
     * The locale override has to actually resolve translations, or everything below it passes on
     * English. Both sides are read from resources, so this states nothing about what the words are.
     */
    private fun assertTranslationsResolve(locale: Locale) {
        val translated = localizedContext(locale).getString(R.string.settings_startup_home)
        val english = localizedContext(Locale.ENGLISH).getString(R.string.settings_startup_home)

        assertNotEquals(
            "Resources for $locale fell back to English, so nothing else here could fail",
            english,
            translated
        )
        composeTestRule
            .onNodeWithText(translated, useUnmergedTree = true)
            .assertExists("the startup row rendered something other than its $locale label")
    }

    /**
     * `hasVisualOverflow` cannot answer this. A `BasicText` holding a plain `String` shrinks its node
     * to the width of its content while the paragraph keeps the width it was measured in, so the
     * flag is true of every line that merely leaves room to spare — English included. The ellipsis
     * the row would actually show is what [TextLayoutResult.isLineEllipsized] reports.
     */
    private fun assertNothingEllipsised(locale: Locale) {
        forEachRenderedText { layout ->
            val ellipsised = (0 until layout.lineCount).any { line -> layout.isLineEllipsized(line) }

            assertFalse(
                "$locale: \"${layout.layoutInput.text.text}\" does not fit the width a settings " +
                    "row gives it at $BASELINE_PHONE_WIDTH, so it is ellipsised away",
                ellipsised
            )
        }
    }

    /**
     * The first Arabic glyphs the suite lays out. Fails if something forces a left-to-right text
     * direction on these rows, and equally if the rows rendered the English fallback, whose strong
     * characters resolve the paragraph the other way.
     */
    private fun assertEveryParagraphReadsRightToLeft() {
        forEachRenderedText { layout ->
            assertEquals(
                "ar: \"${layout.layoutInput.text.text}\" was laid out left to right",
                ResolvedTextDirection.Rtl,
                layout.getParagraphDirection(0)
            )
        }
    }

    private fun forEachRenderedText(assertion: (TextLayoutResult) -> Unit) {
        val texts = composeTestRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true
        )
        val count = texts.fetchSemanticsNodes().size
        assertTrue("No text was rendered at all — the settings rows did not compose", count > 0)

        repeat(count) { index -> assertion(texts[index].textLayoutResult()) }
    }

    private fun SemanticsNodeInteraction.textLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(results) }

        return results.first()
    }

    private companion object {
        /**
         * The baseline phone width these rows are asserted at, rather than whatever the emulator
         * happens to be: a wider device would let a translation that does not fit a phone pass. A
         * narrower one clamps this, which only makes the assertion stricter.
         */
        val BASELINE_PHONE_WIDTH = 360.dp

        val GERMAN: Locale = Locale.forLanguageTag("de")
        val ROMANIAN: Locale = Locale.forLanguageTag("ro")
        val ARABIC: Locale = Locale.forLanguageTag("ar")
    }
}
