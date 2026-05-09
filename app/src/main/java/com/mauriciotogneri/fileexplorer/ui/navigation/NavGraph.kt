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
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Navigation routes for the app.
 */
object Routes {
    const val HOME = "home"
    const val FOLDER = "folder/{path}?title={title}"
    const val SEARCH = "search?root={root}"
    const val RECENT = "recent"
    const val SETTINGS = "settings"

    fun folder(path: String, title: String? = null): String {
        val encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8.toString())
        return if (title != null) {
            val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
            "folder/$encodedPath?title=$encodedTitle"
        } else {
            "folder/$encodedPath"
        }
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
    data class OpenPath(val path: String) : StartMode()
}

@Composable
fun FileExplorerNavGraph(
    startMode: StartMode = StartMode.Normal,
    navController: NavHostController = rememberNavController()
) {
    val startDestination = when (startMode) {
        is StartMode.Normal -> Routes.HOME
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
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToFolder = { path, title ->
                    navController.navigate(Routes.folder(path, title))
                }
            )
        }

        composable(
            route = Routes.FOLDER,
            arguments = listOf(
                navArgument("path") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
            val path = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString())
            val encodedTitle = backStackEntry.arguments?.getString("title")
            val title = encodedTitle?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) }
            FolderScreen(
                path = path,
                title = title,
                onNavigateToFolder = { folderPath ->
                    // Preserve the original title when navigating to subfolders
                    navController.navigate(Routes.folder(folderPath, title))
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
