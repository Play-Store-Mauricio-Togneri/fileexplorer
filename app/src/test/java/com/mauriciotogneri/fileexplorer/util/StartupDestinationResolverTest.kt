package com.mauriciotogneri.fileexplorer.util

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StartupDestinationResolverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun storage(path: String, displayName: String = "Internal storage") = StorageDevice(
        path = path,
        displayName = displayName,
        totalBytes = 0,
        availableBytes = 0
    )

    @Test
    fun `resolves a folder inside a storage device`() {
        val root = temporaryFolder.newFolder("emulated")
        val folder = File(root, "Download").apply { mkdirs() }

        val destination = StartupDestinationResolver.resolve(
            folder.absolutePath,
            listOf(storage(root.absolutePath))
        )

        assertNotNull(destination)
        assertEquals(folder.absolutePath, destination?.path)
        assertEquals("Download", destination?.title)
        assertEquals(root.absolutePath, destination?.rootPath)
        assertEquals("Internal storage", destination?.rootDisplayName)
    }

    @Test
    fun `titles the storage root with its display name`() {
        val root = temporaryFolder.newFolder("emulated")

        val destination = StartupDestinationResolver.resolve(
            root.absolutePath,
            listOf(storage(root.absolutePath, "SD card"))
        )

        assertEquals("SD card", destination?.title)
        assertEquals(root.absolutePath, destination?.rootPath)
    }

    @Test
    fun `prefers the longest matching storage root`() {
        val outer = temporaryFolder.newFolder("outer")
        val inner = File(outer, "inner").apply { mkdirs() }
        val folder = File(inner, "Music").apply { mkdirs() }

        val destination = StartupDestinationResolver.resolve(
            folder.absolutePath,
            listOf(
                storage(outer.absolutePath, "Internal storage"),
                storage(inner.absolutePath, "SD card")
            )
        )

        assertEquals(inner.absolutePath, destination?.rootPath)
        assertEquals("SD card", destination?.rootDisplayName)
    }

    @Test
    fun `returns null when nothing is configured`() {
        val root = temporaryFolder.newFolder("emulated")

        assertNull(StartupDestinationResolver.resolve(null, listOf(storage(root.absolutePath))))
        assertNull(StartupDestinationResolver.resolve("", listOf(storage(root.absolutePath))))
        assertNull(StartupDestinationResolver.resolve("   ", listOf(storage(root.absolutePath))))
    }

    @Test
    fun `returns null when the folder no longer exists`() {
        val root = temporaryFolder.newFolder("emulated")
        val folder = File(root, "Deleted")

        assertNull(
            StartupDestinationResolver.resolve(
                folder.absolutePath,
                listOf(storage(root.absolutePath))
            )
        )
    }

    @Test
    fun `returns null when the path is a file`() {
        val root = temporaryFolder.newFolder("emulated")
        val file = File(root, "notes.txt").apply { writeText("x") }

        assertNull(
            StartupDestinationResolver.resolve(
                file.absolutePath,
                listOf(storage(root.absolutePath))
            )
        )
    }

    // An unmounted SD card leaves the stored path matching no storage device.
    @Test
    fun `returns null when no storage device contains the path`() {
        val root = temporaryFolder.newFolder("emulated")
        val folder = temporaryFolder.newFolder("sdcard", "Music")

        assertNull(
            StartupDestinationResolver.resolve(
                folder.absolutePath,
                listOf(storage(root.absolutePath))
            )
        )
    }

    @Test
    fun `returns null when no storage devices are mounted`() {
        val folder = temporaryFolder.newFolder("emulated", "Download")

        assertNull(StartupDestinationResolver.resolve(folder.absolutePath, emptyList()))
    }

    // The last segment of a storage root is "0" for internal storage and a volume ID for an SD
    // card, neither of which names anything a user would recognise in the settings row.
    @Test
    fun `labels a storage root with its display name`() {
        val storages = listOf(
            storage("/storage/emulated/0", "Internal storage"),
            storage("/storage/1A2B-3C4D", "SD card")
        )

        assertEquals("Internal storage", StartupDestinationResolver.label("/storage/emulated/0", storages))
        assertEquals("SD card", StartupDestinationResolver.label("/storage/1A2B-3C4D", storages))
    }

    @Test
    fun `labels a folder with its own name`() {
        val storages = listOf(storage("/storage/emulated/0"))

        assertEquals("Download", StartupDestinationResolver.label("/storage/emulated/0/Download", storages))
    }

    // The settings row renders before the storage list has loaded.
    @Test
    fun `labels from the path alone when storages are unavailable`() {
        assertEquals("Download", StartupDestinationResolver.label("/storage/emulated/0/Download", emptyList()))
    }

    // "/storage/emulatedX" must not resolve against the "/storage/emulated" device: matching on a
    // bare prefix would let a sibling folder inherit another volume's root and breadcrumbs.
    @Test
    fun `does not match a sibling path sharing a storage prefix`() {
        val root = temporaryFolder.newFolder("emulated")
        val sibling = temporaryFolder.newFolder("emulatedX")

        assertNull(
            StartupDestinationResolver.resolve(
                sibling.absolutePath,
                listOf(storage(root.absolutePath))
            )
        )
    }
}
