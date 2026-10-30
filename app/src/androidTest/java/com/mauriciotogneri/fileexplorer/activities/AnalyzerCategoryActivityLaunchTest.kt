package com.mauriciotogneri.fileexplorer.activities

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerResultsHolder
import com.mauriciotogneri.fileexplorer.data.repository.CategoryFiles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [AnalyzerCategoryActivity] launched for real, from the intent its own `createIntent` builds.
 *
 * `AnalyzerCategoryLaunchTest` pins that each chart row's intent names its category, but stubs the
 * launch, so nothing covered the other half: the activity reading that extra back. Dropping the
 * read, or the two sides spelling the key differently, makes every category open a screen that
 * closes itself, with every other test green.
 */
@RunWith(AndroidJUnit4::class)
class AnalyzerCategoryActivityLaunchTest {

    /**
     * Empty rather than `createAndroidComposeRule`: each test launches the activity with the intent
     * under test. The rule only provides the waiting and matching against whatever is on screen.
     */
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // An empty listing for every category, so the intent is the only thing that can close the
        // screen: with nothing in the holder it closes on its own, whatever the intent carries.
        AnalyzerResultsHolder.store(
            AnalyzerCategory.entries.associateWith { CategoryFiles(totalBytes = 0L, entries = emptyList()) }
        )
    }

    @After
    fun tearDown() {
        AnalyzerResultsHolder.clear()
    }

    @Test
    fun categoryIntent_opensTheListingForThatCategory() {
        (AnalyzerCategory.entries - AnalyzerCategory.SYSTEM).forEach { category ->
            val intent = AnalyzerCategoryActivity.createIntent(context, category)

            ActivityScenario.launch<AnalyzerCategoryActivity>(intent).use { scenario ->
                assertEquals(
                    "The listing for $category closed itself instead of drawing",
                    Lifecycle.State.RESUMED,
                    scenario.state
                )

                // The title is the category's label, so it pins which category the activity read
                // out of the intent, not only that it drew.
                awaitText(context.getString(category.labelResId))
            }
        }
    }

    // Without a category there is nothing to list; the activity must close rather than draw an
    // empty or guessed listing — with results in the holder, only the extra's guard can close it.
    @Test
    fun withoutCategoryExtra_finishes() {
        ActivityScenario.launch<AnalyzerCategoryActivity>(Intent(context, AnalyzerCategoryActivity::class.java)).use { scenario ->
            // finish() runs in onCreate before setContent, so DESTROYED is the only steady state the
            // activity can reach, and the launch returns once it is there.
            assertEquals(
                "A launch without the category extra must finish instead of showing a listing",
                Lifecycle.State.DESTROYED,
                scenario.state
            )
        }
    }

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
