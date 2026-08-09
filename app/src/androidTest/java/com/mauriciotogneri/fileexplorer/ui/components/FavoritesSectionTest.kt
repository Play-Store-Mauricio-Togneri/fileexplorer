package com.mauriciotogneri.fileexplorer.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The favourites row on the home screen, which had no test at any level despite being a whole
 * user-facing feature.
 *
 * The behaviour worth pinning is the `onMenuClick(favorite, source)` contract: the same callback
 * carries a different source string depending on whether the user tapped the card's icon or
 * long-pressed the card, and analytics distinguishes the two. A refactor that collapsed them would
 * be invisible without this.
 */
@RunWith(AndroidJUnit4::class)
class FavoritesSectionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    private fun favorite(name: String, isDirectory: Boolean = false) = Favorite(
        path = "/storage/emulated/0/Download/$name",
        name = name,
        isDirectory = isDirectory,
        mimeType = if (isDirectory) "" else "text/plain",
        favoritedTimestamp = 1_700_000_000_000L,
        lastModified = 1_700_000_000_000L
    )

    private val notes = favorite("notes.txt")
    private val report = favorite("report.pdf")
    private val documents = favorite("Documents", isDirectory = true)

    private fun render(
        favorites: List<Favorite>,
        onFileClick: (Favorite) -> Unit = {},
        onMenuClick: (Favorite, String) -> Unit = { _, _ -> }
    ) {
        composeTestRule.setContent {
            FileExplorerTheme {
                FavoritesSection(
                    favorites = favorites,
                    onFileClick = onFileClick,
                    onMenuClick = onMenuClick
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // ==================== Display ====================

    @Test
    fun favoritesSection_displaysSectionTitle() {
        render(listOf(notes))

        composeTestRule.onNodeWithText(string(R.string.section_favorites)).assertIsDisplayed()
    }

    @Test
    fun favoritesSection_displaysEveryFavorite() {
        render(listOf(notes, report, documents))

        composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("report.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
    }

    /** With nothing favourited the section returns early, so even its heading must be absent. */
    @Test
    fun favoritesSection_emptyList_rendersNothing() {
        render(emptyList())

        composeTestRule.onNodeWithText(string(R.string.section_favorites)).assertDoesNotExist()
    }

    // ==================== Interaction ====================

    @Test
    fun favoritesSection_cardClick_reportsThatFavorite() {
        var clicked: Favorite? = null
        render(listOf(notes, report), onFileClick = { clicked = it })

        composeTestRule.onNodeWithText("report.pdf").performClick()

        assertEquals(report, clicked)
    }

    /** A long press opens the same sheet, but analytics needs to know it came from a press. */
    @Test
    fun favoritesSection_longPress_reportsPressSource() {
        var received: Pair<Favorite, String>? = null
        render(listOf(notes), onMenuClick = { fav, source -> received = fav to source })

        composeTestRule.onNodeWithText("notes.txt").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        assertEquals(notes, received?.first)
        assertEquals("press", received?.second)
    }

    @Test
    fun favoritesSection_iconClick_reportsIconSource() {
        var received: Pair<Favorite, String>? = null
        render(listOf(notes), onMenuClick = { fav, source -> received = fav to source })

        composeTestRule
            .onAllNodesWithContentDescription(string(R.string.content_description_more_options))[0]
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(notes, received?.first)
        assertEquals("icon", received?.second)
    }

    @Test
    fun favoritesSection_eachCardHasItsOwnMenuButton() {
        render(listOf(notes, report, documents))

        val menus = composeTestRule
            .onAllNodesWithContentDescription(string(R.string.content_description_more_options))
            .fetchSemanticsNodes()

        assertTrue(
            "Each favourite card needs its own overflow, found ${menus.size}",
            menus.size >= 3
        )
    }
}
