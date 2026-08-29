package com.mauriciotogneri.fileexplorer.util

import android.os.Build

/**
 * Whether the app is running on an emulator, which — with `BuildConfig.DEBUG` — is what keeps
 * Crashlytics and Analytics off non-production devices. A false negative here files test-run noise
 * into the production Firebase project alongside real user crashes, indistinguishable from them.
 *
 * Every clause only ever *suppresses* telemetry, so the cost of matching too widely is losing
 * reporting for a device, while matching too narrowly silently corrupts production data. The
 * checks are therefore deliberately generous.
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
}
