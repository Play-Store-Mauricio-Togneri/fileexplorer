package com.mauriciotogneri.fileexplorer.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mauriciotogneri.fileexplorer.util.AndroidPermissionChecker
import com.mauriciotogneri.fileexplorer.util.PermissionChecker
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeScreen
import com.mauriciotogneri.fileexplorer.ui.screens.permission.PermissionScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val FOLDER = "folder/{path}?title={title}&rootPath={rootPath}&rootDisplayName={rootDisplayName}"

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
    val context = LocalContext.current
    val permissionChecker = remember(context) { AndroidPermissionChecker(context) }
    FileExplorerNavGraph(hasPermission, navController, permissionChecker)
}

// The test controls the permission probe; navigation and lifecycle handling stay in production.
@Composable
internal fun FileExplorerNavGraph(
    hasPermission: Boolean,
    navController: NavHostController,
    permissionChecker: PermissionChecker
) {
    val startDestination = if (hasPermission) Routes.HOME else Routes.PERMISSION

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { InstantEnter },
        exitTransition = { InstantExit },
        popEnterTransition = { InstantEnter },
        popExitTransition = { InstantExit },
        // Since navigation-compose 2.10 a predictive back gesture runs these instead of
        // popEnter/popExit, and their defaults scale the outgoing screen down to 0.7f.
        predictivePopEnterTransition = { InstantEnter },
        predictivePopExitTransition = { InstantExit }
    ) {
        composable(Routes.PERMISSION) {
            PermissionScreen(
                permissionChecker = permissionChecker,
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
    }
}
