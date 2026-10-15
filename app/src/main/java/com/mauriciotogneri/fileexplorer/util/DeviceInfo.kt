package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Whether the app is running on a non-production device — an emulator or a Firebase Test Lab
 * device — which, with `BuildConfig.DEBUG`, is what keeps Crashlytics and Analytics off them. A
 * false negative here files test-run noise into the production Firebase project alongside real user
 * crashes, indistinguishable from them.
 *
 * Every clause only ever *suppresses* telemetry, so the cost of matching too widely is losing
 * reporting for a device, while matching too narrowly silently corrupts production data. The
 * emulator heuristics are therefore deliberately generous; [isTestLab] needs none, because Test
 * Lab marks its devices explicitly.
 */
object DeviceInfo {
    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") ||
                Build.DEVICE.startsWith("generic") ||
                // The Android emulator's kernel, and the single most durable signal: "goldfish" is
                // the original QEMU1 platform, "ranchu" every current image. No physical device
                // reports either.
                Build.HARDWARE == "goldfish" ||
                Build.HARDWARE == "ranchu" ||
                // Covers every `sdk_*` system image — sdk_gphone64_x86_64, sdk_gphone64_arm64,
                // sdk_google_*, plain "sdk". The previous exact-match and `sdk_google` clauses
                // missed `sdk_gphone64_x86_64`, which is what the x86_64 AVDs this project is
                // tested on actually report: on those, a release build reported to production.
                Build.PRODUCT.startsWith("sdk_") ||
                Build.PRODUCT == "sdk" ||
                Build.DEVICE.startsWith("emu")
    }

    /**
     * Whether the app is running on Firebase Test Lab, which is where Play Console's pre-launch
     * report runs every uploaded build — and that report cannot be switched off. Its devices are
     * physical or virtual hardware that [isEmulator] does not reliably match, so without this their
     * crashes land in the production Crashlytics project. Test Lab marks its devices with this
     * system setting; the key is absent on every other device.
     *
     * Called from `Application.onCreate`, so a settings read that fails answers false rather than
     * crashing startup over telemetry.
     */
    fun isTestLab(context: Context): Boolean {
        return runCatching {
            Settings.System.getString(context.contentResolver, FIREBASE_TEST_LAB_KEY) == "true"
        }.getOrDefault(false)
    }

    private const val FIREBASE_TEST_LAB_KEY = "firebase.test.lab"
}
