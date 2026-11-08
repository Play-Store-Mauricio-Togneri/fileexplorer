package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemFoldersTest {

    // ==================== isProtectedSystemFolder ====================

    @Test
    fun `primary volume data and obb are protected from api 30`() {
        assertTrue(isProtectedSystemFolder("/storage/emulated/0/Android/data", 30))
        assertTrue(isProtectedSystemFolder("/storage/emulated/0/Android/obb", 30))
        assertTrue(isProtectedSystemFolder("/storage/emulated/0/Android/data", 36))
    }

    @Test
    fun `nothing is protected below api 30`() {
        assertFalse(isProtectedSystemFolder("/storage/emulated/0/Android/data", 29))
        assertFalse(isProtectedSystemFolder("/storage/emulated/0/Android/obb", 29))
        // What JVM tests read Build.VERSION.SDK_INT as, so the repository's default rule is off.
        assertFalse(isProtectedSystemFolder("/storage/emulated/0/Android/data", 0))
    }

    @Test
    fun `every volume root shape is covered`() {
        val paths = listOf(
            "/storage/emulated/10/Android/data",
            "/storage/1234-ABCD/Android/data",
            "/storage/1234-ABCD/Android/obb",
            "/storage/self/primary/Android/obb",
            "/storage/emulated/0/Android/data/"
        )
        for (path in paths) {
            assertTrue(path, isProtectedSystemFolder(path, 30))
        }
    }

    @Test
    fun `neighbours, descendants and look-alikes are not protected`() {
        val paths = listOf(
            "/storage/emulated/0/Android",
            "/storage/emulated/0/Android/media",
            "/storage/emulated/0/Download/Android/data",
            "/storage/emulated/0/Android/data/com.foo",
            "/storage/emulated/0/android/data",
            "/storage/emulated/0/Android/Data",
            "/data/user/0/Android/data",
            "/storage/emulated/0/data"
        )
        for (path in paths) {
            assertFalse(path, isProtectedSystemFolder(path, 30))
        }
    }

    // ==================== explainsFailuresInside ====================

    @Test
    fun `only data explains every failure inside it`() {
        assertTrue(explainsFailuresInside(File("/storage/emulated/0/Android/data")))
        assertFalse(explainsFailuresInside(File("/storage/emulated/0/Android/obb")))
    }

    // ==================== deleteLocationOf ====================

    @Test
    fun `locations are bucketed by where the path sits on its volume`() {
        val cases = listOf(
            "/storage/emulated/0/Android/data" to DeleteLocation.ANDROID_DATA,
            "/storage/emulated/0/Android/data/com.foo/files" to DeleteLocation.ANDROID_DATA,
            "/storage/1234-ABCD/Android/obb" to DeleteLocation.ANDROID_OBB,
            "/storage/emulated/0/Android/obb/com.game/main.obb" to DeleteLocation.ANDROID_OBB,
            "/storage/emulated/0/Android" to DeleteLocation.ANDROID,
            "/storage/emulated/0/Android/media/com.whatsapp" to DeleteLocation.ANDROID,
            "/storage/emulated/0" to DeleteLocation.VOLUME_ROOT,
            "/storage/self/primary" to DeleteLocation.VOLUME_ROOT,
            "/storage/1234-ABCD/" to DeleteLocation.VOLUME_ROOT,
            "/storage/emulated/0/Download/Android/data" to DeleteLocation.OTHER,
            "/storage/emulated/0/Androidx" to DeleteLocation.OTHER,
            "/data/user/0/com.foo" to DeleteLocation.OTHER
        )
        for ((path, expected) in cases) {
            assertEquals(path, expected, deleteLocationOf(path))
        }
    }

    @Test
    fun `location labels are the fixed analytics values`() {
        assertEquals(
            listOf("android_data", "android_obb", "android", "volume_root", "other"),
            DeleteLocation.entries.map { it.analyticsLabel }
        )
    }
}
