package com.mauriciotogneri.fileexplorer.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.HomeSectionsOrderDialog
import com.mauriciotogneri.fileexplorer.activities.homeSectionHandleTag
import com.mauriciotogneri.fileexplorer.activities.homeSectionRowTag
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real `HomeSectionsOrderDialog`, driven by the gesture it ships with.
 *
 * Dragging is the only way to reorder — there are no buttons and no accessibility actions — so a
 * test that skipped the gesture would leave the whole feature uncovered: a handle wired to the wrong
 * row would still save a plausible-looking order.
 */
@RunWith(AndroidJUnit4::class)
class HomeSectionsOrderDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    @Test
    fun dialog_listsEverySection_inTheStoredOrder() {
        val stored = listOf(
            HomeSection.STORAGE,
            HomeSection.RECENT,
            HomeSection.LOCATIONS,
            HomeSection.FAVORITES
        )
        renderDialog(order = stored)

        HomeSection.entries.forEach { section ->
            composeTestRule.onNodeWithText(string(section.titleResId)).assertIsDisplayed()
        }
        assertEquals(stored, renderedOrder())
    }

    @Test
    fun drag_movesASectionDown() {
        var saved: List<HomeSection>? = null
        renderDialog(order = HomeSection.DEFAULT_ORDER, onSave = { saved = it })

        dragHandle(HomeSection.RECENT, rows = 1)
        save()

        assertEquals(
            listOf(HomeSection.FAVORITES, HomeSection.RECENT, HomeSection.LOCATIONS, HomeSection.STORAGE),
            saved
        )
    }

    @Test
    fun drag_movesASectionUp() {
        var saved: List<HomeSection>? = null
        renderDialog(order = HomeSection.DEFAULT_ORDER, onSave = { saved = it })

        dragHandle(HomeSection.STORAGE, rows = -1)
        save()

        assertEquals(
            listOf(HomeSection.RECENT, HomeSection.FAVORITES, HomeSection.STORAGE, HomeSection.LOCATIONS),
            saved
        )
    }

    @Test
    fun drag_acrossSeveralRows_movesThatFar() {
        var saved: List<HomeSection>? = null
        renderDialog(order = HomeSection.DEFAULT_ORDER, onSave = { saved = it })

        dragHandle(HomeSection.RECENT, rows = 3)
        save()

        assertEquals(
            listOf(HomeSection.FAVORITES, HomeSection.LOCATIONS, HomeSection.STORAGE, HomeSection.RECENT),
            saved
        )
    }

    @Test
    fun drag_reordersTheRowsOnScreen() {
        renderDialog(order = HomeSection.DEFAULT_ORDER)

        dragHandle(HomeSection.RECENT, rows = 1)

        assertEquals(
            listOf(HomeSection.FAVORITES, HomeSection.RECENT, HomeSection.LOCATIONS, HomeSection.STORAGE),
            renderedOrder()
        )
    }

    @Test
    fun drag_pastTheEndOfTheList_stopsAtTheLastRow() {
        var saved: List<HomeSection>? = null
        renderDialog(order = HomeSection.DEFAULT_ORDER, onSave = { saved = it })

        dragHandle(HomeSection.RECENT, rows = 9)
        save()

        assertEquals(
            listOf(HomeSection.FAVORITES, HomeSection.LOCATIONS, HomeSection.STORAGE, HomeSection.RECENT),
            saved
        )
    }

    @Test
    fun cancel_discardsTheDrag() {
        var saved: List<HomeSection>? = null
        var dismissed = false
        renderDialog(
            order = HomeSection.DEFAULT_ORDER,
            onSave = { saved = it },
            onDismiss = { dismissed = true }
        )

        dragHandle(HomeSection.RECENT, rows = 1)
        // Asserted before cancelling: without it the test passes just as well on a drag that moved
        // nothing, which is the case it exists to rule out.
        assertEquals(
            listOf(HomeSection.FAVORITES, HomeSection.RECENT, HomeSection.LOCATIONS, HomeSection.STORAGE),
            renderedOrder()
        )

        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()
        composeTestRule.waitForIdle()

        assertNull(saved)
        assertTrue(dismissed)
    }

    @Test
    fun save_withoutADrag_keepsTheOrderItWasGiven() {
        var saved: List<HomeSection>? = null
        renderDialog(order = HomeSection.DEFAULT_ORDER, onSave = { saved = it })

        save()

        assertEquals(HomeSection.DEFAULT_ORDER, saved)
    }

    @Test
    fun save_afterTheStoredOrderArrives_keepsThatOrder() {
        val stored = listOf(
            HomeSection.STORAGE,
            HomeSection.RECENT,
            HomeSection.LOCATIONS,
            HomeSection.FAVORITES
        )
        var order by mutableStateOf(HomeSection.DEFAULT_ORDER)
        var saved: List<HomeSection>? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                HomeSectionsOrderDialog(
                    order = order,
                    onSave = { saved = it },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // The caller seeds its state with the default arrangement and replaces it when the
        // preference flow emits, which can land after the dialog is already on screen.
        order = stored
        composeTestRule.waitForIdle()

        // Asserted before saving: a dialog still showing the placeholder is the failure this test
        // exists to catch, whether or not Save happens to write the right thing.
        assertEquals(stored, renderedOrder())

        save()

        assertEquals(stored, saved)
    }

    @Test
    fun save_dismissesTheDialog() {
        var dismissed = false
        renderDialog(order = HomeSection.DEFAULT_ORDER, onDismiss = { dismissed = true })

        save()

        assertTrue(dismissed)
    }

    private fun renderDialog(
        order: List<HomeSection>,
        onSave: (List<HomeSection>) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FileExplorerTheme {
                HomeSectionsOrderDialog(
                    order = order,
                    onSave = onSave,
                    onDismiss = onDismiss
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun save() {
        composeTestRule.onNodeWithText(string(R.string.dialog_save)).performClick()
        composeTestRule.waitForIdle()
    }

    /** The sections as they are laid out, top to bottom. */
    private fun renderedOrder(): List<HomeSection> = HomeSection.entries
        .map { section ->
            section to composeTestRule.onNodeWithTag(homeSectionRowTag(section))
                .fetchSemanticsNode().positionInRoot.y
        }
        .sortedBy { (_, y) -> y }
        .map { (section, _) -> section }

    /**
     * Drags [section]'s handle far enough to move it [rows] places, negative for upwards.
     *
     * Travels half a row past the target slot. A swap fires when the dragged row's centre reaches
     * its neighbour's — a full row away — and the gesture spends its touch slop before the first
     * delta is reported, so landing exactly on the boundary would make the outcome turn on the
     * device's slop. The row height is read from a rendered row rather than hard-coded, keeping the
     * gesture correct at any density.
     *
     * Delivered as several moves, not one: a single move would run every swap inside one `onDrag`
     * call, leaving the state carried *between* drag events — which is what a real finger exercises
     * on every frame — never read back.
     */
    private fun dragHandle(section: HomeSection, rows: Int) {
        val rowHeight = composeTestRule.onNodeWithTag(homeSectionRowTag(section))
            .fetchSemanticsNode().size.height.toFloat()
        val overshoot = if (rows < 0) -0.5f else 0.5f
        val distance = rowHeight * (rows + overshoot)

        composeTestRule.onNodeWithTag(homeSectionHandleTag(section)).performTouchInput {
            down(center)
            repeat(MOVE_EVENTS) { moveBy(Offset(0f, distance / MOVE_EVENTS)) }
            up()
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        /** Move events per drag, so the gesture spans frames the way a real one does. */
        const val MOVE_EVENTS = 8
    }
}
