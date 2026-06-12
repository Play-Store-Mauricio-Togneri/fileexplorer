package com.mauriciotogneri.fileexplorer.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
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
    }
}
