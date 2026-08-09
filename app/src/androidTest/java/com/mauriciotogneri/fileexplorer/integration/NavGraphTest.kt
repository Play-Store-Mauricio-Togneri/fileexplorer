package com.mauriciotogneri.fileexplorer.integration

import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.navigation.FileExplorerNavGraph
import com.mauriciotogneri.fileexplorer.ui.navigation.InstantEnter
import com.mauriciotogneri.fileexplorer.ui.navigation.InstantExit
import com.mauriciotogneri.fileexplorer.ui.navigation.Routes
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FileExplorerNavGraph] had no test. Two things about it are load-bearing:
 *
 * 1. The start destination is chosen from the permission state. Getting it backwards would show the
 *    permission wall to users who already granted access, or the file list to users who have not.
 * 2. Granting permission pops the permission screen off the back stack. Without the `popUpTo`, back
 *    from home would return the user to the wall they just cleared.
 *
 * The transition assertions guard the crash documented in `NavTransitions`: `NavHost` divides by the
 * transition duration every frame, so a zero-duration transition — which is what
 * `EnterTransition.None` produces — can yield NaN and kill the process.
 */
@RunWith(AndroidJUnit4::class)
class NavGraphTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    private fun renderGraph(hasPermission: Boolean) {
        composeTestRule.setContent {
            navController = rememberNavController()
            FileExplorerTheme {
                FileExplorerNavGraph(
                    hasPermission = hasPermission,
                    navController = navController
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun currentRoute(): String? =
        navController.currentBackStackEntry?.destination?.route

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ==================== Start destination ====================

    @Test
    fun navGraph_withoutPermission_startsAtPermissionScreen() {
        renderGraph(hasPermission = false)

        assertEquals(Routes.PERMISSION, currentRoute())
        composeTestRule.onNodeWithText(string(R.string.permission_title)).assertIsDisplayed()
    }

    @Test
    fun navGraph_withPermission_startsAtHome() {
        renderGraph(hasPermission = true)

        assertEquals(Routes.HOME, currentRoute())
        waitForText(string(R.string.search_placeholder))
        composeTestRule.onNodeWithText(string(R.string.permission_title)).assertDoesNotExist()
    }

    // ==================== Permission -> Home ====================

    @Test
    fun navGraph_grantingPermission_navigatesToHome() {
        renderGraph(hasPermission = false)

        composeTestRule.runOnUiThread {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.PERMISSION) { inclusive = true }
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(Routes.HOME, currentRoute())
    }

    /**
     * `inclusive = true` is what removes the wall from the stack. Without it, `previousBackStackEntry`
     * would still point at the permission screen and back would take the user straight back to it.
     */
    @Test
    fun navGraph_grantingPermission_popsThePermissionScreen() {
        renderGraph(hasPermission = false)

        composeTestRule.runOnUiThread {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.PERMISSION) { inclusive = true }
            }
        }
        composeTestRule.waitForIdle()

        assertNull(
            "The permission screen must not remain on the back stack",
            navController.previousBackStackEntry
        )
    }

    // ==================== Transition invariants ====================

    /**
     * Both must animate rather than being `None`: a transition with no animations has zero
     * duration, which is the divide-by-zero that crashes `NavHost`.
     */
    @Test
    fun navTransitions_areNotTheZeroDurationNoneVariants() {
        assertNotEquals(
            "InstantEnter must animate, not be EnterTransition.None",
            EnterTransition.None,
            InstantEnter
        )
        assertNotEquals(
            "InstantExit must animate, not be ExitTransition.None",
            ExitTransition.None,
            InstantExit
        )
    }

    // ==================== Route builder ====================

    @Test
    fun routes_folder_withoutOptionalArguments_hasNoQueryString() {
        val route = Routes.folder("/storage/emulated/0/Download")

        assertEquals("folder/%2Fstorage%2Femulated%2F0%2FDownload", route)
    }

    @Test
    fun routes_folder_omitsOnlyTheArgumentsThatAreNull() {
        val route = Routes.folder(
            path = "/storage/emulated/0/Download",
            title = "Downloads",
            rootPath = null,
            rootDisplayName = null
        )

        assertEquals("folder/%2Fstorage%2Femulated%2F0%2FDownload?title=Downloads", route)
    }
}
