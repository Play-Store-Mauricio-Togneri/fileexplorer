package com.mauriciotogneri.fileexplorer.ui.screens.about

import android.app.Activity
import android.app.Instrumentation
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.BuildConfig
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.AboutRow
import com.mauriciotogneri.fileexplorer.activities.AboutScreen
import com.mauriciotogneri.fileexplorer.activities.LegalActivity
import com.mauriciotogneri.fileexplorer.activities.OtherAppsActivity
import com.mauriciotogneri.fileexplorer.testutil.clickableWithText
import com.mauriciotogneri.fileexplorer.testutil.hasBadgeDot
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info

/**
 * Exercises the real `AboutScreen` from `AboutActivity`, exposed as an `internal` test seam.
 *
 * The previous version of this file asserted against a private replica that had no
 * `showOtherAppsBadge` parameter and no `BadgeDot`, so the badge and its dismiss-on-tap shipped
 * untested. Row taps are verified with Espresso-Intents (`intending(anyIntent())` stubs the launch)
 * rather than through a callback the replica invented, so the real navigation is what is asserted.
 */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    @Before
    fun setUp() {
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    private fun renderAbout(
        showOtherAppsBadge: Boolean = false,
        onOtherAppsBadgeDismiss: () -> Unit = {},
        onBackClick: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FileExplorerTheme {
                AboutScreen(
                    showOtherAppsBadge = showOtherAppsBadge,
                    onOtherAppsBadgeDismiss = onOtherAppsBadgeDismiss,
                    onBackClick = onBackClick
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // ==================== Display ====================

    @Test
    fun aboutScreen_displaysTitle() {
        renderAbout()

        composeTestRule.onNodeWithText(string(R.string.drawer_about)).assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysAllRows() {
        renderAbout()

        composeTestRule.onNodeWithText(string(R.string.about_other_apps)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.about_privacy_policy)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.about_terms)).assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysBuildVersion() {
        renderAbout()

        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.about_version, BuildConfig.VERSION_NAME)
            )
            .assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysBackButton() {
        renderAbout()

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).assertIsDisplayed()
    }

    @Test
    fun aboutScreen_backButton_triggersCallback() {
        var backClicked = false
        renderAbout(onBackClick = { backClicked = true })

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()

        assertTrue("Back should invoke onBackClick", backClicked)
    }

    // ==================== Row navigation ====================

    @Test
    fun otherAppsRow_launchesOtherAppsActivity() {
        renderAbout()

        composeTestRule.onNodeWithText(string(R.string.about_other_apps)).performClick()

        intended(hasComponent(OtherAppsActivity::class.java.name))
    }

    @Test
    fun privacyRow_launchesLegalActivity() {
        renderAbout()

        composeTestRule.onNodeWithText(string(R.string.about_privacy_policy)).performClick()

        intended(hasComponent(LegalActivity::class.java.name))
    }

    @Test
    fun termsRow_launchesLegalActivity() {
        renderAbout()

        composeTestRule.onNodeWithText(string(R.string.about_terms)).performClick()

        intended(hasComponent(LegalActivity::class.java.name))
    }

    // ==================== Other-apps badge ====================

    @Test
    fun otherAppsRow_whenBadgeRequested_showsBadgeDot() {
        renderAbout(showOtherAppsBadge = true)

        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertExists()
    }

    @Test
    fun otherAppsRow_withoutBadge_showsNoBadgeDot() {
        renderAbout(showOtherAppsBadge = false)

        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * Tapping the row is what marks the badge seen. Without this the dot would reappear on every
     * visit — the exact regression the replica could not catch, since it had no badge at all.
     */
    @Test
    fun otherAppsRow_tap_dismissesBadge() {
        var dismissed = false
        renderAbout(showOtherAppsBadge = true, onOtherAppsBadgeDismiss = { dismissed = true })

        composeTestRule.onNodeWithText(string(R.string.about_other_apps)).performClick()

        assertTrue("Tapping Other apps should dismiss its badge", dismissed)
    }

    @Test
    fun otherRows_tap_doNotDismissOtherAppsBadge() {
        var dismissed = false
        renderAbout(showOtherAppsBadge = true, onOtherAppsBadgeDismiss = { dismissed = true })

        composeTestRule.onNodeWithText(string(R.string.about_privacy_policy)).performClick()

        assertFalse("Only the Other apps row owns that badge", dismissed)
    }

    // ==================== AboutRow ====================

    @Test
    fun aboutRow_withValue_displaysValue() {
        composeTestRule.setContent {
            FileExplorerTheme {
                AboutRow(
                    icon = Icons.Outlined.Info,
                    title = string(R.string.about_terms),
                    value = "1.2.3",
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1.2.3").assertIsDisplayed()
    }

    /**
     * `AboutRow` only attaches `Modifier.clickable` when `onClick` is non-null, so a decorative row
     * must carry no click semantics at all — asserting on a callback that was never wired would be
     * a tautology.
     */
    @Test
    fun aboutRow_withoutOnClick_hasNoClickAction() {
        composeTestRule.setContent {
            FileExplorerTheme {
                AboutRow(
                    icon = Icons.Outlined.Info,
                    title = string(R.string.about_terms),
                    onClick = null
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(clickableWithText(string(R.string.about_terms))).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.about_terms)).assertIsDisplayed()
    }
}
