package com.mauriciotogneri.fileexplorer.integration

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for folder route argument encoding/decoding.
 *
 * Navigation Compose URL-decodes route arguments exactly once (via Uri.decode).
 * [Routes.folder] encodes values with Uri.encode() to match, and destinations read
 * the already-decoded values directly. A previous implementation decoded a second
 * time with URLDecoder, which crashed with
 * `IllegalArgumentException: URLDecoder: Illegal hex characters in escape (%) pattern`
 * whenever a file/folder name contained a literal '%' (e.g. "%#@"), and silently
 * corrupted names containing spaces or '+'.
 *
 * These tests drive the real [Routes.folder] builder and [Routes.FOLDER] route
 * template through a NavHost and assert each argument round-trips back to its
 * original raw value.
 */
@RunWith(AndroidJUnit4::class)
class FolderRouteEncodingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class CapturedArgs {
        var path: String? = null
        var title: String? = null
        var rootPath: String? = null
        var rootDisplayName: String? = null
    }

    private fun navigateToFolderAndCapture(
        path: String,
        title: String? = null,
        rootPath: String? = null,
        rootDisplayName: String? = null
    ): CapturedArgs {
        val captured = CapturedArgs()
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = "start") {
                composable("start") { Text("start") }
                composable(
                    route = Routes.FOLDER,
                    arguments = listOf(
                        navArgument("path") { type = NavType.StringType },
                        navArgument("title") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("rootPath") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("rootDisplayName") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    captured.path = backStackEntry.arguments?.getString("path")
                    captured.title = backStackEntry.arguments?.getString("title")
                    captured.rootPath = backStackEntry.arguments?.getString("rootPath")
                    captured.rootDisplayName = backStackEntry.arguments?.getString("rootDisplayName")
                    Text("folder")
                }
            }
        }

        composeTestRule.runOnIdle {
            navController.navigate(Routes.folder(path, title, rootPath, rootDisplayName))
        }
        composeTestRule.waitForIdle()

        return captured
    }

    @Test
    fun folderPath_withPercentLiteral_roundTripsWithoutCrashing() {
        // Exactly reproduces the Crashlytics crash input ("%#@").
        val path = "/storage/emulated/0/100%#@"

        val captured = navigateToFolderAndCapture(path)

        assertEquals(path, captured.path)
    }

    @Test
    fun folderPath_withSpace_roundTripsAsSpaceNotPlus() {
        val path = "/storage/emulated/0/My Folder"

        val captured = navigateToFolderAndCapture(path)

        assertEquals(path, captured.path)
    }

    @Test
    fun folderPath_withPlusLiteral_roundTripsAsPlusNotSpace() {
        val path = "/storage/emulated/0/a+b"

        val captured = navigateToFolderAndCapture(path)

        assertEquals(path, captured.path)
    }

    @Test
    fun folderPath_withReservedUriCharacters_roundTrips() {
        val path = "/storage/emulated/0/a#b&c=d?e"

        val captured = navigateToFolderAndCapture(path)

        assertEquals(path, captured.path)
    }

    @Test
    fun folderQueryArgs_withPercentLiteral_roundTripWithoutCrashing() {
        val path = "/storage/emulated/0/Music"
        val title = "50% off"
        val rootPath = "/storage/emulated/0/r%t"
        val rootDisplayName = "Root %#@"

        val captured = navigateToFolderAndCapture(path, title, rootPath, rootDisplayName)

        assertEquals(path, captured.path)
        assertEquals(title, captured.title)
        assertEquals(rootPath, captured.rootPath)
        assertEquals(rootDisplayName, captured.rootDisplayName)
    }
}
