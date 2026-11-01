package com.mauriciotogneri.fileexplorer.integration

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.system.Os
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Drives [FileRepository.moveByRename] with its production rename — a real `rename(2)` through
 * `android.system.Os` — on shared storage and on the SD card, where the JVM suite can only stand in
 * for it with `java.io`. What only a device can answer: that a move within one volume really renames
 * on the FUSE-backed storage users have, and that one across volumes is refused rather than turned
 * into a silent copy.
 *
 * The gallery cases check the end state only: rows at the new paths and none at the old. On every
 * version this class runs on (R+) the media provider moves, adds and drops those rows itself as the
 * files move, so these cases pass whether or not the app hands it anything. What they do catch is
 * an update from the app that undoes that bookkeeping, such as a moved file's new path reported as
 * deleted, which removes the live row and the file behind it. Which paths the step reports is
 * asserted by the cases above and by `FileRepositoryTest`; that the folder screen hands every batch
 * to MediaStore, by `FolderViewModelTest`.
 *
 * Reaching shared storage needs All Files Access, an app op that only exists from R, so earlier
 * releases are skipped, as [com.mauriciotogneri.fileexplorer.util.MediaStoreUtilProviderTest] does.
 * The cross-volume cases skip on a device with no mounted removable volume; the project emulator
 * has one, so they run there.
 */
