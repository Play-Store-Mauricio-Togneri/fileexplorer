package com.mauriciotogneri.fileexplorer.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
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

    // Named "Documents" until it collided with `location_documents`: the card renders the on-disk
    // name, so the literal is right in kind, but a fixture sharing a resource value makes every
    // matcher against it locale-dependent. "Ledgers" appears in no <string> value.
    private val ledgers = favorite("Ledgers", isDirectory = true)

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
        render(listOf(notes, report, ledgers))

        composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("report.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ledgers").assertIsDisplayed()
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

        // onNode, not onAllNodes[0]: one favourite is rendered, so exactly one overflow must exist.
        // Indexing would have picked the first of a duplicated set rather than failing on it.
        composeTestRule
            .onNodeWithContentDescription(string(R.string.content_description_more_options))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(notes, received?.first)
        assertEquals("icon", received?.second)
    }

    /**
     * Each card's overflow must report *that* card's favourite. This counted the overflow buttons
     * instead (`menus.size >= 3`), which tolerates duplicates and checks no identity at all, and
     * the two tests that do check identity each render a single-item list — so the classic capture
     * bug, `onIconClick = { onMenuClick(favorites.first(), "icon") }` opening the first favourite's
     * sheet from every card, was green everywhere.
     *
     * The overflow is addressed through the card that owns it (`hasAnyAncestor(hasText(name))`)
     * rather than by index into `onAllNodes`, which would pick a duplicate rather than fail on it.
     */
    @Test
    fun favoritesSection_eachCardsMenu_reportsItsOwnFavorite() {
        val order = listOf(notes, report, ledgers)
        val received = mutableListOf<Favorite>()
        render(order, onMenuClick = { favorite, _ -> received += favorite })

        order.forEach { favorite ->
            composeTestRule
                .onNode(
                    hasContentDescription(string(R.string.content_description_more_options)) and
                        hasAnyAncestor(hasText(favorite.name))
                )
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()
        }

        assertEquals(order, received)
    }
}
