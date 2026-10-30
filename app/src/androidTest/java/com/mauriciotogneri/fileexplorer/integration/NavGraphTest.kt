package com.mauriciotogneri.fileexplorer.integration

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.mauriciotogneri.fileexplorer.util.PermissionChecker
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.navigation.FileExplorerNavGraph
import com.mauriciotogneri.fileexplorer.ui.navigation.InstantEnter
import com.mauriciotogneri.fileexplorer.ui.navigation.InstantExit
import com.mauriciotogneri.fileexplorer.ui.navigation.Routes
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
 * The transition test guards the crash documented in `NavTransitions`: `NavHost` divides by the
 * transition duration every frame, so a zero-duration transition can yield NaN and kill the process.
 * It measures the duration Compose actually computes for [InstantEnter] and [InstantExit], because
 * every way of losing it — a zero constant, a spring, a dropped `animationSpec`, `None` — leaves a
 * transition that still compiles and still looks instant.
 */
@RunWith(AndroidJUnit4::class)
class NavGraphTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private var permissionGranted = false
    private val permissionChecker = object : PermissionChecker {
        override fun hasStoragePermission(): Boolean = permissionGranted
    }

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    private fun renderGraph(hasPermission: Boolean) {
        permissionGranted = hasPermission
        composeTestRule.setContent {
            navController = rememberNavController()
            FileExplorerTheme {
                FileExplorerNavGraph(
                    hasPermission = hasPermission,
                    navController = navController,
                    permissionChecker = permissionChecker
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

        assertEquals(Routes.PERMISSION, navController.graph.startDestinationRoute)
        assertEquals(Routes.PERMISSION, currentRoute())
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

        grantPermissionAndResume()

        assertEquals(Routes.HOME, currentRoute())
    }

    // Deleting production's inclusive pop must leave the permission entry and fail this test.
    @Test
    fun navGraph_grantingPermission_popsThePermissionScreen() {
        renderGraph(hasPermission = false)

        grantPermissionAndResume()

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

    private fun grantPermissionAndResume() {
        assertEquals(Routes.PERMISSION, currentRoute())
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeTestRule.runOnUiThread { permissionGranted = true }
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            currentRoute() == Routes.HOME
        }
        composeTestRule.waitForIdle()
    }

    // ==================== Transition invariants ====================

    /**
     * Guards the NaN crash in `NavTransitions`: `NavHost` divides by its transition's total duration
     * on every frame, and the KDoc there puts the safe floor above ~9 ms, below which an interrupted
     * transition can round what remains of it back down to zero.
     *
     * Comparing against `EnterTransition.None` caught only one way to reach zero. Setting
     * `INSTANT_TRANSITION_MS = 0`, swapping the tween for a spring, or dropping `animationSpec` (the
     * default spring lasts 0 ms for 1f -> 1f) all passed it. Each of them reads 0 here.
     *
     * The state is a `SeekableTransitionState`, the type `NavHost` divides by, and it drives an
     * `AnimatedVisibility`: the same enter/exit machinery `NavHost`'s `AnimatedContent` runs for each
     * destination. The enter and exit values are production's; only the host is the test's. The
     * clock is held and every seek is to fraction 0, so nothing runs to completion between a seek
     * and the read that follows it.
     */
    @Test
    fun navTransitions_lastLongerThanTheRoundingThreshold() {
        composeTestRule.mainClock.autoAdvance = false
        val transitionState = SeekableTransitionState(false)
        lateinit var transition: Transition<Boolean>
        lateinit var scope: CoroutineScope

        composeTestRule.setContent {
            transition = rememberTransition(transitionState)
            scope = rememberCoroutineScope()
            // production-transition: hosts production's InstantEnter/InstantExit to measure the
            // duration NavHost divides by; the test declares no enter or exit of its own.
            transition.AnimatedVisibility(
                visible = { it },
                enter = InstantEnter,
                exit = InstantExit
            ) {
                Box(Modifier.size(1.dp))
            }
        }

        settle(scope) { transitionState.seekTo(0f, targetState = true) }
        assertOutlastsTheRoundingThreshold("enter (InstantEnter)", transition)

        settle(scope) { transitionState.snapTo(true) }
        settle(scope) { transitionState.seekTo(0f, targetState = false) }
        assertOutlastsTheRoundingThreshold("exit (InstantExit)", transition)
    }

    /**
     * Runs [action] in the composition's scope, then advances frames until it has finished. A seek
     * suspends until a frame has recomposed its new target, and the enter/exit animations only join
     * the transition when that frame is laid out, which the `waitForIdle` after each frame covers.
     */
    private fun settle(scope: CoroutineScope, action: suspend () -> Unit) {
        lateinit var job: Job
        composeTestRule.runOnUiThread { job = scope.launch { action() } }
        var frames = 0
        do {
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.waitForIdle()
            frames++
        } while (!job.isCompleted && frames < MAX_SETTLE_FRAMES)
        assertTrue("The transition did not settle within $MAX_SETTLE_FRAMES frames", job.isCompleted)
    }

    private fun assertOutlastsTheRoundingThreshold(direction: String, transition: Transition<Boolean>) {
        val durationNanos = composeTestRule.runOnIdle { transition.totalDurationNanos }
        assertTrue(
            "The $direction transition lasts $durationNanos ns; NavHost needs at least " +
                "$MIN_TRANSITION_NANOS ns or a non-monotonic frame divides by zero",
            durationNanos >= MIN_TRANSITION_NANOS
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

    private companion object {
        /** Just above the ~9 ms at which `NavTransitions` says an interrupted transition rounds to 0. */
        const val MIN_TRANSITION_NANOS = 10_000_000L

        const val MAX_SETTLE_FRAMES = 5
    }
}
