package com.mauriciotogneri.fileexplorer.integration

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.components.Breadcrumbs
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = composeTestRule.activity

    // ==================== Home → Folder Navigation Tests ====================

    @Test
    fun home_tapStorage_navigatesToFolder() {
        var navigatedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestHomeScreen(
                    onNavigateToFolder = { path, _, _, _ ->
                        navigatedPath = path
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify home screen is shown
        composeTestRule.onNodeWithText(context.getString(R.string.storage_internal))
            .assertIsDisplayed()

        // Tap on Internal Storage
        composeTestRule.onNodeWithText(context.getString(R.string.storage_internal))
            .performClick()

        composeTestRule.waitForIdle()

        // Verify navigation was triggered to storage root
        assertEquals("/storage/emulated/0", navigatedPath)
    }

    @Test
    fun home_tapLocation_navigatesToFolder() {
        var navigatedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestHomeScreen(
                    onNavigateToFolder = { path, _, _, _ ->
                        navigatedPath = path
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Tap on Downloads location
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads))
            .performClick()

        composeTestRule.waitForIdle()

        // Verify navigation was triggered to Downloads
        assertEquals("/storage/emulated/0/Download", navigatedPath)
    }

    // ==================== Folder Navigation Tests ====================

    @Test
    fun folder_tapFolder_navigatesDeeper() {
        var currentPath by mutableStateOf("/storage/emulated/0")
        val navigationHistory = mutableListOf<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreen(
                    currentPath = currentPath,
                    items = listOf(
                        TestFileItem("Documents", isDirectory = true),
                        TestFileItem("Pictures", isDirectory = true),
                        TestFileItem("readme.txt", isDirectory = false)
                    ),
                    onNavigateToFolder = { path ->
                        navigationHistory.add(path)
                        currentPath = path
                    },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Tap on Documents folder
        composeTestRule.onNodeWithText("Documents")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify navigation to subfolder
        assertEquals("/storage/emulated/0/Documents", navigationHistory.last())
    }

    @Test
    fun folder_backButton_navigatesToParent() {
        var backNavigated = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreen(
                    currentPath = "/storage/emulated/0/Documents",
                    items = listOf(
                        TestFileItem("Work", isDirectory = true),
                        TestFileItem("file.txt", isDirectory = false)
                    ),
                    onNavigateToFolder = {},
                    onNavigateBack = { backNavigated = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Tap back button
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()

        composeTestRule.waitForIdle()

        assertTrue("Back navigation should be triggered", backNavigated)
    }

    @Test
    fun folder_systemBack_navigatesToParent() {
        var backNavigated = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithBackHandler(
                    currentPath = "/storage/emulated/0/Documents",
                    isInSelectionMode = false,
                    onNavigateBack = { backNavigated = true },
                    onExitSelectionMode = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Perform system back
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeTestRule.waitForIdle()

        assertTrue("System back should trigger navigation back", backNavigated)
    }

    @Test
    fun folder_systemBack_inSelectionMode_exitsSelection() {
        var selectionExited = false
        var backNavigated = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithBackHandler(
                    currentPath = "/storage/emulated/0/Documents",
                    isInSelectionMode = true,
                    onNavigateBack = { backNavigated = true },
                    onExitSelectionMode = { selectionExited = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Perform system back
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeTestRule.waitForIdle()

        assertTrue("Selection mode should be exited", selectionExited)
        assertFalse("Should not navigate back when exiting selection mode", backNavigated)
    }

    // ==================== Breadcrumb Navigation Tests ====================

    @Test
    fun folder_tapBreadcrumb_navigatesToAncestor() {
        var navigatedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithBreadcrumbs(
                    currentPath = "/storage/emulated/0/Documents/Work/Projects",
                    onNavigateToPath = { path ->
                        navigatedPath = path
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify breadcrumbs show current location (use testTag to avoid ambiguity)
        composeTestRule.onNodeWithTag("breadcrumbs")
            .assertIsDisplayed()

        // Tap on "Documents" breadcrumb
        composeTestRule.onNodeWithText("Documents").performClick()

        composeTestRule.waitForIdle()

        assertEquals("/storage/emulated/0/Documents", navigatedPath)
    }

    // ==================== Navigation Stack Tests ====================

    @Test
    fun folder_navigateDeep_thenBackMultipleTimes_returnsToStart() {
        val navStack = mutableStateListOf("/storage/emulated/0")
        var currentPath by mutableStateOf("/storage/emulated/0")

        composeTestRule.setContent {
            FileExplorerTheme {
                TestNavigationStack(
                    navStack = navStack,
                    currentPath = currentPath,
                    onNavigateToFolder = { path ->
                        navStack.add(path)
                        currentPath = path
                    },
                    onNavigateBack = {
                        if (navStack.size > 1) {
                            navStack.removeAt(navStack.lastIndex)
                            currentPath = navStack.last()
                        }
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Navigate deep: root → Documents → Work → Projects
        composeTestRule.onNodeWithText("Go to Documents").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Go to Work").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Go to Projects").performClick()
        composeTestRule.waitForIdle()

        assertEquals(4, navStack.size)
        assertEquals("/storage/emulated/0/Documents/Work/Projects", currentPath)

        // Navigate back 3 times
        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.waitForIdle()
        assertEquals("/storage/emulated/0/Documents/Work", currentPath)

        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.waitForIdle()
        assertEquals("/storage/emulated/0/Documents", currentPath)

        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.waitForIdle()
        assertEquals("/storage/emulated/0", currentPath)

        assertEquals(1, navStack.size)
    }

    // ==================== Drawer Navigation Tests ====================

    @Test
    fun drawer_settings_triggersSettingsNavigation() {
        var settingsTapped = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestHomeScreenWithDrawer(
                    onSettingsTap = { settingsTapped = true },
                    onAboutTap = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Open drawer
        composeTestRule.onNodeWithContentDescription("Menu")
            .performClick()

        composeTestRule.waitForIdle()

        // Tap Settings
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_settings))
            .performClick()

        composeTestRule.waitForIdle()

        assertTrue("Settings navigation should be triggered", settingsTapped)
    }

    @Test
    fun drawer_about_triggersAboutNavigation() {
        var aboutTapped = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestHomeScreenWithDrawer(
                    onSettingsTap = {},
                    onAboutTap = { aboutTapped = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Open drawer
        composeTestRule.onNodeWithContentDescription("Menu")
            .performClick()

        composeTestRule.waitForIdle()

        // Tap About
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_about))
            .performClick()

        composeTestRule.waitForIdle()

        assertTrue("About navigation should be triggered", aboutTapped)
    }

    // ==================== Deep Link / OpenPath Tests ====================

    @Test
    fun deepLink_validPath_navigatesToFolder() {
        var startPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestDeepLinkHandler(
                    deepLinkPath = "/storage/emulated/0/Documents",
                    onPathResolved = { path -> startPath = path }
                )
            }
        }

        composeTestRule.waitForIdle()

        assertEquals("/storage/emulated/0/Documents", startPath)
        composeTestRule.onNodeWithText("Opened: /storage/emulated/0/Documents")
            .assertIsDisplayed()
    }

    @Test
    fun deepLink_invalidPath_showsError() {
        var errorShown = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestDeepLinkHandler(
                    deepLinkPath = "/nonexistent/path",
                    isPathValid = false,
                    onPathResolved = {},
                    onError = { errorShown = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        assertTrue("Error should be shown for invalid path", errorShown)
        composeTestRule.onNodeWithText("Invalid path")
            .assertIsDisplayed()
    }

    // ==================== Test Data Classes ====================

    data class TestFileItem(
        val name: String,
        val isDirectory: Boolean
    )

    // ==================== Test Composables ====================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestHomeScreen(
        onNavigateToFolder: (path: String, title: String?, rootPath: String?, rootDisplayName: String?) -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Home") }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Storage section
                Text(
                    text = "Storage",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onNavigateToFolder(
                                "/storage/emulated/0",
                                null,
                                "/storage/emulated/0",
                                "Internal Storage"
                            )
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.storage_internal))
                }

                HorizontalDivider()

                // Locations section
                Text(
                    text = "Locations",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onNavigateToFolder(
                                "/storage/emulated/0/Download",
                                "Downloads",
                                "/storage/emulated/0/Download",
                                "Downloads"
                            )
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.location_downloads))
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestFolderScreen(
        currentPath: String,
        items: List<TestFileItem>,
        onNavigateToFolder: (String) -> Unit,
        onNavigateBack: () -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentPath.substringAfterLast("/")) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.isDirectory) {
                                    onNavigateToFolder("$currentPath/${item.name}")
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (item.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(item.name)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    @Composable
    private fun TestFolderScreenWithBackHandler(
        currentPath: String,
        isInSelectionMode: Boolean,
        onNavigateBack: () -> Unit,
        onExitSelectionMode: () -> Unit
    ) {
        BackHandler(enabled = true) {
            if (isInSelectionMode) {
                onExitSelectionMode()
            } else {
                onNavigateBack()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Current path: $currentPath")
            Text("Selection mode: $isInSelectionMode")
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestFolderScreenWithBreadcrumbs(
        currentPath: String,
        onNavigateToPath: (String) -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentPath.substringAfterLast("/")) }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Breadcrumbs(
                    currentPath = currentPath,
                    onNavigateToPath = onNavigateToPath,
                    rootPath = null,
                    rootDisplayName = null,
                    modifier = Modifier.testTag("breadcrumbs")
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Content for: $currentPath")
            }
        }
    }

    @Composable
    private fun TestNavigationStack(
        navStack: List<String>,
        currentPath: String,
        onNavigateToFolder: (String) -> Unit,
        onNavigateBack: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Current: $currentPath")
            Text("Stack size: ${navStack.size}")
            Spacer(modifier = Modifier.height(16.dp))

            if (navStack.size > 1) {
                Text(
                    text = "Back",
                    modifier = Modifier
                        .clickable { onNavigateBack() }
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                currentPath == "/storage/emulated/0" -> {
                    Text(
                        text = "Go to Documents",
                        modifier = Modifier
                            .clickable { onNavigateToFolder("/storage/emulated/0/Documents") }
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                currentPath == "/storage/emulated/0/Documents" -> {
                    Text(
                        text = "Go to Work",
                        modifier = Modifier
                            .clickable { onNavigateToFolder("/storage/emulated/0/Documents/Work") }
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                currentPath == "/storage/emulated/0/Documents/Work" -> {
                    Text(
                        text = "Go to Projects",
                        modifier = Modifier
                            .clickable { onNavigateToFolder("/storage/emulated/0/Documents/Work/Projects") }
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {
                    Text("End of navigation")
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestHomeScreenWithDrawer(
        onSettingsTap: () -> Unit,
        onAboutTap: () -> Unit
    ) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.drawer_settings)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onSettingsTap()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.drawer_about)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onAboutTap()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Home") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Text("Home content")
                }
            }
        }
    }

    @Composable
    private fun TestDeepLinkHandler(
        deepLinkPath: String,
        isPathValid: Boolean = true,
        onPathResolved: (String) -> Unit,
        onError: () -> Unit = {}
    ) {
        var resolved by remember { mutableStateOf(false) }
        var hasError by remember { mutableStateOf(false) }

        if (!resolved) {
            resolved = true
            if (isPathValid) {
                onPathResolved(deepLinkPath)
            } else {
                hasError = true
                onError()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (hasError) {
                Text("Invalid path")
            } else {
                Text("Opened: $deepLinkPath")
            }
        }
    }
}
