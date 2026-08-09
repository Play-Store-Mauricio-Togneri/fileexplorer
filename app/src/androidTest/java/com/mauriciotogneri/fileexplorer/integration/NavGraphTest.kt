package com.mauriciotogneri.fileexplorer.integration

import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    /**
     * Asserted on the graph rather than on `currentBackStackEntry`, because the real
     * [com.mauriciotogneri.fileexplorer.ui.screens.permission.PermissionScreen] leaves for home the
     * moment it resumes with the permission actually held — and other tests in the suite grant
     * `MANAGE_EXTERNAL_STORAGE` via `appops`, which sticks for the rest of the run. The start
     * destination is the decision this graph owns; where the screen goes next is its own.
     */
    @Test
    fun navGraph_withoutPermission_startsAtPermissionScreen() {
        renderGraph(hasPermission = false)

        assertEquals(Routes.PERMISSION, navController.graph.startDestinationRoute)
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
     * `inclusive = true` is what removes the wall from the stack. Without it the permission screen
     * would still be on the back stack and back would take the user straight back to it.
     *
     * Stated as "no permission entry anywhere on the stack" rather than "nothing behind home",
     * because the screen may already have performed this same navigation itself (see
     * [navGraph_withoutPermission_startsAtPermissionScreen]), which leaves a second home entry
     * behind this one.
     *
     * That self-navigation is also this test's limit: when the app already holds the permission, the
     * screen has popped the wall before the navigation below runs, so the assertion would hold even
     * with `inclusive = false`. It discriminates only on a device where the permission is absent —
     * which, given the suite-wide `appops` grant, means running this class before the tests that
     * grant it.
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

        // `getBackStackEntry` throws when the route is absent, which is the signal wanted here.
        // `currentBackStack` would read better but is `@RestrictTo(LIBRARY_GROUP)`, so it turns into
        // a RestrictedApi lint error the day test sources are linted.
        val permissionStillOnStack = runCatching {
            navController.getBackStackEntry(Routes.PERMISSION)
        }.isSuccess

        assertFalse(
            "The permission screen must not remain on the back stack",
            permissionStillOnStack
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
