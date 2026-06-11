package com.mauriciotogneri.fileexplorer.ui.navigation

import android.net.Uri
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
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeScreen
import com.mauriciotogneri.fileexplorer.ui.screens.permission.PermissionScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val FOLDER = "folder/{path}?title={title}&rootPath={rootPath}&rootDisplayName={rootDisplayName}"
    const val SEARCH = "search?root={root}"
    const val RECENT = "recent"
    const val SETTINGS = "settings"

    fun folder(
        path: String,
        title: String? = null,
        rootPath: String? = null,
        rootDisplayName: String? = null
    ): String {
        val encodedPath = Uri.encode(path)
        val queryParams = mutableListOf<String>()
        if (title != null) {
            queryParams.add("title=${Uri.encode(title)}")
        }
        if (rootPath != null) {
            queryParams.add("rootPath=${Uri.encode(rootPath)}")
        }
        if (rootDisplayName != null) {
            queryParams.add("rootDisplayName=${Uri.encode(rootDisplayName)}")
        }
        return if (queryParams.isNotEmpty()) {
            "folder/$encodedPath?${queryParams.joinToString("&")}"
        } else {
            "folder/$encodedPath"
        }
    }

}

@Composable
fun FileExplorerNavGraph(
    hasPermission: Boolean,
    navController: NavHostController = rememberNavController()
) {
    val startDestination = if (hasPermission) Routes.HOME else Routes.PERMISSION

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Routes.PERMISSION) {
            PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PERMISSION) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen()
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
            // Navigation Compose already URL-decodes arguments once.
            val root = backStackEntry.arguments?.getString("root") ?: ""
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
