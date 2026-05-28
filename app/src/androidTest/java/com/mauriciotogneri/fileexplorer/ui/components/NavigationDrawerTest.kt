package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationDrawerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun drawer_opensViaMenuButton() {
        composeTestRule.setContent {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            FileExplorerTheme {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(modifier = Modifier.height(16.dp))
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = stringResource(R.string.drawer_settings)
                                    )
                                },
                                label = { Text(stringResource(R.string.drawer_settings)) },
                                selected = false,
                                onClick = {},
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                ) {
                    HomeSearchBar(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onSearchContainerClick = {},
                        onSearchIconClick = {},
                        showMenuBadge = false
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.menu_open))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.drawer_settings))
            .assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun drawer_displaysSettingsItem() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ModalNavigationDrawer(
                    drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(modifier = Modifier.height(16.dp))
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = stringResource(R.string.drawer_settings)
                                    )
                                },
                                label = { Text(stringResource(R.string.drawer_settings)) },
                                selected = false,
                                onClick = {},
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                ) {}
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_settings))
            .assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun drawer_displaysFeedbackItem() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ModalNavigationDrawer(
                    drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(modifier = Modifier.height(16.dp))
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Feedback,
                                        contentDescription = stringResource(R.string.drawer_feedback)
                                    )
                                },
                                label = { Text(stringResource(R.string.drawer_feedback)) },
                                selected = false,
                                onClick = {},
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                ) {}
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_feedback))
            .assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun drawer_displaysAboutItem() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ModalNavigationDrawer(
                    drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(modifier = Modifier.height(16.dp))
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = stringResource(R.string.drawer_about)
                                    )
                                },
                                label = { Text(stringResource(R.string.drawer_about)) },
                                selected = false,
                                onClick = {},
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                ) {}
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_about))
            .assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun drawer_settingsClick_triggersCallback() {
        var settingsClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ModalNavigationDrawer(
                    drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(modifier = Modifier.height(16.dp))
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = stringResource(R.string.drawer_settings)
                                    )
                                },
                                label = { Text(stringResource(R.string.drawer_settings)) },
                                selected = false,
                                onClick = { settingsClicked = true },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                ) {}
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_settings))
            .performClick()

        assertTrue("Settings item click should trigger callback", settingsClicked)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun drawer_feedbackClick_triggersCallback() {
        var feedbackClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ModalNavigationDrawer(
                    drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(modifier = Modifier.height(16.dp))
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Feedback,
                                        contentDescription = stringResource(R.string.drawer_feedback)
                                    )
                                },
                                label = { Text(stringResource(R.string.drawer_feedback)) },
                                selected = false,
                                onClick = { feedbackClicked = true },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                ) {}
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_feedback))
            .performClick()

        assertTrue("Feedback item click should trigger callback", feedbackClicked)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun drawer_aboutClick_triggersCallback() {
        var aboutClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ModalNavigationDrawer(
                    drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(modifier = Modifier.height(16.dp))
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = stringResource(R.string.drawer_about)
                                    )
                                },
                                label = { Text(stringResource(R.string.drawer_about)) },
                                selected = false,
                                onClick = { aboutClicked = true },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                ) {}
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_about))
            .performClick()

        assertTrue("About item click should trigger callback", aboutClicked)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun drawer_displaysAllNavigationItems() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ModalNavigationDrawer(
                    drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(modifier = Modifier.height(16.dp))
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = stringResource(R.string.drawer_settings)
                                    )
                                },
                                label = { Text(stringResource(R.string.drawer_settings)) },
                                selected = false,
                                onClick = {},
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Feedback,
                                        contentDescription = stringResource(R.string.drawer_feedback)
                                    )
                                },
                                label = { Text(stringResource(R.string.drawer_feedback)) },
                                selected = false,
                                onClick = {},
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = stringResource(R.string.drawer_about)
                                    )
                                },
                                label = { Text(stringResource(R.string.drawer_about)) },
                                selected = false,
                                onClick = {},
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                ) {}
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_settings))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_feedback))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_about))
            .assertIsDisplayed()
    }
}
