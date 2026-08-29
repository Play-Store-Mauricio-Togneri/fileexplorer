package com.mauriciotogneri.fileexplorer.util

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [DeviceInfo.isEmulator] is one of the two switches that keep Firebase off non-production
 * devices: `AnalyticsTracker` and `ErrorReporter` both gate collection on
 * `!(BuildConfig.DEBUG || DeviceInfo.isEmulator())`. The `BuildConfig.DEBUG` half covers this
 * suite; the emulator half is what protects a *release* build someone runs on an emulator, where
 * debug is false and this predicate is the only thing standing between a test run and the
 * production Crashlytics project that real user crashes land in.
 *
 * It cannot be unit tested: `Build.FINGERPRINT` and its siblings are `static final` — unsettable by
 * reflection — and read back null off-device, so `isEmulator()` throws on the JVM.
 *
 * What this pins is one direction only: that the detection still recognises the emulator family the
 * project tests on. A regression that made it answer true on real hardware would silently disable
 * telemetry for every user and is not observable from here — that one needs a physical device.
 */
@RunWith(AndroidJUnit4::class)
class DeviceInfoTest {

    /**
     * `CLAUDE.md` requires an emulator for the instrumentation suite, so this runs on one by
     * definition. Deliberately a hard assertion rather than an `assumeTrue` guard: a skip would
     * report green while checking nothing, which is the failure mode this suite exists to avoid.
     */
    @Test
    fun isEmulator_onTheDeviceTheSuiteRunsOn_isTrue() {
        // The Build values are what the predicate reads, so naming them turns a failure into its
        // own diagnosis: a new emulator image whose identifiers no branch matches reads very
        // differently from a predicate that was broken outright.
        assertTrue(
            "DeviceInfo.isEmulator() no longer recognises this emulator, so a release build run " +
                "here would report to the production Firebase project. If you are running the " +
                "suite on physical hardware, this failure is expected — the project runs " +
                "instrumentation tests on emulators. " +
                "FINGERPRINT=${Build.FINGERPRINT} MODEL=${Build.MODEL} " +
                "PRODUCT=${Build.PRODUCT} BRAND=${Build.BRAND} DEVICE=${Build.DEVICE} " +
                "MANUFACTURER=${Build.MANUFACTURER}",
            DeviceInfo.isEmulator()
        )
    }
}
