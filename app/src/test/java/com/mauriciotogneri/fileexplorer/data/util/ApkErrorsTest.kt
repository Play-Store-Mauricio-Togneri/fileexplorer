package com.mauriciotogneri.fileexplorer.data.util

import android.content.pm.PackageManager
import android.content.res.Resources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ApkErrorsTest {

    @Test
    fun `isUnreadableApk returns true when the archive resources cannot be opened`() {
        val e = PackageManager.NameNotFoundException("Unable to open /storage/emulated/0/app.apk")
        assertTrue(isUnreadableApk(e))
    }

    @Test
    fun `isUnreadableApk returns true when the icon resource has no loadable entry`() {
        // A base APK split out of an app bundle: the launcher icon lives in a config split.
        val e = Resources.NotFoundException("Resource ID #0x7f100000")
        assertTrue(isUnreadableApk(e))
    }

    @Test
    fun `isUnreadableApk returns true for the framework icon lookup NullPointerException`() {
        // The exact failure reported by Crashlytics: ApplicationPackageManager dereferencing a
        // null ApplicationInfo while resolving the icon of an APK that is not installed.
        val e = NullPointerException(
            "Attempt to read from field 'java.lang.String " +
                "android.content.pm.ApplicationInfo.publicSourceDir' on a null object reference"
        )
        assertTrue(isUnreadableApk(e))
    }

    @Test
    fun `isUnreadableApk returns true for a null ApplicationInfo method call`() {
        // Same framework failure, reached through a method instead of a field on another ROM.
        val e = NullPointerException(
            "Attempt to invoke virtual method 'int " +
                "android.content.pm.ApplicationInfo.hashCode()' on a null object reference"
        )
        assertTrue(isUnreadableApk(e))
    }

    @Test
    fun `isUnreadableApk returns false for a NullPointerException from unrelated code`() {
        // Matching the bare type would swallow genuine null-safety bugs in the fetcher.
        assertFalse(isUnreadableApk(NullPointerException()))
        assertFalse(isUnreadableApk(NullPointerException("thumbnail must not be null")))
    }

    @Test
    fun `isUnreadableApk returns false for unrelated exceptions`() {
        assertFalse(isUnreadableApk(IOException("failed to read the archive")))
        assertFalse(isUnreadableApk(IllegalStateException("boom")))
        assertFalse(isUnreadableApk(IllegalArgumentException()))
        assertFalse(isUnreadableApk(RuntimeException()))
        assertFalse(isUnreadableApk(OutOfMemoryError()))
    }
}
