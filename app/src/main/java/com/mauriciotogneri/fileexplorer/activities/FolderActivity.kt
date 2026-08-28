package com.mauriciotogneri.fileexplorer.activities

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mauriciotogneri.fileexplorer.ui.navigation.InstantEnter
import com.mauriciotogneri.fileexplorer.ui.navigation.InstantExit
import com.mauriciotogneri.fileexplorer.ui.navigation.Routes
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.screens.main.MainViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager

/**
 * Hosts the folder-browsing UI (and its embedded destination picker) as a standalone Activity so it
 * can be launched from anywhere via [createIntent] (home location/storage cards, recent files,
 * search results, ...).
 *
 * Folder-to-folder navigation runs through an internal [NavHost] so the existing back-stack and
 * breadcrumb behavior is preserved; pressing back at the launch folder finishes the Activity.
 */
class FolderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyNoTransition()

        val path = intent.getStringExtra(EXTRA_PATH) ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE)
        val rootPath = intent.getStringExtra(EXTRA_ROOT_PATH)
        val rootDisplayName = intent.getStringExtra(EXTRA_ROOT_DISPLAY_NAME)

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory())
            val themeMode by viewModel.themeMode.collectAsState(initial = ThemeManager.currentTheme)

            FileExplorerTheme(themeMode = themeMode) {
                FolderNavHost(
                    path = path,
                    title = title,
                    rootPath = rootPath,
                    rootDisplayName = rootDisplayName,
                    onFinish = { finish() }
                )
            }
        }
    }

    /**
     * Suppresses the open/close animation to match the in-app folder navigation it replaces. On
     * Android 14+ both transitions are registered up front so they apply regardless of how the
     * Activity finishes — including a system back at the launch folder, which finishes via the back
     * dispatcher rather than [finish].
     */
    private fun applyNoTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val EXTRA_PATH = "extra_path"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ROOT_PATH = "extra_root_path"
        private const val EXTRA_ROOT_DISPLAY_NAME = "extra_root_display_name"

        fun createIntent(
            context: Context,
            path: String,
            title: String? = null,
            rootPath: String? = null,
            rootDisplayName: String? = null
        ): Intent {
            return Intent(context, FolderActivity::class.java).apply {
                putExtra(EXTRA_PATH, path)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ROOT_PATH, rootPath)
                putExtra(EXTRA_ROOT_DISPLAY_NAME, rootDisplayName)
            }
        }
    }
}

@Composable
private fun FolderNavHost(
    path: String,
    title: String?,
    rootPath: String?,
    rootDisplayName: String?,
    onFinish: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.folder(path, title, rootPath, rootDisplayName),
        enterTransition = { InstantEnter },
        exitTransition = { InstantExit },
        popEnterTransition = { InstantEnter },
        popExitTransition = { InstantExit },
        // Since navigation-compose 2.10 a predictive back gesture runs these instead of
        // popEnter/popExit, and their defaults scale the outgoing screen down to 0.7f.
        predictivePopEnterTransition = { InstantEnter },
        predictivePopExitTransition = { InstantExit }
    ) {
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
            // Navigation Compose already URL-decodes arguments once (via Uri.decode);
            // values built with Uri.encode in Routes.folder() arrive fully decoded here.
            val folderPath = backStackEntry.arguments?.getString("path") ?: ""
            val folderTitle = backStackEntry.arguments?.getString("title")
            val folderRootPath = backStackEntry.arguments?.getString("rootPath")
            val folderRootDisplayName = backStackEntry.arguments?.getString("rootDisplayName")
            FolderScreen(
                path = folderPath,
                title = folderTitle,
                rootPath = folderRootPath,
                rootDisplayName = folderRootDisplayName,
                onNavigateToFolder = { targetPath ->
                    // Every entry in this stack is pushed from here and keeps the title, rootPath
                    // and rootDisplayName it was launched with, so a filled route identifies an
                    // entry by its path alone.
                    val targetRoute =
                        Routes.folder(targetPath, folderTitle, folderRootPath, folderRootDisplayName)
                    val isAncestor = targetPath != folderPath &&
                        (folderPath.startsWith("$targetPath/") || targetPath == "/")
                    // An ancestor is not necessarily on the back stack: the trail is trimmed to
                    // rootPath, which the startup-folder setting places above the folder the
                    // Activity launched at, so it renders ancestors that were never pushed.
                    // Deriving a pop count from path depth pops more entries than exist and leaves
                    // the stack empty, while the NavHost goes on composing the entry it just popped
                    // — held at CREATED, so the folder freezes on its last content and never
                    // updates again. Ask the back stack instead, and open the folder forward when
                    // it has no entry for it.
                    if (isAncestor && navController.hasEntryFor(targetRoute)) {
                        navController.popBackStack(targetRoute, inclusive = false)
                    } else {
                        navController.navigate(targetRoute)
                    }
                },
                onNavigateBack = {
                    // Pop within the folder stack; finish the Activity when at the launch folder
                    if (!navController.popBackStack()) onFinish()
                }
            )
        }
    }
}

/**
 * Whether [route] is on this controller's back stack.
 *
 * Asked through [NavHostController.getBackStackEntry], which throws when the route is absent — the
 * signal wanted here. The alternatives are worse: `currentBackStack` is `@RestrictTo`, and probing
 * with `popBackStack(route, inclusive = false)` logs the route it could not find, which for this
 * screen is the path of a folder the user is browsing.
 */
private fun NavHostController.hasEntryFor(route: String): Boolean =
    runCatching { getBackStackEntry(route) }.isSuccess
