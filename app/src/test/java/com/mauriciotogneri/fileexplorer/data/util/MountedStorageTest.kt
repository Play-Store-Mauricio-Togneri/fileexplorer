package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Covers the predicate that decides whether a stored favorite or recent may be deleted for good.
 *
 * The asymmetry is the whole point: keeping an entry too long is a stale card the user can remove
 * by hand, and forgetting one is a DataStore write with no undo and no backup. Every case below is
 * therefore written to say which way the answer falls, not merely that it is computed.
 */
class MountedStorageTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "test_mounted_storage_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @Test
    fun `a file that exists is never forgettable`() {
        val file = File(tempDir, "present.txt").apply { writeText("content") }

        assertFalse(isForgettable(file.absolutePath, listOf(tempDir.absolutePath)))
    }

    @Test
    fun `a missing file on a mounted volume is forgettable`() {
        val missing = File(tempDir, "gone.txt").absolutePath

        assertTrue(isForgettable(missing, listOf(tempDir.absolutePath)))
    }

    @Test
    fun `a missing file on an unmounted volume is not forgettable`() {
        assertFalse(isForgettable("/storage/1234-5678/photo.jpg", listOf(tempDir.absolutePath)))
    }

    @Test
    fun `nothing is forgettable when no volume could be enumerated`() {
        // getStorages() returning nothing means this app cannot say where anything lives, which is
        // the case where deleting would be most confidently wrong.
        assertFalse(isForgettable(File(tempDir, "gone.txt").absolutePath, emptyList()))
    }

    @Test
    fun `a volume root itself is matched`() {
        assertTrue(isForgettable("/storage/1234-5678", listOf("/storage/1234-5678")))
    }

    @Test
    fun `a volume whose name merely starts with a root is not matched`() {
        // Without the separator, root "/storage/1234" would claim every path on "/storage/12345"
        // and delete the entries of a volume that was never mounted.
        assertFalse(isForgettable("/storage/12345/photo.jpg", listOf("/storage/1234")))
    }

    @Test
    fun `a root already ending in a separator is matched without doubling it`() {
        assertTrue(isForgettable("/storage/1234-5678/photo.jpg", listOf("/storage/1234-5678/")))
    }

    @Test
    fun `a path is matched against any of the mounted volumes`() {
        val roots = listOf("/storage/emulated/0", "/storage/1234-5678")

        assertTrue(isForgettable("/storage/1234-5678/photo.jpg", roots))
        assertTrue(isForgettable("/storage/emulated/0/Download/a.pdf", roots))
        assertFalse(isForgettable("/storage/ABCD-EF01/photo.jpg", roots))
    }
}
