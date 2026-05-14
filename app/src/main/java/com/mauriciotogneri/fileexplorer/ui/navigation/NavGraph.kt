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
import com.mauriciotogneri.fileexplorer.ui.screens.permission.PermissionScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Navigation routes for the app.
 */
object Routes {
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val FOLDER = "folder/{path}?title={title}&rootPath={rootPath}"
    const val SEARCH = "search?root={root}"
    const val RECENT = "recent"
    const val SETTINGS = "settings"

    fun folder(path: String, title: String? = null, rootPath: String? = null): String {
        val encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8.toString())
        val queryParams = mutableListOf<String>()
        if (title != null) {
            val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
            queryParams.add("title=$encodedTitle")
        }
        if (rootPath != null) {
            val encodedRootPath = URLEncoder.encode(rootPath, StandardCharsets.UTF_8.toString())
            queryParams.add("rootPath=$encodedRootPath")
        }
        return if (queryParams.isNotEmpty()) {
            "folder/$encodedPath?${queryParams.joinToString("&")}"
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
    hasPermission: Boolean,
    navController: NavHostController = rememberNavController()
) {
    val startDestination = when {
        !hasPermission -> Routes.PERMISSION
        startMode is StartMode.OpenPath -> Routes.folder(startMode.path)
        else -> Routes.HOME
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Routes.PERMISSION) {
            val destinationRoute = when (startMode) {
                is StartMode.OpenPath -> Routes.folder(startMode.path)
                else -> Routes.HOME
            }
            PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(destinationRoute) {
                        popUpTo(Routes.PERMISSION) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToFolder = { path, title, rootPath ->
                    navController.navigate(Routes.folder(path, title, rootPath))
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
                },
                navArgument("rootPath") {
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
            val encodedRootPath = backStackEntry.arguments?.getString("rootPath")
            val rootPath = encodedRootPath?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) }
            FolderScreen(
                path = path,
                title = title,
                rootPath = rootPath,
                onNavigateToFolder = onNavigateToFolder@{ folderPath ->
                    val isAncestor = folderPath != path &&
                        (path.startsWith("$folderPath/") || folderPath == "/")
                    if (isAncestor) {
                        // Target is an ancestor - calculate levels and pop back
                        val currentSegments = path.split("/").filter { it.isNotEmpty() }
                        val targetSegments = folderPath.split("/").filter { it.isNotEmpty() }
                        val levelsBack = currentSegments.size - targetSegments.size
                        // Pop back safely - stop early if back stack is exhausted
                        for (i in 0 until levelsBack) {
                            if (!navController.popBackStack()) return@onNavigateToFolder
                        }
                    } else {
                        // Target is a child or unrelated - navigate forward, preserving title and rootPath
                        navController.navigate(Routes.folder(folderPath, title, rootPath))
                    }
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
