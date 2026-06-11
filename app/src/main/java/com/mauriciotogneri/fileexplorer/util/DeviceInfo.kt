package com.mauriciotogneri.fileexplorer.util

import android.os.Build

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
                Build.PRODUCT == "sdk" ||
                Build.PRODUCT == "sdk_gphone64_arm64" ||
                Build.PRODUCT.startsWith("sdk_google")
    }
}
