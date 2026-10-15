package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import com.mauriciotogneri.fileexplorer.BuildConfig
import com.mauriciotogneri.fileexplorer.util.DeviceInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * A release build's manifest switches Crashlytics and Analytics on, so on a Play pre-launch (Test
 * Lab) device or an emulator, [isTelemetryCollectionEnabled] is the only thing keeping test runs
 * out of the production Firebase project. Dropping any one clause must fail here.
 */
class TelemetryCollectionTest {

    private val context = mockk<Context>()

    @Before
    fun setUp() {
        mockkObject(DeviceInfo)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `only a release build on a real non-Test-Lab device collects`() {
        listOf(false, true).forEach { debug ->
            listOf(false, true).forEach { emulator ->
                listOf(false, true).forEach { testLab ->
                    every { DeviceInfo.isEmulator() } returns emulator
                    every { DeviceInfo.isTestLab(context) } returns testLab

                    assertEquals(
                        "debug=$debug emulator=$emulator testLab=$testLab",
                        !debug && !emulator && !testLab,
                        isTelemetryCollectionEnabled(context, isDebugBuild = debug)
                    )
                }
            }
        }
    }

    // Pins the default argument to the build type, so it holds under testReleaseUnitTest as well.
    @Test
    fun `by default only a non-debug build collects on a real device`() {
        every { DeviceInfo.isEmulator() } returns false
        every { DeviceInfo.isTestLab(context) } returns false

        assertEquals(!BuildConfig.DEBUG, isTelemetryCollectionEnabled(context))
    }
}
