package com.mauriciotogneri.fileexplorer.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.screens.storage.StorageScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Navigation routes for the app.
 */
object Routes {
    const val STORAGE = "storage"
    const val FOLDER = "folder/{path}"
    const val SEARCH = "search?root={root}"
    const val RECENT = "recent"
    const val SETTINGS = "settings"

    fun folder(path: String): String {
        val encoded = URLEncoder.encode(path, StandardCharsets.UTF_8.toString())
        return "folder/$encoded"
    }

    fun search(root: String): String {
        val encoded = URLEncoder.encode(root, StandardCharsets.UTF_8.toString())
        return "search?root=$encoded"
    }
}

/**
 * Represents how the app was started.
 */
sealed class StartMode {
    data object Normal : StartMode()
    data class Picker(val mimeType: String) : StartMode()
    data class OpenPath(val path: String) : StartMode()
}

@Composable
fun FileExplorerNavGraph(
    startMode: StartMode = StartMode.Normal,
    navController: NavHostController = rememberNavController()
) {
    val startDestination = when (startMode) {
        is StartMode.Normal -> Routes.STORAGE
        is StartMode.Picker -> Routes.STORAGE
        is StartMode.OpenPath -> Routes.folder(startMode.path)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Routes.STORAGE) {
            StorageScreen(
                startMode = startMode,
                onNavigateToFolder = { path ->
                    navController.navigate(Routes.folder(path))
                }
            )
        }

        composable(
            route = Routes.FOLDER,
            arguments = listOf(
                navArgument("path") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
            val path = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString())
            FolderScreen(
                path = path,
                onNavigateToFolder = { folderPath ->
                    navController.navigate(Routes.folder(folderPath))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.SEARCH,
            arguments = listOf(
                navArgument("root") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val encodedRoot = backStackEntry.arguments?.getString("root") ?: ""
            val root = URLDecoder.decode(encodedRoot, StandardCharsets.UTF_8.toString())
            // Placeholder for Phase 9
            SearchScreenPlaceholder(root = root)
        }

        composable(Routes.RECENT) {
            // Placeholder for Phase 10
            RecentScreenPlaceholder()
        }

        composable(Routes.SETTINGS) {
            // Placeholder for Phase 11
            SettingsScreenPlaceholder()
        }
    }
}

@Composable
private fun SearchScreenPlaceholder(root: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Search in: $root\n(Phase 9)",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RecentScreenPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Recent Files\n(Phase 10)",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsScreenPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Settings\n(Phase 11)",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
