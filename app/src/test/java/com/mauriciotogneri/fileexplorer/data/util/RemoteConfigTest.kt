package com.mauriciotogneri.fileexplorer.data.util

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The review prompt switch must fail open: a console that never answered, or answered with
 * something unreadable, leaves the app asking as it did before the switch existed.
 */
class RemoteConfigTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun consoleValue(valueSource: Int, readAsBoolean: () -> Boolean) {
        val value = mockk<FirebaseRemoteConfigValue> {
            every { source } returns valueSource
            every { asBoolean() } answers { readAsBoolean() }
        }
        val remoteConfig = mockk<FirebaseRemoteConfig> {
            every { getValue("in_app_review") } returns value
        }
        mockkStatic(FirebaseRemoteConfig::class)
        every { FirebaseRemoteConfig.getInstance() } returns remoteConfig
    }

    @Test
    fun `the console switching the review prompt off disables it`() {
        consoleValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) { false }

        assertFalse(RemoteConfig.isInAppReviewEnabled())
    }

    @Test
    fun `the console switching the review prompt on enables it`() {
        consoleValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) { true }

        assertTrue(RemoteConfig.isInAppReviewEnabled())
    }

    // The SDK reads a key it has no value for as false, as it does here.
    @Test
    fun `before any value is fetched the review prompt stays enabled`() {
        consoleValue(FirebaseRemoteConfig.VALUE_SOURCE_STATIC) { false }

        assertTrue(RemoteConfig.isInAppReviewEnabled())
    }

    @Test
    fun `a value that is not a boolean leaves the review prompt enabled`() {
        consoleValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
            throw IllegalArgumentException("[maybe] cannot be converted to a boolean.")
        }

        assertTrue(RemoteConfig.isInAppReviewEnabled())
    }

    @Test
    fun `without Firebase the review prompt stays enabled and init does not throw`() {
        RemoteConfig.init()

        assertTrue(RemoteConfig.isInAppReviewEnabled())
    }
}
