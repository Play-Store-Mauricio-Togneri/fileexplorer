package com.mauriciotogneri.fileexplorer.integration

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.source.MediaIndexSync
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The premise the app's gallery handling rests on: from Android 11 the system's media service keeps
 * the media index right by itself for every file operation the app makes through a file path, so
 * the app hands it nothing (see `MediaIndexSync`). Each case runs one operation through
 * [FileRepository] with a sync that does nothing, then reads the index.
 *
 * A future Android release that stops doing this fails here, instead of leaving every gallery
 * silently wrong. The index is read as the app that made the changes, which also sees rows the
 * system still holds as pending; whether other apps see them at once is not what this checks.
 *
 * Folder rows are left out: deleting a folder leaves its own row behind, which no gallery lists.
 *
 * Reaching shared storage needs All Files Access, an app op that only exists from R, so earlier
 * releases are skipped.
 */
@RunWith(AndroidJUnit4::class)
class MediaIndexPremiseTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val collection: Uri = MediaStore.Files.getContentUri("external")
    private val fileRepository = FileRepository(mediaIndexSync = NoMediaIndexSync)

    private lateinit var root: File
    private lateinit var allowedRoots: List<String>

    @Before
    fun setUp() {
        assumeTrue(
            "Reaching shared storage needs All Files Access, which only exists from R",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        )
        grantAllFilesAccess()
        assumeTrue(
            "All Files Access was not granted, so no external volume is visible",
            Environment.isExternalStorageManager()
        )
        assumeTrue(
            "Device has no mounted shared storage",
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        )

        root = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "fe_media_index_premise_${System.currentTimeMillis()}"
        ).apply { mkdirs() }
        allowedRoots = listOf(Environment.getExternalStorageDirectory().absolutePath)
    }

    /** The recipe `AllFilesAccessPremiseTest` checks: an app op, not a runtime permission. */
    private fun grantAllFilesAccess() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"
        ).close()
        // The shell command runs on another process; give the op a moment to land.
        repeat(20) {
            if (Environment.isExternalStorageManager()) return
            Thread.sleep(100)
        }
    }

    @After
    fun tearDown() {
        if (::root.isInitialized) {
            runCatching { root.deleteRecursively() }
        }
    }

    @Test
    fun createdImage_isIndexedWithoutAScan() {
        val image = createImage(root, "created.png")

        assertTrue("A new image should be indexed without a scan", awaitRow(image))
    }

    @Test
    fun renamedImage_hasARowAtItsNewNameAndNoneAtItsOld() = runBlocking {
        val image = createIndexedImage(root, "before.png")

        val result = fileRepository.rename(FileItem.from(image), "after.png")

        assertNotNull("The rename should succeed", result)
        val renamed = File(root, "after.png")
        assertTrue("The index should list the new name", awaitRow(renamed))
        assertTrue("The index should no longer list the old name", awaitNoRow(image))
    }

    @Test
    fun renamedFolder_takesEveryRowUnderItAlong() = runBlocking {
        val folder = File(root, "Trip")
        val relativePaths = listOf("beach.png", "day2/hike.png", "day2/night/stars.png")
        relativePaths.forEach { createIndexedImage(folder, it) }

        val result = fileRepository.rename(FileItem.from(folder), "Holiday")

        assertNotNull("The rename should succeed", result)
        val renamed = File(root, "Holiday")
        relativePaths.forEach { relativePath ->
            assertTrue(
                "$relativePath should be listed under the new name",
                awaitRow(File(renamed, relativePath))
            )
            assertTrue(
                "$relativePath should no longer be listed under the old name",
                awaitNoRow(File(folder, relativePath))
            )
        }
    }

    @Test
    fun copiedImage_hasARowAtTheCopyAndKeepsTheOriginalOne() = runBlocking {
        val image = createIndexedImage(File(root, "source"), "harbour.png")
        val target = File(root, "target").apply { mkdirs() }

        fileRepository.copyFiles(
            sources = listOf(FileItem.from(image)),
            targetDir = target.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val copy = File(target, "harbour.png")
        assertTrue("The copy should arrive", copy.exists())
        assertTrue("The index should list the copy", awaitRow(copy))
        assertTrue("The index should still list the original", rowExists(image))
    }

    /** What a move does when it cannot rename: copy, then delete the source. */
    @Test
    fun imageMovedByTheCopy_hasARowAtItsNewPathAndNoneAtItsOld() = runBlocking {
        val image = createIndexedImage(File(root, "source"), "lighthouse.png")
        val target = File(root, "target").apply { mkdirs() }

        fileRepository.copyFiles(
            sources = listOf(FileItem.from(image)),
            targetDir = target.absolutePath,
            deleteAfter = true,
            allowedRoots = allowedRoots
        ).toList()

        val moved = File(target, "lighthouse.png")
        assertTrue("The copy should arrive", moved.exists())
        assertFalse("The original should be gone", image.exists())
        assertTrue("The index should list the new path", awaitRow(moved))
        assertTrue("The index should no longer list the old path", awaitNoRow(image))
    }

    @Test
    fun deletedImage_losesItsRow() = runBlocking {
        val image = createIndexedImage(root, "deleted.png")

        val result = fileRepository.delete(listOf(FileItem.from(image)))

        assertEquals("The delete should succeed", listOf(image.absolutePath), result.removedPaths)
        assertTrue("The index should no longer list the image", awaitNoRow(image))
    }

    @Test
    fun deletedFolder_losesTheRowOfEveryFileUnderIt() = runBlocking {
        val folder = File(root, "Album")
        val images = listOf("one.png", "inner/two.png").map { createIndexedImage(folder, it) }

        val result = fileRepository.delete(listOf(FileItem.from(folder)))

        assertEquals("The delete should succeed", listOf(folder.absolutePath), result.removedPaths)
        images.forEach { image ->
            assertTrue("${image.name} should no longer be listed", awaitNoRow(image))
        }
    }

    // region Helpers

    /** A real PNG, so the index takes it for the image a user would have. */
    private fun createImage(dir: File, relativePath: String): File {
        val file = File(dir, relativePath)
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    /** An image whose row is in the index before the operation runs, failing when it never got one. */
    private fun createIndexedImage(dir: File, relativePath: String): File {
        val image = createImage(dir, relativePath)
        assertTrue(
            "${image.name} got no row within $ROW_POLL_ATTEMPTS polls at $ROW_POLL_INTERVAL_MS ms, " +
                "so nothing in this test ran. Re-run on a device that is not under load.",
            awaitRow(image)
        )
        return image
    }

    private fun awaitRow(file: File): Boolean {
        repeat(ROW_POLL_ATTEMPTS) {
            if (rowExists(file)) return true
            Thread.sleep(ROW_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun awaitNoRow(file: File): Boolean {
        repeat(ROW_POLL_ATTEMPTS) {
            if (!rowExists(file)) return true
            Thread.sleep(ROW_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun rowExists(file: File): Boolean = context.contentResolver.query(
        collection,
        arrayOf(MediaStore.Files.FileColumns._ID),
        "${MediaStore.Files.FileColumns.DATA}=?",
        arrayOf(file.absolutePath),
        null
    )?.use { it.count > 0 } ?: false

    // endregion

    /** No update from the app, so what the index holds is the system's doing alone. */
    private object NoMediaIndexSync : MediaIndexSync {
        override fun afterOperation(created: List<String>, removed: List<String>) = Unit
    }

    private companion object {
        const val ROW_POLL_ATTEMPTS = 20
        const val ROW_POLL_INTERVAL_MS = 250L
    }
}