@RunWith(AndroidJUnit4::class)
class MoveByRenameTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val collection: Uri = MediaStore.Files.getContentUri("external")
    private val stamp = System.currentTimeMillis()
    private val fileRepository = FileRepository()

    private val createdRoots = mutableListOf<File>()
    private lateinit var internalRoot: File
    private lateinit var sourceDir: File
    private lateinit var targetDir: File
    private lateinit var allowedRoots: List<String>
    private var sdCardRoot: File? = null

    /**
     * A stand-in for the folder screen's handling of each batch — scan the new paths, drop the old
     * ones — so the gallery cases see the app's updates land on top of the provider's own. It is a
     * copy, not the screen's code: `FolderViewModelTest` is what pins the screen.
     */
    private val updateMediaStore: suspend (List<String>, List<String>) -> Unit = { created, deleted ->
        if (created.isNotEmpty()) {
            MediaStoreUtil.scanFiles(context, created)
        }
        if (deleted.isNotEmpty()) {
            MediaStoreUtil.notifyDeleted(context, deleted)
        }
    }

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

        internalRoot = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "fe_move_rename_$stamp"
        )
        createdRoots.add(internalRoot)
        sourceDir = File(internalRoot, "source").apply { mkdirs() }
        targetDir = File(internalRoot, "target").apply { mkdirs() }

        sdCardRoot = mountedRemovableVolumeRoot()
        allowedRoots = listOfNotNull(
            Environment.getExternalStorageDirectory().absolutePath,
            sdCardRoot?.absolutePath
        )
    }

    /** See `MediaStoreUtilProviderTest.grantAllFilesAccess`: an app op, not a runtime permission. */
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
        createdRoots.forEach { root ->
            runCatching { root.deleteRecursively() }
            runCatching {
                runBlocking { MediaStoreUtil.notifyTreeDeleted(context, listOf(root.absolutePath)) }
            }
        }
    }

    // region Same volume

    @Test
    fun sameVolume_file_isRenamedWithContentAndDateIntact() = runBlocking {
        val file = createTextFile(sourceDir, "notes.txt", "the content")
        val modified = file.lastModified()
        val item = FileItem.from(file)
        val reported = ReportedPaths()

        val result = fileRepository.moveByRename(
            sources = listOf(item),
            targetDir = targetDir.absolutePath,
            allowedRoots = allowedRoots,
            onRenamedPaths = reported.record
        )

        val moved = File(targetDir, "notes.txt")
        assertEquals("The file should move by rename", listOf(item.path), result.renamed.map { it.path })
        assertTrue("Nothing should be left for the copy", result.remaining.isEmpty())
        assertEquals(1, result.movedFiles)
        assertFalse("The source path should be empty", file.exists())
        assertEquals("The content should arrive intact", "the content", moved.readText())
        assertEquals("A rename keeps the modified date", modified, moved.lastModified())
        assertEquals(listOf(moved.absolutePath), reported.created)
        assertEquals(listOf(file.absolutePath), reported.deleted)
    }

    @Test
    fun sameVolume_folderTree_keepsItsStructure() = runBlocking {
        val folder = File(sourceDir, "Projects")
        createTextFile(folder, "top.txt", "top")
        createTextFile(folder, "sub/middle.txt", "middle")
        createTextFile(folder, "sub/deeper/bottom.txt", "bottom")
        File(folder, "empty").mkdirs()
        File(folder, "sub/also_empty").mkdirs()
        val treeBefore = relativeTree(folder)
        val inodeBefore = Os.stat(folder.path).st_ino
        val reported = ReportedPaths()

        val result = fileRepository.moveByRename(
            sources = listOf(FileItem.from(folder)),
            targetDir = targetDir.absolutePath,
            allowedRoots = allowedRoots,
            onRenamedPaths = reported.record
        )

        val moved = File(targetDir, "Projects")
        assertEquals("The folder should move by rename", listOf(folder.path), result.renamed.map { it.path })
        assertFalse("The source folder should be gone", folder.exists())
        assertEquals("The tree should arrive with the same structure", treeBefore, relativeTree(moved))
        assertEquals("bottom", File(moved, "sub/deeper/bottom.txt").readText())
        assertEquals("Files only are counted, not folders", 3, result.movedFiles)
        assertEquals(
            setOf("top.txt", "sub/middle.txt", "sub/deeper/bottom.txt").map { File(moved, it).absolutePath }.toSet(),
            reported.created.toSet()
        )
        assertEquals(
            setOf("top.txt", "sub/middle.txt", "sub/deeper/bottom.txt").map { File(folder, it).absolutePath }.toSet(),
            reported.deleted.toSet()
        )
        // Extra evidence only, never the proof: FUSE may answer with its own node ids, so the inode
        // is compared only where a rename on this volume is seen to keep one.
        if (renameKeepsInodes(internalRoot)) {
            assertEquals(
                "A rename keeps the folder's inode, which a copy would not",
                inodeBefore,
                Os.stat(moved.path).st_ino
            )
        }
    }

    /**
     * The size of tree whose bookkeeping the folder screen waits on with no byte progress to show,
     * so the elapsed time is logged under [TAG] for reading off a run.
     */
    @Test
    fun sameVolume_twoThousandFileTree_reportsEveryFile() = runBlocking {
        val folder = File(sourceDir, "Bulk")
        val relativePaths = (0 until BULK_FILE_COUNT).map { index ->
            "part_${index % BULK_FOLDER_COUNT}/file_$index.txt"
        }
        relativePaths.forEach { createTextFile(folder, it, "x") }
        val reported = ReportedPaths()

        val started = SystemClock.elapsedRealtime()
        val result = fileRepository.moveByRename(
            sources = listOf(FileItem.from(folder)),
            targetDir = targetDir.absolutePath,
            allowedRoots = allowedRoots
        ) { created, deleted ->
            reported.record(created, deleted)
            updateMediaStore(created, deleted)
        }
        val elapsedMs = SystemClock.elapsedRealtime() - started
        Log.i(TAG, "Moved $BULK_FILE_COUNT files by rename in $elapsedMs ms, MediaStore updates included")

        val moved = File(targetDir, "Bulk")
        assertEquals(BULK_FILE_COUNT, result.movedFiles)
        assertEquals("Every file reported once", BULK_FILE_COUNT, reported.created.size)
        assertEquals(relativePaths.map { File(moved, it).absolutePath }.toSet(), reported.created.toSet())
        assertEquals("Every old path reported once", BULK_FILE_COUNT, reported.deleted.size)
        assertEquals(relativePaths.map { File(folder, it).absolutePath }.toSet(), reported.deleted.toSet())
        assertEquals(
            "Every file should be at the destination",
            BULK_FILE_COUNT,
            moved.walkTopDown().count { it.isFile }
        )
        assertFalse("The source folder should be gone", folder.exists())
    }

    @Test
    fun takenNames_leaveTheItemsInRemainingUntouched() = runBlocking {
        val file = createTextFile(sourceDir, "report.txt", "moving")
        val folder = File(sourceDir, "Albums")
        createTextFile(folder, "inside.txt", "moving")
        createTextFile(targetDir, "report.txt", "already there")
        File(targetDir, "Albums").mkdirs()
        val items = listOf(FileItem.from(file), FileItem.from(folder))
        val reported = ReportedPaths()

        val collisions = fileRepository.findMoveCollisions(items, targetDir.absolutePath)
        val result = fileRepository.moveByRename(
            sources = items,
            targetDir = targetDir.absolutePath,
            allowedRoots = allowedRoots,
            onRenamedPaths = reported.record
        )

        assertEquals("Both names are taken", items.map { it.path }, collisions.map { it.path })
        assertEquals("Both are left for the copy", items.map { it.path }, result.remaining.map { it.path })
        assertTrue("Nothing is renamed onto a taken name", result.renamed.isEmpty())
        assertEquals("moving", file.readText())
        assertEquals("moving", File(folder, "inside.txt").readText())
        assertEquals("The file already there is untouched", "already there", File(targetDir, "report.txt").readText())
        assertFalse(
            "The folder already there must not have been replaced",
            File(targetDir, "Albums/inside.txt").exists()
        )
        assertTrue("Nothing moved, so nothing is reported", reported.batches.isEmpty())
    }

    /**
     * Shared storage folds case, so a name that differs only in case is the same entry, and
     * `rename(2)` onto it would replace the file there. The JVM's filesystems do not fold case,
     * which leaves this to a device.
     */
    @Test
    fun takenNameInAnotherCase_leavesTheItemUntouched() = runBlocking {
        assertStorageFoldsCase()
        val file = createTextFile(sourceDir, "photo.txt", "moving")
        createTextFile(targetDir, "PHOTO.TXT", "already there")
        val item = FileItem.from(file)

        val result = fileRepository.moveByRename(
            sources = listOf(item),
            targetDir = targetDir.absolutePath,
            allowedRoots = allowedRoots
        ) { _, _ -> }

        assertEquals(listOf(item.path), result.remaining.map { it.path })
        assertEquals("moving", file.readText())
        assertEquals("already there", File(targetDir, "PHOTO.TXT").readText())
    }

    // endregion

    // region Across volumes

    @Test
    fun internalToSdCard_renameIsRefused_andTheItemStaysForTheCopy() = runBlocking {
        val sdTarget = sdCardFolder("target")
        assertRenameRefusedAcrossVolumes(sourceDir, sdTarget)
    }

    @Test
    fun sdCardToInternal_renameIsRefused_andTheItemStaysForTheCopy() = runBlocking {
        val sdSource = sdCardFolder("source")
        assertRenameRefusedAcrossVolumes(sdSource, targetDir)
    }

    @Test
    fun withinSdCard_fileIsRenamed() = runBlocking {
        val sdSource = sdCardFolder("source")
        val sdTarget = sdCardFolder("target")
        val file = createTextFile(sdSource, "card.txt", "on the card")
        val item = FileItem.from(file)

        val result = fileRepository.moveByRename(
            sources = listOf(item),
            targetDir = sdTarget.absolutePath,
            allowedRoots = allowedRoots
        ) { _, _ -> }

        assertEquals("A move within the card renames", listOf(item.path), result.renamed.map { it.path })
        assertFalse(file.exists())
        assertEquals("on the card", File(sdTarget, "card.txt").readText())
    }

    /**
     * The name is free, so the item stays behind only because the rename failed — the case the
     * folder screen measures free space for again before copying.
     */
    private suspend fun assertRenameRefusedAcrossVolumes(from: File, to: File) {
        val file = createTextFile(from, "travelling.txt", "crossing volumes")
        val item = FileItem.from(file)
        val reported = ReportedPaths()

        val collisions = fileRepository.findMoveCollisions(listOf(item), to.absolutePath)
        val result = fileRepository.moveByRename(
            sources = listOf(item),
            targetDir = to.absolutePath,
            allowedRoots = allowedRoots,
            onRenamedPaths = reported.record
        )

        assertTrue("The name is free at the destination", collisions.isEmpty())
        assertEquals("Left for the copy", listOf(item.path), result.remaining.map { it.path })
        assertTrue("A rename cannot cross volumes", result.renamed.isEmpty())
        assertEquals("The source is untouched", "crossing volumes", file.readText())
        assertFalse("Nothing may arrive by any other means", File(to, "travelling.txt").exists())
        assertTrue("Nothing moved, so nothing is reported", reported.batches.isEmpty())
    }

    // endregion

    // region Gallery

    @Test
    fun movedImage_hasARowAtItsNewPathAndNoneAtItsOld() = runBlocking {
        val image = createImage(sourceDir, "sunset.png")
        scanAndAwaitRow(image)
        val item = FileItem.from(image)

        val result = fileRepository.moveByRename(
            sources = listOf(item),
            targetDir = targetDir.absolutePath,
            allowedRoots = allowedRoots,
            onRenamedPaths = updateMediaStore
        )

        val moved = File(targetDir, "sunset.png")
        assertEquals(listOf(item.path), result.renamed.map { it.path })
        assertTrue("The gallery should list the image at its new path", awaitRow(moved.absolutePath))
        assertTrue("The gallery should no longer list the old path", awaitNoRow(image.absolutePath))
    }

    @Test
    fun movedFolderOfImages_hasEveryRowAtTheNewPathsAndNoneAtTheOld() = runBlocking {
        val folder = File(sourceDir, "Trip")
        val relativePaths = listOf("beach.png", "day2/hike.png", "day2/night/stars.png")
        relativePaths.forEach { scanAndAwaitRow(createImage(folder, it)) }

        val result = fileRepository.moveByRename(
            sources = listOf(FileItem.from(folder)),
            targetDir = targetDir.absolutePath,
            allowedRoots = allowedRoots,
            onRenamedPaths = updateMediaStore
        )

        val moved = File(targetDir, "Trip")
        assertEquals(relativePaths.size, result.movedFiles)
        relativePaths.forEach { relativePath ->
            assertTrue(
                "$relativePath should be listed at its new path",
                awaitRow(File(moved, relativePath).absolutePath)
            )
            assertTrue(
                "$relativePath should no longer be listed at its old path",
                awaitNoRow(File(folder, relativePath).absolutePath)
            )
        }
    }

    /**
     * A move to another volume cannot rename, so the copy moves the image instead, and the gallery
     * ends with a row at the new path and none at the old. As with the other gallery cases, the
     * provider does most of that itself; the copy's paths are handed over through the stand-in
     * above so that an update undoing it would show.
     */
    @Test
    fun imageMovedToTheSdCardByTheCopy_hasARowAtItsNewPathAndNoneAtItsOld() = runBlocking {
        val sdTarget = sdCardFolder("target")
        val image = createImage(sourceDir, "harbour.png")
        scanAndAwaitRow(image)
        val item = FileItem.from(image)

        val renameResult = fileRepository.moveByRename(
            sources = listOf(item),
            targetDir = sdTarget.absolutePath,
            allowedRoots = allowedRoots,
            onRenamedPaths = updateMediaStore
        )
        assertEquals("A rename cannot cross volumes", listOf(item.path), renameResult.remaining.map { it.path })

        fileRepository.copyFiles(
            sources = renameResult.remaining,
            targetDir = sdTarget.absolutePath,
            deleteAfter = true,
            allowedRoots = allowedRoots
        ).collect { progress ->
            val deleted = if (progress.sourceDeleteFailed) emptyList() else progress.deletedSourcePaths
            updateMediaStore(progress.createdPaths, deleted)
        }

        val moved = File(sdTarget, "harbour.png")
        assertTrue("The copy should arrive on the card", moved.exists())
        assertFalse("The original should be gone", image.exists())
        assertTrue("The gallery should list the image at its new path", awaitRow(moved.absolutePath))
        assertTrue("The gallery should no longer list the old path", awaitNoRow(image.absolutePath))
    }

    // endregion

    // region Helpers

    /** Every batch [FileRepository.moveByRename] handed over, in order. */
    private class ReportedPaths {
        val batches = mutableListOf<Pair<List<String>, List<String>>>()
        val created: List<String> get() = batches.flatMap { it.first }
        val deleted: List<String> get() = batches.flatMap { it.second }
        val record: suspend (List<String>, List<String>) -> Unit = { created, deleted ->
            batches.add(created to deleted)
        }
    }

    /**
     * The root of a mounted removable volume — the emulator's SD card — read from the platform's
     * own volume list rather than from the app's storage code, or null when there is none.
     */
    private fun mountedRemovableVolumeRoot(): File? =
        context.getSystemService(StorageManager::class.java)
            .storageVolumes
            .firstOrNull { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            ?.directory

    /** A fresh folder on the SD card, or a skip when the device has no mounted removable volume. */
    private fun sdCardFolder(name: String): File {
        val root = sdCardRoot
        assumeTrue("Device has no mounted removable volume", root != null)
        val testRoot = File(root!!, "fe_move_rename_$stamp")
        if (testRoot !in createdRoots) createdRoots.add(testRoot)
        return File(testRoot, name).apply { mkdirs() }
    }

    private fun createTextFile(dir: File, relativePath: String, content: String): File {
        val file = File(dir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    /** A real PNG, so the media scanner indexes it as the image a user would move. */
    private fun createImage(dir: File, relativePath: String): File {
        val file = File(dir, relativePath)
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    /** Every entry below [root], folders marked with a trailing slash. */
    private fun relativeTree(root: File): Set<String> =
        root.walkTopDown()
            .drop(1)
            .map { it.relativeTo(root).path + if (it.isDirectory) "/" else "" }
            .toSet()

    /**
     * Whether a rename on [dir]'s volume keeps an entry's inode number, probed with the platform's
     * own rename on a scratch file so the answer does not come from the code under test.
     */
    private fun renameKeepsInodes(dir: File): Boolean {
        val probe = File(dir, "inode_probe").apply { writeText("probe") }
        val moved = File(dir, "inode_probe_moved")
        return try {
            val before = Os.stat(probe.path).st_ino
            Os.rename(probe.path, moved.path)
            before == Os.stat(moved.path).st_ino
        } finally {
            probe.delete()
            moved.delete()
        }
    }

    private fun assertStorageFoldsCase() {
        val probe = createTextFile(sourceDir, "case_probe.txt", "probe")
        val folds = File(sourceDir, "CASE_PROBE.TXT").exists()
        probe.delete()
        assertTrue(
            "${sourceDir.parent} does not fold case, so a name taken in another case is never " +
                "taken here. Run on an emulator or device with emulated external storage.",
            folds
        )
    }

    /** Indexes [file] and waits for its row, failing when the fixture never got one. */
    private fun scanAndAwaitRow(file: File) {
        val scanned = CountDownLatch(1)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, _ ->
            scanned.countDown()
        }
        scanned.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertTrue(
            "The media scanner produced no row for ${file.name} within $SCAN_TIMEOUT_SECONDS s, " +
                "nor in the $ROW_POLL_ATTEMPTS polls at $ROW_POLL_INTERVAL_MS ms after it, so " +
                "nothing in this test ran. Re-run on a device that is not under load.",
            awaitRow(file.absolutePath)
        )
    }

    private fun awaitRow(path: String): Boolean {
        repeat(ROW_POLL_ATTEMPTS) {
            if (rowExists(path)) return true
            Thread.sleep(ROW_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun awaitNoRow(path: String): Boolean {
        repeat(ROW_POLL_ATTEMPTS) {
            if (!rowExists(path)) return true
            Thread.sleep(ROW_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun rowExists(path: String): Boolean = context.contentResolver.query(
        collection,
        arrayOf(MediaStore.Files.FileColumns._ID),
        "${MediaStore.Files.FileColumns.DATA}=?",
        arrayOf(path),
        null
    )?.use { it.count > 0 } ?: false

    // endregion

    private companion object {
        const val TAG = "MoveByRenameTest"
        const val BULK_FILE_COUNT = 2_000
        const val BULK_FOLDER_COUNT = 4
        const val SCAN_TIMEOUT_SECONDS = 10L
        const val ROW_POLL_ATTEMPTS = 20
        const val ROW_POLL_INTERVAL_MS = 250L
    }
}
