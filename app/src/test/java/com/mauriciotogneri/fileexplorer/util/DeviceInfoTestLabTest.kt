package com.mauriciotogneri.fileexplorer.util

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [DeviceInfo.isTestLab] only ever suppresses telemetry, so the case that matters most is the
 * negative one: every real user device lacks the key, and answering true there would silently
 * switch off Crashlytics and Analytics for everyone.
 */
class DeviceInfoTestLabTest {

    private val contentResolver = mockk<ContentResolver>()
    private val context = mockk<Context> {
        every { contentResolver } returns this@DeviceInfoTestLabTest.contentResolver
    }

    @Before
    fun setUp() {
        mockkStatic(Settings.System::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a device Test Lab marks is detected`() {
        stubSetting("true")

        assertTrue(DeviceInfo.isTestLab(context))
    }

    @Test
    fun `a device without the setting is not Test Lab`() {
        stubSetting(null)

        assertFalse(DeviceInfo.isTestLab(context))
    }

    @Test
    fun `any value other than true is not Test Lab`() {
        listOf("false", "", "TRUE", "1").forEach { value ->
            stubSetting(value)

            assertFalse("value=\"$value\"", DeviceInfo.isTestLab(context))
        }
    }

    @Test
    fun `a failing settings read answers false instead of throwing`() {
        every { Settings.System.getString(contentResolver, KEY) } throws SecurityException("denied")

        assertFalse(DeviceInfo.isTestLab(context))
    }

    private fun stubSetting(value: String?) {
        every { Settings.System.getString(contentResolver, KEY) } returns value
    }

    private companion object {
        // Spelled out rather than shared with production: a typo there must fail these tests.
        const val KEY = "firebase.test.lab"
    }
}
