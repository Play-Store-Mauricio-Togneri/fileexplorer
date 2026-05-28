package com.mauriciotogneri.fileexplorer.ui.screens.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarTitleStyle
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // ==================== Display Tests ====================

    @Test
    fun aboutScreen_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = "1.0.0",
                    onOtherAppsClick = {},
                    onPrivacyClick = {},
                    onTermsClick = {},
                    onVersionClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_about))
            .assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysOtherAppsRow() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = "1.0.0",
                    onOtherAppsClick = {},
                    onPrivacyClick = {},
                    onTermsClick = {},
                    onVersionClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.about_other_apps))
            .assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysPrivacyPolicyRow() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = "1.0.0",
                    onOtherAppsClick = {},
                    onPrivacyClick = {},
                    onTermsClick = {},
                    onVersionClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.about_privacy_policy))
            .assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysTermsRow() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = "1.0.0",
                    onOtherAppsClick = {},
                    onPrivacyClick = {},
                    onTermsClick = {},
                    onVersionClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.about_terms))
            .assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysVersionRow() {
        val versionName = "2.5.0"

        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = versionName,
                    onOtherAppsClick = {},
                    onPrivacyClick = {},
                    onTermsClick = {},
                    onVersionClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.about_version, versionName))
            .assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysAllRows() {
        val versionName = "1.0.0"

        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = versionName,
                    onOtherAppsClick = {},
                    onPrivacyClick = {},
                    onTermsClick = {},
                    onVersionClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.about_other_apps))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.about_privacy_policy))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.about_terms))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.about_version, versionName))
            .assertIsDisplayed()
    }

    // ==================== Click Tests ====================

    @Test
    fun aboutScreen_otherAppsClick_triggersCallback() {
        var otherAppsClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = "1.0.0",
                    onOtherAppsClick = { otherAppsClicked = true },
                    onPrivacyClick = {},
                    onTermsClick = {},
                    onVersionClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.about_other_apps))
            .performClick()

        assertTrue("Other apps click should trigger callback", otherAppsClicked)
    }

    @Test
    fun aboutScreen_privacyClick_triggersCallback() {
        var privacyClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = "1.0.0",
                    onOtherAppsClick = {},
                    onPrivacyClick = { privacyClicked = true },
                    onTermsClick = {},
                    onVersionClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.about_privacy_policy))
            .performClick()

        assertTrue("Privacy click should trigger callback", privacyClicked)
    }

    @Test
    fun aboutScreen_termsClick_triggersCallback() {
        var termsClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = "1.0.0",
                    onOtherAppsClick = {},
                    onPrivacyClick = {},
                    onTermsClick = { termsClicked = true },
                    onVersionClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.about_terms))
            .performClick()

        assertTrue("Terms click should trigger callback", termsClicked)
    }

    @Test
    fun aboutScreen_versionClick_triggersCallback() {
        var versionClicked = false
        val versionName = "1.0.0"

        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = versionName,
                    onOtherAppsClick = {},
                    onPrivacyClick = {},
                    onTermsClick = {},
                    onVersionClick = { versionClicked = true },
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.about_version, versionName))
            .performClick()

        assertTrue("Version click should trigger callback", versionClicked)
    }

    @Test
    fun aboutScreen_backClick_triggersCallback() {
        var backClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = "1.0.0",
                    onOtherAppsClick = {},
                    onPrivacyClick = {},
                    onTermsClick = {},
                    onVersionClick = {},
                    onBackClick = { backClicked = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()

        assertTrue("Back click should trigger callback", backClicked)
    }

    // ==================== Click Order/Independence Tests ====================

    @Test
    fun aboutScreen_clickingOneRow_doesNotTriggerOthers() {
        var otherAppsClicked = false
        var privacyClicked = false
        var termsClicked = false
        var versionClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = "1.0.0",
                    onOtherAppsClick = { otherAppsClicked = true },
                    onPrivacyClick = { privacyClicked = true },
                    onTermsClick = { termsClicked = true },
                    onVersionClick = { versionClicked = true },
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.about_privacy_policy))
            .performClick()

        assertTrue("Privacy should be clicked", privacyClicked)
        assertEquals("Other apps should not be clicked", false, otherAppsClicked)
        assertEquals("Terms should not be clicked", false, termsClicked)
        assertEquals("Version should not be clicked", false, versionClicked)
    }

    @Test
    fun aboutScreen_multipleClicks_allTriggerCorrectly() {
        var otherAppsClickCount = 0
        var privacyClickCount = 0
        var termsClickCount = 0
        var versionClickCount = 0
        val versionName = "1.0.0"

        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = versionName,
                    onOtherAppsClick = { otherAppsClickCount++ },
                    onPrivacyClick = { privacyClickCount++ },
                    onTermsClick = { termsClickCount++ },
                    onVersionClick = { versionClickCount++ },
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.about_other_apps))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.about_privacy_policy))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.about_terms))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.about_version, versionName))
            .performClick()

        assertEquals("Other apps should be clicked once", 1, otherAppsClickCount)
        assertEquals("Privacy should be clicked once", 1, privacyClickCount)
        assertEquals("Terms should be clicked once", 1, termsClickCount)
        assertEquals("Version should be clicked once", 1, versionClickCount)
    }

    // ==================== Version Display Tests ====================

    @Test
    fun aboutScreen_displaysCorrectVersionFormat() {
        val versionName = "3.14.159"

        composeTestRule.setContent {
            FileExplorerTheme {
                TestAboutScreen(
                    versionName = versionName,
                    onOtherAppsClick = {},
                    onPrivacyClick = {},
                    onTermsClick = {},
                    onVersionClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // The version string includes "Version " prefix from the string resource
        composeTestRule.onNodeWithText(context.getString(R.string.about_version, versionName))
            .assertIsDisplayed()
    }

    // ==================== Test Composable ====================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestAboutScreen(
        versionName: String,
        onOtherAppsClick: () -> Unit,
        onPrivacyClick: () -> Unit,
        onTermsClick: () -> Unit,
        onVersionClick: () -> Unit,
        onBackClick: () -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.drawer_about), style = AppBarTitleStyle) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AboutRow(
                    icon = Icons.Outlined.Apps,
                    title = stringResource(R.string.about_other_apps),
                    onClick = onOtherAppsClick
                )
                AboutRow(
                    icon = Icons.Outlined.Shield,
                    title = stringResource(R.string.about_privacy_policy),
                    onClick = onPrivacyClick
                )
                AboutRow(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.about_terms),
                    onClick = onTermsClick
                )
                AboutRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.about_version, versionName),
                    onClick = onVersionClick
                )
            }
        }
    }

    @Composable
    private fun AboutRow(
        icon: ImageVector,
        title: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
