package com.mauriciotogneri.fileexplorer.testutil

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.toSize
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import java.io.File

private typealias ActivityRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/**
 * Drives the real [FolderScreen] over a temp directory.
 *
 * The folder tests used to each declare a private `@Composable` that re-implemented the list, the
 * selection state machine and the overflow menu; those copies asserted nothing about production.
 * This robot renders the real screen instead and centralises the two awkward bits of addressing it:
 * the toolbar overflow shares its content description with every row's menu button, and row order
 * can only be read from vertical bounds.
 */
class FolderScreenRobot(
    private val rule: ActivityRule,
    private val directory: File
) {
    private val activity get() = rule.activity

    fun string(@StringRes id: Int): String = activity.getString(id)

    fun plural(@StringRes id: Int, quantity: Int): String =
        activity.resources.getQuantityString(id, quantity, quantity)

    /** Renders the real screen rooted at [directory] and waits for the first load to settle. */
    fun render(
        onNavigateToFolder: (String) -> Unit = {},
        onNavigateBack: () -> Unit = {}
    ): FolderScreenRobot {
        rule.setContent {
            FileExplorerTheme {
                FolderScreen(
                    path = directory.absolutePath,
                    onNavigateToFolder = onNavigateToFolder,
                    onNavigateBack = onNavigateBack
                )
            }
        }
        rule.waitForIdle()
        return this
    }

    fun waitForText(text: String, timeoutMillis: Long = 10_000): FolderScreenRobot {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        return this
    }

    fun waitForTextToDisappear(text: String, timeoutMillis: Long = 10_000): FolderScreenRobot {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
        return this
    }

    fun node(text: String): SemanticsNodeInteraction = rule.onNodeWithText(text)

    fun click(text: String): FolderScreenRobot {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
        return this
    }

    fun longClick(text: String): FolderScreenRobot {
        rule.onNodeWithText(text).performTouchInput { longClick() }
        rule.waitForIdle()
        return this
    }

    /**
     * Taps the toolbar overflow. The row menu buttons carry the same content description, so the
     * toolbar one is identified as the topmost match rather than by index.
     */
    fun openOverflowMenu(): FolderScreenRobot {
        val nodes = rule.onAllNodesWithContentDescription(string(R.string.content_description_more_options))
        val tops = nodes.fetchSemanticsNodes().map { it.boundsInRoot.top }
        val topIndex = tops.indices.minByOrNull { tops[it] } ?: error("No overflow menu node found")
        nodes[topIndex].performClick()
        rule.waitForIdle()
        return this
    }

    /**
     * Opens the bottom sheet for the row carrying [fileName], via that row's own overflow button.
     *
     * The toolbar action and every row's button share one content description, so the right button
     * is found by geometry rather than by index, and two things make that exact: the row's
     * `combinedClickable` merges its descendants, so [fileName] resolves to the whole row node
     * rather than to the name text, and the row centres its overflow button vertically, so that
     * button and no other spans the row's centre. Picking the bottom-most match instead, as this
     * did before, opens the last row's sheet whatever name was asked for.
     */
    fun openRowActions(fileName: String): FolderScreenRobot {
        waitForText(fileName)
        val rowCenter = rule.onNodeWithText(fileName).fetchSemanticsNode().unclippedBounds().center.y
        val nodes = rule.onAllNodesWithContentDescription(string(R.string.content_description_more_options))
        val bounds = nodes.fetchSemanticsNodes().map { it.unclippedBounds() }
        val rowIndex = bounds.indices.firstOrNull { rowCenter in bounds[it].top..bounds[it].bottom }
            ?: error("No overflow menu found on the row for '$fileName'")
        nodes[rowIndex].performClick()
        rule.waitForIdle()
        waitForText(string(R.string.action_select))
        return this
    }

    /** Vertical position of the row carrying [name], for order assertions. */
    fun topOf(name: String): Float =
        rule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top

    fun isTopToBottom(vararg names: String): Boolean =
        names.map { topOf(it) }.zipWithNext().all { (upper, lower) -> upper < lower }

    /**
     * Bounds with the list viewport's clip left out. `boundsInRoot` applies it, which shrinks a
     * half-scrolled row without shrinking the button inside it, moving the row's centre out of it.
     */
    private fun SemanticsNode.unclippedBounds(): Rect = Rect(positionInRoot, size.toSize())
}
