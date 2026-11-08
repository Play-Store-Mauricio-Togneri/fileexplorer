package com.mauriciotogneri.fileexplorer.ui.screens.permission

import android.os.Build
import androidx.compose.runtime.saveable.SaverScope
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.util.SettingsPage
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The permission screen's trip to Settings, decided here rather than in the composable so the rule
 * for every Android version can be checked: the device tests run on API 36 only, which never
 * reaches the pre-Android 11 arm.
 */
class SettingsReturnStateTest {

    private val android11 = Build.VERSION_CODES.R
    private val android10 = Build.VERSION_CODES.Q
    private val api36 = 36

    @Before
    fun setUp() {
        mockkObject(AnalyticsTracker)
        every { AnalyticsTracker.trackPermissionReturnedWithoutGranting(any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackPermissionGrantedOnReturn(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(AnalyticsTracker)
    }

    @Test
    fun `a resume with no trip to Settings is not a return`() {
        val state = SettingsReturnState()

        state.onResumed(hasPermission = false, recreated = false, sdkInt = api36)

        verifyNoReturnReported()
        assertNull(state.hint(api36))
        assertFalse(state.hintShown)
    }

    @Test
    fun `a grant button that opened nothing arms no trip`() {
        val state = SettingsReturnState()

        state.onSettingsOpened(null)
        state.onResumed(hasPermission = false, recreated = false, sdkInt = api36)

        verifyNoReturnReported()
        assertNull(state.hint(api36))
    }

    @Test
    fun `coming back without access shows the hint and reports the page`() {
        val state = SettingsReturnState()

        state.onSettingsOpened(SettingsPage.APP)
        state.onResumed(hasPermission = false, recreated = false, sdkInt = api36)

        verify(exactly = 1) {
            AnalyticsTracker.trackPermissionReturnedWithoutGranting(
                SettingsPage.APP,
                hintShown = false,
                recreated = false
            )
        }
        assertEquals(R.string.permission_all_files_hint, state.hint(api36))
        assertTrue(state.hintShown)
    }

    @Test
    fun `a second return without access reports the hint as shown`() {
        val state = SettingsReturnState()
        state.onSettingsOpened(SettingsPage.APP)
        state.onResumed(hasPermission = false, recreated = false, sdkInt = api36)

        state.onSettingsOpened(SettingsPage.APP)
        state.onResumed(hasPermission = false, recreated = false, sdkInt = api36)

        verify(exactly = 1) {
            AnalyticsTracker.trackPermissionReturnedWithoutGranting(
                SettingsPage.APP,
                hintShown = true,
                recreated = false
            )
        }
    }

    @Test
    fun `coming back with access reports the grant and shows no hint`() {
        val state = SettingsReturnState()

        state.onSettingsOpened(SettingsPage.APP)
        state.onResumed(hasPermission = true, recreated = false, sdkInt = api36)

        verify(exactly = 1) {
            AnalyticsTracker.trackPermissionGrantedOnReturn(
                SettingsPage.APP,
                hintShown = false,
                recreated = false
            )
        }
        verify(exactly = 0) { AnalyticsTracker.trackPermissionReturnedWithoutGranting(any(), any(), any()) }
        assertNull(state.hint(api36))
    }

    @Test
    fun `a grant after a failed return reports that the hint was shown`() {
        val state = SettingsReturnState()
        state.onSettingsOpened(SettingsPage.APP)
        state.onResumed(hasPermission = false, recreated = false, sdkInt = api36)

        state.onSettingsOpened(SettingsPage.APP)
        state.onResumed(hasPermission = true, recreated = false, sdkInt = api36)

        verify(exactly = 1) {
            AnalyticsTracker.trackPermissionGrantedOnReturn(
                SettingsPage.APP,
                hintShown = true,
                recreated = false
            )
        }
    }

    @Test
    fun `a return into a rebuilt screen is reported as recreated`() {
        val state = SettingsReturnState()

        state.onSettingsOpened(SettingsPage.APP)
        state.onResumed(hasPermission = false, recreated = true, sdkInt = api36)

        verify(exactly = 1) {
            AnalyticsTracker.trackPermissionReturnedWithoutGranting(
                SettingsPage.APP,
                hintShown = false,
                recreated = true
            )
        }
    }

    @Test
    fun `the trip ends with the return`() {
        val state = SettingsReturnState()
        state.onSettingsOpened(SettingsPage.APP)
        state.onResumed(hasPermission = false, recreated = false, sdkInt = api36)

        // The screen turned off and on again: no new trip to Settings, so no new return.
        state.onResumed(hasPermission = false, recreated = false, sdkInt = api36)

        verify(exactly = 1) { AnalyticsTracker.trackPermissionReturnedWithoutGranting(any(), any(), any()) }
        assertNull(state.awayOn)
    }

    @Test
    fun `the all-apps list gets the same hint as the app page`() {
        val state = SettingsReturnState()

        state.onSettingsOpened(SettingsPage.LIST)
        state.onResumed(hasPermission = false, recreated = false, sdkInt = android11)

        verify(exactly = 1) {
            AnalyticsTracker.trackPermissionReturnedWithoutGranting(
                SettingsPage.LIST,
                hintShown = false,
                recreated = false
            )
        }
        assertEquals(R.string.permission_all_files_hint, state.hint(android11))
    }

    @Test
    fun `the App info fallback on Android 11+ points to Settings`() {
        val state = SettingsReturnState()

        state.onSettingsOpened(SettingsPage.APP_DETAILS)
        state.onResumed(hasPermission = false, recreated = false, sdkInt = android11)

        assertEquals(R.string.permission_settings_unavailable, state.hint(android11))
    }

    @Test
    fun `below Android 11 a return adds no hint`() {
        val state = SettingsReturnState()

        state.onSettingsOpened(SettingsPage.APP_DETAILS)
        state.onResumed(hasPermission = false, recreated = false, sdkInt = android10)

        verify(exactly = 1) {
            AnalyticsTracker.trackPermissionReturnedWithoutGranting(
                SettingsPage.APP_DETAILS,
                hintShown = false,
                recreated = false
            )
        }
        assertNull(state.hint(android10))
        assertFalse(state.hintShown)
    }

    @Test
    fun `the trip and the hint survive a save and restore`() {
        val pages = SettingsPage.entries + null
        pages.forEach { awayOn ->
            pages.forEach { hintFrom ->
                val restored = saveAndRestore(SettingsReturnState(awayOn, hintFrom))

                assertEquals("awayOn for $awayOn/$hintFrom", awayOn, restored.awayOn)
                assertEquals("hintFrom for $awayOn/$hintFrom", hintFrom, restored.hintFrom)
            }
        }
    }

    @Test
    fun `a saved page that no longer exists restores as no trip and no hint`() {
        val restored = SettingsReturnState.Saver.restore(listOf("REMOVED_PAGE", "REMOVED_PAGE"))

        assertNull(restored?.awayOn)
        assertNull(restored?.hintFrom)
    }

    private fun saveAndRestore(state: SettingsReturnState): SettingsReturnState {
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(SettingsReturnState.Saver) { scope.save(state) }!!
        return SettingsReturnState.Saver.restore(saved)!!
    }

    private fun verifyNoReturnReported() {
        verify(exactly = 0) { AnalyticsTracker.trackPermissionReturnedWithoutGranting(any(), any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackPermissionGrantedOnReturn(any(), any(), any()) }
    }
}
